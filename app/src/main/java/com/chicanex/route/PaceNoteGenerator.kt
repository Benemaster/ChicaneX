package com.chicanex.route

import com.chicanex.model.*

/**
 * Generates rally-style pace notes from analyzed route features.
 *
 * Pace notes use standard rally co-driver terminology:
 * - Curve severity: "1" (gentle) to "6" (very tight)
 * - Direction: "Left" / "Right"
 * - Hairpins: "Hairpin Left/Right"
 * - Modifiers: "tightens", "opens", "long", "over crest"
 * - Distances: "100" (meters to next feature)
 * - Connections: "into" (features follow immediately)
 * - Elevation: "Crest", "Dip", "Climb", "Descent"
 * - Compound: "Chicane Left-Right", "S-Bend"
 *
 * Example output: "Left 3 tightens, 80, Right 5 into Hairpin Left over crest"
 */
class PaceNoteGenerator(
    private val config: PaceNoteConfig = PaceNoteConfig()
) {
    /**
     * Generates pace notes from a list of route features.
     */
    fun generatePaceNotes(features: List<RouteFeature>): List<PaceNote> {
        val sorted = features.sortedBy { it.distanceFromStart }
        val notes = mutableListOf<PaceNote>()
        var i = 0

        while (i < sorted.size) {
            val feature = sorted[i]
            val linkedFeatures = mutableListOf(feature)

            // Look ahead for closely-linked features ("into" connections)
            var j = i + 1
            while (j < sorted.size) {
                val next = sorted[j]
                val gap = next.distanceFromStart - feature.distanceFromStart -
                    (linkedFeatures.last().lengthMeters ?: 0.0)

                if (gap < config.intoGapMeters && isLinkableFeature(next)) {
                    linkedFeatures.add(next)
                    j++
                } else {
                    break
                }
            }

            // Generate note text for this group
            val noteText = buildNoteText(linkedFeatures, sorted, i)
            if (noteText.isNotBlank()) {
                val priority = determinePriority(linkedFeatures)
                val callAhead = determineCallAhead(linkedFeatures, priority)

                notes.add(
                    PaceNote(
                        distanceFromStart = feature.distanceFromStart,
                        noteText = noteText,
                        features = linkedFeatures,
                        priority = priority,
                        callAheadMeters = callAhead
                    )
                )
            }

            i = j
        }

        return notes
    }

    /**
     * Builds the spoken text for a group of linked features.
     */
    internal fun buildNoteText(
        features: List<RouteFeature>,
        allFeatures: List<RouteFeature>,
        currentIndex: Int
    ): String {
        val parts = mutableListOf<String>()

        for ((idx, feature) in features.withIndex()) {
            val part = featureToText(feature)
            if (part.isNotBlank()) {
                if (idx > 0) {
                    parts.add("into")
                }
                parts.add(part)
            }
        }

        // Add distance to next significant feature if there's a gap
        val lastFeature = features.last()
        val lastFeatureDist = lastFeature.distanceFromStart + (lastFeature.lengthMeters ?: 0.0)

        // Find next feature after our group
        val allSorted = allFeatures.sortedBy { it.distanceFromStart }
        val nextFeature = allSorted.firstOrNull { it.distanceFromStart > lastFeatureDist + 5 }
        if (nextFeature != null) {
            val gap = nextFeature.distanceFromStart - lastFeatureDist
            if (gap >= config.minDistanceCallMeters && gap <= config.maxDistanceCallMeters) {
                // Round to nearest 10
                val roundedGap = (Math.round(gap / 10.0) * 10).toInt()
                parts.add(roundedGap.toString())
            }
        }

        return parts.joinToString(" ")
    }

    /**
     * Converts a single route feature into rally pace note text.
     */
    internal fun featureToText(feature: RouteFeature): String {
        return when (feature.type) {
            FeatureType.CURVE -> {
                val dir = directionText(feature.direction)
                val sev = feature.severity?.toString() ?: "3"
                val mod = modifierText(feature.modifier)
                val length = lengthModifier(feature.lengthMeters)
                listOfNotNull(dir, sev, length, mod).joinToString(" ")
            }

            FeatureType.HAIRPIN -> {
                val dir = directionText(feature.direction)
                val mod = modifierText(feature.modifier)
                listOfNotNull("Hairpin", dir, mod).joinToString(" ")
            }

            FeatureType.CHICANE -> {
                val dir = directionText(feature.direction)
                val oppositeDir = if (feature.direction == TurnDirection.LEFT) "Right" else "Left"
                "Chicane $dir $oppositeDir"
            }

            FeatureType.S_BEND -> "S-Bend"

            FeatureType.STRAIGHT -> {
                val length = feature.lengthMeters
                if (length != null && length >= config.longStraightMeters) {
                    val rounded = (Math.round(length / 10.0) * 10).toInt()
                    "Straight $rounded"
                } else {
                    "" // Short straights are just gaps
                }
            }

            FeatureType.CREST -> {
                val severity = if (feature.gradientPercent != null && Math.abs(feature.gradientPercent) > 8) {
                    " big"
                } else ""
                "Crest$severity"
            }

            FeatureType.DIP -> "Dip"

            FeatureType.CLIMB -> {
                val steep = if (feature.modifier == FeatureModifier.STEEP) "steep " else ""
                val length = feature.lengthMeters
                if (length != null && length > 200) {
                    val rounded = (Math.round(length / 50.0) * 50).toInt()
                    "${steep}Climb $rounded"
                } else {
                    "${steep}Climb"
                }
            }

            FeatureType.DESCENT -> {
                val steep = if (feature.modifier == FeatureModifier.STEEP) "steep " else ""
                val length = feature.lengthMeters
                if (length != null && length > 200) {
                    val rounded = (Math.round(length / 50.0) * 50).toInt()
                    "${steep}Descent $rounded"
                } else {
                    "${steep}Descent"
                }
            }

            FeatureType.BRIDGE -> "Bridge"
            FeatureType.JUNCTION -> "Junction"
            FeatureType.START -> "Start"
            FeatureType.FINISH -> "Finish"
        }
    }

    private fun directionText(direction: TurnDirection?): String {
        return when (direction) {
            TurnDirection.LEFT -> "Left"
            TurnDirection.RIGHT -> "Right"
            null -> ""
        }
    }

    private fun modifierText(modifier: FeatureModifier?): String? {
        return when (modifier) {
            FeatureModifier.TIGHTENS -> "tightens"
            FeatureModifier.OPENS -> "opens"
            FeatureModifier.OVER_CREST -> "over crest"
            FeatureModifier.OVER_BRIDGE -> "over bridge"
            FeatureModifier.CAUTION -> "caution"
            FeatureModifier.STEEP -> "steep"
            FeatureModifier.SUDDEN -> "sudden"
            FeatureModifier.FLAT_OUT -> "flat out"
            FeatureModifier.INTO -> null // handled by grouping
            FeatureModifier.LONG -> "long"
            FeatureModifier.SHORT -> null
            null -> null
        }
    }

    private fun lengthModifier(lengthMeters: Double?): String? {
        if (lengthMeters == null) return null
        return when {
            lengthMeters > config.longCurveMeters -> "long"
            else -> null
        }
    }

    private fun isLinkableFeature(feature: RouteFeature): Boolean {
        return feature.type in setOf(
            FeatureType.CURVE, FeatureType.HAIRPIN, FeatureType.CHICANE,
            FeatureType.CREST, FeatureType.DIP
        )
    }

    private fun determinePriority(features: List<RouteFeature>): NotePriority {
        val hasHairpin = features.any { it.type == FeatureType.HAIRPIN }
        val hasSevereCurve = features.any { (it.severity ?: 0) >= 5 }
        val hasCrest = features.any { it.type == FeatureType.CREST }
        val hasCaution = features.any { it.modifier == FeatureModifier.CAUTION }

        return when {
            hasCaution -> NotePriority.CRITICAL
            hasHairpin || hasSevereCurve -> NotePriority.CRITICAL
            hasCrest -> NotePriority.NORMAL
            else -> NotePriority.NORMAL
        }
    }

    private fun determineCallAhead(features: List<RouteFeature>, priority: NotePriority): Double {
        return when (priority) {
            NotePriority.CRITICAL -> config.criticalCallAheadMeters
            NotePriority.NORMAL -> config.normalCallAheadMeters
            NotePriority.INFO -> config.infoCallAheadMeters
        }
    }
}

/**
 * Configuration for pace note generation.
 */
data class PaceNoteConfig(
    /** Maximum gap in meters between features to link them with "into" */
    val intoGapMeters: Double = 20.0,
    /** Minimum distance to call between features in meters */
    val minDistanceCallMeters: Double = 30.0,
    /** Maximum distance to call between features in meters */
    val maxDistanceCallMeters: Double = 500.0,
    /** Threshold for a "long" straight in meters */
    val longStraightMeters: Double = 150.0,
    /** Threshold for a "long" curve in meters */
    val longCurveMeters: Double = 80.0,
    /** Call-ahead distance for critical notes (meters) */
    val criticalCallAheadMeters: Double = 150.0,
    /** Call-ahead distance for normal notes (meters) */
    val normalCallAheadMeters: Double = 100.0,
    /** Call-ahead distance for info notes (meters) */
    val infoCallAheadMeters: Double = 80.0
)
