package com.chicanex.model

/**
 * Represents a pace note — a single spoken instruction for the rally driver.
 * Pace notes are generated from route features and spoken via TTS.
 */
data class PaceNote(
    /** Distance from route start in meters where this note should be spoken */
    val distanceFromStart: Double,
    /** The pace note text in rally co-driver format (e.g., "Left 3 long into Right 5") */
    val noteText: String,
    /** The route feature(s) this note describes */
    val features: List<RouteFeature>,
    /** Priority level for timing — higher priority notes are spoken earlier */
    val priority: NotePriority = NotePriority.NORMAL,
    /** How far ahead (in meters) this note should be called before reaching the feature */
    val callAheadMeters: Double = DEFAULT_CALL_AHEAD
) {
    companion object {
        const val DEFAULT_CALL_AHEAD = 100.0 // meters ahead
    }
}

/**
 * Priority levels for pace notes, affecting when they are spoken.
 */
enum class NotePriority {
    /** Safety-critical notes (e.g., caution, don't cut) */
    CRITICAL,
    /** Standard pace notes (turns, straights) */
    NORMAL,
    /** Supplementary info (surface changes, landmarks) */
    INFO
}
