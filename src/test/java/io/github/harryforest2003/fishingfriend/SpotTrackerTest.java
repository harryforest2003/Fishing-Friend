package io.github.harryforest2003.fishingfriend;

import io.github.harryforest2003.fishingfriend.BobberTracker.Pos;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SpotTrackerTest {
	private static final Pos SPOT = new Pos(100, 62, 100);

	private final List<String> events = new ArrayList<>();
	private SpotTracker tracker;

	@BeforeEach
	void setUp() {
		tracker = new SpotTracker(again -> events.add(again ? "still fished out" : "fished out"));
		tracker.setMoveDistance(3);
	}

	private static Pos at(double dx, double dz) {
		return new Pos(SPOT.x() + dx, SPOT.y(), SPOT.z() + dz);
	}

	@Test
	void nothingIsTooCloseBeforeASpotIsFishedOut() {
		assertFalse(tracker.hasFishedOutSpot());
		assertEquals(0, tracker.blocksToGo(SPOT));
		assertEquals(List.of(), events);
	}

	@Test
	void anEmptyCatchFishesOutTheSpot() {
		tracker.onEmptyCatch(SPOT, 0);

		assertTrue(tracker.hasFishedOutSpot());
		assertEquals(List.of("fished out"), events);
	}

	@Test
	void blocksToGoCountsDownToZeroAtTheMoveDistance() {
		tracker.onEmptyCatch(SPOT, 0);

		assertEquals(3, tracker.blocksToGo(SPOT));
		assertEquals(2, tracker.blocksToGo(at(1, 1)));
		assertEquals(1, tracker.blocksToGo(at(2.5, 0)));
		assertEquals(0, tracker.blocksToGo(at(3, 0)));
		assertEquals(0, tracker.blocksToGo(at(0, -10)));
	}

	@Test
	void notAimingAtWaterIsNeverTooClose() {
		tracker.onEmptyCatch(SPOT, 0);

		assertEquals(0, tracker.blocksToGo(null));
	}

	@Test
	void diagonalsOnlyCountTheLongerAxis() {
		tracker.onEmptyCatch(SPOT, 0);

		assertEquals(1, tracker.blocksToGo(at(2.5, 2.5)));
		assertEquals(0, tracker.blocksToGo(at(2.5, 3)));
	}

	@Test
	void catchingSomethingForgetsTheFishedOutSpot() {
		tracker.onEmptyCatch(SPOT, 0);
		tracker.onCatch(at(6, 0), 100);

		assertFalse(tracker.hasFishedOutSpot());
		assertEquals(0, tracker.blocksToGo(SPOT));
	}

	@Test
	void theServerMessageAndTheEmptyCatchFromOneReelAlertOnce() {
		tracker.onServerSaysFishedOut(SPOT, 0, 0);
		tracker.onEmptyCatch(at(0.5, 0), 20);

		assertEquals(List.of("fished out"), events);
	}

	@Test
	void fishingTheSameSpotAgainAlertsAgain() {
		tracker.onServerSaysFishedOut(SPOT, 0, 0);
		tracker.onEmptyCatch(SPOT, 20);

		// Later the player casts back into the same spot and the server complains again.
		tracker.onServerSaysFishedOut(at(1, 0), 0, 400);
		tracker.onEmptyCatch(at(1, 0), 420);

		assertEquals(List.of("fished out", "still fished out"), events);
		assertEquals(3, tracker.blocksToGo(at(1, 0)));
	}

	@Test
	void repeatedWarningsInTheSameSpotAlertEachTime() {
		for (int reel = 0; reel < 5; reel++) {
			tracker.onEmptyCatch(SPOT, reel * 200L);
		}

		assertEquals(List.of("fished out", "still fished out", "still fished out", "still fished out", "still fished out"), events);
	}

	@Test
	void aDistanceInTheServerMessageWinsIfItIsLarger() {
		tracker.onServerSaysFishedOut(SPOT, 6, 0);
		tracker.onEmptyCatch(SPOT, 20);

		assertEquals(6, tracker.requiredDistance());
		assertEquals(1, tracker.blocksToGo(at(5, 0)));
		assertEquals(0, tracker.blocksToGo(at(6, 0)));
	}

	@Test
	void reachingTheCatchLimitFishesOutTheSpot() {
		tracker.setCatchLimit(3);
		tracker.onCatch(SPOT, 0);
		tracker.onCatch(at(1, 0), 400);
		assertEquals(List.of(), events);

		tracker.onCatch(at(0, 1), 800);
		assertEquals(List.of("fished out"), events);
		assertEquals(3, tracker.blocksToGo(at(0, 1)));
	}

	@Test
	void movingBetweenCatchesRestartsTheCount() {
		tracker.setCatchLimit(3);
		tracker.onCatch(SPOT, 0);
		tracker.onCatch(at(1, 0), 400);
		tracker.onCatch(at(10, 0), 800);
		tracker.onCatch(at(11, 0), 1200);

		assertEquals(List.of(), events);
	}
}
