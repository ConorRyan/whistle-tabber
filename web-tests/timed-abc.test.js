// The form TimedAbc.kt writes (tapped rhythm): ties across bar lines, tied
// pieces, a pickup bar, rests and per-bar accidentals, checked against abcjs.
const test = require("node:test");
const assert = require("node:assert/strict");
const { WhistleCore, fixture, parse } = require("./helpers");

const tune = parse(fixture("timed-tap.abc"));

function elements() {
    return tune.lines.flatMap((line) => line.staff[0].voices[0]);
}

test("parses without warnings, with its tempo", () => {
    assert.deepEqual(tune.warnings || [], []);
    assert.equal(tune.metaText.tempo.bpm, 120);
});

test("every note and rest has the length written", () => {
    const lengths = elements()
        .filter((el) => el.el_type === "note")
        .map((el) => [el.rest ? "z" : el.pitches[0].name, el.duration]);
    assert.deepEqual(lengths, [
        ["A", 0.25],
        ["D", 0.75], ["E", 0.25],
        ["E", 0.25], ["z", 0.25], ["=c", 0.25], ["^c", 0.25],
        ["d", 0.5], ["d", 0.125], ["B", 0.375],
    ]);
});

test("full bars add up to a bar of 4/4", () => {
    const bars = [[]];
    for (const el of elements()) {
        if (el.el_type === "bar") bars.push([]);
        else if (el.el_type === "note") bars[bars.length - 1].push(el.duration);
    }
    const totals = bars.filter((b) => b.length).map((b) => b.reduce((a, d) => a + d, 0));
    assert.deepEqual(totals, [0.25, 1, 1, 1]);
});

test("each written piece is a note for the diagrams, accidentals per bar", () => {
    const midi = WhistleCore.extractNotes(tune).map((n) => n.midi);
    // Tied pieces each get a diagram: E over the bar line, then d4- d.
    assert.deepEqual(midi, [69, 62, 64, 64, 72, 73, 74, 74, 71]);
});
