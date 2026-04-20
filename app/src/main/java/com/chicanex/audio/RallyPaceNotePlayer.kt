package com.chicanex.audio

import android.content.Context
import android.location.Location
import com.chicanex.model.GpsPoint
import com.chicanex.model.PaceNote

/**
 * RallyPaceNotePlayer manages the playback of pace notes synchronized
 * with the driver's position along the route.
 *
 * It tracks the current position via GPS or simulated advancement,
 * determines which pace note should be called next, and triggers
 * the audio engine at the correct timing (call-ahead distance).
 *
 * Usage:
 * 1. Load pace notes via [loadPaceNotes]
 * 2. Start playback via [start]
 * 3. Feed position updates via [updatePosition] or [advanceByDistance]
 * 4. Stop with [stop]
 */
class RallyPaceNotePlayer(
    private val audioEngine: RallyAudioEngine
) {
    private var paceNotes: List<PaceNote> = emptyList()
    private var currentNoteIndex = 0
    private var currentDistanceMeters = 0.0
    private var isPlaying = false

    /** Callback when a note is about to be spoken */
    var onNoteTriggered: ((PaceNote, Int) -> Unit)? = null

    /**
     * Loads pace notes for playback.
     */
    fun loadPaceNotes(notes: List<PaceNote>) {
        paceNotes = notes.sortedBy { it.distanceFromStart }
        currentNoteIndex = 0
        currentDistanceMeters = 0.0
    }

    /**
     * Starts pace note playback from the current position.
     */
    fun start() {
        isPlaying = true
        checkAndPlayNotes()
    }

    /**
     * Stops pace note playback.
     */
    fun stop() {
        isPlaying = false
        audioEngine.stop()
    }

    /**
     * Resets playback to the beginning.
     */
    fun reset() {
        stop()
        currentNoteIndex = 0
        currentDistanceMeters = 0.0
    }

    /**
     * Updates the player with a new GPS position.
     * The player calculates the distance along the route and triggers
     * any pace notes that should be called at this position.
     *
     * @param location The current GPS location from Android's Location API
     * @param routeWaypoints The original route waypoints (for distance calculation)
     */
    fun updatePosition(location: Location, routeWaypoints: List<GpsPoint>) {
        if (!isPlaying) return

        val currentPoint = GpsPoint(location.latitude, location.longitude, location.altitude)
        val distance = estimateDistanceAlongRoute(currentPoint, routeWaypoints)
        currentDistanceMeters = distance
        checkAndPlayNotes()
    }

    /**
     * Advances the simulated position by a given distance.
     * Useful for testing or simulated playback without GPS.
     *
     * @param meters Distance to advance in meters
     */
    fun advanceByDistance(meters: Double) {
        if (!isPlaying) return
        currentDistanceMeters += meters
        checkAndPlayNotes()
    }

    /**
     * Sets the playback position to a specific distance.
     */
    fun seekToDistance(distanceMeters: Double) {
        currentDistanceMeters = distanceMeters
        // Find the appropriate note index
        currentNoteIndex = paceNotes.indexOfFirst {
            it.distanceFromStart - it.callAheadMeters > distanceMeters
        }.coerceAtLeast(0)

        if (isPlaying) {
            checkAndPlayNotes()
        }
    }

    /**
     * Checks if any pace notes should be triggered at the current position
     * and plays them via the audio engine.
     */
    private fun checkAndPlayNotes() {
        while (currentNoteIndex < paceNotes.size) {
            val note = paceNotes[currentNoteIndex]
            val triggerDistance = note.distanceFromStart - note.callAheadMeters

            if (currentDistanceMeters >= triggerDistance) {
                audioEngine.speakPaceNote(note)
                onNoteTriggered?.invoke(note, currentNoteIndex)
                currentNoteIndex++
            } else {
                break
            }
        }
    }

    /**
     * Estimates the distance along the route for a given GPS point
     * by finding the closest route segment.
     */
    private fun estimateDistanceAlongRoute(
        point: GpsPoint,
        waypoints: List<GpsPoint>
    ): Double {
        if (waypoints.isEmpty()) return 0.0

        var minDist = Double.MAX_VALUE
        var closestIndex = 0
        var cumulativeDistance = 0.0
        val distances = mutableListOf(0.0)

        // Build cumulative distances
        for (i in 1 until waypoints.size) {
            cumulativeDistance += waypoints[i - 1].distanceTo(waypoints[i])
            distances.add(cumulativeDistance)
        }

        // Find closest waypoint
        for (i in waypoints.indices) {
            val dist = point.distanceTo(waypoints[i])
            if (dist < minDist) {
                minDist = dist
                closestIndex = i
            }
        }

        return distances[closestIndex]
    }

    /** Current playback distance in meters */
    val currentDistance: Double get() = currentDistanceMeters

    /** Current note index */
    val currentIndex: Int get() = currentNoteIndex

    /** Total number of pace notes */
    val totalNotes: Int get() = paceNotes.size

    /** Whether playback is active */
    val isActive: Boolean get() = isPlaying

    /** Progress as a fraction (0.0 to 1.0) */
    val progress: Float
        get() {
            val lastNote = paceNotes.lastOrNull() ?: return 0f
            return (currentDistanceMeters / lastNote.distanceFromStart).toFloat().coerceIn(0f, 1f)
        }
}
