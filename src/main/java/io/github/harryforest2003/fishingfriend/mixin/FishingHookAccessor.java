package io.github.harryforest2003.fishingfriend.mixin;

import net.minecraft.world.entity.projectile.FishingHook;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

@Mixin(FishingHook.class)
public interface FishingHookAccessor {
	/** Synced from the server; true while a fish is on the line and reeling in will catch it. */
	@Accessor("biting")
	boolean fishingfriend$isBiting();
}
