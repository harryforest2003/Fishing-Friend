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
import net.minecraft.tags.ItemTags;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.projectile.FishingHook;
import net.minecraft.world.item.FishingRodItem;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;

/**
 * Feeds the game into a {@link BobberTracker} and {@link SpotTracker} every tick, and turns what they
 * report into sounds, action bar messages and stats.
 */
final class FishingWatcher implements BobberTracker.Listener<ItemEntity>, SpotTracker.Listener {
	/** How long "Reel in now!" or a bobber warning keeps the distance message from replacing it. */
	private static final int MESSAGE_TICKS = 50;
	/** The action bar fades after 3 seconds, so the distance message is re-sent more often than that. */
	private static final int DISTANCE_REFRESH_TICKS = 20;
	/** How often (in ticks) to re-check where a cast would land while the distance message is relevant. */
	private static final int AIM_CHECK_TICKS = 2;
	/**
	 * How long to keep showing the last distance when there's briefly nothing to measure (a cast that would
	 * hit the shore, or the bobber still in the air), so the message doesn't blink.
	 */
	private static final int DISTANCE_GRACE_TICKS = 20;
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
	private Component lastDistanceMessage;
	private long distanceShownAt;
	private boolean distanceShowing;
	private int distanceBlocks;
	private boolean distanceFromBobber;
	private long distanceMeasuredAt = Long.MIN_VALUE;
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

		boolean useDown = client.options.keyUse.isDown();
		boolean usePressed = useDown && !useWasDown;
		useWasDown = useDown;

		// Only follow the bobber while there is one (or a catch is still being waited for), so a player who
		// isn't fishing costs next to nothing.
		FishingHook fishingHook = player.fishing;
		if (fishingHook != null || bobber.isOut() || bobber.isAwaitingCatch()) {
			bobber.setEmptyCatchWaitTicks(config.emptyCatchWaitTicks);
			bobber.setMaxOnTimeReactionTicks(config.maxOnTimeReactionTicks);
			spot.setMoveDistance(config.moveDistance);
			spot.setCatchLimit(config.catchesPerSpot);
			BobberTracker.Player me = new BobberTracker.Player(
				pos(player), isHoldingRod(player), usePressed, countItems(player.getInventory()));
			BobberTracker.Hook hook = snapshot(fishingHook);
			bobber.tick(ticks, hook, me);
			if (hook != null) {
				StatsStore.record(stats -> stats.fishingTicks++);
			}
		}

		handlePendingServerMessage();
		if (!catchesToIdentify.isEmpty()) {
			identifyCatches();
		}
		updateDistanceMessage(client, player, config);
		if (ticks % STATS_SAVE_TICKS == 0) {
			StatsStore.saveIfChanged();
		}
	}

	void onEntityLoad(Entity entity, ClientLevel level) {
		if (!bobber.isOut() && !bobber.isAwaitingCatch()) {
			return;
		}
		if (entity instanceof ItemEntity item) {
			bobber.onItemSpawned(ticks, pos(entity), item);
		}
	}

	void onGameMessage(Component message, boolean overlay) {
		FishingFriendConfig config = FishingFriendConfig.get();
		if (showingOwnMessage || !inWorld || !config.enabled || !config.readServerMessages) {
			return;
		}
		// Busy servers send a lot of chat; only read it around a cast or reel, when a warning could arrive.
		if (!bobber.isOut() && !bobber.usedRodWithin(ticks, SERVER_MESSAGE_WINDOW_TICKS)) {
			return;
		}
		String text = message.getString();
		if (!ServerMessages.containsAny(text, config.fishedOutPhrases)) {
			return;
		}
		int blocks = ServerMessages.blocksMentioned(text);
		Runnable warning = () -> spot.onServerSaysFishedOut(bobber.hookPos(), blocks, ticks);

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
		if (config.bite.enabled) {
			Sounds.play(config.bite);
		}
		if (config.reelInMessage) {
			showMessage(Component.translatable("fishingfriend.message.bite").withStyle(ChatFormatting.GOLD, ChatFormatting.BOLD));
		}
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
		spot.onCatch(at, ticks);
	}

	@Override
	public void onEmptyCatch(Pos at, int reactionTicks) {
		StatsStore.record(stats -> stats.recordEmptyCatch(reactionTicks));
		spot.onEmptyCatch(at, ticks);
	}

	@Override
	public void onReeledInLate(int reactionTicks) {
		StatsStore.record(stats -> stats.missedBites++);
	}

	@Override
	public void onLineSnapped() {
		StatsStore.record(stats -> stats.lineSnaps++);
		bobberWarning(Component.translatable("fishingfriend.message.line_snapped"));
	}

	// --- Spot events ---

	/** The spot is fished out: just the sound. How far to move shows once the player aims at the water. */
	@Override
	public void onSpotFishedOut(boolean again) {
		FishingFriendConfig.Alert alert = FishingFriendConfig.get().fishedOut;
		if (alert.enabled) {
			Sounds.play(alert);
		}
	}

	// --- Helpers ---

	/**
	 * While the player is actually fishing near a fished-out spot (holding a rod, no menu open, and about to
	 * cast into it or with the bobber in it), shows how much further away they need to cast.
	 */
	private void updateDistanceMessage(Minecraft client, LocalPlayer player, FishingFriendConfig config) {
		if (!config.distanceMessage || !spot.hasFishedOutSpot() || !isHoldingRod(player) || VersionCompat.isScreenOpen(client)) {
			hideDistanceMessage();
			return;
		}
		if (ticks % AIM_CHECK_TICKS != 0) {
			return;
		}

		// With a bobber in the water, measure from it; otherwise from where a cast would land right now.
		Pos target = null;
		boolean fromBobber = false;
		if (bobber.isOut()) {
			if (bobber.inWater()) {
				target = bobber.hookPos();
				fromBobber = true;
			}
		} else if (client.level != null) {
			target = CastPrediction.landingPoint(client.level, player);
		}
		if (target != null) {
			distanceBlocks = spot.blocksToGo(target);
			distanceFromBobber = fromBobber;
			distanceMeasuredAt = ticks;
		} else if (ticks - distanceMeasuredAt > DISTANCE_GRACE_TICKS) {
			distanceBlocks = 0;
		}
		if (distanceBlocks == 0) {
			hideDistanceMessage();
			return;
		}

		String key = distanceFromBobber ? "fishingfriend.message.distance.bobber" : "fishingfriend.message.distance.aim";
		Component message = Component.translatable(distanceBlocks == 1 ? key + ".one" : key, distanceBlocks)
			.withStyle(ChatFormatting.YELLOW);
		// Show changes straight away; otherwise re-send often enough that it never fades.
		boolean changed = !message.equals(lastDistanceMessage);
		if (ticks >= messageUntil && (changed || !distanceShowing || ticks - distanceShownAt >= DISTANCE_REFRESH_TICKS)) {
			showOverlay(message);
			distanceShownAt = ticks;
			distanceShowing = true;
		}
		lastDistanceMessage = message;
	}

	/** Clears the distance message straight away once it no longer applies, rather than letting it linger. */
	private void hideDistanceMessage() {
		if (distanceShowing && ticks >= messageUntil) {
			showOverlay(Component.empty());
		}
		distanceShowing = false;
		lastDistanceMessage = null;
		distanceBlocks = 0;
		distanceMeasuredAt = Long.MIN_VALUE;
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

	private void bobberWarning(Component message) {
		if (FishingFriendConfig.get().bobberWarnings) {
			showMessage(message.copy().withStyle(ChatFormatting.YELLOW));
		}
	}

	private void showMessage(Component message) {
		messageUntil = ticks + MESSAGE_TICKS;
		distanceShowing = false;
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
