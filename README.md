# 30 Seconds Ago

30 Seconds Ago is a lightweight Android replay-buffer app built for the AYN Thor dual-screen handheld. It keeps recent gameplay in memory and saves the latest replay as an MP4 clip.

## Current Features

- Replay dashboard designed for the Thor bottom screen
- Configurable replay length, quality presets, bitrate, and internal-audio toggle
- Save clips to the default Movies/ThorReplay folder or a selected folder
- In-app clips list for opening saved replays
- Optional saved-clip popup on a selected display
- Configurable hardware replay trigger through Android accessibility key events
- Custom filename templates

## Notes For Testers

Android screen recording requires MediaProjection permission each time the replay buffer starts. Internal audio recording depends on Android playback capture support, and some games or emulators may block it.

Hardware trigger support depends on whether Android exposes the selected button to apps. If one button does not work, choose another controller button in the Key tab.

## Build A Debug APK

From the project root:

```bash
./gradlew assembleDebug
```

The debug APK is created at:

```text
app/build/outputs/apk/debug/app-debug.apk
```

## Build A Signed Release APK

Use Android Studio:

1. Open this project.
2. Choose `Build` -> `Generate Signed App Bundle / APK`.
3. Choose `APK`.
4. Select or create a private keystore outside this repo.
5. Choose the `release` build variant.
6. Finish the wizard.

Do not commit keystore files or passwords. Share signed APKs through GitHub Releases instead of committing them to the repository.

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

## Credits

Made by Ryan Arthur Walker using AI. Ryan actually has no idea how to code.
