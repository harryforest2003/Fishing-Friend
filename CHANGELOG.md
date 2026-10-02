# Changelog

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
