package com.gabbymaybelle.spawngazebo.mixin;

import com.gabbymaybelle.spawngazebo.GazeboGuard;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.FireBlock;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Fire can't burn away the gazebo's fences and benches. This copy is for NeoForge, which
 * adds the direction the fire spread from to checkBurnOut (see Fabric's own FireBlockMixin).
 */
@Mixin(FireBlock.class)
public abstract class FireBlockMixin {
	@Inject(method = "checkBurnOut", at = @At("HEAD"), cancellable = true)
	private void spawn_gazebo$spareGazebo(Level level, BlockPos pos, int chance, RandomSource random, int age, Direction face, CallbackInfo ci) {
		if (GazeboGuard.guards(level, pos)) {
			ci.cancel();
		}
	}
}
