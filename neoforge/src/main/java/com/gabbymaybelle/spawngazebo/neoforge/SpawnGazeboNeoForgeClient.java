package com.gabbymaybelle.spawngazebo.neoforge;

import com.gabbymaybelle.spawngazebo.GazeboGuard;
import com.gabbymaybelle.spawngazebo.SpawnGazeboMod;
import com.gabbymaybelle.spawngazebo.client.SettingsScreen;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.fml.ModContainer;
import net.neoforged.fml.common.Mod;
import net.neoforged.neoforge.client.event.ClientPlayerNetworkEvent;
import net.neoforged.neoforge.client.gui.IConfigScreenFactory;
import net.neoforged.neoforge.common.NeoForge;

/** The client's part on NeoForge: the Mods screen's Config button, and forgetting the gazebo on leaving a world. */
@Mod(value = SpawnGazeboMod.MOD_ID, dist = Dist.CLIENT)
public class SpawnGazeboNeoForgeClient {
	public SpawnGazeboNeoForgeClient(ModContainer container) {
		container.registerExtensionPoint(IConfigScreenFactory.class, (mod, parent) -> new SettingsScreen(parent));
		NeoForge.EVENT_BUS.addListener((ClientPlayerNetworkEvent.LoggingOut event) -> GazeboGuard.setClientBox(null));
	}
}
