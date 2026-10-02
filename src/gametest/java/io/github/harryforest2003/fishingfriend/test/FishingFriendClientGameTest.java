package io.github.harryforest2003.fishingfriend.test;

import io.github.harryforest2003.fishingfriend.config.FishingFriendConfigScreen;
import io.github.harryforest2003.fishingfriend.config.SoundPresets;
import io.github.harryforest2003.fishingfriend.test.mixin.FishingHookMixin;
import net.fabricmc.fabric.api.client.gametest.v1.FabricClientGameTest;
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;
import net.fabricmc.fabric.api.client.gametest.v1.context.TestServerContext;
import net.fabricmc.fabric.api.client.gametest.v1.context.TestSingleplayerContext;
import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.player.Inventory;

import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

/**
 * Fishes in a real singleplayer world and checks which alert sounds the mod plays.
 * Run with {@code ./gradlew :<version>:runClientGameTest}.
 */
@SuppressWarnings("UnstableApiUsage")
public final class FishingFriendClientGameTest implements FabricClientGameTest {
	private static final String BITE_SOUND = SoundPresets.BELL;
	private static final String EMPTY_CATCH_SOUND = SoundPresets.VILLAGER_NO;
	/** Without Lure a fish bites within about 35 seconds. */
	private static final int BITE_TIMEOUT_TICKS = 20 * 90;

	private final List<String> playedSounds = new CopyOnWriteArrayList<>();

	@Override
	public void runTest(ClientGameTestContext context) {
		context.runOnClient(client -> client.getSoundManager().addListener(
			(sound, soundSet, range) -> playedSounds.add(sound.getIdentifier().toString())));

		try (TestSingleplayerContext singleplayer = context.worldBuilder().create()) {
			buildPond(singleplayer.getServer());
			context.waitTicks(40);

			// Reeling in on a bite: ding, then the catch arrives and there is no warning.
			int itemsBefore = countItems(context);
			castAndWaitForBite(context);
			useRod(context);
			context.waitFor(client -> countItems(client.player.getInventory()) > itemsBefore, 100);
			context.waitTicks(40);
			assertNotPlayed(EMPTY_CATCH_SOUND, "after a successful catch");

			// An overfishing rule deletes the catch: the warning plays.
			FishingHookMixin.swallowCatch = true;
			try {
				castAndWaitForBite(context);
				useRod(context);
				context.waitFor(client -> playedSounds.contains(EMPTY_CATCH_SOUND), 60);
			} finally {
				FishingHookMixin.swallowCatch = false;
			}

			// Reeling in before anything bites stays quiet.
			playedSounds.clear();
			useRod(context);
			context.waitTicks(40);
			useRod(context);
			context.waitTicks(40);
			assertNotPlayed(BITE_SOUND, "when reeling in early");
			assertNotPlayed(EMPTY_CATCH_SOUND, "when reeling in early");

			// The config screen opens and Done returns to the previous screen.
			context.setScreen(() -> new FishingFriendConfigScreen(null));
			context.takeScreenshot("fishingfriend-config");
			context.clickScreenButton("gui.done");
			context.waitForScreen(null);
		}
	}

	/** A pond around the player, who stands on a single stone block facing south. */
	private static void buildPond(TestServerContext server) {
		BlockPos feet = server.computeOnServer(s -> s.getPlayerList().getPlayers().getFirst().blockPosition());
		int x = feet.getX(), y = feet.getY(), z = feet.getZ();
		server.runCommand(String.format("fill %d %d %d %d %d %d minecraft:stone", x - 13, y - 5, z - 13, x + 13, y - 1, z + 13));
		server.runCommand(String.format("fill %d %d %d %d %d %d minecraft:water", x - 12, y - 4, z - 12, x + 12, y - 1, z + 12));
		server.runCommand(String.format("setblock %d %d %d minecraft:stone", x, y - 1, z));
		server.runCommand(String.format("fill %d %d %d %d %d %d minecraft:air", x - 13, y, z - 13, x + 13, y + 3, z + 13));
		server.runCommand(String.format("tp @a %d.5 %d %d.5 0 25", x, y, z));
		server.runCommand("item replace entity @a weapon.mainhand with minecraft:fishing_rod");
		server.runCommand("time set day");
		server.runCommand("weather clear");
	}

	private void castAndWaitForBite(ClientGameTestContext context) {
		playedSounds.clear();
		useRod(context);
		context.waitFor(client -> playedSounds.contains(BITE_SOUND), BITE_TIMEOUT_TICKS);
	}

	/** Right click with the rod: casts when there is no bobber out, reels in when there is. */
	private static void useRod(ClientGameTestContext context) {
		context.getInput().pressKey(options -> options.keyUse);
		context.waitTicks(2);
	}

	private void assertNotPlayed(String sound, String when) {
		if (playedSounds.contains(sound)) {
			throw new AssertionError(sound + " played " + when + "; sounds: " + playedSounds);
		}
	}

	private static int countItems(ClientGameTestContext context) {
		return context.computeOnClient(client -> countItems(client.player.getInventory()));
	}

	private static int countItems(Inventory inventory) {
		int total = 0;
		for (int slot = 0; slot < inventory.getContainerSize(); slot++) {
			total += inventory.getItem(slot).getCount();
		}
		return total;
	}
}
