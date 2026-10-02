package io.github.harryforest2003.fishingfriend;

import io.github.harryforest2003.fishingfriend.BobberTracker.Pos;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;

class BobberTrackerTest {
	private static final int HOOK = 42;
	private static final Pos WATER = new Pos(100, 62, 100);
	private static final Pos PLAYER = new Pos(100, 63, 95);

	private final List<String> events = new ArrayList<>();
	private BobberTracker<String> tracker;
	private long now;
	private int inventory = 10;
	private boolean holdingRod = true;
	private boolean pressUse;
	private Pos player = PLAYER;

	@BeforeEach
	void setUp() {
		tracker = new BobberTracker<>(new BobberTracker.Listener<>() {
			@Override
			public void onCast() {
				events.add("cast@" + now);
			}

			@Override
			public void onLandedInWater(Pos at) {
				events.add("water@" + now);
			}

			@Override
			public void onLandedOnGround() {
				events.add("ground@" + now);
			}

			@Override
			public void onHookedEntity(String name) {
				events.add("hooked " + name + "@" + now);
			}

			@Override
			public void onBite() {
				events.add("bite@" + now);
			}

			@Override
			public void onBiteMissed() {
				events.add("missed@" + now);
			}

			@Override
			public void onReeledInWithoutBite() {
				events.add("early@" + now);
			}

			@Override
			public void onCatch(Pos at, String item, int reactionTicks) {
				events.add("catch " + item + " in " + reactionTicks + "@" + now);
			}

			@Override
			public void onEmptyCatch(Pos at, int reactionTicks) {
				events.add("empty in " + reactionTicks + "@" + now);
			}

			@Override
			public void onLineSnapped() {
				events.add("snapped@" + now);
			}
		});
		tracker.setEmptyCatchWaitTicks(20);
	}

	private void tick(BobberTracker.Hook hook) {
		now++;
		tracker.tick(now, hook, new BobberTracker.Player(player, holdingRod, pressUse, inventory));
		pressUse = false;
	}

	private static BobberTracker.Hook bobbing(boolean biting) {
		return new BobberTracker.Hook(HOOK, WATER, biting, true, false, null);
	}

	private static BobberTracker.Hook bobbing(int id, boolean biting) {
		return new BobberTracker.Hook(id, WATER, biting, true, false, null);
	}

	/** A bobber that has landed in water and settled, without the cast and landing events. */
	private void castAndSettle() {
		for (int i = 0; i < BobberTracker.SETTLE_TICKS; i++) {
			tick(bobbing(false));
		}
		events.clear();
	}

	private void reelIn() {
		pressUse = true;
		tick(bobbing(true));
	}

	private void idle(int ticks) {
		for (int i = 0; i < ticks; i++) {
			tick(null);
		}
	}

	@Test
	void reportsTheCastAndWhereItLanded() {
		tick(new BobberTracker.Hook(HOOK, WATER, false, false, false, null));
		tick(new BobberTracker.Hook(HOOK, WATER, false, false, false, null));
		for (int i = 0; i < BobberTracker.SETTLE_TICKS; i++) {
			tick(bobbing(false));
		}

		assertEquals(List.of("cast@1", "water@7"), events);
	}

	@Test
	void warnsOnceWhenTheBobberLandsOnTheGround() {
		for (int i = 0; i < 20; i++) {
			tick(new BobberTracker.Hook(HOOK, WATER, false, false, true, null));
		}

		assertEquals(List.of("cast@1", "ground@5"), events);
	}

	@Test
	void reportsAHookedMobOnce() {
		tick(new BobberTracker.Hook(HOOK, WATER, false, false, false, null));
		tick(new BobberTracker.Hook(HOOK, WATER, false, false, false, "Zombie"));
		tick(new BobberTracker.Hook(HOOK, WATER, false, false, false, "Zombie"));

		assertEquals(List.of("cast@1", "hooked Zombie@2"), events);
	}

	@Test
	void dingsOnceWhenTheBobberStartsBiting() {
		castAndSettle();
		tick(bobbing(true));
		tick(bobbing(true));
		tick(bobbing(true));

		assertEquals(List.of("bite@6"), events);
	}

	@Test
	void aBiteThatEndsWithoutReelingInIsMissed() {
		castAndSettle();
		tick(bobbing(true));
		tick(bobbing(false));
		tick(bobbing(true));

		assertEquals(List.of("bite@6", "missed@7", "bite@8"), events);
	}

	@Test
	void reelingInOnTimeWithTheItemSpawningFirstIsACatch() {
		castAndSettle();
		tick(bobbing(true));
		tick(bobbing(true));
		reelIn();
		// The server spawns the fish at the bobber before removing the bobber.
		tracker.onItemSpawned(now, new Pos(100.2, 62.1, 99.9), "cod");
		tick(null);
		idle(40);

		assertEquals(List.of("bite@6", "catch cod in 2@9"), events);
	}

	@Test
	void reelingInOnTimeWithTheItemSpawningAfterIsACatch() {
		castAndSettle();
		tick(bobbing(true));
		reelIn();
		tick(null);
		tick(null);
		tracker.onItemSpawned(now, WATER, "salmon");
		idle(40);

		assertEquals(List.of("bite@6", "catch salmon in 1@9"), events);
	}

	@Test
	void reelingInOnTimeWithNothingSpawningIsAnEmptyCatch() {
		castAndSettle();
		tick(bobbing(true));
		reelIn();
		tick(null);
		idle(40);

		assertEquals(List.of("bite@6", "empty in 1@28"), events);
	}

	@Test
	void anItemSpawningFarAwayDoesNotCountAsTheCatch() {
		castAndSettle();
		tick(bobbing(true));
		tracker.onItemSpawned(now, new Pos(120, 62, 100), "far");
		reelIn();
		tick(null);
		tracker.onItemSpawned(now, new Pos(100, 62, 130), "also far");
		idle(40);

		assertEquals(List.of("bite@6", "empty in 1@28"), events);
	}

	@Test
	void anOldItemNearTheBobberDoesNotCountAsTheCatch() {
		castAndSettle();
		tracker.onItemSpawned(now, WATER, "old");
		for (int i = 0; i < 10; i++) {
			tick(bobbing(false));
		}
		tick(bobbing(true));
		reelIn();
		tick(null);
		idle(40);

		assertEquals(List.of("bite@16", "empty in 1@38"), events);
	}

	@Test
	void catchesThatGoStraightIntoTheInventoryCount() {
		castAndSettle();
		tick(bobbing(true));
		reelIn();
		tick(null);
		inventory++;
		tick(null);
		idle(40);

		assertEquals(List.of("bite@6", "catch null in 1@9"), events);
	}

	@Test
	void reelingInFromTheWaterWithoutABiteIsEarly() {
		castAndSettle();
		pressUse = true;
		tick(bobbing(false));
		tick(null);
		idle(40);

		assertEquals(List.of("early@7"), events);
	}

	@Test
	void reelingInAfterTheBiteEndedIsNotAnEmptyCatch() {
		castAndSettle();
		tick(bobbing(true));
		tick(bobbing(false));
		pressUse = true;
		tick(bobbing(false));
		tick(null);
		idle(40);

		assertEquals(List.of("bite@6", "missed@7", "early@9"), events);
	}

	@Test
	void switchingAwayFromTheRodDuringABiteSaysNothing() {
		castAndSettle();
		tick(bobbing(true));
		holdingRod = false;
		tick(null);
		idle(40);

		assertEquals(List.of("bite@6"), events);
	}

	@Test
	void walkingTooFarAwaySnapsTheLine() {
		castAndSettle();
		player = new Pos(100, 63, 131);
		tick(bobbing(false));
		tick(null);

		assertEquals(List.of("snapped@7"), events);
	}

	@Test
	void reelingInFromFarAwayIsNotASnap() {
		castAndSettle();
		player = new Pos(100, 63, 131);
		pressUse = true;
		tick(bobbing(false));
		tick(null);

		assertEquals(List.of("early@7"), events);
	}

	@Test
	void castingANewBobberRightAwayStillReportsTheOldOne() {
		castAndSettle();
		tick(bobbing(true));
		reelIn();
		for (int i = 0; i < 40; i++) {
			tick(bobbing(HOOK + 1, false));
		}

		assertEquals(List.of("bite@6", "cast@8", "water@12", "empty in 1@28"), events);
	}

	@Test
	void remembersRecentRodUseForServerMessages() {
		assertEquals(false, tracker.usedRodWithin(now, 100));
		tick(bobbing(false));
		assertEquals(true, tracker.usedRodWithin(now, 100));

		for (int i = 0; i < 150; i++) {
			tick(bobbing(false));
		}
		assertEquals(false, tracker.usedRodWithin(now, 100));

		pressUse = true;
		tick(bobbing(false));
		tick(null);
		assertEquals(true, tracker.usedRodWithin(now, 100));
	}

	@Test
	void reelingInCountsAsRodUseSinceAnEarlierTick() {
		for (int i = 0; i < 150; i++) {
			tick(bobbing(false));
		}
		long messageArrived = now;
		assertEquals(true, tracker.isOut());
		assertEquals(false, tracker.usedRodSince(messageArrived));

		tick(null);
		assertEquals(false, tracker.isOut());
		assertEquals(true, tracker.usedRodSince(messageArrived));
	}

	@Test
	void clickingWithSomethingElseIsNotRodUse() {
		holdingRod = false;
		pressUse = true;
		tick(null);

		assertEquals(false, tracker.usedRodWithin(now, 100));
	}

	@Test
	void resetDropsAPendingEmptyCatch() {
		castAndSettle();
		tick(bobbing(true));
		reelIn();
		tick(null);
		tracker.reset();
		idle(40);

		assertEquals(List.of("bite@6"), events);
	}
}
