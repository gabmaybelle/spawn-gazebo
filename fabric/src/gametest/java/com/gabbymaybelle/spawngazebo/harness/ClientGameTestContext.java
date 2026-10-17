package com.gabbymaybelle.spawngazebo.harness;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.function.Predicate;
import java.util.function.Supplier;
import net.minecraft.client.Minecraft;
import net.minecraft.client.Screenshot;
import net.minecraft.client.gui.components.AbstractButton;
import net.minecraft.client.gui.components.events.ContainerEventHandler;
import net.minecraft.client.gui.components.events.GuiEventListener;
import net.minecraft.client.gui.screens.ConnectScreen;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.TitleScreen;
import net.minecraft.client.multiplayer.ServerData;
import net.minecraft.client.multiplayer.resolver.ServerAddress;
import net.minecraft.network.chat.contents.TranslatableContents;

/** The test's handle on the game: work on the client thread, waits measured in client ticks, screens and screenshots. */
public final class ClientGameTestContext {
	private final Minecraft mc;
	private final TestInput input = new TestInput(this);

	ClientGameTestContext(Minecraft mc) {
		this.mc = mc;
	}

	/** Keys, typing, the mouse and the window. */
	public TestInput getInput() {
		return input;
	}

	public void waitTick() {
		waitTicks(1);
	}

	public void waitTicks(int ticks) {
		GameTestRunner.awaitTicks(ticks);
	}

	/** Waits, a tick at a time, until the client says so; an {@link AssertionError} when the ticks run out. */
	public void waitFor(Predicate<Minecraft> done, int maxTicks) {
		for (int waited = 0; ; waited++) {
			if (computeOnClient(done::test)) {
				return;
			}
			if (waited >= maxTicks) {
				throw new AssertionError("still waiting after " + maxTicks + " ticks");
			}
			waitTick();
		}
	}

	/** Waits up to a minute. */
	public void waitFor(Predicate<Minecraft> done) {
		waitFor(done, 1200);
	}

	/** Waits until this kind of screen is open, or with null until none is. */
	public <T extends Screen> void waitForScreen(Class<T> screen) {
		waitFor(mc -> screen == null ? mc.screen == null : screen.isInstance(mc.screen), 600);
	}

	/** Opens a screen (or none, with a supplier of null) on the client thread. */
	public void setScreen(Supplier<Screen> screen) {
		runOnClient(mc -> mc.setScreen(screen.get()));
	}

	public void runOnClient(Consumer<Minecraft> action) {
		computeOnClient(mc -> {
			action.accept(mc);
			return null;
		});
	}

	public <T> T computeOnClient(Function<Minecraft, T> action) {
		return mc.submit(() -> action.apply(mc)).join();
	}

	/** Presses the button on the open screen whose label is this translation key, or shows this text. */
	public void clickScreenButton(String translationKey) {
		runOnClient(mc -> {
			AbstractButton button = mc.screen == null ? null : findButton(mc.screen, translationKey);
			if (button == null) {
				throw new AssertionError("no button " + translationKey + " on " + (mc.screen == null ? "no screen" : mc.screen.getClass().getSimpleName()));
			}
			button.onPress();
		});
	}

	private static AbstractButton findButton(ContainerEventHandler container, String translationKey) {
		for (GuiEventListener child : container.children()) {
			if (child instanceof AbstractButton button && (button.getMessage().getContents() instanceof TranslatableContents contents
					&& contents.getKey().equals(translationKey) || button.getMessage().getString().equals(translationKey))) {
				return button;
			}
			if (child instanceof ContainerEventHandler nested) {
				AbstractButton found = findButton(nested, translationKey);
				if (found != null) {
					return found;
				}
			}
		}
		return null;
	}

	/** A screenshot of the last frame, as {@code screenshots/<name>.png} in the run directory; returns the file once written. */
	public Path takeScreenshot(String name) {
		Path file = mc.gameDirectory.toPath().resolve("screenshots").resolve(name + ".png");
		runOnClient(mc -> Screenshot.grab(mc.gameDirectory, name + ".png", mc.getMainRenderTarget(), message -> {}));
		// The file is written off the render thread: wait until it exists and has stopped growing.
		long size = -1;
		for (int i = 0; i < 200; i++) {
			waitTick();
			long now = Files.exists(file) ? file.toFile().length() : -1;
			if (now > 0 && now == size) {
				break;
			}
			size = now;
		}
		return file;
	}

	public TestWorldBuilder worldBuilder() {
		return new TestWorldBuilder(this);
	}

	/**
	 * Joins a multiplayer server (one already running, in offline mode, such as a dedicated test
	 * server) the way the multiplayer screen does, and waits until the player is in its world.
	 * Closing the result leaves for the title screen. It has no server of this game's own to
	 * reach, so {@link TestSingleplayerContext#getServer} is null.
	 */
	public TestSingleplayerContext connect(String address) {
		runOnClient(mc -> ConnectScreen.startConnecting(new TitleScreen(), mc, ServerAddress.parseString(address),
				new ServerData("Spawn Gazebo test server", address, ServerData.Type.OTHER), false, null));
		waitFor(mc -> mc.level != null && mc.player != null && mc.screen == null, 2400);
		return new TestSingleplayerContext(this, null);
	}
}
