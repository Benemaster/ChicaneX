package com.chicanex.gpx

import com.chicanex.model.GpsPoint
import java.io.InputStream
import javax.xml.parsers.DocumentBuilderFactory

/**
 * Parses GPX (GPS Exchange Format) files to extract GPS waypoints for route analysis.
 *
 * Supports GPX 1.0 and 1.1 formats. Extracts track points (<trkpt>) with
 * latitude, longitude, and optional elevation data.
 *
 * Usage:
 * ```
 * val parser = GpxParser()
 * val waypoints = parser.parse(inputStream)
 * ```
 */
class GpxParser {

    /**
     * Parses a GPX file from an InputStream and returns the list of GPS track points.
     *
     * @param inputStream The GPX file input stream
     * @return List of GpsPoint objects extracted from the track
     * @throws GpxParseException if the file cannot be parsed
     */
    fun parse(inputStream: InputStream): GpxParseResult {
        return try {
            val factory = DocumentBuilderFactory.newInstance()
            // Disable external entities to prevent XXE attacks
            factory.setFeature("http://apache.org/xml/features/disallow-doctype-decl", true)
            factory.setFeature("http://xml.org/sax/features/external-general-entities", false)
            factory.setFeature("http://xml.org/sax/features/external-parameter-entities", false)

            val builder = factory.newDocumentBuilder()
            val document = builder.parse(inputStream)
            document.documentElement.normalize()

            val trackName = extractTrackName(document)
            val waypoints = extractTrackPoints(document)

            if (waypoints.isEmpty()) {
                // Try route points as fallback
                val routePoints = extractRoutePoints(document)
                if (routePoints.isEmpty()) {
                    throw GpxParseException("No track points or route points found in GPX file")
                }
                GpxParseResult(name = trackName, waypoints = routePoints)
            } else {
                GpxParseResult(name = trackName, waypoints = waypoints)
            }
        } catch (e: GpxParseException) {
            throw e
        } catch (e: Exception) {
            throw GpxParseException("Failed to parse GPX file: ${e.message}", e)
        }
    }

    private fun extractTrackName(document: org.w3c.dom.Document): String {
        // Try trk/name first
        val trkElements = document.getElementsByTagName("trk")
        if (trkElements.length > 0) {
            val trk = trkElements.item(0)
            val children = trk.childNodes
            for (i in 0 until children.length) {
                val child = children.item(i)
                if (child.nodeName == "name") {
                    return child.textContent?.trim() ?: "Unnamed Route"
                }
            }
        }

        // Try metadata/name
        val metaElements = document.getElementsByTagName("metadata")
        if (metaElements.length > 0) {
            val meta = metaElements.item(0)
            val children = meta.childNodes
            for (i in 0 until children.length) {
                val child = children.item(i)
                if (child.nodeName == "name") {
                    return child.textContent?.trim() ?: "Unnamed Route"
                }
            }
        }

        return "Unnamed Route"
    }

    private fun extractTrackPoints(document: org.w3c.dom.Document): List<GpsPoint> {
        val points = mutableListOf<GpsPoint>()
        val trkptElements = document.getElementsByTagName("trkpt")

        for (i in 0 until trkptElements.length) {
            val element = trkptElements.item(i)
            val point = parsePointElement(element)
            if (point != null) {
                points.add(point)
            }
        }

        return points
    }

    private fun extractRoutePoints(document: org.w3c.dom.Document): List<GpsPoint> {
        val points = mutableListOf<GpsPoint>()
        val rteptElements = document.getElementsByTagName("rtept")

        for (i in 0 until rteptElements.length) {
            val element = rteptElements.item(i)
            val point = parsePointElement(element)
            if (point != null) {
                points.add(point)
            }
        }

        return points
    }

    private fun parsePointElement(element: org.w3c.dom.Node): GpsPoint? {
        val attributes = element.attributes ?: return null
        val latAttr = attributes.getNamedItem("lat")?.nodeValue?.toDoubleOrNull() ?: return null
        val lonAttr = attributes.getNamedItem("lon")?.nodeValue?.toDoubleOrNull() ?: return null

        var elevation: Double? = null
        val children = element.childNodes
        for (i in 0 until children.length) {
            val child = children.item(i)
            if (child.nodeName == "ele") {
                elevation = child.textContent?.trim()?.toDoubleOrNull()
            }
        }

        return GpsPoint(
            latitude = latAttr,
            longitude = lonAttr,
            elevation = elevation
        )
    }
}

/**
 * Result of parsing a GPX file.
 */
data class GpxParseResult(
    val name: String,
    val waypoints: List<GpsPoint>
)

/**
 * Exception thrown when GPX parsing fails.
 */
class GpxParseException(message: String, cause: Throwable? = null) : Exception(message, cause)
