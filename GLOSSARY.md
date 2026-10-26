# Glossary

Domain language for Whistle Tabber. Code, tests and docs name things as they
are named here; add a term when code names a new concept.

- **Page**: the WebView running abcjs (`whistle-page.js`) that renders one
  tune and plays it. Reached through the `TunePage` port: `TuneWebController`
  is the real adapter, an in-memory fake stands in for unit tests.
- **Tune renderer** (`TuneRenderer`): the render cycle as one module: what
  the page shows (ABC, playback transpose, fingerings), render dedupe, the
  notes-to-diagrams step, transpose request/reply matching and playback
  state. Owned by a screen's ViewModel; the page is attached to it.
- **Fingering inputs**: the settings and the tune's written-for whistle a
  render's diagrams come from.
- **Written-for whistle**: the whistle a tune is written for: its
  `%whistle_tab_key` directive, else its key's suggestion, else D.
- **Tab-settings editor** (`TabSettingsEditor`): the one owner for
  tab-settings writes from the UI; both settings surfaces (the settings
  screen and the tune screen's sheet) are backed by one.
- **Key-change preview** (`KeyChangePreview`): previewing a tune rewritten
  into another key: the tonic, its name, the steps, and the rewritten ABC
  once the page has answered.
- **Recently deleted** (`RecentlyDeleted`): delete with undo. Deleting a
  tune removes it and puts it on offer; the library offers Undo and answers
  with undo (restored with its id, star and dates) or dismiss. One tune is on
  offer at a time.
