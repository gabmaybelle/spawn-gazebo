# The listing video and the YouTube Short

Makes `spawn-gazebo.mp4` (1920x1080, with the game's own sounds), `spawn-gazebo-silent.mp4` and
`spawn-gazebo-thumbnail.png` (1280x720), and the vertical Short: `spawn-gazebo-short.mp4`,
`spawn-gazebo-short-silent.mp4` and `spawn-gazebo-short-thumbnail.png` (1080x1920). Needs ffmpeg and
Python 3 with Pillow.

1. Film it: `./gradlew :fabric:runClientGameTest -PvideoTour`. `VideoTour` makes new worlds until one
   starts among oaks on plains or in a forest (`-PvideoSeed=<seed>` tries that seed first), then builds
   gazebos in a cherry grove, a birch forest and a desert the way `/spawngazebo build` does, hands
   the protection back to the world's own gazebo, and films every scene: the Short's tall
   (`s_...`), the video's wide (`w_...`). `-PvideoScenes=s_creeper,w_creeper` films only those
   scenes, to redo a few. Frames, `actions.tsv` and `frames.tsv` go in
   `fabric/build/run/clientGameTest/video_tour`; the next game-test run clears that folder, so copy
   it somewhere first. Building in far-off biomes keeps the window busy for a while, so a watcher
   may report it as not responding during the set-up. On Linux without a screen, run it under
   `xvfb-run -a -s "-screen 0 1920x1920x24"` with `SDL_VIDEO_FORCE_EGL=1`.
2. `python tools/video/get_sounds.py <sounds dir>`: the sounds, from the game's asset cache.
3. `python tools/video/assemble.py short <video_tour folder> <sounds dir> <out dir>`, and the same with
   `wide` for the video.

Keep the frames, sounds and videos out of the repo. If a scene changes, change its caption in
`assemble.py` too.
