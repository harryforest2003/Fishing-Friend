package io.github.harryforest2003.fishingfriend.test;

/** Switches on a fake server overfishing rule, applied by {@code FishingHookMixin}. */
public final class OverfishingRule {
	/** While true, reeling in on a bite removes the bobber without spawning the catch. */
	public static volatile boolean active;

	private OverfishingRule() {
	}
}
