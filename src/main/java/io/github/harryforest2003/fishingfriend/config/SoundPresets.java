package io.github.harryforest2003.fishingfriend.config;

import java.util.List;

/** The sounds the config screen cycles through. Any other sound id can still be set in the config file. */
public final class SoundPresets {
	public static final String BELL = "minecraft:block.note_block.bell";
	public static final String VILLAGER_NO = "minecraft:entity.villager.no";

	public record Preset(String sound, String translationKey) {
	}

	public static final List<Preset> ALL = List.of(
		preset(BELL, "bell"),
		preset("minecraft:block.note_block.chime", "chime"),
		preset("minecraft:block.note_block.pling", "pling"),
		preset("minecraft:entity.experience_orb.pickup", "xp"),
		preset("minecraft:entity.arrow.hit_player", "arrow_ding"),
		preset("minecraft:block.bell.use", "village_bell"),
		preset("minecraft:entity.player.levelup", "level_up"),
		preset("minecraft:block.note_block.bit", "bit"),
		preset(VILLAGER_NO, "villager_no"),
		preset("minecraft:block.note_block.bass", "bass"),
		preset("minecraft:block.note_block.didgeridoo", "didgeridoo"),
		preset("minecraft:block.anvil.land", "anvil"),
		preset("minecraft:entity.item.break", "item_break")
	);

	/** Index of the preset for {@code sound}, or -1 if it is a custom sound. */
	public static int indexOf(String sound) {
		for (int i = 0; i < ALL.size(); i++) {
			if (ALL.get(i).sound().equals(sound)) {
				return i;
			}
		}
		return -1;
	}

	/** The preset after {@code sound}, wrapping around; a custom sound moves to the first preset. */
	public static Preset next(String sound) {
		return ALL.get((indexOf(sound) + 1) % ALL.size());
	}

	private static Preset preset(String sound, String name) {
		return new Preset(sound, "fishingfriend.sound." + name);
	}

	private SoundPresets() {
	}
}
