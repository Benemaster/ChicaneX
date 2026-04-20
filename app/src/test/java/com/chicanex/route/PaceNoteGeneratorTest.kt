package com.chicanex.route

import com.chicanex.model.*
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test

/**
 * Tests for PaceNoteGenerator — verifies correct rally-style pace note text generation.
 */
class PaceNoteGeneratorTest {

    private lateinit var generator: PaceNoteGenerator

    @Before
    fun setUp() {
        generator = PaceNoteGenerator()
    }

    @Test
    fun `featureToText generates correct curve text`() {
        val feature = RouteFeature(
            type = FeatureType.CURVE,
            distanceFromStart = 100.0,
            position = GpsPoint(48.0, 11.0),
            severity = 3,
            direction = TurnDirection.LEFT
        )
        val text = generator.featureToText(feature)
        assertEquals("Left 3", text)
    }

    @Test
    fun `featureToText generates curve with tightens modifier`() {
        val feature = RouteFeature(
            type = FeatureType.CURVE,
            distanceFromStart = 100.0,
            position = GpsPoint(48.0, 11.0),
            severity = 4,
            direction = TurnDirection.RIGHT,
            modifier = FeatureModifier.TIGHTENS
        )
        val text = generator.featureToText(feature)
        assertEquals("Right 4 tightens", text)
    }

    @Test
    fun `featureToText generates hairpin text`() {
        val feature = RouteFeature(
            type = FeatureType.HAIRPIN,
            distanceFromStart = 200.0,
            position = GpsPoint(48.0, 11.0),
            direction = TurnDirection.LEFT
        )
        val text = generator.featureToText(feature)
        assertEquals("Hairpin Left", text)
    }

    @Test
    fun `featureToText generates chicane text`() {
        val feature = RouteFeature(
            type = FeatureType.CHICANE,
            distanceFromStart = 150.0,
            position = GpsPoint(48.0, 11.0),
            direction = TurnDirection.LEFT
        )
        val text = generator.featureToText(feature)
        assertEquals("Chicane Left Right", text)
    }

    @Test
    fun `featureToText generates crest text`() {
        val feature = RouteFeature(
            type = FeatureType.CREST,
            distanceFromStart = 300.0,
            position = GpsPoint(48.0, 11.0),
            gradientPercent = 5.0
        )
        val text = generator.featureToText(feature)
        assertEquals("Crest", text)
    }

    @Test
    fun `featureToText generates big crest for steep gradient`() {
        val feature = RouteFeature(
            type = FeatureType.CREST,
            distanceFromStart = 300.0,
            position = GpsPoint(48.0, 11.0),
            gradientPercent = 12.0
        )
        val text = generator.featureToText(feature)
        assertEquals("Crest big", text)
    }

    @Test
    fun `featureToText generates long straight with distance`() {
        val feature = RouteFeature(
            type = FeatureType.STRAIGHT,
            distanceFromStart = 50.0,
            position = GpsPoint(48.0, 11.0),
            lengthMeters = 250.0
        )
        val text = generator.featureToText(feature)
        assertEquals("Straight 250", text)
    }

    @Test
    fun `featureToText returns empty for short straight`() {
        val feature = RouteFeature(
            type = FeatureType.STRAIGHT,
            distanceFromStart = 50.0,
            position = GpsPoint(48.0, 11.0),
            lengthMeters = 80.0
        )
        val text = generator.featureToText(feature)
        assertEquals("", text)
    }

    @Test
    fun `featureToText generates steep climb`() {
        val feature = RouteFeature(
            type = FeatureType.CLIMB,
            distanceFromStart = 400.0,
            position = GpsPoint(48.0, 11.0),
            lengthMeters = 300.0,
            gradientPercent = 12.0,
            modifier = FeatureModifier.STEEP
        )
        val text = generator.featureToText(feature)
        assertEquals("steep Climb 300", text)
    }

    @Test
    fun `featureToText generates curve with long modifier`() {
        val feature = RouteFeature(
            type = FeatureType.CURVE,
            distanceFromStart = 100.0,
            position = GpsPoint(48.0, 11.0),
            severity = 2,
            direction = TurnDirection.RIGHT,
            lengthMeters = 120.0
        )
        val text = generator.featureToText(feature)
        assertEquals("Right 2 long", text)
    }

    @Test
    fun `generatePaceNotes creates notes from features`() {
        val features = listOf(
            RouteFeature(FeatureType.START, 0.0, GpsPoint(48.0, 11.0)),
            RouteFeature(
                FeatureType.CURVE, 100.0, GpsPoint(48.001, 11.0),
                severity = 3, direction = TurnDirection.LEFT, lengthMeters = 40.0
            ),
            RouteFeature(
                FeatureType.STRAIGHT, 200.0, GpsPoint(48.002, 11.0),
                lengthMeters = 200.0
            ),
            RouteFeature(
                FeatureType.CURVE, 450.0, GpsPoint(48.004, 11.0),
                severity = 5, direction = TurnDirection.RIGHT, lengthMeters = 30.0
            ),
            RouteFeature(FeatureType.FINISH, 600.0, GpsPoint(48.006, 11.0))
        )

        val notes = generator.generatePaceNotes(features)
        assertTrue("Should generate pace notes", notes.isNotEmpty())

        // Verify notes are sorted by distance
        for (i in 0 until notes.size - 1) {
            assertTrue(
                "Notes should be sorted by distance",
                notes[i].distanceFromStart <= notes[i + 1].distanceFromStart
            )
        }
    }

    @Test
    fun `generatePaceNotes links close features with into`() {
        val features = listOf(
            RouteFeature(
                FeatureType.CURVE, 100.0, GpsPoint(48.0, 11.0),
                severity = 3, direction = TurnDirection.LEFT, lengthMeters = 20.0
            ),
            RouteFeature(
                FeatureType.CURVE, 125.0, GpsPoint(48.001, 11.0),
                severity = 4, direction = TurnDirection.RIGHT, lengthMeters = 25.0
            )
        )

        val notes = generator.generatePaceNotes(features)
        assertTrue("Should have at least one note", notes.isNotEmpty())

        // The closely-linked features should be combined with "into"
        val combinedNote = notes.firstOrNull { it.noteText.contains("into") }
        assertNotNull("Close features should be linked with 'into'", combinedNote)
    }
}
