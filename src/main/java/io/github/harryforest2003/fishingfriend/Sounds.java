package io.github.harryforest2003.fishingfriend;

import io.github.harryforest2003.fishingfriend.config.FishingFriendConfig;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.resources.sounds.SimpleSoundInstance;
import net.minecraft.resources.Identifier;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.phys.Vec3;

public final class Sounds {
	/**
	 * Plays the alert's sound to the local player only.
	 *
	 * <p>{@code SoundManager.play} changed its return type in 1.21.6, so calling it directly would tie
	 * the jar to one side of that change. {@code playLocalSound} wraps it with a signature that has not
	 * changed. Placing the sound exactly at the listener makes it sound like a UI sound: full volume,
	 * no direction.
	 */
	public static void play(FishingFriendConfig.Alert alert) {
		Identifier id = Identifier.tryParse(alert.sound);
		if (id == null) {
			FishingFriendClient.LOGGER.warn("'{}' is not a valid sound id", alert.sound);
			return;
		}
		SoundEvent sound = SoundEvent.createVariableRangeEvent(id);
		Minecraft client = Minecraft.getInstance();
		ClientLevel level = client.level;
		if (level != null) {
			Vec3 listener = client.getSoundManager().getListenerTransform().position();
			level.playLocalSound(listener.x, listener.y, listener.z, sound, SoundSource.MASTER, alert.volume, alert.pitch, false);
		} else {
			// No world loaded (e.g. testing from the title screen). Queued sounds start on the next tick,
			// and the game is never paused without a world.
			client.getSoundManager().playDelayed(SimpleSoundInstance.forUI(sound, alert.pitch, alert.volume), 0);
		}
	}

	private Sounds() {
	}
}
