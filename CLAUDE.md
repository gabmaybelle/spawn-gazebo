# Spawn Gazebo

A small Fabric and NeoForge mod for Minecraft 26.3 (mod ID `spawn_gazebo`): new worlds start in a round stone
gazebo with a copper dome, built once at the world spawn, with an optional starter chest and
protection round it. See README.md for what players get.

`PROGRESS.md` says what's done, what's broken and what's next, with a row per session: read
it first, and add this session's row before ending.

## Layout

- `common/src/main/java/com/gabbymaybelle/spawngazebo/`: `SpawnGazebo.java` (choosing the wood and
  palette, clearing the ground, placing the template, the chest, cooling nearby lava),
  `GazeboGuard.java` (the protected area), `Config.java`, `client/` (settings screen) and `mixin/`
  (explosions, pistons, buckets, block items). `Platform` is what the loader answers (config folder,
  installed mods, other mods' starter kits, what the world remembers, sending the gazebo's box to a
  player); `SpawnGazeboMod.init()` and plain handlers in `SpawnGazebo` and `GazeboGuard` are what each
  loader's events call. The built-in data pack is `resourcepacks/spawn_gazebo_data`.
- `fabric/`: `SpawnGazeboFabric` (Fabric API events, attachments, networking, built-in pack),
  `SpawnGazeboFabricClient`, `ModMenuIntegration`, `fabric.mod.json`, its own `FireBlockMixin` and
  `spawn_gazebo.fire.mixins.json`, and `src/gametest/` (the client game test, `SpawnGazeboClientTest`).
- `neoforge/`: `SpawnGazeboNeoForge` (events, attachments, an optional payload, the data pack through
  `AddPackFindersEvent`), `SpawnGazeboNeoForgeClient` (the Mods screen's Config button), its own
  `FireBlockMixin` and `spawn_gazebo.fire.mixins.json`, `neoforge.mods.toml`, and `src/clienttest/` (the
  NeoForge in-game test; part of the mod only in dev runs, never in the jar).
- `paper/`: the Paper server plugin (`SpawnGazeboPlugin`: settings, the new-world check, the kit on a
  player's first join; `GazeboBuilder`: the same build as `SpawnGazebo.java`, written against the
  Paper API). It never uses `common`: a plugin sees only the Paper API, not Minecraft's classes. Its
  build copies the structure files out of `common` into the jar as `gazebo/<name>.nbt`. The world
  remembers the gazebo in its own persistent data (`spawngazebo:checked`, `box`, `table`, `claimed`).
- `tools/`: `generate-spawn.js` writes the gazebo templates, `generate-data.js` the data pack,
  `render-gazebo.js` the icon and the README picture, `paper-server-test.js` the Paper plugin's test.

## Build and test

- `./gradlew build`: jars in `fabric/build/libs/` and `neoforge/build/libs/`.
- `./gradlew :neoforge:runClientTest` (NeoForge): the settings file, a new world's gazebo (kit claimed, spawn radius 0), that the world remembers it and where, that the player's game is told the box, and protection incl. the right-click refusal. A failed check crashes the game with a report in `neoforge/build/run/clientTest/crash-reports/`; a watchdog stops a hang after 10 minutes.
- `./gradlew :fabric:runClientGameTest` (Fabric): the settings file (typos, unknown settings, safe saving), the settings screen (title screen, in a world, greyed out on a
  server), saving, a new world's gazebo, and protection inside and just outside the radius.
- `node tools/paper-server-test.js <folder> --plugin paper/build/libs/spawn-gazebo-paper-<v>.jar --eula` (Paper): downloads a Paper server into the folder once, starts it on a brand-new world with no window, and checks the plugin built the gazebo at the spawn (floor, spore blossom, lightning rod, the chest and its kit), that TNT, fire and a piston leave it alone (a `clone` of the gazebo compared with `execute if blocks`, random ticks off so the glow berries don't grow and snow doesn't settle meanwhile) while TNT still works outside the radius, and `/spawngazebo build` (a second gazebo, the refusal on top of one, confirm with nothing to confirm), with no warnings. `--keep-world` checks an existing world is left alone; `--no-protection` is the proof run (the protection checks must fail). `--eula` is your acceptance of Minecraft's server EULA. The console is saved as `test-console.log` in the folder.
- `-PtestLootr` works on both tasks (`:fabric:runClientGameTest`, `:neoforge:runClientTest`): Lootr is installed (NeoForge: Modrinth id `PdFGq3vT`, Fabric `gVwn7m2v`) and the test checks the starter chest became a Lootr chest and the kit isn't handed out on joining. CI runs both with and without it.

## Gotchas

- **Another mod can bundle this one** (jar-in-jar) and fill the chest with its own kit through
  fabric.mod.json `custom.spawn_gazebo.starter_kit` (see `Config.starterKitTable`; on NeoForge a mod says it in neoforge.mods.toml `[modproperties.<its id>]` as `"spawn_gazebo:starter_kit" = "<loot table id>"`).
  The guard lets wooden stairs through with a tool in hand, so a seat mod can sit players on the benches.
- **Fabric worlds keep their gazebo.** Fabric stores whether a world was checked and where the gazebo is as attachments `spawn_gazebo` and `spawn_gazebo_box`; they must keep those ids, or an existing world would be given a second gazebo or lose its protection.
- **One mixin differs by loader.** NeoForge's patched `FireBlock.checkBurnOut` takes an extra `Direction`, so `FireBlockMixin` has a copy in `fabric/` and one in `neoforge/` (each with its own mixin config). The game crashes on launch if a mixin doesn't match, and the NeoForge test is what caught this. Check the others when a Minecraft version changes.
- **The structure files are generated.** Edit `tools/generate-spawn.js` and re-run
  `node tools/generate-data.js`; don't edit the `.nbt` files.
- **Lootr:** with Lootr installed the chest becomes a Lootr chest. Lootr 1.25.42 converts it by
  itself; the `lootr force_chunk` command SpawnGazebo sends is kept as a safety net for Lootr
  versions that don't. `-PtestLootr` checks the result.
- The gazebo is built by the server when the world is created, so its log lines are in the
  server's log, not the client's.

## This mod's job

**Spawn Gazebo**: the gazebo at the world spawn and its starter chest.

Gabby has other mods, each with its own job. Add only features that fit this mod's job. If an
idea doesn't fit, don't add it here or start it in another repo: ask Gabby where it goes.

## Working on Gabby's mods (the same in every repo)

Gabby makes these mods with help from Claude. Several Claude sessions work on them: one on her
computer and others in the cloud. These rules keep their work fitting together.

**One session per repo at a time.**
- A cloud session works on a `claude/...` branch and never pushes to `main`. Gabby's local
  session reviews the branch, runs its tests and merges it as one commit.
- Start from the latest `main` (`git pull`), and say in the branch's last commit or a short note
  what is finished and what isn't.

**Commits are made one at a time, by hand.** Never commit or push from a chained script. Run
the test, read its result (and the screenshot, for anything visual), then commit that one
branch, then push it. A cherry-pick that conflicts stops the work until it is resolved by hand.
A worldgen or balance change is tested on every terrain in the branch's matrix before it goes
to any other branch. (Learned on another mod, 2026-10-14, when scripted chains committed
a conflicted cherry-pick, put one branch's files on another, and sent an untested ore change to
all three branches.) For a Gradle game test use a game watcher that reports a verdict (in the world, error screen, crashed or hung),
not a wait loop.

**Commits.**
- Author and committer are always `gabmaybelle <211247938+gabmaybelle@users.noreply.github.com>`:
  `git -c user.name=gabmaybelle -c user.email=211247938+gabmaybelle@users.noreply.github.com commit ...`.
  Never a personal email, and never `Claude <noreply@anthropic.com>` as the author. Claude goes in
  a `Co-Authored-By:` line at the end of the message.
- Ask Gabby before committing or pushing when she's around. Finish the build and the tests first.
- Commit messages say what changed for players, in plain sentences.

**Privacy.** These repos are public. Don't add personal email addresses, real names other than
"Gabby", or anything about her other private projects and modpacks: not in code, docs, commits
or test names. Check the diff for these before every push.

**Tests.**
- `./gradlew build` and `./gradlew runClientGameTest` (where the repo has one) must pass before
  a commit. The game test opens a real game window for a minute or two.
- A new or changed check should fail with the feature switched off: run it that way once, then
  put the code back. A check that compares against the setting it tests proves nothing.
- A data change (worldgen, loot, a template, a pack's mod list) gets the same proof: run the part
  of the test that sees it, and quote the numbers in the progress row.
- On Linux without a screen, run game tests under `xvfb-run -a`. 26.3 also needs
  `SDL_VIDEO_FORCE_EGL=1` with `libegl1 libegl-mesa0` installed, or the game hangs at startup.

**Writing.**
- README and CHANGELOG are for players: plain, friendly, no jargon. New player-facing changes
  go in the CHANGELOG's newest section.
- Keep the credit line: "made by Gabby with help from Claude, an AI assistant".
- Gabby publishes on Modrinth herself. Don't upload anything there.

**Minecraft.** Fabric on Minecraft 26.3 (Mojang names; Loom 1.18.4, Java 25). Vanilla names
changed a lot in 26.x: check the merged jar rather than older docs (for example `iron_chain`,
`pink_petals[flower_amount=N]`, timelines instead of `time query daytime`).
