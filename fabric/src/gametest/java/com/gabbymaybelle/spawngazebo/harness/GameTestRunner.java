package com.gabbymaybelle.spawngazebo.harness;

import java.util.List;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.AccessibilityOnboardingScreen;
import net.minecraft.client.gui.screens.TitleScreen;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * The test harness: Fabric's client game test API doesn't exist for 1.21.1, so this is the
 * little of it the tests use. With the {@code spawn_gazebo.gametest} system property set
 * (the {@code runClientGameTest} task sets it), the tests run on their own thread once the title
 * screen is up; they drive the game through {@link ClientGameTestContext}, which hands work to
 * the client and server threads and waits on the client's ticks. When every test has passed the
 * game closes; a failure crashes it, so the Gradle task fails and the failure is in the log.
 */
public final class GameTestRunner implements ClientModInitializer {
	static final Logger LOGGER = LoggerFactory.getLogger("Spawn Gazebo test");
	private static final Object TICKS = new Object();
	private static long ticks;
	private boolean started;
	private Class<?> lastScreen;

	@Override
	public void onInitializeClient() {
		if (!Boolean.getBoolean("spawn_gazebo.gametest")) {
			return;
		}
		ClientTickEvents.END_CLIENT_TICK.register(mc -> {
			synchronized (TICKS) {
				ticks++;
				TICKS.notifyAll();
			}
			// The screens the game opens on its own are the first thing to look at when a run
			// goes wrong (a death, a disconnect), so each change is logged.
			Class<?> screen = mc.screen == null ? null : mc.screen.getClass();
			if (screen != lastScreen) {
				LOGGER.info("[test] screen: {}", screen == null ? "none" : screen.getSimpleName());
				lastScreen = screen;
			}
			// A fresh run directory (CI, say) opens on the accessibility onboarding screen the first
			// time; closing it records that and goes on to the title screen.
			if (!started && mc.getOverlay() == null && mc.screen instanceof AccessibilityOnboardingScreen onboarding) {
				onboarding.onClose();
				return;
			}
			if (!started && mc.getOverlay() == null && mc.screen instanceof TitleScreen) {
				started = true;
				// The window is rarely in front while the test runs; a pause would stop the server.
				mc.options.pauseOnLostFocus = false;
				start(mc);
			}
		});
	}

	private static void start(Minecraft mc) {
		List<ClientGameTest> tests = FabricLoader.getInstance().getEntrypoints("spawn_gazebo:gametest", ClientGameTest.class);
		Thread thread = new Thread(() -> {
			ClientGameTestContext ctx = new ClientGameTestContext(mc);
			try {
				for (ClientGameTest test : tests) {
					LOGGER.info("[test] running {}", test.getClass().getSimpleName());
					test.runTest(ctx);
				}
				LOGGER.info("[test] every test passed; closing the game");
				mc.execute(mc::stop);
			} catch (Throwable failure) {
				LOGGER.error("[test] FAILED", failure);
				// An Error thrown from a client task isn't caught by the task runner: the game
				// crashes, with this in the report, and the Gradle task fails on the exit code.
				mc.execute(() -> {
					throw new AssertionError("The client game test failed: " + failure.getMessage(), failure);
				});
			}
		}, "Client game test");
		thread.setDaemon(true);
		thread.start();
	}

	/** Blocks the calling (test) thread until the client has ticked this many more times. */
	static void awaitTicks(long count) {
		synchronized (TICKS) {
			long target = ticks + count;
			while (ticks < target) {
				try {
					TICKS.wait();
				} catch (InterruptedException e) {
					Thread.currentThread().interrupt();
					throw new IllegalStateException("interrupted while waiting for ticks", e);
				}
			}
		}
	}
}
