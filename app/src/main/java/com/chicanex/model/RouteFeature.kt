package com.chicanex.model

/**
 * Represents a detected feature along the route.
 * Each feature has a type, position, and metadata describing
 * the characteristic in rally co-driver terminology.
 */
data class RouteFeature(
    /** The type of route feature */
    val type: FeatureType,
    /** Distance from route start in meters */
    val distanceFromStart: Double,
    /** The GPS position where this feature occurs */
    val position: GpsPoint,
    /** Severity/grade for curves (1=gentle, 6=very sharp). Null for non-curve features */
    val severity: Int? = null,
    /** Direction of curves */
    val direction: TurnDirection? = null,
    /** Length of the feature in meters (e.g., length of a straight, length of a curve) */
    val lengthMeters: Double? = null,
    /** Gradient percentage (positive = uphill, negative = downhill) */
    val gradientPercent: Double? = null,
    /** Additional modifier for the feature */
    val modifier: FeatureModifier? = null,
    /** Estimated radius of curvature in meters (for curves) */
    val radiusMeters: Double? = null
)

/**
 * Types of route features detected during analysis.
 */
enum class FeatureType {
    /** A turn/curve in the road */
    CURVE,
    /** A straight section */
    STRAIGHT,
    /** A hairpin turn (very tight, 180-degree turn) */
    HAIRPIN,
    /** A crest (top of a hill) */
    CREST,
    /** A dip/compression in the road */
    DIP,
    /** Elevation climb (sustained uphill) */
    CLIMB,
    /** Elevation descent (sustained downhill) */
    DESCENT,
    /** A chicane (quick left-right or right-left combination) */
    CHICANE,
    /** A series of linked curves (S-bends) */
    S_BEND,
    /** A junction or intersection */
    JUNCTION,
    /** A bridge */
    BRIDGE,
    /** Start of route */
    START,
    /** End of route */
    FINISH
}

/**
 * Direction of a turn.
 */
enum class TurnDirection {
    LEFT,
    RIGHT
}

/**
 * Additional modifiers that can be applied to route features.
 */
enum class FeatureModifier {
    /** Curve tightens as you go through it */
    TIGHTENS,
    /** Curve opens up as you go through it */
    OPENS,
    /** Long version of the feature */
    LONG,
    /** Short/quick version */
    SHORT,
    /** Feature followed by a sudden change */
    SUDDEN,
    /** Very steep gradient */
    STEEP,
    /** Feature occurs over a crest */
    OVER_CREST,
    /** Feature is on a bridge */
    OVER_BRIDGE,
    /** Deceptive / tricky feature (e.g., looks easier than it is) */
    CAUTION,
    /** Feature followed immediately by another with no gap */
    INTO,
    /** Keep pace (don't brake) through this section */
    FLAT_OUT
}
