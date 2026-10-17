package com.gabbymaybelle.spawngazebo.harness;

import java.util.function.Consumer;
import net.minecraft.client.gui.screens.ConfirmScreen;
import net.minecraft.client.gui.screens.worldselection.CreateWorldScreen;
import net.minecraft.client.gui.screens.worldselection.WorldCreationUiState;
import net.minecraft.world.Difficulty;
import net.minecraft.world.level.levelgen.presets.WorldPresets;

/**
 * Makes a fresh singleplayer world through the game's own create-world screen: flat, survival,
 * peaceful, commands on and seed 1, much as Fabric's client game test does on 26.x. The
 * world's folder gets the time in its name, so a run never reopens a world an earlier run left.
 */
public final class TestWorldBuilder {
	private final ClientGameTestContext ctx;
	private Consumer<WorldCreationUiState> adjust = ui -> {};
	private boolean flat = true;

	TestWorldBuilder(ClientGameTestContext ctx) {
		this.ctx = ctx;
	}

	/** Changes to the settings, made after the defaults above. */
	public TestWorldBuilder adjustSettings(Consumer<WorldCreationUiState> adjust) {
		this.adjust = adjust;
		return this;
	}

	/** A normal world instead of a flat one (for screenshots with a view). */
	public TestWorldBuilder normalWorld() {
		flat = false;
		return this;
	}

	public TestSingleplayerContext create() {
		ctx.runOnClient(mc -> CreateWorldScreen.openFresh(mc, mc.screen));
		ctx.waitFor(mc -> mc.screen instanceof CreateWorldScreen, 1200);
		ctx.runOnClient(mc -> {
			WorldCreationUiState ui = ((CreateWorldScreen) mc.screen).getUiState();
			ui.setName("Spawn Gazebo test " + System.currentTimeMillis());
			ui.setGameMode(WorldCreationUiState.SelectedGameMode.SURVIVAL);
			ui.setDifficulty(Difficulty.PEACEFUL);
			ui.setAllowCommands(true);
			ui.setSeed("1");
			if (flat) {
				ui.getNormalPresetList().stream().filter(entry -> entry.preset().is(WorldPresets.FLAT)).findFirst().ifPresent(ui::setWorldType);
			}
			adjust.accept(ui);
		});
		ctx.clickScreenButton("selectWorld.create");
		// A mod with experimental features would put a warning in the way first.
		for (int waited = 0; waited < 2400; waited += 10) {
			if (ctx.computeOnClient(mc -> mc.screen instanceof ConfirmScreen)) {
				ctx.clickScreenButton("gui.yes");
			}
			if (ctx.computeOnClient(mc -> mc.getSingleplayerServer() != null && mc.level != null && mc.player != null && mc.screen == null)) {
				break;
			}
			ctx.waitTicks(10);
		}
		ctx.waitFor(mc -> mc.getSingleplayerServer() != null && mc.level != null && mc.player != null && mc.screen == null, 200);
		return new TestSingleplayerContext(ctx, ctx.computeOnClient(mc -> new TestServerContext(mc.getSingleplayerServer())));
	}
}
