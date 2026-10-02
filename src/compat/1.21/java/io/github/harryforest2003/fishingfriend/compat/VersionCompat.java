package io.github.harryforest2003.fishingfriend.compat;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

/** The few calls whose names or owners differ between Minecraft versions. This copy is for 1.21.x. */
public final class VersionCompat {
	public static void setScreen(Minecraft client, Screen screen) {
		client.setScreen(screen);
	}

	/** Shows a short message above the hotbar. */
	public static void showOverlayMessage(Minecraft client, Component message) {
		client.gui.setOverlayMessage(message, false);
	}

	private VersionCompat() {
	}
}
