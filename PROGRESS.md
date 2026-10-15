# Progress

What's done, what's broken, what's next. Newest entry first. Each session adds a row before it
ends, saying what changed, what the game test counted, what the proof run was (the feature off,
which checks fail, code put back), what was learned, and what was deliberately not done.

## Where things stand

- **Done:** Spawn Gazebo 1.1.1: a round stone gazebo at the world spawn, a protected radius, a starter chest whose kit another mod can supply, lava near it set to stone; client game test (settings, a new world's gazebo, protection inside and outside the radius, Lootr), CI on every push.
- **Broken:** Nothing known as of this writing (not re-verified).
- **Next:** Nothing planned beyond feedback; Espresso Flavoured and Cozy Core bundle or carry it.

## Log

| Date | Session | What happened |
| --- | --- | --- |
| 2026-10-15 | Notes | `PROGRESS.md` added, with the rows below reconstructed from the commit history (one row per day, the commits' own words); the two shared rules (commits one at a time by hand, proof runs for data changes) added to `CLAUDE.md`. No code change. |
| 2026-10-14 | (from history) | Every log line starts with the mod's name; 1.1.1: a bad chestLoot falls back to the pack's own kit, and the protection won't run half-applied. |
| 2026-10-13 | (from history) | 1.1.0: a mod can bring its own starter kit, and benches work with a tool in hand; Notes: why the Lootr command stays; Game test with Lootr: the starter chest becomes a Lootr chest; Safer settings: no half-written file, and a typo can't turn the protection off; CI runs the game test on every push; Notes for Claude: how this repo works and the rules every session follows; Game test: protection inside and just outside the radius; Client game test for the settings screen and a new world's gazebo; Set lava near the gazebo to stone, so nobody's first steps are into it; Spawn Gazebo 1.0.0: a round stone gazebo at the world spawn. |
| 2026-10-12 | (from history) | Leave no litter when the gazebo is built; Protected radius, a choice of chest loot, and an icon drawn with the game's textures. |
