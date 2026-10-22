# Spawn Gazebo

Every new world starts in a little round stone gazebo instead of a random patch of grass. It's built from the wood that grows nearest your spawn, with a starter chest, benches and lanterns, and survival players and creepers can't damage it.

A Fabric and NeoForge mod for Minecraft 26.3 and 1.21.1. Download it from [CurseForge](https://www.curseforge.com/minecraft/mc-mods/spawn-gazebo), or [watch the tour](https://youtu.be/RPLnT72llCc).

This is the version for **Minecraft 1.21.1**, on Fabric and NeoForge. It builds the same gazebo and keeps it the same way; the few small differences are under [On 1.21.1](#on-1211).

![The gazebo in stone and oak, stone and spruce, and sandstone and jungle](docs/gazebo.png)

## What you get

- **A round gazebo at the world spawn**, built once when a world is created. A round stepped plinth, eight pillars of stripped logs on stone bases, arched openings on four sides, a carved stone band, and a dome of weathered copper with a lightning rod on top. Inside, a coffered ceiling rises to a spore blossom over a glowstone floor, with two benches, flower pots holding saplings and a cactus, and four decorated pots hiding mangrove propagules, bamboo and seeds (break them to get what's inside).
- **Built from what's around it.** The pillars and benches are the wood that grows nearest the spawn: oak, spruce, birch, jungle, acacia, dark oak, mangrove, cherry, pale oak, bamboo or poplar (on 1.21.1, which has no pale oak or poplar, one of the others). With no trees in sight, it's the wood the biome is known for (spruce on snowy plains, cherry in a cherry grove). In deserts and badlands the stone is sandstone and the dome a browner copper; everywhere else it's grey stone with moss creeping in and azaleas growing over the dome.
- **A starter chest**, in the railing opposite the entrance, like vanilla's bonus chest: a bed, sixteen torches, six bread, four saplings and a bundle (no bundle on 1.21.1). Or, if you'd rather, the bonus chest's own loot (a few tools, wood, food and seeds, different every time), or any loot table you name. The first player takes the chest; everyone who joins after them is handed a roll of the same loot. With [Lootr](https://modrinth.com/mod/lootr) installed, each player gets their own copy of the chest instead.
- **Lanterns** hang under the eaves and stand either side of the steps, so the spawn is lit at night.
- **Protection.** Outside creative the gazebo can't be broken or built on, and explosions, fire, pistons and buckets leave it alone. The chest, benches and pots still work, and the decorated pots can still be broken for their contents. The protection reaches 12 blocks from the middle of the gazebo (the gazebo itself reaches 7), so the lawn around it stays as it was; the radius is a setting.

Building it also moves the world spawn to the middle of the gazebo and sets the `spawn_radius` game rule to 0, so everyone starts inside it. Any lava pool at the surface within 16 blocks (or the protected radius, if that's wider) is set to stone, so nobody's first steps outside are into lava.

## Settings

In `config/spawn_gazebo.properties`, or in game through [Mod Menu](https://modrinth.com/mod/modmenu) on Fabric, or the Mods screen's Config button on NeoForge (Spawn Gazebo > Config):

| Setting | Default | What it does |
|---|---|---|
| `spawnGazebo` | on | Build the gazebo in new worlds. Only matters when a world is created (`/spawngazebo build` works either way). |
| `starterChest` | on | Put the starter chest in the gazebo. Off, the railing runs across its spot and no kit is handed out. Only matters when a world is created. |
| `chestLoot` | `starter_kit` | What the chest holds: `starter_kit`, `bonus_chest` (vanilla's bonus chest loot), or any loot table id, such as `minecraft:chests/village/village_plains_house`. Only matters when a world is created. A mod that includes Spawn Gazebo can bring its own starter kit; `starter_kit` then means that one. |
| `protectGazebo` | on | Keep the gazebo from being changed outside creative. Switch off to remodel it. |
| `protectedRadius` | 12 | How far from the middle of the gazebo the protection reaches, in blocks: 7 is the gazebo itself, up to 64. |

On a server, the server's settings file decides; players' own settings are greyed out there.

## Worlds

Brand-new worlds get the gazebo by themselves. Worlds from before the mod keep their spawn as it is, until an operator asks for one:

- Stand where you want the gazebo and type `/spawngazebo build`. It tells you what will happen: trees and blocks in a 15 by 15 area are cleared and built over, and the world spawn and the protection move to the new gazebo.
- Type `/spawngazebo build confirm` within 30 seconds to build it. It's made of whatever wood grows nearby (sandstone in deserts), with the starter chest if that setting is on. Anyone standing where it goes is moved up onto its floor. Players who join after that are each handed their own roll of the starter kit.
- It won't build where a gazebo already stands; it says how far to move instead.
- A world has one protected gazebo. Building another elsewhere leaves the old one standing as ordinary blocks. Removing the mod leaves the gazebo standing as ordinary blocks.

## On 1.21.1

- The pillars and benches come in the woods 1.21.1 has: oak, spruce, birch, jungle, acacia, dark oak, mangrove, cherry or bamboo. Pale oak and poplar came later.
- The starter kit has no bundle, since bundles are still an experiment in 1.21.1: a bed, sixteen torches, six bread and four saplings.
- In deserts and badlands the planting round the gazebo is all dead bushes (there are no cactus flowers in 1.21.1).

## Requirements

This version: Minecraft 1.21.1 and Java 21, with either Fabric (Fabric Loader 0.16 or newer and Fabric API; Mod Menu is optional) or NeoForge 21.1 (nothing else needed). Lootr is optional on both. Pick the `fabric` or `neoforge` file for your loader. For Minecraft 26.3, see the main page.

## Building

```
./gradlew build
```

This is the `1.21.1` branch; `main` is Minecraft 26.x. The jars land in `fabric/build/libs/` and `neoforge/build/libs/`. The structure files and the data pack are generated by `node tools/generate-data.js`; edit `tools/generate-spawn.js` to change the gazebo and re-run it. The icon and the picture above are drawn from those structure files with the game's own block textures by `node tools/render-gazebo.js` (add `--docs` for the picture); it reads the textures from the Minecraft client jar.

`./gradlew :fabric:runClientGameTest` starts the game and checks the settings file (typos, unknown settings, safe saving), the settings screen from the title screen, in a world and on a server (where it's greyed out; give a running dedicated server with `-PtestServer=localhost:25565`, otherwise that part is skipped), that changes are saved, that a new world gets its gazebo, and that a survival player can't break, build or dig a path inside the protected radius but can just outside it. Fabric's client game test API doesn't exist for 1.21.1, so the test brings a small harness of its own (`fabric/src/gametest/.../harness`). Screenshots land in `fabric/build/run/clientGameTest/screenshots`. `./gradlew :neoforge:runClientTest` runs the same kind of checks on the NeoForge jar (the settings file, a new world's gazebo, that the world remembers it, and the protection).

## Licence

MIT. See [LICENSE](LICENSE).
