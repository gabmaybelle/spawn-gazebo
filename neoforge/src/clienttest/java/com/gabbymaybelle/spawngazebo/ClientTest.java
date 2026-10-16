package com.gabbymaybelle.spawngazebo;

import java.util.ArrayDeque;
import java.util.Deque;
import java.util.concurrent.CompletableFuture;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.function.Predicate;
import net.minecraft.client.Minecraft;
import net.minecraft.client.Screenshot;
import net.minecraft.client.server.IntegratedServer;
import net.minecraft.core.registries.Registries;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.Difficulty;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.LevelSettings;
import net.minecraft.world.level.WorldDataConfiguration;
import net.minecraft.world.level.levelgen.WorldOptions;
import net.minecraft.world.level.levelgen.presets.WorldPresets;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * A small step-by-step in-game test for the NeoForge jar, since NeoForge has no client game test
 * framework like Fabric's. It only runs in the `clientTest` run (`./gradlew :neoforge:runClientTest`),
 * which sets the system property below; this source set is never in the jar players get.
 *
 * <p>Each step runs on the game's own thread, one or more ticks at a time. A failed check throws,
 * which crashes the game with a crash report and a non-zero exit code, so Gradle (and CI) fail.
 * When every step has passed, the game closes normally. A watchdog ends a run that hangs.
 */
@EventBusSubscriber(modid = SpawnGazeboMod.MOD_ID, value = Dist.CLIENT)
final class ClientTest {
	static final boolean ENABLED = Boolean.getBoolean(SpawnGazeboMod.MOD_ID + ".clienttest");
	static final Logger LOGGER = LoggerFactory.getLogger("Spawn Gazebo test");
	/** A run that takes longer than this has hung (the game thread may be stuck, so a separate thread checks). */
	private static final long WATCHDOG_MILLIS = 10 * 60 * 1000;

	/** One step: returns true once it's done, and is called again next tick until then. */
	interface Step {
		boolean tick(Minecraft mc);
	}

	private static final Deque<Step> STEPS = new ArrayDeque<>();
	private static boolean started;
	private static boolean finished;

	private ClientTest() {
	}

	/** Runs once. */
	static void run(Consumer<Minecraft> action) {
		STEPS.add(mc -> {
			action.accept(mc);
			return true;
		});
	}

	/** Waits this many ticks. */
	static void waitTicks(int ticks) {
		int[] left = { ticks };
		STEPS.add(mc -> --left[0] <= 0);
	}

	/** Waits until the condition holds, failing after this many ticks. */
	static void waitUntil(String what, int timeoutTicks, Predicate<Minecraft> condition) {
		int[] waited = { 0 };
		STEPS.add(mc -> {
			if (condition.test(mc)) {
				return true;
			}
			if (++waited[0] > timeoutTicks) {
				throw new AssertionError("Timed out after " + timeoutTicks + " ticks waiting for: " + what);
			}
			return false;
		});
	}

	/**
	 * Runs this on the singleplayer server's thread and waits for it. It returns what went wrong
	 * (one line per problem), or an empty string when everything was right.
	 */
	static void onServer(String what, Function<MinecraftServer, String> check) {
		@SuppressWarnings("unchecked")
		CompletableFuture<String>[] result = new CompletableFuture[1];
		run(mc -> {
			IntegratedServer server = mc.getSingleplayerServer();
			if (server == null) {
				throw new AssertionError("No singleplayer server for: " + what);
			}
			result[0] = server.submit(() -> check.apply(server));
		});
		waitUntil(what + " on the server", 400, mc -> result[0].isDone());
		run(mc -> {
			String problems = result[0].join();
			check(problems.isEmpty(), what + ":" + problems);
		});
	}

	/** Runs a command on the singleplayer server as an operator, then gives it a few ticks to reach the client. */
	static void command(String command) {
		run(mc -> {
			IntegratedServer server = mc.getSingleplayerServer();
			if (server == null) {
				throw new AssertionError("No singleplayer server for the command: " + command);
			}
			server.execute(() -> server.getCommands().performPrefixedCommand(server.createCommandSourceStack(), command));
		});
		waitTicks(4);
	}

	/** Saves a screenshot in the run's screenshots folder (named by the time it was taken). */
	static void screenshot() {
		run(mc -> Screenshot.grab(mc, false));
		waitTicks(2);
	}

	/** Fails the run with this message unless the condition holds. */
	static void check(boolean condition, String message) {
		if (!condition) {
			throw new AssertionError(message);
		}
	}

	/**
	 * Creates a fresh flat creative world with commands on, and waits until the player is in it. The
	 * folder gets the time in its name, so a run never reopens a world an earlier run left behind
	 * (in the same game folder, perhaps on another Minecraft version).
	 */
	static void createWorld(String baseName) {
		String name = baseName + "-" + System.currentTimeMillis();
		waitUntil("the title screen", 2400, mc -> mc.gui.overlay() == null && mc.gui.screen() != null);
		run(mc -> mc.createWorldOpenFlows().createFreshLevel(name,
				new LevelSettings(name, GameType.CREATIVE, new LevelSettings.DifficultySettings(Difficulty.PEACEFUL, false, false), true,
						WorldDataConfiguration.DEFAULT),
				new WorldOptions(1L, false, false),
				registries -> registries.lookupOrThrow(Registries.WORLD_PRESET).getOrThrow(WorldPresets.FLAT).value().createWorldDimensions(),
				mc.gui.screen()));
		waitUntil("the player in the new world", 2400, mc -> mc.player != null && mc.level != null && mc.gui.screen() == null);
		// Time for the chunks around the player to arrive and draw.
		waitTicks(60);
	}

	@SubscribeEvent
	static void onTick(ClientTickEvent.Post event) {
		if (!ENABLED || finished) {
			return;
		}
		Minecraft mc = Minecraft.getInstance();
		if (!started) {
			started = true;
			LOGGER.info("Spawn Gazebo: client test starting");
			Thread watchdog = new Thread(() -> {
				try {
					Thread.sleep(WATCHDOG_MILLIS);
				} catch (InterruptedException e) {
					return;
				}
				LOGGER.error("Spawn Gazebo: client test hung for {} minutes; stopping", WATCHDOG_MILLIS / 60000);
				Runtime.getRuntime().halt(3);
			}, "Spawn Gazebo client test watchdog");
			watchdog.setDaemon(true);
			watchdog.start();
			SpawnGazeboClientTest.steps();
		}
		Step step = STEPS.peek();
		if (step == null) {
			finished = true;
			LOGGER.info("Spawn Gazebo: client test passed, every check");
			mc.stop();
			return;
		}
		if (step.tick(mc)) {
			STEPS.poll();
		}
	}
}
