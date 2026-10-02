package io.github.harryforest2003.fishingfriend;

import io.github.harryforest2003.fishingfriend.BobberTracker.Pos;
import io.github.harryforest2003.fishingfriend.compat.VersionCompat;
import io.github.harryforest2003.fishingfriend.config.FishingFriendConfig;
import io.github.harryforest2003.fishingfriend.mixin.FishingHookAccessor;
import io.github.harryforest2003.fishingfriend.stats.FishingStats;
import io.github.harryforest2003.fishingfriend.stats.StatsStore;
import net.minecraft.ChatFormatting;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.network.chat.Component;
import net.minecraft.tags.FluidTags;
import net.minecraft.tags.ItemTags;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.projectile.FishingHook;
import net.minecraft.world.item.FishingRodItem;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;

/**
 * Feeds the game into a {@link BobberTracker} and {@link SpotTracker} every tick, and turns what they
 * report into sounds, action bar messages and stats.
 */
final class FishingWatcher implements BobberTracker.Listener<ItemEntity>, SpotTracker.Listener {
	/** How long a one-off action bar message keeps the move reminder from replacing it. */
	private static final int MESSAGE_TICKS = 50;
	/** The action bar fades after 3 seconds, so the move reminder is re-sent more often than that. */
	private static final int REMINDER_REFRESH_TICKS = 10;
	/** How far away the player can aim at water when looking for a new spot. */
	private static final double AIM_RANGE = 48;
	private static final int STATS_SAVE_TICKS = 20 * 30;
	/**
	 * Servers send overfishing warnings the moment you cast or reel in. Ignoring them at other times
	 * stops ordinary chat from triggering anything on servers that relay player chat as system messages.
	 */
	private static final int SERVER_MESSAGE_WINDOW_TICKS = 20 * 5;
	/**
	 * Servers usually send the warning just before removing the bobber, so a warning that arrives while
	 * the bobber is out is held this long to see whether it gets reeled in.
	 */
	private static final int PENDING_MESSAGE_TICKS = 20;
	private static final Set<Item> ALWAYS_TREASURE = Set.of(Items.ENCHANTED_BOOK, Items.NAME_TAG, Items.NAUTILUS_SHELL, Items.SADDLE);
	private static final Set<Item> TREASURE_IF_ENCHANTED = Set.of(Items.BOW, Items.FISHING_ROD);

	private final BobberTracker<ItemEntity> bobber = new BobberTracker<>(this);
	private final SpotTracker spot = new SpotTracker(this);
	private final List<ItemEntity> catchesToIdentify = new ArrayList<>();
	private KeyMapping toggleKey;
	private long ticks;
	private long messageUntil;
	private boolean inWorld;
	private boolean useWasDown;
	private Component lastReminder;
	/** True while this mod is putting its own message in the action bar, which on 26.x also fires the message event. */
	private boolean showingOwnMessage;
	private Runnable pendingServerMessage;
	private long pendingSince;

	void setToggleKey(KeyMapping toggleKey) {
		this.toggleKey = toggleKey;
	}

	void tick(Minecraft client) {
		ticks++;
		FishingFriendConfig config = FishingFriendConfig.get();
		LocalPlayer player = client.player;
		if (player == null) {
			if (inWorld) {
				inWorld = false;
				stop();
				StatsStore.saveIfChanged();
			}
			return;
		}
		if (!inWorld) {
			inWorld = true;
			StatsStore.startSession();
		}
		handleToggleKey(client, config);
		if (!config.enabled) {
			stop();
			return;
		}

		bobber.setEmptyCatchWaitTicks(config.emptyCatchWaitTicks);
		spot.setMoveDistance(config.moveDistance);
		spot.setCatchLimit(config.catchesPerSpot);

		boolean useDown = client.options.keyUse.isDown();
		BobberTracker.Player me = new BobberTracker.Player(
			pos(player), isHoldingRod(player), useDown && !useWasDown, countItems(player.getInventory()));
		useWasDown = useDown;

		BobberTracker.Hook hook = snapshot(player.fishing);
		bobber.tick(ticks, hook, me);
		if (hook != null) {
			StatsStore.record(stats -> stats.fishingTicks++);
		}

		handlePendingServerMessage();
		identifyCatches();
		updateMoveReminder(client, player, config);
		if (ticks % STATS_SAVE_TICKS == 0) {
			StatsStore.saveIfChanged();
		}
	}

	void onEntityLoad(Entity entity, ClientLevel level) {
		if (inWorld && entity instanceof ItemEntity item) {
			bobber.onItemSpawned(ticks, pos(entity), item);
		}
	}

	void onGameMessage(Component message, boolean overlay) {
		FishingFriendConfig config = FishingFriendConfig.get();
		if (showingOwnMessage || !inWorld || !config.enabled || !config.readServerMessages) {
			return;
		}
		String text = message.getString();
		Runnable warning;
		if (ServerMessages.containsAny(text, config.fishedOutPhrases)) {
			int blocks = ServerMessages.blocksMentioned(text);
			warning = () -> spot.onServerSaysFishedOut(bobber.hookPos(), blocks);
		} else if (ServerMessages.containsAny(text, config.runningLowPhrases)) {
			warning = spot::onServerSaysRunningLow;
		} else {
			return;
		}

		if (bobber.usedRodWithin(ticks, SERVER_MESSAGE_WINDOW_TICKS)) {
			warning.run();
		} else if (bobber.isOut()) {
			pendingServerMessage = warning;
			pendingSince = ticks;
		}
	}

	/** Acts on a held warning once the bobber is reeled in, or drops it if that doesn't happen soon. */
	private void handlePendingServerMessage() {
		if (pendingServerMessage == null) {
			return;
		}
		if (bobber.usedRodSince(pendingSince)) {
			Runnable warning = pendingServerMessage;
			pendingServerMessage = null;
			warning.run();
		} else if (ticks - pendingSince > PENDING_MESSAGE_TICKS) {
			pendingServerMessage = null;
		}
	}

	// --- Bobber events ---

	@Override
	public void onCast() {
		StatsStore.record(stats -> stats.casts++);
	}

	@Override
	public void onLandedOnGround() {
		bobberWarning(Component.translatable("fishingfriend.message.landed_on_ground"));
	}

	@Override
	public void onHookedEntity(String name) {
		bobberWarning(Component.translatable("fishingfriend.message.hooked_entity", name));
	}

	@Override
	public void onBite() {
		StatsStore.record(stats -> stats.bites++);
		FishingFriendConfig config = FishingFriendConfig.get();
		alert(config.bite, Component.translatable("fishingfriend.message.bite").withStyle(ChatFormatting.GOLD, ChatFormatting.BOLD));
	}

	@Override
	public void onBiteMissed() {
		StatsStore.record(stats -> stats.missedBites++);
		bobberWarning(Component.translatable("fishingfriend.message.bite_missed"));
	}

	@Override
	public void onReeledInWithoutBite() {
		StatsStore.record(stats -> stats.reelsWithoutBite++);
	}

	@Override
	public void onCatch(Pos at, ItemEntity item, int reactionTicks) {
		StatsStore.record(stats -> stats.recordCatch(reactionTicks));
		if (item != null) {
			catchesToIdentify.add(item);
		}
		spot.onCatch(at);
	}

	@Override
	public void onEmptyCatch(Pos at, int reactionTicks) {
		StatsStore.record(stats -> stats.recordEmptyCatch(reactionTicks));
		spot.onEmptyCatch(at);
	}

	@Override
	public void onLineSnapped() {
		StatsStore.record(stats -> stats.lineSnaps++);
		bobberWarning(Component.translatable("fishingfriend.message.line_snapped"));
	}

	// --- Spot events ---

	@Override
	public void onSpotRunningLow() {
		if (FishingFriendConfig.get().actionBarAlerts) {
			showMessage(Component.translatable("fishingfriend.message.running_low").withStyle(ChatFormatting.YELLOW));
		}
	}

	@Override
	public void onSpotFishedOut() {
		FishingFriendConfig config = FishingFriendConfig.get();
		alert(config.fishedOut, Component.translatable("fishingfriend.message.fished_out").withStyle(ChatFormatting.RED));
	}

	@Override
	public void onFarEnough() {
		if (FishingFriendConfig.get().moveReminder) {
			showMessage(Component.translatable("fishingfriend.message.far_enough").withStyle(ChatFormatting.GREEN));
		}
	}

	// --- Helpers ---

	private void updateMoveReminder(Minecraft client, LocalPlayer player, FishingFriendConfig config) {
		boolean bobberInWater = bobber.inWater();
		Pos target = !spot.hasFishedOutSpot() ? null : bobberInWater ? bobber.hookPos() : aimedWater(client, player);
		SpotTracker.Status status = spot.update(target);
		if (!config.moveReminder || ticks < messageUntil) {
			lastReminder = null;
			return;
		}
		Component reminder = switch (status) {
			case NOT_AIMING -> Component.translatable("fishingfriend.message.reminder.aim", spot.requiredDistance());
			case TOO_CLOSE -> {
				int blocks = spot.blocksToGo(target);
				String key = bobberInWater ? "fishingfriend.message.reminder.bobber" : "fishingfriend.message.reminder.closer";
				yield Component.translatable(blocks == 1 ? key + ".one" : key, blocks);
			}
			default -> null;
		};
		// Show changes straight away (e.g. aiming back into the spot); otherwise just keep it from fading.
		if (reminder != null && (!reminder.equals(lastReminder) || ticks % REMINDER_REFRESH_TICKS == 0)) {
			showOverlay(reminder.copy().withStyle(ChatFormatting.YELLOW));
		}
		lastReminder = reminder;
	}

	/** The water the player's crosshair is on, or null if they are not looking at water. */
	private static Pos aimedWater(Minecraft client, LocalPlayer player) {
		HitResult hit = player.pick(AIM_RANGE, 1.0f, true);
		if (client.level == null || hit.getType() != HitResult.Type.BLOCK) {
			return null;
		}
		if (!client.level.getFluidState(((BlockHitResult) hit).getBlockPos()).is(FluidTags.WATER)) {
			return null;
		}
		Vec3 at = hit.getLocation();
		return new Pos(at.x, at.y, at.z);
	}

	/** Records what each catch was once its item data has arrived (it follows the spawn by a tick). */
	private void identifyCatches() {
		catchesToIdentify.removeIf(item -> {
			ItemStack stack = item.getItem();
			if (stack.isEmpty()) {
				return item.isRemoved();
			}
			String name = stack.getHoverName().getString();
			int count = stack.getCount();
			FishingStats.Kind kind = kindOf(stack);
			StatsStore.record(stats -> stats.recordItem(name, count, kind));
			return true;
		});
	}

	private static FishingStats.Kind kindOf(ItemStack stack) {
		if (stack.is(ItemTags.FISHES)) {
			return FishingStats.Kind.FISH;
		}
		Item item = stack.getItem();
		if (ALWAYS_TREASURE.contains(item) || (TREASURE_IF_ENCHANTED.contains(item) && stack.isEnchanted())) {
			return FishingStats.Kind.TREASURE;
		}
		return FishingStats.Kind.JUNK;
	}

	private void handleToggleKey(Minecraft client, FishingFriendConfig config) {
		if (toggleKey == null) {
			return;
		}
		while (toggleKey.consumeClick()) {
			config.enabled = !config.enabled;
			config.save();
			showOverlay(Component.translatable(config.enabled ? "fishingfriend.message.enabled" : "fishingfriend.message.disabled"));
		}
	}

	private void alert(FishingFriendConfig.Alert alert, Component message) {
		if (alert.enabled) {
			Sounds.play(alert);
		}
		if (FishingFriendConfig.get().actionBarAlerts) {
			showMessage(message);
		}
	}

	private void bobberWarning(Component message) {
		if (FishingFriendConfig.get().bobberWarnings) {
			showMessage(message.copy().withStyle(ChatFormatting.YELLOW));
		}
	}

	private void showMessage(Component message) {
		messageUntil = ticks + MESSAGE_TICKS;
		showOverlay(message);
	}

	private void showOverlay(Component message) {
		showingOwnMessage = true;
		try {
			VersionCompat.showOverlayMessage(Minecraft.getInstance(), message);
		} finally {
			showingOwnMessage = false;
		}
	}

	private void stop() {
		bobber.reset();
		spot.reset();
		catchesToIdentify.clear();
		pendingServerMessage = null;
	}

	private static BobberTracker.Hook snapshot(FishingHook hook) {
		if (hook == null || hook.isRemoved()) {
			return null;
		}
		boolean biting = ((FishingHookAccessor) hook).fishingfriend$isBiting();
		Entity hooked = hook.getHookedIn();
		String hookedName = hooked == null ? null : hooked.getName().getString();
		return new BobberTracker.Hook(hook.getId(), pos(hook), biting, hook.isInWater(), hook.onGround(), hookedName);
	}

	private static Pos pos(Entity entity) {
		return new Pos(entity.getX(), entity.getY(), entity.getZ());
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
