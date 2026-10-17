package com.gabbymaybelle.spawngazebo.client;

import com.gabbymaybelle.spawngazebo.Config;
import com.gabbymaybelle.spawngazebo.GazeboGuard;
import com.mojang.serialization.Codec;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;
import net.minecraft.client.Minecraft;
import net.minecraft.client.OptionInstance;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.gui.components.StringWidget;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.options.OptionsSubScreen;
import net.minecraft.client.server.IntegratedServer;
import net.minecraft.network.chat.Component;

/**
 * Spawn Gazebo's settings, opened from Mod Menu. Built from vanilla option widgets so it looks
 * like the game's own options. Settings apply to worlds you host; on someone else's server, the
 * server's config file decides, so they're greyed out there.
 */
public class SettingsScreen extends OptionsSubScreen {
	private static final String PREFIX = "spawn_gazebo.options.";

	public SettingsScreen(Screen parent) {
		super(parent, Minecraft.getInstance().options, Component.translatable(PREFIX + "title"));
	}

	/** True while connected to a server that isn't this game's own. */
	private static boolean onServer() {
		Minecraft mc = Minecraft.getInstance();
		return mc.level != null && mc.getSingleplayerServer() == null;
	}

	@Override
	protected void addOptions() {
		boolean onServer = onServer();
		if (onServer) {
			// 1.21.1's options list has no header row: a line of text across the full width instead.
			list.addSmall(new StringWidget(310, 20, Component.translatable(PREFIX + "on_server"), font), null);
		}
		OptionInstance<?>[] options = {
				toggle("spawnGazebo", Config.spawnGazebo, v -> Config.spawnGazebo = v),
				toggle("starterChest", Config.starterChest, v -> Config.starterChest = v),
				chestLoot(),
				toggle("protectGazebo", Config.protectGazebo, v -> Config.protectGazebo = v),
				radius("protectedRadius", Config.protectedRadius, Config.MIN_RADIUS, Config.MAX_RADIUS, v -> Config.protectedRadius = v),
		};
		list.addSmall(options);
		if (onServer) {
			for (OptionInstance<?> option : options) {
				AbstractWidget widget = list.findOption(option);
				if (widget != null) {
					widget.active = false;
				}
			}
		}
	}

	@Override
	public void removed() {
		super.removed();
		Config.save();
		IntegratedServer server = Minecraft.getInstance().getSingleplayerServer();
		if (server != null) {
			// Hosting a LAN world: guests hear whether the gazebo is protected, and how far that reaches.
			server.execute(() -> GazeboGuard.broadcast(server));
		}
	}

	private static OptionInstance<Boolean> toggle(String key, boolean value, Consumer<Boolean> set) {
		return OptionInstance.createBoolean(PREFIX + key,
				OptionInstance.cachedConstantTooltip(Component.translatable(PREFIX + key + ".tooltip")),
				value, set::accept);
	}

	private static OptionInstance<Integer> radius(String key, int value, int min, int max, Consumer<Integer> set) {
		return new OptionInstance<>(PREFIX + key,
				OptionInstance.cachedConstantTooltip(Component.translatable(PREFIX + key + ".tooltip")),
				(caption, v) -> Component.translatable("options.generic_value", caption, Component.literal(Integer.toString(v))),
				new OptionInstance.IntRange(min, max), value, set::accept);
	}

	/**
	 * A button that cycles the chest's loot between the starter kit and the bonus chest. A loot
	 * table id typed into the config file shows as "Custom" until the button is clicked.
	 */
	private static OptionInstance<String> chestLoot() {
		List<String> values = new ArrayList<>(List.of(Config.STARTER_KIT, Config.BONUS_CHEST));
		boolean custom = !values.contains(Config.chestLoot);
		if (custom) {
			values.add(Config.chestLoot);
		}
		return new OptionInstance<>(PREFIX + "chestLoot",
				OptionInstance.cachedConstantTooltip(Component.translatable(PREFIX + "chestLoot.tooltip")),
				// The button adds the caption itself, so this is just the value.
				(caption, v) -> values.indexOf(v) < 2 ? Component.translatable(PREFIX + "chestLoot." + v) : Component.translatable(PREFIX + "chestLoot.custom"),
				new OptionInstance.Enum<>(values, Codec.STRING),
				Config.chestLoot, v -> Config.chestLoot = v);
	}
}
