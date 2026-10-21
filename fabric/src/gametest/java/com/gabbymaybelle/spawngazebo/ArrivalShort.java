package com.gabbymaybelle.spawngazebo;

import com.mojang.blaze3d.platform.InputConstants;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import net.fabricmc.fabric.api.client.gametest.v1.FabricClientGameTest;
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;
import net.minecraft.client.gui.screens.inventory.ContainerScreen;
import net.minecraft.client.gui.screens.worldselection.WorldCreationUiState;
import net.minecraft.client.tutorial.TutorialSteps;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.server.level.ParticleStatus;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.Container;
import net.minecraft.world.Difficulty;
import net.minecraft.world.entity.monster.Creeper;
import net.minecraft.world.level.block.ChestBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;

/**
 * Films Spawn Gazebo's second Short, "the world-load reveal": the first frames of brand-new survival
 * worlds, each starting inside its own gazebo (oak on plains, then cherry, birch and a desert), and,
 * in third person, the starter chest and a creeper that can't hurt the gazebo. Every arrival is a
 * real new world made from its seed, filmed from the first frame the game draws once its loading
 * screen closes (26.3 has no fade-in; the land further out may still be drawing in). Runs only with
 * {@code ./gradlew :fabric:runClientGameTest -PvideoTour -PvideoShort2}; the kit's other -Pvideo...
 * flags work too ({@code -PvideoScout}, {@code -PvideoScenes=s_cherry}, {@code -PvideoSkin=<png>}).
 */
public class ArrivalShort implements FabricClientGameTest {
	private static final String PREFIX = "spawngazebo";

	/** A new world to arrive in: its seed, the biomes its spawn may be in, and the gazebo it must get. */
	private record Arrival(String seed, List<String> biomes, String wood, String palette) {
	}

	/** The world the chest and creeper scenes are filmed in, and the first arrival. */
	private static final Arrival HOME = new Arrival("spawn gazebo 1", List.of("plains", "sunflower_plains", "forest", "flower_forest", "meadow"), "oak", "stone");
	private static final Arrival CHERRY = new Arrival("-1909472536", List.of("cherry_grove", "meadow", "plains"), "cherry", "stone");
	private static final Arrival BIRCH = new Arrival("1605713702489912095", List.of("birch_forest", "old_growth_birch_forest"), "birch", "stone");
	private static final Arrival DESERT = new Arrival("spawn gazebo 62", List.of("desert"), "acacia", "sand");
	private static final Arrival LOOP = new Arrival("spawn gazebo 65", List.of("flower_forest"), "oak", "stone");

	private Film film;
	private ClientGameTestContext ctx;

	static boolean enabled() {
		return Film.on(PREFIX) && Boolean.getBoolean(PREFIX + ".videoShort2");
	}

	@Override
	public void runTest(ClientGameTestContext context) {
		if (!enabled()) {
			return;
		}
		ctx = context;
		film = new Film(context, PREFIX, "video_tour");
		// Tall from the title screen on, so every new world opens in the Short's own frame.
		film.window(1080, 1920, 3);
		film.take(() -> create(HOME.seed()), () -> { }, this::scenes);
	}

	private void scenes() {
		film.window(1080, 1920, 3);
		// Minimal particles, as a player filming would set them: a spore from the gazebo's blossom
		// drifting past the eye filled a quarter of the tall frame in the first scout.
		ctx.runOnClient(mc -> mc.options.particles().set(ParticleStatus.MINIMAL));
		if (film.scouting()) {
			film.scene("x_set_home", this::setHome);
		}
		// In the home world (the world's own gazebo), third person, as Gabby.
		film.scene("s_chest", this::chest);
		film.scene("s_creeper", this::creeper);
		// Each arrival makes its own new world, so these come last.
		film.scene("s_arrive", () -> arrive(HOME, 64, 30, 12, -7, -2));
		film.scene("s_cherry", () -> arrive(CHERRY, 22, -14, 6, -6, -2));
		film.scene("s_birch", () -> arrive(BIRCH, 22, 14, -6, -6, -2));
		film.scene("s_desert", () -> arrive(DESERT, 22, -14, 6, -6, -2));
		film.scene("s_loop", () -> arrive(LOOP, 76, -24, 4, -7, -2));
	}

	// ------------------------------------------------------------------ the worlds

	/** A brand-new survival world from this seed, the way a player makes one (peaceful, commands on). */
	private net.fabricmc.fabric.api.client.gametest.v1.context.TestSingleplayerContext create(String seed) {
		film.note("seed", seed);
		// No "use W A S D to move" pop-up over the first frames.
		ctx.runOnClient(mc -> mc.getTutorial().setStep(TutorialSteps.NONE));
		return ctx.worldBuilder().setUseConsistentSettings(false).adjustSettings(state -> {
			state.setSeed(seed);
			state.setGameMode(WorldCreationUiState.SelectedGameMode.SURVIVAL);
			state.setDifficulty(Difficulty.PEACEFUL);
			state.setAllowCommands(true);
		}).create();
	}

	/**
	 * Closes the world in hand and makes a new one from the arrival's seed, filming from the first
	 * frame the game draws in it: the view turns once ({@code yaw} and {@code pitch} offsets from the
	 * way the spawn faces, out of the entrance), so it moves from frame 1. Then checks the gazebo
	 * the mod built: the biome, the wood of its pillars and its stone.
	 */
	private void arrive(Arrival a, int frames, float yawFrom, float yawTo, float pitchFrom, float pitchTo) {
		if (film.world != null) {
			film.world.close();
			film.world = null;
		}
		film.hud(true);
		// The player's own eyes (the chest and creeper scenes leave third person on).
		ctx.runOnClient(mc -> mc.options.setCameraType(net.minecraft.client.CameraType.FIRST_PERSON));
		film.world = create(a.seed());
		film.server = film.world.getServer();
		film.immediate = true;
		// Before the first frame, and quick: replies off, the filming pace (one saved frame a tick).
		film.server.runCommand("gamerule send_command_feedback false");
		film.server.runCommand("tick rate " + film.prop("videoTickRate", "10"));
		// The cherry and loop gazebos stand below a slope; they look out the open way (take 1's loop faced a bank).
		float spawnYaw = a.wood().equals("cherry") ? towardsLeaves("cherry_leaves")
				: a == LOOP ? towardsLeaves("oak_leaves")
				: film.server.computeOnServer(s -> s.overworld().getRespawnData().yaw());
		film.log("arrive", a.seed());
		for (int i = 0; i < frames; i++) {
			// Fastest at the start, settling as it ends.
			double t = 1 - Math.pow(1 - i / (frames - 1.0), 2);
			float yaw = spawnYaw + (float) (yawFrom + (yawTo - yawFrom) * t);
			float pitch = (float) (pitchFrom + (pitchTo - pitchFrom) * t);
			look(yaw, pitch);
			film.quiet();
			film.record(1);
		}
		String[] got = gazebo();
		film.note("gazebo", a.seed() + ": " + String.join(" ", got));
		if (!a.biomes().contains("?")) {
			Film.check(a.biomes().contains(got[0]), a.seed() + " spawns in " + a.biomes() + " (it is " + got[0] + ")");
		}
		Film.check(got[1].equals(a.wood()), a.seed() + "'s gazebo has " + a.wood() + " pillars (they are " + got[1] + ")");
		Film.check(got[2].equals(a.palette()), a.seed() + "'s gazebo is " + a.palette() + " (it is " + got[2] + ")");
		BlockPos middle = film.server.computeOnServer(s -> s.overworld().getRespawnData().pos());
		Vec3 woke = ctx.computeOnClient(mc -> mc.player.position());
		Film.check(woke.distanceTo(Vec3.atBottomCenterOf(middle)) < 1.5, "the player woke up in the gazebo's middle (" + woke + ", the middle is " + middle.toShortString() + ")");
	}

	/**
	 * The way to face from the gazebo's middle to see the most of these leaves within 32 blocks
	 * (the direction of their middle), so a cherry arrival looks out at the blossom, not a hillside.
	 */
	private float towardsLeaves(String leaves) {
		return film.server.computeOnServer(s -> {
			ServerLevel level = s.overworld();
			BlockPos middle = level.getRespawnData().pos();
			double sx = 0, sz = 0;
			int n = 0;
			for (BlockPos p : BlockPos.betweenClosed(middle.offset(-32, -8, -32), middle.offset(32, 24, 32))) {
				if (BuiltInRegistries.BLOCK.getKey(level.getBlockState(p).getBlock()).getPath().equals(leaves)) {
					sx += p.getX() - middle.getX();
					sz += p.getZ() - middle.getZ();
					n++;
				}
			}
			float yaw = n == 0 ? level.getRespawnData().yaw() : (float) Math.toDegrees(Math.atan2(-sx, sz));
			// Cherry groves grow on slopes, so the gazebo can face a bank of earth: of the ways out
			// with open ground for 14 blocks (nothing over the floor), take the one that sees the most
			// of these leaves in its 30 degrees, not too far up or down.
			int floor = middle.getY();
			int best = -1;
			for (int d = 0; d < 24; d++) {
				double a = Math.toRadians(d * 15);
				double fx = -Math.sin(a), fz = Math.cos(a);
				boolean open = true;
				for (int r = 7; r <= 14 && open; r++) {
					int h = level.getHeight(net.minecraft.world.level.levelgen.Heightmap.Types.MOTION_BLOCKING_NO_LEAVES,
							middle.getX() + (int) Math.round(fx * r), middle.getZ() + (int) Math.round(fz * r));
					open = h <= floor + 1;
				}
				if (!open) {
					continue;
				}
				int seen = 0;
				for (BlockPos p : BlockPos.betweenClosed(middle.offset(-40, -6, -40), middle.offset(40, 14, 40))) {
					double dx = p.getX() - middle.getX(), dz = p.getZ() - middle.getZ(), dist = Math.hypot(dx, dz);
					if (dist < 8 || dist > 40) {
						continue;
					}
					double off = Math.toDegrees(Math.acos(Math.max(-1, Math.min(1, (dx * fx + dz * fz) / dist))));
					if (off <= 15 && BuiltInRegistries.BLOCK.getKey(level.getBlockState(p).getBlock()).getPath().equals(leaves)) {
						seen++;
					}
				}
				if (seen > best) {
					best = seen;
					yaw = d * 15;
				}
			}
			film.note("towards", leaves + ": " + n + " blocks in all; the most open view sees " + best + ", yaw " + Math.round(yaw));
			return yaw;
		});
	}

	/**
	 * The gazebo at the world spawn, read from its blocks: the biome at its middle, the wood its
	 * stripped-log pillars are made of, and its stone ("sand" for sandstone, as in a desert).
	 */
	private String[] gazebo() {
		return film.server.computeOnServer(s -> {
			ServerLevel level = s.overworld();
			BlockPos middle = level.getRespawnData().pos();
			String biome = level.getBiome(middle).unwrapKey().map(k -> k.identifier().getPath()).orElse("?");
			Map<String, Integer> woods = new HashMap<>();
			int sand = 0;
			for (BlockPos p : BlockPos.betweenClosed(middle.getX() - SpawnGazebo.HALF, middle.getY() - 3, middle.getZ() - SpawnGazebo.HALF,
					middle.getX() + SpawnGazebo.HALF, middle.getY() + 12, middle.getZ() + SpawnGazebo.HALF)) {
				String path = BuiltInRegistries.BLOCK.getKey(level.getBlockState(p).getBlock()).getPath();
				if (path.startsWith("stripped_") && path.endsWith("_log")) {
					woods.merge(path.substring("stripped_".length(), path.length() - "_log".length()), 1, Integer::sum);
				} else if (path.contains("sandstone")) {
					sand++;
				}
			}
			String wood = woods.entrySet().stream().max(Map.Entry.comparingByValue()).map(Map.Entry::getKey).orElse("none");
			return new String[] { biome, wood, sand > 20 ? "sand" : "stone", woods.toString(), "sandstone=" + sand };
		});
	}

	// ------------------------------------------------------------------ the home world

	/** Scouting only: the home gazebo from the entrance, so the chest and creeper sets can be judged. */
	private void setHome() {
		film.hud(false);
		film.spectator();
		Film.Spot home = home();
		film.camera(home.at(20, 1, 12), home.at(0, 0, 4));
		film.record(1);
	}

	/** Third person, as Gabby: she turns to the starter chest and opens it on its kit. */
	private void chest() {
		film.hud(false);
		film.survival();
		film.server.runCommand("clear @a");
		film.server.runCommand("time set 6000");
		Film.Spot home = home();
		BlockPos chest = film.server.computeOnServer(s -> {
			ServerLevel level = s.overworld();
			BlockPos middle = level.getRespawnData().pos();
			for (BlockPos p : BlockPos.betweenClosed(middle.offset(-7, -1, -7), middle.offset(7, 4, 7))) {
				if (level.getBlockState(p).getBlock() instanceof ChestBlock) {
					return p.immutable();
				}
			}
			return null;
		});
		Film.check(chest != null, "the gazebo has its starter chest");
		// Two and a half blocks from the chest towards the middle, facing it.
		double dx = home.x() - (chest.getX() + 0.5), dz = home.z() - (chest.getZ() + 0.5), len = Math.max(1, Math.hypot(dx, dz));
		Vec3 stand = new Vec3(chest.getX() + 0.5 + dx / len * 2.5, chest.getY(), chest.getZ() + 0.5 + dz / len * 2.5);
		Vec3 lid = Vec3.atCenterOf(chest);
		// Start turned a little away, so the view moves from frame 1 as she turns to it.
		Vec3 away = lid.add(dz / len * 2.2, 0.6, -dx / len * 2.2);
		film.server.runCommand(String.format(Locale.ROOT, "tp @a %.3f %.3f %.3f facing %.3f %.3f %.3f", stand.x, stand.y, stand.z, away.x, away.y, away.z));
		ctx.waitTicks(4);
		film.thirdPerson(true, 4.5);
		film.turnHead(away);
		film.bigGui();
		for (int i = 0; i < 14; i++) {
			film.turnHead(away.lerp(lid, Film.ease(i / 13.0)));
			film.record(1);
		}
		film.aim(chest);
		film.record(4);
		ctx.getInput().pressKey(o -> o.keyUse);
		film.log("chest_open", "");
		film.until(mc -> mc.gui.screen() instanceof ContainerScreen);
		film.record(6);
		// The cursor wanders over the kit, unevenly, as a player's does.
		double[][] stops = { { 0.42, 0.33 }, { 0.50, 0.36 }, { 0.58, 0.31 }, { 0.47, 0.40 } };
		double[] from = { 0.62, 0.62 };
		for (double[] to : stops) {
			film.glide(from, to, 6 + film.jitter.nextInt(5));
			film.record(6 + film.jitter.nextInt(8));
			from = to;
		}
		ctx.getInput().pressKey(InputConstants.KEY_ESCAPE);
		film.log("chest_close", "");
		film.record(8);
		film.normalGui();
		// The kit the chest handed out, from the mod's own loot table (data/spawn_gazebo/loot_table/chests/spawn_kit.json).
		String kit = film.server.computeOnServer(s -> {
			if (!(s.overworld().getBlockEntity(chest) instanceof Container box)) {
				return "no container";
			}
			Map<String, Integer> counts = new HashMap<>();
			for (int i = 0; i < box.getContainerSize(); i++) {
				counts.merge(BuiltInRegistries.ITEM.getKey(box.getItem(i).getItem()).getPath(), box.getItem(i).getCount(), Integer::sum);
			}
			return counts.getOrDefault("white_bed", 0) + " bed, " + counts.getOrDefault("torch", 0) + " torches, " + counts.getOrDefault("bread", 0) + " bread";
		});
		film.note("kit", kit);
		Film.check(kit.equals("1 bed, 16 torches, 6 bread"), "the starter chest held a bed, 16 torches and 6 bread (it held " + kit + ")");
	}

	/**
	 * Dusk, third person: Gabby stands on the lawn in front of the gazebo as a creeper beside the
	 * entrance pillar goes off; every protected block is the same afterwards. She stands more than
	 * six blocks from it, out of the blast's reach.
	 */
	private void creeper() {
		film.hud(false);
		film.survival();
		film.server.runCommand("clear @a");
		film.server.runCommand("time set 12800");
		film.server.runCommand("difficulty normal");
		Film.Spot home = home();
		// Inside, towards the back, looking out of the entrance at sunset (the first scout's framing);
		// the creeper on the lawn in the arch's opening, inside the protected ground (12 blocks from
		// the middle), about 12 blocks from her. The lawns round this gazebo are wooded or dip away,
		// so a camera out there saw trees or a bank (scouts 3-6).
		Vec3 stand = home.at(-1.5, -1.2, 0);
		Vec3 boom = home.at(9.5, 1.6, 0);
		int standY = (int) Math.floor(stand.y);
		int boomY = film.ground((int) Math.floor(boom.x), (int) Math.floor(boom.z));
		// The third-person camera looks over her head at what she faces, so she faces a point three
		// blocks to her side of the creeper: it then shows beside her head (the take hid it behind).
		Vec3 aside = home.at(9.5, -1.4, 0);
		Vec3 view = new Vec3(aside.x, boomY + 1.0, aside.z);
		Vec3 dome = view.add(0, 1.8, 0);
		// Full particles here, so the blast is the game's own cloud (minimal shrank it to a puff).
		ctx.runOnClient(mc -> mc.options.particles().set(ParticleStatus.ALL));
		film.server.runCommand(String.format(Locale.ROOT, "tp @a %.3f %d %.3f facing %.3f %.3f %.3f", stand.x, standY, stand.z, dome.x, dome.y, dome.z));
		ctx.waitTicks(4);
		film.thirdPerson(true, 3.5);
		film.turnHead(dome);
		// No random ticks while the blocks are compared: grass spreading onto a dirt block counted as
		// a change in the seventh scout.
		film.server.runCommand("gamerule random_tick_speed 0");
		List<String> before = guarded();
		film.note("guarded blocks", Integer.toString(before.size()));
		film.server.runCommand(String.format(Locale.ROOT, "summon creeper %.2f %d %.2f {NoAI:1b}", boom.x, boomY, boom.z));
		// The view eases from the dome down onto the creeper; the fuse is lit only once filming has begun.
		for (int i = 0; i < 12; i++) {
			film.turnHead(dome.lerp(view, Film.ease(i / 11.0)));
			film.record(1);
		}
		film.server.runCommand("execute as @e[type=minecraft:creeper] run data merge entity @s {ignited:1b}");
		film.log("fuse", "");
		film.until(mc -> mc.level.getEntitiesOfClass(Creeper.class, mc.player.getBoundingBox().inflate(64)).isEmpty());
		film.log("boom", "");
		film.record(44);
		List<String> after = guarded();
		int changed = 0;
		for (int i = 0; i < Math.min(before.size(), after.size()); i++) {
			if (!before.get(i).equals(after.get(i))) {
				changed++;
				film.note("changed", before.get(i) + " -> " + after.get(i).substring(after.get(i).indexOf('=') + 1));
			}
		}
		film.note("guarded blocks changed", Integer.toString(changed));
		Film.check(before.size() == after.size() && changed == 0, "every protected block is the same after the blast (" + changed + " changed)");
		Film.check(film.ctx.computeOnClient(mc -> mc.player.getHealth() >= mc.player.getMaxHealth()), "Gabby stood out of the blast's reach");
		film.server.runCommand("gamerule random_tick_speed 3");
		ctx.runOnClient(mc -> mc.options.particles().set(ParticleStatus.MINIMAL));
		film.server.runCommand("difficulty peaceful");
		film.calm();
	}

	/** Every block the gazebo's protection covers (the mod's own rule), as "position=state", in a fixed order. */
	private List<String> guarded() {
		return film.server.computeOnServer(s -> {
			ServerLevel level = s.overworld();
			BlockPos middle = level.getRespawnData().pos();
			List<String> out = new ArrayList<>();
			int r = Config.protectedRadius + 2;
			for (BlockPos p : BlockPos.betweenClosed(middle.offset(-r, -3 - GazeboGuard.DEPTH, -r), middle.offset(r, GazeboGuard.HEIGHT, r))) {
				if (GazeboGuard.guards(level, p)) {
					BlockState state = level.getBlockState(p);
					out.add(p.toShortString() + "=" + state);
				}
			}
			return out;
		});
	}

	/** The world spawn (the gazebo's middle, on its floor) and the way it faces, out of the entrance. */
	private Film.Spot home() {
		return film.server.computeOnServer(s -> {
			var data = s.overworld().getRespawnData();
			return new Film.Spot(data.pos().getX() + 0.5, data.pos().getY(), data.pos().getZ() + 0.5, data.yaw());
		});
	}

	/** Sets the view from the client, with no in-between turn left over for the frame to draw. */
	private void look(float yaw, float pitch) {
		ctx.runOnClient(mc -> {
			var p = mc.player;
			p.setYRot(yaw);
			p.yRotO = yaw;
			p.setXRot(pitch);
			p.xRotO = pitch;
			p.setYHeadRot(yaw);
			p.yHeadRotO = yaw;
			p.setYBodyRot(yaw);
			p.yBodyRotO = yaw;
		});
	}
}
