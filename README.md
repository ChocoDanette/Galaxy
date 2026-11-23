# Galaxy

Galaxy (working title) is a multiplayer survival and exploration modpack for **Minecraft 26.3 on NeoForge**. A crew flies a shared ship through generated galaxies of small planets with their own gravity, gathering resources to survive and keep the ship moving.

## Repository layout

Every mod in the pack is its own Gradle project under `mods/`. They share one build setup in the root `build.gradle`.

| Folder | Mod id | What it does |
| --- | --- | --- |
| `mods/core` | `galaxy_core` | Shared code: ids, networking and save data used by the other mods |
| `mods/gravity` | `galaxy_gravity` | Planet gravity that pulls toward a planet's center |
| `mods/worldgen` | `galaxy_worldgen` | Galaxy Generation: the galaxy dimension and its generated planets |

Planned next: Ship, Survival, Creatures.

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

## Trying planet gravity

Gravity has test commands until galaxy generation places planets by itself. In a world with cheats on:

```
/galaxy planet add ~ ~-20 ~ 15 45
```

This adds a planet centered 20 blocks below you, with a surface radius of 15 blocks, pulling anything within 45 blocks of its center. Build a ball of blocks there (radius 15) to stand on. An optional last number sets the strength (1 = normal gravity).

For a cube of blocks, give two opposite corner blocks instead, so the center is exact:

```
/galaxy planet cube <x1> <y1> <z1> <x2> <y2> <z2> 25
```

On a cube, each face walks like flat ground, and gravity only bends over the last 2 blocks before an edge.

Other commands: `/galaxy planet list`, `/galaxy planet remove <number>`, `/galaxy planet clear`.

Each planet's gravity center shows as a small glowing block (gold for a sphere, diamond for a cube). Its outline shows through blocks, so you can see the center inside the planet. Turn the markers off and on with `/galaxy planet centers hide` and `/galaxy planet centers show`.

What works so far (our own gravity, no other mods needed):

- Planets are saved with the world and sent to every player.
- Each player and mob has its own "up", which turns gradually (about half a second for a quarter turn) toward the planet's center, so the view tilts smoothly as you walk around a round planet.
- Walking, jumping, friction, aiming (breaking blocks, attacking) and the camera all follow that tilted "up". Other players and mobs are drawn tilted too.
- You can stand and walk on any side of a planet, including underneath.

Known limits: hitboxes must stay box-shaped along the world axes, so a player's hitbox lies along the nearest axis (for example sideways on a planet's east side). Auto step-up onto blocks and sneaking at edges only work on top of planets for now. There's no fall damage while tilted.

## Visiting the galaxy

Galaxy Generation adds a dimension of empty, starry space (`galaxy_worldgen:galaxy`). Planets in it are generated from rules, so every world and every galaxy is different:

- Space is split into 96 by 96 block cells. Each cell holds at most one planet, which always fits inside it, so planets never overlap. Planets spread up and down between heights -64 and 383.
- Each galaxy is a round area (1,300 to 2,500 blocks across) with its own personality: how crowded it is, which kinds of planets are common, whether planets tend to be small or big, and how many are cubes. Galaxy 1 is centered on x = 100,000, galaxy 2 on x = 200,000, and so on.
- A planet is a sphere (6 to 28 blocks radius, with gentle hills) or a cube (11 to 27 blocks wide, flat faces). Its kind decides its blocks: verdant, dune, frost, rocky, ember, mossy, canyon, fungal or crystal. Every planet has a surface layer, soil, rock, a deep layer and a core, with ores in clusters. Gold, redstone and lapis only appear deeper in, diamonds only in the core.
- Each planet gets its gravity automatically: the server adds it to the Gravity mod's planet list when a player comes within about 190 blocks, and players receive it like any other planet.

Commands (cheats or operator needed):

```
/galaxy go [number]   travel to a galaxy (1 if no number), landing on the planet in its middle
/galaxy info          the galaxy you're in and the planet nearest you
/galaxy leave         back to the overworld spawn
```

Known limits: there's no gravity between planets, so falling off into open space drops you into the void. Planets have no plants, caves or structures yet.

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
