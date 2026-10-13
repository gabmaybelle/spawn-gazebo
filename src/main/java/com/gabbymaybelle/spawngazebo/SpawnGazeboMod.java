package com.gabbymaybelle.spawngazebo;

import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.resource.v1.ResourceLoader;
import net.fabricmc.fabric.api.resource.v1.pack.PackActivationType;
import net.fabricmc.loader.api.FabricLoader;
import net.fabricmc.loader.api.ModContainer;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Spawn Gazebo: new worlds start in a little round stone gazebo. {@link SpawnGazebo} builds it,
 * {@link GazeboGuard} keeps it standing, and the built-in data pack holds the structures and the
 * starter chest's kit.
 */
public class SpawnGazeboMod implements ModInitializer {
	public static final String MOD_ID = "spawn_gazebo";
	public static final Logger LOGGER = LoggerFactory.getLogger(MOD_ID);

	@Override
	public void onInitialize() {
		Config.load();
		SpawnGazebo.register();
		GazeboGuard.register();
		ModContainer mod = FabricLoader.getInstance().getModContainer(MOD_ID).orElseThrow();
		// The gazebo structures, the starter chest's loot table and the functions that hand out the kit.
		ResourceLoader.registerBuiltinPack(Identifier.fromNamespaceAndPath(MOD_ID, "spawn_gazebo_data"), mod,
				Component.translatable("pack.spawn_gazebo.data"), PackActivationType.ALWAYS_ENABLED);
		LOGGER.info("Spawn Gazebo: ready (gazebo {}, starter chest {} with {}, protected {} to {} blocks)",
				Config.spawnGazebo, Config.starterChest, Config.chestLoot, Config.protectGazebo, Config.protectedRadius);
	}
}
