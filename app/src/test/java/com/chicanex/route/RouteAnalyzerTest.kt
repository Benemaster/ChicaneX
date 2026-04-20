package com.chicanex.route

import com.chicanex.model.*
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test

/**
 * Tests for RouteAnalyzer — verifies detection of curves, hairpins, straights,
 * elevation features, and compound features like chicanes.
 */
class RouteAnalyzerTest {

    private lateinit var analyzer: RouteAnalyzer

    @Before
    fun setUp() {
        analyzer = RouteAnalyzer()
    }

    @Test
    fun `buildSegments returns correct number of segments`() {
        val points = listOf(
            GpsPoint(48.0, 11.0, 500.0),
            GpsPoint(48.001, 11.0, 505.0),
            GpsPoint(48.002, 11.0, 510.0),
            GpsPoint(48.003, 11.0, 515.0)
        )
        val segments = analyzer.buildSegments(points)
        assertEquals(3, segments.size)
    }

    @Test
    fun `buildSegments calculates cumulative distance`() {
        val points = listOf(
            GpsPoint(48.0, 11.0, 500.0),
            GpsPoint(48.001, 11.0, 500.0),
            GpsPoint(48.002, 11.0, 500.0)
        )
        val segments = analyzer.buildSegments(points)
        assertTrue(segments[1].cumulativeDistance > segments[0].cumulativeDistance)
    }

    @Test
    fun `analyzeRoute returns empty for fewer than 3 waypoints`() {
        val points = listOf(
            GpsPoint(48.0, 11.0),
            GpsPoint(48.001, 11.0)
        )
        val features = analyzer.analyzeRoute(points)
        assertTrue(features.isEmpty())
    }

    @Test
    fun `analyzeRoute includes START and FINISH features`() {
        val points = createStraightRoute(10)
        val features = analyzer.analyzeRoute(points)
        assertTrue(features.any { it.type == FeatureType.START })
        assertTrue(features.any { it.type == FeatureType.FINISH })
    }

    @Test
    fun `analyzeRoute detects straight section`() {
        // Create a long straight road going north
        val points = createStraightRoute(50)
        val features = analyzer.analyzeRoute(points)
        assertTrue("Should detect at least one straight", features.any { it.type == FeatureType.STRAIGHT })
    }

    @Test
    fun `classifySeverity returns 1 for gentle curves`() {
        val severity = analyzer.classifySeverity(radiusMeters = 150.0, absAngle = 15.0)
        assertEquals(1, severity)
    }

    @Test
    fun `classifySeverity returns 6 for very tight curves`() {
        val severity = analyzer.classifySeverity(radiusMeters = 8.0, absAngle = 160.0)
        assertEquals(6, severity)
    }

    @Test
    fun `classifySeverity returns middle values for medium curves`() {
        val severity = analyzer.classifySeverity(radiusMeters = 50.0, absAngle = 55.0)
        assertTrue("Severity should be 2-4, was $severity", severity in 2..4)
    }

    @Test
    fun `normalizedBearingChange handles wrap-around`() {
        // 350 -> 10 should be +20 (right turn)
        val change1 = RouteAnalyzer.normalizedBearingChange(350.0, 10.0)
        assertEquals(20.0, change1, 0.01)

        // 10 -> 350 should be -20 (left turn)
        val change2 = RouteAnalyzer.normalizedBearingChange(10.0, 350.0)
        assertEquals(-20.0, change2, 0.01)
    }

    @Test
    fun `normalizedBearingChange returns positive for right turns`() {
        val change = RouteAnalyzer.normalizedBearingChange(0.0, 90.0)
        assertTrue("Right turn should be positive", change > 0)
    }

    @Test
    fun `normalizedBearingChange returns negative for left turns`() {
        val change = RouteAnalyzer.normalizedBearingChange(90.0, 0.0)
        assertTrue("Left turn should be negative", change < 0)
    }

    @Test
    fun `smoothGradients returns same size list`() {
        val segments = listOf(
            createSegment(0.0, 10.0, 100.0, 1.0),
            createSegment(0.0, 10.0, 200.0, 2.0),
            createSegment(0.0, 10.0, 300.0, -1.0)
        )
        val smoothed = analyzer.smoothGradients(segments)
        assertEquals(segments.size, smoothed.size)
    }

    @Test
    fun `detectElevationFeatures handles missing elevation`() {
        val segments = listOf(
            createSegment(0.0, null, 100.0, 0.0),
            createSegment(0.0, null, 200.0, 0.0)
        )
        val features = analyzer.detectElevationFeatures(segments)
        assertTrue("Should return empty when elevation data missing", features.isEmpty())
    }

    @Test
    fun `calculateStatistics computes elevation gain`() {
        val points = listOf(
            GpsPoint(48.0, 11.0, 500.0),
            GpsPoint(48.001, 11.0, 510.0),
            GpsPoint(48.002, 11.0, 505.0),
            GpsPoint(48.003, 11.0, 520.0)
        )
        val segments = analyzer.buildSegments(points)
        val features = analyzer.analyzeRoute(points)
        val stats = analyzer.calculateStatistics(points, features, segments)

        // Gain: 10 + 15 = 25, Loss: 5
        assertEquals(25.0, stats.elevationGain, 0.1)
        assertEquals(5.0, stats.elevationLoss, 0.1)
    }

    // --- Helper methods ---

    private fun createStraightRoute(pointCount: Int): List<GpsPoint> {
        return (0 until pointCount).map { i ->
            GpsPoint(48.0 + i * 0.001, 11.0, 500.0)
        }
    }

    private fun createSegment(
        bearing: Double,
        elevationChange: Double?,
        cumulativeDistance: Double,
        distance: Double
    ): RouteSegment {
        return RouteSegment(
            from = GpsPoint(48.0, 11.0, 500.0),
            to = GpsPoint(48.001, 11.0, 500.0 + (elevationChange ?: 0.0)),
            distance = if (distance > 0) distance else 100.0,
            bearing = bearing,
            cumulativeDistance = cumulativeDistance,
            elevationChange = elevationChange
        )
    }
}
