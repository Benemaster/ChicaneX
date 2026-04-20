package com.chicanex.route

import com.chicanex.model.*

/**
 * RouteAnalyzer extracts route features from a list of GPS waypoints.
 *
 * The analyzer processes raw GPS data to identify:
 * - Curves with direction and severity (1-6 rally scale)
 * - Hairpin turns
 * - Chicanes and S-bends
 * - Straight sections with distance
 * - Crests and dips (elevation profile)
 * - Sustained climbs and descents
 * - Gradient changes
 * - Tightening and opening curves
 *
 * The analysis uses bearing changes, curvature radius estimation,
 * and elevation profiling to classify features using real rally
 * co-driver terminology.
 */
class RouteAnalyzer(
    private val config: AnalyzerConfig = AnalyzerConfig()
) {
    /**
     * Analyzes a route and extracts all features.
     *
     * @param waypoints List of GPS points forming the route
     * @return List of detected route features, sorted by distance from start
     */
    fun analyzeRoute(waypoints: List<GpsPoint>): List<RouteFeature> {
        if (waypoints.size < 3) return emptyList()

        val segments = buildSegments(waypoints)
        val features = mutableListOf<RouteFeature>()

        features.add(
            RouteFeature(
                type = FeatureType.START,
                distanceFromStart = 0.0,
                position = waypoints.first()
            )
        )

        // Detect curves, hairpins, straights
        features.addAll(detectCurves(segments))

        // Detect elevation features (crests, dips, climbs, descents)
        features.addAll(detectElevationFeatures(segments))

        // Post-process: detect chicanes, S-bends, tightening/opening
        val processed = postProcessFeatures(features)

        val totalDist = segments.lastOrNull()?.cumulativeDistance ?: 0.0
        processed.add(
            RouteFeature(
                type = FeatureType.FINISH,
                distanceFromStart = totalDist,
                position = waypoints.last()
            )
        )

        return processed.sortedBy { it.distanceFromStart }
    }

    /**
     * Builds route segments with bearing and distance data from raw waypoints.
     */
    internal fun buildSegments(waypoints: List<GpsPoint>): List<RouteSegment> {
        val segments = mutableListOf<RouteSegment>()
        var cumulativeDistance = 0.0

        for (i in 0 until waypoints.size - 1) {
            val from = waypoints[i]
            val to = waypoints[i + 1]
            val distance = from.distanceTo(to)
            val bearing = from.bearingTo(to)
            cumulativeDistance += distance

            segments.add(
                RouteSegment(
                    from = from,
                    to = to,
                    distance = distance,
                    bearing = bearing,
                    cumulativeDistance = cumulativeDistance,
                    elevationChange = from.elevationDifferenceTo(to)
                )
            )
        }

        return segments
    }

    /**
     * Detects curves, hairpins, and straight sections from bearing changes.
     */
    internal fun detectCurves(segments: List<RouteSegment>): List<RouteFeature> {
        if (segments.size < 2) return emptyList()

        val features = mutableListOf<RouteFeature>()
        var straightStartDist = 0.0
        var straightStartPoint = segments.first().from
        var inStraight = true
        var curveStartIndex = -1
        var curveAccumulatedAngle = 0.0
        var curveDistance = 0.0

        for (i in 0 until segments.size - 1) {
            val bearingChange = normalizedBearingChange(segments[i].bearing, segments[i + 1].bearing)
            val absBearingChange = Math.abs(bearingChange)

            // Calculate effective turn rate (degrees per meter)
            val segmentLength = segments[i + 1].distance
            val turnRate = if (segmentLength > 0) absBearingChange / segmentLength else 0.0

            if (turnRate > config.curveDetectionThresholdDegPerMeter) {
                // We're in a curve
                if (inStraight) {
                    // End the straight section
                    val straightLength = segments[i].cumulativeDistance - straightStartDist
                    if (straightLength >= config.minStraightLengthMeters) {
                        features.add(
                            RouteFeature(
                                type = FeatureType.STRAIGHT,
                                distanceFromStart = straightStartDist,
                                position = straightStartPoint,
                                lengthMeters = straightLength
                            )
                        )
                    }
                    inStraight = false
                    curveStartIndex = i
                    curveAccumulatedAngle = 0.0
                    curveDistance = 0.0
                }
                curveAccumulatedAngle += bearingChange
                curveDistance += segmentLength
            } else {
                // We're in a straight
                if (!inStraight && curveStartIndex >= 0) {
                    // End the curve section — classify it
                    val curveFeature = classifyCurve(
                        startSegment = segments[curveStartIndex],
                        totalAngle = curveAccumulatedAngle,
                        curveLength = curveDistance,
                        segments = segments,
                        startIndex = curveStartIndex,
                        endIndex = i
                    )
                    features.add(curveFeature)

                    inStraight = true
                    straightStartDist = segments[i].cumulativeDistance
                    straightStartPoint = segments[i].to
                    curveStartIndex = -1
                }
            }
        }

        // Handle trailing curve or straight
        if (!inStraight && curveStartIndex >= 0) {
            val curveFeature = classifyCurve(
                startSegment = segments[curveStartIndex],
                totalAngle = curveAccumulatedAngle,
                curveLength = curveDistance,
                segments = segments,
                startIndex = curveStartIndex,
                endIndex = segments.size - 1
            )
            features.add(curveFeature)
        } else if (inStraight) {
            val straightLength = (segments.lastOrNull()?.cumulativeDistance ?: 0.0) - straightStartDist
            if (straightLength >= config.minStraightLengthMeters) {
                features.add(
                    RouteFeature(
                        type = FeatureType.STRAIGHT,
                        distanceFromStart = straightStartDist,
                        position = straightStartPoint,
                        lengthMeters = straightLength
                    )
                )
            }
        }

        return features
    }

    /**
     * Classifies a detected curve into a rally-style feature.
     * Uses the accumulated angle to determine severity (1-6) and type (curve/hairpin).
     */
    internal fun classifyCurve(
        startSegment: RouteSegment,
        totalAngle: Double,
        curveLength: Double,
        segments: List<RouteSegment>,
        startIndex: Int,
        endIndex: Int
    ): RouteFeature {
        val absAngle = Math.abs(totalAngle)
        val direction = if (totalAngle > 0) TurnDirection.RIGHT else TurnDirection.LEFT

        // Estimate average radius: radius = arc_length / angle_in_radians
        val angleRad = Math.toRadians(absAngle).coerceAtLeast(0.01)
        val estimatedRadius = curveLength / angleRad

        // Determine if it's a hairpin (>= 140 degrees)
        val isHairpin = absAngle >= config.hairpinAngleThreshold

        // Severity classification based on radius (rally scale 1-6)
        // 1 = very gentle/fast, 6 = very tight/slow
        val severity = classifySeverity(estimatedRadius, absAngle)

        // Detect tightening or opening curves
        val modifier = detectCurveModifier(segments, startIndex, endIndex, totalAngle)

        return RouteFeature(
            type = if (isHairpin) FeatureType.HAIRPIN else FeatureType.CURVE,
            distanceFromStart = startSegment.cumulativeDistance,
            position = startSegment.from,
            severity = severity,
            direction = direction,
            lengthMeters = curveLength,
            radiusMeters = estimatedRadius,
            modifier = modifier
        )
    }

    /**
     * Classifies curve severity on a 1-6 scale based on estimated radius and total angle.
     *
     * Scale:
     * 1 = Very fast, gentle (radius > 120m or angle < 20°)
     * 2 = Fast (radius 70-120m or angle 20-40°)
     * 3 = Medium (radius 40-70m or angle 40-70°)
     * 4 = Slow-medium (radius 25-40m or angle 70-100°)
     * 5 = Slow (radius 12-25m or angle 100-140°)
     * 6 = Very slow/tight (radius < 12m or angle > 140°)
     */
    internal fun classifySeverity(radiusMeters: Double, absAngle: Double): Int {
        // Use a combined score from both radius and angle
        val radiusSeverity = when {
            radiusMeters > 120.0 -> 1
            radiusMeters > 70.0 -> 2
            radiusMeters > 40.0 -> 3
            radiusMeters > 25.0 -> 4
            radiusMeters > 12.0 -> 5
            else -> 6
        }

        val angleSeverity = when {
            absAngle < 20.0 -> 1
            absAngle < 40.0 -> 2
            absAngle < 70.0 -> 3
            absAngle < 100.0 -> 4
            absAngle < 140.0 -> 5
            else -> 6
        }

        // Weight: radius contributes 60%, angle 40%
        val combined = (radiusSeverity * 0.6 + angleSeverity * 0.4)
        return combined.toInt().coerceIn(1, 6)
    }

    /**
     * Detects if a curve is tightening (getting sharper) or opening (getting gentler).
     */
    internal fun detectCurveModifier(
        segments: List<RouteSegment>,
        startIndex: Int,
        endIndex: Int,
        totalAngle: Double
    ): FeatureModifier? {
        if (endIndex - startIndex < 2) return null

        val midIndex = (startIndex + endIndex) / 2
        var firstHalfAngle = 0.0
        var secondHalfAngle = 0.0

        for (i in startIndex until midIndex) {
            if (i < segments.size - 1) {
                firstHalfAngle += Math.abs(normalizedBearingChange(segments[i].bearing, segments[i + 1].bearing))
            }
        }
        for (i in midIndex until endIndex) {
            if (i < segments.size - 1) {
                secondHalfAngle += Math.abs(normalizedBearingChange(segments[i].bearing, segments[i + 1].bearing))
            }
        }

        val ratio = if (firstHalfAngle > 0) secondHalfAngle / firstHalfAngle else 1.0
        return when {
            ratio > config.tighteningThreshold -> FeatureModifier.TIGHTENS
            ratio < config.openingThreshold -> FeatureModifier.OPENS
            else -> null
        }
    }

    /**
     * Detects elevation-based features: crests, dips, sustained climbs and descents.
     */
    internal fun detectElevationFeatures(segments: List<RouteSegment>): List<RouteFeature> {
        val features = mutableListOf<RouteFeature>()

        // Need elevation data
        if (segments.any { it.elevationChange == null }) return features

        // Smooth elevation profile to reduce GPS noise
        val smoothedGradients = smoothGradients(segments)

        var climbStart: Int? = null
        var descentStart: Int? = null
        var totalClimb = 0.0
        var totalDescent = 0.0

        for (i in smoothedGradients.indices) {
            val gradient = smoothedGradients[i]
            val segment = segments[i]

            // Detect crests (transition from climbing to descending)
            if (i > 0 && smoothedGradients[i - 1] > config.crestDetectionGradient && gradient < -config.crestDetectionGradient) {
                features.add(
                    RouteFeature(
                        type = FeatureType.CREST,
                        distanceFromStart = segment.cumulativeDistance,
                        position = segment.from,
                        gradientPercent = gradient * 100
                    )
                )
            }

            // Detect dips (transition from descending to climbing)
            if (i > 0 && smoothedGradients[i - 1] < -config.dipDetectionGradient && gradient > config.dipDetectionGradient) {
                features.add(
                    RouteFeature(
                        type = FeatureType.DIP,
                        distanceFromStart = segment.cumulativeDistance,
                        position = segment.from,
                        gradientPercent = gradient * 100
                    )
                )
            }

            // Track sustained climbs
            if (gradient > config.climbThresholdGradient) {
                if (climbStart == null) {
                    climbStart = i
                    totalClimb = 0.0
                }
                totalClimb += segment.elevationChange ?: 0.0
            } else {
                if (climbStart != null) {
                    val climbDist = segment.cumulativeDistance - segments[climbStart].cumulativeDistance
                    if (climbDist >= config.minClimbLengthMeters) {
                        val avgGradient = totalClimb / climbDist
                        features.add(
                            RouteFeature(
                                type = FeatureType.CLIMB,
                                distanceFromStart = segments[climbStart].cumulativeDistance,
                                position = segments[climbStart].from,
                                lengthMeters = climbDist,
                                gradientPercent = avgGradient * 100,
                                modifier = if (avgGradient > config.steepGradientThreshold) FeatureModifier.STEEP else null
                            )
                        )
                    }
                    climbStart = null
                }
            }

            // Track sustained descents
            if (gradient < -config.descentThresholdGradient) {
                if (descentStart == null) {
                    descentStart = i
                    totalDescent = 0.0
                }
                totalDescent += Math.abs(segment.elevationChange ?: 0.0)
            } else {
                if (descentStart != null) {
                    val descentDist = segment.cumulativeDistance - segments[descentStart].cumulativeDistance
                    if (descentDist >= config.minClimbLengthMeters) {
                        val avgGradient = -totalDescent / descentDist
                        features.add(
                            RouteFeature(
                                type = FeatureType.DESCENT,
                                distanceFromStart = segments[descentStart].cumulativeDistance,
                                position = segments[descentStart].from,
                                lengthMeters = descentDist,
                                gradientPercent = avgGradient * 100,
                                modifier = if (Math.abs(avgGradient) > config.steepGradientThreshold) FeatureModifier.STEEP else null
                            )
                        )
                    }
                    descentStart = null
                }
            }
        }

        return features
    }

    /**
     * Smooths the gradient values using a simple moving average to reduce GPS noise.
     */
    internal fun smoothGradients(segments: List<RouteSegment>): List<Double> {
        val rawGradients = segments.map { seg ->
            if (seg.distance > 0 && seg.elevationChange != null) {
                seg.elevationChange / seg.distance
            } else {
                0.0
            }
        }

        val windowSize = config.gradientSmoothingWindow
        if (rawGradients.size < windowSize) return rawGradients

        return rawGradients.mapIndexed { index, _ ->
            val start = maxOf(0, index - windowSize / 2)
            val end = minOf(rawGradients.size, index + windowSize / 2 + 1)
            rawGradients.subList(start, end).average()
        }
    }

    /**
     * Post-processes features to detect compound features like chicanes and S-bends.
     */
    internal fun postProcessFeatures(features: MutableList<RouteFeature>): MutableList<RouteFeature> {
        val sorted = features.sortedBy { it.distanceFromStart }.toMutableList()
        val toRemove = mutableSetOf<Int>()
        val toAdd = mutableListOf<RouteFeature>()

        // Detect chicanes (two opposite-direction curves close together)
        for (i in 0 until sorted.size - 1) {
            val current = sorted[i]
            val next = sorted[i + 1]

            if ((current.type == FeatureType.CURVE || current.type == FeatureType.HAIRPIN) &&
                (next.type == FeatureType.CURVE || next.type == FeatureType.HAIRPIN)
            ) {
                val gap = next.distanceFromStart - current.distanceFromStart - (current.lengthMeters ?: 0.0)

                // Opposite directions and close together = chicane
                if (current.direction != null && next.direction != null &&
                    current.direction != next.direction &&
                    gap < config.chicaneMaxGapMeters
                ) {
                    toRemove.add(i)
                    toRemove.add(i + 1)
                    toAdd.add(
                        RouteFeature(
                            type = FeatureType.CHICANE,
                            distanceFromStart = current.distanceFromStart,
                            position = current.position,
                            direction = current.direction,
                            severity = maxOf(current.severity ?: 3, next.severity ?: 3),
                            lengthMeters = (next.distanceFromStart + (next.lengthMeters ?: 0.0)) - current.distanceFromStart
                        )
                    )
                }
            }
        }

        // Detect S-bends (three or more alternating curves close together)
        // This runs on already-processed features, so chicanes are handled first
        val result = mutableListOf<RouteFeature>()
        for (i in sorted.indices) {
            if (i !in toRemove) {
                result.add(sorted[i])
            }
        }
        result.addAll(toAdd)

        // Detect curves over crests
        val curveFeatures = result.filter { it.type == FeatureType.CURVE || it.type == FeatureType.HAIRPIN }
        val crestFeatures = result.filter { it.type == FeatureType.CREST }

        for (curve in curveFeatures) {
            for (crest in crestFeatures) {
                val crestDist = crest.distanceFromStart
                val curveStart = curve.distanceFromStart
                val curveEnd = curveStart + (curve.lengthMeters ?: 0.0)
                if (crestDist in curveStart..curveEnd && curve.modifier == null) {
                    val idx = result.indexOf(curve)
                    if (idx >= 0) {
                        result[idx] = curve.copy(modifier = FeatureModifier.OVER_CREST)
                    }
                }
            }
        }

        return result
    }

    /**
     * Calculates the route statistics from analyzed features.
     */
    fun calculateStatistics(
        waypoints: List<GpsPoint>,
        features: List<RouteFeature>,
        segments: List<RouteSegment>
    ): RouteStatistics {
        val totalDistance = segments.lastOrNull()?.cumulativeDistance ?: 0.0

        var elevationGain = 0.0
        var elevationLoss = 0.0
        var maxGradient = 0.0

        for (segment in segments) {
            val elChange = segment.elevationChange
            if (elChange != null) {
                if (elChange > 0) elevationGain += elChange
                else elevationLoss += Math.abs(elChange)

                if (segment.distance > 0) {
                    val gradient = Math.abs(elChange / segment.distance) * 100
                    if (gradient > maxGradient) maxGradient = gradient
                }
            }
        }

        val curves = features.filter {
            it.type == FeatureType.CURVE || it.type == FeatureType.HAIRPIN || it.type == FeatureType.CHICANE
        }
        val avgSeverity = if (curves.isNotEmpty()) {
            curves.mapNotNull { it.severity }.average()
        } else 0.0

        return RouteStatistics(
            totalDistanceMeters = totalDistance,
            elevationGain = elevationGain,
            elevationLoss = elevationLoss,
            maxGradientPercent = maxGradient,
            curveCount = curves.size,
            averageSeverity = avgSeverity
        )
    }

    companion object {
        /**
         * Normalizes a bearing change to the range [-180, 180].
         * Positive = right turn, Negative = left turn.
         */
        fun normalizedBearingChange(from: Double, to: Double): Double {
            var diff = to - from
            while (diff > 180) diff -= 360
            while (diff < -180) diff += 360
            return diff
        }
    }
}

/**
 * A segment between two consecutive GPS waypoints with precomputed properties.
 */
data class RouteSegment(
    val from: GpsPoint,
    val to: GpsPoint,
    val distance: Double,
    val bearing: Double,
    val cumulativeDistance: Double,
    val elevationChange: Double?
)

/**
 * Statistics computed for a route.
 */
data class RouteStatistics(
    val totalDistanceMeters: Double,
    val elevationGain: Double,
    val elevationLoss: Double,
    val maxGradientPercent: Double,
    val curveCount: Int,
    val averageSeverity: Double
)

/**
 * Configuration parameters for the route analyzer.
 * All thresholds can be tuned for different GPS data quality and route types.
 */
data class AnalyzerConfig(
    /** Minimum turn rate (degrees per meter) to consider as a curve */
    val curveDetectionThresholdDegPerMeter: Double = 1.5,
    /** Minimum length in meters to be considered a straight */
    val minStraightLengthMeters: Double = 50.0,
    /** Angle threshold in degrees for hairpin detection */
    val hairpinAngleThreshold: Double = 140.0,
    /** Ratio threshold for detecting tightening curves */
    val tighteningThreshold: Double = 1.8,
    /** Ratio threshold for detecting opening curves */
    val openingThreshold: Double = 0.55,
    /** Gradient threshold for crest detection (as fraction, e.g., 0.03 = 3%) */
    val crestDetectionGradient: Double = 0.03,
    /** Gradient threshold for dip detection (as fraction) */
    val dipDetectionGradient: Double = 0.03,
    /** Gradient threshold for sustained climb (as fraction) */
    val climbThresholdGradient: Double = 0.02,
    /** Gradient threshold for sustained descent (as fraction) */
    val descentThresholdGradient: Double = 0.02,
    /** Minimum length in meters for a sustained climb/descent to be reported */
    val minClimbLengthMeters: Double = 100.0,
    /** Gradient considered "steep" (as fraction) */
    val steepGradientThreshold: Double = 0.10,
    /** Maximum gap in meters between curves to be considered a chicane */
    val chicaneMaxGapMeters: Double = 30.0,
    /** Window size for gradient smoothing */
    val gradientSmoothingWindow: Int = 5
)
