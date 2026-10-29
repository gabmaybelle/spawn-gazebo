package com.gabbymaybelle.spawngazebo.paper;

import java.util.Locale;
import java.util.logging.Level;
import io.papermc.paper.plugin.lifecycle.event.types.LifecycleEvents;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.NamespacedKey;
import org.bukkit.World;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerRespawnEvent;
import org.bukkit.event.server.ServerLoadEvent;
import io.papermc.paper.event.player.AsyncPlayerSpawnLocationEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.persistence.PersistentDataContainer;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.plugin.java.JavaPlugin;

/**
 * Spawn Gazebo as a Paper plugin: the server builds the gazebo at the world spawn the first time it
 * starts a brand-new world, and the world remembers it. Settings are in {@code plugins/SpawnGazebo/config.yml}.
 * The gazebo itself is {@link GazeboBuilder}, the protection {@link GazeboGuard} and the build
 * command for existing worlds {@link GazeboCommand}.
 */
public final class SpawnGazeboPlugin extends JavaPlugin implements Listener {
	/** The gazebo itself reaches this far from its middle; the protected area can't be smaller. */
	static final int MIN_RADIUS = GazeboBuilder.HALF, MAX_RADIUS = 64;

	/** The world remembers whether it was looked at, so a world from before the plugin keeps its spawn as it is. */
	private NamespacedKey checkedKey;
	/** The gazebo's box, {@code [minX, minY, minZ, maxX, maxY, maxZ]}, for the protection. */
	private NamespacedKey boxKey;
	/** Whether the starter chest has been claimed by the first player, so later newcomers get their own kit. */
	private NamespacedKey claimedKey;
	/** The loot table the chest was filled from, or "" when the chest is off: the kit later players are handed. */
	private NamespacedKey tableKey;

	private boolean protectGazebo = true;
	private int protectedRadius = 12;
	/** {@link #middle()} as last worked out on the main thread, for the join event, which runs off it. */
	private volatile Location spawnMiddle;

	@Override
	public void onEnable() {
		saveDefaultConfig();
		protectGazebo = getConfig().getBoolean("protectGazebo", true);
		protectedRadius = Math.max(MIN_RADIUS, Math.min(MAX_RADIUS, getConfig().getInt("protectedRadius", 12)));
		checkedKey = new NamespacedKey(this, "checked");
		boxKey = new NamespacedKey(this, "box");
		claimedKey = new NamespacedKey(this, "claimed");
		tableKey = new NamespacedKey(this, "table");
		Bukkit.getPluginManager().registerEvents(this, this);
		Bukkit.getPluginManager().registerEvents(new GazeboGuard(this), this);
		getLifecycleManager().registerEventHandler(LifecycleEvents.COMMANDS, event ->
				event.registrar().register("spawngazebo", "Builds the gazebo where you stand (operators): /spawngazebo build, then confirm", new GazeboCommand(this)));
	}

	/** The server has started: a brand-new world gets its gazebo, once. */
	@EventHandler
	public void onServerLoad(ServerLoadEvent event) {
		if (event.getType() != ServerLoadEvent.LoadType.STARTUP) {
			return;
		}
		World world = overworld();
		PersistentDataContainer data = world.getPersistentDataContainer();
		if (data.has(checkedKey, PersistentDataType.BYTE)) {
			getLogger().info("Spawn Gazebo: " + world.getName() + " was looked at before, leaving it as it is");
			keepLoaded();
			return;
		}
		data.set(checkedKey, PersistentDataType.BYTE, (byte) 1);
		keepLoaded();
		// Only a brand-new world: one that has never ticked. Older worlds keep their spawn as it is.
		if (!getConfig().getBoolean("spawnGazebo", true)) {
			getLogger().info("Spawn Gazebo: spawnGazebo is off, leaving the spawn as it is");
			return;
		}
		if (world.getFullTime() > 0) {
			getLogger().info("Spawn Gazebo: " + world.getName() + " has already been played (time " + world.getFullTime() + "), leaving its spawn as it is");
			return;
		}
		try {
			GazeboBuilder.Result built = GazeboBuilder.build(this, world, world.getSpawnLocation().getBlockX(), world.getSpawnLocation().getBlockZ(), kit());
			if (built != null) {
				remember(built, false);
			}
		} catch (RuntimeException e) {
			getLogger().log(Level.SEVERE, "Spawn Gazebo: couldn't build the gazebo", e);
		}
	}

	/** The world remembers the gazebo just built: its box, what its chest holds, and whether the chest is already someone's. */
	void remember(GazeboBuilder.Result built, boolean claimed) {
		PersistentDataContainer data = overworld().getPersistentDataContainer();
		data.set(boxKey, PersistentDataType.INTEGER_ARRAY, built.box());
		data.set(tableKey, PersistentDataType.STRING, built.table() == null ? "" : built.table());
		if (claimed) {
			data.set(claimedKey, PersistentDataType.BYTE, (byte) 1);
		} else {
			data.remove(claimedKey);
		}
		overworld().removePluginChunkTickets(this);
		keepLoaded();
		getLogger().info("Spawn Gazebo: built the gazebo (" + built.palette() + " " + built.wood() + ") at "
				+ built.middle().getBlockX() + " " + built.middle().getBlockY() + " " + built.middle().getBlockZ()
				+ (built.chest() == null ? "" : ", chest at " + built.chest().getBlockX() + " " + built.chest().getBlockY() + " " + built.chest().getBlockZ()));
	}

	/** The box the world remembers, or null when it has no gazebo. */
	int[] savedBox() {
		return overworld().getPersistentDataContainer().get(boxKey, PersistentDataType.INTEGER_ARRAY);
	}

	/** Where players wake up: the middle of the gazebo's floor, facing out of the entrance, or null without a gazebo. */
	Location middle() {
		int[] box = savedBox();
		if (box == null) {
			return null;
		}
		World world = overworld();
		return new Location(world, box[0] + GazeboBuilder.HALF + 0.5, box[1] + GazeboBuilder.DEPTH + 3, box[2] + GazeboBuilder.HALF + 0.5,
				world.getSpawnLocation().getYaw(), 0f);
	}

	/**
	 * Keeps the gazebo's chunk loaded. A server need not keep its spawn chunks loaded, and the
	 * game's own search for a place to put a new player reads the ground height of an unloaded
	 * chunk as the bottom of the world: this is what put the first test player under the bedrock.
	 */
	private void keepLoaded() {
		Location middle = middle();
		spawnMiddle = middle;
		if (middle != null) {
			overworld().addPluginChunkTicket(middle.getBlockX() >> 4, middle.getBlockZ() >> 4, this);
		}
	}

	/**
	 * A player's first join puts them on the gazebo's floor, whatever the game's own spawn search
	 * would do. The event runs off the main thread, so it only reads the location worked out earlier.
	 */
	@EventHandler
	public void onSpawnLocation(AsyncPlayerSpawnLocationEvent event) {
		Location middle = spawnMiddle;
		if (middle != null && event.isNewPlayer()) {
			event.setSpawnLocation(middle.clone());
		}
	}

	/** So does a respawn without a bed or a respawn anchor. */
	@EventHandler
	public void onRespawn(PlayerRespawnEvent event) {
		Location middle = middle();
		if (middle != null && !event.isBedSpawn() && !event.isAnchorSpawn()) {
			event.setRespawnLocation(middle);
		}
	}

	boolean protectGazebo() {
		return protectGazebo;
	}

	int protectedRadius() {
		return protectedRadius;
	}

	/**
	 * The first player to join gets the chest; everyone after them is handed a roll of the same kit
	 * on their first join, so nobody arrives to an emptied chest.
	 */
	@EventHandler
	public void onJoin(PlayerJoinEvent event) {
		Player player = event.getPlayer();
		if (player.hasPlayedBefore()) {
			return;
		}
		World world = overworld();
		PersistentDataContainer data = world.getPersistentDataContainer();
		String table = data.getOrDefault(tableKey, PersistentDataType.STRING, "");
		if (table.isEmpty()) {
			return;
		}
		if (data.has(claimedKey, PersistentDataType.BYTE)) {
			for (ItemStack item : GazeboBuilder.kitItems(table)) {
				player.getInventory().addItem(item).values().forEach(left -> world.dropItem(player.getLocation(), left));
			}
			return;
		}
		data.set(claimedKey, PersistentDataType.BYTE, (byte) 1);
	}

	/** What the chest holds, from the settings: the starter kit, the bonus chest, any loot table id, or nothing. */
	String kit() {
		if (!getConfig().getBoolean("starterChest", true)) {
			return null;
		}
		String value = getConfig().getString("chestLoot", GazeboBuilder.STARTER_KIT).trim().toLowerCase(Locale.ROOT);
		if (value.isEmpty() || value.equals(GazeboBuilder.STARTER_KIT)) {
			return GazeboBuilder.STARTER_KIT;
		}
		if (value.equals(GazeboBuilder.BONUS_CHEST)) {
			return GazeboBuilder.BONUS_CHEST_TABLE;
		}
		if (NamespacedKey.fromString(value) == null || Bukkit.getLootTable(NamespacedKey.fromString(value)) == null) {
			getLogger().warning("Spawn Gazebo: chestLoot \"" + value + "\" isn't a loot table this server knows, using the starter kit");
			return GazeboBuilder.STARTER_KIT;
		}
		return value;
	}

	/** The server's main world: the first one loaded is always the default world of server.properties, whatever else a world plugin adds. */
	static World overworld() {
		return Bukkit.getWorlds().get(0);
	}
}
