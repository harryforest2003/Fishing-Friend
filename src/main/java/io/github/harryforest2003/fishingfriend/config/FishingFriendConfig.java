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
	/** Bumped when defaults change in a way existing config files should pick up. */
	private static final int CURRENT_VERSION = 2;
	private static FishingFriendConfig instance = new FishingFriendConfig();

	/** 0 for files written before 1.2.0. */
	public int configVersion;
	public boolean enabled = true;

	/** Sound played when a fish bites. */
	public Alert bite = new Alert(SoundPresets.BELL);
	/** Sound played when the current spot is fished out. */
	@SerializedName(value = "fishedOut", alternate = "emptyCatch")
	public Alert fishedOut = new Alert(SoundPresets.VILLAGER_NO);

	/** "Reel in now!" above the hotbar when a fish bites. */
	@SerializedName(value = "reelInMessage", alternate = {"actionBarAlerts", "showMessages"})
	public boolean reelInMessage = true;
	/** How much further to cast, shown while aiming at or fishing in a fished-out spot. */
	@SerializedName(value = "distanceMessage", alternate = "moveReminder")
	public boolean distanceMessage = true;
	/** Action bar warnings when the bobber lands on the ground, hooks a mob, a fish gets away, or the line snaps. */
	public boolean bobberWarnings = false;

	/** How far (in blocks) the next spot has to be from a fished-out one. Match your server's rule. */
	public int moveDistance = 3;
	/** Catches allowed in one spot before the server stops giving fish, or 0 if unknown. */
	public int catchesPerSpot = 0;

	/** Watch server chat for overfishing messages. */
	public boolean readServerMessages = true;
	/** Server messages containing any of these mean the spot is fished out. */
	public List<String> fishedOutPhrases = new ArrayList<>(List.of("overfishing", "overfished"));

	/** Ticks to wait after reeling in for the catch before deciding nothing was caught. */
	public int emptyCatchWaitTicks = 20;
	/**
	 * An empty reel only means the spot is fished out if it came this many ticks or fewer after the bite.
	 * Slower reels may simply have missed the bite window.
	 */
	public int maxOnTimeReactionTicks = 15;

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
					instance.upgrade();
				}
			} catch (IOException | JsonParseException e) {
				FishingFriendClient.LOGGER.warn("Could not read {}, using defaults", path, e);
			}
		}
		instance.configVersion = CURRENT_VERSION;
		instance.sanitize();
		instance.save();
	}

	private void upgrade() {
		if (configVersion < 2) {
			// 1.2.0 trimmed the action bar down to "Reel in now!" and the distance message.
			bobberWarnings = false;
		}
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
		moveDistance = clamp(moveDistance, 1, 64);
		catchesPerSpot = clamp(catchesPerSpot, 0, 100);
		emptyCatchWaitTicks = clamp(emptyCatchWaitTicks, 1, 200);
		maxOnTimeReactionTicks = clamp(maxOnTimeReactionTicks, 1, 40);
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
