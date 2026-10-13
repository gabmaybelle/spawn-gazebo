package com.gabbymaybelle.spawngazebo;

import com.gabbymaybelle.spawngazebo.client.SettingsScreen;
import com.mojang.blaze3d.platform.InputConstants;
import java.io.IOException;
import java.nio.file.Files;
import java.util.ArrayDeque;
import net.fabricmc.fabric.api.client.gametest.v1.FabricClientGameTest;
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;
import net.fabricmc.fabric.api.client.gametest.v1.context.TestDedicatedServerContext;
import net.fabricmc.fabric.api.client.gametest.v1.context.TestSingleplayerContext;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.gui.components.events.ContainerEventHandler;
import net.minecraft.client.gui.components.events.GuiEventListener;
import net.minecraft.client.gui.screens.TitleScreen;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.Identifier;
import net.minecraft.world.level.gamerules.GameRules;

/**
 * The settings screen in a real game: from the title screen (where Mod Menu opens it), in a
 * world you're hosting, and greyed out on someone else's server. Also checks a new world gets
 * its gazebo. Run with `./gradlew runClientGameTest`; screenshots land in
 * build/run/clientGameTest/screenshots.
 */
public class SpawnGazeboClientTest implements FabricClientGameTest {
	@Override
	public void runTest(ClientGameTestContext ctx) {
		titleScreenSettings(ctx);
		try (TestSingleplayerContext world = ctx.worldBuilder().create()) {
			world.getConnection().waitForChunksRender();
			CompoundTag spawn = world.getServer().computeOnServer(server -> server.getCommandStorage()
					.get(Identifier.fromNamespaceAndPath(SpawnGazeboMod.MOD_ID, "spawn")));
			check(spawn.getBooleanOr("chest", false), "a new world gets the gazebo, with its starter chest");
			int radius = world.getServer().computeOnServer(server -> server.overworld().getGameRules().get(GameRules.RESPAWN_RADIUS));
			check(radius == 0, "building the gazebo sets the spawn radius to 0 (got " + radius + ")");
			ctx.takeScreenshot("spawn_gazebo_new_world");
			inWorldSettings(ctx);
		}
		try (TestDedicatedServerContext server = ctx.worldBuilder().createServer()) {
			try (var connection = server.connect()) {
				connection.waitForChunksRender();
				onServerSettings(ctx);
			}
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
		ctx.runOnClient(mc -> mc.gui.toastManager().clear());
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
			ArrayDeque<GuiEventListener> queue = new ArrayDeque<>(mc.gui.screen().children());
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
