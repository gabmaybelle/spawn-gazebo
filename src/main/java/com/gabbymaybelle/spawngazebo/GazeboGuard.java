package com.gabbymaybelle.spawngazebo;

import io.netty.buffer.ByteBuf;
import java.util.Optional;
import net.fabricmc.fabric.api.attachment.v1.AttachmentRegistry;
import net.fabricmc.fabric.api.attachment.v1.AttachmentType;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.event.player.PlayerBlockBreakEvents;
import net.fabricmc.fabric.api.event.player.UseBlockCallback;
import net.fabricmc.fabric.api.networking.v1.PayloadTypeRegistry;
import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.core.BlockPos;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.tags.ItemTags;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.DecoratedPotBlock;
import net.minecraft.world.level.levelgen.structure.BoundingBox;

/**
 * Keeps the gazebo standing: inside its box, players outside creative can't break or place
 * blocks or reshape them with tools, and explosions, fire, pistons and buckets leave it alone (see
 * the mixins that call {@link #guards}). The chest, the benches and the flower pots still work, and
 * the two decorated pots can still be broken for what's inside them. {@link Config#protectGazebo}
 * switches it off for anyone who wants to remodel.
 */
public final class GazeboGuard {
	/** The gazebo's box in the overworld, saved when it's built. */
	private static final AttachmentType<BoundingBox> BOX = AttachmentRegistry.create(
			Identifier.fromNamespaceAndPath(SpawnGazeboMod.MOD_ID, "spawn_gazebo_box"),
			builder -> builder.persistent(BoundingBox.CODEC));
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
		public static final Type<Box> TYPE = new Type<>(Identifier.fromNamespaceAndPath(SpawnGazeboMod.MOD_ID, "gazebo_box"));
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

		/** What to tell players right now: the box while it's protected, nothing otherwise. */
		static Box current() {
			return new Box(Optional.ofNullable(Config.protectGazebo ? GazeboGuard.box : null));
		}
	}

	private GazeboGuard() {
	}

	static void register() {
		PayloadTypeRegistry.clientboundPlay().register(Box.TYPE, Box.CODEC);
		ServerLifecycleEvents.SERVER_STARTED.register(server -> box = load(server.overworld()));
		ServerLifecycleEvents.SERVER_STOPPED.register(server -> box = null);
		ServerPlayConnectionEvents.JOIN.register((handler, sender, server) -> {
			if (ServerPlayNetworking.canSend(handler.player, Box.TYPE.id())) {
				sender.sendPacket(Box.current());
			}
		});
		PlayerBlockBreakEvents.BEFORE.register((level, player, pos, state, blockEntity) ->
				state.getBlock() instanceof DecoratedPotBlock || !protects(level, player, pos));
		// Tools that turn one block into another: stripping, paths, farmland.
		UseBlockCallback.EVENT.register((player, level, hand, hit) -> {
			ItemStack stack = player.getItemInHand(hand);
			boolean reshapes = stack.is(ItemTags.AXES) || stack.is(ItemTags.SHOVELS) || stack.is(ItemTags.HOES);
			return reshapes && protects(level, player, hit.getBlockPos()) ? InteractionResult.FAIL : InteractionResult.PASS;
		});
	}

	/** Remembers where the gazebo was just built. */
	static void guard(ServerLevel level, BoundingBox built) {
		level.setAttached(BOX, built);
		box = built;
		broadcast(level.getServer());
	}

	/** Tells everyone on the server where the gazebo is now (after it's built, or the setting changes). */
	public static void broadcast(MinecraftServer server) {
		for (ServerPlayer player : server.getPlayerList().getPlayers()) {
			if (ServerPlayNetworking.canSend(player, Box.TYPE.id())) {
				ServerPlayNetworking.send(player, Box.current());
			}
		}
	}

	/** On a player's game: what the server last said. */
	public static void setClientBox(BoundingBox received) {
		clientBox = received;
	}

	/**
	 * True when {@code pos} is part of the gazebo and it's being protected. On the server that's the
	 * saved box and the setting; on a player's game it's what the server said when they joined.
	 */
	public static boolean guards(Level level, BlockPos pos) {
		BoundingBox current = level.isClientSide() ? clientBox : Config.protectGazebo ? box : null;
		return current != null && level.dimension() == Level.OVERWORLD && current.isInside(pos);
	}

	/** {@link #guards}, unless it's a creative player doing it (so the gazebo can still be edited). */
	public static boolean protects(Level level, Entity entity, BlockPos pos) {
		return !(entity instanceof Player player && player.isCreative()) && guards(level, pos);
	}

	/** The box saved when the gazebo was built, or null when this world has none. */
	private static BoundingBox load(ServerLevel level) {
		return level.getAttached(BOX);
	}
}
