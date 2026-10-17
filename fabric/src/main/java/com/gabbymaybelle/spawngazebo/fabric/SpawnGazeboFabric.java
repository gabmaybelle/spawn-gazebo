package com.gabbymaybelle.spawngazebo.fabric;

import com.gabbymaybelle.spawngazebo.GazeboGuard;
import com.gabbymaybelle.spawngazebo.Platform;
import com.gabbymaybelle.spawngazebo.SpawnGazebo;
import com.gabbymaybelle.spawngazebo.SpawnGazeboMod;
import com.mojang.serialization.Codec;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.Map;
import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.attachment.v1.AttachmentRegistry;
import net.fabricmc.fabric.api.attachment.v1.AttachmentType;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.event.player.PlayerBlockBreakEvents;
import net.fabricmc.fabric.api.event.player.UseBlockCallback;
import net.fabricmc.fabric.api.networking.v1.PayloadTypeRegistry;
import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.fabricmc.fabric.api.resource.ResourceManagerHelper;
import net.fabricmc.fabric.api.resource.ResourcePackActivationType;
import net.fabricmc.loader.api.FabricLoader;
import net.fabricmc.loader.api.ModContainer;
import net.fabricmc.loader.api.metadata.CustomValue;
import net.minecraft.network.chat.Component;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.levelgen.structure.BoundingBox;

/** Fabric's way in: Fabric API's events, attachments, networking and built-in pack, passed on to the shared code. */
public class SpawnGazeboFabric implements ModInitializer {
	/** Set on the overworld once this world has been looked at, so the gazebo is never built twice. */
	private static final AttachmentType<Boolean> CHECKED = AttachmentRegistry.create(
			ResourceLocation.fromNamespaceAndPath(SpawnGazeboMod.MOD_ID, "spawn_gazebo"),
			builder -> builder.persistent(Codec.BOOL));
	/** The gazebo's box in the overworld, saved when it's built. */
	private static final AttachmentType<BoundingBox> BOX = AttachmentRegistry.create(
			ResourceLocation.fromNamespaceAndPath(SpawnGazeboMod.MOD_ID, "spawn_gazebo_box"),
			builder -> builder.persistent(BoundingBox.CODEC));

	@Override
	public void onInitialize() {
		Platform.set(new Platform.Hooks() {
			@Override
			public Path configDir() {
				return FabricLoader.getInstance().getConfigDir();
			}

			@Override
			public boolean isModLoaded(String id) {
				return FabricLoader.getInstance().isModLoaded(id);
			}

			@Override
			public Map<String, String> starterKits() {
				Map<String, String> kits = new LinkedHashMap<>();
				for (ModContainer mod : FabricLoader.getInstance().getAllMods()) {
					CustomValue custom = mod.getMetadata().getCustomValue(SpawnGazeboMod.MOD_ID);
					if (custom == null || custom.getType() != CustomValue.CvType.OBJECT) {
						continue;
					}
					CustomValue table = custom.getAsObject().get("starter_kit");
					if (table != null && table.getType() == CustomValue.CvType.STRING) {
						kits.put(mod.getMetadata().getId(), table.getAsString());
					}
				}
				return kits;
			}

			@Override
			public boolean gazeboChecked(ServerLevel level) {
				return level.getAttachedOrElse(CHECKED, false);
			}

			@Override
			public void markGazeboChecked(ServerLevel level) {
				level.setAttached(CHECKED, true);
			}

			@Override
			public BoundingBox savedBox(ServerLevel level) {
				return level.getAttached(BOX);
			}

			@Override
			public void saveBox(ServerLevel level, BoundingBox box) {
				level.setAttached(BOX, box);
			}

			@Override
			public boolean canSend(ServerPlayer player, CustomPacketPayload.Type<?> type) {
				return ServerPlayNetworking.canSend(player, type.id());
			}

			@Override
			public void send(ServerPlayer player, CustomPacketPayload payload) {
				ServerPlayNetworking.send(player, payload);
			}
		});
		SpawnGazeboMod.init();

		PayloadTypeRegistry.playS2C().register(GazeboGuard.Box.TYPE, GazeboGuard.Box.CODEC);
		ServerLifecycleEvents.SERVER_STARTED.register(server -> {
			SpawnGazebo.onServerStarted(server);
			GazeboGuard.onServerStarted(server);
		});
		ServerLifecycleEvents.SERVER_STOPPED.register(server -> GazeboGuard.onServerStopped());
		ServerPlayConnectionEvents.JOIN.register((handler, sender, server) -> GazeboGuard.onPlayerJoin(handler.player));
		PlayerBlockBreakEvents.BEFORE.register((level, player, pos, state, blockEntity) -> GazeboGuard.allowBreak(level, player, pos, state));
		UseBlockCallback.EVENT.register(GazeboGuard::onUseBlock);

		ModContainer mod = FabricLoader.getInstance().getModContainer(SpawnGazeboMod.MOD_ID).orElseThrow();
		// The gazebo structures, the starter chest's loot table and the functions that hand out the kit.
		ResourceManagerHelper.registerBuiltinResourcePack(ResourceLocation.fromNamespaceAndPath(SpawnGazeboMod.MOD_ID, "spawn_gazebo_data"), mod,
				Component.translatable("pack.spawn_gazebo.data"), ResourcePackActivationType.ALWAYS_ENABLED);
	}
}
