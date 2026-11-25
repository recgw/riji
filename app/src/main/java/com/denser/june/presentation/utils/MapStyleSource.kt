package com.denser.june.presentation.utils

import androidx.compose.runtime.Immutable
import org.maplibre.android.maps.MapLibreMap
import org.maplibre.android.maps.Style
import org.maplibre.compose.style.BaseStyle

/**
 * A style that MapLibre can render, either loaded from a URL (remote styles) or
 * given as an inline JSON document (used for raster providers like AMap that
 * have no hosted style of their own).
 */
@Immutable
sealed interface MapStyleSource {
    data class Url(val url: String) : MapStyleSource
    data class Json(val json: String) : MapStyleSource
}

fun MapStyleSource.toBaseStyle(): BaseStyle = when (this) {
    is MapStyleSource.Url -> BaseStyle.Uri(url)
    is MapStyleSource.Json -> BaseStyle.Json(json)
}

fun MapStyleSource.applyToMap(map: MapLibreMap, onStyleLoaded: (Style) -> Unit) {
    val callback = Style.OnStyleLoaded { style -> onStyleLoaded(style) }
    when (this) {
        is MapStyleSource.Url -> map.setStyle(url, callback)
        is MapStyleSource.Json -> map.setStyle(Style.Builder().fromJson(json), callback)
    }
}
