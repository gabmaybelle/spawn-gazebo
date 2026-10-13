package com.gabbymaybelle.spawngazebo.mixin;

import com.gabbymaybelle.spawngazebo.GazeboGuard;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.piston.PistonBaseBlock;
import net.minecraft.world.level.block.state.BlockState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** Pistons can't push or pull the spawn gazebo apart. */
@Mixin(PistonBaseBlock.class)
public abstract class PistonBaseBlockMixin {
	@Inject(method = "isPushable", at = @At("HEAD"), cancellable = true)
	private static void spawn_gazebo$spareGazebo(BlockState state, Level level, BlockPos pos, Direction direction, boolean allowDestroy,
			Direction pistonFacing, CallbackInfoReturnable<Boolean> cir) {
		if (GazeboGuard.guards(level, pos)) {
			cir.setReturnValue(false);
		}
	}
}
