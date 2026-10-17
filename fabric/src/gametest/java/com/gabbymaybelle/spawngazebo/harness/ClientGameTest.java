package com.gabbymaybelle.spawngazebo.harness;

/**
 * A test the {@link GameTestRunner} plays once the game reaches its title screen. Listed in the
 * test mod's {@code fabric.mod.json} under the {@code spawn_gazebo:gametest} entrypoint.
 */
public interface ClientGameTest {
	void runTest(ClientGameTestContext ctx);
}
