package com.gabbymaybelle.spawngazebo.paper;

import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.Set;
import io.papermc.paper.registry.RegistryAccess;
import io.papermc.paper.registry.RegistryKey;
import io.papermc.paper.registry.TypedKey;
import io.papermc.paper.registry.keys.BiomeKeys;
import io.papermc.paper.registry.keys.GameRuleKeys;
import io.papermc.paper.registry.keys.tags.BiomeTagKeys;
import io.papermc.paper.registry.tag.TagKey;
import org.bukkit.Bukkit;
import org.bukkit.GameRule;
import org.bukkit.HeightMap;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.Registry;
import org.bukkit.Tag;
import org.bukkit.World;
import org.bukkit.block.Biome;
import org.bukkit.block.Block;
import org.bukkit.block.BlockFace;
import org.bukkit.block.Chest;
import org.bukkit.block.data.BlockData;
import org.bukkit.block.data.Levelled;
import org.bukkit.block.data.type.Leaves;
import org.bukkit.block.structure.Mirror;
import org.bukkit.block.structure.StructureRotation;
import org.bukkit.inventory.ItemStack;
import org.bukkit.loot.LootTable;
import org.bukkit.plugin.Plugin;
import org.bukkit.structure.Structure;

/**
 * The gazebo at the world spawn, built once when a world is created: a round plinth, eight
 * stripped-log pillars in the local wood carrying a ring of arches, and a green copper dome with
 * lanterns under its eaves. The template (tools/generate-spawn.js, shared with the mods) comes in
 * one file per wood and per palette: the wood is whatever grows around spawn, and deserts and
 * badlands get sandstone. The ground inside the footprint is cleared and anything hanging over a
 * drop gets a stone foundation under it. The spawn point moves to the middle of the gazebo, so new
 * players wake up under the spore blossom.
 */
final class GazeboBuilder {
	/** The template is 15 wide and 15 tall, centred on (7, y, 7), with its entrance facing south. */
	static final int HALF = 7, SIZE = 15;
	/** Where the starter kit chest is in the template: the north opening of the railing. */
	private static final int CHEST_X = HALF, CHEST_Y = 3, CHEST_Z = 2;
	/** How far below the ground the gazebo's box reaches, so nobody digs it out from underneath, and how far above (the lightning rod, with a little to spare). */
	static final int DEPTH = 3, HEIGHT = 15;
	/** Blocks cleared above the ground inside the footprint (trees, grass, snow). */
	private static final int CLEAR_HEIGHT = 18;
	/** How far down a foundation is laid under a block that would otherwise float. */
	private static final int FOUNDATION_DEPTH = 12;
	/** How far past the footprint a felled tree's canopy can reach (acacia and dark oak spread the widest). */
	private static final int CANOPY_REACH = 8;
	/** A leaf this far from a log (counted through leaves) has lost its tree; the game's own number. */
	private static final int DECAY_DISTANCE = 7;
	/** Lava is set to stone at least this far from the gazebo's middle. */
	private static final int LAVA_REACH = 16;
	/** How far under the surface lava counts as near enough to step or dig into. */
	private static final int LAVA_DEPTH = 4;
	/** A cap on how much lava one world's gazebo cools, in case spawn is by a lava lake. */
	private static final int MAX_LAVA = 8192;
	/** The woods tools/generate-spawn.js writes a template for. */
	private static final List<String> WOODS = List.of("oak", "spruce", "birch", "jungle", "acacia", "dark_oak",
			"mangrove", "cherry", "pale_oak", "bamboo", "poplar");

	/** The plugin's own kit: a bed, torches, bread, saplings and a bundle. */
	static final String STARTER_KIT = "starter_kit";
	/** Vanilla's bonus chest loot: a few tools, logs, planks, food and seeds. */
	static final String BONUS_CHEST = "bonus_chest";
	static final String BONUS_CHEST_TABLE = "minecraft:chests/spawn_bonus_chest";

	/** Templates already read from the jar, by file name. */
	private static final Map<String, Structure> TEMPLATES = new HashMap<>();

	/**
	 * What was built: where players now wake up, the chest (null with the chest off), the palette
	 * and wood, the box {@code [minX, minY, minZ, maxX, maxY, maxZ]}, and the kit the chest holds
	 * ({@link #STARTER_KIT} or a loot table id; null with the chest off).
	 */
	record Result(Location middle, Location chest, String palette, String wood, int[] box, String table) {
	}

	private GazeboBuilder() {
	}

	/**
	 * Builds the gazebo centred on this column of the world and moves the world spawn to it.
	 * {@code kit} is what the chest holds (see {@link Result#table()}), or null for no chest.
	 * Returns null when there's no template to build from.
	 */
	static Result build(Plugin plugin, World world, int cx, int cz, String kit) {
		// The footprint and the tree survey can reach past the spawn chunks; getChunkAt loads them.
		for (int dx = -3; dx <= 3; dx++) {
			for (int dz = -3; dz <= 3; dz++) {
				world.getChunkAt((cx >> 4) + dx, (cz >> 4) + dz);
			}
		}
		int ground = world.getHighestBlockYAt(cx, cz, HeightMap.MOTION_BLOCKING_NO_LEAVES);
		while (ground > world.getMinHeight() && world.getBlockAt(cx, ground, cz).isPassable()) {
			ground--;
		}
		Biome biome = world.getBiome(cx, ground, cz);
		String palette = dry(biome) ? "sand" : "stone";
		String wood = wood(world, cx, cz, biome);
		String name = "gazebo_" + palette + "_" + wood;
		Structure template = template(plugin, name);
		if (template == null) {
			plugin.getLogger().warning("Spawn Gazebo: no gazebo template " + name);
			return null;
		}
		Random random = new Random();
		StructureRotation rotation = StructureRotation.values()[random.nextInt(4)];
		clearAbove(world, cx, ground, cz);
		clearOrphanedCanopy(plugin, world, cx, ground, cz);
		// The structure API turns a template about its own corner, so the corner goes wherever puts the middle at (cx, cz).
		int[] middleOffset = rotate(HALF, HALF, rotation);
		Location origin = new Location(world, cx - middleOffset[0], ground, cz - middleOffset[1]);
		template.place(origin, false, rotation, Mirror.NONE, 0, 1.0f, random);
		Material stone = palette.equals("sand") ? Material.SANDSTONE : Material.COBBLESTONE;
		foundation(world, cx, ground, cz, stone);
		coolLava(plugin, world, cx, ground, cz, palette.equals("sand") ? Material.SANDSTONE : Material.STONE);

		Location middle = new Location(world, cx, ground + 3, cz);
		world.setSpawnLocation(cx, ground + 3, cz, yaw(rotation));
		// Everyone starts in the gazebo itself rather than somewhere within ten blocks of it.
		@SuppressWarnings("unchecked")
		GameRule<Integer> respawnRadius = (GameRule<Integer>) RegistryAccess.registryAccess().getRegistry(RegistryKey.GAME_RULE).getOrThrow(GameRuleKeys.RESPAWN_RADIUS);
		world.setGameRule(respawnRadius, 0);
		int[] chestOffset = rotate(CHEST_X, CHEST_Z, rotation);
		Location chest = new Location(world, origin.getBlockX() + chestOffset[0], ground + CHEST_Y, origin.getBlockZ() + chestOffset[1]);
		String table = kitChest(plugin, world, chest, kit);
		int[] box = {cx - HALF, ground - DEPTH, cz - HALF, cx + HALF, ground + HEIGHT, cz + HALF};
		return new Result(middle, table == null ? null : chest, palette, wood, box, table);
	}

	/** A template from the jar, read once. */
	private static Structure template(Plugin plugin, String name) {
		return TEMPLATES.computeIfAbsent(name, n -> {
			try (InputStream in = plugin.getResource("gazebo/" + n + ".nbt")) {
				return in == null ? null : Bukkit.getStructureManager().loadStructure(in);
			} catch (IOException e) {
				plugin.getLogger().warning("Spawn Gazebo: couldn't read the template " + n + ": " + e);
				return null;
			}
		});
	}

	/**
	 * Where a template block lands, relative to the template's corner, when the structure API turns
	 * the template about that corner (the game's own transform with the pivot at 0, 0).
	 */
	private static int[] rotate(int x, int z, StructureRotation rotation) {
		return switch (rotation) {
			case NONE -> new int[] {x, z};
			case CLOCKWISE_90 -> new int[] {-z, x};
			case CLOCKWISE_180 -> new int[] {-x, -z};
			case COUNTERCLOCKWISE_90 -> new int[] {z, -x};
		};
	}

	/**
	 * The starter kit goes in the chest in the railing. With the chest switched off, the railing
	 * runs across where it would be. Returns the kit the chest holds, or null without a chest.
	 */
	private static String kitChest(Plugin plugin, World world, Location at, String kit) {
		Block block = at.getBlock();
		if (!(block.getState() instanceof Chest chest)) {
			plugin.getLogger().warning("Spawn Gazebo: no starter kit chest at " + at.getBlockX() + " " + at.getBlockY() + " " + at.getBlockZ() + " in the gazebo");
			return null;
		}
		if (kit == null) {
			// The railing beside the chest, continued across its spot: the game shapes it to its neighbours.
			BlockData rail = null;
			for (BlockFace side : new BlockFace[] {BlockFace.EAST, BlockFace.WEST, BlockFace.NORTH, BlockFace.SOUTH}) {
				BlockData beside = block.getRelative(side).getBlockData();
				if (Tag.WALLS.isTagged(beside.getMaterial()) || Tag.FENCES.isTagged(beside.getMaterial())) {
					rail = beside.getMaterial().createBlockData();
					break;
				}
			}
			block.setBlockData(rail == null ? Material.AIR.createBlockData() : rail, true);
			return null;
		}
		// The template's chest names the mods' loot table, which this server doesn't have: fill it here instead.
		chest.setLootTable(null);
		chest.update(true, false);
		if (kit.equals(STARTER_KIT)) {
			Chest placed = (Chest) block.getState();
			for (ItemStack item : kitItems(STARTER_KIT)) {
				placed.getBlockInventory().addItem(item);
			}
			return STARTER_KIT;
		}
		LootTable table = Bukkit.getLootTable(NamespacedKey.fromString(kit));
		if (table == null) {
			plugin.getLogger().warning("Spawn Gazebo: no loot table " + kit + ", using the starter kit");
			return kitChest(plugin, world, at, STARTER_KIT);
		}
		Chest placed = (Chest) block.getState();
		placed.setLootTable(table);
		placed.update(true, false);
		return kit;
	}

	/** The kit handed to a player on their first join: the starter kit's items, or a roll of the loot table the chest was filled from. */
	static List<ItemStack> kitItems(String table) {
		if (table.equals(STARTER_KIT)) {
			return List.of(new ItemStack(Material.WHITE_BED), new ItemStack(Material.TORCH, 16), new ItemStack(Material.BREAD, 6),
					new ItemStack(Material.OAK_SAPLING, 4), new ItemStack(Material.BUNDLE));
		}
		LootTable loot = Bukkit.getLootTable(NamespacedKey.fromString(table));
		if (loot == null) {
			return kitItems(STARTER_KIT);
		}
		return new ArrayList<>(loot.populateLoot(new Random(), new org.bukkit.loot.LootContext.Builder(Bukkit.getWorlds().get(0).getSpawnLocation()).build()));
	}

	/**
	 * Trees, plants, snow and overhanging ground above the footprint go, top down and without
	 * physics, so nothing drops and no half of a two-block plant is left to break on its own.
	 */
	private static void clearAbove(World world, int cx, int ground, int cz) {
		for (int y = ground + CLEAR_HEIGHT; y > ground; y--) {
			for (int x = cx - HALF; x <= cx + HALF; x++) {
				for (int z = cz - HALF; z <= cz + HALF; z++) {
					Block block = world.getBlockAt(x, y, z);
					if (!block.getType().isAir()) {
						block.setType(Material.AIR, false);
					}
				}
			}
		}
	}

	/**
	 * The canopies of the trees that stood in the footprint are cut off from their trunks now, and
	 * the game would let them rot away over the next few minutes, scattering sticks and saplings
	 * round the gazebo. They go now instead, without drops: every natural leaf within reach that
	 * can't find a log within six leaves, which is how the game decides a leaf has lost its tree.
	 */
	private static void clearOrphanedCanopy(Plugin plugin, World world, int cx, int ground, int cz) {
		List<Block> orphans = new ArrayList<>();
		for (int x = cx - HALF - CANOPY_REACH; x <= cx + HALF + CANOPY_REACH; x++) {
			for (int z = cz - HALF - CANOPY_REACH; z <= cz + HALF + CANOPY_REACH; z++) {
				for (int y = ground + 1; y <= ground + CLEAR_HEIGHT + CANOPY_REACH; y++) {
					Block block = world.getBlockAt(x, y, z);
					if (block.getBlockData() instanceof Leaves leaves && !leaves.isPersistent() && !hasTree(block)) {
						orphans.add(block);
					}
				}
			}
		}
		for (Block block : orphans) {
			block.setType(Material.AIR, false);
		}
		if (!orphans.isEmpty()) {
			plugin.getLogger().info("Spawn Gazebo: cleared " + orphans.size() + " leaves left hanging by the trees that stood at spawn");
		}
	}

	/** Whether a leaf still has a log within six leaves of it, counted the way the game does (through leaves only). */
	private static boolean hasTree(Block leaf) {
		Set<Block> seen = new HashSet<>(Set.of(leaf));
		List<Block> frontier = List.of(leaf);
		for (int step = 1; step < DECAY_DISTANCE && !frontier.isEmpty(); step++) {
			List<Block> next = new ArrayList<>();
			for (Block block : frontier) {
				for (BlockFace face : new BlockFace[] {BlockFace.UP, BlockFace.DOWN, BlockFace.NORTH, BlockFace.SOUTH, BlockFace.EAST, BlockFace.WEST}) {
					Block neighbour = block.getRelative(face);
					if (!seen.add(neighbour)) {
						continue;
					}
					if (Tag.LOGS.isTagged(neighbour.getType())) {
						return true;
					}
					if (neighbour.getBlockData() instanceof Leaves) {
						next.add(neighbour);
					}
				}
			}
			frontier = next;
		}
		return false;
	}

	/** Stone under every block of the ground layer that has air, water or plants beneath it. */
	private static void foundation(World world, int cx, int ground, int cz, Material stone) {
		for (int x = cx - HALF; x <= cx + HALF; x++) {
			for (int z = cz - HALF; z <= cz + HALF; z++) {
				if (world.getBlockAt(x, ground, z).getType().isAir()) {
					continue;
				}
				for (int y = ground - 1; y >= ground - FOUNDATION_DEPTH; y--) {
					Block block = world.getBlockAt(x, y, z);
					if (!block.isPassable() && !block.isLiquid()) {
						break;
					}
					block.setType(stone, false);
				}
			}
		}
	}

	/**
	 * A new player's first steps out of the gazebo shouldn't be into a lava pool. Lava near the
	 * surface within {@link #LAVA_REACH} of the middle sets to stone, and so does the rest of any
	 * pool it belongs to, so no pool is left half cooled. Lava that was only flowing from it goes.
	 */
	private static void coolLava(Plugin plugin, World world, int cx, int ground, int cz, Material stone) {
		int reach = LAVA_REACH;
		// A pool is followed this far past the reach, within the chunks build() loaded.
		int poolReach = Math.min(reach + LAVA_REACH, 44);
		ArrayDeque<Block> queue = new ArrayDeque<>();
		Set<Block> seen = new HashSet<>();
		for (int x = cx - reach; x <= cx + reach; x++) {
			for (int z = cz - reach; z <= cz + reach; z++) {
				// The ground can rise or fall away round the gazebo, so follow it in each column.
				int top = world.getHighestBlockYAt(x, z, HeightMap.WORLD_SURFACE) + 1;
				int from = Math.min(top, ground) - LAVA_DEPTH;
				int to = Math.max(top, ground + 1);
				for (int y = from; y <= to; y++) {
					Block block = world.getBlockAt(x, y, z);
					if (block.getType() == Material.LAVA && seen.add(block)) {
						queue.add(block);
					}
				}
			}
		}
		int cooled = 0;
		while (!queue.isEmpty() && cooled < MAX_LAVA) {
			Block lava = queue.poll();
			boolean source = lava.getBlockData() instanceof Levelled level && level.getLevel() == 0;
			lava.setType(source ? stone : Material.AIR, false);
			cooled++;
			for (BlockFace face : new BlockFace[] {BlockFace.UP, BlockFace.DOWN, BlockFace.NORTH, BlockFace.SOUTH, BlockFace.EAST, BlockFace.WEST}) {
				Block next = lava.getRelative(face);
				if (Math.abs(next.getX() - cx) <= poolReach && Math.abs(next.getZ() - cz) <= poolReach
						&& next.getY() >= ground - 2 * LAVA_DEPTH && next.getType() == Material.LAVA && seen.add(next)) {
					queue.add(next);
				}
			}
		}
		if (cooled > 0) {
			plugin.getLogger().info("Spawn Gazebo: set " + cooled + " blocks of lava near the gazebo to stone");
		}
	}

	private static boolean is(Biome biome, TypedKey<Biome> key) {
		return biome.getKey().asString().equals(key.key().asString());
	}

	private static boolean in(Biome biome, TagKey<Biome> tag) {
		Registry<Biome> biomes = RegistryAccess.registryAccess().getRegistry(RegistryKey.BIOME);
		return biomes.hasTag(tag) && biomes.getTagValues(tag).contains(biome);
	}

	private static boolean dry(Biome biome) {
		return in(biome, BiomeTagKeys.IS_BADLANDS) || in(biome, BiomeTagKeys.HAS_STRUCTURE_VILLAGE_DESERT) || in(biome, BiomeTagKeys.HAS_STRUCTURE_DESERT_PYRAMID);
	}

	/** The wood of the most common tree within 32 blocks of spawn, or the biome's usual wood when there are none. */
	private static String wood(World world, int cx, int cz, Biome biome) {
		Map<String, Integer> counts = new HashMap<>();
		for (int x = cx - 32; x <= cx + 32; x += 2) {
			for (int z = cz - 32; z <= cz + 32; z += 2) {
				int top = world.getHighestBlockYAt(x, z, HeightMap.WORLD_SURFACE);
				for (int y = top; y > top - 24 && y > world.getMinHeight(); y--) {
					Material material = world.getBlockAt(x, y, z).getType();
					if (Tag.LOGS.isTagged(material)) {
						String wood = woodOf(material);
						if (wood != null) {
							counts.merge(wood, 1, Integer::sum);
						}
					}
				}
			}
		}
		return counts.entrySet().stream()
				.sorted(Map.Entry.<String, Integer>comparingByValue().reversed().thenComparing(Map.Entry.comparingByKey()))
				.map(Map.Entry::getKey).findFirst()
				.orElseGet(() -> biomeWood(biome));
	}

	/** "stripped_dark_oak_log" is dark_oak; only woods with a template count. */
	private static String woodOf(Material material) {
		String path = material.getKey().getKey();
		if (path.startsWith("stripped_")) {
			path = path.substring("stripped_".length());
		}
		String wood;
		if (path.endsWith("_log")) {
			wood = path.substring(0, path.length() - "_log".length());
		} else if (path.endsWith("_wood")) {
			wood = path.substring(0, path.length() - "_wood".length());
		} else if (path.equals("bamboo_block")) {
			wood = "bamboo";
		} else {
			return null;
		}
		return WOODS.contains(wood) ? wood : null;
	}

	/** The wood a spawn with no trees nearby gets: the tree its biome is known for. */
	static String biomeWood(Biome biome) {
		if (is(biome, BiomeKeys.CHERRY_GROVE)) {
			return "cherry";
		}
		if (is(biome, BiomeKeys.PALE_GARDEN)) {
			return "pale_oak";
		}
		if (is(biome, BiomeKeys.MANGROVE_SWAMP)) {
			return "mangrove";
		}
		if (is(biome, BiomeKeys.BAMBOO_JUNGLE)) {
			return "bamboo";
		}
		if (is(biome, BiomeKeys.BIRCH_FOREST) || is(biome, BiomeKeys.OLD_GROWTH_BIRCH_FOREST)) {
			return "birch";
		}
		if (is(biome, BiomeKeys.DARK_FOREST)) {
			return "dark_oak";
		}
		if (in(biome, BiomeTagKeys.IS_TAIGA) || is(biome, BiomeKeys.SNOWY_PLAINS) || is(biome, BiomeKeys.ICE_SPIKES)
				|| is(biome, BiomeKeys.SNOWY_SLOPES) || is(biome, BiomeKeys.FROZEN_PEAKS) || is(biome, BiomeKeys.JAGGED_PEAKS)
				|| is(biome, BiomeKeys.GROVE) || is(biome, BiomeKeys.SNOWY_BEACH) || is(biome, BiomeKeys.SNOWY_TAIGA)) {
			return "spruce";
		}
		if (in(biome, BiomeTagKeys.IS_JUNGLE)) {
			return "jungle";
		}
		if (in(biome, BiomeTagKeys.IS_SAVANNA)) {
			return "acacia";
		}
		if (dry(biome)) {
			return "jungle";
		}
		return "oak";
	}

	/** The entrance faces south in the template; this is the way a player should face to look out of it. */
	private static float yaw(StructureRotation rotation) {
		return switch (rotation) {
			case NONE -> 0f;
			case CLOCKWISE_90 -> 90f;
			case CLOCKWISE_180 -> 180f;
			case COUNTERCLOCKWISE_90 -> -90f;
		};
	}
}
