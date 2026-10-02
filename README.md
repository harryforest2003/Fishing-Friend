# Fishing Friend

A client-side Fabric mod that listens to your fishing bobber for you.

- **Bite alert:** plays a ding the moment a fish bites (when the bobber dips), so you know to reel in.
- **Empty catch alert:** if you reel in on time but nothing comes out, it plays a second sound. Servers with overfishing rules (for example mcMMO's) delete your catch once a spot is fished out, so this tells you to move somewhere else.

Both sounds, their volume and pitch, and the on-screen messages can be changed in-game through [Mod Menu](https://modrinth.com/mod/modmenu).

## Downloads

Grab the jar for your Minecraft version from the [releases page](https://github.com/harryforest2003/Fishing-Friend/releases):

| Minecraft | Jar |
| --- | --- |
| 1.21 – 1.21.11 | `fishing-friend-<version>+mc1.21-1.21.11.jar` |
| 26.1 – 26.1.2 | `fishing-friend-<version>+mc26.1-26.1.2.jar` |
| 26.2 – 26.3 | `fishing-friend-<version>+mc26.2-26.3.jar` |

Requires [Fabric Loader](https://fabricmc.net/use/) and [Fabric API](https://modrinth.com/mod/fabric-api). Mod Menu is optional; it is only needed to change settings in-game.

## How it works

When a fish bites, the server marks your bobber as "biting" and sends that to your client. That flag is what makes the bobber dip, and it stays set for exactly the window in which reeling in catches the fish. Fishing Friend watches that flag on your own bobber and plays the bite sound when it turns on.

When you reel in while the flag is still set, the server spawns your catch at the bobber. If no item appears there (or in your inventory) within a second, the catch was taken away, so the empty catch sound plays. Reeling in too early or too late never triggers it.

It only reads what your client already receives, so it works on any server and sends nothing extra.

## Settings

With Mod Menu installed, open **Mods → Fishing Friend → configure**. Each alert can be turned off, given a different sound (with a **Test** button), and have its volume and pitch adjusted. Settings are saved to `config/fishingfriend.json`, where you can also set any sound id (e.g. `minecraft:entity.cat.ambient`) and how long to wait for a catch (`emptyCatchWaitTicks`, 20 ticks = 1 second).

## Building

Needs JDK 25 (Gradle runs on it and compiles the 1.21 jar for Java 21).

```bash
./gradlew build
```

The jars end up in `build/libs/`. The project builds one jar per folder in `versions/`, all from the shared code in `src/main`. The few calls that differ between Minecraft versions live in `src/compat/<version>/`.

Each jar is compiled against one Minecraft version but claims a range. Check that it links against every release in that range:

```bash
python3 scripts/check_compat.py
```

It downloads each Minecraft client (remapped to Fabric's intermediary names for 1.21.x) and confirms every class, method, field, override and mixin target the jar uses still exists. CI runs it on every push.

Unit tests for the bite and catch timing run as part of `./gradlew build`. There is also an in-game test that builds a pond, fishes for real and checks which sounds play (a Minecraft window opens while it runs):

```bash
./gradlew :26.2:runClientGameTest
```

### Adding a Minecraft version

1. If the existing jar's code still works, widen `mc_range` in its `versions/<name>/gradle.properties` and run `scripts/check_compat.py`.
2. Otherwise add a new `versions/<name>/` folder (copy a neighbour and update the versions from [fabricmc.net/develop](https://fabricmc.net/develop)), and if needed a `src/compat/<name>/` copy of `VersionCompat`.

## License

MIT
