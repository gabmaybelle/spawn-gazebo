package com.gabbymaybelle.spawngazebo.filmmixin;

import com.gabbymaybelle.spawngazebo.Film;
import net.minecraft.client.Camera;
import net.minecraft.world.phys.Vec3;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Filming only (the game-test mod, never the mod's jar): while {@link Film#front} is on, the view is
 * drawn from its eye towards its look point instead of from behind the player, so a shot can face her.
 * Right after the camera is placed behind her, before the game works out what is in view: moved later,
 * the world was still culled for her own view and what was behind her vanished from the shot.
 */
@Mixin(Camera.class)
abstract class FilmCameraMixin {
	@Shadow
	private boolean detached;

	@Shadow
	protected abstract void setRotation(float yRot, float xRot);

	@Shadow
	protected abstract void setPosition(Vec3 position);

	@Inject(method = "alignWithEntity", at = @At("TAIL"))
	private void film$front(float partialTicks, CallbackInfo ci) {
		Vec3 eye = Film.FRONT_EYE, look = Film.FRONT_LOOK;
		if (!Film.FRONT_ON || eye == null || look == null) {
			return;
		}
		Vec3 d = look.subtract(eye);
		float yaw = (float) Math.toDegrees(Math.atan2(-d.x, d.z));
		float pitch = (float) -Math.toDegrees(Math.atan2(d.y, Math.hypot(d.x, d.z)));
		detached = true;
		setRotation(yaw, pitch);
		setPosition(eye);
	}
}
