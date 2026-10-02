package io.github.harryforest2003.fishingfriend;

import io.github.harryforest2003.fishingfriend.compat.VersionCompat;
import io.github.harryforest2003.fishingfriend.config.FishingFriendConfig;
import io.github.harryforest2003.fishingfriend.stats.StatsCommand;
import io.github.harryforest2003.fishingfriend.stats.StatsStore;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.command.v2.ClientCommandRegistrationCallback;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientEntityEvents;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientLifecycleEvents;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.message.v1.ClientReceiveMessageEvents;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public final class FishingFriendClient implements ClientModInitializer {
	public static final String MOD_ID = "fishingfriend";
	public static final Logger LOGGER = LoggerFactory.getLogger("Fishing Friend");

	@Override
	public void onInitializeClient() {
		FishingFriendConfig.load();
		StatsStore.load();

		FishingWatcher watcher = new FishingWatcher();
		watcher.setToggleKey(VersionCompat.registerToggleKey());
		ClientTickEvents.END_CLIENT_TICK.register(watcher::tick);
		ClientEntityEvents.ENTITY_LOAD.register(watcher::onEntityLoad);
		ClientReceiveMessageEvents.GAME.register(watcher::onGameMessage);
		ClientCommandRegistrationCallback.EVENT.register((dispatcher, buildContext) -> StatsCommand.register(dispatcher));
		ClientLifecycleEvents.CLIENT_STOPPING.register(client -> StatsStore.saveIfChanged());
	}
}
