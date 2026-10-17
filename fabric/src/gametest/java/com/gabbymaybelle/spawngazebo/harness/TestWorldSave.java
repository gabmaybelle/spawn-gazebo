package com.gabbymaybelle.spawngazebo.harness;

import net.minecraft.client.gui.screens.TitleScreen;

/** A world the test made and left, by its folder name, ready to open again (to check what was saved). */
public final class TestWorldSave {
	private final ClientGameTestContext ctx;
	private final String folder;

	TestWorldSave(ClientGameTestContext ctx, String folder) {
		this.ctx = ctx;
		this.folder = folder;
	}

	/** Opens the world again, the way the singleplayer screen does, and waits until the player is in it. */
	public TestSingleplayerContext open() {
		ctx.runOnClient(mc -> mc.createWorldOpenFlows().openWorld(folder, () -> mc.setScreen(new TitleScreen())));
		ctx.waitFor(mc -> mc.getSingleplayerServer() != null && mc.level != null && mc.player != null && mc.screen == null, 2400);
		return new TestSingleplayerContext(ctx, ctx.computeOnClient(mc -> new TestServerContext(mc.getSingleplayerServer())));
	}
}
