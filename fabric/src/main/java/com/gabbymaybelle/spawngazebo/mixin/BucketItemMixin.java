package com.gabbymaybelle.spawngazebo.mixin;

import com.gabbymaybelle.spawngazebo.GazeboGuard;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.BucketItem;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.BlockHitResult;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * No pouring water or lava into the spawn gazebo, by hand or by dispenser. This copy is for Fabric,
 * where emptyContents has vanilla's arguments; NeoForge adds one (see its own BucketItemMixin).
 */
@Mixin(BucketItem.class)
public abstract class BucketItemMixin {
	@Inject(method = "emptyContents(Lnet/minecraft/world/entity/player/Player;Lnet/minecraft/world/level/Level;Lnet/minecraft/core/BlockPos;Lnet/minecraft/world/phys/BlockHitResult;)Z", at = @At("HEAD"), cancellable = true)
	private void spawn_gazebo$spareGazebo(Player user, Level level, BlockPos pos, BlockHitResult hit, CallbackInfoReturnable<Boolean> cir) {
		if (GazeboGuard.protects(level, user, pos)) {
			if (user instanceof ServerPlayer player) {
				player.containerMenu.sendAllDataToRemote();
			}
			cir.setReturnValue(false);
		}
	}
}
