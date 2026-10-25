# Session 3 TODO: library, editor, storage

Goal: replace the demo screen with a real app. You keep a library of tunes,
open one to see its tabs and play it, edit its ABC with a live preview, and
import or export `.abc` files. Scope comes from `docs/ROADMAP.md` (S3) plus
the items carried over from the S2 log.

Rules from AGENTS.md still apply:
- No network.
- No TheSession data.
- Tests on the emulator only.
- Keep AGENTS.md's architecture section in sync.

## 0. Before coding
- [x] Check in Eskin's MIT `abcjs-basic-eskin.js` (seisiuneer/abctools) for the exact syntax of `%whistle_tab_key` and `%whistle_tab_octave`, so files round-trip with his tool. Write down what you find in `AbcBook.kt`.
- [x] Spike in node: does `ABCJS.strTranspose(abc, visualObj, steps)` work on a `parseOnly` tune, or does it need a rendered one? This decides whether "rewrite key" can be node-tested or must go through the WebView.

## 1. Pure Kotlin: `music/AbcBook.kt`
- [x] `split(text)`: split a multi-tune file into tunes at `X:` lines. Keep the file header (lines before the first `X:`) and apply it to each tune.
- [x] `headers(abc)`: read X, T (first title, plus alternates), R, M, L, K, C, O, Z, B, and the whistle directives. Cope with `T:` containing spaces or colons, `%` comments, inline fields, CRLF, and a BOM.
- [x] `withWhistle(abc, key)` / `whistleOf(abc)`: read and write the per-tune whistle directive without touching anything else in the text.
- [x] `join(tunes)`: export tunes with unique `X:` numbers, separated by blank lines.
- [x] Map `K:` to a suggested default whistle. D, G, Em, Bm, Am and A modal keys suggest a D whistle; F and Dm suggest C; leave anything else to the user.
- [x] Tests (`src/test/resources/abc/`, hand-written or O'Neill's only):
  - [x] Multi-tune files, with and without a file header.
  - [x] Odd header shapes and CRLF.
  - [x] Round trips that change nothing.
  - [x] Whistle directive add, replace and remove.
  - [x] `join` renumbers `X:`.

## 2. Storage: Room
- [x] `TuneEntity`:
  - Fields: id, abc (the canonical record), title, rhythm, key, meter, whistle, starred, source, createdAt, updatedAt.
  - The index fields are derived from `abc` with `AbcBook.headers` on every save. The UI never sets them directly.
- [x] `TuneDao`: observe all tunes, sorted by title; search title and alternate titles, case- and accent-insensitive; filter by rhythm and starred; insert many in one transaction.
- [x] `AppDatabase` v1 with `exportSchema = true` (`app/schemas/` is already set up in ksp). Add a migration test harness now, even with only one version.
- [x] `TuneRepository`, the only way from the UI to the database: `tunes`, `search`, `get(id)`, `save(abc)`, `delete`, `importBook(text): ImportResult(added, skipped duplicates, errors)`, `exportAll(): String`.
- [x] Duplicates: on import, a tune with the same title and the same ABC is skipped. A same title with different ABC is added.
- [x] Add the database and repository to `AppGraph`. Add an `appScope` like weeklies has.
- [x] Tests: the DAO and repository with in-memory Room under Robolectric; import counts; derived fields update when the ABC is edited; export and re-import is lossless.

## 3. Starter set
- [x] (No polkas exist in the 1850 book; one slide. Marches stand in for polkas.) Pick about 50 tunes from O'Neill's 1850: a mix of reels, jigs, slip jigs, hornpipes, polkas, slides and airs, mostly in D, G, Em, Am and Bm, and playable on a D whistle with folding off where possible. List them in `scripts/starter-tunes.txt`.
- [x] Add `scripts/build-starter-set.sh`. It fetches the listed files from trillian.mit.edu, keeps each tune's `Z:`/`B:` credit lines, and writes `app/src/main/assets/tunes/oneills.abc`. Commit both the script and its output; the build does not run it.
- [ ] Offer the set on first run with an empty library ("Add 50 traditional tunes?"). Also offer it later from Settings.
- [x] Update THIRD_PARTY_NOTICES with the asset path (GPL-2+).
- [x] Tests: a JVM test that the asset parses into the expected number of tunes, every one with a title and `K:`, and no abcjs warnings. A node test that runs every tune in the asset through `parseOnly` and `extractNotes`.

## 4. Screens and navigation
- [ ] Navigation Compose with three destinations: Library (start), Tune/{id}, Editor/{id|new}, plus Settings.
- [ ] **Library**:
  - [ ] Search field and rhythm filter chips.
  - [ ] List rows show title, rhythm, key and whistle; tunes can be starred.
  - [ ] An empty state that offers the starter set.
  - [ ] Menu: import `.abc`, export the whole library, new tune.
- [ ] **Tune screen** (replaces `DemoTuneScreen`):
  - [ ] WebView with diagrams and playback, using the per-tune whistle. Tab settings move into `TuneViewModel`, which takes over from `DemoTuneViewModel` together with its tests.
  - [ ] Actions: edit, export this tune, change whistle, rewrite into another key (see step 5), delete with undo.
  - [ ] Keep one `TuneWebController` per screen and destroy it on dispose.
- [ ] **Editor**:
  - [ ] Monospace ABC text field, with the draft kept in `SavedStateHandle`.
  - [ ] Debounced (~400 ms) live preview in a WebView below the text.
  - [ ] abcjs parse warnings listed inline.
  - [ ] Saving is blocked when there is no `T:` or `K:`.
  - [ ] New tunes start from a template.
- [ ] **Settings**:
  - [ ] Default whistle and C natural, moved out of the tune sheet.
  - [ ] Fold toggle and transpose mode.
  - [ ] Add the starter set.
  - [ ] About and licences.
- [ ] Delete `ui/spike/` once its pieces have moved. Keep `TabSettingsSheet` as a per-tune whistle picker if it still fits.
- [ ] Add every new string in English and French.

## 5. Rewriting a tune into another key
- [x] Bridge: a `transposeText(steps)` command, and a `transposedText(abc)` event in reply (or do it in node-tested core code if the spike in step 0 allows). Add both to `BridgeProtocolSyncTest`.
- [ ] UI: pick a target key, preview it, then save as a new version or as a copy.
- [x] Tests: D→G and G→D keep the same note count with every pitch shifted by the step count; accidentals and the `K:` line are rewritten; codec tests for the new messages.

## 6. Files (SAF)
- [ ] Import: `OpenDocument` (multiple, `text/*` plus `*/*` for `.abc` files with odd MIME types). Read as UTF-8, falling back to Latin-1 for old O'Neill's-style files. Show a summary snackbar.
- [ ] Export: `CreateDocument("text/plain")` named `whistle-tabber-YYYY-MM-DD.abc` for the library or `<title>.abc` for one tune. This is the v1 backup.
- [ ] Optional, if time allows: the Android share sheet for one tune.
- [ ] Tests: Robolectric tests of import and export with fake content URIs, the encoding fallback, and a 10k-line file.

## 7. Tests and verification
- [ ] ViewModels with Turbine: `LibraryViewModel` (search, filter, first-run offer), `TuneViewModel` (per-tune whistle, re-render rules carried over from `DemoTuneViewModel`), `EditorViewModel` (debounce, validation, save, restoring the draft).
- [ ] Compose UI tests (Robolectric): the library list and its empty state, and editor validation messages.
- [ ] Emulator end-to-end: first run → add starter set → search "reel" → open a tune → diagrams drawn → play → edit a note → the preview updates → save → export → re-import gives the same count.
- [ ] Re-check the layout on the emulator with a few starter tunes: a long air, a polka, and a tune in E minor.
- [ ] Run `./gradlew :app:testDebugUnitTest`, `(cd web-tests && node --test)`, `ANDROID_SERIAL=emulator-5554 ./gradlew :app:connectedDebugAndroidTest`, and a minified `assembleRelease` smoke test on the emulator.
- [ ] Update AGENTS.md (architecture, invariants) and add an S3 entry to the ROADMAP log.
- [ ] Commit and push to `main`.

## Open questions (ask at the start of S3)
- Should the starter set be offered on first run, or added automatically?
- When rewriting a tune into another key, should the default be to save a copy or to replace the original?
- Should starred tunes appear as their own tab, or only as a filter?
