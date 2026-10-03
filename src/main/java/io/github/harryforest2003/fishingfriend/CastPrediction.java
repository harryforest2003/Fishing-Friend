package io.github.harryforest2003.fishingfriend;

import io.github.harryforest2003.fishingfriend.BobberTracker.Pos;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.core.BlockPos;
import net.minecraft.tags.FluidTags;

/** Runs {@link CastPredictor} against the world the player is in. */
public final class CastPrediction {
	/** Where a cast from the player's current view would land in water, or null if it wouldn't. */
	public static Pos landingPoint(ClientLevel level, LocalPlayer player) {
		BlockPos.MutableBlockPos pos = new BlockPos.MutableBlockPos();
		return CastPredictor.landingPoint(player.getX(), player.getEyeY(), player.getZ(), player.getYRot(), player.getXRot(),
			(x, y, z) -> {
				pos.set(x, y, z);
				if (level.getFluidState(pos).is(FluidTags.WATER)) {
					return CastPredictor.Surface.WATER;
				}
				return level.getBlockState(pos).getCollisionShape(level, pos).isEmpty()
					? CastPredictor.Surface.AIR
					: CastPredictor.Surface.SOLID;
			});
	}

	private CastPrediction() {
	}
}
