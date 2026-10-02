package io.github.harryforest2003.fishingfriend.test;

import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

/**
 * Sound ids the mod asked to play, recorded by {@code SoundsMixin}. Recording the request rather than
 * listening to the sound engine keeps the test working on machines without audio, like CI.
 */
public final class PlayedAlerts {
	public static final List<String> SOUNDS = new CopyOnWriteArrayList<>();

	private PlayedAlerts() {
	}
}
