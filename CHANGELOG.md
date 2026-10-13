# Changelog

## 1.0.0

The first release. The gazebo began life as part of [Espresso Flavoured](https://github.com/gabmaybelle/espresso-flavoured) and is now a mod of its own.

- **A gazebo at spawn:** new worlds start in a little round stone gazebo: eight stripped-log pillars of whatever wood grows nearby on a round plinth, a ring of arches, a weathered copper dome with azaleas creeping over it and a lightning rod on top, lanterns under the eaves and flanking the steps, and a spore blossom over a glowstone floor inside. Deserts and badlands get sandstone and a browner dome.
- **A starter chest,** optional like the bonus chest, with a bed, torches, bread, saplings and a bundle, or the bonus chest's own loot, or any loot table you name. Later players are handed a roll of the same loot, or get their own chest with Lootr.
- **Protection:** outside creative it can't be broken or built on, and explosions, fire, pistons and buckets leave it alone. It reaches 12 blocks from the middle of the gazebo by default, so the ground around it is kept too; the radius is a setting. Switch off "Protect the Gazebo" to remodel it.
- Building it moves the world spawn to the middle of the gazebo and sets `spawn_radius` to 0.
- Building it leaves no litter: the parts of felled trees' canopies that hang outside the footprint are cleared with it rather than left to rot into sticks and saplings, and two-block flowers go without dropping.
- No lava on the doorstep: a lava pool at the surface within 16 blocks of the gazebo (or the protected radius, if that's wider) is set to stone when it's built, the whole pool, not just the part inside that distance.
- Settings are saved safely: the file is never left half-written if the game crashes while saving, a typo in a setting ("ture") keeps its default and says so in the log rather than switching it off, and notes you add or settings the mod doesn't know about are kept.
