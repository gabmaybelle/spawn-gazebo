package com.gabbymaybelle.spawngazebo.harness;

import com.mojang.blaze3d.platform.InputConstants;
import java.lang.reflect.Method;
import java.util.function.Function;
import net.fabricmc.fabric.api.client.keybinding.v1.KeyBindingHelper;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.KeyboardHandler;
import net.minecraft.client.Minecraft;
import net.minecraft.client.MouseHandler;
import net.minecraft.client.Options;
import org.lwjgl.glfw.GLFW;

/**
 * Key presses, typing, the mouse and the window size, the way a player's input reaches the game:
 * through the game's own keyboard and mouse handlers, so screens and Fabric API's screen events
 * see them as in play. Nothing here touches the real mouse or keyboard: the handlers are called
 * directly (some are private, so by reflection), and the mouse position only exists in the game.
 */
public final class TestInput {
	private final ClientGameTestContext ctx;
	/** GLFW modifier bits held down for the next key presses (Shift). */
	private int modifiers;

	TestInput(ClientGameTestContext ctx) {
		this.ctx = ctx;
	}

	/** Presses and lets go of whatever key this key mapping is bound to. */
	public void pressKey(KeyMapping key) {
		InputConstants.Key bound = ctx.computeOnClient(mc -> KeyBindingHelper.getBoundKeyOf(key));
		pressKey(bound.getValue());
	}

	/** Presses one of the game's own keys, such as {@code options -> options.keyInventory}. */
	public void pressKey(Function<Options, KeyMapping> key) {
		KeyMapping mapping = ctx.computeOnClient(mc -> key.apply(mc.options));
		pressKey(mapping);
	}

	/** Presses and lets go of a key by its GLFW key code, then lets the game take a tick over it. */
	public void pressKey(int keyCode) {
		ctx.runOnClient(mc -> {
			long window = mc.getWindow().getWindow();
			mc.keyboardHandler.keyPress(window, keyCode, 0, GLFW.GLFW_PRESS, modifiers);
			mc.keyboardHandler.keyPress(window, keyCode, 0, GLFW.GLFW_RELEASE, modifiers);
		});
		ctx.waitTick();
	}

	/** Key presses from here on come with Shift held. */
	public void holdShift() {
		modifiers |= GLFW.GLFW_MOD_SHIFT;
	}

	public void releaseShift() {
		modifiers &= ~GLFW.GLFW_MOD_SHIFT;
	}

	/** Types text into whatever has the focus, a character at a time. */
	public void typeChars(String text) {
		text.codePoints().forEach(c -> ctx.runOnClient(mc -> invoke(mc.keyboardHandler, KeyboardHandler.class, "charTyped",
				new Class<?>[] { long.class, int.class, int.class }, mc.getWindow().getWindow(), c, 0)));
		ctx.waitTick();
	}

	public void typeChar(char c) {
		typeChars(String.valueOf(c));
	}

	/** Puts the mouse at a point in window pixels (not GUI units). */
	public void setCursorPos(double x, double y) {
		ctx.runOnClient(mc -> {
			Class<?>[] types = { long.class, double.class, double.class };
			// The first move after the window opens only sets the position, so move twice.
			invoke(mc.mouseHandler, MouseHandler.class, "onMove", types, mc.getWindow().getWindow(), x, y);
			invoke(mc.mouseHandler, MouseHandler.class, "onMove", types, mc.getWindow().getWindow(), x, y);
		});
	}

	/** Presses and lets go of a mouse button where the mouse is. */
	public void pressMouse(int button) {
		ctx.runOnClient(mc -> mouseButton(mc, button, GLFW.GLFW_PRESS));
		ctx.waitTick();
		ctx.runOnClient(mc -> mouseButton(mc, button, GLFW.GLFW_RELEASE));
	}

	private void mouseButton(Minecraft mc, int button, int action) {
		invoke(mc.mouseHandler, MouseHandler.class, "onPress", new Class<?>[] { long.class, int.class, int.class, int.class },
				mc.getWindow().getWindow(), button, action, modifiers);
	}

	/** Resizes the game window (in window pixels) and waits until the game has laid itself out again. */
	public void resizeWindow(int width, int height) {
		ctx.runOnClient(mc -> GLFW.glfwSetWindowSize(mc.getWindow().getWindow(), width, height));
		ctx.waitFor(mc -> mc.getWindow().getScreenWidth() == width && mc.getWindow().getScreenHeight() == height, 200);
		ctx.waitTicks(2);
	}

	private static void invoke(Object owner, Class<?> declaring, String name, Class<?>[] types, Object... args) {
		try {
			Method method = declaring.getDeclaredMethod(name, types);
			method.setAccessible(true);
			method.invoke(owner, args);
		} catch (ReflectiveOperationException e) {
			throw new AssertionError("couldn't call " + declaring.getSimpleName() + "." + name, e);
		}
	}
}
