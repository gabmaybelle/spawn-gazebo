# Changelog

## 1.3.3

- **The right wood even with no trees in sight.** When nothing grows near the spawn, the gazebo now takes the wood its biome is known for: spruce on snowy plains, ice spikes, frozen peaks and snowy beaches, cherry in a cherry grove, pale oak in a pale garden, mangrove in a mangrove swamp, bamboo in a bamboo jungle, birch in a birch forest and dark oak in a dark forest. Before, all of these got oak.

## 1.3.2

- **No gazebo on top of a gazebo.** `/spawngazebo build` now refuses to build where a gazebo already stands, the protected one or an older one, and says how far to move. Before, it put the new gazebo on the old one's roof and drove its stone foundation down through the old one.

## 1.3.1

- **Nobody gets built into the plinth.** When an operator builds a gazebo with `/spawngazebo build confirm`, anyone standing where it goes, usually the operator, is now moved up onto its floor at the new spawn, facing out of the entrance. Before, they were left inside the stone and could suffocate in survival. Players elsewhere stay where they are.

## 1.3.0

- **A gazebo in a world you already have.** An operator can stand where they want it and type `/spawngazebo build`. It says what it will do, and `/spawngazebo build confirm` within 30 seconds builds it there, in the local wood, with the starter chest if that setting is on. The world spawn and the protection move to the new gazebo; an older gazebo stays standing, unprotected. Players who join after that are each handed their own roll of the starter kit.

## 1.2.0

- Spawn Gazebo now comes as a NeoForge mod as well as a Fabric one, for Minecraft 26.3. It builds the same gazebo and keeps it the same way. On NeoForge, the settings are on the Mods screen's Config button. Worlds you already have on Fabric keep their gazebo.
- The Fabric file is now named `spawn-gazebo-fabric-1.2.0.jar`.

## 1.1.1

- A `chestLoot` that isn't a loot table id now falls back to the pack's own starter kit (Espresso
  Flavoured Core's, say), not Spawn Gazebo's.
- If another mod has changed the game code the protection hooks into, the game now stops with a
  clear message instead of running with the gazebo quietly unprotected.

## 1.1.0

- A mod can bring its own starter kit for the chest: Espresso Flavoured Core now includes Spawn
  Gazebo and fills the chest with its kit this way. Your own `chestLoot` setting still wins.
- The benches can be sat on with a tool in hand, where a mod lets you sit on stairs. Before, the
  protection stepped in first and nothing happened.

## 1.0.0

The first release. The gazebo began life as part of [Espresso Flavoured](https://github.com/gabmaybelle/espresso-flavoured) and is now a mod of its own.

- **A gazebo at spawn:** new worlds start in a little round stone gazebo: eight stripped-log pillars of whatever wood grows nearby on a round plinth, a ring of arches, a weathered copper dome with azaleas creeping over it and a lightning rod on top, lanterns under the eaves and flanking the steps, and a spore blossom over a glowstone floor inside. Deserts and badlands get sandstone and a browner dome.
- **A starter chest,** optional like the bonus chest, with a bed, torches, bread, saplings and a bundle, or the bonus chest's own loot, or any loot table you name. Later players are handed a roll of the same loot, or get their own chest with Lootr.
- **Protection:** outside creative it can't be broken or built on, and explosions, fire, pistons and buckets leave it alone. It reaches 12 blocks from the middle of the gazebo by default, so the ground around it is kept too; the radius is a setting. Switch off "Protect the Gazebo" to remodel it.
- Building it moves the world spawn to the middle of the gazebo and sets `spawn_radius` to 0.
- Building it leaves no litter: the parts of felled trees' canopies that hang outside the footprint are cleared with it rather than left to rot into sticks and saplings, and two-block flowers go without dropping.
- No lava on the doorstep: a lava pool at the surface within 16 blocks of the gazebo (or the protected radius, if that's wider) is set to stone when it's built, the whole pool, not just the part inside that distance.
- Settings are saved safely: the file is never left half-written if the game crashes while saving, a typo in a setting ("ture") keeps its default and says so in the log rather than switching it off, and notes you add or settings the mod doesn't know about are kept.
