const test = require("node:test");
const assert = require("node:assert/strict");
const { ABCJS, WhistleCore, fixture, parse } = require("./helpers");

const transpose = (abc, steps) => WhistleCore.transposeAbc(ABCJS, abc, steps);
const midiOf = (abc) => WhistleCore.extractNotes(parse(abc)).map((n) => n.midi);
const keyOf = (abc) => abc.match(/^K:\s*(.*)$/m)[1].trim();

const D_TUNE = "X:1\nT:Test\nM:6/8\nL:1/8\nK:D\n|:dAF ^GAB|c2d e2f|gfe dcB|A3 d3:|\n";

test("D to G: every pitch moves up a fourth and K: follows", () => {
    const out = transpose(D_TUNE, 5);
    assert.equal(keyOf(out), "G");
    const before = midiOf(D_TUNE);
    assert.deepEqual(midiOf(out), before.map((m) => m + 5));
    assert.match(out, /\^c/); // the G sharp accidental became C sharp
});

test("G to D: down a fifth", () => {
    const g = transpose(D_TUNE, 5);
    const back = transpose(g, -5);
    assert.equal(keyOf(back), "D");
    assert.deepEqual(midiOf(back), midiOf(D_TUNE));
});

test("a real tune keeps its note count and shifts every pitch", () => {
    const abc = fixture("ballyhooley.abc");
    for (const steps of [-5, -2, 2, 7]) {
        const out = transpose(abc, steps);
        const before = midiOf(abc);
        const after = midiOf(out);
        assert.equal(after.length, before.length, `steps ${steps}`);
        assert.deepEqual(after, before.map((m) => m + steps), `steps ${steps}`);
        assert.deepEqual(parse(out).warnings || [], [], `steps ${steps}`);
    }
});

test("minor and modal keys keep their mode", () => {
    assert.equal(keyOf(transpose("X:1\nT:t\nK:Em\nEFG|\n", 5)), "Am");
    assert.equal(keyOf(transpose("X:1\nT:t\nK:Ador\nABc|\n", -2)), "Gdor");
});

test("chord symbols move, headers and the whistle directive don't", () => {
    const abc = "X:1\nT:t\n%whistle_tab_key d\nR:reel\nK:D\n\"D\"DFA \"G\"GBd|\n";
    const out = transpose(abc, 2);
    assert.match(out, /"E"EGB/);
    assert.match(out, /"A"Ace\|/);
    assert.match(out, /^%whistle_tab_key d$/m);
    assert.match(out, /^T:t$/m);
});

test("zero or nonsense steps return the text unchanged", () => {
    assert.equal(transpose(D_TUNE, 0), D_TUNE);
    assert.equal(transpose(D_TUNE, "x"), D_TUNE);
});

test("strTranspose given a bare tune is a no-op (why transposeAbc wraps it)", () => {
    assert.equal(ABCJS.strTranspose(D_TUNE, parse(D_TUNE), 5), D_TUNE);
});
