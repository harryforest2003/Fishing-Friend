package io.github.harryforest2003.fishingfriend.test;

import io.github.harryforest2003.fishingfriend.config.FishingFriendConfigScreen;
import io.github.harryforest2003.fishingfriend.config.SoundPresets;
import io.github.harryforest2003.fishingfriend.stats.FishingStats;
import io.github.harryforest2003.fishingfriend.stats.StatsStore;
import net.fabricmc.fabric.api.client.gametest.v1.FabricClientGameTest;
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;
import net.fabricmc.fabric.api.client.gametest.v1.context.TestServerContext;
import net.fabricmc.fabric.api.client.gametest.v1.context.TestSingleplayerContext;
import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.player.Inventory;

import java.util.Collections;
import java.util.List;
import java.util.Locale;
import java.util.function.Predicate;

/**
 * Fishes in a real singleplayer world and checks the sounds, stats and messages the mod produces.
 * Run with {@code ./gradlew :<version>:runClientGameTest}; screenshots land in the run directory.
 */
@SuppressWarnings("UnstableApiUsage")
public final class FishingFriendClientGameTest implements FabricClientGameTest {
	private static final String BITE_SOUND = SoundPresets.BELL;
	private static final String FISHED_OUT_SOUND = SoundPresets.VILLAGER_NO;
	/** Without Lure a fish bites within about 35 seconds. */
	private static final int BITE_TIMEOUT_TICKS = 20 * 90;
	private static final int ENTER = 257;

	private final List<String> playedSounds = PlayedAlerts.SOUNDS;
	private BlockPos feet;

	@Override
	public void runTest(ClientGameTestContext context) {
		try (TestSingleplayerContext singleplayer = context.worldBuilder().create()) {
			TestServerContext server = singleplayer.getServer();
			buildPond(server);
			context.waitTicks(40);

			// Reeling in on a bite: ding, the catch arrives, no fished-out alert, and it shows in the stats.
			int itemsBefore = countItems(context);
			castAndWaitForBite(context);
			useRod(context);
			context.waitFor(client -> countItems(client.player.getInventory()) > itemsBefore, 100);
			context.waitTicks(40);
			assertNotPlayed(FISHED_OUT_SOUND, "after a successful catch");
			assertStats("after a catch", stats -> stats.bites >= 1 && stats.catches == 1 && !stats.items.isEmpty());

			// An overfishing rule deletes the catch: the fished-out alert plays and the reminder starts.
			OverfishingRule.active = true;
			try {
				castAndWaitForBite(context);
				useRod(context);
				context.waitFor(client -> playedSounds.contains(FISHED_OUT_SOUND), 60);
				assertStats("after an empty catch", stats -> stats.emptyCatches == 1);

				// Fishing the same spot again by mistake: the server repeats its warning (just before removing the
				// bobber, as mcMMO does) and the catch is deleted again. One alert for the reel, and no crash.
				castAndWaitForBite(context);
				server.runCommand("tellraw @a \"That area is suffering from overfishing. At least 3 blocks away.\"");
				server.runCommand("title @a actionbar \"That area is suffering from overfishing. At least 3 blocks away.\"");
				useRod(context);
				context.waitFor(client -> playedSounds.contains(FISHED_OUT_SOUND), 60);
				context.waitTicks(60);
				int alerts = Collections.frequency(playedSounds, FISHED_OUT_SOUND);
				if (alerts != 1) {
					throw new AssertionError("expected one fished-out alert for the repeat reel, got " + alerts + ": " + playedSounds);
				}
				assertStats("after fishing the spot again", stats -> stats.emptyCatches == 2);
			} finally {
				OverfishingRule.active = false;
			}

			// Aiming at the fished-out spot shows the reminder, aiming away clears it, aiming back brings it back.
			context.waitTicks(60);
			context.takeScreenshot("fishingfriend-reminder-aiming-at-spot");
			face(server, 180);
			context.waitTicks(5);
			context.takeScreenshot("fishingfriend-reminder-far-enough");
			context.waitTicks(60);
			face(server, 0);
			context.waitTicks(5);
			context.takeScreenshot("fishingfriend-reminder-back-in-spot");

			// Server warnings only count right after casting or reeling in, so ordinary chat can't trigger them.
			face(server, 180);
			useRod(context);
			context.waitTicks(130);
			playedSounds.clear();
			server.runCommand("tellraw @a \"That area is suffering from overfishing. At least 5 blocks away.\"");
			context.waitTicks(40);
			assertNotPlayed(FISHED_OUT_SOUND, "for a server message long after casting");

			// The same message right as the bobber is reeled in marks this spot as fished out. Servers send it
			// just before removing the bobber, so send it first here too.
			server.runCommand("tellraw @a \"That area is suffering from overfishing. At least 5 blocks away.\"");
			useRod(context);
			context.waitFor(client -> playedSounds.contains(FISHED_OUT_SOUND), 40);

			// And it still counts when it arrives just after the reel. Wait a few seconds first: warnings for the
			// same spot within three seconds count as one reel.
			playedSounds.clear();
			face(server, 0);
			useRod(context);
			context.waitTicks(80);
			useRod(context);
			server.runCommand("tellraw @a \"That area is suffering from overfishing. At least 5 blocks away.\"");
			context.waitFor(client -> playedSounds.contains(FISHED_OUT_SOUND), 40);

			// A running-low warning in the action bar. On 26.x the mod's own action bar message also arrives as a
			// message event, which used to set off an endless loop and crash the game.
			server.runCommand("title @a actionbar \"You sense that there might not be many fish left in this area.\"");
			context.waitTicks(3);
			context.takeScreenshot("fishingfriend-server-running-low");

			// Reeling in before anything bites stays quiet.
			face(server, 180);
			playedSounds.clear();
			useRod(context);
			context.waitTicks(40);
			useRod(context);
			context.waitTicks(40);
			assertNotPlayed(BITE_SOUND, "when reeling in early");
			assertNotPlayed(FISHED_OUT_SOUND, "when reeling in early");

			// /fishingstats prints to chat.
			context.getInput().pressKey(options -> options.keyChat);
			context.waitTicks(5);
			context.getInput().typeChars("/fishingstats");
			context.getInput().pressKey(ENTER);
			context.waitTicks(10);
			context.takeScreenshot("fishingfriend-stats");

			// Both config pages open, and Done returns to the previous screen.
			context.setScreen(() -> new FishingFriendConfigScreen(null));
			context.takeScreenshot("fishingfriend-config-sounds");
			context.clickScreenButton("fishingfriend.config.page.spots");
			context.takeScreenshot("fishingfriend-config-spots");
			context.clickScreenButton("gui.done");
			context.waitForScreen(null);
		}
	}

	/**
	 * A raised pond with the player standing on a single stone block in the middle, facing south.
	 * It is built above the ground because test worlds spawn the player right at the bottom of the world.
	 */
	private void buildPond(TestServerContext server) {
		feet = server.computeOnServer(s -> s.getPlayerList().getPlayers().getFirst().blockPosition()).above(6);
		int x = feet.getX(), y = feet.getY(), z = feet.getZ();
		server.runCommand(String.format("fill %d %d %d %d %d %d minecraft:stone", x - 13, y - 5, z - 13, x + 13, y - 1, z + 13));
		server.runCommand(String.format("fill %d %d %d %d %d %d minecraft:water", x - 12, y - 4, z - 12, x + 12, y - 1, z + 12));
		server.runCommand(String.format("setblock %d %d %d minecraft:stone", x, y - 1, z));
		server.runCommand(String.format("fill %d %d %d %d %d %d minecraft:air", x - 13, y, z - 13, x + 13, y + 3, z + 13));
		face(server, 0);
		server.runCommand("item replace entity @a weapon.mainhand with minecraft:fishing_rod");
		server.runCommand("time set day");
		server.runCommand("weather clear");
	}

	/** Turns the player to {@code yaw} (0 = south), looking a little down at the water. */
	private void face(TestServerContext server, int yaw) {
		// Block centres are x + 0.5 even for negative coordinates (block -10 spans -10 to -9).
		server.runCommand(String.format(Locale.ROOT, "tp @a %.1f %d %.1f %d 25", feet.getX() + 0.5, feet.getY(), feet.getZ() + 0.5, yaw));
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

	private static void assertStats(String when, Predicate<FishingStats> check) {
		FishingStats stats = StatsStore.session();
		if (!check.test(stats)) {
			throw new AssertionError("unexpected stats " + when + ": bites=" + stats.bites + " catches=" + stats.catches
				+ " empty=" + stats.emptyCatches + " items=" + stats.items);
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
