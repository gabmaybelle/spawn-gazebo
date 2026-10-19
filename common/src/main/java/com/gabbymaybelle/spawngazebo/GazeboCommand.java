package com.gabbymaybelle.spawngazebo;

import com.mojang.brigadier.CommandDispatcher;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.levelgen.structure.BoundingBox;
import net.minecraft.world.phys.AABB;

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
		liftPlayersOut(level, middle);
		BlockPos built = middle;
		source.sendSuccess(() -> Component.translatableWithFallback("spawn_gazebo.command.built", "Built the gazebo; the world spawn is now in it (%s, %s, %s).", built.getX(), built.getY(), built.getZ()), true);
		return 1;
	}

	/**
	 * The gazebo was just built where people may be standing, usually the operator who asked for it:
	 * anyone left inside its box (in the stone plinth, say) is moved to the new spawn on its floor,
	 * facing out of the entrance. Players elsewhere stay where they are.
	 */
	static void liftPlayersOut(ServerLevel level, BlockPos middle) {
		BoundingBox box = Platform.get().savedBox(level);
		if (box == null) {
			return;
		}
		AABB inside = AABB.of(box);
		float yaw = level.getSharedSpawnAngle();
		for (ServerPlayer player : List.copyOf(level.players())) {
			if (!player.isSpectator() && player.getBoundingBox().intersects(inside)) {
				player.stopRiding();
				player.teleportTo(level, middle.getX() + 0.5, middle.getY(), middle.getZ() + 0.5, yaw, 0f);
				player.resetFallDistance();
				player.sendSystemMessage(Component.translatableWithFallback("spawn_gazebo.command.moved", "The gazebo was built where you stood, so you've been moved up onto its floor."));
			}
		}
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
