package io.github.harryforest2003.fishingfriend.test.mixin;

import io.github.harryforest2003.fishingfriend.Sounds;
import io.github.harryforest2003.fishingfriend.config.FishingFriendConfig;
import io.github.harryforest2003.fishingfriend.test.PlayedAlerts;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(value = Sounds.class, remap = false)
public abstract class SoundsMixin {
	@Inject(method = "play", at = @At("HEAD"))
	private static void fishingfriendTest$record(FishingFriendConfig.Alert alert, CallbackInfo ci) {
		PlayedAlerts.SOUNDS.add(alert.sound);
	}
}
