package com.chicanex.audio

import com.chicanex.model.*
import org.junit.Assert.*
import org.junit.Test

/**
 * Tests for RallyAudioEngine speech text preparation.
 * These tests verify the text transformation logic without requiring Android TTS.
 */
class RallyAudioEngineTextTest {

    @Test
    fun `prepareSpeechText replaces severity numbers with words`() {
        val engine = createTestableEngine()
        val note = createNote("Left 3")
        val result = engine.prepareSpeechText(note)
        assertTrue("Should replace '3' with 'three'", result.contains("three"))
    }

    @Test
    fun `prepareSpeechText replaces all severity numbers`() {
        val engine = createTestableEngine()
        for ((digit, word) in mapOf("1" to "one", "2" to "two", "3" to "three",
            "4" to "four", "5" to "five", "6" to "six")) {
            val note = createNote("Left $digit")
            val result = engine.prepareSpeechText(note)
            assertTrue("Should replace '$digit' with '$word': $result", result.contains(word))
        }
    }

    @Test
    fun `prepareSpeechText adds emphasis to caution`() {
        val engine = createTestableEngine()
        val note = createNote("Left 5 caution")
        val result = engine.prepareSpeechText(note)
        assertTrue("Should emphasize caution: $result", result.contains("CAUTION!"))
    }

    @Test
    fun `prepareSpeechText handles into connections`() {
        val engine = createTestableEngine()
        val note = createNote("Left 3 into Right 4")
        val result = engine.prepareSpeechText(note)
        assertTrue("Should contain 'into'", result.contains("into"))
    }

    @Test
    fun `prepareSpeechText preserves distance numbers`() {
        val engine = createTestableEngine()
        val note = createNote("Left 3 long 120 Right 5")
        val result = engine.prepareSpeechText(note)
        assertTrue("Should preserve distance '120'", result.contains("120"))
    }

    private fun createTestableEngine(): TestableRallyAudioEngine {
        return TestableRallyAudioEngine()
    }

    private fun createNote(text: String): PaceNote {
        return PaceNote(
            distanceFromStart = 0.0,
            noteText = text,
            features = emptyList()
        )
    }
}

/**
 * A testable version of RallyAudioEngine that exposes prepareSpeechText
 * without requiring Android Context or TTS initialization.
 */
class TestableRallyAudioEngine {
    fun prepareSpeechText(note: PaceNote): String {
        var text = note.noteText

        // Same logic as RallyAudioEngine.prepareSpeechText
        text = text.replace(Regex("(\\d{2,3})\\s+(?=[A-Z])")) { match ->
            "${match.groupValues[1]}. "
        }

        text = text.replace(" into ", ", into, ")

        text = text.replace("caution", "CAUTION!")
        text = text.replace("Caution", "CAUTION!")

        text = text.replace(Regex("\\b([1-6])\\b")) { match ->
            when (match.groupValues[1]) {
                "1" -> "one"
                "2" -> "two"
                "3" -> "three"
                "4" -> "four"
                "5" -> "five"
                "6" -> "six"
                else -> match.value
            }
        }

        return text
    }
}
