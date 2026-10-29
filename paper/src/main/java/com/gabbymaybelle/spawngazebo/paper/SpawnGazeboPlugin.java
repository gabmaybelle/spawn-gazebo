package com.gabbymaybelle.spawngazebo.paper;

import java.util.Locale;
import org.bukkit.Bukkit;
import org.bukkit.NamespacedKey;
import org.bukkit.World;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.server.ServerLoadEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.persistence.PersistentDataContainer;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.plugin.java.JavaPlugin;

/**
 * Spawn Gazebo as a Paper plugin: the server builds the gazebo at the world spawn the first time it
 * starts a brand-new world, and the world remembers it. Settings are in {@code plugins/SpawnGazebo/config.yml}.
 * The gazebo itself is {@link GazeboBuilder}.
 */
public final class SpawnGazeboPlugin extends JavaPlugin implements Listener {
	/** The world remembers whether it was looked at, so a world from before the plugin keeps its spawn as it is. */
	private NamespacedKey checkedKey;
	/** The gazebo's box, {@code [minX, minY, minZ, maxX, maxY, maxZ]}, for the protection. */
	private NamespacedKey boxKey;
	/** Whether the starter chest has been claimed by the first player, so later newcomers get their own kit. */
	private NamespacedKey claimedKey;
	/** The loot table the chest was filled from, or "" when the chest is off: the kit later players are handed. */
	private NamespacedKey tableKey;

	@Override
	public void onEnable() {
		saveDefaultConfig();
		checkedKey = new NamespacedKey(this, "checked");
		boxKey = new NamespacedKey(this, "box");
		claimedKey = new NamespacedKey(this, "claimed");
		tableKey = new NamespacedKey(this, "table");
		Bukkit.getPluginManager().registerEvents(this, this);
	}

	/** The server has started: a brand-new world gets its gazebo, once. */
	@EventHandler
	public void onServerLoad(ServerLoadEvent event) {
		if (event.getType() != ServerLoadEvent.LoadType.STARTUP) {
			return;
		}
		World world = Bukkit.getWorlds().get(0);
		PersistentDataContainer data = world.getPersistentDataContainer();
		if (data.has(checkedKey, PersistentDataType.BYTE)) {
			getLogger().info("Spawn Gazebo: " + world.getName() + " was looked at before, leaving it as it is");
			return;
		}
		data.set(checkedKey, PersistentDataType.BYTE, (byte) 1);
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
			if (built == null) {
				return;
			}
			data.set(boxKey, PersistentDataType.INTEGER_ARRAY, built.box());
			data.set(tableKey, PersistentDataType.STRING, built.table() == null ? "" : built.table());
			getLogger().info("Spawn Gazebo: built the gazebo (" + built.palette() + " " + built.wood() + ") at "
					+ built.middle().getBlockX() + " " + built.middle().getBlockY() + " " + built.middle().getBlockZ()
					+ (built.chest() == null ? "" : ", chest at " + built.chest().getBlockX() + " " + built.chest().getBlockY() + " " + built.chest().getBlockZ()));
		} catch (RuntimeException e) {
			getLogger().severe("Spawn Gazebo: couldn't build the gazebo: " + e);
			e.printStackTrace();
		}
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
		World world = Bukkit.getWorlds().get(0);
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
	private String kit() {
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
}
