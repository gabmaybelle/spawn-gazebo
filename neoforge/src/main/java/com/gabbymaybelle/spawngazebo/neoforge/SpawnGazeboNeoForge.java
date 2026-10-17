package com.gabbymaybelle.spawngazebo.neoforge;

import com.gabbymaybelle.spawngazebo.GazeboCommand;
import com.gabbymaybelle.spawngazebo.GazeboGuard;
import com.gabbymaybelle.spawngazebo.Platform;
import com.gabbymaybelle.spawngazebo.SpawnGazebo;
import com.gabbymaybelle.spawngazebo.SpawnGazeboMod;
import com.mojang.serialization.Codec;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;
import java.util.function.Supplier;
import net.minecraft.network.chat.Component;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.packs.PackType;
import net.minecraft.server.packs.repository.Pack;
import net.minecraft.server.packs.repository.PackSource;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.levelgen.structure.BoundingBox;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.ModList;
import net.neoforged.fml.common.Mod;
import net.neoforged.fml.loading.FMLPaths;
import net.neoforged.neoforge.attachment.AttachmentType;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.AddPackFindersEvent;
import net.neoforged.neoforge.event.RegisterCommandsEvent;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.event.entity.player.PlayerInteractEvent;
import net.neoforged.neoforge.event.level.BlockEvent;
import net.neoforged.neoforge.event.server.ServerStartedEvent;
import net.neoforged.neoforge.event.server.ServerStoppedEvent;
import net.neoforged.neoforge.network.PacketDistributor;
import net.neoforged.neoforge.network.event.RegisterPayloadHandlersEvent;
import net.neoforged.neoforge.registries.DeferredRegister;
import net.neoforged.neoforge.registries.NeoForgeRegistries;
import net.neoforged.neoforgespi.language.IModInfo;

/**
 * NeoForge's way in: NeoForge's events, attachments, networking and built-in data pack, passed on
 * to the shared code. Runs on both sides; {@link SpawnGazeboNeoForgeClient} adds the client's part.
 */
@Mod(SpawnGazeboMod.MOD_ID)
public class SpawnGazeboNeoForge {
	private static final DeferredRegister<AttachmentType<?>> ATTACHMENTS = DeferredRegister.create(NeoForgeRegistries.ATTACHMENT_TYPES, SpawnGazeboMod.MOD_ID);
	/** Set on the overworld once this world has been looked at, so the gazebo is never built twice. */
	private static final Supplier<AttachmentType<Boolean>> CHECKED = ATTACHMENTS.register("spawn_gazebo",
			() -> AttachmentType.builder(() -> false).serialize(Codec.BOOL.fieldOf("checked").codec()).build());
	/** The gazebo's box in the overworld, saved when it's built. */
	private static final Supplier<AttachmentType<Optional<BoundingBox>>> BOX = ATTACHMENTS.register("spawn_gazebo_box",
			() -> AttachmentType.builder(() -> Optional.<BoundingBox>empty()).serialize(BoundingBox.CODEC.optionalFieldOf("box").codec()).build());
	/** A mod's starter kit in its neoforge.mods.toml: [modproperties.<its id>] "spawn_gazebo:starter_kit" = "<loot table id>". */
	private static final String KIT_PROPERTY = SpawnGazeboMod.MOD_ID + ":starter_kit";

	public SpawnGazeboNeoForge(IEventBus modBus) {
		Platform.set(new Platform.Hooks() {
			@Override
			public Path configDir() {
				return FMLPaths.CONFIGDIR.get();
			}

			@Override
			public boolean isModLoaded(String id) {
				return ModList.get().isLoaded(id);
			}

			@Override
			public Map<String, String> starterKits() {
				Map<String, String> kits = new LinkedHashMap<>();
				for (IModInfo mod : ModList.get().getMods()) {
					Object table = mod.getModProperties().get(KIT_PROPERTY);
					if (table instanceof String id) {
						kits.put(mod.getModId(), id);
					}
				}
				return kits;
			}

			@Override
			public boolean gazeboChecked(ServerLevel level) {
				return level.getExistingData(CHECKED).orElse(false);
			}

			@Override
			public void markGazeboChecked(ServerLevel level) {
				level.setData(CHECKED, true);
			}

			@Override
			public BoundingBox savedBox(ServerLevel level) {
				return level.getExistingData(BOX).flatMap(box -> box).orElse(null);
			}

			@Override
			public void saveBox(ServerLevel level, BoundingBox box) {
				level.setData(BOX, Optional.of(box));
			}

			@Override
			public boolean canSend(ServerPlayer player, CustomPacketPayload.Type<?> type) {
				return player.connection.hasChannel(type);
			}

			@Override
			public void send(ServerPlayer player, CustomPacketPayload payload) {
				PacketDistributor.sendToPlayer(player, payload);
			}
		});
		SpawnGazeboMod.init();

		ATTACHMENTS.register(modBus);
		modBus.addListener(SpawnGazeboNeoForge::payloads);
		modBus.addListener(SpawnGazeboNeoForge::packs);
		NeoForge.EVENT_BUS.addListener(SpawnGazeboNeoForge::serverStarted);
		NeoForge.EVENT_BUS.addListener(SpawnGazeboNeoForge::serverStopped);
		NeoForge.EVENT_BUS.addListener((RegisterCommandsEvent event) -> GazeboCommand.register(event.getDispatcher()));
		NeoForge.EVENT_BUS.addListener(SpawnGazeboNeoForge::playerJoined);
		NeoForge.EVENT_BUS.addListener(SpawnGazeboNeoForge::blockBreak);
		NeoForge.EVENT_BUS.addListener(SpawnGazeboNeoForge::useBlock);
	}

	/** Optional, so a player without Spawn Gazebo can still join; they just aren't told where the gazebo is. */
	private static void payloads(RegisterPayloadHandlersEvent event) {
		event.registrar("1").optional().playToClient(GazeboGuard.Box.TYPE, GazeboGuard.Box.CODEC,
				(payload, context) -> context.enqueueWork(() -> GazeboGuard.setClientBox(payload.box().orElse(null))));
	}

	/** The gazebo structures, the starter chest's loot table and the functions that hand out the kit, always on. */
	private static void packs(AddPackFindersEvent event) {
		if (event.getPackType() == PackType.SERVER_DATA) {
			event.addPackFinders(ResourceLocation.fromNamespaceAndPath(SpawnGazeboMod.MOD_ID, "resourcepacks/spawn_gazebo_data"), PackType.SERVER_DATA,
					Component.translatable("pack.spawn_gazebo.data"), PackSource.BUILT_IN, true, Pack.Position.TOP);
		}
	}

	private static void serverStarted(ServerStartedEvent event) {
		SpawnGazebo.onServerStarted(event.getServer());
		GazeboGuard.onServerStarted(event.getServer());
	}

	private static void serverStopped(ServerStoppedEvent event) {
		GazeboGuard.onServerStopped();
		GazeboCommand.onServerStopped();
	}

	private static void playerJoined(PlayerEvent.PlayerLoggedInEvent event) {
		if (event.getEntity() instanceof ServerPlayer player) {
			GazeboGuard.onPlayerJoin(player);
		}
	}

	private static void blockBreak(BlockEvent.BreakEvent event) {
		if (event.getLevel() instanceof Level level && !GazeboGuard.allowBreak(level, event.getPlayer(), event.getPos(), event.getState())) {
			event.setCanceled(true);
		}
	}

	private static void useBlock(PlayerInteractEvent.RightClickBlock event) {
		InteractionResult result = GazeboGuard.onUseBlock(event.getEntity(), event.getLevel(), event.getHand(), event.getHitVec());
		if (result != InteractionResult.PASS) {
			event.setCancellationResult(result);
			event.setCanceled(true);
		}
	}
}
