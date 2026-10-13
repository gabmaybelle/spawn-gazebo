# Spawn Gazebo

A small Fabric mod for Minecraft 26.3 (mod ID `spawn_gazebo`): new worlds start in a round stone
gazebo with a copper dome, built once at the world spawn, with an optional starter chest and
protection round it. See README.md for what players get.

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

- **The gazebo builder has a twin** in Espresso Flavoured Core
  (`content/SpawnGazebo.java`, github.com/gabmaybelle/espresso-flavoured). A fix to one usually
  belongs in the other too.
- **The structure files are generated.** Edit `tools/generate-spawn.js` and re-run
  `node tools/generate-data.js`; don't edit the `.nbt` files.
- **Lootr:** with Lootr installed the chest becomes a Lootr chest. Lootr 1.25.42 converts it by
  itself; the `lootr force_chunk` command SpawnGazebo sends is kept as a safety net for Lootr
  versions that don't. `-PtestLootr` checks the result.
- The gazebo is built by the server when the world is created, so its log lines are in the
  server's log, not the client's.

## Working on Gabby's mods (the same in every repo)

Gabby makes these mods with help from Claude. Several Claude sessions work on them: one on her
computer and others in the cloud. These rules keep their work fitting together.

**One session per repo at a time.**
- A cloud session works on a `claude/...` branch and never pushes to `main`. Gabby's local
  session reviews the branch, runs its tests and merges it as one commit.
- Start from the latest `main` (`git pull`), and say in the branch's last commit or a short note
  what is finished and what isn't.

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
