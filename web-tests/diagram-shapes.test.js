const test = require("node:test");
const assert = require("node:assert/strict");
const { WhistleCore } = require("./helpers");

const holes = (d) => d.shapes.filter((s) => s.kind === "hole");
const texts = (d) => d.shapes.filter((s) => s.kind === "text");

test("six holes, top hole first, filled by state", () => {
    const d = WhistleCore.diagramShapes({ holes: "CCCHOO", octave: 0 }, 50, 100);
    const h = holes(d);
    assert.equal(h.length, 6);
    assert.deepEqual(h.map((s) => s.fill), ["closed", "closed", "closed", "half", "open", "open"]);
    for (let i = 1; i < 6; i++) assert.ok(h[i].cy > h[i - 1].cy, "holes go downwards");
    assert.ok(h.every((s) => s.cx === 50));
    assert.ok(h[0].cy - h[0].r >= 100, "first hole starts at the top edge");
});

test("there is a wider gap between the hands", () => {
    const h = holes(WhistleCore.diagramShapes({ holes: "OOOOOO" }, 0, 0));
    const gaps = h.slice(1).map((s, i) => s.cy - h[i].cy);
    assert.ok(gaps[2] > gaps[0] && gaps[2] > gaps[3], `gaps ${gaps}`);
    assert.ok(Math.abs(gaps[0] - gaps[1]) < 1e-9);
    assert.ok(Math.abs(gaps[3] - gaps[4]) < 1e-9);
});

test("holes never overlap", () => {
    const h = holes(WhistleCore.diagramShapes({ holes: "CCCCCC" }, 0, 0));
    for (let i = 1; i < 6; i++) assert.ok(h[i].cy - h[i - 1].cy > 2 * h[i].r);
});

test("octave marks go below the holes: + and ++", () => {
    const low = WhistleCore.diagramShapes({ holes: "CCCCCC", octave: 0 }, 0, 0);
    assert.equal(texts(low).length, 0);
    const second = WhistleCore.diagramShapes({ holes: "CCCCCC", octave: 1 }, 0, 0);
    assert.deepEqual(texts(second).map((t) => t.text), ["+"]);
    assert.ok(texts(second)[0].y > holes(second)[5].cy);
    const third = WhistleCore.diagramShapes({ holes: "OCCCCC", octave: 2, hard: true }, 0, 0);
    assert.deepEqual(texts(third).map((t) => t.text), ["++"]);
    assert.ok(third.classes.includes("wt-hard"));
});

test("folded notes get an arrow below the octave mark and a class", () => {
    const up = WhistleCore.diagramShapes({ holes: "OOOOOO", octave: 0, folded: 1 }, 0, 0);
    assert.deepEqual(texts(up).map((t) => t.role), ["folded"]);
    assert.equal(texts(up)[0].text, "↑");
    assert.ok(up.classes.includes("wt-folded"));
    const down = WhistleCore.diagramShapes({ holes: "CCCCCH", octave: 1, folded: -1 }, 0, 0);
    assert.deepEqual(texts(down).map((t) => t.text), ["+", "↓"]);
    assert.ok(texts(down)[1].y > texts(down)[0].y);
});

test("out-of-range notes draw a single question mark and no holes", () => {
    const d = WhistleCore.diagramShapes({ outOfRange: true }, 10, 0);
    assert.equal(holes(d).length, 0);
    assert.deepEqual(texts(d).map((t) => t.text), ["?"]);
    assert.ok(d.classes.includes("wt-out-of-range"));
});

test("everything fits inside DIAGRAM_HEIGHT, even with octave and fold marks", () => {
    const d = WhistleCore.diagramShapes({ holes: "CCCCCC", octave: 2, folded: -1 }, 0, 0);
    const bottom = Math.max(
        ...holes(d).map((s) => s.cy + s.r),
        ...texts(d).map((t) => t.y + t.size / 2),
    );
    assert.ok(bottom <= WhistleCore.DIAGRAM_HEIGHT, `${bottom} > ${WhistleCore.DIAGRAM_HEIGHT}`);
});

test("scale multiplies every dimension", () => {
    const a = holes(WhistleCore.diagramShapes({ holes: "CCCCCC" }, 0, 0, 1));
    const b = holes(WhistleCore.diagramShapes({ holes: "CCCCCC" }, 0, 0, 2));
    assert.equal(b[0].r, 2 * a[0].r);
    assert.equal(b[5].cy, 2 * a[5].cy);
});

test("unknown hole letters draw as open", () => {
    const h = holes(WhistleCore.diagramShapes({ holes: "C?" }, 0, 0));
    assert.deepEqual(h.map((s) => s.fill), ["closed", "open", "open", "open", "open", "open"]);
});
