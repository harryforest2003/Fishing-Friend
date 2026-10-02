package io.github.harryforest2003.fishingfriend.stats;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** Fishing counters for one period (a session or all time). Serialised as-is with Gson. */
public final class FishingStats {
	public enum Kind { FISH, TREASURE, JUNK }

	/** Reaction times above this (in ticks) are not real reactions, e.g. a catch noticed via the inventory. */
	static final int MAX_REACTION_TICKS = 60;

	public long fishingTicks;
	public int casts;
	public int bites;
	public int catches;
	public int missedBites;
	public int reelsWithoutBite;
	public int emptyCatches;
	public int lineSnaps;
	public int fish;
	public int treasure;
	public int junk;
	public long reactionTicksTotal;
	public int reactions;
	public int bestReactionTicks = -1;
	/** Caught items by display name. */
	public Map<String, Integer> items = new LinkedHashMap<>();

	public void recordCatch(int reactionTicks) {
		catches++;
		recordReaction(reactionTicks);
	}

	public void recordEmptyCatch(int reactionTicks) {
		emptyCatches++;
		recordReaction(reactionTicks);
	}

	public void recordItem(String name, int count, Kind kind) {
		items.merge(name, count, Integer::sum);
		switch (kind) {
			case FISH -> fish += count;
			case TREASURE -> treasure += count;
			case JUNK -> junk += count;
		}
	}

	/** Catches per hour of time with a bobber out, or -1 until there is a minute of fishing to go on. */
	public double catchesPerHour() {
		return fishingTicks < 20 * 60 ? -1 : catches * 72_000.0 / fishingTicks;
	}

	/** Average seconds from a bite to pressing use, or -1 if there is no data. */
	public double averageReactionSeconds() {
		return reactions == 0 ? -1 : reactionTicksTotal / (reactions * 20.0);
	}

	public double bestReactionSeconds() {
		return bestReactionTicks < 0 ? -1 : bestReactionTicks / 20.0;
	}

	/** The {@code limit} most caught items, most first. */
	public List<Map.Entry<String, Integer>> topItems(int limit) {
		List<Map.Entry<String, Integer>> sorted = new ArrayList<>(items.entrySet());
		sorted.sort(Map.Entry.<String, Integer>comparingByValue().reversed());
		return sorted.subList(0, Math.min(limit, sorted.size()));
	}

	private void recordReaction(int ticks) {
		if (ticks < 0 || ticks > MAX_REACTION_TICKS) {
			return;
		}
		reactionTicksTotal += ticks;
		reactions++;
		if (bestReactionTicks < 0 || ticks < bestReactionTicks) {
			bestReactionTicks = ticks;
		}
	}

	void sanitize() {
		if (items == null) {
			items = new LinkedHashMap<>();
		}
	}
}
