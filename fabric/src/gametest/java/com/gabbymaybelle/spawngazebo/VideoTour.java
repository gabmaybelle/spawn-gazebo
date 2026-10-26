package com.gabbymaybelle.spawngazebo;

import com.mojang.blaze3d.platform.InputConstants;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Random;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Predicate;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.gametest.v1.FabricClientGameTest;
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;
import net.fabricmc.fabric.api.client.gametest.v1.context.TestServerContext;
import net.fabricmc.fabric.api.client.gametest.v1.context.TestSingleplayerContext;
import net.fabricmc.fabric.api.client.gametest.v1.screenshot.TestScreenshotOptions;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.inventory.ContainerScreen;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Holder;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.Difficulty;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.level.biome.Biomes;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.client.gui.screens.worldselection.WorldCreationUiState;

/**
 * Films the listing video and the vertical Short: a screenshot every tick in a real world, at the
 * gazebo the world was given, then at gazebos built in a cherry grove, a birch forest and a desert.
 * The Short's scenes ("s_...") are filmed in a tall 1080x1920 window, the video's ("w_...") in a
 * 1920x1080 one. Frames, `actions.tsv` (what happened at which frame, for the sounds) and
 * `frames.tsv` (the cursor, when a screen is open) go in the game folder's `video_tour`. Only runs
 * with `./gradlew :fabric:runClientGameTest -PvideoTour`; tools/video turns the frames into videos.
 */
public class VideoTour implements FabricClientGameTest {
	/** Midday light for every scene but the last. */
	private static final int NOON = 6000;
	/** Late-afternoon light for the video's closing shot. */
	private static final int EVENING = 11600;

	/** A built gazebo: its middle column, the ground under it, and the way its entrance faces (a yaw). */
	private record Gazebo(String name, int cx, int cz, int ground, float yaw) {
		/** Out of the entrance, along the ground. */
		double fx() {
			return -Math.sin(Math.toRadians(yaw));
		}

		double fz() {
			return Math.cos(Math.toRadians(yaw));
		}
	}

	private ClientGameTestContext ctx;
	private TestSingleplayerContext world;
	private TestServerContext server;
	private Path dir;
	private String scene;
	private int frame;
	private final List<String> actions = new ArrayList<>();
	private final List<String> frames = new ArrayList<>();
	/** Small, uneven pauses (typing, the cursor), the same on every run. */
	private final Random jitter = new Random(31);
	private Gazebo home;
	private final List<Gazebo> woods = new ArrayList<>();
	/** Client ticks so far, logged with each frame so the assembly can check the pace. */
	private static final AtomicInteger TICKS = new AtomicInteger();

	/** `-PvideoScenes=s_creeper,w_creeper` films only those scenes (after the same set-up), to redo a few. */
	private static final List<String> ONLY = List.of(System.getProperty("spawngazebo.videoScenes", "").split(",")).stream()
			.filter(n -> !n.isBlank()).toList();

	private static boolean want(String scene) {
		return ONLY.isEmpty() || ONLY.contains(scene);
	}

	static boolean enabled() {
		// -PvideoShort2 films the second Short (ArrivalShort) instead.
		return Boolean.getBoolean("spawngazebo.videoTour") && !Boolean.getBoolean("spawngazebo.videoShort2") && !Boolean.getBoolean("spawngazebo.videoShort3") && !Boolean.getBoolean("spawngazebo.videoShort3Face")
				&& !Boolean.getBoolean("spawngazebo.videoListing");
	}

	@Override
	public void runTest(ClientGameTestContext context) {
		if (!enabled()) {
			return;
		}
		ctx = context;
		ClientTickEvents.END_CLIENT_TICK.register(mc -> TICKS.incrementAndGet());
		dir = FabricLoader.getInstance().getGameDir().resolve("video_tour");
		try {
			if (Files.isDirectory(dir)) {
				try (var old = Files.list(dir)) {
					for (Path file : old.toList()) {
						Files.delete(file);
					}
				}
			}
			Files.createDirectories(dir);
		} catch (IOException e) {
			throw new AssertionError(e);
		}
		try (TestSingleplayerContext w = greenWorld()) {
			film(w);
		} finally {
			// Even after a failed scene, so the scenes before it can still be used.
			try {
				Files.write(dir.resolve("actions.tsv"), actions);
				Files.write(dir.resolve("frames.tsv"), frames);
			} catch (IOException e) {
				throw new AssertionError(e);
			}
		}
	}

	private void film(TestSingleplayerContext w) {
		world = w;
		server = w.getServer();
		setUp();

		// The Short, tall.
		window(1080, 1920, 3);
		if (want("s_hook")) {
			hook();
		}
		if (want("s_chest")) {
			chest("s_chest");
		}
		if (want("s_creeper")) {
			creeper("s_creeper", 22, 1);
		}
		if (want("s_wood")) {
			woodShots("s_wood", 23, 0);
		}
		if (want("s_loop")) {
			loop();
		}

		// The video, wide.
		window(1920, 1080, 3);
		if (want("w_wake")) {
			wake();
		}
		if (want("w_orbit")) {
			orbit();
		}
		if (want("w_inside")) {
			inside();
		}
		if (want("w_chest")) {
			chest("w_chest");
		}
		if (want("w_protect")) {
			protect();
		}
		if (want("w_creeper")) {
			creeper("w_creeper", 20, 2);
		}
		if (want("w_wood")) {
			woodShots("w_wood", 19, 3);
		}
		if (want("w_build")) {
			build();
		}
		if (want("w_closing")) {
			closing();
		}
		server.runCommand("tick rate 20");
	}

	// ------------------------------------------------------------------ the set

	/**
	 * A new world whose own spawn gazebo stands among oaks in a forest or on plains, the way most
	 * new worlds start: seeds are tried in turn ({@code -PvideoSeed} first, when given) and the
	 * first that fits is kept. Every try is a real new world with the gazebo the mod gave it.
	 */
	private TestSingleplayerContext greenWorld() {
		List<String> seeds = new ArrayList<>();
		String given = System.getProperty("spawngazebo.videoSeed", "");
		if (!given.isEmpty()) {
			seeds.add(given);
		}
		for (int i = 1; i <= 12; i++) {
			seeds.add("spawn gazebo " + i);
		}
		for (String seed : seeds) {
			TestSingleplayerContext w = ctx.worldBuilder().setUseConsistentSettings(false).adjustSettings(state -> {
				state.setSeed(seed);
				state.setGameMode(WorldCreationUiState.SelectedGameMode.SURVIVAL);
				state.setDifficulty(Difficulty.PEACEFUL);
				state.setAllowCommands(true);
			}).create();
			String found = w.getServer().computeOnServer(s -> {
				ServerLevel level = s.overworld();
				BlockPos middle = level.getRespawnData().pos();
				return mostWood(level, middle.getX(), middle.getZ()) + " " + wood(level, middle);
			});
			log0("seed", seed + ": " + found);
			String[] parts = found.split(" ");
			if (parts[0].equals("oak") && GREEN.contains(parts[1])) {
				return w;
			}
			w.close();
		}
		throw new AssertionError("Failed: none of the seeds tried starts among oaks in a forest or on plains");
	}

	private static final List<String> GREEN = List.of("forest", "flower_forest", "plains", "sunflower_plains", "meadow");

	private void setUp() {
		server.runCommand("gamerule send_command_feedback false");
		server.runCommand("gamerule advance_time false");
		server.runCommand("gamerule advance_weather false");
		server.runCommand("gamerule spawn_mobs false");
		// No "has made the advancement" lines or pop-ups over the scenes.
		server.runCommand("gamerule show_advancement_messages false");
		server.runCommand("advancement grant @a everything");
		// The advancements' experience off again: a new player shows no level over the hotbar.
		server.runCommand("xp set @a 0 levels");
		server.runCommand("xp set @a 0 points");
		server.runCommand("weather clear");
		server.runCommand("time set " + NOON);
		chunks();
		home = server.computeOnServer(s -> {
			ServerLevel level = s.overworld();
			BlockPos middle = level.getRespawnData().pos();
			return new Gazebo(wood(level, middle), middle.getX(), middle.getZ(), middle.getY() - 3, level.getRespawnData().yaw());
		});
		log0("home", home.name() + " at " + home.cx() + " " + home.cz() + " facing " + home.yaw());
		woods.add(home);
		// The other woods, each in a real biome of its own, built the way the command builds them.
		addWood("cherry", Biomes.CHERRY_GROVE);
		addWood("birch", Biomes.BIRCH_FOREST);
		addWood("desert", Biomes.DESERT);
		// Only the newest gazebo is protected, so the protection goes back to home, as it is in a new
		// world (the same box SpawnGazebo.build hands GazeboGuard): the creeper and the pickaxe meet a
		// guarded gazebo.
		server.runOnServer(s -> GazeboGuard.guard(s.overworld(), new net.minecraft.world.level.levelgen.structure.BoundingBox(
				home.cx() - SpawnGazebo.HALF, home.ground() - GazeboGuard.DEPTH, home.cz() - SpawnGazebo.HALF,
				home.cx() - SpawnGazebo.HALF + SpawnGazebo.SIZE - 1, home.ground() + GazeboGuard.HEIGHT, home.cz() - SpawnGazebo.HALF + SpawnGazebo.SIZE - 1)));
		ctx.waitTicks(20);
		// Frames take longer to save than a tick, so the game runs at half speed while filming:
		// one frame is then one tick, and 20 frames a second plays back at the game's real pace.
		server.runCommand("tick rate 10");
	}

	/**
	 * Finds one of these biomes where the mod would pick this wood (the most common tree within 32
	 * blocks, as SpawnGazebo counts it; a desert gets sandstone whatever grows) and builds a gazebo
	 * there. Searches out from home in rings; skipped if no spot fits.
	 */
	@SafeVarargs
	private void addWood(String name, ResourceKey<Biome>... biomes) {
		BlockPos at = server.computeOnServer(s -> {
			ServerLevel level = s.overworld();
			for (int ring = 0; ring <= 3; ring++) {
				for (int dir = 0; dir < (ring == 0 ? 1 : 8); dir++) {
					double a = Math.PI / 4 * dir;
					BlockPos from = new BlockPos(home.cx() + (int) (Math.cos(a) * ring * 1200), 80, home.cz() + (int) (Math.sin(a) * ring * 1200));
					var found = level.findClosestBiome3d(b -> {
						for (ResourceKey<Biome> key : biomes) {
							if (b.is(key)) {
								return true;
							}
						}
						return false;
					}, from, 1600, 32, 64);
					if (found == null) {
						continue;
					}
					// A little way into the biome, so the trees around are its own.
					BlockPos p = found.getFirst();
					BlockPos spot = p.offset(Integer.signum(p.getX() - from.getX()) * 24, 0, Integer.signum(p.getZ() - from.getZ()) * 24);
					boolean fits = name.equals("desert")
							? level.getBiome(new BlockPos(spot.getX(), level.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, spot.getX(), spot.getZ()), spot.getZ())).is(Biomes.DESERT)
							: mostWood(level, spot.getX(), spot.getZ()).equals(name);
					SpawnGazeboMod.LOGGER.info("Spawn Gazebo video: {} at {} {} {}", name, spot.getX(), spot.getZ(), fits ? "fits" : "doesn't fit");
					if (fits) {
						return spot;
					}
				}
			}
			return null;
		});
		if (at == null) {
			log0("missing", name);
			return;
		}
		woods.add(buildAt(name, at.getX(), at.getZ()));
	}

	/** The wood of the most common tree within 32 blocks, counted the way SpawnGazebo counts it. */
	private static String mostWood(ServerLevel level, int cx, int cz) {
		java.util.Map<String, Integer> counts = new java.util.HashMap<>();
		// Generate the ground first: an unloaded chunk reads as empty.
		for (int x = (cx - 32) >> 4; x <= (cx + 32) >> 4; x++) {
			for (int z = (cz - 32) >> 4; z <= (cz + 32) >> 4; z++) {
				level.getChunk(x, z);
			}
		}
		BlockPos.MutableBlockPos pos = new BlockPos.MutableBlockPos();
		for (int x = cx - 32; x <= cx + 32; x += 2) {
			for (int z = cz - 32; z <= cz + 32; z += 2) {
				int top = level.getHeight(Heightmap.Types.WORLD_SURFACE, x, z) - 1;
				for (int y = top; y > top - 24 && y > level.getMinY(); y--) {
					pos.set(x, y, z);
					var state = level.getBlockState(pos);
					if (state.is(net.minecraft.tags.BlockTags.LOGS)) {
						String path = net.minecraft.core.registries.BuiltInRegistries.BLOCK.getKey(state.getBlock()).getPath().replace("stripped_", "");
						if (path.endsWith("_log")) {
							counts.merge(path.substring(0, path.length() - 4), 1, Integer::sum);
						}
					}
				}
			}
		}
		return counts.entrySet().stream().max(java.util.Map.Entry.comparingByValue()).map(java.util.Map.Entry::getKey).orElse("none");
	}

	/** Builds a gazebo centred on this column, as /spawngazebo build confirm does. */
	private Gazebo buildAt(String name, int x, int z) {
		Gazebo built = server.computeOnServer(s -> {
			ServerLevel level = s.overworld();
			BlockPos middle = SpawnGazebo.build(level, x, z, true);
			check(middle != null, "the " + name + " gazebo was built");
			return new Gazebo(wood(level, middle), middle.getX(), middle.getZ(), middle.getY() - 3, level.getRespawnData().yaw());
		});
		log0("built", built.name() + " at " + built.cx() + " " + built.cz());
		return built;
	}

	/** The biome the gazebo stands in, for the log. */
	private static String wood(ServerLevel level, BlockPos middle) {
		Holder<Biome> biome = level.getBiome(middle);
		return biome.unwrapKey().map(k -> k.identifier().getPath()).orElse("?");
	}

	// ------------------------------------------------------------------ the Short

	/** Frame 1: the finished gazebo at a fresh spawn, the camera easing in. */
	private void hook() {
		hud(false);
		spectator();
		start("s_hook");
		dolly(home, 60, 23, 20, -0.6, 0.6, 15, 13.5);
	}

	/** Back out to exactly where the hook starts, so a replay loops. */
	private void loop() {
		hud(false);
		spectator();
		start("s_loop");
		dolly(home, 30, 26, 23, 0, -0.6, 16.5, 15);
	}

	// ------------------------------------------------------------------ the video

	/** Waking up in it: a new player's own view from the middle, looking out of the entrance. */
	private void wake() {
		hud(true);
		survival();
		server.runCommand("clear @a");
		start("w_wake");
		look(home, 0, 0, 3, 12, 0, 4.6);
		ctx.waitTicks(4);
		for (int i = 0; i < 70; i++) {
			double t = ease(i / 69.0);
			turnHead(home, 12, -2.5 + 5 * t, 5.0 - 0.6 * t);
			record(1);
		}
	}

	/** A slow drift a quarter of the way round, the dome and the lanterns against the sky. */
	private void orbit() {
		hud(false);
		spectator();
		start("w_orbit");
		int n = 160;
		for (int i = 0; i < n; i++) {
			double t = ease(i / (n - 1.0));
			double angle = Math.toRadians(-35 + 80 * t);
			double d = 17 - 2 * t;
			standAround(home, angle, d, 13 - 3 * t, 0, 5);
			record(1);
		}
	}

	/** Inside: from the benches up to the ceiling and the spore blossom. */
	private void inside() {
		hud(false);
		spectator();
		start("w_inside");
		for (int i = 0; i < 100; i++) {
			double t = ease(i / 99.0);
			// From just inside the entrance, looking across at the benches, then up.
			look(home, 4, 0.3, 3.4, -6 + 3 * t, 0, 4 + 6 * t);
			record(1);
		}
	}

	/** A survival player at the pillar beside the entrance, swinging at it: it doesn't break. */
	private void protect() {
		hud(true);
		survival();
		server.runCommand("item replace entity @a weapon.mainhand with iron_pickaxe");
		// Off camera until the item's name above the hotbar has faded.
		ctx.waitTicks(45);
		start("w_protect");
		// Outside the entrance, one block to the side, looking at the first pillar.
		look(home, 8.5, 2.6, 1, 6, 2, 3);
		ctx.waitTicks(6);
		turnHead(home, 6, 2, 3);
		ctx.waitTicks(2);
		String target = ctx.computeOnClient(mc -> mc.hitResult instanceof net.minecraft.world.phys.BlockHitResult hit
				? mc.level.getBlockState(hit.getBlockPos()).getBlock().toString() : "nothing");
		log0("pickaxe aims at", target);
		record(12);
		log("mine", "start");
		ctx.getInput().holdKey(options -> options.keyAttack);
		record(54);
		ctx.getInput().releaseKey(options -> options.keyAttack);
		log("mine", "stop");
		record(14);
		server.runCommand("clear @a");
	}

	/** Building one with the command, in a desert, seen from the player's own eyes. */
	private void build() {
		// Beside the desert gazebo built for the wood shots, far enough off to stand clear of it.
		Gazebo first = woods.stream().filter(g -> g.name().equals("desert")).findFirst().orElse(null);
		if (first == null) {
			log0("missing", "desert for the build");
			return;
		}
		BlockPos desert = server.computeOnServer(s -> {
			ServerLevel level = s.overworld();
			for (int[] o : new int[][] { { 96, 0 }, { -96, 0 }, { 0, 96 }, { 0, -96 } }) {
				BlockPos p = new BlockPos(first.cx() + o[0], 80, first.cz() + o[1]);
				if (level.getBiome(p).is(Biomes.DESERT)) {
					return p;
				}
			}
			return null;
		});
		if (desert == null) {
			log0("missing", "a second desert spot");
			return;
		}
		// Generated first: an unloaded chunk reads as the bottom of the world.
		int y = server.computeOnServer(s -> {
			s.overworld().getChunk(desert.getX() >> 4, desert.getZ() >> 4);
			return s.overworld().getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, desert.getX(), desert.getZ());
		});
		hud(true);
		survival();
		server.runCommand(String.format(Locale.ROOT, "tp @a %d.5 %d %d.5 0 5", desert.getX(), y, desert.getZ()));
		ctx.waitTicks(20);
		chunks();
		// Command replies stay off: chat shows only what the player types (Gabby, 2026-10-19).
		start("w_build");
		record(10);
		type("/spawngazebo build");
		record(26);
		type("/spawngazebo build confirm");
		Gazebo before = woods.getLast();
		check(Math.abs(server.computeOnServer(s -> s.overworld().getRespawnData().pos().getX()) - desert.getX()) <= 2, "the command built the desert gazebo");
		log("built", "desert");
		// Cut as it's built: the command leaves whoever stands there inside the plinth, so the
		// video goes straight to the outside view.
		record(3);
		quiet();
		Gazebo sand = server.computeOnServer(s -> {
			ServerLevel level = s.overworld();
			BlockPos middle = level.getRespawnData().pos();
			return new Gazebo("desert", middle.getX(), middle.getZ(), middle.getY() - 3, level.getRespawnData().yaw());
		});
		check(sand != before, "a new gazebo");
		start("w_desert");
		hud(false);
		spectator();
		dolly(sand, 60, 19, 17, 2.5, 3.5, 4.4, 4.0);
	}

	/** The last shot: home in the late-afternoon light, held still, no caption. */
	private void closing() {
		server.runCommand("time set " + EVENING);
		hud(false);
		spectator();
		start("w_closing");
		dolly(home, 90, 26, 24, 2, 1, 16, 15.5);
		server.runCommand("time set " + NOON);
	}

	// ------------------------------------------------------------------ scenes for both

	/** A survival player opens the starter chest, looks over the kit, and closes it. */
	private void chest(String name) {
		hud(true);
		survival();
		// Bigger than the options allow (the game caps a 1080-wide window at 3), so the kit reads on a phone.
		ctx.runOnClient(mc -> mc.getWindow().setGuiScale(mc.getWindow().getWidth() < 1500 ? 5 : 4));
		server.runCommand("clear @a");
		// The real chest block, then two and a half blocks from it towards the middle, eyes on it.
		BlockPos chest = server.computeOnServer(s -> {
			ServerLevel level = s.overworld();
			for (BlockPos p : BlockPos.betweenClosed(home.cx() - 7, home.ground(), home.cz() - 7, home.cx() + 7, home.ground() + 6, home.cz() + 7)) {
				if (level.getBlockState(p).getBlock() instanceof net.minecraft.world.level.block.ChestBlock) {
					return p.immutable();
				}
			}
			return null;
		});
		check(chest != null, "the gazebo has its starter chest");
		double dx = home.cx() - chest.getX(), dz = home.cz() - chest.getZ(), len = Math.max(1, Math.hypot(dx, dz));
		server.runCommand(String.format(Locale.ROOT, "tp @a %.3f %d %.3f facing %.3f %.3f %.3f",
				chest.getX() + 0.5 + dx / len * 2.5, chest.getY(), chest.getZ() + 0.5 + dz / len * 2.5,
				chest.getX() + 0.5, chest.getY() + 0.5, chest.getZ() + 0.5));
		ctx.waitTicks(4);
		// The teleport's facing turns a survival player's head but not always their pitch; aim from the client too.
		ctx.getInput().lookAt(chest);
		ctx.waitTicks(3);
		check(ctx.computeOnClient(mc -> mc.hitResult instanceof net.minecraft.world.phys.BlockHitResult hit && hit.getBlockPos().equals(chest)),
				"the player is looking at the starter chest");
		start(name);
		record(14);
		ctx.getInput().pressKey(options -> options.keyUse);
		log("chest_open", "");
		until(mc -> mc.gui.screen() instanceof ContainerScreen);
		record(6);
		// The cursor wanders over the kit, unevenly, as a player's does.
		double[][] stops = { { 0.42, 0.33 }, { 0.50, 0.36 }, { 0.58, 0.31 }, { 0.47, 0.40 } };
		double[] from = { 0.62, 0.62 };
		for (double[] to : stops) {
			glide(from, to, 6 + jitter.nextInt(5));
			record(8 + jitter.nextInt(10));
			from = to;
		}
		ctx.getInput().pressKey(InputConstants.KEY_ESCAPE);
		log("chest_close", "");
		record(16);
		ctx.runOnClient(Minecraft::resizeGui);
	}

	/** A lit creeper beside the gazebo goes off; nothing of it is touched. */
	private void creeper(String name, double distance, double side) {
		hud(false);
		spectator();
		server.runCommand("difficulty normal");
		// In front of the entrance, beside the steps, inside the protected square: the open side, clear of trees.
		double sx = home.cx() + 0.5 + home.fx() * 9 - home.fz() * 4, sz = home.cz() + 0.5 + home.fz() * 9 + home.fx() * 4;
		int sy = server.computeOnServer(s -> s.overworld().getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, (int) Math.floor(sx), (int) Math.floor(sz)));
		// High in front of the entrance, as the opening shot is: the ground before the steps is clear of trees.
		look(home, distance, side, 14, 6, 2, 2);
		ctx.waitTicks(4);
		start(name);
		record(10);
		server.runCommand(String.format(Locale.ROOT, "summon creeper %.2f %d %.2f {ignited:1b,NoAI:1b}", sx, sy, sz));
		log("fuse", "");
		until(mc -> mc.level.getEntitiesOfClass(net.minecraft.world.entity.monster.Creeper.class,
				mc.player.getBoundingBox().inflate(64)).isEmpty() && frame > 20);
		log("boom", "");
		record(40);
		server.runCommand("difficulty peaceful");
		server.runCommand("kill @e[type=item]");
	}

	/** The same view of each wood, one second each, the camera drifting a little. */
	private void woodShots(String name, double distance, double side) {
		hud(false);
		spectator();
		start(name);
		for (Gazebo g : woods) {
			look(g, distance, side - 0.4, 13, 0, side * 0.2, 5);
			ctx.waitTicks(10);
			chunks();
			log("wood", g.name());
			for (int i = 0; i < 22; i++) {
				look(g, distance - i * 0.04, side - 0.4 + i * 0.04, 13, 0, side * 0.2, 5);
				record(1);
			}
		}
	}

	// ------------------------------------------------------------------ the camera

	/**
	 * Stands the camera relative to a gazebo: {@code out} blocks out of the entrance, {@code side}
	 * to the right as you face the entrance, {@code up} above the plinth's ground, looking at a point
	 * given the same way.
	 */
	private void look(Gazebo g, double out, double side, double up, double lookOut, double lookSide, double lookUp) {
		double x = g.cx() + 0.5 + g.fx() * out - g.fz() * side;
		double z = g.cz() + 0.5 + g.fz() * out + g.fx() * side;
		double lx = g.cx() + 0.5 + g.fx() * lookOut - g.fz() * lookSide;
		double lz = g.cz() + 0.5 + g.fz() * lookOut + g.fx() * lookSide;
		server.runCommand(String.format(Locale.ROOT, "tp @a %.3f %.3f %.3f facing %.3f %.3f %.3f",
				x, g.ground() + up, z, lx, g.ground() + lookUp, lz));
	}

	/**
	 * Turns a survival player's head towards a point (given as {@link #look} does) from the client:
	 * a teleport's facing doesn't always reach a survival player's pitch.
	 */
	private void turnHead(Gazebo g, double lookOut, double lookSide, double lookUp) {
		double lx = g.cx() + 0.5 + g.fx() * lookOut - g.fz() * lookSide;
		double lz = g.cz() + 0.5 + g.fz() * lookOut + g.fx() * lookSide;
		double ly = g.ground() + lookUp;
		ctx.runOnClient(mc -> {
			var eye = mc.player.getEyePosition();
			double dx = lx - eye.x, dy = ly - eye.y, dz = lz - eye.z;
			float yaw = (float) Math.toDegrees(Math.atan2(-dx, dz));
			float pitch = (float) -Math.toDegrees(Math.atan2(dy, Math.hypot(dx, dz)));
			mc.player.setYRot(yaw);
			mc.player.setXRot(pitch);
			mc.player.setYHeadRot(yaw);
		});
	}

	/** On a circle round the gazebo, {@code angle} from straight out of the entrance. */
	private void standAround(Gazebo g, double angle, double distance, double up, double lookOut, double lookUp) {
		look(g, Math.cos(angle) * distance, Math.sin(angle) * distance, up, lookOut, 0, lookUp);
	}

	/** Eases the camera straight in (or out) towards the entrance over {@code n} frames. */
	private void dolly(Gazebo g, int n, double fromOut, double toOut, double fromSide, double toSide, double fromUp, double toUp) {
		for (int i = 0; i < n; i++) {
			double t = ease(i / (n - 1.0));
			look(g, fromOut + (toOut - fromOut) * t, fromSide + (toSide - fromSide) * t, fromUp + (toUp - fromUp) * t, 0, 0, 5);
			if (i == 0) {
				// Long enough for the camera to arrive before the first frame.
				ctx.waitTicks(10);
				chunks();
			}
			record(1);
		}
	}

	private static double ease(double t) {
		return t * t * (3 - 2 * t);
	}

	/** Moves the cursor across an open screen, as fractions of the window, over a few frames. */
	private void glide(double[] from, double[] to, int steps) {
		for (int i = 1; i <= steps; i++) {
			double t = ease(i / (double) steps);
			double fx = from[0] + (to[0] - from[0]) * t, fy = from[1] + (to[1] - from[1]) * t;
			int[] size = ctx.computeOnClient(mc -> new int[] { mc.getWindow().getWidth(), mc.getWindow().getHeight() });
			ctx.getInput().setCursorPos(fx * size[0], fy * size[1]);
			record(1);
		}
	}

	/** Opens chat and types a command a key at a time with uneven pauses, then sends it. */
	private void type(String command) {
		ctx.getInput().pressKey(options -> options.keyChat);
		record(6);
		for (char c : command.toCharArray()) {
			ctx.getInput().typeChar(c);
			log("key", String.valueOf(c));
			record(1 + (jitter.nextInt(5) == 0 ? 2 + jitter.nextInt(3) : jitter.nextInt(2)));
		}
		record(10);
		ctx.getInput().pressKey(InputConstants.KEY_RETURN);
		log("enter", command);
		record(2);
	}

	private void spectator() {
		server.runCommand("gamemode spectator @a");
	}

	private void survival() {
		server.runCommand("gamemode survival @a");
	}

	private void hud(boolean show) {
		ctx.runOnClient(mc -> {
			if (mc.gui.hud.isHidden() == show) {
				mc.gui.hud.toggle();
			}
		});
	}

	// ------------------------------------------------------------------ filming

	private void start(String name) {
		quiet();
		scene = name;
		frame = 0;
	}

	/** Waits for the world to draw; a slow draw can take longer than the wait allows. */
	private void chunks() {
		try {
			world.getConnection().waitForChunksRender();
		} catch (AssertionError e) {
			SpawnGazeboMod.LOGGER.warn("Spawn Gazebo: video tour went on before every chunk had drawn");
		}
	}

	/** Sizes the window and the GUI, and checks both took. */
	private void window(int width, int height, int scale) {
		ctx.getInput().resizeWindow(width, height);
		ctx.waitTicks(3);
		ctx.runOnClient(mc -> {
			mc.options.guiScale().set(scale);
			mc.resizeGui();
		});
		ctx.waitTicks(2);
		String got = ctx.computeOnClient(mc -> mc.getWindow().getWidth() + "x" + mc.getWindow().getHeight());
		check(got.equals(width + "x" + height), "the window is " + width + "x" + height + " (it is " + got + ")");
	}

	private void record(int count) {
		for (int i = 0; i < count; i++) {
			shoot();
			ctx.waitTick();
		}
	}

	private void until(Predicate<Minecraft> done) {
		for (int i = 0; i < 200; i++) {
			if (ctx.computeOnClient(done::test)) {
				return;
			}
			shoot();
			ctx.waitTick();
		}
		throw new AssertionError("Failed: " + scene + " waited twenty seconds at frame " + frame);
	}

	private void shoot() {
		ctx.takeScreenshot(TestScreenshotOptions.of(String.format(Locale.ROOT, "%s_%04d", scene, frame)).disableCounterPrefix().withDestinationDir(dir));
		String cursor = ctx.computeOnClient(mc -> mc.gui.screen() == null ? "-\t-"
				: (int) mc.mouseHandler.xpos() + "\t" + (int) mc.mouseHandler.ypos());
		frames.add(scene + "\t" + frame + "\t" + cursor + "\t" + TICKS.get());
		frame++;
	}

	private void log(String kind, String what) {
		actions.add(scene + "\t" + frame + "\t" + kind + "\t" + what);
	}

	/** Notes about the set itself, before any scene. */
	private void log0(String kind, String what) {
		SpawnGazeboMod.LOGGER.info("Spawn Gazebo video: {} {}", kind, what);
		actions.add("set\t0\t" + kind + "\t" + what);
	}

	private void quiet() {
		ctx.runOnClient(mc -> {
			mc.gui.toastManager().clear();
			mc.gui.hud.getChat().clearMessages(false);
		});
	}

	private static void check(boolean ok, String what) {
		if (!ok) {
			throw new AssertionError("Failed: " + what);
		}
	}
}
