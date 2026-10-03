package io.github.harryforest2003.fishingfriend.test.mixin;

import io.github.harryforest2003.fishingfriend.compat.VersionCompat;
import io.github.harryforest2003.fishingfriend.test.PlayedAlerts;
import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(value = VersionCompat.class, remap = false)
public abstract class VersionCompatMixin {
	@Inject(method = "showOverlayMessage", at = @At("HEAD"))
	private static void fishingfriendTest$record(Minecraft client, Component message, CallbackInfo ci) {
		PlayedAlerts.MESSAGES.add(message.getString());
	}
}
