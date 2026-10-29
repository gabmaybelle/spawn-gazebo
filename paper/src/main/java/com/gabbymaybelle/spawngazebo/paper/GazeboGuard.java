package com.gabbymaybelle.spawngazebo.paper;

import org.bukkit.GameMode;
import org.bukkit.Material;
import org.bukkit.Tag;
import org.bukkit.block.Block;
import org.bukkit.block.TileState;
import org.bukkit.block.data.Directional;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;
import org.bukkit.event.Event;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.block.BlockBurnEvent;
import org.bukkit.event.block.BlockDispenseEvent;
import org.bukkit.event.block.BlockExplodeEvent;
import org.bukkit.event.block.BlockIgniteEvent;
import org.bukkit.event.block.BlockMultiPlaceEvent;
import org.bukkit.event.block.BlockPistonExtendEvent;
import org.bukkit.event.block.BlockPistonRetractEvent;
import org.bukkit.event.block.BlockPlaceEvent;
import org.bukkit.event.entity.EntityExplodeEvent;
import org.bukkit.event.player.PlayerBucketEmptyEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.inventory.ItemStack;

/**
 * Keeps the gazebo standing: inside its box, and as far round it as the protectedRadius setting
 * reaches, players outside creative can't break or place blocks or reshape them with tools, and
 * explosions, fire, pistons and buckets leave it alone. The chest, the benches and the flower pots
 * still work, and the decorated pots can still be broken for what's inside them. The protectGazebo
 * setting switches it off for anyone who wants to remodel. The same rules as the mods' GazeboGuard,
 * as event handlers.
 */
final class GazeboGuard implements Listener {
	private final SpawnGazeboPlugin plugin;

	GazeboGuard(SpawnGazeboPlugin plugin) {
		this.plugin = plugin;
	}

	/**
	 * True when this block is in the protected area round the gazebo: the saved box widened to the
	 * protectedRadius setting from the middle, while the protectGazebo setting is on.
	 */
	boolean guards(Block block) {
		int[] box = plugin.savedBox();
		if (box == null || !plugin.protectGazebo() || !block.getWorld().equals(SpawnGazeboPlugin.overworld())) {
			return false;
		}
		int extra = Math.max(0, plugin.protectedRadius() - GazeboBuilder.HALF);
		return block.getX() >= box[0] - extra && block.getX() <= box[3] + extra
				&& block.getY() >= box[1] && block.getY() <= box[4]
				&& block.getZ() >= box[2] - extra && block.getZ() <= box[5] + extra;
	}

	/** {@link #guards}, unless it's a creative player doing it (so the gazebo can still be edited). */
	boolean protects(Entity entity, Block block) {
		return !(entity instanceof Player player && player.getGameMode() == GameMode.CREATIVE) && guards(block);
	}

	/** No breaking the gazebo outside creative; the decorated pots can always be broken for what's inside. */
	@EventHandler(ignoreCancelled = true)
	public void onBreak(BlockBreakEvent event) {
		if (event.getBlock().getType() != Material.DECORATED_POT && protects(event.getPlayer(), event.getBlock())) {
			event.setCancelled(true);
		}
	}

	/** No building into the gazebo outside creative. */
	@EventHandler(ignoreCancelled = true)
	public void onPlace(BlockPlaceEvent event) {
		if (protects(event.getPlayer(), event.getBlock())) {
			event.setCancelled(true);
			return;
		}
		if (event instanceof BlockMultiPlaceEvent multi) {
			for (var state : multi.getReplacedBlockStates()) {
				if (protects(event.getPlayer(), state.getBlock())) {
					event.setCancelled(true);
					return;
				}
			}
		}
	}

	/** No pouring water or lava into the gazebo by hand. */
	@EventHandler(ignoreCancelled = true)
	public void onBucket(PlayerBucketEmptyEvent event) {
		if (protects(event.getPlayer(), event.getBlock())) {
			event.setCancelled(true);
		}
	}

	/** Nor by dispenser. */
	@EventHandler(ignoreCancelled = true)
	public void onDispense(BlockDispenseEvent event) {
		ItemStack item = event.getItem();
		boolean liquid = item.getType() == Material.WATER_BUCKET || item.getType() == Material.LAVA_BUCKET || item.getType() == Material.POWDER_SNOW_BUCKET;
		if (liquid && event.getBlock().getBlockData() instanceof Directional facing && guards(event.getBlock().getRelative(facing.getFacing()))) {
			event.setCancelled(true);
		}
	}

	/**
	 * A player right-clicking a block: tools that turn one block into another (stripping, scraping
	 * the copper, paths, farmland) are refused inside the area; anything else goes through. The
	 * chest, the pots and anything else you open or take from still works with a tool in hand, and
	 * the benches are wooden stairs, which no tool changes, so a seat plugin still gets its turn.
	 */
	@EventHandler(ignoreCancelled = true)
	public void onUseBlock(PlayerInteractEvent event) {
		if (event.getAction() != Action.RIGHT_CLICK_BLOCK || event.getClickedBlock() == null || event.getItem() == null) {
			return;
		}
		Material tool = event.getItem().getType();
		boolean reshapes = Tag.ITEMS_AXES.isTagged(tool) || Tag.ITEMS_SHOVELS.isTagged(tool) || Tag.ITEMS_HOES.isTagged(tool);
		Block block = event.getClickedBlock();
		if (!reshapes || !protects(event.getPlayer(), block)) {
			return;
		}
		boolean harmless = block.getState() instanceof TileState || Tag.FLOWER_POTS.isTagged(block.getType()) || Tag.WOODEN_STAIRS.isTagged(block.getType());
		if (!harmless) {
			event.setUseInteractedBlock(Event.Result.DENY);
			event.setUseItemInHand(Event.Result.DENY);
		}
	}

	/** Pistons can't push the gazebo apart, nor push anything into it. */
	@EventHandler(ignoreCancelled = true)
	public void onPistonExtend(BlockPistonExtendEvent event) {
		if (guards(event.getBlock().getRelative(event.getDirection()))) {
			event.setCancelled(true);
			return;
		}
		for (Block moved : event.getBlocks()) {
			if (guards(moved) || guards(moved.getRelative(event.getDirection()))) {
				event.setCancelled(true);
				return;
			}
		}
	}

	/** Nor pull it apart. */
	@EventHandler(ignoreCancelled = true)
	public void onPistonRetract(BlockPistonRetractEvent event) {
		for (Block moved : event.getBlocks()) {
			if (guards(moved) || guards(moved.getRelative(event.getDirection()))) {
				event.setCancelled(true);
				return;
			}
		}
	}

	/** Explosions of any kind (creepers, TNT, fireballs, beds) leave the gazebo's blocks standing. */
	@EventHandler(ignoreCancelled = true)
	public void onExplode(EntityExplodeEvent event) {
		event.blockList().removeIf(this::guards);
	}

	@EventHandler(ignoreCancelled = true)
	public void onBlockExplode(BlockExplodeEvent event) {
		event.blockList().removeIf(this::guards);
	}

	/** Fire can't burn away the gazebo's fences and benches. */
	@EventHandler(ignoreCancelled = true)
	public void onBurn(BlockBurnEvent event) {
		if (guards(event.getBlock())) {
			event.setCancelled(true);
		}
	}

	/** Nor catch on them, unless a creative player lights it. */
	@EventHandler(ignoreCancelled = true)
	public void onIgnite(BlockIgniteEvent event) {
		if (protects(event.getPlayer(), event.getBlock())) {
			event.setCancelled(true);
		}
	}
}
