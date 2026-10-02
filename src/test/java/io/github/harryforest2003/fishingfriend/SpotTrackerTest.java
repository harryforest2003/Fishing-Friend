package io.github.harryforest2003.fishingfriend;

import io.github.harryforest2003.fishingfriend.BobberTracker.Pos;
import io.github.harryforest2003.fishingfriend.SpotTracker.Status;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;

class SpotTrackerTest {
	private static final Pos SPOT = new Pos(100, 62, 100);

	private final List<String> events = new ArrayList<>();
	private SpotTracker tracker;

	@BeforeEach
	void setUp() {
		tracker = new SpotTracker(new SpotTracker.Listener() {
			@Override
			public void onSpotRunningLow() {
				events.add("low");
			}

			@Override
			public void onSpotFishedOut() {
				events.add("fished out");
			}

			@Override
			public void onFarEnough() {
				events.add("far enough");
			}
		});
		tracker.setMoveDistance(3);
	}

	private static Pos at(double dx, double dz) {
		return new Pos(SPOT.x() + dx, SPOT.y(), SPOT.z() + dz);
	}

	@Test
	void nothingToSayBeforeASpotIsFishedOut() {
		assertEquals(Status.NONE, tracker.update(SPOT));
		assertEquals(Status.NONE, tracker.update(null));
		assertEquals(List.of(), events);
	}

	@Test
	void anEmptyCatchFishesOutTheSpotUntilThePlayerAimsFarEnough() {
		tracker.onEmptyCatch(SPOT);

		assertEquals(Status.NOT_AIMING, tracker.update(null));
		assertEquals(Status.TOO_CLOSE, tracker.update(at(1, 1)));
		assertEquals(2, tracker.blocksToGo(at(1, 1)));
		assertEquals(Status.FAR_ENOUGH, tracker.update(at(3, 0)));
		assertEquals(List.of("fished out", "far enough"), events);
	}

	@Test
	void theReminderComesBackWhenAimingBackIntoTheSpot() {
		tracker.onEmptyCatch(SPOT);
		tracker.update(at(5, 0));

		assertEquals(Status.TOO_CLOSE, tracker.update(at(1, 0)));
		assertEquals(Status.FAR_ENOUGH, tracker.update(at(0, -4)));
		assertEquals(List.of("fished out", "far enough", "far enough"), events);
	}

	@Test
	void lookingAwayAfterFindingANewSpotIsQuiet() {
		tracker.onEmptyCatch(SPOT);
		tracker.update(at(5, 0));

		assertEquals(Status.NONE, tracker.update(null));
		assertEquals(Status.TOO_CLOSE, tracker.update(SPOT));
	}

	@Test
	void diagonalsOnlyCountTheLongerAxis() {
		tracker.onEmptyCatch(SPOT);

		assertEquals(Status.TOO_CLOSE, tracker.update(at(2.5, 2.5)));
		assertEquals(Status.FAR_ENOUGH, tracker.update(at(2.5, 3)));
	}

	@Test
	void catchingSomethingForgetsTheFishedOutSpot() {
		tracker.onEmptyCatch(SPOT);
		tracker.onCatch(at(6, 0));

		assertEquals(Status.NONE, tracker.update(SPOT));
	}

	@Test
	void theServerMessageAndTheEmptyCatchOnlyAlertOnce() {
		tracker.onServerSaysFishedOut(SPOT, 0);
		tracker.onEmptyCatch(at(0.5, 0));

		assertEquals(List.of("fished out"), events);
	}

	@Test
	void aDistanceInTheServerMessageWinsIfItIsLarger() {
		tracker.onServerSaysFishedOut(SPOT, 6);
		tracker.onEmptyCatch(SPOT);

		assertEquals(6, tracker.requiredDistance());
		assertEquals(Status.TOO_CLOSE, tracker.update(at(5, 0)));
		assertEquals(Status.FAR_ENOUGH, tracker.update(at(6, 0)));
	}

	@Test
	void theCatchLimitWarnsOneEarlyThenFishesOutTheSpot() {
		tracker.setCatchLimit(3);
		tracker.onCatch(SPOT);
		tracker.onCatch(at(1, 0));
		assertEquals(List.of("low"), events);

		tracker.onCatch(at(0, 1));
		assertEquals(List.of("low", "fished out"), events);
		assertEquals(Status.TOO_CLOSE, tracker.update(SPOT));
	}

	@Test
	void movingBetweenCatchesRestartsTheCount() {
		tracker.setCatchLimit(3);
		tracker.onCatch(SPOT);
		tracker.onCatch(at(1, 0));
		tracker.onCatch(at(10, 0));
		tracker.onCatch(at(11, 0));

		assertEquals(List.of("low", "low"), events);
	}
}
