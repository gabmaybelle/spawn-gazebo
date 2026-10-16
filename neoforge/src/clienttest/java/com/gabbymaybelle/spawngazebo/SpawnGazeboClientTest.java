package com.gabbymaybelle.spawngazebo;

import static com.gabbymaybelle.spawngazebo.ClientTest.check;
import static com.gabbymaybelle.spawngazebo.ClientTest.createWorld;
import static com.gabbymaybelle.spawngazebo.ClientTest.onServer;
import static com.gabbymaybelle.spawngazebo.ClientTest.run;
import static com.gabbymaybelle.spawngazebo.ClientTest.screenshot;
import static com.gabbymaybelle.spawngazebo.ClientTest.waitUntil;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.Identifier;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.gamerules.GameRules;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;
import net.neoforged.fml.ModList;
import net.neoforged.neoforge.common.CommonHooks;
import net.neoforged.neoforge.event.entity.player.PlayerInteractEvent;

/**
 * The NeoForge jar's in-game test: the same checks as the Fabric game test (fabric/src/gametest)
 * for the settings file, a new world's gazebo and its protection, plus what's NeoForge's own: the
 * world remembers it was given its gazebo and where (NeoForge attachments), the player's game is
 * told where it is (NeoForge networking), and the right-click and break events refuse what they should.
 */
final class SpawnGazeboClientTest {
	/** The middle of the gazebo, as the server saw it. */
	private static volatile BlockPos middle;

	private SpawnGazeboClientTest() {
	}

	static void steps() {
		run(mc -> settingsFile());
		createWorld("spawn_gazebo-clienttest");

		onServer("a new world's gazebo", server -> {
			StringBuilder out = new StringBuilder();
			ServerLevel level = server.overworld();
			middle = level.getRespawnData().pos();
			CompoundTag spawn = server.getCommandStorage().get(Identifier.fromNamespaceAndPath(SpawnGazeboMod.MOD_ID, "spawn"));
			expect(out, spawn.getBooleanOr("chest", false), "a new world gets the gazebo, with its starter chest");
			if (ModList.get().isLoaded("lootr")) {
				lootrChest(server, level, spawn, out);
			} else {
				expect(out, spawn.getBooleanOr("claimed", false), "the first player to join claims the chest's kit");
			}
			int radius = level.getGameRules().get(GameRules.RESPAWN_RADIUS);
			expect(out, radius == 0, "building the gazebo sets the spawn radius to 0 (got " + radius + ")");
			expect(out, Platform.get().gazeboChecked(level), "the world remembers it has been given its gazebo");
			expect(out, Platform.get().savedBox(level) != null, "the world remembers where the gazebo is");
			return out.toString();
		});
		// The server tells the player's game where the gazebo is, so it refuses a placement there itself.
		waitUntil("the player's game knowing where the gazebo is", 200, mc -> middle != null && GazeboGuard.guards(mc.level, middle));
		screenshot();
		protection();
	}

	/**
	 * With Lootr, the starter chest becomes a Lootr chest (everyone gets their own copy), so the
	 * kit isn't handed out on joining and the chest still rolls the starter kit.
	 */
	private static void lootrChest(MinecraftServer server, ServerLevel level, CompoundTag spawn, StringBuilder out) {
		if (!spawn.getBooleanOr("lootr", false)) {
			out.append("\n  the gazebo noted that Lootr is installed");
			return;
		}
		for (BlockPos pos : BlockPos.betweenClosed(middle.offset(-8, -3, -8), middle.offset(8, 3, 8))) {
			Identifier id = BuiltInRegistries.BLOCK.getKey(level.getBlockState(pos).getBlock());
			if (id.getNamespace().equals("lootr") && id.getPath().contains("chest")) {
				if (server.getPlayerList().getPlayers().getFirst().getInventory().contains(new ItemStack(Items.TORCH))) {
					out.append("\n  with Lootr the kit isn't handed out on joining");
				}
				return;
			}
		}
		out.append("\n  the starter chest became a Lootr chest");
	}

	/**
	 * The settings file: a typo keeps the default instead of switching the protection off, case and
	 * spaces don't matter, a setting from a newer version survives, no temporary file is left
	 * behind, and a complete file isn't rewritten just by loading it (so notes added by hand stay).
	 */
	private static void settingsFile() {
		Path file = Platform.get().configDir().resolve(SpawnGazeboMod.MOD_ID + ".properties");
		try {
			Files.writeString(file, "protectGazebo=ture\nstarterChest= FALSE \nfutureSetting=hello\n");
			Config.load();
			check(Config.protectGazebo, "a typo (\"ture\") leaves Protect the Gazebo on, its default");
			check(!Config.starterChest, "\" FALSE \" reads as false");
			String written = Files.readString(file);
			check(written.contains("futureSetting=hello"), "a setting this version doesn't know is kept");
			check(written.contains("protectedRadius=12"), "missing settings are written with their defaults");
			check(!Files.exists(file.resolveSibling(file.getFileName() + ".tmp")), "no temporary file is left behind");
			String complete = "# a note added by hand\n" + written;
			Files.writeString(file, complete);
			Config.load();
			check(Files.readString(file).equals(complete), "loading a complete file leaves it exactly as it was");
		} catch (IOException e) {
			throw new AssertionError("couldn't use the settings file", e);
		} finally {
			Config.starterChest = true;
			Config.protectGazebo = true;
			Config.save();
		}
	}

	/** Inside the protected radius nothing changes; just outside it, everything works as usual. */
	private static void protection() {
		onServer("protection", s -> {
			ServerLevel level = s.overworld();
			ServerPlayer player = s.getPlayerList().getPlayers().getFirst();
			player.setGameMode(GameType.SURVIVAL);
			int ground = middle.getY() - 3;
			// The lawn 10 blocks out (inside the radius) and 14 out (outside it), off the gazebo's footprint.
			BlockPos inside = new BlockPos(middle.getX() + 10, ground, middle.getZ());
			BlockPos outside = new BlockPos(middle.getX() + 14, ground, middle.getZ());
			StringBuilder failures = new StringBuilder();
			for (BlockPos pos : new BlockPos[] { inside, outside }) {
				level.setBlockAndUpdate(pos, Blocks.GRASS_BLOCK.defaultBlockState());
				level.setBlockAndUpdate(pos.above(), Blocks.AIR.defaultBlockState());
				level.setBlockAndUpdate(pos.above(2), Blocks.AIR.defaultBlockState());
			}
			player.teleportTo(middle.getX() + 12.5, ground + 1, middle.getZ() + 2.5);

			expect(failures, !use(player, level, new ItemStack(Items.IRON_SHOVEL), inside), "a shovel makes a path inside the radius");
			expect(failures, level.getBlockState(inside).is(Blocks.GRASS_BLOCK), "the lawn inside the radius stays grass");
			expect(failures, use(player, level, new ItemStack(Items.IRON_SHOVEL), outside), "a shovel can't make a path outside the radius");
			expect(failures, level.getBlockState(outside).is(Blocks.DIRT_PATH), "the lawn outside the radius becomes a path");

			expect(failures, !use(player, level, new ItemStack(Items.OAK_PLANKS, 4), inside), "a block can be placed inside the radius");
			expect(failures, level.getBlockState(inside.above()).isAir(), "nothing appears on the lawn inside the radius");
			expect(failures, use(player, level, new ItemStack(Items.OAK_PLANKS, 4), outside), "a block can't be placed outside the radius");
			expect(failures, level.getBlockState(outside.above()).is(Blocks.OAK_PLANKS), "the planks appear outside the radius");

			expect(failures, !player.gameMode.destroyBlock(inside), "the lawn inside the radius can be broken");
			expect(failures, level.getBlockState(inside).is(Blocks.GRASS_BLOCK), "the lawn inside the radius is still there");
			expect(failures, player.gameMode.destroyBlock(outside.above()), "the planks outside the radius can't be broken");

			player.setGameMode(GameType.CREATIVE);
			expect(failures, player.gameMode.destroyBlock(inside), "a creative player can't break the lawn inside the radius");
			player.setGameMode(GameType.SURVIVAL);

			// A bench (wooden stairs) right-clicked with a tool in hand is let through, so a mod that
			// seats you on stairs still works; the lawn beside it with the same axe is refused.
			level.setBlockAndUpdate(inside, Blocks.GRASS_BLOCK.defaultBlockState());
			BlockPos bench = inside.east();
			level.setBlockAndUpdate(bench, Blocks.OAK_STAIRS.defaultBlockState());
			expect(failures, rightClickEvent(player, new ItemStack(Items.IRON_AXE), bench) == InteractionResult.PASS,
					"a bench inside the radius can be right-clicked with an axe in hand");
			expect(failures, rightClickEvent(player, new ItemStack(Items.IRON_AXE), inside) == InteractionResult.FAIL,
					"the lawn inside the radius is refused to an axe");
			level.setBlockAndUpdate(bench, Blocks.AIR.defaultBlockState());
			player.setItemInHand(InteractionHand.MAIN_HAND, ItemStack.EMPTY);
			return failures.toString();
		});
	}

	/** Uses an item on the top of a block, as a right-click would; true if it did anything. */
	private static boolean use(ServerPlayer player, ServerLevel level, ItemStack stack, BlockPos pos) {
		player.setItemInHand(InteractionHand.MAIN_HAND, stack);
		BlockHitResult hit = new BlockHitResult(Vec3.atCenterOf(pos).add(0, 0.5, 0), Direction.UP, pos, false);
		return player.gameMode.useItemOn(player, level, stack, InteractionHand.MAIN_HAND, hit).consumesAction();
	}

	/** What NeoForge's right-click event comes back with for this item on this block: FAIL when it's refused, PASS otherwise. */
	private static InteractionResult rightClickEvent(ServerPlayer player, ItemStack stack, BlockPos pos) {
		player.setItemInHand(InteractionHand.MAIN_HAND, stack);
		BlockHitResult hit = new BlockHitResult(Vec3.atCenterOf(pos).add(0, 0.5, 0), Direction.UP, pos, false);
		PlayerInteractEvent.RightClickBlock event = CommonHooks.onRightClickBlock(player, InteractionHand.MAIN_HAND, pos, hit);
		return event.isCanceled() ? event.getCancellationResult() : InteractionResult.PASS;
	}

	private static void expect(StringBuilder failures, boolean ok, String what) {
		if (!ok) {
			failures.append("\n  ").append(what);
		}
	}
}
