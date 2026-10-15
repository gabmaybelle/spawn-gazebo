# Spawn Gazebo

A small Fabric mod for Minecraft 26.3 (mod ID `spawn_gazebo`): new worlds start in a round stone
gazebo with a copper dome, built once at the world spawn, with an optional starter chest and
protection round it. See README.md for what players get.

`PROGRESS.md` says what's done, what's broken and what's next, with a row per session: read
it first, and add this session's row before ending.

## Layout

- `src/main/java/com/gabbymaybelle/spawngazebo/`: `SpawnGazebo.java` (choosing the wood and
  palette, clearing the ground, placing the template, the chest, cooling nearby lava),
  `GazeboGuard.java` (the protected area), `Config.java`, `client/` (settings screen, Mod Menu)
  and `mixin/` (explosions, fire, pistons, buckets, block items).
- `src/gametest/`: the client game test (`SpawnGazeboClientTest`).
- `tools/`: `generate-spawn.js` writes the gazebo templates, `generate-data.js` the data pack,
  `render-gazebo.js` the icon and the README picture.

## Build and test

- `./gradlew build`: jar in `build/libs/`.
- `./gradlew runClientGameTest`: the settings file (typos, unknown settings, safe saving), the settings screen (title screen, in a world, greyed out on a
  server), saving, a new world's gazebo, and protection inside and just outside the radius.

## Gotchas

- **Espresso Flavoured Core bundles this mod** (github.com/gabmaybelle/espresso-flavoured,
  `libs/spawn-gazebo-<version>.jar`, jar-in-jar) and fills the chest with its own kit through
  fabric.mod.json `custom.spawn_gazebo.starter_kit` (see `Config.starterKitTable`). After a
  release here, copy the jar into Espresso's libs/ and bump the version there. Espresso's Seat
  sits players on the benches, which is why the guard lets wooden stairs through with a tool in hand.
- **The structure files are generated.** Edit `tools/generate-spawn.js` and re-run
  `node tools/generate-data.js`; don't edit the `.nbt` files.
- **Lootr:** with Lootr installed the chest becomes a Lootr chest. Lootr 1.25.42 converts it by
  itself; the `lootr force_chunk` command SpawnGazebo sends is kept as a safety net for Lootr
  versions that don't. `-PtestLootr` checks the result.
- The gazebo is built by the server when the world is created, so its log lines are in the
  server's log, not the client's.

## Gabby's mods: who does what (the same in every repo)

One job per mod, decided 2026-10-15, so nothing is done twice and each repo tells the same story:

- **Cozy Core** (cozy-core): the builder rules, each an ordinary game rule: builds stay built (mob
  explosions, endermen, fire), no phantoms, keep your hotbar on death, the Hearth Lantern, cheap
  wood in the stonecutter, re-dyeing and builder trades. Fabric and NeoForge jars, plus a plain
  data pack of the recipes.
- **Espresso Flavoured Core** (espresso-flavoured): the café: coffee you grow and brew, mugs,
  baristas, Bean Coins, the café charms, and Drowsy (coffee's reason to exist). Longer days stay a
  Core setting for now. The survival rules Cozy Core now covers left Core in 0.4.0 (its PLAN-0.4.0.md).
- **Spawn Gazebo** (spawn-gazebo): the gazebo at the world spawn and its starter chest.
- **Nightfall Clock** (nightfall-clock): the clock above the hotbar and the dusk warning.
- **Pocket List** (pocket-list): the gathering checklist.
- **Espresso Flavoured** (espresso-flavoured-pack): the modpack that puts them together. Since
  pack 0.5.0 (2026-10-15) it carries all five: Cozy Core, Espresso Flavoured Core (back as the café
  once it stopped doing Cozy Core's jobs), Spawn Gazebo, Nightfall Clock and Pocket List.

A new feature goes in the mod whose job it is. If none fits, ask Gabby before adding it anywhere.

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
to any other branch. (Learned on Beyond the Dragon, 2026-10-14, when scripted chains committed
a conflicted cherry-pick, put one branch's files on another, and sent an untested ore change to
all three branches.) For a Gradle game test use the toolkit's watcher (`~/.claude/CLAUDE.md`),
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
