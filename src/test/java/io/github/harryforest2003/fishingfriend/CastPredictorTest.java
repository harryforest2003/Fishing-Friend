package io.github.harryforest2003.fishingfriend;

import io.github.harryforest2003.fishingfriend.BobberTracker.Pos;
import io.github.harryforest2003.fishingfriend.CastPredictor.Surface;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class CastPredictorTest {
	/** Standing on a block at the origin, feet at y = 0, with water everywhere below y = 0. */
	private static final CastPredictor.Terrain POND = (x, y, z) -> {
		if (x == 0 && y == -1 && z == 0) {
			return Surface.SOLID;
		}
		return y < 0 ? Surface.WATER : Surface.AIR;
	};
	private static final double EYE = 1.62;

	private static double distanceSouth(float pitch) {
		Pos landing = CastPredictor.landingPoint(0.5, EYE, 0.5, 0, pitch, POND);
		assertNotNull(landing);
		assertTrue(Math.abs(landing.x() - 0.5) < 0.01, "a cast facing south should stay on the x axis");
		return landing.z() - 0.5;
	}

	@Test
	void lookingDownLandsAFewBlocksOut() {
		double distance = distanceSouth(25);
		assertTrue(distance > 3 && distance < 5, "landed " + distance + " blocks out");
	}

	@Test
	void lookingAtTheHorizonLandsFurther() {
		double distance = distanceSouth(0);
		assertTrue(distance > 5 && distance < 10, "landed " + distance + " blocks out");
	}

	@Test
	void lookingUpLandsFurtherStill() {
		assertTrue(distanceSouth(-30) > distanceSouth(0));
	}

	@Test
	void facingWestGoesWest() {
		Pos landing = CastPredictor.landingPoint(0.5, EYE, 0.5, 90, 25, POND);
		assertNotNull(landing);
		assertTrue(landing.x() < -1, "x was " + landing.x());
	}

	@Test
	void aWallInTheWayMeansNoWaterLanding() {
		CastPredictor.Terrain walled = (x, y, z) -> z >= 2 ? Surface.SOLID : POND.at(x, y, z);
		assertNull(CastPredictor.landingPoint(0.5, EYE, 0.5, 0, 0, walled));
	}

	@Test
	void noWaterAnywhereMeansNoLanding() {
		assertNull(CastPredictor.landingPoint(0.5, EYE, 0.5, 0, -60, (x, y, z) -> Surface.AIR));
	}
}
