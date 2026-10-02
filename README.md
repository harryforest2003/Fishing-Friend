# Fishing Friend

A client-side Fabric mod that watches your fishing bobber for you, so you can stop staring at it. It's built for servers with overfishing rules.

- **Bite alert:** a ding and **"Reel in now!"** above the hotbar the moment a fish bites (when the bobber dips).
- **Fished-out alert:** a second sound when your spot stops giving fish. It's detected three ways:
  - you reel in on time but nothing comes out (the server deleted your catch);
  - the server says so in chat (mcMMO's "suffering from overfishing" message and similar);
  - you reach the number of catches your server allows in one spot.
- **Move reminder:** once a spot is fished out, a message above the hotbar tells you how many more blocks to move. It shows whenever you aim at the old spot or your bobber lands in it, says "Far enough, cast here!" when you aim far enough away, and comes back if you aim back into the spot.
- **Bobber warnings:** above-the-hotbar messages when your bobber lands on the ground, hooks a mob, a fish gets away, or your line snaps because you walked too far.
- **`/fishingstats`:** casts, bites, catches, misses, fished-out reels, catches per hour, reaction time, fish/treasure/junk and your top catches.
- **Toggle key:** turn the mod on and off without opening any menus.

Everything can be changed in-game through [Mod Menu](https://modrinth.com/mod/modmenu).

## Downloads

Grab the jar for your Minecraft version from the [releases page](https://github.com/harryforest2003/Fishing-Friend/releases):

| Minecraft | Jar |
| --- | --- |
| 1.21 – 1.21.8 | `fishing-friend-<version>+mc1.21-1.21.8.jar` |
| 1.21.9 – 1.21.11 | `fishing-friend-<version>+mc1.21.9-1.21.11.jar` |
| 26.1 – 26.1.2 | `fishing-friend-<version>+mc26.1-26.1.2.jar` |
| 26.2 – 26.3 | `fishing-friend-<version>+mc26.2-26.3.jar` |

Requires [Fabric Loader](https://fabricmc.net/use/) and [Fabric API](https://modrinth.com/mod/fabric-api). Mod Menu is optional; it is only needed to change settings in-game.

## How it works

When a fish bites, the server marks your bobber as "biting" and sends that to your client. That flag is what makes the bobber dip, and it stays set for exactly the window in which reeling in catches the fish. Fishing Friend watches that flag on your own bobber.

When you reel in while the flag is still set, the server spawns your catch at the bobber. If no item appears there (or in your inventory) within a second, the catch was taken away, which is how overfishing rules like mcMMO's work. Reeling in too early or too late never counts.

A fished-out spot is remembered until you catch something again, matching how mcMMO resets its count. Distances are measured along the x and z axes the way servers measure them, so 3 blocks diagonally isn't counted as 3 blocks away.

It only reads what your client already receives, so it works on any server, sends nothing extra, and never fishes for you.

## Settings

With Mod Menu installed, open **Mods → Fishing Friend → configure**.

- **Sounds page:** turn each alert's sound on or off, pick a sound (with a **Test** button), and set its volume and pitch.
- **Spots & Messages page:**
  - **Move Distance:** how far the next spot has to be. Set it to your server's rule.
  - **Catches per Spot:** how many catches your server allows per spot. Off by default.
  - Switches for the move reminder, reading server messages, action bar alerts and bobber warnings.

The toggle key is unbound by default; set it under **Options → Controls → Fishing Friend**.

Settings are saved to `config/fishingfriend.json`. A few extra options live only there:
- `fishedOutPhrases` and `runningLowPhrases`: the chat phrases to look for. Match your server's wording if it isn't mcMMO.
- any sound id for the alerts, e.g. `minecraft:entity.cat.ambient`;
- `emptyCatchWaitTicks`: how long to wait for a catch (20 ticks = 1 second).

## Commands

| Command | |
| --- | --- |
| `/fishingstats` | Stats since you joined the current world or server |
| `/fishingstats total` | All-time stats (saved to `config/fishingfriend-stats.json`) |
| `/fishingstats reset` | Clear this session's stats |
| `/fishingstats reset total` | Clear all-time stats |

## Building

Needs JDK 25 (Gradle runs on it and compiles the 1.21 jars for Java 21).

```bash
./gradlew build
```

The jars end up in `build/libs/`. The project builds one jar per folder in `versions/`, all from the shared code in `src/main`. The few calls that differ between Minecraft versions live in `src/compat/<version>/VersionCompat.java`.

Each jar is compiled against one Minecraft version but claims a range. Check that it links against every release in that range:

```bash
python3 scripts/check_compat.py
```

It downloads each Minecraft client (remapped to Fabric's intermediary names for 1.21.x) and the newest Fabric API for it, then confirms every class, method, field, override and mixin target the jar uses still exists. CI runs it on every push.

Unit tests for the bobber, spot and stats logic run as part of `./gradlew build`. There is also an in-game test that builds a pond and fishes for real. It checks the alerts, a simulated overfishing rule, the move reminder, `/fishingstats` and the config screen, and saves screenshots to `versions/<version>/build/run/clientGameTest/screenshots`. A Minecraft window opens while it runs:

```bash
./gradlew :26.2:runClientGameTest
```

### Adding a Minecraft version

1. If the existing jar's code still works, widen `mc_range` in its `versions/<name>/gradle.properties` and run `scripts/check_compat.py`.
2. Otherwise add a new `versions/<name>/` folder (copy a neighbour and update the versions from [fabricmc.net/develop](https://fabricmc.net/develop)), and if needed a `src/compat/<name>/` copy of `VersionCompat`.

## License

MIT
