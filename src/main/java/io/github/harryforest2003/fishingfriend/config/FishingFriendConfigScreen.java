package io.github.harryforest2003.fishingfriend.config;

import io.github.harryforest2003.fishingfriend.Sounds;
import io.github.harryforest2003.fishingfriend.compat.VersionCompat;
import net.minecraft.ChatFormatting;
import net.minecraft.client.gui.components.AbstractSliderButton;
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
 * Settings screen opened from Mod Menu. Built only from plain vanilla widgets so it needs no config
 * library and works across every supported Minecraft version.
 */
public final class FishingFriendConfigScreen extends Screen {
	private static final int COLUMN_WIDTH = 150;
	private static final int GAP = 10;
	private static final int ROW = 24;
	private static final int TEST_BUTTON_WIDTH = 40;
	private static final int CONTENT_HEIGHT = 214;

	private final Screen parent;

	public FishingFriendConfigScreen(Screen parent) {
		super(Component.translatable("fishingfriend.config.title"));
		this.parent = parent;
	}

	@Override
	protected void init() {
		FishingFriendConfig config = FishingFriendConfig.get();
		int fullWidth = COLUMN_WIDTH * 2 + GAP;
		int left = width / 2 - fullWidth / 2;
		int right = left + COLUMN_WIDTH + GAP;
		int y = Math.max(8, (height - CONTENT_HEIGHT) / 2);

		addRenderableWidget(new StringWidget(0, y, width, 9, title, font));
		y += 18;

		addRenderableWidget(toggle(left, y, fullWidth, "fishingfriend.config.enabled",
			() -> config.enabled, value -> config.enabled = value));
		y += ROW + 8;

		addRenderableWidget(header(left, y, "fishingfriend.config.bite"));
		addRenderableWidget(header(right, y, "fishingfriend.config.empty_catch"));
		y += 14;

		addAlertColumn(left, y, config.bite, "fishingfriend.config.bite.tooltip");
		addAlertColumn(right, y, config.emptyCatch, "fishingfriend.config.empty_catch.tooltip");
		y += ROW * 4 + 4;

		addRenderableWidget(toggle(left, y, fullWidth, "fishingfriend.config.messages",
			() -> config.showMessages, value -> config.showMessages = value));
		y += ROW + 8;

		addRenderableWidget(Button.builder(CommonComponents.GUI_DONE, button -> onClose())
			.bounds(width / 2 - 100, y, 200, 20)
			.build());
	}

	@Override
	public void onClose() {
		FishingFriendConfig.get().save();
		VersionCompat.setScreen(minecraft, parent);
	}

	private void addAlertColumn(int x, int y, FishingFriendConfig.Alert alert, String tooltipKey) {
		Button enabled = toggle(x, y, COLUMN_WIDTH, "fishingfriend.config.alert_enabled",
			() -> alert.enabled, value -> alert.enabled = value);
		enabled.setTooltip(Tooltip.create(Component.translatable(tooltipKey)));
		addRenderableWidget(enabled);
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

		addRenderableWidget(new Slider(x, y, "fishingfriend.config.volume", 0, 1, alert.volume,
			value -> alert.volume = (float) value, value -> Math.round(value * 100) + "%"));
		y += ROW;

		addRenderableWidget(new Slider(x, y, "fishingfriend.config.pitch", 0.5, 2, alert.pitch,
			value -> alert.pitch = (float) value, value -> String.format("%.2f", value)));
	}

	private StringWidget header(int x, int y, String key) {
		return new StringWidget(x, y, COLUMN_WIDTH, 9, Component.translatable(key).withStyle(ChatFormatting.YELLOW), font);
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

	/** Slider over [min, max] that writes straight into the config. */
	private static final class Slider extends AbstractSliderButton {
		private final Component caption;
		private final double min;
		private final double max;
		private final DoubleConsumer setter;
		private final DoubleFunction<String> formatter;

		Slider(int x, int y, String key, double min, double max, double current,
			   DoubleConsumer setter, DoubleFunction<String> formatter) {
			super(x, y, COLUMN_WIDTH, 20, Component.empty(), (current - min) / (max - min));
			this.caption = Component.translatable(key);
			this.min = min;
			this.max = max;
			this.setter = setter;
			this.formatter = formatter;
			updateMessage();
		}

		private double actualValue() {
			return min + value * (max - min);
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
