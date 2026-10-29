package com.gabbymaybelle.spawngazebo.paper;

import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.logging.Level;
import io.papermc.paper.command.brigadier.BasicCommand;
import io.papermc.paper.command.brigadier.CommandSourceStack;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.Bukkit;
import org.bukkit.GameMode;
import org.bukkit.HeightMap;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;
import org.bukkit.util.BoundingBox;

/**
 * {@code /spawngazebo build}, for operators: a gazebo in a world that already exists, centred where
 * you stand. The first run says what it will do; {@code /spawngazebo build confirm} within 30
 * seconds builds it there, and the world spawn and the protection move to it.
 */
final class GazeboCommand implements BasicCommand {
	/** How long a {@code build} waits for its {@code confirm}, in ticks. */
	static final int CONFIRM_TICKS = 30 * 20;
	/** The server console and command blocks have no UUID; they share this one. */
	private static final UUID NO_ENTITY = new UUID(0, 0);

	/** Where each operator asked to build, and until when (game time) the confirm counts. */
	private record Pending(int x, int z, long until) {
	}

	private final SpawnGazeboPlugin plugin;
	private final Map<UUID, Pending> pending = new HashMap<>();

	GazeboCommand(SpawnGazeboPlugin plugin) {
		this.plugin = plugin;
	}

	@Override
	public String permission() {
		return "spawngazebo.build";
	}

	@Override
	public Collection<String> suggest(CommandSourceStack source, String[] args) {
		if (args.length <= 1) {
			return List.of("build");
		}
		return args.length == 2 && args[0].equals("build") ? List.of("confirm") : List.of();
	}

	@Override
	public void execute(CommandSourceStack source, String[] args) {
		if (args.length == 0 || !args[0].equals("build")) {
			fail(source.getSender(), "Usage: /spawngazebo build, then /spawngazebo build confirm.");
			return;
		}
		if (args.length >= 2 && args[1].equals("confirm")) {
			confirm(source);
		} else {
			ask(source);
		}
	}

	/** The first step: say what will happen here, and wait for the confirm. */
	private void ask(CommandSourceStack source) {
		CommandSender sender = source.getSender();
		World world = overworld();
		Location at = source.getLocation();
		if (!world.equals(at.getWorld())) {
			fail(sender, "The gazebo can only be built in the Overworld.");
			return;
		}
		if (refuseOnGazebo(sender, world, at.getBlockX(), at.getBlockZ())) {
			return;
		}
		pending.put(key(source), new Pending(at.getBlockX(), at.getBlockZ(), world.getGameTime() + CONFIRM_TICKS));
		say(sender, "This builds a gazebo centred here (" + at.getBlockX() + ", " + at.getBlockZ() + "): trees and blocks in a 15 by 15 area are cleared and built over, and the world spawn and the protection move to it.");
		int[] old = plugin.savedBox();
		if (old != null) {
			say(sender, "The old gazebo near " + (old[0] + GazeboBuilder.HALF) + ", " + (old[2] + GazeboBuilder.HALF) + " stays standing, but is no longer protected.");
		}
		say(sender, "Type /spawngazebo build confirm within 30 seconds to build it.");
	}

	/** The second step: build where the first step was run, if it was run in the last 30 seconds. */
	private void confirm(CommandSourceStack source) {
		CommandSender sender = source.getSender();
		World world = overworld();
		Pending asked = pending.remove(key(source));
		if (asked == null || world.getGameTime() > asked.until()) {
			fail(sender, "Nothing to confirm: run /spawngazebo build first, where you want the gazebo.");
			return;
		}
		if (refuseOnGazebo(sender, world, asked.x(), asked.z())) {
			return;
		}
		GazeboBuilder.Result built;
		try {
			// Loads the chunks round the spot and builds on the main thread, so the server pauses for a
			// moment while it works, the same as the mod; it's an operator's one-off, not something that runs in play.
			built = GazeboBuilder.build(plugin, world, asked.x(), asked.z(), plugin.kit());
		} catch (RuntimeException e) {
			plugin.getLogger().log(Level.SEVERE, "Spawn Gazebo: couldn't build the gazebo", e);
			built = null;
		}
		if (built == null) {
			fail(sender, "Couldn't build the gazebo here; the server log says why.");
			return;
		}
		// Built into a world people already play in: the chest is theirs, and newcomers get their own roll.
		plugin.remember(built, true);
		liftPlayersOut(world, built);
		say(sender, "Built the gazebo; the world spawn is now in it (" + built.middle().getBlockX() + ", " + built.middle().getBlockY() + ", " + built.middle().getBlockZ() + ").");
	}

	/**
	 * Building where a gazebo already stands would put the new one on the old one's roof, with its
	 * foundation driven down through the old one, so the command says so and builds nothing.
	 */
	private boolean refuseOnGazebo(CommandSender sender, World world, int cx, int cz) {
		int[] found = gazeboNear(world, cx, cz);
		if (found == null) {
			return false;
		}
		fail(sender, "There's already a gazebo here (near " + found[0] + ", " + found[1] + "). Stand at least 15 blocks from it to build another.");
		return true;
	}

	/**
	 * A gazebo whose blocks a new one centred on this column would land on, as {x, z}, or null:
	 * the protected gazebo the world remembers, or an older one, found by its cut copper dome or
	 * lightning rod on the surface of the 15 by 15 area.
	 */
	int[] gazeboNear(World world, int cx, int cz) {
		int half = GazeboBuilder.HALF;
		int[] saved = plugin.savedBox();
		if (saved != null && saved[0] <= cx + half && saved[3] >= cx - half && saved[2] <= cz + half && saved[5] >= cz - half) {
			return new int[] {saved[0] + half, saved[2] + half};
		}
		for (int x = cx - half; x <= cx + half; x++) {
			for (int z = cz - half; z <= cz + half; z++) {
				String path = world.getBlockAt(x, world.getHighestBlockYAt(x, z, HeightMap.WORLD_SURFACE), z).getType().getKey().getKey();
				if (path.contains("cut_copper") || path.equals("lightning_rod")) {
					return new int[] {x, z};
				}
			}
		}
		return null;
	}

	/**
	 * The gazebo was just built where people may be standing, usually the operator who asked for it:
	 * anyone left inside its box (in the stone plinth, say) is moved to the new spawn on its floor,
	 * facing out of the entrance. Players elsewhere stay where they are.
	 */
	private void liftPlayersOut(World world, GazeboBuilder.Result built) {
		int[] box = built.box();
		BoundingBox inside = new BoundingBox(box[0], box[1], box[2], box[3] + 1, box[4] + 1, box[5] + 1);
		float yaw = world.getSpawnLocation().getYaw();
		for (Player player : List.copyOf(world.getPlayers())) {
			if (player.getGameMode() != GameMode.SPECTATOR && player.getBoundingBox().overlaps(inside)) {
				player.leaveVehicle();
				player.teleport(new Location(world, built.middle().getBlockX() + 0.5, built.middle().getBlockY(), built.middle().getBlockZ() + 0.5, yaw, 0f));
				player.setFallDistance(0f);
				say(player, "The gazebo was built where you stood, so you've been moved up onto its floor.");
			}
		}
	}

	private static World overworld() {
		return SpawnGazeboPlugin.overworld();
	}

	private static UUID key(CommandSourceStack source) {
		Entity entity = source.getExecutor();
		return entity == null ? NO_ENTITY : entity.getUniqueId();
	}

	private static void say(CommandSender to, String text) {
		to.sendMessage(Component.text(text));
	}

	private static void fail(CommandSender to, String text) {
		to.sendMessage(Component.text(text, NamedTextColor.RED));
	}
}
