package com.gabbymaybelle.spawngazebo.harness;

import java.util.function.Consumer;
import java.util.function.Function;
import net.minecraft.server.MinecraftServer;

/** Work on the integrated server's thread, waited for. */
public final class TestServerContext {
	private final MinecraftServer server;

	TestServerContext(MinecraftServer server) {
		this.server = server;
	}

	/** Runs a command as the server (an operator, so every command works), and waits until it has run. */
	public void runCommand(String command) {
		runOnServer(s -> s.getCommands().performPrefixedCommand(s.createCommandSourceStack(), command));
	}

	public void runOnServer(Consumer<MinecraftServer> action) {
		computeOnServer(server -> {
			action.accept(server);
			return null;
		});
	}

	public <T> T computeOnServer(Function<MinecraftServer, T> action) {
		return server.submit(() -> action.apply(server)).join();
	}
}
