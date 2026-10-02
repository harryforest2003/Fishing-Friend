package io.github.harryforest2003.fishingfriend;

import io.github.harryforest2003.fishingfriend.stats.FishingStats;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;

class FishingStatsTest {
	@Test
	void catchesPerHourNeedsAMinuteOfFishing() {
		FishingStats stats = new FishingStats();
		stats.recordCatch(5);
		stats.fishingTicks = 20 * 30;
		assertEquals(-1, stats.catchesPerHour());

		stats.fishingTicks = 20 * 60 * 10;
		assertEquals(6.0, stats.catchesPerHour(), 1e-9);
	}

	@Test
	void reactionTimesIgnoreImplausibleValues() {
		FishingStats stats = new FishingStats();
		stats.recordCatch(4);
		stats.recordCatch(8);
		stats.recordEmptyCatch(6);
		stats.recordCatch(500);

		assertEquals(3, stats.catches);
		assertEquals(1, stats.emptyCatches);
		assertEquals(0.3, stats.averageReactionSeconds(), 1e-9);
		assertEquals(0.2, stats.bestReactionSeconds(), 1e-9);
	}

	@Test
	void topItemsAreSortedByCount() {
		FishingStats stats = new FishingStats();
		stats.recordItem("Salmon", 1, FishingStats.Kind.FISH);
		stats.recordItem("Cod", 1, FishingStats.Kind.FISH);
		stats.recordItem("Cod", 2, FishingStats.Kind.FISH);
		stats.recordItem("Name Tag", 1, FishingStats.Kind.TREASURE);

		assertEquals(List.of(Map.entry("Cod", 3), Map.entry("Salmon", 1)), stats.topItems(2));
		assertEquals(4, stats.fish);
		assertEquals(1, stats.treasure);
	}
}
