package io.github.harryforest2003.fishingfriend.config;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonParseException;
import com.google.gson.annotations.SerializedName;
import io.github.harryforest2003.fishingfriend.FishingFriendClient;
import net.fabricmc.loader.api.FabricLoader;

import java.io.IOException;
import java.io.Reader;
import java.io.Writer;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

/** Settings stored in {@code config/fishingfriend.json}. Edited live by the config screen. */
public final class FishingFriendConfig {
	private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
	private static FishingFriendConfig instance = new FishingFriendConfig();

	public boolean enabled = true;

	/** Sound played when a fish bites. */
	public Alert bite = new Alert(SoundPresets.BELL);
	/** Sound played when the current spot is fished out. */
	@SerializedName(value = "fishedOut", alternate = "emptyCatch")
	public Alert fishedOut = new Alert(SoundPresets.VILLAGER_NO);

	/** "Reel in now!", "Spot fished out" and similar messages above the hotbar. */
	@SerializedName(value = "actionBarAlerts", alternate = "showMessages")
	public boolean actionBarAlerts = true;
	/** Keep reminding the player to move while they aim at or fish in a fished-out spot. */
	public boolean moveReminder = true;
	/** Action bar warnings when the bobber lands on the ground, hooks a mob, or the line snaps. */
	public boolean bobberWarnings = true;

	/** How far (in blocks) the next spot has to be from a fished-out one. Match your server's rule. */
	public int moveDistance = 3;
	/** Catches allowed in one spot before the server stops giving fish, or 0 if unknown. */
	public int catchesPerSpot = 0;

	/** Watch server chat for overfishing messages. */
	public boolean readServerMessages = true;
	/** Server messages containing any of these mean the spot is fished out. */
	public List<String> fishedOutPhrases = new ArrayList<>(List.of("overfishing", "overfished"));
	/** Server messages containing any of these mean the spot is about to run out. */
	public List<String> runningLowPhrases = new ArrayList<>(List.of("many fish left", "fish are running low"));

	/** Ticks to wait after reeling in for the catch before deciding nothing was caught. */
	public int emptyCatchWaitTicks = 20;

	public static final class Alert {
		/** Whether the sound plays. */
		public boolean enabled = true;
		/** Any sound event id, e.g. {@code minecraft:block.note_block.bell}. */
		public String sound;
		/** 0 to 1. */
		public float volume = 1.0f;
		/** 0.5 to 2. */
		public float pitch = 1.0f;

		Alert(String sound) {
			this.sound = sound;
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
			bite = new Alert(SoundPresets.BELL);
		}
		if (fishedOut == null) {
			fishedOut = new Alert(SoundPresets.VILLAGER_NO);
		}
		bite.sanitize(SoundPresets.BELL);
		fishedOut.sanitize(SoundPresets.VILLAGER_NO);
		if (fishedOutPhrases == null) {
			fishedOutPhrases = new ArrayList<>();
		}
		if (runningLowPhrases == null) {
			runningLowPhrases = new ArrayList<>();
		}
		moveDistance = clamp(moveDistance, 1, 64);
		catchesPerSpot = clamp(catchesPerSpot, 0, 100);
		emptyCatchWaitTicks = clamp(emptyCatchWaitTicks, 1, 200);
	}

	private static Path path() {
		return FabricLoader.getInstance().getConfigDir().resolve(FishingFriendClient.MOD_ID + ".json");
	}

	private static float clamp(float value, float min, float max) {
		return Math.max(min, Math.min(max, value));
	}

	private static int clamp(int value, int min, int max) {
		return Math.max(min, Math.min(max, value));
	}
}
