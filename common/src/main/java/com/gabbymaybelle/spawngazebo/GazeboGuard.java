package com.gabbymaybelle.spawngazebo;

import io.netty.buffer.ByteBuf;
import java.util.Optional;
import net.minecraft.core.BlockPos;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.tags.BlockTags;
import net.minecraft.tags.ItemTags;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.DecoratedPotBlock;
import net.minecraft.world.level.block.FlowerPotBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.structure.BoundingBox;
import net.minecraft.world.phys.BlockHitResult;

/**
 * Keeps the gazebo standing: inside its box, and as far round it as {@link Config#protectedRadius}
 * reaches, players outside creative can't break or place blocks or reshape them with tools, and
 * explosions, fire, pistons and buckets leave it alone (see the mixins that call {@link #guards}).
 * The chest, the benches and the flower pots still work, and the decorated pots can still be
 * broken for what's inside them. {@link Config#protectGazebo} switches it off for anyone who wants
 * to remodel.
 */
public final class GazeboGuard {
	/** How far below the ground the box reaches, so nobody digs the gazebo out from underneath. */
	static final int DEPTH = 3;
	/** How far above the ground the box reaches: the lightning rod on the dome, with a little to spare. */
	static final int HEIGHT = 15;

	/** The running server's gazebo, or null when there's none. */
	private static volatile BoundingBox box;
	/**
	 * On a player's game: the gazebo the server told it about, or null when there's none or it isn't
	 * protected. Known on both sides so the game can refuse a placement itself, rather than show the
	 * block for a moment and then take it back when the server says no.
	 */
	private static volatile BoundingBox clientBox;

	/** Where the gazebo is, and whether it's protected, sent to each player as they join. */
	public record Box(Optional<BoundingBox> box) implements CustomPacketPayload {
		public static final Type<Box> TYPE = new Type<>(ResourceLocation.fromNamespaceAndPath(SpawnGazeboMod.MOD_ID, "gazebo_box"));
		private static final StreamCodec<ByteBuf, BoundingBox> BOX_CODEC = StreamCodec.composite(
				ByteBufCodecs.VAR_INT, BoundingBox::minX, ByteBufCodecs.VAR_INT, BoundingBox::minY, ByteBufCodecs.VAR_INT, BoundingBox::minZ,
				ByteBufCodecs.VAR_INT, BoundingBox::maxX, ByteBufCodecs.VAR_INT, BoundingBox::maxY, ByteBufCodecs.VAR_INT, BoundingBox::maxZ,
				BoundingBox::new);
		public static final StreamCodec<RegistryFriendlyByteBuf, Box> CODEC = StreamCodec.composite(
				ByteBufCodecs.optional(BOX_CODEC), Box::box, Box::new);

		@Override
		public Type<Box> type() {
			return TYPE;
		}

		/** What to tell players right now: the protected area while it's protected, nothing otherwise. */
		static Box current() {
			return new Box(Optional.ofNullable(protectedArea()));
		}
	}

	private GazeboGuard() {
	}

	/** The server has started: the box saved when the gazebo was built, or null when this world has none. */
	public static void onServerStarted(MinecraftServer server) {
		box = Platform.get().savedBox(server.overworld());
	}

	public static void onServerStopped() {
		box = null;
	}

	/** A player joined: tell their game where the gazebo is, if it understands. */
	public static void onPlayerJoin(ServerPlayer player) {
		if (Platform.get().canSend(player, Box.TYPE)) {
			Platform.get().send(player, Box.current());
		}
	}

	/** Whether a player may break this block; the decorated pots can always be broken for what's inside. */
	public static boolean allowBreak(Level level, Player player, BlockPos pos, BlockState state) {
		return state.getBlock() instanceof DecoratedPotBlock || !protects(level, player, pos);
	}

	/**
	 * A player right-clicking a block: tools that turn one block into another (stripping, scraping
	 * the copper, paths, farmland) are refused inside the area with FAIL; anything else is PASS. The
	 * chest, the pots and anything else you open or take from still works with a tool in hand.
	 */
	public static InteractionResult onUseBlock(Player player, Level level, InteractionHand hand, BlockHitResult hit) {
		ItemStack stack = player.getItemInHand(hand);
		boolean reshapes = stack.is(ItemTags.AXES) || stack.is(ItemTags.SHOVELS) || stack.is(ItemTags.HOES);
		if (!reshapes || !protects(level, player, hit.getBlockPos())) {
			return InteractionResult.PASS;
		}
		BlockState state = level.getBlockState(hit.getBlockPos());
		// The benches are wooden stairs, which no tool changes; a mod that lets you sit on stairs
		// (Espresso Flavoured Core does) still gets its turn with a tool in hand.
		boolean harmless = state.hasBlockEntity() || state.getBlock() instanceof FlowerPotBlock || state.is(BlockTags.WOODEN_STAIRS);
		return harmless ? InteractionResult.PASS : InteractionResult.FAIL;
	}

	/** Remembers where the gazebo was just built. */
	static void guard(ServerLevel level, BoundingBox built) {
		Platform.get().saveBox(level, built);
		box = built;
		broadcast(level.getServer());
	}

	/** Tells everyone on the server where the gazebo is now (after it's built, or the setting changes). */
	public static void broadcast(MinecraftServer server) {
		for (ServerPlayer player : server.getPlayerList().getPlayers()) {
			onPlayerJoin(player);
		}
	}

	/** On a player's game: what the server last said. */
	public static void setClientBox(BoundingBox received) {
		clientBox = received;
	}

	/**
	 * On the server: the area being protected right now, or null when there's no gazebo or the
	 * setting is off. It's the gazebo's own box widened to {@link Config#protectedRadius} from the
	 * middle, so the ground round the gazebo is kept as it was too.
	 */
	private static BoundingBox protectedArea() {
		BoundingBox built = box;
		if (built == null || !Config.protectGazebo) {
			return null;
		}
		int extra = Math.max(0, Config.protectedRadius - SpawnGazebo.HALF);
		return extra == 0 ? built : built.inflatedBy(extra, 0, extra);
	}

	/**
	 * True when {@code pos} is in the protected area round the gazebo. On the server that's the
	 * saved box, the radius and the setting; on a player's game it's what the server said when they
	 * joined.
	 */
	public static boolean guards(Level level, BlockPos pos) {
		BoundingBox current = level.isClientSide() ? clientBox : protectedArea();
		return current != null && level.dimension() == Level.OVERWORLD && current.isInside(pos);
	}

	/** {@link #guards}, unless it's a creative player doing it (so the gazebo can still be edited). */
	public static boolean protects(Level level, Entity entity, BlockPos pos) {
		return !(entity instanceof Player player && player.isCreative()) && guards(level, pos);
	}
}
