package com.gabbymaybelle.spawngazebo;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.OptionalInt;
import java.util.Set;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.Holder;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.tags.BiomeTags;
import net.minecraft.tags.BlockTags;
import net.minecraft.tags.TagKey;
import net.minecraft.world.level.GameRules;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.level.biome.Biomes;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.ChestBlock;
import net.minecraft.world.level.block.DoublePlantBlock;
import net.minecraft.world.level.block.LeavesBlock;
import net.minecraft.world.level.block.Mirror;
import net.minecraft.world.level.block.Rotation;
import net.minecraft.world.level.block.entity.RandomizableContainerBlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.DoubleBlockHalf;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.level.levelgen.structure.BoundingBox;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructurePlaceSettings;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructureTemplate;

/**
 * The gazebo at the world spawn, built once when a world is created: a round
 * plinth, eight stripped-log pillars in the local wood carrying a ring of arches, and a green
 * copper dome with lanterns under its eaves. The template (tools/generate-spawn.js) comes in one
 * file per wood and per palette: the wood is whatever grows around spawn, and deserts and
 * badlands get sandstone. The ground inside the footprint is cleared and anything hanging over a
 * drop gets a stone foundation under it. The spawn point moves to the middle of the gazebo, so
 * new players wake up under the spore blossom. An operator can build one in a world that already
 * exists with {@code /spawngazebo build} ({@link GazeboCommand}).
 */
public final class SpawnGazebo {
	/** The template is 15 wide and 15 tall, centred on (7, y, 7), with its entrance facing south. */
	static final int HALF = 7, SIZE = 15;
	/** Where the starter kit chest is in the template (tools/generate-spawn.js puts it in the north opening; the railing takes its place when the chest is switched off). */
	private static final BlockPos CHEST = new BlockPos(HALF, 3, 2);
	/** Blocks cleared above the ground inside the footprint (trees, grass, snow). */
	private static final int CLEAR_HEIGHT = 18;
	/** How far down a foundation is laid under a block that would otherwise float. */
	private static final int FOUNDATION_DEPTH = 12;
	/** How far past the footprint a felled tree's canopy can reach (acacia and dark oak spread the widest). */
	private static final int CANOPY_REACH = 8;
	/** Lava is set to stone at least this far from the gazebo's middle, even when the protected radius is smaller. */
	private static final int LAVA_REACH = 16;
	/** How far under the surface lava counts as near enough to step or dig into. */
	private static final int LAVA_DEPTH = 4;
	/** A cap on how much lava one world's gazebo cools, in case spawn is by a lava lake. */
	private static final int MAX_LAVA = 8192;
	/** The woods tools/generate-spawn.js writes a template for. */
	private static final List<String> WOODS = List.of("oak", "spruce", "birch", "jungle", "acacia", "dark_oak",
			"mangrove", "cherry", "bamboo");
	private static final TagKey<Biome> C_IS_DESERT = TagKey.create(Registries.BIOME, ResourceLocation.fromNamespaceAndPath("c", "is_desert"));
	private static final TagKey<Biome> C_IS_BADLANDS = TagKey.create(Registries.BIOME, ResourceLocation.fromNamespaceAndPath("c", "is_badlands"));
	private static final TagKey<Biome> C_IS_SNOWY = TagKey.create(Registries.BIOME, ResourceLocation.fromNamespaceAndPath("c", "is_snowy"));

	private SpawnGazebo() {
	}

	/** The server has started: a brand-new world gets its gazebo, once. Each loader calls this before {@link GazeboGuard#onServerStarted}. */
	public static void onServerStarted(MinecraftServer server) {
		ServerLevel level = server.overworld();
		if (Platform.get().gazeboChecked(level)) {
			return;
		}
		Platform.get().markGazeboChecked(level);
		// Only a brand-new world: one that has never ticked. Worlds from before this feature keep their spawn as it is.
		if (!Config.spawnGazebo || level.getGameTime() > 0) {
			return;
		}
		try {
			BlockPos spawn = level.getSharedSpawnPos();
			build(level, spawn.getX(), spawn.getZ(), false);
		} catch (RuntimeException e) {
			SpawnGazeboMod.LOGGER.error("Spawn Gazebo: couldn't build the gazebo", e);
		}
	}

	/**
	 * Builds the gazebo centred on this column of the overworld, then moves the world spawn and the
	 * protection to it (an older gazebo stays standing, unprotected). {@code claimed}: the starter
	 * chest is for the players already here, so each newcomer is handed their own roll of the kit.
	 * Returns where players now wake up, or null when there's no template to build from.
	 */
	static BlockPos build(ServerLevel level, int cx, int cz, boolean claimed) {
		// The footprint and the tree survey can reach past the spawn chunks; loading is synchronous here.
		for (int dx = -3; dx <= 3; dx++) {
			for (int dz = -3; dz <= 3; dz++) {
				level.getChunk((cx >> 4) + dx, (cz >> 4) + dz);
			}
		}
		int ground = level.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, cx, cz) - 1;
		Holder<Biome> biome = level.getBiome(new BlockPos(cx, ground, cz));
		String palette = dry(biome) ? "sand" : "stone";
		String wood = wood(level, cx, cz, biome);
		ResourceLocation id = ResourceLocation.fromNamespaceAndPath(SpawnGazeboMod.MOD_ID, "spawn/gazebo_" + palette + "_" + wood);
		Optional<StructureTemplate> template = level.getStructureManager().get(id);
		if (template.isEmpty()) {
			SpawnGazeboMod.LOGGER.warn("Spawn Gazebo: no gazebo template {}", id);
			return null;
		}
		BlockPos origin = new BlockPos(cx - HALF, ground, cz - HALF);
		Rotation rotation = Rotation.getRandom(level.getRandom());
		clearAbove(level, origin);
		clearOrphanedCanopy(level, origin);
		StructurePlaceSettings settings = new StructurePlaceSettings()
				.setRotation(rotation).setRotationPivot(new BlockPos(HALF, 0, HALF))
				.setIgnoreEntities(true).setKnownShape(false);
		// No drops: a plant under the plinth whose other half or support goes would otherwise litter the floor.
		template.get().placeInWorld(level, origin, origin, settings, level.getRandom(), Block.UPDATE_CLIENTS | Block.UPDATE_SUPPRESS_DROPS);
		foundation(level, origin, palette.equals("sand") ? Blocks.SANDSTONE : Blocks.COBBLESTONE);
		coolLava(level, cx, ground, cz, palette.equals("sand") ? Blocks.SANDSTONE : Blocks.STONE);
		GazeboGuard.guard(level, new BoundingBox(origin.getX(), ground - GazeboGuard.DEPTH, origin.getZ(),
				origin.getX() + SIZE - 1, ground + GazeboGuard.HEIGHT, origin.getZ() + SIZE - 1));

		BlockPos middle = new BlockPos(cx, ground + 3, cz);
		level.setDefaultSpawnPos(middle, yaw(rotation));
		// Everyone starts in the gazebo itself rather than somewhere within ten blocks of it.
		level.getGameRules().getRule(GameRules.RULE_SPAWN_RADIUS).set(0, level.getServer());
		kitChest(level, origin, rotation, claimed);
		SpawnGazeboMod.LOGGER.info("Spawn Gazebo: built the gazebo ({} {}) at {}", palette, wood, middle);
		return middle;
	}

	/**
	 * The starter kit is in the chest in the railing (a loot chest, so Lootr can give every player
	 * their own copy). The starter kit function reads storage {@code spawn_gazebo:spawn} to know
	 * whether there is a chest, which loot table it rolls, and whether Lootr is here to share it
	 * out. With the chest switched off, the railing runs across where it would be.
	 */
	private static void kitChest(ServerLevel level, BlockPos origin, Rotation rotation, boolean claimed) {
		BlockPos pivot = new BlockPos(HALF, 0, HALF);
		BlockPos chest = origin.offset(StructureTemplate.transform(CHEST, Mirror.NONE, rotation, pivot));
		if (!(level.getBlockState(chest).getBlock() instanceof ChestBlock)) {
			SpawnGazeboMod.LOGGER.warn("Spawn Gazebo: no starter kit chest at {} in the gazebo", chest);
			return;
		}
		CompoundTag spawn = new CompoundTag();
		if (!Config.starterChest) {
			// The railing beside the chest, continued across its spot.
			BlockPos beside = origin.offset(StructureTemplate.transform(CHEST.west(), Mirror.NONE, rotation, pivot));
			BlockState rail = Block.updateFromNeighbourShapes(level.getBlockState(beside), level, chest);
			level.setBlock(chest, rail, Block.UPDATE_ALL);
			spawn.putBoolean("chest", false);
			level.getServer().getCommandStorage().set(ResourceLocation.fromNamespaceAndPath(SpawnGazeboMod.MOD_ID, "spawn"), spawn);
			return;
		}
		// The template's chest holds the starter kit; the setting can swap that for the bonus chest's loot or any other table.
		ResourceLocation table = Config.chestLootTable();
		if (level.getBlockEntity(chest) instanceof RandomizableContainerBlockEntity container) {
			container.setLootTable(ResourceKey.create(Registries.LOOT_TABLE, table));
		}
		boolean lootr = Platform.get().isModLoaded("lootr");
		if (lootr) {
			// Lootr converts loot chests it finds during world generation; this one came later, so ask it to look again.
			level.getServer().getCommands().performPrefixedCommand(level.getServer().createCommandSourceStack().withSuppressedOutput(),
					"execute positioned " + chest.getX() + " " + chest.getY() + " " + chest.getZ() + " run lootr force_chunk");
		}
		spawn.putBoolean("chest", true);
		// The function that hands the kit to later players rolls the same table (a macro reads "table").
		spawn.putString("table", table.toString());
		if (lootr) {
			spawn.putBoolean("lootr", true);
		}
		if (claimed) {
			// Built into a world people already play in: the chest is theirs, and newcomers get their own roll.
			spawn.putBoolean("claimed", true);
		}
		level.getServer().getCommandStorage().set(ResourceLocation.fromNamespaceAndPath(SpawnGazeboMod.MOD_ID, "spawn"), spawn);
	}

	/**
	 * Trees, plants, snow and overhanging ground above the footprint go, top down so nothing
	 * drops. A two-block plant (a lilac, tall grass) goes bottom half first: the game breaks the
	 * other half itself when one goes, dropping whatever it drops regardless of the no-drops
	 * flag, and only the bottom half has anything to drop.
	 */
	private static void clearAbove(ServerLevel level, BlockPos origin) {
		int flags = Block.UPDATE_CLIENTS | Block.UPDATE_SUPPRESS_DROPS;
		BlockPos.MutableBlockPos pos = new BlockPos.MutableBlockPos();
		for (int y = origin.getY() + CLEAR_HEIGHT; y > origin.getY(); y--) {
			for (int x = 0; x < SIZE; x++) {
				for (int z = 0; z < SIZE; z++) {
					pos.set(origin.getX() + x, y, origin.getZ() + z);
					BlockState state = level.getBlockState(pos);
					if (state.isAir()) {
						continue;
					}
					if (state.getBlock() instanceof DoublePlantBlock && state.getValue(DoublePlantBlock.HALF) == DoubleBlockHalf.UPPER) {
						level.setBlock(pos.below(), Blocks.AIR.defaultBlockState(), flags);
					}
					level.setBlock(pos, Blocks.AIR.defaultBlockState(), flags);
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
	private static void clearOrphanedCanopy(ServerLevel level, BlockPos origin) {
		List<BlockPos> orphans = new ArrayList<>();
		for (int x = -CANOPY_REACH; x < SIZE + CANOPY_REACH; x++) {
			for (int z = -CANOPY_REACH; z < SIZE + CANOPY_REACH; z++) {
				for (int y = 1; y <= CLEAR_HEIGHT + CANOPY_REACH; y++) {
					BlockPos pos = origin.offset(x, y, z);
					BlockState state = level.getBlockState(pos);
					if (state.getBlock() instanceof LeavesBlock && !state.getValue(LeavesBlock.PERSISTENT) && !hasTree(level, pos)) {
						orphans.add(pos);
					}
				}
			}
		}
		for (BlockPos pos : orphans) {
			level.setBlock(pos, Blocks.AIR.defaultBlockState(), Block.UPDATE_CLIENTS | Block.UPDATE_SUPPRESS_DROPS);
		}
		if (!orphans.isEmpty()) {
			SpawnGazeboMod.LOGGER.info("Spawn Gazebo: cleared {} leaves left hanging by the trees that stood at spawn", orphans.size());
		}
	}

	/** Whether a leaf still has a log within six leaves of it, counted the way the game does (through leaves only). */
	private static boolean hasTree(ServerLevel level, BlockPos leaf) {
		Set<BlockPos> seen = new HashSet<>(Set.of(leaf));
		List<BlockPos> frontier = List.of(leaf);
		for (int step = 1; step < LeavesBlock.DECAY_DISTANCE && !frontier.isEmpty(); step++) {
			List<BlockPos> next = new ArrayList<>();
			for (BlockPos pos : frontier) {
				for (Direction direction : Direction.values()) {
					BlockPos neighbour = pos.relative(direction);
					if (!seen.add(neighbour)) {
						continue;
					}
					// 0 for a log, a leaf's own distance for leaves, nothing for anything else.
					OptionalInt distance = LeavesBlock.getOptionalDistanceAt(level.getBlockState(neighbour));
					if (distance.isPresent() && distance.getAsInt() == 0) {
						return true;
					}
					if (distance.isPresent()) {
						next.add(neighbour);
					}
				}
			}
			frontier = next;
		}
		return false;
	}

	/** Stone under every block of the ground layer that has air, water or plants beneath it. */
	private static void foundation(ServerLevel level, BlockPos origin, Block stone) {
		BlockPos.MutableBlockPos pos = new BlockPos.MutableBlockPos();
		for (int x = 0; x < SIZE; x++) {
			for (int z = 0; z < SIZE; z++) {
				pos.set(origin.getX() + x, origin.getY(), origin.getZ() + z);
				if (level.getBlockState(pos).isAir()) {
					continue;
				}
				for (int y = origin.getY() - 1; y >= origin.getY() - FOUNDATION_DEPTH; y--) {
					pos.setY(y);
					BlockState state = level.getBlockState(pos);
					if (!state.getCollisionShape(level, pos).isEmpty() && state.getFluidState().isEmpty()) {
						break;
					}
					level.setBlock(pos, stone.defaultBlockState(), Block.UPDATE_CLIENTS | Block.UPDATE_SUPPRESS_DROPS);
				}
			}
		}
	}

	/**
	 * A new player's first steps out of the gazebo shouldn't be into a lava pool. Lava near the
	 * surface round the gazebo, over the protected area or at least {@link #LAVA_REACH} blocks
	 * from the middle, sets to stone, and so does the rest of any pool it belongs to, so no pool is
	 * left half cooled. Lava that was only flowing from it goes.
	 */
	private static void coolLava(ServerLevel level, int cx, int ground, int cz, Block stone) {
		int reach = Math.max(Config.protectedRadius, LAVA_REACH);
		// A pool is followed this far past the reach, within the chunks place() loaded.
		int poolReach = Math.min(reach + LAVA_REACH, 44);
		ArrayDeque<BlockPos> queue = new ArrayDeque<>();
		Set<BlockPos> seen = new HashSet<>();
		BlockPos.MutableBlockPos pos = new BlockPos.MutableBlockPos();
		for (int x = cx - reach; x <= cx + reach; x++) {
			for (int z = cz - reach; z <= cz + reach; z++) {
				// The ground can rise or fall away round the gazebo, so follow it in each column.
				int top = level.getHeight(Heightmap.Types.WORLD_SURFACE, x, z);
				int from = Math.min(top, ground) - LAVA_DEPTH;
				int to = Math.max(top, ground + 1);
				for (int y = from; y <= to; y++) {
					pos.set(x, y, z);
					if (isLava(level, pos) && seen.add(pos.immutable())) {
						queue.add(pos.immutable());
					}
				}
			}
		}
		int flags = Block.UPDATE_CLIENTS | Block.UPDATE_SUPPRESS_DROPS;
		int cooled = 0;
		while (!queue.isEmpty() && cooled < MAX_LAVA) {
			BlockPos lava = queue.poll();
			boolean source = level.getFluidState(lava).isSource();
			level.setBlock(lava, source ? stone.defaultBlockState() : Blocks.AIR.defaultBlockState(), flags);
			cooled++;
			for (Direction direction : Direction.values()) {
				BlockPos next = lava.relative(direction);
				if (Math.abs(next.getX() - cx) <= poolReach && Math.abs(next.getZ() - cz) <= poolReach
						&& next.getY() >= ground - 2 * LAVA_DEPTH && isLava(level, next) && seen.add(next)) {
					queue.add(next);
				}
			}
		}
		if (cooled > 0) {
			SpawnGazeboMod.LOGGER.info("Spawn Gazebo: set {} blocks of lava near the gazebo to stone", cooled);
		}
	}

	/** A lava block (flowing or still), not lava inside a waterlogged-style block. */
	private static boolean isLava(ServerLevel level, BlockPos pos) {
		return level.getBlockState(pos).is(Blocks.LAVA);
	}

	private static boolean dry(Holder<Biome> biome) {
		return biome.is(BiomeTags.IS_BADLANDS) || biome.is(BiomeTags.HAS_VILLAGE_DESERT) || biome.is(BiomeTags.HAS_DESERT_PYRAMID)
				|| biome.is(C_IS_DESERT) || biome.is(C_IS_BADLANDS);
	}

	/** The wood of the most common tree within 32 blocks of spawn, or the biome's usual wood when there are none. */
	private static String wood(ServerLevel level, int cx, int cz, Holder<Biome> biome) {
		Map<String, Integer> counts = new HashMap<>();
		BlockPos.MutableBlockPos pos = new BlockPos.MutableBlockPos();
		for (int x = cx - 32; x <= cx + 32; x += 2) {
			for (int z = cz - 32; z <= cz + 32; z += 2) {
				int top = level.getHeight(Heightmap.Types.WORLD_SURFACE, x, z) - 1;
				for (int y = top; y > top - 24 && y > level.getMinBuildHeight(); y--) {
					pos.set(x, y, z);
					BlockState state = level.getBlockState(pos);
					if (state.is(BlockTags.LOGS)) {
						String wood = woodOf(state);
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

	/** "minecraft:stripped_dark_oak_log" is dark_oak; only woods with a template count. */
	private static String woodOf(BlockState state) {
		ResourceLocation id = BuiltInRegistries.BLOCK.getKey(state.getBlock());
		if (!id.getNamespace().equals("minecraft")) {
			return null;
		}
		String path = id.getPath();
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
	static String biomeWood(Holder<Biome> biome) {
		if (biome.is(Biomes.CHERRY_GROVE)) {
			return "cherry";
		}
		if (biome.is(Biomes.MANGROVE_SWAMP)) {
			return "mangrove";
		}
		if (biome.is(Biomes.BAMBOO_JUNGLE)) {
			return "bamboo";
		}
		if (biome.is(Biomes.BIRCH_FOREST) || biome.is(Biomes.OLD_GROWTH_BIRCH_FOREST)) {
			return "birch";
		}
		if (biome.is(Biomes.DARK_FOREST)) {
			return "dark_oak";
		}
		if (biome.is(BiomeTags.IS_TAIGA) || biome.is(C_IS_SNOWY) || biome.is(Biomes.SNOWY_PLAINS) || biome.is(Biomes.ICE_SPIKES)
				|| biome.is(Biomes.SNOWY_SLOPES) || biome.is(Biomes.FROZEN_PEAKS) || biome.is(Biomes.JAGGED_PEAKS) || biome.is(Biomes.GROVE)
				|| biome.is(Biomes.SNOWY_BEACH)) {
			return "spruce";
		}
		if (biome.is(BiomeTags.IS_JUNGLE)) {
			return "jungle";
		}
		if (biome.is(BiomeTags.IS_SAVANNA)) {
			return "acacia";
		}
		if (dry(biome)) {
			return "jungle";
		}
		return "oak";
	}

	/** The entrance faces south in the template; this is the way a player should face to look out of it. */
	private static float yaw(Rotation rotation) {
		return switch (rotation) {
			case NONE -> 0f;
			case CLOCKWISE_90 -> 90f;
			case CLOCKWISE_180 -> 180f;
			case COUNTERCLOCKWISE_90 -> -90f;
		};
	}
}
