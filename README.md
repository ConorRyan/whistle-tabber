# Whistle Tabber

A free, offline Android app that shows tin whistle fingering diagrams for tunes
written in [ABC notation](https://abcnotation.com/), and plays them back.

Status: early development (see `docs/ROADMAP.md`). Planned for F-Droid.

## Building

Requires JDK 21 and Android SDK platform 37.

```bash
./gradlew assembleDebug
./gradlew :app:testDebugUnitTest
(cd web-tests && node --test)
```

## Licence

MIT (see `LICENSE`). Third-party components and their licences are listed in
`THIRD_PARTY_NOTICES.md`.
