package io.github.harryforest2003.fishingfriend;

/**
 * Follows the local player's bobber from cast to reel-in and reports what happened to it.
 *
 * <p>Kept free of Minecraft types so the timing rules can be unit tested; {@link FishingWatcher}
 * feeds it a snapshot of the game every client tick.
 *
 * <p>How the server behaves, which these rules rely on:
 * <ul>
 *   <li>When a fish bites, the server flags the bobber as biting. That flag is what makes the bobber
 *       dip on the client, and it stays set for the whole window in which reeling in catches the fish.</li>
 *   <li>Reeling in during that window spawns the caught item at the bobber and then removes the bobber.
 *       Both packets arrive in that order, so if the bobber is still flagged as biting when it
 *       disappears, the server accepted the catch.</li>
 *   <li>Overfishing rules (for example mcMMO's) delete that item before it spawns. Reeling in on time
 *       and then seeing no item is the signal that the spot is fished out.</li>
 *   <li>The server removes the bobber by itself once the player is more than 32 blocks away.</li>
 * </ul>
 *
 * @param <T> whatever the caller uses to identify a spawned item, handed back with the catch
 */
public final class BobberTracker<T> {
	/** Receives what happens to the bobber. Called on the client thread. */
	public interface Listener<T> {
		/** A new bobber is out. */
		default void onCast() {
		}

		/** The bobber settled in water at {@code at}. */
		default void onLandedInWater(Pos at) {
		}

		/** The bobber settled on a block instead of water, so nothing will bite. */
		default void onLandedOnGround() {
		}

		/** The bobber caught on a mob or other entity. */
		default void onHookedEntity(String name) {
		}

		/** A fish bit the bobber; the player should reel in now. */
		default void onBite() {
		}

		/** A bite ended without the player reeling in. */
		default void onBiteMissed() {
		}

		/** The player reeled in from the water while nothing was biting. */
		default void onReeledInWithoutBite() {
		}

		/**
		 * The player reeled in on time and something came out.
		 *
		 * @param item          the spawned catch, or null if it went straight into the inventory
		 * @param reactionTicks ticks from the bite to the player pressing use
		 */
		default void onCatch(Pos at, T item, int reactionTicks) {
		}

		/** The player reeled in on time but nothing came out. */
		default void onEmptyCatch(Pos at, int reactionTicks) {
		}

		/** The player walked too far away and the server removed the bobber. */
		default void onLineSnapped() {
		}
	}

	public record Pos(double x, double y, double z) {
		public double distanceTo(Pos other) {
			double dx = x - other.x, dy = y - other.y, dz = z - other.z;
			return Math.sqrt(dx * dx + dy * dy + dz * dz);
		}
	}

	/** The local player's bobber on a given tick. {@code hookedEntity} is null unless it caught on an entity. */
	public record Hook(int entityId, Pos pos, boolean biting, boolean inWater, boolean onGround, String hookedEntity) {
	}

	/** The local player on a given tick. {@code usePressed} is true on the tick the use key goes down. */
	public record Player(Pos pos, boolean holdingRod, boolean usePressed, int inventoryItems) {
	}

	/** How close (in blocks) a new item must appear to the bobber to count as the catch. */
	static final double CATCH_RADIUS = 3.0;
	/**
	 * The item spawn is handled before the client tick that notices the bobber is gone, so a spawn up
	 * to this many ticks before the removal still counts.
	 */
	static final int SPAWN_BEFORE_REMOVAL_GRACE = 3;
	/** Ticks the bobber has to stay in water or on the ground before it counts as landed. */
	static final int SETTLE_TICKS = 5;
	/** The server drops the line at 32 blocks; a bobber that vanishes beyond this distance snapped. */
	static final double SNAP_DISTANCE = 30.0;
	/** A press of the use key this recent means the player reeled in, rather than the line snapping. */
	static final int RECENT_USE_TICKS = 10;

	private static final int NO_HOOK = Integer.MIN_VALUE;
	private static final long NEVER = Long.MIN_VALUE;

	private final Listener<T> listener;
	private int emptyCatchWaitTicks = 20;

	private int hookId = NO_HOOK;
	private Pos hookPos;
	private boolean biting;
	private boolean inWater;
	private boolean landed;
	private int settleTicks;
	private String hookedEntity;
	private double distanceToPlayer;
	private long biteTick = NEVER;
	private long reelPressTick = NEVER;
	private long lastUsePress = NEVER;
	private long lastActivity = NEVER;
	private long lastSpawnNearHook = NEVER;
	private T lastSpawnedItem;

	private boolean awaitingCatch;
	private long awaitDeadline;
	private Pos awaitPos;
	private int awaitReaction;
	private int itemsBeforeReel;

	public BobberTracker(Listener<T> listener) {
		this.listener = listener;
	}

	/** How long to wait after reeling in for the catch to show up before calling it empty. */
	public void setEmptyCatchWaitTicks(int ticks) {
		this.emptyCatchWaitTicks = Math.max(1, ticks);
	}

	/** Where the bobber is (or last was), or null if none has been cast. */
	public Pos hookPos() {
		return hookPos;
	}

	/** Whether the player cast, reeled in or clicked with the rod within the last {@code ticks} ticks. */
	public boolean usedRodWithin(long now, int ticks) {
		return lastActivity != NEVER && now - lastActivity <= ticks;
	}

	/** Whether the player cast, reeled in or clicked with the rod at or after tick {@code tick}. */
	public boolean usedRodSince(long tick) {
		return lastActivity != NEVER && lastActivity >= tick;
	}

	/** Whether the player has a bobber out. */
	public boolean isOut() {
		return hookId != NO_HOOK;
	}

	/** Whether the current bobber has settled in water. */
	public boolean inWater() {
		return hookId != NO_HOOK && landed && inWater;
	}

	/**
	 * Advances the tracker by one client tick.
	 *
	 * @param now    a counter that increases by one every client tick
	 * @param hook   the player's bobber, or {@code null} if they have none out
	 * @param player the local player
	 */
	public void tick(long now, Hook hook, Player player) {
		if (player.usePressed() && (player.holdingRod() || hookId != NO_HOOK)) {
			lastUsePress = now;
			lastActivity = now;
			if (biting && reelPressTick == NEVER) {
				reelPressTick = now;
			}
		}

		if (hookId != NO_HOOK && (hook == null || hook.entityId() != hookId)) {
			onHookGone(now, player);
		}

		if (hook != null) {
			if (hook.entityId() != hookId) {
				startTracking(hook);
				lastActivity = now;
				listener.onCast();
			}
			updateHook(now, hook, player);
		}

		if (awaitingCatch) {
			if (player.inventoryItems() > itemsBeforeReel) {
				awaitingCatch = false;
				listener.onCatch(awaitPos, null, awaitReaction);
			} else if (now >= awaitDeadline) {
				awaitingCatch = false;
				listener.onEmptyCatch(awaitPos, awaitReaction);
			}
		}
	}

	/** Call when an item entity appears in the client world. */
	public void onItemSpawned(long now, Pos at, T item) {
		if (awaitingCatch && at.distanceTo(awaitPos) <= CATCH_RADIUS) {
			awaitingCatch = false;
			listener.onCatch(awaitPos, item, awaitReaction);
			return;
		}
		if (hookId != NO_HOOK && at.distanceTo(hookPos) <= CATCH_RADIUS) {
			lastSpawnNearHook = now;
			lastSpawnedItem = item;
		}
	}

	/** Forgets everything, e.g. when the player leaves the world or the mod is switched off. */
	public void reset() {
		hookId = NO_HOOK;
		hookPos = null;
		biting = false;
		awaitingCatch = false;
		lastSpawnNearHook = NEVER;
		lastSpawnedItem = null;
		lastUsePress = NEVER;
		lastActivity = NEVER;
	}

	private void startTracking(Hook hook) {
		hookId = hook.entityId();
		biting = false;
		inWater = false;
		landed = false;
		settleTicks = 0;
		hookedEntity = null;
		biteTick = NEVER;
		reelPressTick = NEVER;
		lastSpawnNearHook = NEVER;
		lastSpawnedItem = null;
	}

	private void updateHook(long now, Hook hook, Player player) {
		if (hook.biting() && !biting) {
			biteTick = now;
			reelPressTick = NEVER;
			listener.onBite();
		} else if (!hook.biting() && biting) {
			listener.onBiteMissed();
		}
		biting = hook.biting();

		if (!landed) {
			boolean resting = hook.inWater() || hook.onGround();
			settleTicks = resting ? settleTicks + 1 : 0;
			if (settleTicks >= SETTLE_TICKS) {
				landed = true;
				if (hook.inWater()) {
					listener.onLandedInWater(hook.pos());
				} else {
					listener.onLandedOnGround();
				}
			}
		}
		inWater = hook.inWater();

		if (hook.hookedEntity() != null && hookedEntity == null) {
			listener.onHookedEntity(hook.hookedEntity());
		}
		hookedEntity = hook.hookedEntity();

		hookPos = hook.pos();
		distanceToPlayer = hook.pos().distanceTo(player.pos());
	}

	private void onHookGone(long now, Player player) {
		boolean wasBiting = biting;
		boolean wasInWater = landed && inWater && hookedEntity == null;
		boolean reeledIn = lastUsePress != NEVER && now - lastUsePress <= RECENT_USE_TICKS;
		boolean snapped = distanceToPlayer > SNAP_DISTANCE && !reeledIn;
		boolean itemAlreadySpawned = lastSpawnNearHook != NEVER && now - lastSpawnNearHook <= SPAWN_BEFORE_REMOVAL_GRACE;
		int reaction = (int) ((reelPressTick != NEVER ? reelPressTick : now) - biteTick);
		T spawnedItem = lastSpawnedItem;

		hookId = NO_HOOK;
		biting = false;
		lastSpawnNearHook = NEVER;
		lastSpawnedItem = null;
		awaitingCatch = false;
		lastActivity = now;

		if (snapped) {
			listener.onLineSnapped();
			return;
		}
		// Switching hotbar slots also removes the bobber, but then the player is no longer holding a rod.
		if (!player.holdingRod()) {
			return;
		}
		if (!wasBiting) {
			if (wasInWater) {
				listener.onReeledInWithoutBite();
			}
			return;
		}
		if (itemAlreadySpawned) {
			listener.onCatch(hookPos, spawnedItem, reaction);
			return;
		}
		awaitingCatch = true;
		awaitDeadline = now + emptyCatchWaitTicks;
		awaitPos = hookPos;
		awaitReaction = reaction;
		itemsBeforeReel = player.inventoryItems();
	}
}
