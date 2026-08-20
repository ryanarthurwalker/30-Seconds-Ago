# Help, Compatibility, and Known Issues

This page collects the hardware compatibility notes, known issues, and settings that have worked best during testing.

## Supported Device

30 Seconds Ago is built specifically for the **AYN Thor** and its dual-screen layout. It has not been tested on other Android devices, so compatibility, performance, display selection, and controller hotkeys are not guaranteed elsewhere.

## Recommended Capture Settings

| Game type | Recommended starting point | Notes |
| --- | --- | --- |
| Dolphin Emulator | `720p60 Smooth` | Use `720p30 Standard` or a lower bitrate if a demanding game stutters or capture becomes unstable. |
| Native Android games | `1080p60 High` | Fortnite and Minecraft have worked well at this setting in testing so far. |

These are starting points rather than guarantees. Performance can vary by game, emulator settings, and what is happening on screen.

## Dolphin Emulator

Dolphin can already place a heavy load on the Thor. Running the replay buffer at the same time makes Dolphin and the screen recorder compete for GPU resources.

`720p60 Smooth` is the recommended starting point. Games with several active players, effects, or moving elements may still cause captured video to hiccup or the capture session to stop. Mario Party with four players is one known example.

If this happens:

1. Change the capture preset to `720p30 Standard`.
2. If necessary, lower the capture bitrate as well.
3. Restart the replay buffer before returning to the game.

## Native Android Games

Fortnite and Minecraft have run well at `1080p60 High` during testing, with no major capture issues encountered so far. Other games and future game updates may behave differently.

## Other Known Limitations

- Android screen capture permission is required each time the replay buffer is started.
- Some games, emulators, or protected content may prevent internal audio capture.
- Some hardware and system buttons are not exposed to Android apps and cannot be used as controller hotkeys.
- A saved replay may be slightly longer than the selected duration because recording begins at the nearest safe video keyframe.
- Very demanding games may require a lower resolution, frame rate, or bitrate even if a higher preset works elsewhere.

## Reporting a Problem

When reporting a bug, please include:

- The game or emulator and its version
- The capture resolution, frame rate, and bitrate
- Whether internal audio was enabled
- What was happening on screen when the problem occurred
- Whether the game, the saved clip, or the replay buffer itself was affected

Report issues on the [GitHub issue tracker](https://github.com/ryanarthurwalker/30-Seconds-Ago/issues).
