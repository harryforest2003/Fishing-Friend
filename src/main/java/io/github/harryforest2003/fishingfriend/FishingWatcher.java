package io.github.harryforest2003.fishingfriend;

import io.github.harryforest2003.fishingfriend.compat.VersionCompat;
import io.github.harryforest2003.fishingfriend.config.FishingFriendConfig;
import io.github.harryforest2003.fishingfriend.mixin.FishingHookAccessor;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.projectile.FishingHook;
import net.minecraft.world.item.FishingRodItem;

/** Feeds the local player's fishing state into a {@link BiteTracker} and turns its verdicts into alerts. */
final class FishingWatcher implements BiteTracker.Listener {
	private final BiteTracker tracker = new BiteTracker(this);
	private long ticks;

	void tick(Minecraft client) {
		ticks++;
		LocalPlayer player = client.player;
		FishingFriendConfig config = FishingFriendConfig.get();
		if (player == null || !config.enabled) {
			tracker.reset();
			return;
		}
		tracker.setEmptyCatchWaitTicks(config.emptyCatchWaitTicks);
		tracker.tick(ticks, snapshot(player.fishing), isHoldingRod(player), countItems(player.getInventory()));
	}

	void onEntityLoad(Entity entity, ClientLevel level) {
		if (entity instanceof ItemEntity) {
			tracker.onItemSpawned(ticks, entity.getX(), entity.getY(), entity.getZ());
		}
	}

	@Override
	public void onBite() {
		FishingFriendConfig config = FishingFriendConfig.get();
		alert(config, config.bite, "fishingfriend.message.bite");
	}

	@Override
	public void onEmptyCatch() {
		FishingFriendConfig config = FishingFriendConfig.get();
		alert(config, config.emptyCatch, "fishingfriend.message.empty_catch");
	}

	private static void alert(FishingFriendConfig config, FishingFriendConfig.Alert alert, String messageKey) {
		if (!alert.enabled) {
			return;
		}
		Sounds.play(alert);
		if (config.showMessages) {
			VersionCompat.showOverlayMessage(Minecraft.getInstance(), Component.translatable(messageKey));
		}
	}

	private static BiteTracker.Hook snapshot(FishingHook hook) {
		if (hook == null || hook.isRemoved()) {
			return null;
		}
		boolean biting = ((FishingHookAccessor) hook).fishingfriend$isBiting();
		return new BiteTracker.Hook(hook.getId(), biting, hook.getX(), hook.getY(), hook.getZ());
	}

	private static boolean isHoldingRod(LocalPlayer player) {
		return player.getMainHandItem().getItem() instanceof FishingRodItem
			|| player.getOffhandItem().getItem() instanceof FishingRodItem;
	}

	private static int countItems(Inventory inventory) {
		int total = 0;
		for (int slot = 0; slot < inventory.getContainerSize(); slot++) {
			total += inventory.getItem(slot).getCount();
		}
		return total;
	}
}
