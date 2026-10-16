package com.gabbymaybelle.spawngazebo.mixin;

import com.gabbymaybelle.spawngazebo.GazeboGuard;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.context.BlockPlaceContext;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** No building into the spawn gazebo outside creative. */
@Mixin(BlockItem.class)
public abstract class BlockItemMixin {
	@Inject(method = "place", at = @At("HEAD"), cancellable = true)
	private void spawn_gazebo$spareGazebo(BlockPlaceContext context, CallbackInfoReturnable<InteractionResult> cir) {
		if (GazeboGuard.protects(context.getLevel(), context.getPlayer(), context.getClickedPos())) {
			// The client already placed the block and used up the item; put its inventory right.
			if (context.getPlayer() instanceof ServerPlayer player) {
				player.containerMenu.sendAllDataToRemote();
			}
			cir.setReturnValue(InteractionResult.FAIL);
		}
	}
}
