package com.chicanex.model

/**
 * Represents a complete analyzed route with its waypoints and extracted features.
 */
data class AnalyzedRoute(
    /** Name of the route/stage */
    val name: String,
    /** Original GPS waypoints */
    val waypoints: List<GpsPoint>,
    /** Extracted route features */
    val features: List<RouteFeature>,
    /** Generated pace notes */
    val paceNotes: List<PaceNote>,
    /** Total route distance in meters */
    val totalDistanceMeters: Double,
    /** Total elevation gain in meters */
    val totalElevationGain: Double,
    /** Total elevation loss in meters */
    val totalElevationLoss: Double,
    /** Maximum gradient percentage on the route */
    val maxGradientPercent: Double,
    /** Number of curves on the route */
    val curveCount: Int,
    /** Average curve severity */
    val averageCurveSeverity: Double
) {
    /** Route summary statistics for display */
    fun summary(): String = buildString {
        appendLine("Stage: $name")
        appendLine("Distance: ${"%.1f".format(totalDistanceMeters / 1000)} km")
        appendLine("Elevation gain: ${"%.0f".format(totalElevationGain)} m")
        appendLine("Elevation loss: ${"%.0f".format(totalElevationLoss)} m")
        appendLine("Max gradient: ${"%.1f".format(maxGradientPercent)}%")
        appendLine("Curves: $curveCount (avg severity: ${"%.1f".format(averageCurveSeverity)})")
        appendLine("Pace notes: ${paceNotes.size}")
    }
}
