package com.gabbymaybelle.spawngazebo.harness;

import net.minecraft.world.level.storage.LevelResource;

/** An open singleplayer world; closing it leaves for the title screen and waits for the server to stop. */
public final class TestSingleplayerContext implements AutoCloseable {
	private final ClientGameTestContext ctx;
	private final TestServerContext server;

	TestSingleplayerContext(ClientGameTestContext ctx, TestServerContext server) {
		this.ctx = ctx;
		this.server = server;
	}

	public TestServerContext getServer() {
		return server;
	}

	/** The world's folder, to open it again after this context closes. */
	public TestWorldSave getWorldSave() {
		String folder = server.computeOnServer(s -> s.getWorldPath(LevelResource.ROOT).toAbsolutePath().normalize().getFileName().toString());
		return new TestWorldSave(ctx, folder);
	}

	/** Waits until the chunk the player stands in has been built for rendering. */
	public void waitForChunksRender() {
		ctx.waitFor(mc -> mc.player != null && mc.levelRenderer.isSectionCompiled(mc.player.blockPosition()), 2400);
		ctx.waitTicks(20);
	}

	@Override
	public void close() {
		ctx.runOnClient(mc -> {
			if (mc.level != null) {
				mc.level.disconnect();
			}
			mc.disconnect();
		});
		ctx.waitFor(mc -> mc.getSingleplayerServer() == null && mc.level == null, 1200);
	}
}
