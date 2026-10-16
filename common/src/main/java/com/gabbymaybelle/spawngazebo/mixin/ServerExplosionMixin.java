package com.gabbymaybelle.spawngazebo.mixin;

import com.gabbymaybelle.spawngazebo.GazeboGuard;
import java.util.List;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.ServerExplosion;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Explosions of any kind (creepers, TNT, fireballs, modded blasts) leave the gazebo standing. This
 * hooks the step that breaks the blocks rather than the one that finds them, which Lithium replaces.
 */
@Mixin(ServerExplosion.class)
public abstract class ServerExplosionMixin {
	@Inject(method = "interactWithBlocks", at = @At("HEAD"))
	private void spawn_gazebo$spareGazebo(List<BlockPos> blocks, CallbackInfo ci) {
		ServerExplosion explosion = (ServerExplosion) (Object) this;
		blocks.removeIf(pos -> GazeboGuard.guards(explosion.level(), pos));
	}
}
