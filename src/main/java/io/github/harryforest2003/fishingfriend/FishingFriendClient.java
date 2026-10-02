package io.github.harryforest2003.fishingfriend;

import io.github.harryforest2003.fishingfriend.config.FishingFriendConfig;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientEntityEvents;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public final class FishingFriendClient implements ClientModInitializer {
	public static final String MOD_ID = "fishingfriend";
	public static final Logger LOGGER = LoggerFactory.getLogger("Fishing Friend");

	@Override
	public void onInitializeClient() {
		FishingFriendConfig.load();

		FishingWatcher watcher = new FishingWatcher();
		ClientTickEvents.END_CLIENT_TICK.register(watcher::tick);
		ClientEntityEvents.ENTITY_LOAD.register(watcher::onEntityLoad);
	}
}
