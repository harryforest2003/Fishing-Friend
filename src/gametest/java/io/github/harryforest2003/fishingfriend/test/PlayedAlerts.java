package io.github.harryforest2003.fishingfriend.test;

import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

/**
 * What the mod asked to play or show, recorded by mixins on its own classes. Recording the requests rather
 * than listening to the sound engine or reading the screen keeps the test working on machines without
 * audio, like CI.
 */
public final class PlayedAlerts {
	/** Sound ids passed to {@code Sounds.play}. */
	public static final List<String> SOUNDS = new CopyOnWriteArrayList<>();
	/** Text of every action bar message the mod showed (an empty string clears it). */
	public static final List<String> MESSAGES = new CopyOnWriteArrayList<>();

	private PlayedAlerts() {
	}
}
