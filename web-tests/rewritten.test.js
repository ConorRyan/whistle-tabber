// The tap editor writes imported tunes back in its own layout (TapTune.toAbc,
// fixture written by TapTuneImportTest). abcjs must play each rewritten
// starter tune exactly as the original: the same notes, grace notes
// included, at the same times, repeats and endings followed.
const test = require("node:test");
const assert = require("node:assert/strict");
const fs = require("node:fs");
const path = require("node:path");
const { ABCJS, fixture } = require("./helpers");

const original = ABCJS.parseOnly(
    fs.readFileSync(path.join(__dirname, "..", "app", "src", "main", "assets", "tunes", "oneills.abc"), "utf8"),
);
const rewritten = ABCJS.parseOnly(fixture("starter-rewritten.abc"));

/** Every sound abcjs plays: pitch, start and length in whole notes, rounded. */
function sounds(tune) {
    const track = tune.setUpAudio({}).tracks[0] || [];
    return track
        .filter((ev) => ev.cmd === "note")
        .map((ev) => [ev.pitch, +ev.start.toFixed(4), +ev.duration.toFixed(4)])
        .sort((a, b) => a[1] - b[1] || a[0] - b[0]);
}

test("every starter tune was rewritten", () => {
    assert.equal(rewritten.length, original.length);
    assert.equal(original.length, 10);
});

original.forEach((tune, i) => {
    const title = tune.metaText.title;
    test(`${title} plays the same once rewritten`, () => {
        const after = rewritten[i];
        assert.equal(after.metaText.title, title);
        assert.deepEqual(after.warnings || [], []);
        assert.deepEqual(sounds(after), sounds(tune));
    });
});
