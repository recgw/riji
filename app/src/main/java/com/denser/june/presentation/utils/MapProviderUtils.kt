package com.denser.june.presentation.utils

import android.Manifest
import android.content.Context
import android.location.LocationManager
import androidx.annotation.RequiresPermission
import com.denser.june.core.domain.model.JournalLocation
import com.denser.june.core.domain.model.enums.MapStyleProvider
import com.denser.june.core.domain.preferences.JournalPreferences
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONArray
import org.json.JSONObject
import org.maplibre.android.geometry.LatLng
import java.io.IOException
import java.util.Locale
import org.koin.core.component.KoinComponent
import org.koin.core.component.inject
import com.denser.june.core.utils.Constants

object MapProviderUtils : KoinComponent {
    private val client: OkHttpClient by inject()
    private val journalPreferences: JournalPreferences by inject()
    private val context: Context by inject()
    private val locationProvider: LocationProvider by inject()

    fun getStyleSourceWithKeys(
        provider: MapStyleProvider,
        isDark: Boolean,
        mapTilerKey: String,
        stadiaKey: String
    ): MapStyleSource {
        return when (provider) {
            MapStyleProvider.AMAP -> MapStyleSource.Json(buildAmapStyleJson(isDark))
            MapStyleProvider.MAPTILER -> {
                val styleName = if (isDark) "streets-v4-dark" else "streets-v4"
                MapStyleSource.Url("${Constants.MAPTILER_MAPS_BASE_URL}$styleName/style.json?key=$mapTilerKey")
            }
            MapStyleProvider.STADIA -> {
                val styleName = if (isDark) "alidade_smooth_dark" else "alidade_smooth"
                val keyQuery = if (stadiaKey.isNotBlank()) "?api_key=$stadiaKey" else ""
                MapStyleSource.Url("${Constants.STADIA_MAPS_BASE_URL}$styleName.json$keyQuery")
            }
            MapStyleProvider.CARTO -> {
                val styleName = if (isDark) "dark-matter-gl-style" else "positron-gl-style"
                MapStyleSource.Url("${Constants.CARTO_MAPS_BASE_URL}$styleName/style.json")
            }
            MapStyleProvider.OPENMAP -> {
                val styleName = if (isDark) "fiord" else "bright"
                MapStyleSource.Url("${Constants.OPENMAP_MAPS_BASE_URL}$styleName")
            }
        }
    }

    /**
     * AMap (高德) only serves raster tiles, so the style document is generated here
     * instead of being downloaded. Two raster sources are stacked: a 512 px road
     * basemap (scl=2, style=7) that stays sharp down to street level, and the
     * transparent Chinese label/POI overlay (scl=1, style=8). Night mode dims the
     * basemap and lifts the label brightness floor so the text stays readable.
     */
    private fun buildAmapStyleJson(isDark: Boolean): String {
        val baseTiles = tileUrls(Constants.AMAP_TILE_URL_FORMAT)
        val labelTiles = tileUrls(Constants.AMAP_LABEL_URL_FORMAT)
        val backgroundColor = if (isDark) "#141414" else "#f4f1ea"
        val baseSaturation = if (isDark) -0.75 else 0.0
        val baseBrightnessMax = if (isDark) 0.42 else 1.0
        val baseContrast = if (isDark) 0.08 else 0.0
        val labelSaturation = if (isDark) -0.6 else 0.0
        val labelBrightnessMin = if (isDark) 0.55 else 0.0
        return """
            {
              "version": 8,
              "name": "AMap ${if (isDark) "Night" else "Day"}",
              "sources": {
                "amap-base": {
                  "type": "raster",
                  "tiles": [
                    $baseTiles
                  ],
                  "tileSize": 512,
                  "minzoom": ${Constants.AMAP_TILE_MIN_ZOOM},
                  "maxzoom": ${Constants.AMAP_TILE_MAX_ZOOM},
                  "attribution": "© 高德地图"
                },
                "amap-labels": {
                  "type": "raster",
                  "tiles": [
                    $labelTiles
                  ],
                  "tileSize": 256,
                  "minzoom": ${Constants.AMAP_LABEL_MIN_ZOOM},
                  "maxzoom": ${Constants.AMAP_LABEL_MAX_ZOOM},
                  "attribution": "© 高德地图"
                }
              },
              "layers": [
                {
                  "id": "amap-background",
                  "type": "background",
                  "paint": {
                    "background-color": "$backgroundColor"
                  }
                },
                {
                  "id": "amap-base",
                  "type": "raster",
                  "source": "amap-base",
                  "minzoom": 0,
                  "maxzoom": 22,
                  "paint": {
                    "raster-opacity": 1,
                    "raster-saturation": $baseSaturation,
                    "raster-brightness-min": 0,
                    "raster-brightness-max": $baseBrightnessMax,
                    "raster-contrast": $baseContrast
                  }
                },
                {
                  "id": "amap-labels",
                  "type": "raster",
                  "source": "amap-labels",
                  "minzoom": 0,
                  "maxzoom": 22,
                  "paint": {
                    "raster-opacity": 1,
                    "raster-saturation": $labelSaturation,
                    "raster-brightness-min": $labelBrightnessMin,
                    "raster-brightness-max": 1
                  }
                }
              ]
            }
        """.trimIndent()
    }

    private fun tileUrls(format: String): String =
        (1..Constants.AMAP_TILE_SUBDOMAINS).joinToString(",\n                    ") { subdomain ->
            "\"" + String.format(Locale.US, format, subdomain) + "\""
        }

    suspend fun getStyleSource(
        provider: MapStyleProvider,
        isDark: Boolean
    ): MapStyleSource {
        val mapTilerKey = journalPreferences.maptilerKey().first()
        val stadiaKey = journalPreferences.stadiaKey().first()
        return getStyleSourceWithKeys(provider, isDark, mapTilerKey, stadiaKey)
    }

    @RequiresPermission(anyOf = [Manifest.permission.ACCESS_FINE_LOCATION, Manifest.permission.ACCESS_COARSE_LOCATION])
    suspend fun fetchCurrentLocation(context: Context): JournalLocation? {
        return locationProvider.fetchCurrentLocation(context)
    }

    @RequiresPermission(anyOf = [Manifest.permission.ACCESS_FINE_LOCATION, Manifest.permission.ACCESS_COARSE_LOCATION])
    suspend fun fetchCurrentFix(context: Context): LocationFix? {
        return locationProvider.fetchCurrentFix(context)
    }

    /** Coordinates only, no reverse geocoding round trip. */
    @RequiresPermission(anyOf = [Manifest.permission.ACCESS_FINE_LOCATION, Manifest.permission.ACCESS_COARSE_LOCATION])
    suspend fun fetchCurrentCoordinates(context: Context): CoordinateFix? {
        return locationProvider.fetchCurrentCoordinates(context)
    }

    private suspend fun performGetRequest(context: Context, url: String): String? {
        return withContext(Dispatchers.IO) {
            try {
                val request = Request.Builder()
                    .url(url)
                    .header("User-Agent", "JuneApp/${context.packageName}")
                    .header("Referer", "android://${context.packageName}")
                    .build()

                client.newCall(request).execute().use { response ->
                    if (!response.isSuccessful) return@withContext null
                    return@withContext response.body?.string()
                }
            } catch (e: IOException) {
                e.printStackTrace()
                null
            }
        }
    }

    suspend fun searchLocation(context: Context, query: String): List<MapSearchResult> {
        val mapTilerKey = journalPreferences.maptilerKey().first()

        if (mapTilerKey.isNotBlank()) {
            val encodedQuery = java.net.URLEncoder.encode(query, "UTF-8")
            val url = "${Constants.MAPTILER_GEOCODING_BASE_URL}$encodedQuery.json?key=$mapTilerKey&limit=5"
            val response = performGetRequest(context, url)
            if (response != null) {
                try {
                    val json = JSONObject(response)
                    val features = json.getJSONArray("features")
                    val results = mutableListOf<MapSearchResult>()
                    for (i in 0 until features.length()) {
                        val feature = features.getJSONObject(i)
                        val center = feature.getJSONArray("center")
                        val placeName = feature.getString("place_name")
                        val text = feature.optString("text", "Selected Location")
                        results.add(
                            MapSearchResult(
                                name = text,
                                address = placeName,
                                latitude = center.getDouble(1),
                                longitude = center.getDouble(0)
                            )
                        )
                    }
                    if (results.isNotEmpty()) return results
                } catch (e: Exception) {
                    e.printStackTrace()
                }
            }
        }

        val photonResults = searchPhoton(context, query)
        if (photonResults.isNotEmpty()) return photonResults

        val encodedQuery = java.net.URLEncoder.encode(query, "UTF-8")
        val url = "${Constants.NOMINATIM_SEARCH_URL}?q=$encodedQuery&format=json&limit=5"
        val response = performGetRequest(context, url) ?: return emptyList()
        return try {
            val jsonArray = JSONArray(response)
            val results = mutableListOf<MapSearchResult>()
            for (i in 0 until jsonArray.length()) {
                val obj = jsonArray.getJSONObject(i)
                val displayName = obj.getString("display_name")
                val parts = displayName.split(",", limit = 2)
                val name = parts.firstOrNull()?.trim() ?: "Selected Location"
                val address = parts.getOrNull(1)?.trim() ?: displayName
                results.add(
                    MapSearchResult(
                        name = name,
                        address = address,
                        latitude = obj.getDouble("lat"),
                        longitude = obj.getDouble("lon")
                    )
                )
            }
            results
        } catch (e: Exception) {
            e.printStackTrace()
            emptyList()
        }
    }

    suspend fun updateLocationFromCenter(context: Context, center: LatLng): JournalLocation {
        val mapTilerKey = journalPreferences.maptilerKey().first()

        if (mapTilerKey.isNotBlank()) {
            val url = "${Constants.MAPTILER_GEOCODING_BASE_URL}${center.longitude},${center.latitude}.json?key=$mapTilerKey"
            val response = performGetRequest(context, url)
            if (response != null) {
                try {
                    val json = JSONObject(response)
                    val features = json.getJSONArray("features")
                    if (features.length() > 0) {
                        val feature = features.getJSONObject(0)
                        val placeName = feature.getString("place_name")
                        val text = feature.optString("text", "Selected Location")

                        var locality = ""
                        val contextArray = feature.optJSONArray("context")
                        if (contextArray != null) {
                            for (i in 0 until contextArray.length()) {
                                val item = contextArray.getJSONObject(i)
                                val id = item.getString("id")
                                if (id.startsWith("place") || id.startsWith("region")) {
                                    locality = item.getString("text")
                                    break
                                }
                            }
                        }

                        return JournalLocation(
                            latitude = center.latitude,
                            longitude = center.longitude,
                            name = text,
                            address = placeName,
                            locality = locality
                        )
                    }
                } catch (e: Exception) {
                    e.printStackTrace()
                }
            }
        }

        reverseGeocodePhoton(context, center)?.let { return it }

        val url = "${Constants.NOMINATIM_REVERSE_URL}?lat=${center.latitude}&lon=${center.longitude}&format=json"
        val response = performGetRequest(context, url)
        if (response != null) {
            try {
                val json = JSONObject(response)
                val displayName = json.getString("display_name")
                val addressObj = json.optJSONObject("address")
                val name = addressObj?.optString("road")
                    ?: addressObj?.optString("suburb")
                    ?: addressObj?.optString("city")
                    ?: "Selected Location"
                val locality = addressObj?.optString("city")
                    ?: addressObj?.optString("town")
                    ?: addressObj?.optString("village")
                    ?: ""

                return JournalLocation(
                    latitude = center.latitude,
                    longitude = center.longitude,
                    name = name,
                    address = displayName,
                    locality = locality
                )
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }

        return JournalLocation(center.latitude, center.longitude, name = "Unknown Location", address = generateFallbackLabel(center))
    }

    private fun generateFallbackLabel(center: LatLng): String {
        return "Lat: ${"%.4f".format(Locale.US, center.latitude)}, Lon: ${"%.4f".format(Locale.US, center.longitude)}"
    }

    /**
     * Photon (OSM based, keyless) works as the China-friendly fallback: Nominatim
     * frequently times out from mainland networks, leaving the picker with nothing
     * but raw coordinates.
     */
    private suspend fun reverseGeocodePhoton(context: Context, center: LatLng): JournalLocation? {
        val url = "${Constants.PHOTON_REVERSE_URL}?lat=${center.latitude}&lon=${center.longitude}&limit=1"
        val response = performGetRequest(context, url) ?: return null
        return try {
            val features = JSONObject(response).optJSONArray("features") ?: return null
            val props = features.optJSONObject(0)?.optJSONObject("properties") ?: return null
            val street = firstNonBlank(props.optString("street"), props.optString("name")).orEmpty()
            val name = firstNonBlank(
                street,
                props.optString("district"),
                props.optString("city"),
                props.optString("state")
            ) ?: return null
            JournalLocation(
                latitude = center.latitude,
                longitude = center.longitude,
                name = name,
                address = photonAddress(props, name),
                locality = firstNonBlank(props.optString("city"), props.optString("district")) ?: ""
            )
        } catch (e: Exception) {
            e.printStackTrace()
            null
        }
    }

    private suspend fun searchPhoton(context: Context, query: String): List<MapSearchResult> {
        val encodedQuery = java.net.URLEncoder.encode(query, "UTF-8")
        val url = "${Constants.PHOTON_SEARCH_URL}?q=$encodedQuery" +
            "&limit=5&bbox=${Constants.PHOTON_CHINA_BBOX}"
        val response = performGetRequest(context, url) ?: return emptyList()
        return try {
            val features = JSONObject(response).optJSONArray("features") ?: return emptyList()
            val results = mutableListOf<MapSearchResult>()
            for (i in 0 until features.length()) {
                val feature = features.optJSONObject(i) ?: continue
                val props = feature.optJSONObject("properties") ?: continue
                val coordinates = feature.optJSONObject("geometry")?.optJSONArray("coordinates") ?: continue
                val name = firstNonBlank(
                    props.optString("name"),
                    props.optString("street"),
                    props.optString("district"),
                    props.optString("city")
                ) ?: continue
                results.add(
                    MapSearchResult(
                        name = name,
                        address = photonAddress(props, name),
                        latitude = coordinates.optDouble(1),
                        longitude = coordinates.optDouble(0)
                    )
                )
            }
            results
        } catch (e: Exception) {
            e.printStackTrace()
            emptyList()
        }
    }

    private fun photonAddress(props: JSONObject, name: String): String {
        val street = props.optString("street")
        val houseNumber = props.optString("housenumber")
        val road = if (houseNumber.isBlank()) street else "$street$houseNumber"
        val parts = mutableListOf<String>()
        listOf(props.optString("state"), props.optString("city"), props.optString("district"), road.ifBlank { name })
            .filter { it.isNotBlank() }
            .forEach { part -> if (parts.none { it == part }) parts.add(part) }
        return parts.joinToString("").ifBlank { name }
    }

    private fun firstNonBlank(vararg values: String): String? =
        values.map { it.trim() }.firstOrNull { it.isNotEmpty() }

    fun isLocationServiceEnabled(context: Context): Boolean {
        val lm = context.getSystemService(Context.LOCATION_SERVICE) as? LocationManager ?: return false
        return lm.isProviderEnabled(LocationManager.GPS_PROVIDER) || lm.isProviderEnabled(LocationManager.NETWORK_PROVIDER)
    }

    fun isSameCoordinates(lat1: Double, lon1: Double, lat2: Double, lon2: Double, threshold: Double = 1e-5): Boolean {
        return kotlin.math.abs(lat1 - lat2) < threshold && kotlin.math.abs(lon1 - lon2) < threshold
    }

    fun formatCoordinates(lat: Double, lon: Double): String {
        return "${String.format(Locale.US, "%.5f", lat)}, ${String.format(Locale.US, "%.5f", lon)}"
    }

    suspend fun testApiKey(provider: MapStyleProvider, key: String): Boolean {
        if (key.isBlank()) return false
        val url = when (provider) {
            MapStyleProvider.MAPTILER -> {
                "${Constants.MAPTILER_MAPS_BASE_URL}streets-v4/style.json?key=$key"
            }
            MapStyleProvider.STADIA -> {
                "${Constants.STADIA_MAPS_BASE_URL}alidade_smooth.json?api_key=$key"
            }
            MapStyleProvider.AMAP,
            MapStyleProvider.CARTO,
            MapStyleProvider.OPENMAP -> return true
        }

        return withContext(Dispatchers.IO) {
            try {
                val request = Request.Builder()
                    .url(url)
                    .header("User-Agent", "JuneApp/${context.packageName}")
                    .header("Referer", "android://${context.packageName}")
                    .build()
                client.newCall(request).execute().use { response ->
                    android.util.Log.d("MapProviderUtils", "testApiKey [$provider] → HTTP ${response.code}")
                    response.isSuccessful
                }
            } catch (e: Exception) {
                android.util.Log.e("MapProviderUtils", "testApiKey [$provider] → exception: ${e.message}", e)
                false
            }
        }
    }
}
