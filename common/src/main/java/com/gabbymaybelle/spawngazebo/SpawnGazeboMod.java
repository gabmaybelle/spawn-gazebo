package com.gabbymaybelle.spawngazebo;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Spawn Gazebo: new worlds start in a little round stone gazebo. {@link SpawnGazebo} builds it,
 * {@link GazeboGuard} keeps it standing, and the built-in data pack holds the structures and the
 * starter chest's kit.
 */
public final class SpawnGazeboMod {
	public static final String MOD_ID = "spawn_gazebo";
	public static final Logger LOGGER = LoggerFactory.getLogger(MOD_ID);

	private SpawnGazeboMod() {
	}

	/**
	 * Reads the settings. Each loader calls this once, after {@link Platform#set}, then registers its
	 * events (which call {@link SpawnGazebo} and {@link GazeboGuard}) and the built-in data pack
	 * (resourcepacks/spawn_gazebo_data: the gazebo structures, the starter chest's loot table and the
	 * functions that hand out the kit).
	 */
	public static void init() {
		Config.load();
		LOGGER.info("Spawn Gazebo: ready (gazebo {}, starter chest {} with {} ({}), protected {} to {} blocks)",
				Config.spawnGazebo, Config.starterChest, Config.chestLoot, Config.chestLootTable(), Config.protectGazebo, Config.protectedRadius);
	}
}
