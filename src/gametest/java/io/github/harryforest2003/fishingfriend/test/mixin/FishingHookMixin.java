package io.github.harryforest2003.fishingfriend.test.mixin;

import net.minecraft.world.entity.projectile.FishingHook;
import net.minecraft.world.item.ItemStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** Lets a test imitate a server overfishing rule: the bobber is pulled in on time but no catch spawns. */
@Mixin(FishingHook.class)
public abstract class FishingHookMixin {
	public static volatile boolean swallowCatch;

	@Shadow
	private int nibble;

	@Inject(method = "retrieve", at = @At("HEAD"), cancellable = true)
	private void fishingfriendTest$swallowCatch(ItemStack rod, CallbackInfoReturnable<Integer> cir) {
		if (swallowCatch && nibble > 0) {
			((FishingHook) (Object) this).discard();
			cir.setReturnValue(1);
		}
	}
}
