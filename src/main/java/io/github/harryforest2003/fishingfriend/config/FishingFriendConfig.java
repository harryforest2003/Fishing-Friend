package io.github.harryforest2003.fishingfriend.config;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonParseException;
import io.github.harryforest2003.fishingfriend.FishingFriendClient;
import net.fabricmc.loader.api.FabricLoader;

import java.io.IOException;
import java.io.Reader;
import java.io.Writer;
import java.nio.file.Files;
import java.nio.file.Path;

/** Settings stored in {@code config/fishingfriend.json}. Edited live by the config screen. */
public final class FishingFriendConfig {
	private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
	private static FishingFriendConfig instance = new FishingFriendConfig();

	public boolean enabled = true;
	/** Show a short message above the hotbar alongside each sound. */
	public boolean showMessages = true;
	public Alert bite = new Alert(SoundPresets.BELL, 1.0f, 1.0f);
	public Alert emptyCatch = new Alert(SoundPresets.VILLAGER_NO, 1.0f, 1.0f);
	/** Ticks to wait after reeling in for the catch before warning that nothing was caught. */
	public int emptyCatchWaitTicks = 20;

	public static final class Alert {
		public boolean enabled = true;
		/** Any sound event id, e.g. {@code minecraft:block.note_block.bell}. */
		public String sound;
		/** 0 to 1. */
		public float volume;
		/** 0.5 to 2. */
		public float pitch;

		Alert(String sound, float volume, float pitch) {
			this.sound = sound;
			this.volume = volume;
			this.pitch = pitch;
		}

		private void sanitize(String defaultSound) {
			if (sound == null || sound.isBlank()) {
				sound = defaultSound;
			}
			volume = clamp(volume, 0f, 1f);
			pitch = clamp(pitch, 0.5f, 2f);
		}
	}

	public static FishingFriendConfig get() {
		return instance;
	}

	public static void load() {
		Path path = path();
		if (Files.exists(path)) {
			try (Reader reader = Files.newBufferedReader(path)) {
				FishingFriendConfig loaded = GSON.fromJson(reader, FishingFriendConfig.class);
				if (loaded != null) {
					instance = loaded;
				}
			} catch (IOException | JsonParseException e) {
				FishingFriendClient.LOGGER.warn("Could not read {}, using defaults", path, e);
			}
		}
		instance.sanitize();
		instance.save();
	}

	public void save() {
		Path path = path();
		try {
			Files.createDirectories(path.getParent());
			try (Writer writer = Files.newBufferedWriter(path)) {
				GSON.toJson(this, writer);
			}
		} catch (IOException e) {
			FishingFriendClient.LOGGER.warn("Could not save {}", path, e);
		}
	}

	private void sanitize() {
		if (bite == null) {
			bite = new Alert(SoundPresets.BELL, 1.0f, 1.0f);
		}
		if (emptyCatch == null) {
			emptyCatch = new Alert(SoundPresets.VILLAGER_NO, 1.0f, 1.0f);
		}
		bite.sanitize(SoundPresets.BELL);
		emptyCatch.sanitize(SoundPresets.VILLAGER_NO);
		emptyCatchWaitTicks = Math.max(1, Math.min(200, emptyCatchWaitTicks));
	}

	private static Path path() {
		return FabricLoader.getInstance().getConfigDir().resolve(FishingFriendClient.MOD_ID + ".json");
	}

	private static float clamp(float value, float min, float max) {
		return Math.max(min, Math.min(max, value));
	}
}
