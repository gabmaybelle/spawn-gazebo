package com.gabbymaybelle.spawngazebo;

import java.io.IOException;
import java.io.Reader;
import java.io.Writer;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.List;
import java.util.Locale;
import java.util.Properties;
import net.fabricmc.loader.api.FabricLoader;
import net.fabricmc.loader.api.ModContainer;
import net.fabricmc.loader.api.metadata.CustomValue;
import net.minecraft.resources.Identifier;

/**
 * Settings in {@code config/spawn_gazebo.properties}. Missing keys get their defaults, and the file
 * is only written when it's missing a setting (so players can see what exists) or the settings
 * screen changes one; a value that can't be read keeps its default and says so in the log.
 * Settings this version doesn't know are kept. Read at startup; the settings screen changes them
 * as you play.
 */
public final class Config {
	private static final Path FILE = FabricLoader.getInstance().getConfigDir().resolve(SpawnGazeboMod.MOD_ID + ".properties");
	/** Written to the file, so a later version can tell an older file apart if it ever needs to change one. */
	private static final int CONFIG_VERSION = 1;
	/** Every setting the file should have; the file is written when one is missing. */
	private static final List<String> KEYS = List.of("spawnGazebo", "starterChest", "chestLoot", "protectGazebo",
			"protectedRadius", "configVersion");
	/** The file as it was read, so settings from a newer version (or added by hand) survive a save. */
	private static Properties loaded = new Properties();

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
			return starterKitTable();
		}
		if (value.equals(BONUS_CHEST)) {
			return BONUS_CHEST_TABLE;
		}
		Identifier id = Identifier.tryParse(value);
		if (id == null) {
			SpawnGazeboMod.LOGGER.warn("Spawn Gazebo: chestLoot \"{}\" isn't a loot table id, using the starter kit", value);
			return starterKitTable();
		}
		return id;
	}

	/**
	 * The starter kit: Spawn Gazebo's own, unless a mod brings one of its own for the pack it's in.
	 * A mod says so in its fabric.mod.json: {@code "custom": {"spawn_gazebo": {"starter_kit": "<loot table id>"}}}.
	 * (Espresso Flavoured Core bundles this mod and fills the chest with its own kit this way.)
	 */
	public static Identifier starterKitTable() {
		for (ModContainer mod : FabricLoader.getInstance().getAllMods()) {
			CustomValue custom = mod.getMetadata().getCustomValue(SpawnGazeboMod.MOD_ID);
			if (custom == null || custom.getType() != CustomValue.CvType.OBJECT) {
				continue;
			}
			CustomValue table = custom.getAsObject().get("starter_kit");
			if (table == null || table.getType() != CustomValue.CvType.STRING) {
				continue;
			}
			Identifier id = Identifier.tryParse(table.getAsString());
			if (id == null) {
				SpawnGazeboMod.LOGGER.warn("Spawn Gazebo: {} names a starter kit that isn't a loot table id: \"{}\"",
						mod.getMetadata().getId(), table.getAsString());
				continue;
			}
			return id;
		}
		return STARTER_KIT_TABLE;
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
		loaded = props;
		// Only write when there's something to add: a new file or a new setting.
		if (!KEYS.stream().allMatch(props::containsKey)) {
			save();
		}
	}

	/** Writes every setting back to the file (called after loading and from the settings screen). */
	public static void save() {
		Properties props = new Properties();
		props.putAll(loaded);
		props.setProperty("spawnGazebo", Boolean.toString(spawnGazebo));
		props.setProperty("starterChest", Boolean.toString(starterChest));
		props.setProperty("chestLoot", chestLoot);
		props.setProperty("protectGazebo", Boolean.toString(protectGazebo));
		props.setProperty("protectedRadius", Integer.toString(protectedRadius));
		props.setProperty("configVersion", Integer.toString(CONFIG_VERSION));
		// Written to a temporary file first and then swapped in, so a crash part-way through never
		// leaves a half-written or empty settings file.
		Path temp = FILE.resolveSibling(FILE.getFileName() + ".tmp");
		try (Writer writer = Files.newBufferedWriter(temp)) {
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
			return;
		}
		try {
			try {
				Files.move(temp, FILE, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
			} catch (AtomicMoveNotSupportedException e) {
				Files.move(temp, FILE, StandardCopyOption.REPLACE_EXISTING);
			}
		} catch (IOException e) {
			SpawnGazeboMod.LOGGER.warn("Spawn Gazebo: couldn't write {}", FILE, e);
		}
	}

	private static boolean bool(Properties props, String key, boolean fallback) {
		String value = props.getProperty(key);
		if (value == null) {
			return fallback;
		}
		String v = value.trim().toLowerCase(Locale.ROOT);
		if (v.equals("true") || v.equals("false")) {
			return v.equals("true");
		}
		// A typo shouldn't quietly switch a setting off (the protection, say): keep the default and say so.
		SpawnGazeboMod.LOGGER.warn("Spawn Gazebo: {} should be true or false, not \"{}\"; using {}", key, value.trim(), fallback);
		return fallback;
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
