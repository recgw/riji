package com.denser.june.presentation.utils

import org.maplibre.android.geometry.LatLng
import org.maplibre.android.maps.Style
import org.maplibre.android.style.layers.CircleLayer
import org.maplibre.android.style.layers.FillLayer
import org.maplibre.android.style.layers.PropertyFactory
import org.maplibre.android.style.sources.GeoJsonSource
import kotlin.math.cos
import kotlin.math.sin

internal const val USER_LOCATION_SOURCE_ID = "june-user-location"
internal const val USER_LOCATION_ACCURACY_SOURCE_ID = "june-user-location-accuracy"
internal const val USER_LOCATION_DOT_LAYER_ID = "june-user-location-dot"
internal const val USER_LOCATION_ACCURACY_LAYER_ID = "june-user-location-accuracy-halo"
internal val USER_LOCATION_BLUE = 0xFF1A73E8.toInt()

/**
 * Draws the "you are here" dot plus the accuracy halo. Layers are created on demand, so
 * the same path works for the generated AMap style and for downloaded styles.
 *
 * @param displayTarget the point in the map's display datum (GCJ-02 on AMap).
 * @param accuracyMeters reported horizontal accuracy; null falls back to a 30 m halo.
 */
internal fun updateUserLocationOnMap(
    style: Style,
    displayTarget: LatLng,
    accuracyMeters: Float?
) {
    ensureUserLocationLayers(style)
    style.getSourceAs<GeoJsonSource>(USER_LOCATION_SOURCE_ID)
        ?.setGeoJson(userLocationPointGeoJson(displayTarget))
    style.getSourceAs<GeoJsonSource>(USER_LOCATION_ACCURACY_SOURCE_ID)
        ?.setGeoJson(userLocationAccuracyGeoJson(displayTarget, accuracyMeters ?: 30f))
}

internal fun ensureUserLocationLayers(style: Style) {
    if (style.getSource(USER_LOCATION_SOURCE_ID) != null) return
    try {
        style.addSource(GeoJsonSource(USER_LOCATION_SOURCE_ID))
        style.addSource(GeoJsonSource(USER_LOCATION_ACCURACY_SOURCE_ID))
        style.addLayer(
            FillLayer(USER_LOCATION_ACCURACY_LAYER_ID, USER_LOCATION_ACCURACY_SOURCE_ID)
                .withProperties(
                    PropertyFactory.fillColor(USER_LOCATION_BLUE),
                    PropertyFactory.fillOpacity(0.15f)
                )
        )
        style.addLayer(
            CircleLayer(USER_LOCATION_DOT_LAYER_ID, USER_LOCATION_SOURCE_ID)
                .withProperties(
                    PropertyFactory.circleRadius(6.5f),
                    PropertyFactory.circleColor(USER_LOCATION_BLUE),
                    PropertyFactory.circleStrokeWidth(2f),
                    PropertyFactory.circleStrokeColor(android.graphics.Color.WHITE)
                )
        )
    } catch (e: Exception) {
        e.printStackTrace()
    }
}

private fun userLocationPointGeoJson(target: LatLng): String =
    """{"type":"Feature","properties":{},"geometry":{"type":"Point","coordinates":[${target.longitude},${target.latitude}]}}"""

/** Builds a 48-point ring from the accuracy radius so the halo scales with zoom. */
private fun userLocationAccuracyGeoJson(target: LatLng, accuracyMeters: Float): String {
    val radius = accuracyMeters.toDouble().coerceIn(5.0, 250.0)
    val latDelta = radius / 111_320.0
    val lonDelta = radius / (111_320.0 * cos(Math.toRadians(target.latitude)).coerceAtLeast(1e-6))
    val ring = (0 until 48).map { step ->
        val angle = 2.0 * Math.PI * step / 48.0
        "[${target.longitude + lonDelta * cos(angle)},${target.latitude + latDelta * sin(angle)}]"
    } + "[${target.longitude + lonDelta},${target.latitude}]"
    return """{"type":"Feature","properties":{},"geometry":{"type":"Polygon","coordinates":[[${ring.joinToString(",")}]]}}"""
}
