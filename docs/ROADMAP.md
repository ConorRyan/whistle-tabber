# Whistle tab app: v1 plan (no AI features)

## Context
The goal is a FOSS Android app for F-Droid that shows tin whistle fingering diagrams for ABC tunes and plays them back. Doing it all natively would mean writing an ABC parser, a sheet-music renderer and a synth. Instead, the app uses a **hybrid** design:
- **Native Kotlin/Compose shell** (same stack and conventions as `~/weeklies` and `~/timer-widget`): library, storage, settings, fingering logic, importers.
- **Bundled abcjs 6.7.2 (MIT) in a local WebView**: parsing, staff rendering, playback, transposition.

Decisions so far:
- Licence: MIT.
- Store languages: English and French.
- v1 scope: core features, the text-tab importer, a native tab-only view and MusicXML import.
- App name: **Whistle Tabber**, id `io.github.conorryan.whistletabber` (permanent once published).

Hard rules:
- No AI features or network calls in the app.
- No TheSession data anywhere: not bundled, not used as fixtures, never handed to the assistant.
- Starter tunes come from O'Neill's 1850 (GPL-2+ data asset, credited in THIRD_PARTY_NOTICES).
- Instrumented tests run **only on the emulator** (AVD `Medium_Phone_API_37.0`), never on the connected phone. See Verification.

Facts checked in the abcjs 6.7.2 source:
- Upstream abcjs has string tablature only, no whistle tabs. We draw the diagrams ourselves.
- The synth loads `<soundFontUrl><instrument>-mp3/<Note>.mp3` through XHR and decodes it with `decodeAudioData`. We can serve our own samples from a local origin.
- It exports `TimingCallbacks`, `strTranspose`, `getMidiFile`, and `parseOnly` (which works in node).

## Architecture (single `:app` module, like weeklies)
```
assets/web/  index.html, abcjs-basic.js (vendored, unminified), whistle.js (our glue + diagram drawing)
     ▲ WebViewAssetLoader  https://appassets.androidplatform.net/
     │   /assets/...            → bundled files
     │   /sound/whistle-mp3/X.mp3 → WAV bytes generated in Kotlin (no soundfont asset/licence)
     │ WebMessageListener (androidx.webkit, origin-restricted), JSON via kotlinx.serialization
web/ TuneWebView + WebBridge (Kotlin side of the protocol: render, play, pause, seek, loop, tempo, transpose,
     fingeringTable ⇄ events: rendered(noteEvents), playing(noteIndex), error)
music/  pure Kotlin: Fingering, FingeringEngine (whistle tonic + C-nat pref + profile → offset→holes table),
        InstrumentProfile (D whistle, low whistle, flute), Transpose modes, WhistleSynth (WAV),
        AbcBook (split multi-tune files, read X/T/R/M/K headers, write Eskin %whistle_tab_key directives),
        TextTabParser (letters / hole-counts / ●○◐ → ABC), MusicXmlImport
data/   Room (TuneEntity, TuneDao, AppDatabase), TuneRepository, SettingsStore (DataStore), TuneImporter
ui/     library/, tune/ (staff view + playback bar + tab-only view), editor/, import/, settings/, support/, theme/
App     manual AppGraph DI in Application class (weeklies `WeekliesApplication.kt` pattern)
```
Key design points:
- **ABC text is the canonical record.** Room stores the text plus an index of title, R, K and M.
- **Fingering stays in Kotlin.** Kotlin computes a lookup table (semitone offset → hole states, octave mark, hard flag) and sends it to JS. `whistle.js` only looks up `table[midi - tonic]` and draws SVG diagrams under each note, positioned from abcjs's `abselem` elements. Extra space under each staff comes from `%%staffsep`.
  - Fallback if positioning proves fragile: show letter names under the notes via `w:` lyrics, and treat the native tab view as the main diagram view.
- **Native tab-only view.** JS sends `noteEvents` (midi, beats, bar, startMs, tie/grace flags) after rendering. A Compose grid draws large diagrams by bar, highlighted from the `playing` events. The WebView stays alive, hidden, so playback still works.
- **Transposition has three modes:**
  1. Change the whistle and keep the same fingerings: playback and staff move via abcjs `midiTranspose`/`visualTranspose`.
  2. Change the whistle and keep the same pitches: rebuild the fingering table against the new tonic.
  3. Rewrite the tune into another key with `strTranspose`, saved as a new ABC version.
- **C natural** is a per-whistle-profile preference: OXX OOO, ½OO OOO, OXX OXX, OXX XOX, OXX XXX or OXX OOX.
- Write the fingering table ourselves from the facts. Do not copy GPL-2-only code (cgrassin).

## Sessions (each ends green on `./gradlew :app:testDebugUnitTest` + emulator tests)

**S1 — Scaffold and the riskiest spike** ✅ done 2026-10-25
- Pick the name and id.
- Set up the repo: `git init`, Gradle files copied from weeklies (`gradle/libs.versions.toml`, `settings.gradle.kts`, `gradle.properties`, `.gitignore`, wrapper 9.4.1, AGP 9.2.1, Kotlin 2.4.21, compileSdk 37, minSdk 26, JDK 21).
  - Drop okhttp and jsoup.
  - Add `androidx.webkit`, plus androidTest dependencies (compose-ui-test, espresso-web or `evaluateJavascript` helpers).
  - Set `dependenciesInfo { includeInApk = false; includeInBundle = false }`.
- Write LICENSE (MIT), THIRD_PARTY_NOTICES.md and AGENTS.md (weeklies format, with an architecture section).
- Add `scripts/update-abcjs.sh`: fetch the tagged tarball, check its sha256, copy the unminified `dist/abcjs-basic.js` and its LICENSE.
- Build `WebViewAssetLoader` + `WebBridge` + `WhistleSynth`. A debug screen renders a hard-coded O'Neill's tune and plays it offline.
- Tests:
  - Unit: WAV header, length and pitch (zero-crossing check); bridge message codec round-trip; asset handler routing (Robolectric).
  - Node (`node --test`, in `web-tests/`): note extraction from `abcjs.parseOnly`.
  - Emulator: the tune renders an SVG, and play makes `TimingCallbacks` fire.
  - Also confirm on the emulator that WAV bytes served under an `.mp3` name decode. If they don't, adjust the URL or MIME type.

**S2 — Fingering engine and diagrams** ✅ done 2026-10-25
- `Fingering`, `FingeringEngine` and `InstrumentProfile`.
- Settings: default whistle key, C-natural choice, fold out-of-range notes by an octave.
- `whistle.js` diagram drawing and the two "change whistle" transpose modes (rewriting the tune key moved to S3).
- Tests:
  - Table-driven unit tests: every chromatic note across 2.5 octaves for D, C and low-D whistles, each C-natural option, half-holes, a "hard" flag on the third octave, folding.
  - Node tests for diagram layout.
  - Emulator: diagram count equals note count (excluding rests and grace notes) on a fixture tune.

**S3 — Library, editor, storage** (detailed checklist: `docs/TODO-S3.md`) ✅ done 2026-10-25
- Room tune store, `TuneRepository`, `SettingsStore`, and `AppGraph` (built like weeklies').
- Library list with search and a rhythm filter.
- Tune screen.
- ABC editor with a debounced live preview and abcjs parse warnings shown inline.
- SAF import of multi-tune `.abc` files; export of one tune or the whole library (this doubles as backup).
- Starter set: a curated ~50-tune subset of O'Neill's in `assets/tunes/oneills.abc`, offered on first run.
- Tests: `AbcBook` split/headers (fixtures in `src/test/resources/abc/`), repository with in-memory Room, ViewModels (Turbine), Room schema export plus a migration-test harness for later versions.

**S4 — Playback UX and the native tab view**
- Playback bar: play/pause, tempo 40–150%, A–B loop (via TimingCallbacks + seek), tap a bar to start from it, highlight of the current note in both views, screen kept on while playing.
- Compose tab-only view with large diagrams per bar.
- Tests: ViewModel state machine for playback/loop; Compose UI tests on the tab view (Robolectric); emulator end-to-end test: open a tune, switch to tab view, play, and see the highlight advance.

**S5 — Importers**
- `TextTabParser`:
  - Modes: letters (octave marked with `+`, `'` or capitals), WhistleSharp-style hole counts (6→0, 7 = C natural, 8 = high D, `,` = half-hole), and ●○◐ / XO hole strings.
  - Equal note lengths under the chosen R and M.
  - Import screen with a live preview.
- MusicXML import:
  - First check the licence of Wim Vree's `xml2abc.js`. If it is clearly LGPL/GPL-compatible, vendor it.
  - Otherwise write a single-voice Kotlin reader (`XmlPullParser` + `ZipInputStream` for `.mxl`).
- Tests: many tokenizer cases plus round-trips (tab → ABC → parse → same pitches); MusicXML fixtures written by hand (notes, rests, ties, triplets, repeats/endings, key/time changes, `.mxl`).

**S6 — Polish and release**
- Icon (SVGs in `assets/icon/`, as in the other apps), Help and Privacy screens, French strings, dark theme.
- `fastlane/metadata/android/{en-US,fr-FR}`, with screenshots captured from the emulator.
- `RELEASES.md` runbook adapted from timer-widget's (reproducible build, developer-signed `Binaries:`, signing with build-tools 34 `apksigner`).
- Reproducibility check: two clean `assembleRelease` builds must be byte-identical.
- Draft fdroiddata recipe.
- Before submitting: confirm F-Droid main's current policy on AI-assisted code and disclose honestly.

## Verification
- Every session: `./gradlew :app:testDebugUnitTest`, `node --test web-tests/`, `./gradlew assembleDebug`.
- Emulator only:
  1. Start it: `~/Android/Sdk/emulator/emulator -avd Medium_Phone_API_37.0 -no-snapshot-save &`
  2. Wait: `adb -s emulator-5554 wait-for-device`
  3. Run: `ANDROID_SERIAL=emulator-5554 ./gradlew :app:connectedDebugAndroidTest`
  - Always check `adb devices` first and pass `-s emulator-5554` to every adb command, so nothing touches the phone.
- Manual check on the emulator: install, import a `.abc`, change the whistle key, play with a loop, open the tab view, export.
- Release: `./gradlew assembleRelease` twice from a clean tree, then `diff` the two APKs.

## Session log

### S1 (2026-10-25)
- Done: scaffold, vendored abcjs 6.7.2, the bridge, `WhistleSynth`, a demo screen playing Ballyhooley. 26 JVM tests, 14 node tests and 4 emulator tests, all green.
- Confirmed: WAV bytes served under abcjs's `.mp3` URLs decode in the WebView, and playback plus highlighting work.
- Learned:
  - `tune.setUpAudio()` works in node (no DOM) and gives MIDI pitch + startChar per note, so note extraction is node-testable.
  - Compose ui-test pulls Espresso 3.5, which crashes on API 37. Espresso 3.7.0 is pinned.
- Carry into S2:
  - The staff is squeezed at phone width (abcjs keeps the source's 8 bars per line). Render with `wrap: { preferredMeasuresPerLine: 4 }` plus a `staffwidth` from the container width.
  - The composer text is clipped on the right.
  - The launcher icon is still weeklies' placeholder (replace in S6).

### S2 (2026-10-25)
- Done: `FingeringEngine` (third octave sourced from the Woodwind Fingering Guide), `WhistleKey` with written/sounding octaves (playback now sounds in the whistle's octave), C natural preference (default OXX OOO), folding, both "change whistle" modes, `TabSettingsStore`, a settings sheet, and diagrams drawn under every note in the SVG. 66 JVM, 25 node and 13 emulator tests, all green.
- Layout: music is rendered at 1.2× width and scaled to fit, giving 3–4 bars per line on a phone. Credits moved into HTML. Lines are pushed apart where text or stems below a staff would collide with diagrams.
- Learned:
  - abcjs makes `#paper` itself the `width: 100%` container, so padding must sit on a wrapper.
  - `staffsep` ignores text and stems.
  - Leaked WebViews exhaust Chromium's AudioContext limit, which made the instrumented tests flaky until they destroyed their controllers.
- Carry into S3:
  - Per-tune whistle (`%whistle_tab_key`).
  - Rewriting the tune into another key with `strTranspose`.
  - Replace the demo screen with the library and tune screens.

### S3 (2026-10-25)
- Done: Room tune store (`TuneEntity`/`TuneDao`/`TuneRepository`, every other column derived by `TuneIndex` on save), `AbcBook` (`split`/`headers`/`join`, Eskin's `%whistle_tab_key` directive), rewriting into another key (`transposeAbc` + the `transposeText` bridge, `KeyChange`, a `KeyChangePreview` that saves a copy by default), the library (search, rhythm and starred chips, starter-set offer on first run, SAF import/export), the tune screen (per-tune whistle, settings sheet, delete undo), the editor (debounced preview, `T:`/`K:` validation), the starter set (50 O'Neill's 1850 tunes built by `scripts/build-starter-set.sh`), and the render cycle gathered into one `TuneRenderer` behind the `TunePage` port. 175 JVM, 35 node and 14 emulator tests, all green.
- Emulator pass done: first run → starter set → search/rhythm filter → tune (diagrams drawn, playback starts) → editor save → export → re-import skips the duplicate (still 50 tunes); layout re-checked on a long air, an E-minor reel and a hornpipe through `layoutReport()` (no diagram collisions); minified `assembleRelease` smoke test green (SAF adds `.txt` to exports; our text/plain makes that unavoidable).
- Also done, pulled forward from S6: a placeholder launcher icon and a navy splash screen.
- Learned: abcjs's `strTranspose` needs the *array* `parseOnly` returns (with a single tune it silently changes nothing), so key rewriting is node-tested through `whistle-core.js` and only the bridge stays WebView-side.
- Carry into S4: the real icon, and a share sheet for one tune (was optional in S3).

### Between S3 and S4 (2026-10-26)
- Done: multi-select in the library (long press, select all, batch delete with one Undo; `RecentlyDeleted` now offers a batch), and the starter set cut from 50 to 10 well-known tunes (Drowsy Maggie, The Merry Blacksmith, The Wind that Shakes the Barley, Banish Misfortune, Out on the Ocean, Haste to the Wedding, The Harvest Home, Off to California, The Boys of Bluehill, The Rakes of Mallow), all kept from the vetted 50.
- Done: whistle note names are a setting (`NoteNames`): "C D E" ("Low D", "Eb") and "Do Ré Mi" ("Ré grave", "Mi♭") are each a whole convention in every language; the default, Auto, picks by the app language.

### Between S3 and S4 (2026-10-27)
- Done: tunes from the web without network access. `MainActivity` (now `singleTask`) accepts "Open with" (`VIEW`, `content:` only) and "Share" (`SEND`/`SEND_MULTIPLE`, files or ABC text); `IncomingTunes` imports them and the library reports the result, popping back to the library unless the editor is open. A "Find tunes online" screen (Library menu and empty library) opens O'Neill's Project, abcnotation.com, Henrik Norbeck's ABC Tunes and John Chambers' Tune Finder in the browser. TheSession.org is deliberately left out, and a test guards that.
- Done: "Read tab from an image" (Library menu). Pick a screenshot, scan or straight-on photo of whistle tab; `scan/TabReader` finds the six-hole diagrams (half holes and "+" octave marks included; staff, lyrics and chords ignored), `TabTune` turns them into equal-length ABC in the whistle's key, and the editor opens with it as an unsaved draft. Plain Kotlin image code, no OpenCV. Tested on synthetic charts drawn by `TabPainter`; on six real tab images from the web (not committed) it found 363 of 367 diagrams, and reads small blurry JPEGs through a column-first fallback.
