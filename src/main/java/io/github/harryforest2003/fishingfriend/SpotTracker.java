package io.github.harryforest2003.fishingfriend;

import io.github.harryforest2003.fishingfriend.BobberTracker.Pos;

/**
 * Remembers where the player has fished out a spot and whether they are now fishing far enough away.
 *
 * <p>Follows the most common server rule (mcMMO's): catches count against the last spot you fished,
 * and catching something somewhere else resets it. So a fished-out spot is remembered until the
 * player catches something again.
 *
 * <p>Distances are measured horizontally and per axis, as servers measure them with boxes:
 * a spot 3 blocks away has to be 3 blocks away along x or z, not just diagonally.
 */
public final class SpotTracker {
	/** Where the player's current fishing target is relative to the fished-out spot. */
	public enum Status {
		/** No fished-out spot is remembered, or there is nothing to warn about right now. */
		NONE,
		/** The spot just got fished out and the player has not aimed anywhere useful yet. */
		NOT_AIMING,
		/** The player is aiming at, or fishing in, the fished-out spot. */
		TOO_CLOSE,
		/** The player is aiming at, or fishing in, water far enough away. */
		FAR_ENOUGH
	}

	public interface Listener {
		/** The spot is close to running out (one catch left, or the server said so). */
		void onSpotRunningLow();

		/**
		 * The spot is fished out.
		 *
		 * @param again true if this spot was already known to be fished out and the player fished it anyway
		 */
		void onSpotFishedOut(boolean again);

		/** The player moved their aim or bobber out of the fished-out spot. */
		void onFarEnough();
	}

	/**
	 * One reel can produce both a server message and an empty catch. Signals for the same spot this close
	 * together are treated as one, so the player is alerted once per reel.
	 */
	static final int SAME_REEL_TICKS = 60;
	private static final long NEVER = Long.MIN_VALUE;

	private final Listener listener;
	private int moveDistance = 3;
	private int catchLimit;

	private Pos lastCatch;
	private int catchesHere;

	private Pos fishedOut;
	private int requiredDistance;
	private boolean hasBeenFarEnough;
	private Status status = Status.NONE;
	private long lastAlert = NEVER;

	public SpotTracker(Listener listener) {
		this.listener = listener;
	}

	/** How many blocks away the next spot has to be. */
	public void setMoveDistance(int blocks) {
		this.moveDistance = Math.max(1, blocks);
	}

	/** Catches allowed in one spot before it runs out, or 0 if unknown. */
	public void setCatchLimit(int catches) {
		this.catchLimit = Math.max(0, catches);
	}

	public void onCatch(Pos at, long now) {
		// Any catch means the server is handing out fish again, so the old spot no longer matters.
		fishedOut = null;
		status = Status.NONE;
		catchesHere = lastCatch != null && distance(at, lastCatch) < moveDistance ? catchesHere + 1 : 1;
		lastCatch = at;
		if (catchLimit > 0) {
			if (catchesHere >= catchLimit) {
				markFishedOut(at, 0, now);
			} else if (catchesHere == catchLimit - 1) {
				listener.onSpotRunningLow();
			}
		}
	}

	public void onEmptyCatch(Pos at, long now) {
		markFishedOut(at, 0, now);
	}

	/**
	 * The server said the spot is fished out.
	 *
	 * @param at          where the bobber was, or null if unknown
	 * @param blocksHint  a distance mentioned in the message, or 0
	 */
	public void onServerSaysFishedOut(Pos at, int blocksHint, long now) {
		markFishedOut(at != null ? at : lastCatch, blocksHint, now);
	}

	public void onServerSaysRunningLow() {
		listener.onSpotRunningLow();
	}

	/**
	 * Re-evaluates where the player is fishing. Call every tick.
	 *
	 * @param target the bobber if it is in the water, otherwise the water the player is aiming at, or null
	 */
	public Status update(Pos target) {
		Status next;
		if (fishedOut == null) {
			next = Status.NONE;
		} else if (target == null) {
			next = hasBeenFarEnough ? Status.NONE : Status.NOT_AIMING;
		} else if (distance(target, fishedOut) < requiredDistance) {
			next = Status.TOO_CLOSE;
		} else {
			next = Status.FAR_ENOUGH;
			hasBeenFarEnough = true;
			if (status == Status.TOO_CLOSE || status == Status.NOT_AIMING) {
				listener.onFarEnough();
			}
		}
		status = next;
		return next;
	}

	/** How many more blocks {@code target} has to move to leave the fished-out spot. */
	public int blocksToGo(Pos target) {
		if (fishedOut == null || target == null) {
			return requiredDistance;
		}
		return (int) Math.max(1, Math.ceil(requiredDistance - distance(target, fishedOut)));
	}

	/** Whether a fished-out spot is remembered. */
	public boolean hasFishedOutSpot() {
		return fishedOut != null;
	}

	/** How far from the fished-out spot the player has to fish. */
	public int requiredDistance() {
		return requiredDistance;
	}

	public void reset() {
		lastCatch = null;
		catchesHere = 0;
		fishedOut = null;
		status = Status.NONE;
		lastAlert = NEVER;
	}

	private void markFishedOut(Pos at, int blocksHint, long now) {
		if (at == null) {
			return;
		}
		int required = Math.max(moveDistance, blocksHint);
		boolean alreadyKnown = fishedOut != null && distance(at, fishedOut) < Math.max(required, requiredDistance);
		boolean sameReel = alreadyKnown && lastAlert != NEVER && now - lastAlert < SAME_REEL_TICKS;
		fishedOut = at;
		requiredDistance = alreadyKnown ? Math.max(requiredDistance, required) : required;
		lastCatch = null;
		catchesHere = 0;
		if (sameReel) {
			return;
		}
		// Fishing a known spot again brings the reminder back until the player aims somewhere better.
		hasBeenFarEnough = false;
		status = Status.NOT_AIMING;
		lastAlert = now;
		listener.onSpotFishedOut(alreadyKnown);
	}

	private static double distance(Pos a, Pos b) {
		return Math.max(Math.abs(a.x() - b.x()), Math.abs(a.z() - b.z()));
	}
}
