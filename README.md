# ok-ww Android

An experimental Android companion for automating selected *Wuthering Waves* tasks. It uses Android screen capture, on-device text/image recognition, and the Android Accessibility service; it does not read or modify the game's files.

This is an in-development source snapshot, not a stable release. Supported workflows and character combat routines are limited, and unit tests do not establish successful end-to-end behavior on every device. No APK is checked into this repository.

## Build

- JDK 17
- Android SDK platform 35
- Gradle 8.11.1

Import this directory into Android Studio, or install the JDK, SDK, and Gradle in the project-local `.android-tools/` layout and run:

```powershell
.\build.ps1
.\build.ps1 -TestOnly
```

The build script writes generated files and APKs under ignored local build/output directories. `-Fixtures` also packages the Android instrumentation test APK.

## Privacy and permissions

- Screen capture and recognition run on the device. The application does not upload captured game frames or account identifiers.
- Account plans and run records are stored in app-private storage. No personal account IDs or phone numbers are bundled as defaults.
- Account-specific defaults from the development checkout were removed. Single-account daily uses the first Tacet Suppression entry; configure an account plan in the multi-account task if another entry is needed.
- The app requests MediaProjection and Accessibility permissions because those are required for screen capture and UI input. Tasks run only after the user starts them.
- The optional public redemption-code feed is fetched from the URL declared in `RedeemFeed.java` when requested by the user. There is no analytics or telemetry endpoint in this source tree.

## Important

This project is unofficial and is not affiliated with Kuro Games. Automated interaction may conflict with the game's terms or enforcement rules and may put an account at risk. Check the applicable terms before use.

Original source code is licensed under [AGPL-3.0](LICENSE.txt). Game-derived templates and screenshots have separate rights; see [THIRD_PARTY_NOTICES.md](THIRD_PARTY_NOTICES.md).
