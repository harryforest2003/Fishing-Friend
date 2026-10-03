# Fishing Friend

A client-side Fabric mod that watches your fishing bobber for you, so you can stop staring at it. It's built for servers with overfishing rules.

- **Bite alert:** a ding and **"Reel in now!"** above the hotbar the moment a fish bites (when the bobber dips).
- **Fished-out alert:** a second sound when your spot stops giving fish. It's detected three ways:
  - you reel in on time but nothing comes out (the server deleted your catch);
  - the server says so (mcMMO's "suffering from overfishing" message and similar, in chat or the action bar);
  - you reach the number of catches your server allows in one spot.
- **Distance message:** while you're holding a rod and about to cast into (or fishing in) an overfished spot, the action bar shows how much further away you need to cast. It works out where your cast would land, using the bobber's own physics, and disappears once that's far enough away.
- **`/fishingstats`:** casts, bites, catches, misses, fished-out reels, catches per hour, reaction time, fish/treasure/junk and your top catches.
- **Toggle key:** turn the mod on and off without opening any menus.
- **Bobber warnings (off by default):** messages when your bobber lands on the ground, hooks a mob, a fish gets away, or your line snaps.

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

When you reel in while the flag is still set, the server spawns your catch at the bobber. If no item appears there (or in your inventory) within a second, the catch was taken away, which is how overfishing rules like mcMMO's work. Because the flag can arrive late, an empty reel only counts if you reeled in within 0.75 seconds of the bite (`maxOnTimeReactionTicks` in the config file). Reeling in too early or too late never counts.

A fished-out spot is remembered until you catch something again, matching how mcMMO resets its count. Fishing it again anyway plays the alert again. Distances are measured along the x and z axes the way servers measure them, so 3 blocks diagonally isn't counted as 3 blocks away.

It only reads what your client already receives, so it works on any server, sends nothing extra, and never fishes for you.

## Settings

With Mod Menu installed, open **Mods → Fishing Friend → configure**.

- **Sounds page:** turn each alert's sound on or off, pick a sound (with a **Test** button), and set its volume and pitch.
- **Spots & Messages page:**
  - **Move Distance:** how far the next spot has to be. Set it to your server's rule.
  - **Catches per Spot:** how many catches your server allows per spot. Off by default.
  - Switches for the "Reel in now!" message, the distance message, reading server messages, and bobber warnings.

The toggle key is unbound by default; set it under **Options → Controls → Fishing Friend**.

Settings are saved to `config/fishingfriend.json`. A few extra options live only there:
- `fishedOutPhrases`: the server phrases that mean a spot is fished out. Match your server's wording if it isn't mcMMO.
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

Unit tests for the bobber, spot and stats logic run as part of `./gradlew build`.

There is also an in-game test that builds a pond and fishes for real. It checks:
- the alerts and a simulated overfishing rule, including fishing the same spot twice;
- server warnings in chat and in the action bar;
- exactly which action bar messages appear, and that none do with a menu open or without a rod;
- `/fishingstats` and the config screen.

It also times the mod's per-tick code and prints the average and worst tick.

GitHub runs it on every push, on a virtual screen, for each build. It also runs the released 1.21.9–1.21.11 jar on 1.21.10, a version it wasn't compiled against, using the harness in `testing/jar-test`. Screenshots from each run are attached to the workflow run. To run it locally (a Minecraft window opens):

```bash
./gradlew :26.2:runClientGameTest
```

### Releasing

1. Bump `mod_version` in `gradle.properties` and add a section for it to `CHANGELOG.md`.
2. Push to `main`, then tag it: `git tag v<version> && git push origin v<version>`.

GitHub builds the jars, runs the unit, compatibility and in-game tests, and only if they all pass publishes the release. The notes come from the changelog and the jar table is generated from `versions/`.

### Adding a Minecraft version

1. If the existing jar's code still works, widen `mc_range` in its `versions/<name>/gradle.properties` and run `scripts/check_compat.py`.
2. Otherwise add a new `versions/<name>/` folder (copy a neighbour and update the versions from [fabricmc.net/develop](https://fabricmc.net/develop)), and if needed a `src/compat/<name>/` copy of `VersionCompat`.

## License

MIT
