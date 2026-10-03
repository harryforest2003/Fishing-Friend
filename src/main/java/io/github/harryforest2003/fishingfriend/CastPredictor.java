package io.github.harryforest2003.fishingfriend;

import io.github.harryforest2003.fishingfriend.BobberTracker.Pos;

/**
 * Predicts where a cast from the player's current view will land, by running the same physics the game
 * uses for a flying bobber: launched from just in front of the eyes, slowed by 8% and pulled down by 0.03
 * every tick, until it reaches water or hits a block.
 *
 * <p>Where the crosshair meets the water is a poor guide: looking at the horizon, the crosshair hits water
 * far away while the bobber lands 7 or 8 blocks out.
 */
public final class CastPredictor {
	public enum Surface { AIR, WATER, SOLID }

	/** What occupies a block. */
	@FunctionalInterface
	public interface Terrain {
		Surface at(int x, int y, int z);
	}

	static final int MAX_TICKS = 100;
	private static final double GRAVITY = 0.03;
	private static final double DRAG = 0.92;

	/**
	 * Where the bobber would land in water, or null if it would hit a block first or never come down.
	 *
	 * @param yaw   the player's yaw in degrees (0 = south)
	 * @param pitch the player's pitch in degrees (positive = looking down)
	 */
	public static Pos landingPoint(double playerX, double eyeY, double playerZ, float yaw, float pitch, Terrain terrain) {
		double yawRadians = Math.toRadians(-yaw) - Math.PI;
		double pitchRadians = Math.toRadians(-pitch);
		double cosYaw = Math.cos(yawRadians);
		double sinYaw = Math.sin(yawRadians);
		double cosPitch = -Math.cos(pitchRadians);
		double sinPitch = Math.sin(pitchRadians);

		double x = playerX - sinYaw * 0.3;
		double y = eyeY;
		double z = playerZ - cosYaw * 0.3;
		double vx = -sinYaw;
		double vy = Math.max(-5, Math.min(5, -(sinPitch / cosPitch)));
		double vz = -cosYaw;
		// The game adds a random 0.5 +/- 0.01 to the 0.6 / length factor; the average is good enough.
		double scale = 0.6 / Math.sqrt(vx * vx + vy * vy + vz * vz) + 0.5;
		vx *= scale;
		vy *= scale;
		vz *= scale;

		for (int tick = 0; tick < MAX_TICKS; tick++) {
			Surface here = terrain.at((int) Math.floor(x), (int) Math.floor(y), (int) Math.floor(z));
			if (here == Surface.WATER) {
				return new Pos(x, y, z);
			}
			if (here == Surface.SOLID) {
				return null;
			}
			vy -= GRAVITY;
			x += vx;
			y += vy;
			z += vz;
			vx *= DRAG;
			vy *= DRAG;
			vz *= DRAG;
		}
		return null;
	}

	private CastPredictor() {
	}
}
