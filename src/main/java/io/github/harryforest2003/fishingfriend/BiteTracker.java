package io.github.harryforest2003.fishingfriend;

/**
 * Watches the local player's bobber and decides when to alert.
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
 * </ul>
 */
public final class BiteTracker {
	/** Receives the alerts. Called on the client thread. */
	public interface Listener {
		/** A fish bit the bobber; the player should reel in now. */
		void onBite();

		/** The player reeled in while a fish was biting but nothing came out. */
		void onEmptyCatch();

		/** The player reeled in on time and got something. */
		default void onCatch() {
		}
	}

	/** What the tracker needs to know about the local player's bobber on a given tick. */
	public record Hook(int entityId, boolean biting, double x, double y, double z) {
	}

	/** How close (in blocks) a new item must appear to the bobber to count as the catch. */
	static final double CATCH_RADIUS = 3.0;
	/**
	 * The item spawn is handled before the client tick that notices the bobber is gone, so a spawn up
	 * to this many ticks before the removal still counts.
	 */
	static final int SPAWN_BEFORE_REMOVAL_GRACE = 3;

	private static final int NO_HOOK = Integer.MIN_VALUE;
	private static final long NEVER = Long.MIN_VALUE;

	private final Listener listener;
	private int emptyCatchWaitTicks = 20;

	private int hookId = NO_HOOK;
	private boolean biting;
	private double hookX, hookY, hookZ;
	private long lastSpawnNearHook = NEVER;

	private boolean awaitingCatch;
	private long awaitDeadline;
	private double awaitX, awaitY, awaitZ;
	private int itemsBeforeReel;

	public BiteTracker(Listener listener) {
		this.listener = listener;
	}

	/** How long to wait after reeling in for the catch to show up before calling it empty. */
	public void setEmptyCatchWaitTicks(int ticks) {
		this.emptyCatchWaitTicks = Math.max(1, ticks);
	}

	/**
	 * Advances the tracker by one client tick.
	 *
	 * @param now            a counter that increases by one every client tick
	 * @param hook           the player's bobber, or {@code null} if they have none out
	 * @param holdingRod     whether the player holds a fishing rod in either hand
	 * @param inventoryItems total item count in the player's inventory, used to spot servers that put
	 *                       catches straight into the inventory instead of spawning them
	 */
	public void tick(long now, Hook hook, boolean holdingRod, int inventoryItems) {
		if (hookId != NO_HOOK && (hook == null || hook.entityId() != hookId)) {
			onHookGone(now, holdingRod, inventoryItems);
		}

		if (hook != null) {
			if (hook.entityId() != hookId) {
				hookId = hook.entityId();
				biting = false;
				lastSpawnNearHook = NEVER;
			}
			if (hook.biting() && !biting) {
				listener.onBite();
			}
			biting = hook.biting();
			hookX = hook.x();
			hookY = hook.y();
			hookZ = hook.z();
		}

		if (awaitingCatch) {
			if (inventoryItems > itemsBeforeReel) {
				awaitingCatch = false;
				listener.onCatch();
			} else if (now >= awaitDeadline) {
				awaitingCatch = false;
				listener.onEmptyCatch();
			}
		}
	}

	/** Call when an item entity appears in the client world. */
	public void onItemSpawned(long now, double x, double y, double z) {
		if (awaitingCatch && isNear(x, y, z, awaitX, awaitY, awaitZ)) {
			awaitingCatch = false;
			listener.onCatch();
			return;
		}
		if (hookId != NO_HOOK && isNear(x, y, z, hookX, hookY, hookZ)) {
			lastSpawnNearHook = now;
		}
	}

	/** Forgets everything, e.g. when the player leaves the world or the mod is switched off. */
	public void reset() {
		hookId = NO_HOOK;
		biting = false;
		lastSpawnNearHook = NEVER;
		awaitingCatch = false;
	}

	private void onHookGone(long now, boolean holdingRod, int inventoryItems) {
		// Switching hotbar slots also removes the bobber, but then the player is no longer holding a rod.
		boolean reeledInOnBite = biting && holdingRod;
		boolean itemAlreadySpawned = lastSpawnNearHook != NEVER && now - lastSpawnNearHook <= SPAWN_BEFORE_REMOVAL_GRACE;

		hookId = NO_HOOK;
		biting = false;
		lastSpawnNearHook = NEVER;
		awaitingCatch = false;

		if (!reeledInOnBite) {
			return;
		}
		if (itemAlreadySpawned) {
			listener.onCatch();
			return;
		}
		awaitingCatch = true;
		awaitDeadline = now + emptyCatchWaitTicks;
		awaitX = hookX;
		awaitY = hookY;
		awaitZ = hookZ;
		itemsBeforeReel = inventoryItems;
	}

	private static boolean isNear(double x1, double y1, double z1, double x2, double y2, double z2) {
		double dx = x1 - x2, dy = y1 - y2, dz = z1 - z2;
		return dx * dx + dy * dy + dz * dz <= CATCH_RADIUS * CATCH_RADIUS;
	}
}
