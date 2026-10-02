package io.github.harryforest2003.fishingfriend.stats;

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
import java.util.function.Consumer;

/**
 * This session's stats (since joining the current world or server) and all-time stats, which are
 * saved to {@code config/fishingfriend-stats.json}.
 */
public final class StatsStore {
	private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();

	private static FishingStats session = new FishingStats();
	private static FishingStats total = new FishingStats();
	private static boolean dirty;

	public static FishingStats session() {
		return session;
	}

	public static FishingStats total() {
		return total;
	}

	/** Applies {@code change} to both the session and all-time stats. */
	public static void record(Consumer<FishingStats> change) {
		change.accept(session);
		change.accept(total);
		dirty = true;
	}

	public static void startSession() {
		session = new FishingStats();
	}

	public static void resetTotal() {
		total = new FishingStats();
		dirty = true;
		saveIfChanged();
	}

	public static void load() {
		Path path = path();
		if (!Files.exists(path)) {
			return;
		}
		try (Reader reader = Files.newBufferedReader(path)) {
			FishingStats loaded = GSON.fromJson(reader, FishingStats.class);
			if (loaded != null) {
				loaded.sanitize();
				total = loaded;
			}
		} catch (IOException | JsonParseException e) {
			FishingFriendClient.LOGGER.warn("Could not read {}, starting fresh stats", path, e);
		}
	}

	public static void saveIfChanged() {
		if (!dirty) {
			return;
		}
		dirty = false;
		Path path = path();
		try {
			Files.createDirectories(path.getParent());
			try (Writer writer = Files.newBufferedWriter(path)) {
				GSON.toJson(total, writer);
			}
		} catch (IOException e) {
			FishingFriendClient.LOGGER.warn("Could not save {}", path, e);
		}
	}

	private static Path path() {
		return FabricLoader.getInstance().getConfigDir().resolve(FishingFriendClient.MOD_ID + "-stats.json");
	}

	private StatsStore() {
	}
}
