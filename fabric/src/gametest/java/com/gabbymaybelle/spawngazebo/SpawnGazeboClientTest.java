package com.gabbymaybelle.spawngazebo;

import com.gabbymaybelle.spawngazebo.client.SettingsScreen;
import com.mojang.blaze3d.platform.InputConstants;
import com.mojang.brigadier.tree.CommandNode;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayDeque;
import com.gabbymaybelle.spawngazebo.harness.ClientGameTest;
import com.gabbymaybelle.spawngazebo.harness.ClientGameTestContext;
import com.gabbymaybelle.spawngazebo.harness.TestServerContext;
import com.gabbymaybelle.spawngazebo.harness.TestSingleplayerContext;
import net.fabricmc.fabric.api.event.player.UseBlockCallback;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.client.gui.components.events.ContainerEventHandler;
import net.minecraft.client.gui.components.events.GuiEventListener;
import net.minecraft.client.gui.screens.TitleScreen;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.item.BucketItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.material.Fluids;
import net.minecraft.world.level.GameRules;
import net.minecraft.world.level.levelgen.structure.BoundingBox;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * The settings screen in a real game: from the title screen (where Mod Menu opens it), in a
 * world you're hosting, and greyed out on someone else's server. Also checks a new world gets
 * its gazebo. Run with `./gradlew runClientGameTest`; screenshots land in
 * build/run/clientGameTest/screenshots. The server part needs a dedicated server with the mod
 * already running (offline mode): `-PtestServer=localhost:25565` connects to it; without it that
 * part is skipped and says so in the log.
 */
public class SpawnGazeboClientTest implements ClientGameTest {
	private static final Logger LOGGER = LoggerFactory.getLogger("Spawn Gazebo test");

	@Override
	public void runTest(ClientGameTestContext ctx) {
		settingsFile();
		titleScreenSettings(ctx);
		try (TestSingleplayerContext world = ctx.worldBuilder().create()) {
			world.waitForChunksRender();
			CompoundTag spawn = world.getServer().computeOnServer(server -> server.getCommandStorage()
					.get(ResourceLocation.fromNamespaceAndPath(SpawnGazeboMod.MOD_ID, "spawn")));
			check(spawn.getBoolean("chest"), "a new world gets the gazebo, with its starter chest");
			if (FabricLoader.getInstance().isModLoaded("lootr")) {
				lootrChest(world.getServer());
			} else {
				check(spawn.getBoolean("claimed"), "the first player to join claims the chest's kit");
			}
			int radius = world.getServer().computeOnServer(server -> server.overworld().getGameRules().getInt(GameRules.RULE_SPAWN_RADIUS));
			check(radius == 0, "building the gazebo sets the spawn radius to 0 (got " + radius + ")");
			ctx.takeScreenshot("spawn_gazebo_new_world");
			protection(world.getServer());
			buildCommand(world.getServer());
			ctx.takeScreenshot("spawn_gazebo_built_by_command");
			inWorldSettings(ctx);
		}
		String address = System.getProperty("spawn_gazebo.gametest.server");
		if (address == null) {
			LOGGER.info("[test] no dedicated server given (-PtestServer): the on-a-server settings check is skipped");
			return;
		}
		try (TestSingleplayerContext server = ctx.connect(address)) {
			server.waitForChunksRender();
			onServerSettings(ctx);
		}
	}

	/**
	 * The settings file: a typo keeps the default instead of switching the protection off, case and
	 * spaces don't matter, a setting from a newer version survives, no temporary file is left
	 * behind, and a complete file isn't rewritten just by loading it (so notes added by hand stay).
	 */
	private static void settingsFile() {
		Path file = FabricLoader.getInstance().getConfigDir().resolve(SpawnGazeboMod.MOD_ID + ".properties");
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

	/** Mod Menu's Config button opens the screen from the title screen, before any world exists. */
	private static void titleScreenSettings(ClientGameTestContext ctx) {
		ctx.setScreen(() -> new SettingsScreen(new TitleScreen()));
		ctx.waitForScreen(SettingsScreen.class);
		ctx.takeScreenshot("spawn_gazebo_settings");
		for (String label : new String[] { "Spawn Gazebo", "Starter Chest", "Chest Loot", "Protect the Gazebo", "Protected Radius" }) {
			AbstractWidget widget = widgetNamed(ctx, label + ":");
			check(widget != null && widget.active, "the settings screen has a working " + label + " button");
		}

		AbstractWidget loot = widgetNamed(ctx, "Chest Loot:");
		check(loot.getMessage().getString().contains("Starter Kit"), "Chest Loot starts on Starter Kit (got " + loot.getMessage().getString() + ")");
		click(ctx, loot);
		check(Config.chestLoot.equals(Config.BONUS_CHEST), "clicking Chest Loot moves it to the bonus chest (got " + Config.chestLoot + ")");
		check(widgetNamed(ctx, "Chest Loot:").getMessage().getString().contains("Bonus Chest"), "the button now says Bonus Chest");
		ctx.takeScreenshot("spawn_gazebo_settings_bonus_chest");

		click(ctx, widgetNamed(ctx, "Protect the Gazebo:"));
		check(!Config.protectGazebo, "clicking Protect the Gazebo turns it off");

		// Closing the screen saves to the file.
		ctx.getInput().pressKey(InputConstants.KEY_ESCAPE);
		ctx.waitForScreen(TitleScreen.class);
		String saved = readConfig();
		check(saved.contains("chestLoot=bonus_chest"), "Chest Loot is saved to the file");
		check(saved.contains("protectGazebo=false"), "Protect the Gazebo is saved to the file");

		// Back to the defaults for the rest of the test.
		Config.chestLoot = Config.STARTER_KIT;
		Config.protectGazebo = true;
		Config.save();
	}

	/**
	 * A survival player can't break, place or dig a path on the lawn inside the protected radius
	 * (12 by default), can just outside it, and a creative player can anywhere. Driven through the
	 * server's own player actions, so the same checks run as when a player clicks.
	 */
	private static void protection(TestServerContext server) {
		server.runCommand("gamemode survival @a");
		String result = server.computeOnServer(s -> {
			ServerLevel level = s.overworld();
			ServerPlayer player = s.getPlayerList().getPlayers().getFirst();
			BlockPos middle = level.getSharedSpawnPos();
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

			expect(failures, !shovel(player, level, inside), "a shovel makes a path inside the radius");
			expect(failures, level.getBlockState(inside).is(Blocks.GRASS_BLOCK), "the lawn inside the radius stays grass");
			expect(failures, shovel(player, level, outside), "a shovel can't make a path outside the radius");
			expect(failures, level.getBlockState(outside).is(Blocks.DIRT_PATH), "the lawn outside the radius becomes a path");

			expect(failures, !place(player, level, inside), "a block can be placed inside the radius");
			expect(failures, level.getBlockState(inside.above()).isAir(), "nothing appears on the lawn inside the radius");
			expect(failures, place(player, level, outside), "a block can't be placed outside the radius");
			expect(failures, level.getBlockState(outside.above()).is(Blocks.OAK_PLANKS), "the planks appear outside the radius");

			expect(failures, !player.gameMode.destroyBlock(inside), "the lawn inside the radius can be broken");
			expect(failures, level.getBlockState(inside).is(Blocks.GRASS_BLOCK), "the lawn inside the radius is still there");
			expect(failures, player.gameMode.destroyBlock(outside.above()), "the planks outside the radius can't be broken");

			// A water bucket: nothing poured inside the radius, water just outside it.
			BucketItem bucket = (BucketItem) Items.WATER_BUCKET;
			expect(failures, !bucket.emptyContents(player, level, inside.above(), null), "water can be poured inside the radius");
			expect(failures, level.getBlockState(inside.above()).isAir(), "no water appears inside the radius");
			expect(failures, bucket.emptyContents(player, level, outside.above(), null), "water can't be poured outside the radius");
			expect(failures, level.getFluidState(outside.above()).is(Fluids.WATER), "the water appears outside the radius");
			level.setBlockAndUpdate(outside.above(), Blocks.AIR.defaultBlockState());

			player.setGameMode(GameType.CREATIVE);
			expect(failures, player.gameMode.destroyBlock(inside), "a creative player can't break the lawn inside the radius");
			player.setGameMode(GameType.SURVIVAL);

			// A bench (wooden stairs) right-clicked with a tool in hand is let through, so a mod that
			// seats you on stairs still works; the lawn beside it with the same axe is refused.
			level.setBlockAndUpdate(inside, Blocks.GRASS_BLOCK.defaultBlockState());
			BlockPos bench = inside.east();
			level.setBlockAndUpdate(bench, Blocks.OAK_STAIRS.defaultBlockState());
			expect(failures, useCallback(player, level, new ItemStack(Items.IRON_AXE), bench) == InteractionResult.PASS,
					"a bench inside the radius can be right-clicked with an axe in hand");
			expect(failures, useCallback(player, level, new ItemStack(Items.IRON_AXE), inside) == InteractionResult.FAIL,
					"the lawn inside the radius is refused to an axe");
			level.setBlockAndUpdate(bench, Blocks.AIR.defaultBlockState());
			player.setItemInHand(InteractionHand.MAIN_HAND, ItemStack.EMPTY);
			return failures.toString();
		});
		check(result.isEmpty(), "protection:" + result);
	}

	/**
	 * /spawngazebo build in a world that already has its gazebo: only operators, nothing until the
	 * confirm, then a second gazebo where it was asked for, with the spawn and the protection moved
	 * to it and its chest counted as claimed.
	 */
	private static void buildCommand(TestServerContext server) {
		String result = server.computeOnServer(s -> {
			ServerLevel level = s.overworld();
			StringBuilder failures = new StringBuilder();
			BlockPos oldMiddle = level.getSharedSpawnPos();
			// 48 blocks east, well outside the first gazebo's protection.
			BlockPos target = oldMiddle.offset(48, 0, 0);
			CommandSourceStack op = s.createCommandSourceStack().withSuppressedOutput().withPosition(Vec3.atCenterOf(target));
			CommandSourceStack notOp = op.withPermission(0);
			CommandNode<CommandSourceStack> node = s.getCommands().getDispatcher().getRoot().getChild("spawngazebo");
			expect(failures, node != null && node.canUse(op), "operators can use /spawngazebo");
			expect(failures, node != null && !node.canUse(notOp), "players who aren't operators can't use /spawngazebo");
			expect(failures, GazeboCommand.confirm(op) == 0, "confirm does nothing before build has asked");
			expect(failures, GazeboCommand.ask(op) == 1, "build asks first");
			expect(failures, level.getSharedSpawnPos().equals(oldMiddle), "asking alone doesn't build anything or move the spawn");
			expect(failures, GazeboCommand.confirm(op) == 1, "confirm builds the gazebo");
			BlockPos newMiddle = level.getSharedSpawnPos();
			expect(failures, newMiddle.getX() == target.getX() && newMiddle.getZ() == target.getZ(),
					"the world spawn moves into the new gazebo (it's at " + newMiddle + ", asked for " + target + ")");
			BoundingBox box = Platform.get().savedBox(level);
			expect(failures, box != null && box.getCenter().getX() == target.getX() && box.getCenter().getZ() == target.getZ(),
					"the world remembers the new gazebo's box (" + box + ")");
			expect(failures, GazeboGuard.guards(level, newMiddle), "the new gazebo is protected");
			expect(failures, !GazeboGuard.guards(level, oldMiddle), "the old gazebo is no longer protected");
			CompoundTag spawn = s.getCommandStorage().get(ResourceLocation.fromNamespaceAndPath(SpawnGazeboMod.MOD_ID, "spawn"));
			expect(failures, spawn.getBoolean("chest") && spawn.getBoolean("claimed"),
					"the new chest is for the players already here, so newcomers get their own kit (" + spawn + ")");
			expect(failures, GazeboCommand.confirm(op) == 0, "a second confirm builds nothing more");
			return failures.toString();
		});
		check(result.isEmpty(), "the build command:" + result);
	}

	/** Uses a shovel on the top of a block, as a right-click would; true if it did anything. */
	private static boolean shovel(ServerPlayer player, ServerLevel level, BlockPos pos) {
		return use(player, level, new ItemStack(Items.IRON_SHOVEL), pos);
	}

	/** Places planks on the top of a block, as a right-click would; true if it did anything. */
	private static boolean place(ServerPlayer player, ServerLevel level, BlockPos pos) {
		return use(player, level, new ItemStack(Items.OAK_PLANKS, 4), pos);
	}

	private static boolean use(ServerPlayer player, ServerLevel level, ItemStack stack, BlockPos pos) {
		player.setItemInHand(InteractionHand.MAIN_HAND, stack);
		BlockHitResult hit = new BlockHitResult(Vec3.atCenterOf(pos).add(0, 0.5, 0), Direction.UP, pos, false);
		return player.gameMode.useItemOn(player, level, stack, InteractionHand.MAIN_HAND, hit).consumesAction();
	}

	/** What the guard's right-click check says (every registered use-block callback, in fact) about this item on this block. */
	private static InteractionResult useCallback(ServerPlayer player, ServerLevel level, ItemStack stack, BlockPos pos) {
		player.setItemInHand(InteractionHand.MAIN_HAND, stack);
		BlockHitResult hit = new BlockHitResult(Vec3.atCenterOf(pos).add(0, 0.5, 0), Direction.UP, pos, false);
		return UseBlockCallback.EVENT.invoker().interact(player, level, InteractionHand.MAIN_HAND, hit);
	}

	private static void expect(StringBuilder failures, boolean ok, String what) {
		if (!ok) {
			failures.append("\n  ").append(what);
		}
	}

	/**
	 * With Lootr, the starter chest becomes a Lootr chest (everyone gets their own copy), so the
	 * kit isn't handed out on joining and the chest still rolls the starter kit.
	 */
	private static void lootrChest(TestServerContext server) {
		String result = server.computeOnServer(s -> {
			ServerLevel level = s.overworld();
			CompoundTag spawn = s.getCommandStorage().get(ResourceLocation.fromNamespaceAndPath(SpawnGazeboMod.MOD_ID, "spawn"));
			if (!spawn.getBoolean("lootr")) {
				return "the gazebo noted that Lootr is installed";
			}
			BlockPos middle = level.getSharedSpawnPos();
			for (BlockPos pos : BlockPos.betweenClosed(middle.offset(-8, -3, -8), middle.offset(8, 3, 8))) {
				ResourceLocation id = BuiltInRegistries.BLOCK.getKey(level.getBlockState(pos).getBlock());
				if (id.getNamespace().equals("lootr") && id.getPath().contains("chest")) {
					boolean kitHandedOut = s.getPlayerList().getPlayers().getFirst().getInventory().contains(new ItemStack(Items.TORCH));
					return kitHandedOut ? "with Lootr the kit isn't handed out on joining" : "";
				}
			}
			return "the starter chest became a Lootr chest";
		});
		check(result.isEmpty(), result);
	}

	/** In a world you're hosting, the settings work. */
	private static void inWorldSettings(ClientGameTestContext ctx) {
		ctx.setScreen(() -> new SettingsScreen(null));
		ctx.waitForScreen(SettingsScreen.class);
		check(widgetNamed(ctx, "On a server") == null, "no server note in a world you're hosting");
		AbstractWidget protect = widgetNamed(ctx, "Protect the Gazebo:");
		check(protect != null && protect.active, "the settings can be changed in a world you're hosting");
		ctx.takeScreenshot("spawn_gazebo_settings_in_world");
		ctx.getInput().pressKey(InputConstants.KEY_ESCAPE);
		ctx.waitForScreen(null);
	}

	/** On someone else's server, the server's file decides: the note shows and every button is greyed out. */
	private static void onServerSettings(ClientGameTestContext ctx) {
		ctx.setScreen(() -> new SettingsScreen(null));
		ctx.waitForScreen(SettingsScreen.class);
		check(widgetNamed(ctx, "On a server") != null, "the screen says the server's settings apply");
		for (String label : new String[] { "Spawn Gazebo", "Starter Chest", "Chest Loot", "Protect the Gazebo", "Protected Radius" }) {
			AbstractWidget widget = widgetNamed(ctx, label + ":");
			check(widget != null && !widget.active, label + " is greyed out on a server");
		}
		// The server's chat and social toasts would cover the screen.
		ctx.runOnClient(mc -> mc.getToasts().clear());
		ctx.waitTick();
		ctx.takeScreenshot("spawn_gazebo_settings_on_server");
		ctx.getInput().pressKey(InputConstants.KEY_ESCAPE);
		ctx.waitForScreen(null);
	}

	private static String readConfig() {
		try {
			return Files.readString(FabricLoader.getInstance().getConfigDir().resolve(SpawnGazeboMod.MOD_ID + ".properties"));
		} catch (IOException e) {
			throw new AssertionError("couldn't read the settings file", e);
		}
	}

	/** A widget on the current screen (searching inside option lists too) whose label contains the text. */
	private static AbstractWidget widgetNamed(ClientGameTestContext ctx, String text) {
		return ctx.computeOnClient(mc -> {
			ArrayDeque<GuiEventListener> queue = new ArrayDeque<>(mc.screen.children());
			while (!queue.isEmpty()) {
				GuiEventListener child = queue.poll();
				if (child instanceof AbstractWidget widget && widget.getMessage().getString().contains(text)) {
					return widget;
				}
				if (child instanceof ContainerEventHandler container) {
					queue.addAll(container.children());
				}
			}
			return null;
		});
	}

	private static void click(ClientGameTestContext ctx, AbstractWidget widget) {
		double scale = ctx.computeOnClient(mc -> (double) mc.getWindow().getGuiScale());
		ctx.getInput().setCursorPos((widget.getX() + 5) * scale, (widget.getY() + 5) * scale);
		ctx.waitTicks(2);
		ctx.getInput().pressMouse(InputConstants.MOUSE_BUTTON_LEFT);
		ctx.waitTick();
	}

	private static void check(boolean ok, String what) {
		if (!ok) {
			throw new AssertionError("Failed: " + what);
		}
	}
}
