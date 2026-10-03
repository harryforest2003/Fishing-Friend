package io.github.harryforest2003.fishingfriend.config;

import io.github.harryforest2003.fishingfriend.Sounds;
import io.github.harryforest2003.fishingfriend.compat.VersionCompat;
import net.minecraft.ChatFormatting;
import net.minecraft.client.gui.components.AbstractSliderButton;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.StringWidget;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.CommonComponents;
import net.minecraft.network.chat.Component;

import java.util.function.BooleanSupplier;
import java.util.function.Consumer;
import java.util.function.DoubleConsumer;
import java.util.function.DoubleFunction;

/**
 * Settings screen opened from Mod Menu, split into two pages. Built only from plain vanilla widgets
 * so it needs no config library and works across every supported Minecraft version.
 */
public final class FishingFriendConfigScreen extends Screen {
	private enum Page { SOUNDS, SPOTS }

	private static final int COLUMN_WIDTH = 150;
	private static final int GAP = 10;
	private static final int FULL_WIDTH = COLUMN_WIDTH * 2 + GAP;
	private static final int ROW = 24;
	private static final int TEST_BUTTON_WIDTH = 40;
	private static final int CONTENT_HEIGHT = 210;

	private final Screen parent;
	private Page page = Page.SOUNDS;

	public FishingFriendConfigScreen(Screen parent) {
		super(Component.translatable("fishingfriend.config.title"));
		this.parent = parent;
	}

	@Override
	protected void init() {
		int left = width / 2 - FULL_WIDTH / 2;
		int right = left + COLUMN_WIDTH + GAP;
		int y = Math.max(6, (height - CONTENT_HEIGHT) / 2);

		addRenderableWidget(centeredText(y, title));
		y += 14;

		addRenderableWidget(tab(left, y, Page.SOUNDS, "fishingfriend.config.page.sounds"));
		addRenderableWidget(tab(right, y, Page.SPOTS, "fishingfriend.config.page.spots"));
		y += ROW + 8;

		y = page == Page.SOUNDS ? addSoundsPage(left, right, y) : addSpotsPage(left, right, y);

		addRenderableWidget(Button.builder(CommonComponents.GUI_DONE, button -> onClose())
			.bounds(width / 2 - 100, y + 8, 200, 20)
			.build());
	}

	@Override
	public void onClose() {
		FishingFriendConfig.get().save();
		VersionCompat.setScreen(minecraft, parent);
	}

	private int addSoundsPage(int left, int right, int y) {
		FishingFriendConfig config = FishingFriendConfig.get();
		addRenderableWidget(withTooltip(toggle(left, y, FULL_WIDTH, "fishingfriend.config.enabled",
			() -> config.enabled, value -> config.enabled = value), "fishingfriend.config.enabled.tooltip"));
		y += ROW + 6;

		addRenderableWidget(header(left, y, "fishingfriend.config.bite"));
		addRenderableWidget(header(right, y, "fishingfriend.config.fished_out"));
		y += 14;

		addAlertColumn(left, y, config.bite, "fishingfriend.config.bite.tooltip");
		addAlertColumn(right, y, config.fishedOut, "fishingfriend.config.fished_out.tooltip");
		return y + ROW * 4;
	}

	private int addSpotsPage(int left, int right, int y) {
		FishingFriendConfig config = FishingFriendConfig.get();
		addRenderableWidget(withTooltip(new Slider(left, y, "fishingfriend.config.move_distance", 1, 32, 1, config.moveDistance,
				value -> config.moveDistance = (int) value,
				value -> Component.translatable(value == 1 ? "fishingfriend.config.blocks.one" : "fishingfriend.config.blocks", (int) value)),
			"fishingfriend.config.move_distance.tooltip"));
		addRenderableWidget(withTooltip(new Slider(right, y, "fishingfriend.config.catches_per_spot", 0, 50, 1, config.catchesPerSpot,
				value -> config.catchesPerSpot = (int) value,
				value -> value == 0 ? CommonComponents.OPTION_OFF : Component.literal(String.valueOf((int) value))),
			"fishingfriend.config.catches_per_spot.tooltip"));
		y += ROW;

		addRenderableWidget(withTooltip(toggle(left, y, COLUMN_WIDTH, "fishingfriend.config.reel_in_message",
			() -> config.reelInMessage, value -> config.reelInMessage = value), "fishingfriend.config.reel_in_message.tooltip"));
		addRenderableWidget(withTooltip(toggle(right, y, COLUMN_WIDTH, "fishingfriend.config.distance_message",
			() -> config.distanceMessage, value -> config.distanceMessage = value), "fishingfriend.config.distance_message.tooltip"));
		y += ROW;

		addRenderableWidget(withTooltip(toggle(left, y, COLUMN_WIDTH, "fishingfriend.config.server_messages",
			() -> config.readServerMessages, value -> config.readServerMessages = value), "fishingfriend.config.server_messages.tooltip"));
		addRenderableWidget(withTooltip(toggle(right, y, COLUMN_WIDTH, "fishingfriend.config.bobber_warnings",
			() -> config.bobberWarnings, value -> config.bobberWarnings = value), "fishingfriend.config.bobber_warnings.tooltip"));
		y += ROW + 6;

		addRenderableWidget(centeredText(y,
			Component.translatable("fishingfriend.config.toggle_key_hint").withStyle(ChatFormatting.GRAY)));
		return y + 14;
	}

	private void addAlertColumn(int x, int y, FishingFriendConfig.Alert alert, String tooltipKey) {
		addRenderableWidget(withTooltip(toggle(x, y, COLUMN_WIDTH, "fishingfriend.config.play_sound",
			() -> alert.enabled, value -> alert.enabled = value), tooltipKey));
		y += ROW;

		addRenderableWidget(Button.builder(soundLabel(alert.sound), button -> {
				alert.sound = SoundPresets.next(alert.sound).sound();
				button.setMessage(soundLabel(alert.sound));
				Sounds.play(alert);
			})
			.bounds(x, y, COLUMN_WIDTH - TEST_BUTTON_WIDTH - 4, 20)
			.build());
		addRenderableWidget(Button.builder(Component.translatable("fishingfriend.config.test"), button -> Sounds.play(alert))
			.bounds(x + COLUMN_WIDTH - TEST_BUTTON_WIDTH, y, TEST_BUTTON_WIDTH, 20)
			.build());
		y += ROW;

		addRenderableWidget(new Slider(x, y, "fishingfriend.config.volume", 0, 1, 0.01, alert.volume,
			value -> alert.volume = (float) value, value -> Component.literal(Math.round(value * 100) + "%")));
		y += ROW;

		addRenderableWidget(new Slider(x, y, "fishingfriend.config.pitch", 0.5, 2, 0.05, alert.pitch,
			value -> alert.pitch = (float) value, value -> Component.literal(String.format("%.2f", value))));
	}

	private Button tab(int x, int y, Page target, String key) {
		Button button = Button.builder(Component.translatable(key), b -> {
				page = target;
				rebuildWidgets();
			})
			.bounds(x, y, COLUMN_WIDTH, 20)
			.build();
		button.active = page != target;
		return button;
	}

	/** Text centred on the screen. Sized to the text, since newer versions left-align text in wider widgets. */
	private StringWidget centeredText(int y, Component text) {
		int textWidth = font.width(text);
		return new StringWidget(width / 2 - textWidth / 2, y, textWidth, 9, text, font);
	}

	private StringWidget header(int x, int y, String key) {
		return new StringWidget(x, y, COLUMN_WIDTH, 9, Component.translatable(key).withStyle(ChatFormatting.YELLOW), font);
	}

	private static <T extends AbstractWidget> T withTooltip(T widget, String key) {
		widget.setTooltip(Tooltip.create(Component.translatable(key)));
		return widget;
	}

	private static Button toggle(int x, int y, int width, String key, BooleanSupplier getter, Consumer<Boolean> setter) {
		Component caption = Component.translatable(key);
		return Button.builder(CommonComponents.optionStatus(caption, getter.getAsBoolean()), button -> {
				boolean value = !getter.getAsBoolean();
				setter.accept(value);
				button.setMessage(CommonComponents.optionStatus(caption, value));
			})
			.bounds(x, y, width, 20)
			.build();
	}

	private static Component soundLabel(String sound) {
		int index = SoundPresets.indexOf(sound);
		Component name = index >= 0
			? Component.translatable(SoundPresets.ALL.get(index).translationKey())
			: Component.literal(sound);
		return Component.translatable("fishingfriend.config.sound", name);
	}

	/** Slider over [min, max], snapped to {@code step}, that writes straight into the config. */
	private static final class Slider extends AbstractSliderButton {
		private final Component caption;
		private final double min;
		private final double max;
		private final double step;
		private final DoubleConsumer setter;
		private final DoubleFunction<Component> formatter;

		Slider(int x, int y, String key, double min, double max, double step, double current,
			   DoubleConsumer setter, DoubleFunction<Component> formatter) {
			super(x, y, COLUMN_WIDTH, 20, Component.empty(), (current - min) / (max - min));
			this.caption = Component.translatable(key);
			this.min = min;
			this.max = max;
			this.step = step;
			this.setter = setter;
			this.formatter = formatter;
			updateMessage();
		}

		private double actualValue() {
			double raw = min + value * (max - min);
			return Math.max(min, Math.min(max, min + Math.round((raw - min) / step) * step));
		}

		@Override
		protected void updateMessage() {
			setMessage(Component.translatable("options.generic_value", caption, formatter.apply(actualValue())));
		}

		@Override
		protected void applyValue() {
			setter.accept(actualValue());
		}
	}
}
