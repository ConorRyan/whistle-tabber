# Whistle Tabber

A free, offline Android app that shows tin whistle fingering diagrams for tunes
written in [ABC notation](https://abcnotation.com/), and plays them back.

Status: early development (see `docs/ROADMAP.md`). Planned for F-Droid.

## Building

Requires JDK 21, Android SDK platform 37, NDK 29.0.14206865 and CMake 3.22.1
(Gradle downloads the NDK and CMake into the SDK on the first build).

```bash
./gradlew assembleDebug
./gradlew :app:testDebugUnitTest
(cd web-tests && node --test)
```

## Licence

MIT (see `LICENSE`). Third-party components and their licences are listed in
`THIRD_PARTY_NOTICES.md`.
