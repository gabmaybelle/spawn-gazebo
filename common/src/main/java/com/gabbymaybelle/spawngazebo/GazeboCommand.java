package com.gabbymaybelle.spawngazebo;

import com.mojang.brigadier.CommandDispatcher;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.levelgen.structure.BoundingBox;

/**
 * {@code /spawngazebo build}, for operators: a gazebo in a world that already exists, centred where
 * you stand. The first run says what it will do; {@code /spawngazebo build confirm} within 30
 * seconds builds it there, and the world spawn and the protection move to it.
 */
public final class GazeboCommand {
	/** How long a {@code build} waits for its {@code confirm}, in ticks. */
	static final int CONFIRM_TICKS = 30 * 20;
	/** The server console and command blocks have no UUID; they share this one. */
	private static final UUID NO_ENTITY = new UUID(0, 0);

	/** Where each operator asked to build, and until when (game time) the confirm counts. */
	private record Pending(BlockPos at, long until) {
	}

	private static final Map<UUID, Pending> PENDING = new HashMap<>();

	private GazeboCommand() {
	}

	/** Each loader calls this when commands are registered. */
	public static void register(CommandDispatcher<CommandSourceStack> dispatcher) {
		dispatcher.register(Commands.literal("spawngazebo")
				.requires(source -> source.hasPermission(Commands.LEVEL_GAMEMASTERS))
				.then(Commands.literal("build")
						.executes(context -> ask(context.getSource()))
						.then(Commands.literal("confirm").executes(context -> confirm(context.getSource())))));
	}

	/** The first step: say what will happen here, and wait for the confirm. */
	static int ask(CommandSourceStack source) {
		ServerLevel level = source.getLevel();
		if (level.dimension() != Level.OVERWORLD) {
			source.sendFailure(Component.translatableWithFallback("spawn_gazebo.command.overworld_only", "The gazebo can only be built in the Overworld."));
			return 0;
		}
		BlockPos at = BlockPos.containing(source.getPosition());
		PENDING.put(key(source), new Pending(at, level.getGameTime() + CONFIRM_TICKS));
		source.sendSuccess(() -> Component.translatableWithFallback("spawn_gazebo.command.ask", "This builds a gazebo centred here (%s, %s): trees and blocks in a 15 by 15 area are cleared and built over, and the world spawn and the protection move to it.", at.getX(), at.getZ()), false);
		BoundingBox old = Platform.get().savedBox(level);
		if (old != null) {
			source.sendSuccess(() -> Component.translatableWithFallback("spawn_gazebo.command.ask_old", "The old gazebo near %s, %s stays standing, but is no longer protected.", old.getCenter().getX(), old.getCenter().getZ()), false);
		}
		source.sendSuccess(() -> Component.translatableWithFallback("spawn_gazebo.command.ask_confirm", "Type /spawngazebo build confirm within 30 seconds to build it."), false);
		return 1;
	}

	/** The second step: build where the first step was run, if it was run in the last 30 seconds. */
	static int confirm(CommandSourceStack source) {
		ServerLevel level = source.getServer().overworld();
		Pending pending = PENDING.remove(key(source));
		if (pending == null || level.getGameTime() > pending.until()) {
			source.sendFailure(Component.translatableWithFallback("spawn_gazebo.command.nothing_to_confirm", "Nothing to confirm: run /spawngazebo build first, where you want the gazebo."));
			return 0;
		}
		BlockPos middle;
		try {
			middle = SpawnGazebo.build(level, pending.at().getX(), pending.at().getZ(), true);
		} catch (RuntimeException e) {
			SpawnGazeboMod.LOGGER.error("Spawn Gazebo: couldn't build the gazebo", e);
			middle = null;
		}
		if (middle == null) {
			source.sendFailure(Component.translatableWithFallback("spawn_gazebo.command.failed", "Couldn't build the gazebo here; the server log says why."));
			return 0;
		}
		BlockPos built = middle;
		source.sendSuccess(() -> Component.translatableWithFallback("spawn_gazebo.command.built", "Built the gazebo; the world spawn is now in it (%s, %s, %s).", built.getX(), built.getY(), built.getZ()), true);
		return 1;
	}

	private static UUID key(CommandSourceStack source) {
		Entity entity = source.getEntity();
		return entity == null ? NO_ENTITY : entity.getUUID();
	}

	/** The server stopped: nothing waits for a confirm into the next world. */
	public static void onServerStopped() {
		PENDING.clear();
	}
}
