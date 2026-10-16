package com.gabbymaybelle.spawngazebo;

import java.nio.file.Path;
import java.util.Map;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.levelgen.structure.BoundingBox;

/**
 * The few things Spawn Gazebo asks of the mod loader. The Fabric and NeoForge jars each set this
 * first thing, before any other Spawn Gazebo class is used.
 *
 * <p>What the world remembers (whether it has been given its gazebo, and where that is) is kept the
 * loader's own way, so worlds made with an earlier Fabric version keep theirs.
 */
public final class Platform {
	/** What each loader answers. */
	public interface Hooks {
		/** The game's config folder. */
		Path configDir();

		/** Whether a mod with this id is installed. */
		boolean isModLoaded(String id);

		/**
		 * Starter kits other mods bring for the pack they're in, as mod id to loot table id (not yet
		 * checked), in the order the loader lists the mods.
		 */
		Map<String, String> starterKits();

		/** Whether this world has already been checked for a gazebo (built or not). */
		boolean gazeboChecked(ServerLevel level);

		void markGazeboChecked(ServerLevel level);

		/** The gazebo's box saved when it was built, or null when this world has none. */
		BoundingBox savedBox(ServerLevel level);

		void saveBox(ServerLevel level, BoundingBox box);

		/** Whether this player's game understands this message (it has Spawn Gazebo too). */
		boolean canSend(ServerPlayer player, CustomPacketPayload.Type<?> type);

		void send(ServerPlayer player, CustomPacketPayload payload);
	}

	private static Hooks hooks;

	private Platform() {
	}

	public static void set(Hooks loaderHooks) {
		hooks = loaderHooks;
	}

	static Hooks get() {
		return hooks;
	}
}
