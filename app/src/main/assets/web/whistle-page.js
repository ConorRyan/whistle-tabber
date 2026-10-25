/*
 * Whistle Tabber page glue: renders and plays ABC with abcjs, draws the
 * fingering diagrams the app computes, and talks to the app through
 * window.WhistleBridge (a WebMessageListener, or a JavascriptInterface on old
 * WebViews; both expose postMessage(string)).
 *
 * Commands arrive as JSON objects through window.whistlePage.handle(cmd);
 * events go out as JSON strings. The protocol is mirrored in Kotlin by
 * web/BridgeProtocol.kt; change both together.
 */
(function () {
    "use strict";

    // abcjs fetches "<soundFontUrl><instrument>-mp3/<Note>.mp3"; the app
    // answers these URLs with samples it synthesises (web/SoundPathHandler.kt).
    var SOUND_FONT_URL = "/sound/";
    var WHISTLE_PROGRAM = 78; // General MIDI "whistle", zero-based.
    var SVG_NS = "http://www.w3.org/2000/svg";

    // Space under each staff line for the diagrams, in abcjs units.
    var DIAGRAM_MARGIN = 6;
    var DIAGRAM_SPACE = WhistleCore.DIAGRAM_HEIGHT + 2 * DIAGRAM_MARGIN;
    // Music is laid out this much wider than the screen and scaled down to
    // fit, so four bars of a reel or jig fit on a phone with room for diagrams.
    var ZOOM_OUT = 1.2;

    var paper = document.getElementById("paper");
    var credits = document.getElementById("credits");

    var lastRender = null; // the last render command, replayed on resize
    var visualObj = null;
    var noteElements = [];
    var noteIndexByStartChar = {};
    var diagrams = [];
    var synth = null;
    var highlighted = [];
    var tempoPercent = 100;

    function post(event) {
        if (window.WhistleBridge) window.WhistleBridge.postMessage(JSON.stringify(event));
    }

    function clearHighlight() {
        highlighted.forEach(function (el) { el.classList.remove("abcjs-highlight", "wt-highlight"); });
        highlighted = [];
    }

    var cursorControl = {
        onEvent: function (ev) {
            clearHighlight();
            (ev.elements || []).forEach(function (group) {
                group.forEach(function (el) {
                    el.classList.add("abcjs-highlight");
                    highlighted.push(el);
                });
            });
            var diagram = paper.querySelector('.wt-diagram[data-index="' + noteIndexByStartChar[ev.startChar] + '"]');
            if (diagram) {
                diagram.classList.add("wt-highlight");
                highlighted.push(diagram);
            }
            post({ type: "playing", startChar: ev.startChar, measure: ev.measureNumber });
        },
        onFinished: function () {
            clearHighlight();
            post({ type: "finished" });
        },
    };

    function stopSynth() {
        if (synth) {
            synth.destroy();
            synth = null;
        }
        clearHighlight();
    }

    function render(cmd) {
        stopSynth();
        lastRender = cmd;
        var rendered = ABCJS.renderAbc("paper", cmd.abc, {
            responsive: "resize",
            add_classes: true,
            staffwidth: Math.max(240, paper.clientWidth * ZOOM_OUT),
            wrap: { minSpacing: 1.4, maxSpacing: 2.7, preferredMeasuresPerLine: 4 },
            paddingright: 8,
            paddingbottom: DIAGRAM_SPACE,
            afterParsing: makeRoomForDiagrams,
        });
        visualObj = rendered[0];
        noteElements = [];
        if (!visualObj) {
            showCredits(null);
            post({ type: "rendered", notes: [], warnings: ["No tune found"] });
            return;
        }
        showCredits(visualObj);
        var notes = WhistleCore.extractNotes(visualObj, null, noteElements);
        noteIndexByStartChar = {};
        notes.forEach(function (n) { noteIndexByStartChar[n.startChar] = n.index; });
        drawDiagrams();
        post({ type: "rendered", notes: notes, warnings: WhistleCore.warningsOf(visualObj) });
    }

    /**
     * Widens the gap between staff lines so diagrams fit below each one, and
     * moves the bottom text (B:, Z:, N: …) out of the SVG, where it would
     * collide with the last line's diagrams.
     */
    function makeRoomForDiagrams(tune) {
        tune.formatting.staffsep = (61.33 + DIAGRAM_SPACE) * 3 / 4; // abcjs scales staffsep by 4/3
        tune.bottomText = null;
    }

    function showCredits(tune) {
        credits.textContent = "";
        if (!tune || !tune.metaText) return;
        var meta = tune.metaText;
        [
            ["Book", meta.book],
            ["Source", meta.source],
            ["Transcription", meta.transcription],
            ["Notes", meta.notes],
        ].forEach(function (row) {
            if (!row[1]) return;
            var p = document.createElement("p");
            p.textContent = row[0] + ": " + (Array.isArray(row[1]) ? row[1].join(" ") : String(row[1]));
            credits.appendChild(p);
        });
    }

    function setDiagrams(cmd) {
        diagrams = cmd.diagrams || [];
        drawDiagrams();
    }

    /**
     * Draws each diagram inside its staff line's group, under that line's
     * lowest element, then pushes later lines down wherever diagrams would
     * reach them. abcjs's staffsep only counts note pitches, so text and
     * stems below the staff can eat into the space it reserves.
     */
    function drawDiagrams() {
        var svg = paper.querySelector("svg");
        if (!svg) return;
        resetDiagramLayout(svg);
        var musicBottoms = new Map();
        for (var i = 0; i < noteElements.length && i < diagrams.length; i++) {
            var spec = diagrams[i];
            var abselem = noteElements[i].abselem;
            if (!spec || !abselem || !abselem.elemset || !abselem.elemset[0]) continue;
            var line = abselem.elemset[0].closest(".abcjs-staff-wrapper");
            if (!line) continue;
            if (!musicBottoms.has(line)) {
                var box = line.getBBox();
                musicBottoms.set(line, box.y + box.height);
                line.setAttribute("data-music-bottom", String(box.y + box.height));
            }
            var positions = abselem.notePositions || [];
            var cx = positions.length
                ? Math.max.apply(null, positions.map(function (p) { return p.x; }))
                : abselem.elemset[0].getBBox().x;
            line.appendChild(diagramElement(i, WhistleCore.diagramShapes(spec, cx, musicBottoms.get(line) + DIAGRAM_MARGIN)));
        }
        pushLinesApart(svg);
    }

    function resetDiagramLayout(svg) {
        svg.querySelectorAll(".wt-diagram").forEach(function (g) { g.remove(); });
        Array.prototype.forEach.call(svg.children, function (child) {
            child.removeAttribute("transform");
            child.removeAttribute("data-shift");
        });
        var original = svg.getAttribute("data-original-viewbox");
        if (original) {
            svg.setAttribute("viewBox", original);
            paper.style.paddingBottom = svg.getAttribute("data-original-padding");
        } else {
            svg.setAttribute("data-original-viewbox", svg.getAttribute("viewBox"));
            svg.setAttribute("data-original-padding", paper.style.paddingBottom);
        }
    }

    function pushLinesApart(svg) {
        var shift = 0;
        var lowestDiagramBottom = null;
        Array.prototype.forEach.call(svg.children, function (child) {
            if (typeof child.getBBox !== "function") return;
            var box = child.getBBox();
            if (box.width === 0 && box.height === 0) return;
            if (lowestDiagramBottom !== null && box.y + shift < lowestDiagramBottom + DIAGRAM_MARGIN) {
                shift = lowestDiagramBottom + DIAGRAM_MARGIN - box.y;
            }
            if (shift) {
                child.setAttribute("transform", "translate(0," + shift + ")");
                child.setAttribute("data-shift", String(shift));
            }
            if (child.querySelector(".wt-diagram")) {
                lowestDiagramBottom = box.y + box.height + shift;
            }
        });
        if (!shift) return;
        var vb = svg.getAttribute("viewBox").split(/[ ,]+/).map(Number);
        vb[3] += shift;
        svg.setAttribute("viewBox", vb.join(" "));
        // abcjs's responsive container keeps the aspect ratio in padding-bottom.
        paper.style.paddingBottom = (vb[3] / vb[2] * 100) + "%";
    }

    function diagramElement(index, diagram) {
        var g = document.createElementNS(SVG_NS, "g");
        g.setAttribute("class", diagram.classes.join(" "));
        g.setAttribute("data-index", String(index));
        diagram.shapes.forEach(function (shape) {
            if (shape.kind === "hole") {
                g.appendChild(holeElement(shape));
            } else {
                var text = document.createElementNS(SVG_NS, "text");
                text.setAttribute("x", shape.x);
                text.setAttribute("y", shape.y);
                text.setAttribute("font-size", shape.size);
                text.setAttribute("text-anchor", "middle");
                text.setAttribute("dominant-baseline", "middle");
                text.setAttribute("class", "wt-" + shape.role);
                text.textContent = shape.text;
                g.appendChild(text);
            }
        });
        return g;
    }

    function holeElement(shape) {
        var g = document.createElementNS(SVG_NS, "g");
        g.setAttribute("class", "wt-hole wt-" + shape.fill);
        var circle = document.createElementNS(SVG_NS, "circle");
        circle.setAttribute("cx", shape.cx);
        circle.setAttribute("cy", shape.cy);
        circle.setAttribute("r", shape.r);
        g.appendChild(circle);
        if (shape.fill === "half") {
            // Bottom half covered: a semicircle from the left edge to the right.
            var half = document.createElementNS(SVG_NS, "path");
            half.setAttribute("d",
                "M" + (shape.cx - shape.r) + "," + shape.cy +
                " A" + shape.r + "," + shape.r + " 0 0,0 " + (shape.cx + shape.r) + "," + shape.cy + " Z");
            g.appendChild(half);
        }
        return g;
    }

    function ensureSynth() {
        if (synth) return Promise.resolve();
        synth = new ABCJS.synth.SynthController();
        synth.cursorControl = cursorControl;
        if (tempoPercent !== 100) synth.warp = tempoPercent;
        return synth.setTune(visualObj, true, {
            soundFontUrl: SOUND_FONT_URL,
            program: WHISTLE_PROGRAM,
            midiTranspose: (lastRender && lastRender.midiTranspose) || 0,
            chordsOff: true,
        }).then(function (result) {
            var status = (result && result.notesStatus) || {};
            post({
                type: "audioLoaded",
                loaded: (status.loaded || []).length + (status.cached || []).length,
                errors: status.error || [],
            });
        });
    }

    function play() {
        if (!visualObj) return Promise.resolve();
        return ensureSynth().then(function () {
            if (!synth.isStarted) return synth.play();
        });
    }

    function pause() {
        if (synth && synth.isStarted) synth.play(); // play() toggles
    }

    function seek(fraction) {
        if (!visualObj) return Promise.resolve();
        return ensureSynth().then(function () { synth.seek(fraction); });
    }

    function setTempo(percent) {
        tempoPercent = percent;
        if (synth) return synth.setWarp(percent);
        return Promise.resolve();
    }

    /** Rewrites ABC text into another key; the page's own render is untouched. */
    function transposeText(cmd) {
        var abc = WhistleCore.transposeAbc(ABCJS, cmd.abc, cmd.steps);
        post({ type: "transposedText", abc: abc, steps: cmd.steps });
    }

    function handle(cmd) {
        var result;
        switch (cmd.type) {
            case "render": result = render(cmd); break;
            case "diagrams": result = setDiagrams(cmd); break;
            case "play": result = play(); break;
            case "pause": result = pause(); break;
            case "stop": result = stopSynth(); break;
            case "seek": result = seek(cmd.fraction); break;
            case "tempo": result = setTempo(cmd.percent); break;
            case "transposeText": result = transposeText(cmd); break;
            default: throw new Error("Unknown command " + cmd.type);
        }
        Promise.resolve(result).catch(reportError);
    }

    function reportError(e) {
        post({ type: "error", message: String(e && e.message ? e.message : e) });
    }

    /**
     * Geometry of each drawn diagram next to its note and staff line, in SVG
     * units. Used by the instrumented layout tests.
     */
    function layoutReport() {
        var lines = Array.prototype.slice.call(paper.querySelectorAll(".abcjs-staff-wrapper"));
        var shiftOf = function (el) { return Number(el.getAttribute("data-shift") || 0); };
        var musicTop = function (l) { return l.getBBox().y + shiftOf(l); };
        return Array.prototype.map.call(paper.querySelectorAll(".wt-diagram"), function (g) {
            var index = Number(g.getAttribute("data-index"));
            var note = noteElements[index].abselem.elemset[0];
            var noteBox = note.getBBox();
            var lineEl = note.closest(".abcjs-staff-wrapper");
            var shift = shiftOf(lineEl);
            var box = g.getBBox();
            var line = lines.indexOf(lineEl);
            var next = lines[line + 1];
            return {
                index: index,
                line: line,
                left: box.x,
                right: box.x + box.width,
                centerX: box.x + box.width / 2,
                top: box.y + shift,
                bottom: box.y + box.height + shift,
                noteLeft: noteBox.x,
                noteRight: noteBox.x + noteBox.width,
                lineBottom: Number(lineEl.getAttribute("data-music-bottom")) + shift,
                nextLineTop: next ? musicTop(next) : null,
            };
        });
    }

    window.whistlePage = {
        handle: function (cmd) {
            try {
                handle(cmd);
            } catch (e) {
                reportError(e);
            }
        },
        layoutReport: layoutReport,
    };

    // Re-wrap when the width changes (rotation, split screen). Notes and their
    // indices don't change, so the cached diagrams are simply redrawn.
    var resizeTimer = null;
    var lastWidth = paper.clientWidth;
    window.addEventListener("resize", function () {
        if (paper.clientWidth === lastWidth) return;
        lastWidth = paper.clientWidth;
        clearTimeout(resizeTimer);
        resizeTimer = setTimeout(function () {
            if (lastRender && !(synth && synth.isStarted)) window.whistlePage.handle(lastRender);
        }, 200);
    });

    window.addEventListener("error", function (e) { reportError(e.message); });
    post({ type: "ready", abcjsVersion: ABCJS.signature });
})();
