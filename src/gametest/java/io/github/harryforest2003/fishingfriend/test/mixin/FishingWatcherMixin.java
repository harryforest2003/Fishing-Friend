package io.github.harryforest2003.fishingfriend.test.mixin;

import io.github.harryforest2003.fishingfriend.test.TickTimings;
import net.minecraft.client.Minecraft;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Times the mod's per-tick code. */
@Mixin(targets = "io.github.harryforest2003.fishingfriend.FishingWatcher", remap = false)
public abstract class FishingWatcherMixin {
	@Inject(method = "tick", at = @At("HEAD"))
	private void fishingfriendTest$start(Minecraft client, CallbackInfo ci) {
		TickTimings.start();
	}

	@Inject(method = "tick", at = @At("RETURN"))
	private void fishingfriendTest$end(Minecraft client, CallbackInfo ci) {
		TickTimings.end();
	}
}
