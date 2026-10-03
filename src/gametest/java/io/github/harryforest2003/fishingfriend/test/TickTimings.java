package io.github.harryforest2003.fishingfriend.test;

import java.util.Arrays;
import java.util.Locale;

/** How long the mod's per-tick code takes, recorded by {@code FishingWatcherMixin} on the client thread. */
public final class TickTimings {
	private static long started;
	private static long[] durations = new long[4096];
	private static int count;

	public static synchronized void start() {
		started = System.nanoTime();
	}

	public static synchronized void end() {
		if (count == durations.length) {
			durations = Arrays.copyOf(durations, count * 2);
		}
		durations[count++] = System.nanoTime() - started;
	}

	/** Average, 99th percentile and worst tick in microseconds, as one log line. */
	public static synchronized String summary() {
		long[] sorted = Arrays.copyOf(durations, count);
		Arrays.sort(sorted);
		double average = Arrays.stream(sorted).average().orElse(0) / 1000.0;
		double p99 = count == 0 ? 0 : sorted[Math.min(count - 1, (int) (count * 0.99))] / 1000.0;
		double max = count == 0 ? 0 : sorted[count - 1] / 1000.0;
		return String.format(Locale.ROOT, "FISHING_FRIEND_TICK_COST ticks=%d avg_us=%.1f p99_us=%.1f max_us=%.1f", count, average, p99, max);
	}

	/** Average microseconds per tick. */
	public static synchronized double averageMicros() {
		long total = 0;
		for (int i = 0; i < count; i++) {
			total += durations[i];
		}
		return count == 0 ? 0 : total / (count * 1000.0);
	}

	private TickTimings() {
	}
}
