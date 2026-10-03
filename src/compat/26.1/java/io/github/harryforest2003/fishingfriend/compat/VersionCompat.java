package io.github.harryforest2003.fishingfriend.compat;

import com.mojang.blaze3d.platform.InputConstants;
import net.fabricmc.fabric.api.client.keymapping.v1.KeyMappingHelper;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.sounds.SoundEvent;

/** The few calls whose names or owners differ between Minecraft versions. This copy is for 26.1.x. */
public final class VersionCompat {
	public static final String TOGGLE_KEY = "key.fishingfriend.toggle";

	private static final KeyMapping.Category CATEGORY =
		KeyMapping.Category.register(Identifier.fromNamespaceAndPath("fishingfriend", "main"));

	public static void setScreen(Minecraft client, Screen screen) {
		client.setScreen(screen);
	}

	/** Whether a menu, chat or inventory screen is open (including the pause menu shown when the game loses focus). */
	public static boolean isScreenOpen(Minecraft client) {
		return client.screen != null;
	}

	/** Shows a short message above the hotbar. */
	public static void showOverlayMessage(Minecraft client, Component message) {
		if (client.player != null) {
			client.player.sendOverlayMessage(message);
		}
	}

	/** The sound event for an id like {@code minecraft:block.note_block.bell}, or null if the id is malformed. */
	public static SoundEvent soundEvent(String id) {
		Identifier location = Identifier.tryParse(id);
		return location == null ? null : SoundEvent.createVariableRangeEvent(location);
	}

	/** Registers the (unbound by default) key that turns the mod on and off. */
	public static KeyMapping registerToggleKey() {
		return KeyMappingHelper.registerKeyMapping(new KeyMapping(
			TOGGLE_KEY, InputConstants.UNKNOWN.getValue(), CATEGORY));
	}

	private VersionCompat() {
	}
}
