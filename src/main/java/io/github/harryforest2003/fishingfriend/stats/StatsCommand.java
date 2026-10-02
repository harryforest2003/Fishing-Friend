package io.github.harryforest2003.fishingfriend.stats;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import net.fabricmc.fabric.api.client.command.v2.FabricClientCommandSource;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;

import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * {@code /fishingstats} shows this session's stats, {@code /fishingstats total} all-time stats, and
 * {@code /fishingstats reset [total]} clears them.
 */
public final class StatsCommand {
	private static final int TOP_ITEMS = 6;

	public static void register(CommandDispatcher<FabricClientCommandSource> dispatcher) {
		dispatcher.register(literal("fishingstats")
			.executes(context -> show(context.getSource(), StatsStore.session(), "fishingfriend.stats.title.session"))
			.then(literal("total")
				.executes(context -> show(context.getSource(), StatsStore.total(), "fishingfriend.stats.title.total")))
			.then(literal("reset")
				.executes(context -> {
					StatsStore.startSession();
					context.getSource().sendFeedback(Component.translatable("fishingfriend.stats.reset.session"));
					return 1;
				})
				.then(literal("total").executes(context -> {
					StatsStore.resetTotal();
					context.getSource().sendFeedback(Component.translatable("fishingfriend.stats.reset.total"));
					return 1;
				}))));
	}

	private static int show(FabricClientCommandSource source, FishingStats stats, String titleKey) {
		source.sendFeedback(Component.translatable(titleKey).withStyle(ChatFormatting.GOLD, ChatFormatting.BOLD));
		source.sendFeedback(line(
			"fishingfriend.stats.time", duration(stats.fishingTicks),
			"fishingfriend.stats.casts", stats.casts));
		source.sendFeedback(line(
			"fishingfriend.stats.bites", stats.bites,
			"fishingfriend.stats.caught", stats.catches,
			"fishingfriend.stats.missed", stats.missedBites,
			"fishingfriend.stats.early", stats.reelsWithoutBite));
		source.sendFeedback(line(
			"fishingfriend.stats.empty", stats.emptyCatches,
			"fishingfriend.stats.snapped", stats.lineSnaps));

		double perHour = stats.catchesPerHour();
		source.sendFeedback(line("fishingfriend.stats.per_hour", perHour < 0 ? "-" : format(perHour, 1)));

		double average = stats.averageReactionSeconds();
		source.sendFeedback(average < 0
			? line("fishingfriend.stats.reaction", "-")
			: label("fishingfriend.stats.reaction").append(value(Component.translatable("fishingfriend.stats.reaction.value",
				format(average, 2), format(stats.bestReactionSeconds(), 2)))));

		source.sendFeedback(line(
			"fishingfriend.stats.fish", stats.fish,
			"fishingfriend.stats.treasure", stats.treasure,
			"fishingfriend.stats.junk", stats.junk));

		List<Map.Entry<String, Integer>> top = stats.topItems(TOP_ITEMS);
		if (!top.isEmpty()) {
			MutableComponent items = Component.empty();
			for (int i = 0; i < top.size(); i++) {
				if (i > 0) {
					items.append(Component.literal(", ").withStyle(ChatFormatting.GRAY));
				}
				items.append(Component.literal(top.get(i).getKey() + " ×" + top.get(i).getValue()).withStyle(ChatFormatting.WHITE));
			}
			source.sendFeedback(label("fishingfriend.stats.top").append(items));
		}
		return 1;
	}

	/** "Label: value · Label: value ..." from alternating translation keys and values. */
	private static Component line(Object... keysAndValues) {
		MutableComponent line = Component.empty();
		for (int i = 0; i < keysAndValues.length; i += 2) {
			if (i > 0) {
				line.append(Component.literal("  ·  ").withStyle(ChatFormatting.DARK_GRAY));
			}
			line.append(label((String) keysAndValues[i])).append(value(Component.literal(String.valueOf(keysAndValues[i + 1]))));
		}
		return line;
	}

	private static MutableComponent label(String key) {
		return Component.translatable(key).append(": ").withStyle(ChatFormatting.GRAY);
	}

	private static MutableComponent value(Component value) {
		return value.copy().withStyle(ChatFormatting.WHITE);
	}

	private static String duration(long ticks) {
		long seconds = ticks / 20;
		return seconds >= 3600
			? String.format(Locale.ROOT, "%dh %02dm", seconds / 3600, seconds / 60 % 60)
			: String.format(Locale.ROOT, "%dm %02ds", seconds / 60, seconds % 60);
	}

	private static String format(double value, int decimals) {
		return String.format(Locale.ROOT, "%." + decimals + "f", value);
	}

	private static LiteralArgumentBuilder<FabricClientCommandSource> literal(String name) {
		return LiteralArgumentBuilder.literal(name);
	}

	private StatsCommand() {
	}
}
