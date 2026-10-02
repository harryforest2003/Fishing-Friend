package io.github.harryforest2003.fishingfriend.compat;

import com.terraformersmc.modmenu.api.ConfigScreenFactory;
import com.terraformersmc.modmenu.api.ModMenuApi;
import io.github.harryforest2003.fishingfriend.config.FishingFriendConfigScreen;

/** Adds the config button to Mod Menu. Only loaded when Mod Menu is installed. */
public final class ModMenuIntegration implements ModMenuApi {
	@Override
	public ConfigScreenFactory<?> getModConfigScreenFactory() {
		return FishingFriendConfigScreen::new;
	}
}
