/*
 * Whistle Tabber: pure helpers on top of abcjs, free of DOM and bridge code
 * so web-tests/ can run them in node against the vendored abcjs bundle.
 */
(function (root, factory) {
    if (typeof module === "object" && module.exports) {
        module.exports = factory();
    } else {
        root.WhistleCore = factory();
    }
})(this, function () {
    "use strict";

    var BAR_TYPES_THAT_DONT_COUNT = { bar_invisible: true };

    /**
     * Written notes of the first voice, in source order, keyed by startChar.
     *
     * The MIDI pitch comes from abcjs's own audio sequence (so key signature,
     * accidentals carried through the bar and transposition all apply). Chords
     * keep their top note: a whistle plays one line. A note continuing a tie
     * has no sound of its own and is flagged so no diagram is drawn for it.
     *
     * @param tune one element of ABCJS.parseOnly()/renderAbc()
     * @param audio optional result of tune.setUpAudio(), computed when absent
     * @param elementsOut optional array; receives each note's abcjs element at
     *        the same index, so the page can find where a note was drawn
     * @returns [{ index, startChar, endChar, midi, bar, startWhole,
     *            durationWhole, tieContinuation }]
     */
    function extractNotes(tune, audio, elementsOut) {
        if (!audio) audio = tune.setUpAudio({});
        var soundByChar = firstSoundByStartChar(audio);
        var notes = [];
        var bar = 0;
        var notesInBar = 0;
        var line, el, i, j;
        for (i = 0; i < tune.lines.length; i++) {
            line = tune.lines[i];
            if (!line.staff || !line.staff[0] || !line.staff[0].voices[0]) continue;
            var voice = line.staff[0].voices[0];
            for (j = 0; j < voice.length; j++) {
                el = voice[j];
                if (el.el_type === "bar") {
                    if (!BAR_TYPES_THAT_DONT_COUNT[el.type] && notesInBar > 0) {
                        bar++;
                        notesInBar = 0;
                    }
                    continue;
                }
                if (el.el_type !== "note" || el.rest || !el.pitches) continue;
                notesInBar++;
                var sound = soundByChar[el.startChar];
                var tied = !sound && el.pitches.some(function (p) { return p.endTie; });
                if (!sound && !tied) continue;
                var previous = notes[notes.length - 1];
                if (elementsOut) elementsOut.push(el);
                notes.push({
                    index: notes.length,
                    startChar: el.startChar,
                    endChar: el.endChar,
                    midi: sound ? sound.pitch : (previous ? previous.midi : null),
                    bar: bar,
                    startWhole: sound ? sound.start : null,
                    durationWhole: sound ? sound.duration : null,
                    tieContinuation: tied,
                });
            }
        }
        return notes;
    }

    /** First (non-grace) sound for each source position; chords keep the top pitch. */
    function firstSoundByStartChar(audio) {
        var byChar = {};
        var track = audio.tracks[0] || [];
        for (var k = 0; k < track.length; k++) {
            var ev = track[k];
            if (ev.cmd !== "note" || ev.style === "grace" || ev.startChar == null) continue;
            var seen = byChar[ev.startChar];
            if (!seen) {
                byChar[ev.startChar] = ev;
            } else if (seen.start === ev.start && ev.pitch > seen.pitch) {
                byChar[ev.startChar] = ev;
            }
        }
        return byChar;
    }

    /**
     * The ABC text rewritten [steps] semitones up (negative: down): notes,
     * accidentals, K: and chord symbols, by abcjs's strTranspose. Everything
     * else is left as written.
     *
     * strTranspose wants the array parseOnly returns; given a single tune it
     * silently returns the text unchanged, hence this wrapper.
     *
     * @param abcjs the ABCJS module (a parameter so node tests can pass it in)
     */
    function transposeAbc(abcjs, abc, steps) {
        steps = Math.trunc(Number(steps)) || 0;
        if (steps === 0) return abc;
        return abcjs.strTranspose(abc, abcjs.parseOnly(abc), steps);
    }

    /** Parse warnings as plain strings (abcjs returns HTML-ish text). */
    function warningsOf(tune) {
        return (tune.warnings || []).map(function (w) {
            return String(w).replace(/<[^>]*>/g, "");
        });
    }

    // Diagram geometry, in SVG user units at scale 1.
    var HOLE_R = 3.3;
    var HOLE_PITCH = 8.2;
    var HAND_GAP = 3; // extra space between the top-hand and bottom-hand holes
    var MARK_SIZE = 9;
    // Holes start half a pitch below the top; then up to two marks below them.
    var DIAGRAM_HEIGHT = 6.5 * HOLE_PITCH + HAND_GAP + 2 * MARK_SIZE;

    var FILL = { C: "closed", O: "open", H: "half" };

    /**
     * Shapes for one fingering diagram, top hole first.
     *
     * @param spec { holes: "CCCHOO", octave, hard, folded, outOfRange }
     * @param cx horizontal centre
     * @param top y of the diagram's top edge
     * @returns { shapes: [{ kind: "hole", cx, cy, r, fill } |
     *                     { kind: "text", x, y, size, text, role }],
     *            classes: [] }
     */
    function diagramShapes(spec, cx, top, scale) {
        if (!scale) scale = 1;
        var shapes = [];
        var classes = ["wt-diagram"];
        if (spec.outOfRange) {
            classes.push("wt-out-of-range");
            shapes.push({ kind: "text", x: cx, y: top + 3 * HOLE_PITCH * scale, size: 2 * MARK_SIZE * scale, text: "?", role: "question" });
            return { shapes: shapes, classes: classes };
        }
        if (spec.hard) classes.push("wt-hard");
        if (spec.folded) classes.push("wt-folded");
        var y = top + HOLE_PITCH * scale / 2;
        for (var i = 0; i < 6; i++) {
            if (i === 3) y += HAND_GAP * scale;
            shapes.push({ kind: "hole", cx: cx, cy: y, r: HOLE_R * scale, fill: FILL[spec.holes.charAt(i)] || "open" });
            y += HOLE_PITCH * scale;
        }
        var markY = y + MARK_SIZE * scale * 0.5;
        if (spec.octave > 0) {
            shapes.push({ kind: "text", x: cx, y: markY, size: MARK_SIZE * scale, text: spec.octave === 2 ? "++" : "+", role: "octave" });
            markY += MARK_SIZE * scale;
        }
        if (spec.folded) {
            shapes.push({ kind: "text", x: cx, y: markY, size: MARK_SIZE * scale * 0.8, text: spec.folded > 0 ? "\u2191" : "\u2193", role: "folded" });
        }
        return { shapes: shapes, classes: classes };
    }

    return {
        extractNotes: extractNotes,
        transposeAbc: transposeAbc,
        warningsOf: warningsOf,
        diagramShapes: diagramShapes,
        DIAGRAM_HEIGHT: DIAGRAM_HEIGHT,
    };
});
