# Progress

What's done, what's broken, what's next. Newest entry first. Each session adds a row before it
ends, saying what changed, what the game test counted, what the proof run was (the feature off,
which checks fail, code put back), what was learned, and what was deliberately not done.

## Where things stand

- **Done:** Spawn Gazebo 1.2.0 (Fabric and NeoForge; not released yet): a round stone gazebo at the world spawn, a protected radius, a starter chest whose kit another mod can supply, lava near it set to stone; client game test (settings, a new world's gazebo, protection inside and outside the radius, Lootr), CI on every push.
- **Broken:** Nothing known as of this writing (not re-verified).
- **Next:** Nothing planned beyond feedback. Espresso Flavoured Core bundles it (jar-in-jar) and the Espresso Flavoured pack carries it as its own jar.

## Log

| Date | Session | What happened |
| --- | --- | --- |
| 2026-10-16 | Release prep | Version 1.2.0 and its changelog (NeoForge, the Fabric file's new name). No code change. Not released. |
| 2026-10-16 | Lootr on NeoForge, old worlds | The NeoForge test gained the Lootr check Fabric had (`-PtestLootr`, Lootr 1.25.42.124 for NeoForge 26.3): with Lootr the starter chest becomes a Lootr chest and the kit isn't handed out on joining; passes. Proof run: with the gazebo ignoring Lootr the test failed ("the gazebo noted that Lootr is installed"); code put back. CI now runs the NeoForge test in both variants. A world made with the released 1.1.1 jar (43 gazebo blocks, saved record, protection checked by placing a block inside and 30 blocks outside) was reopened on the new Fabric jar: the record and block count are unchanged, no second gazebo was built, and protection still holds (inside refused, outside placed), because the Fabric attachment ids are unchanged. Test only; no player-facing change. |
| 2026-10-16 | NeoForge | Split into `common`, `fabric` and `neoforge`. Loader parts go through `Platform` and plain handlers in `SpawnGazebo`, `GazeboGuard` and `SpawnGazeboMod.init`; Fabric keeps its attachment ids so existing worlds keep their gazebo; NeoForge uses its own attachments, an optional payload and the data pack through `AddPackFindersEvent`. NeoForge's patched `FireBlock.checkBurnOut` takes an extra `Direction`, so `FireBlockMixin` is now one copy per loader (the NeoForge test caught the launch crash). Tests: the Fabric game test passes on its own and with Lootr; the new NeoForge in-game test passes (settings file, a new world's gazebo with its kit claimed and spawn radius 0, that the world remembers it and where, that the player's game is told the box, protection incl. the right-click event); the NeoForge dedicated server starts and builds the gazebo. Proof run: with the NeoForge right-click refusal switched off, the NeoForge test failed on the shovel and lawn checks; code put back, test passes again. Not tested: Lootr on NeoForge, a Fabric world made before this change reopened on the new Fabric jar, Espresso Flavoured Core on NeoForge. Not released. |
| 2026-10-15 | Map | The one-job-per-mod map (Cozy Core: builder rules; Espresso Flavoured Core: the café; Spawn Gazebo, Nightfall Clock, Pocket List: one thing each; the pack: the bundle) written into CLAUDE.md, the same in every repo. No code change. |
| 2026-10-15 | Notes | `PROGRESS.md` added, with the rows below reconstructed from the commit history (one row per day, the commits' own words); the two shared rules (commits one at a time by hand, proof runs for data changes) added to `CLAUDE.md`. No code change. |
| 2026-10-14 | (from history) | Every log line starts with the mod's name; 1.1.1: a bad chestLoot falls back to the pack's own kit, and the protection won't run half-applied. |
| 2026-10-13 | (from history) | 1.1.0: a mod can bring its own starter kit, and benches work with a tool in hand; Notes: why the Lootr command stays; Game test with Lootr: the starter chest becomes a Lootr chest; Safer settings: no half-written file, and a typo can't turn the protection off; CI runs the game test on every push; Notes for Claude: how this repo works and the rules every session follows; Game test: protection inside and just outside the radius; Client game test for the settings screen and a new world's gazebo; Set lava near the gazebo to stone, so nobody's first steps are into it; Spawn Gazebo 1.0.0: a round stone gazebo at the world spawn. |
| 2026-10-12 | (from history) | Leave no litter when the gazebo is built; Protected radius, a choice of chest loot, and an icon drawn with the game's textures. |
