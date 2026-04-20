package com.chicanex.model

/**
 * Represents a single GPS waypoint with latitude, longitude, and optional elevation.
 */
data class GpsPoint(
    val latitude: Double,
    val longitude: Double,
    val elevation: Double? = null
) {
    /**
     * Calculates the distance in meters to another GPS point using the Haversine formula.
     */
    fun distanceTo(other: GpsPoint): Double {
        val earthRadius = 6_371_000.0 // meters
        val dLat = Math.toRadians(other.latitude - latitude)
        val dLon = Math.toRadians(other.longitude - longitude)
        val a = Math.sin(dLat / 2) * Math.sin(dLat / 2) +
                Math.cos(Math.toRadians(latitude)) * Math.cos(Math.toRadians(other.latitude)) *
                Math.sin(dLon / 2) * Math.sin(dLon / 2)
        val c = 2 * Math.atan2(Math.sqrt(a), Math.sqrt(1 - a))
        return earthRadius * c
    }

    /**
     * Calculates the initial bearing (in degrees, 0-360) from this point to another.
     */
    fun bearingTo(other: GpsPoint): Double {
        val lat1 = Math.toRadians(latitude)
        val lat2 = Math.toRadians(other.latitude)
        val dLon = Math.toRadians(other.longitude - longitude)

        val y = Math.sin(dLon) * Math.cos(lat2)
        val x = Math.cos(lat1) * Math.sin(lat2) -
                Math.sin(lat1) * Math.cos(lat2) * Math.cos(dLon)
        val bearing = Math.toDegrees(Math.atan2(y, x))
        return (bearing + 360) % 360
    }

    /**
     * Calculates the elevation difference to another point (positive = uphill).
     * Returns null if either point lacks elevation data.
     */
    fun elevationDifferenceTo(other: GpsPoint): Double? {
        val elev1 = elevation ?: return null
        val elev2 = other.elevation ?: return null
        return elev2 - elev1
    }
}
