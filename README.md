# Galaxy

Galaxy (working title) is a multiplayer survival and exploration modpack for **Minecraft 26.3 on NeoForge**. A crew flies a shared ship through generated galaxies of small planets with their own gravity, gathering resources to survive and keep the ship moving.

## Repository layout

Every mod in the pack is its own Gradle project under `mods/`. They share one build setup in the root `build.gradle`.

| Folder | Mod id | What it does |
| --- | --- | --- |
| `mods/core` | `galaxy_core` | Shared code: ids, networking and save data used by the other mods |
| `mods/gravity` | `galaxy_gravity` | Planet gravity that pulls toward a planet's center |

Planned next: Galaxy Generation, Ship, Survival, Creatures.

## Requirements

- JDK 25 (Gradle downloads it automatically if missing)
- IntelliJ IDEA is recommended: open the repository folder and let Gradle sync

## Building

```
./gradlew build
```

Each mod's jar lands in `mods/<mod>/build/libs/`. GitHub Actions builds every mod on each push and pull request and uploads the jars as a `mod-jars` artifact.

Run one mod in a dev client or server:

```
./gradlew :gravity:runClient
./gradlew :gravity:runServer
```

## Testing in a CurseForge instance

1. In CurseForge, create a custom profile for Minecraft 26.3 with NeoForge 26.3.0.115 or newer.
2. Build, then copy the jar of the mod you are testing **and `galaxy_core`** into the profile's `mods` folder.
3. To test multiplayer, run `./gradlew :<mod>:runServer` and join `localhost` from the instance, or put the same jars on a dedicated server.

## Adding a new mod

1. Copy `mods/gravity` to `mods/<name>` and change `gradle.properties` (`mod_id`, `mod_name`, `mod_version`, `mod_description`).
2. Rename the Java package and main class, and update `MODID`.
3. Add it to `settings.gradle` (`include` plus `projectDir`).

## Multiplayer rules

- The server decides everything: gravity, generation, ship state. Clients display it and predict their own movement.
- Galaxies are generated on the server from a seed.
- Ship and crew data live in the world save, not on one player.
- A feature is done only once it works on a dedicated server with two players.

## Content

All art, names, sounds and music are original. Nothing is copied from Nintendo games.
