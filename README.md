# 30 Seconds Ago

30 Seconds Ago is a lightweight Android replay-buffer app built for the AYN Thor dual-screen handheld. It keeps recent gameplay in memory and lets you save the last few seconds as an MP4 clip.

The app is designed around using the Thor bottom screen as a small replay dashboard while a game or emulator runs on the other screen.

## Features

- First-time setup flow for quality, save folder, popup permission, alert screen, and controller hotkey guidance
- Replay dashboard with large Capture Replay, Start, and Stop buttons
- Configurable replay length
- Video quality presets, including 720p30, 720p60, 1080p30, and 1080p60
- Adjustable bitrate
- Optional internal audio capture when Android and the running app allow it
- Saved clips library with thumbnails
- In-app MP4 playback
- Open or share clips from inside the app
- Default `Movies/ThorReplay` save location or custom folder selection
- Custom filename templates
- Optional saved-clip popup on a selected Thor display
- Configurable controller hotkey through Android accessibility key events

## Recommended First Setup

For Dolphin, start with `720p30 Standard`. Try `720p60 Smooth` if the game still runs well.

During setup, use `Allow Permission` and then `Test Alert` in the Saved Clip Alert section. Android may require popup permission before the saved-clip alert can appear on the selected screen.

The controller hotkey can be configured later. This is recommended because different emulators and games may already use different controller buttons. The bottom-screen dashboard always has a pressable `Capture Replay` button.

## How To Use

1. Open 30 Seconds Ago.
2. Complete the first-time setup.
3. Tap `Start` before playing.
4. Leave the app open on the Thor bottom screen.
5. Tap `Capture Replay` when something happens.
6. Open the `Clips` tab to play, open, or share saved clips.

## Saved Clips

By default, clips are saved to:

```text
Movies/ThorReplay
```

You can choose a different save folder from setup or Settings.

The Clips tab shows saved MP4 files with thumbnails. Tap `Play` to expand a clip and watch it inside the app. Tap `Share` to send the clip to another Android app.

## Filename Templates

The app supports these filename tokens:

```text
{datetime}
{date}
{time}
{duration}
{resolution}
{fps}
```

Example:

```text
Thor_{date}_{time}_{resolution}_{fps}
```

## Tester Notes

This is an early test build.

Please test:

- Whether replay clips save correctly
- Whether clips are close to the selected replay length
- Whether the end of the replay is preserved when pressing Capture Replay
- Whether internal audio works in your games or emulators
- Whether clips play inside the app
- Whether sharing works with apps like Discord, Drive, Gmail, or YouTube
- Whether the saved-clip alert appears on the selected Thor screen
- Whether your chosen controller hotkey works reliably

## Known Limitations

- Android screen capture permission is required when starting the replay buffer.
- Some games, emulators, or protected content may block internal audio capture.
- Some hardware or system buttons may not be visible to Android apps.
- The replay may be slightly over the selected length because the app saves from the nearest safe video keyframe.
- High-resolution or high-frame-rate capture can affect emulator performance. If a game becomes unstable, use 720p30 or lower bitrate settings.

## Build A Debug APK

From the project root:

```bash
./gradlew assembleDebug
```

The debug APK is created at:

```text
app/build/outputs/apk/debug/app-debug.apk
```

## Build A Release APK

Create or configure a signing key in Android Studio, then build a release APK from Android Studio or Gradle.

The Android package name is:

```text
com.thirtysecondsago.thorreplay
```

## Credits

Made by Ryan Arthur Walker using AI. Ryan actually has no idea how to code.
