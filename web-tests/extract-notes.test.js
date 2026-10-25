const test = require("node:test");
const assert = require("node:assert/strict");
const { WhistleCore, fixture, parse } = require("./helpers");

const HEADER = "X:1\nT:t\nM:6/8\nL:1/8\nK:G\n";

function notesOf(body, header = HEADER) {
    return WhistleCore.extractNotes(parse(header + body));
}

test("pitches follow the key signature and accidentals", () => {
    // K:G makes f sharp; =f cancels it for the rest of the bar.
    const midi = notesOf("|GAB fga|=f2f g3|\n").map((n) => n.midi);
    assert.deepEqual(midi, [67, 69, 71, 78, 79, 81, 77, 77, 79]);
});

test("octave marks map to MIDI octaves", () => {
    const midi = notesOf("|D,Dd d'|\n", "X:1\nT:t\nM:4/4\nL:1/4\nK:C\n").map((n) => n.midi);
    assert.deepEqual(midi, [50, 62, 74, 86]);
});

test("rests are skipped and bars are counted from the first note", () => {
    const notes = notesOf("|:GAB z3|e2f g3:|\n");
    assert.deepEqual(notes.map((n) => n.bar), [0, 0, 0, 1, 1, 1]);
    assert.deepEqual(notes.map((n) => n.index), [0, 1, 2, 3, 4, 5]);
});

test("a pickup note gets its own bar", () => {
    const notes = notesOf("D|GAB c2d|\n");
    assert.deepEqual(notes.map((n) => n.bar), [0, 1, 1, 1, 1, 1]);
});

test("first and second endings each count as a bar; [1 does not add an extra one", () => {
    const notes = notesOf("|:GAB c3|[1 d3 d3:|[2 g3 g3||\n");
    assert.deepEqual(notes.map((n) => n.bar), [0, 0, 0, 0, 1, 1, 2, 2]);
});

test("a tied continuation is flagged and inherits the pitch", () => {
    const notes = notesOf("|GAB- B2d|\n");
    const flags = notes.map((n) => n.tieContinuation);
    assert.deepEqual(flags, [false, false, false, true, false]);
    assert.equal(notes[3].midi, 71);
    assert.equal(notes[3].startWhole, null);
});

test("grace notes are not written notes; the main note keeps its pitch", () => {
    const notes = notesOf("|{a}g3 {gfg}e3|\n");
    assert.deepEqual(notes.map((n) => n.midi), [79, 76]);
});

test("chords keep the top note", () => {
    const notes = notesOf("|[GBd]3 [Bg]3|\n");
    assert.deepEqual(notes.map((n) => n.midi), [74, 79]);
});

test("rolls and triplets keep one note per written note", () => {
    const notes = notesOf("|(3ABA ~B3|\n");
    assert.deepEqual(notes.map((n) => n.midi), [69, 71, 69, 71]);
    // (3ABA fits three eighths into the time of two; ~B3 is three eighths.
    const total = notes.reduce((s, n) => s + n.durationWhole, 0);
    assert.ok(Math.abs(total - 0.625) < 1e-6, `expected 5/8, got ${total}`);
});

test("startChar/endChar point back at the source text", () => {
    const abc = HEADER + "|GAB c2d|\n";
    const notes = WhistleCore.extractNotes(parse(abc));
    assert.deepEqual(notes.map((n) => abc.slice(n.startChar, n.endChar).trim()), ["G", "A", "B", "c2", "d"]);
});

test("timing is cumulative in whole notes", () => {
    const notes = notesOf("|GAB c2d|\n");
    assert.deepEqual(notes.map((n) => n.startWhole), [0, 0.125, 0.25, 0.375, 0.625]);
});

test("O'Neill's Ballyhooley: 16 bars, every note in whistle range for a D whistle", () => {
    const notes = WhistleCore.extractNotes(parse(fixture("ballyhooley.abc")));
    // 8 + 8 bars, plus the (e/f/) pickup, which this transcription writes
    // as its own bar between "||" and "|".
    assert.equal(notes[notes.length - 1].bar, 16);
    assert.equal(notes.length, 88); // counted by hand from the transcription
    for (const n of notes) {
        assert.ok(n.midi >= 62 && n.midi <= 88, `midi ${n.midi} out of range at char ${n.startChar}`);
    }
});

test("O'Neill's Banks of the Ilen parses without warnings", () => {
    const tune = parse(fixture("banks-of-the-ilen.abc"));
    assert.deepEqual(WhistleCore.warningsOf(tune), []);
    assert.ok(WhistleCore.extractNotes(tune).length > 100);
});

test("warnings are returned as plain text", () => {
    const tune = parse(HEADER + "|GAB c2d|]]x&&\n");
    const warnings = WhistleCore.warningsOf(tune);
    for (const w of warnings) assert.ok(!/<[^>]+>/.test(w), w);
});

test("elementsOut receives the abcjs element for each note, same index", () => {
    const tune = parse(fixture("ballyhooley.abc"));
    const elements = [];
    const notes = WhistleCore.extractNotes(tune, null, elements);
    assert.equal(elements.length, notes.length);
    notes.forEach((n, i) => assert.equal(elements[i].startChar, n.startChar));
});

test("elementsOut includes tie continuations so indices stay aligned", () => {
    const elements = [];
    const notes = WhistleCore.extractNotes(parse(HEADER + "|GAB- B2d|\n"), null, elements);
    assert.equal(elements.length, 5);
    assert.equal(notes[3].tieContinuation, true);
    assert.equal(elements[3].startChar, notes[3].startChar);
});
