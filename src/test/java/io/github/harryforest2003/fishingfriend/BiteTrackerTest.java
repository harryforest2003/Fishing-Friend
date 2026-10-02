package io.github.harryforest2003.fishingfriend;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;

class BiteTrackerTest {
	private static final int HOOK = 42;

	private final List<String> events = new ArrayList<>();
	private BiteTracker tracker;
	private long now;
	private int inventory = 10;

	@BeforeEach
	void setUp() {
		tracker = new BiteTracker(new BiteTracker.Listener() {
			@Override
			public void onBite() {
				events.add("bite@" + now);
			}

			@Override
			public void onEmptyCatch() {
				events.add("empty@" + now);
			}

			@Override
			public void onCatch() {
				events.add("catch@" + now);
			}
		});
		tracker.setEmptyCatchWaitTicks(20);
	}

	private void tick(BiteTracker.Hook hook, boolean holdingRod) {
		now++;
		tracker.tick(now, hook, holdingRod, inventory);
	}

	private void tick(BiteTracker.Hook hook) {
		tick(hook, true);
	}

	private static BiteTracker.Hook hook(int id, boolean biting) {
		return new BiteTracker.Hook(id, biting, 100, 62, 100);
	}

	private void wait(int ticks) {
		for (int i = 0; i < ticks; i++) {
			tick(null);
		}
	}

	@Test
	void dingsOnceWhenTheBobberStartsBiting() {
		tick(hook(HOOK, false));
		tick(hook(HOOK, false));
		tick(hook(HOOK, true));
		tick(hook(HOOK, true));
		tick(hook(HOOK, true));

		assertEquals(List.of("bite@3"), events);
	}

	@Test
	void dingsAgainForEachNewBite() {
		tick(hook(HOOK, true));
		tick(hook(HOOK, false));
		tick(hook(HOOK, true));

		assertEquals(List.of("bite@1", "bite@3"), events);
	}

	@Test
	void reelingInOnTimeWithTheItemSpawningFirstIsACatch() {
		tick(hook(HOOK, true));
		// The server spawns the fish at the bobber before removing the bobber.
		tracker.onItemSpawned(now, 100.2, 62.1, 99.9);
		tick(null);
		wait(40);

		assertEquals(List.of("bite@1", "catch@2"), events);
	}

	@Test
	void reelingInOnTimeWithTheItemSpawningAfterIsACatch() {
		tick(hook(HOOK, true));
		tick(null);
		tick(null);
		tracker.onItemSpawned(now, 100, 62, 100);
		wait(40);

		assertEquals(List.of("bite@1", "catch@3"), events);
	}

	@Test
	void reelingInOnTimeWithNothingSpawningWarnsAfterTheWait() {
		tick(hook(HOOK, true));
		tick(null);
		wait(40);

		assertEquals(List.of("bite@1", "empty@22"), events);
	}

	@Test
	void anItemSpawningFarAwayDoesNotCountAsTheCatch() {
		tick(hook(HOOK, true));
		tracker.onItemSpawned(now, 120, 62, 100);
		tick(null);
		tracker.onItemSpawned(now, 100, 62, 130);
		wait(40);

		assertEquals(List.of("bite@1", "empty@22"), events);
	}

	@Test
	void anOldItemNearTheBobberDoesNotCountAsTheCatch() {
		tick(hook(HOOK, false));
		tracker.onItemSpawned(now, 100, 62, 100);
		for (int i = 0; i < 10; i++) {
			tick(hook(HOOK, false));
		}
		tick(hook(HOOK, true));
		tick(null);
		wait(40);

		assertEquals(List.of("bite@12", "empty@33"), events);
	}

	@Test
	void catchesThatGoStraightIntoTheInventoryCount() {
		tick(hook(HOOK, true));
		tick(null);
		tick(null);
		inventory++;
		tick(null);
		wait(40);

		assertEquals(List.of("bite@1", "catch@4"), events);
	}

	@Test
	void reelingInWithoutABiteNeverWarns() {
		tick(hook(HOOK, false));
		tick(hook(HOOK, false));
		tick(null);
		wait(40);

		assertEquals(List.of(), events);
	}

	@Test
	void reelingInAfterTheBiteEndedNeverWarns() {
		tick(hook(HOOK, true));
		tick(hook(HOOK, false));
		tick(null);
		wait(40);

		assertEquals(List.of("bite@1"), events);
	}

	@Test
	void switchingAwayFromTheRodDuringABiteNeverWarns() {
		tick(hook(HOOK, true));
		tick(null, false);
		wait(40);

		assertEquals(List.of("bite@1"), events);
	}

	@Test
	void castingANewBobberRightAwayStillReportsTheOldOne() {
		tick(hook(HOOK, true));
		tick(hook(HOOK + 1, false));
		for (int i = 0; i < 40; i++) {
			tick(hook(HOOK + 1, false));
		}

		assertEquals(List.of("bite@1", "empty@22"), events);
	}

	@Test
	void resetDropsAPendingWarning() {
		tick(hook(HOOK, true));
		tick(null);
		tracker.reset();
		wait(40);

		assertEquals(List.of("bite@1"), events);
	}
}
