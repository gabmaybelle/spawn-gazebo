package com.gabbymaybelle.spawngazebo.mixin;

import com.gabbymaybelle.spawngazebo.GazeboGuard;
import net.minecraft.world.level.Explosion;
import net.minecraft.world.level.Level;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Explosions of any kind (creepers, TNT, fireballs, modded blasts) leave the gazebo standing. This
 * hooks the step that breaks the blocks rather than the one that finds them, which Lithium replaces.
 */
@Mixin(Explosion.class)
public abstract class ExplosionMixin {
	@Shadow
	@Final
	private Level level;

	@Inject(method = "finalizeExplosion", at = @At("HEAD"))
	private void spawn_gazebo$spareGazebo(boolean spawnParticles, CallbackInfo ci) {
		((Explosion) (Object) this).getToBlow().removeIf(pos -> GazeboGuard.guards(level, pos));
	}
}
