// The bundled starter set (scripts/build-starter-set.sh) must parse cleanly
// and give every tune notes a D whistle can play without folding.
const test = require("node:test");
const assert = require("node:assert");
const fs = require("node:fs");
const path = require("node:path");
const { ABCJS, WhistleCore } = require("./helpers");

const ASSET = path.join(__dirname, "..", "app", "src", "main", "assets", "tunes", "oneills.abc");
const book = fs.readFileSync(ASSET, "utf8");
const tunes = ABCJS.parseOnly(book);

test("the starter set has 50 tunes", () => {
    // (ABCJS.numberOfTunes counts the comment header as a tune, parseOnly doesn't.)
    assert.strictEqual(tunes.length, 50);
});

test("every starter tune parses without warnings", () => {
    for (const tune of tunes) {
        assert.deepStrictEqual(tune.warnings || [], [], tune.metaText.title);
    }
});

test("every starter tune has notes within a D whistle's two octaves", () => {
    for (const tune of tunes) {
        const notes = WhistleCore.extractNotes(tune).filter((n) => n.midi != null);
        assert.ok(notes.length > 20, tune.metaText.title);
        for (const n of notes) {
            assert.ok(n.midi >= 62 && n.midi <= 83, `${tune.metaText.title}: midi ${n.midi}`);
        }
    }
});
