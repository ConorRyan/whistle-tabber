package io.github.conorryan.whistletabber.web

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class BridgeCodecTest {

    private fun typeOf(json: String) = Json.parseToJsonElement(json).jsonObject["type"]!!.jsonPrimitive.content

    @Test
    fun `commands carry the type the page switches on`() {
        assertEquals("render", typeOf(BridgeCodec.encode(BridgeCommand.Render("X:1"))))
        assertEquals("play", typeOf(BridgeCodec.encode(BridgeCommand.Play)))
        assertEquals("pause", typeOf(BridgeCodec.encode(BridgeCommand.Pause)))
        assertEquals("stop", typeOf(BridgeCodec.encode(BridgeCommand.Stop)))
        assertEquals("seek", typeOf(BridgeCodec.encode(BridgeCommand.Seek(0.5))))
        assertEquals("tempo", typeOf(BridgeCodec.encode(BridgeCommand.Tempo(80))))
        assertEquals("diagrams", typeOf(BridgeCodec.encode(BridgeCommand.Diagrams(emptyList()))))
        assertEquals("transposeText", typeOf(BridgeCodec.encode(BridgeCommand.TransposeText("X:1", 5))))
    }

    @Test
    fun `transposeText carries the text and the steps`() {
        assertEquals(
            """{"type":"transposeText","abc":"X:1\nK:D","steps":-2}""",
            BridgeCodec.encode(BridgeCommand.TransposeText("X:1\nK:D", -2)),
        )
    }

    @Test
    fun `diagrams encode nulls and every spec field`() {
        val json = BridgeCodec.encode(BridgeCommand.Diagrams(listOf(DiagramSpec("CCCHOO", octave = 1), null)))
        assertEquals(
            """{"type":"diagrams","diagrams":[{"holes":"CCCHOO","octave":1,"hard":false,"folded":0,"outOfRange":false},null]}""",
            json,
        )
    }

    @Test
    fun `render carries the playback transpose`() {
        val json = Json.parseToJsonElement(BridgeCodec.encode(BridgeCommand.Render("X:1", midiTranspose = 12))).jsonObject
        assertEquals("12", json["midiTranspose"]!!.jsonPrimitive.content)
    }

    @Test
    fun `render includes default fields so the page never sees undefined`() {
        val json = Json.parseToJsonElement(BridgeCodec.encode(BridgeCommand.Render("X:1"))).jsonObject
        assertEquals("0", json["visualTranspose"]!!.jsonPrimitive.content)
    }

    @Test
    fun `script passes the command as a JS object literal`() {
        val script = BridgeCodec.script(BridgeCommand.Tempo(75))
        assertEquals("""window.whistlePage.handle({"type":"tempo","percent":75});""", script)
    }

    @Test
    fun `abc with quotes, backslashes and newlines stays a valid literal`() {
        val abc = "X:1\nT:Tom's \"Reel\"\nK:D\n|:ABc \\\n def:| "
        val script = BridgeCodec.script(BridgeCommand.Render(abc))
        val literal = script.removePrefix("window.whistlePage.handle(").removeSuffix(");")
        val decoded = Json.parseToJsonElement(literal).jsonObject["abc"]!!.jsonPrimitive.content
        assertEquals(abc, decoded)
        assertTrue("raw newlines would break the statement", '\n' !in script)
    }

    @Test
    fun `decodes every event the page sends`() {
        assertEquals(BridgeEvent.Ready("abcjs-basic v6.7.2"), BridgeCodec.decode("""{"type":"ready","abcjsVersion":"abcjs-basic v6.7.2"}"""))
        assertEquals(BridgeEvent.Finished, BridgeCodec.decode("""{"type":"finished"}"""))
        assertEquals(BridgeEvent.Error("boom"), BridgeCodec.decode("""{"type":"error","message":"boom"}"""))
        assertEquals(
            BridgeEvent.TransposedText("X:1\nK:G", 5),
            BridgeCodec.decode("""{"type":"transposedText","abc":"X:1\nK:G","steps":5}"""),
        )
        assertEquals(BridgeEvent.Playing(12, 3), BridgeCodec.decode("""{"type":"playing","startChar":12,"measure":3}"""))
        assertEquals(BridgeEvent.AudioLoaded(9, listOf("whistle:D5 bad")), BridgeCodec.decode("""{"type":"audioLoaded","loaded":9,"errors":["whistle:D5 bad"]}"""))
    }

    @Test
    fun `rendered notes decode including nulls for tie continuations`() {
        val event = BridgeCodec.decode(
            """{"type":"rendered","warnings":[],"notes":[
              {"index":0,"startChar":29,"endChar":30,"midi":67,"bar":0,"startWhole":0,"durationWhole":0.125,"tieContinuation":false},
              {"index":1,"startChar":34,"endChar":36,"midi":67,"bar":0,"startWhole":null,"durationWhole":null,"tieContinuation":true}
            ]}""",
        ) as BridgeEvent.Rendered
        assertEquals(2, event.notes.size)
        assertEquals(67, event.notes[0].midi)
        assertNull(event.notes[1].startWhole)
        assertTrue(event.notes[1].tieContinuation)
    }

    @Test
    fun `playing with no measure decodes`() {
        assertEquals(BridgeEvent.Playing(5, null), BridgeCodec.decode("""{"type":"playing","startChar":5}"""))
    }

    @Test
    fun `unknown or malformed messages decode to null`() {
        assertNull(BridgeCodec.decode("""{"type":"nope"}"""))
        assertNull(BridgeCodec.decode("not json"))
        assertNull(BridgeCodec.decode("""{"type":"error"}"""))
    }

    @Test
    fun `unknown extra fields are ignored`() {
        assertEquals(BridgeEvent.Finished, BridgeCodec.decode("""{"type":"finished","extra":1}"""))
    }
}
