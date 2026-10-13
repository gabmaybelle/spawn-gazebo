package com.gabbymaybelle.spawngazebo.client;

import com.gabbymaybelle.spawngazebo.GazeboGuard;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;

/** Client setup: hears from the server where the gazebo is, so a placement there is refused on this side too. */
public class SpawnGazeboClient implements ClientModInitializer {
	@Override
	public void onInitializeClient() {
		ClientPlayNetworking.registerGlobalReceiver(GazeboGuard.Box.TYPE, (payload, context) ->
				GazeboGuard.setClientBox(payload.box().orElse(null)));
		ClientPlayConnectionEvents.DISCONNECT.register((handler, client) -> GazeboGuard.setClientBox(null));
	}
}
