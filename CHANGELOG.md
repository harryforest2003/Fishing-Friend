# Changelog

## 1.2.1

- Reeling in too early or too late no longer marks the spot as overfished. An empty reel only counts if you reeled in within 0.75 seconds of the bite; slower empty reels count as missed bites. The server's own overfishing message still works however slow you are.
- The distance message now measures from where your cast would actually land (the mod runs the same physics as the bobber), instead of where your crosshair meets the water. Looking at the horizon, those can be 10+ blocks apart.
- The distance message no longer blinks when your aim briefly leaves the water or while the bobber is in the air, and it updates twice as often.

## 1.2.0

- Far fewer action bar messages. Only two are left: "Reel in now!" when a fish bites, and how much further away to cast while you're aiming at (or fishing in) an overfished spot.
- The distance message only shows while you're holding a rod with no menu open, so nothing piles up while the game is in the background. It clears as soon as you're far enough away.
- Removed the "spot fished out", "far enough" and "running low" messages and the always-on "aim at water" reminder. The fished-out sound still plays.
- Bobber warnings are now off by default (existing settings are switched off once); turn them back on under Spots & Messages if you want them.
- Less work every tick: nothing runs while you aren't fishing, the aim check runs every few ticks and only near an overfished spot, and server chat is only read around a cast or reel.

## 1.1.1

- Fixed a crash on Minecraft 26.x when a server warned that fish were running low. The mod's own action bar message was read back as another server warning, over and over.
- Server overfishing warnings only count right around a cast or reel, so players typing "overfishing" in chat (on servers that relay chat as system messages) no longer trigger anything. Warnings sent just before the bobber is removed, as mcMMO does, still count.
- Fishing a spot that is already fished out now alerts again ("This spot is still fished out!") and brings the move reminder back. One reel only ever gives one alert.

## 1.1.0

- "Reel in now!" in the action bar when a fish bites.
- Fished-out detection from empty catches, server chat and action bar messages (the distance in messages like mcMMO's is picked up), and an optional catches-per-spot limit.
- Move reminder in the action bar while you aim at or fish in a fished-out spot; it clears when you aim far enough away and comes back if you aim back in.
- Bobber warnings: landed on the ground, hooked a mob, a fish got away, the line snapped.
- `/fishingstats` with catches per hour, reaction time and what you caught, for this session and all time.
- A key to turn the mod on and off (unbound by default).
- Two-page settings screen in Mod Menu.

## 1.0.0

- Ding when a fish bites, and a second sound when you reel in on time but catch nothing.
- Sounds, volume and pitch configurable through Mod Menu.
