package com.gabbymaybelle.spawngazebo.client;

import com.gabbymaybelle.spawngazebo.Config;
import com.gabbymaybelle.spawngazebo.GazeboGuard;
import java.util.function.Consumer;
import net.minecraft.client.Minecraft;
import net.minecraft.client.OptionInstance;
import net.minecraft.client.gui.components.AbstractWidget;
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
	public SettingsScreen(Screen parent) {
		super(parent, Minecraft.getInstance().options, Component.translatable("spawn_gazebo.options.title"));
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
			list.addHeader(Component.translatable("spawn_gazebo.options.on_server"));
		}
		OptionInstance<?>[] options = {
				toggle("spawnGazebo", Config.spawnGazebo, v -> Config.spawnGazebo = v),
				toggle("starterChest", Config.starterChest, v -> Config.starterChest = v),
				toggle("protectGazebo", Config.protectGazebo, v -> Config.protectGazebo = v),
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
			// Hosting a LAN world: guests hear whether the gazebo is protected.
			server.execute(() -> GazeboGuard.broadcast(server));
		}
	}

	private static OptionInstance<Boolean> toggle(String key, boolean value, Consumer<Boolean> set) {
		return OptionInstance.createBoolean("spawn_gazebo.options." + key,
				OptionInstance.cachedConstantTooltip(Component.translatable("spawn_gazebo.options." + key + ".tooltip")),
				value, set::accept);
	}
}
