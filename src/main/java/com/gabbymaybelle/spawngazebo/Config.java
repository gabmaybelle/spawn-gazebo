package com.gabbymaybelle.spawngazebo;

import java.io.IOException;
import java.io.Reader;
import java.io.Writer;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Properties;
import net.fabricmc.loader.api.FabricLoader;

/**
 * Settings in {@code config/spawn_gazebo.properties}. Missing keys get their defaults and the file
 * is rewritten with every key, so players can see what exists. Read at startup; the settings screen
 * changes them as you play.
 */
public final class Config {
	private static final Path FILE = FabricLoader.getInstance().getConfigDir().resolve(SpawnGazeboMod.MOD_ID + ".properties");
	/** Written to the file, so a later version can tell an older file apart if it ever needs to change one. */
	private static final int CONFIG_VERSION = 1;

	/** A little round stone gazebo at the world spawn of every new world. Read when a world is created. */
	public static boolean spawnGazebo = true;
	/** A chest in the gazebo's railing with a starter kit, like vanilla's bonus chest. Read when a world is created. */
	public static boolean starterChest = true;
	/** Nobody outside creative can break or build in the gazebo, and explosions, fire and pistons leave it alone. */
	public static boolean protectGazebo = true;

	private Config() {
	}

	static void load() {
		Properties props = new Properties();
		if (Files.exists(FILE)) {
			try (Reader reader = Files.newBufferedReader(FILE)) {
				props.load(reader);
			} catch (IOException e) {
				SpawnGazeboMod.LOGGER.warn("Spawn Gazebo: couldn't read {}, using defaults", FILE, e);
			}
		}
		spawnGazebo = bool(props, "spawnGazebo", spawnGazebo);
		starterChest = bool(props, "starterChest", starterChest);
		protectGazebo = bool(props, "protectGazebo", protectGazebo);
		save();
	}

	/** Writes every setting back to the file (called after loading and from the settings screen). */
	public static void save() {
		Properties props = new Properties();
		props.setProperty("spawnGazebo", Boolean.toString(spawnGazebo));
		props.setProperty("starterChest", Boolean.toString(starterChest));
		props.setProperty("protectGazebo", Boolean.toString(protectGazebo));
		props.setProperty("configVersion", Integer.toString(CONFIG_VERSION));
		try (Writer writer = Files.newBufferedWriter(FILE)) {
			props.store(writer, "Spawn Gazebo (also editable in game: Mod Menu > Spawn Gazebo > Config)\n"
					+ "spawnGazebo: a little round stone gazebo at the world spawn of new worlds\n"
					+ "starterChest: a chest in the gazebo with a bed, torches, bread, saplings and a bundle (like the bonus chest)\n"
					+ "protectGazebo: the gazebo can only be changed in creative, and explosions, fire and pistons leave it alone\n"
					+ "spawnGazebo and starterChest only matter when a world is created; protectGazebo applies straight away");
		} catch (IOException e) {
			SpawnGazeboMod.LOGGER.warn("Spawn Gazebo: couldn't write {}", FILE, e);
		}
	}

	private static boolean bool(Properties props, String key, boolean fallback) {
		String value = props.getProperty(key);
		return value == null ? fallback : Boolean.parseBoolean(value.trim());
	}
}
