package com.gabbymaybelle.spawngazebo;

import java.io.IOException;
import java.io.Reader;
import java.io.Writer;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Properties;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.resources.Identifier;

/**
 * Settings in {@code config/spawn_gazebo.properties}. Missing keys get their defaults and the file
 * is rewritten with every key, so players can see what exists. Read at startup; the settings screen
 * changes them as you play.
 */
public final class Config {
	private static final Path FILE = FabricLoader.getInstance().getConfigDir().resolve(SpawnGazeboMod.MOD_ID + ".properties");
	/** Written to the file, so a later version can tell an older file apart if it ever needs to change one. */
	private static final int CONFIG_VERSION = 1;

	/** The mod's own kit: a bed, torches, bread, saplings and a bundle. */
	public static final String STARTER_KIT = "starter_kit";
	/** Vanilla's bonus chest loot: a few tools, logs, planks, food and seeds. */
	public static final String BONUS_CHEST = "bonus_chest";
	private static final Identifier STARTER_KIT_TABLE = Identifier.fromNamespaceAndPath(SpawnGazeboMod.MOD_ID, "chests/spawn_kit");
	private static final Identifier BONUS_CHEST_TABLE = Identifier.withDefaultNamespace("chests/spawn_bonus_chest");
	/** The gazebo itself reaches this far from its middle; the protected area can't be smaller. */
	public static final int MIN_RADIUS = SpawnGazebo.HALF, MAX_RADIUS = 64;

	/** A little round stone gazebo at the world spawn of every new world. Read when a world is created. */
	public static boolean spawnGazebo = true;
	/** A chest in the gazebo's railing with a starter kit, like vanilla's bonus chest. Read when a world is created. */
	public static boolean starterChest = true;
	/** What the chest holds: {@link #STARTER_KIT}, {@link #BONUS_CHEST}, or any loot table id. Read when a world is created. */
	public static String chestLoot = STARTER_KIT;
	/** Nobody outside creative can break or build in the gazebo, and explosions, fire and pistons leave it alone. */
	public static boolean protectGazebo = true;
	/** How far from the gazebo's middle the protection reaches, in blocks: 7 is the gazebo itself, more takes in the ground round it. */
	public static int protectedRadius = 12;

	private Config() {
	}

	/** The loot table the chest is filled from. An id that doesn't parse falls back to the starter kit. */
	public static Identifier chestLootTable() {
		String value = chestLoot.trim();
		if (value.isEmpty() || value.equals(STARTER_KIT)) {
			return STARTER_KIT_TABLE;
		}
		if (value.equals(BONUS_CHEST)) {
			return BONUS_CHEST_TABLE;
		}
		Identifier id = Identifier.tryParse(value);
		if (id == null) {
			SpawnGazeboMod.LOGGER.warn("Spawn Gazebo: chestLoot \"{}\" isn't a loot table id, using the starter kit", value);
			return STARTER_KIT_TABLE;
		}
		return id;
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
		chestLoot = props.getProperty("chestLoot", chestLoot).trim();
		protectGazebo = bool(props, "protectGazebo", protectGazebo);
		protectedRadius = Math.max(MIN_RADIUS, Math.min(MAX_RADIUS, integer(props, "protectedRadius", protectedRadius)));
		save();
	}

	/** Writes every setting back to the file (called after loading and from the settings screen). */
	public static void save() {
		Properties props = new Properties();
		props.setProperty("spawnGazebo", Boolean.toString(spawnGazebo));
		props.setProperty("starterChest", Boolean.toString(starterChest));
		props.setProperty("chestLoot", chestLoot);
		props.setProperty("protectGazebo", Boolean.toString(protectGazebo));
		props.setProperty("protectedRadius", Integer.toString(protectedRadius));
		props.setProperty("configVersion", Integer.toString(CONFIG_VERSION));
		try (Writer writer = Files.newBufferedWriter(FILE)) {
			props.store(writer, "Spawn Gazebo (also editable in game: Mod Menu > Spawn Gazebo > Config)\n"
					+ "spawnGazebo: a little round stone gazebo at the world spawn of new worlds\n"
					+ "starterChest: a chest in the gazebo's railing, like the bonus chest\n"
					+ "chestLoot: what's in it: starter_kit (a bed, torches, bread, saplings and a bundle), bonus_chest (vanilla's\n"
					+ "  bonus chest loot), or any loot table id such as minecraft:chests/village/village_plains_house\n"
					+ "protectGazebo: the gazebo can only be changed in creative, and explosions, fire and pistons leave it alone\n"
					+ "protectedRadius: how far from the gazebo's middle that reaches, in blocks (7 is the gazebo itself, up to 64)\n"
					+ "spawnGazebo, starterChest and chestLoot only matter when a world is created; the protection settings apply straight away");
		} catch (IOException e) {
			SpawnGazeboMod.LOGGER.warn("Spawn Gazebo: couldn't write {}", FILE, e);
		}
	}

	private static boolean bool(Properties props, String key, boolean fallback) {
		String value = props.getProperty(key);
		return value == null ? fallback : Boolean.parseBoolean(value.trim());
	}

	private static int integer(Properties props, String key, int fallback) {
		String value = props.getProperty(key);
		if (value == null) {
			return fallback;
		}
		try {
			return Integer.parseInt(value.trim());
		} catch (NumberFormatException e) {
			SpawnGazeboMod.LOGGER.warn("Spawn Gazebo: {} \"{}\" isn't a number, using {}", key, value.trim(), fallback);
			return fallback;
		}
	}
}
