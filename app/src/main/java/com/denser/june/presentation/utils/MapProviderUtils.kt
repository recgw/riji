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
     * instead of being downloaded. Night mode is approximated by desaturating and
     * clamping the brightest channels of the daytime tiles.
     */
    private fun buildAmapStyleJson(isDark: Boolean): String {
        val tileUrls = (1..Constants.AMAP_TILE_SUBDOMAINS).joinToString(",\n        ") { subdomain ->
            "\"" + String.format(Locale.US, Constants.AMAP_TILE_URL_FORMAT, subdomain) + "\""
        }
        val saturation = if (isDark) -0.7 else 0.0
        val brightnessMax = if (isDark) 0.45 else 1.0
        val contrast = if (isDark) 0.1 else 0.0
        val backgroundColor = if (isDark) "#1a1a1a" else "#f4f1ea"
        return """
            {
              "version": 8,
              "name": "AMap ${if (isDark) "Night" else "Day"}",
              "sources": {
                "amap-tiles": {
                  "type": "raster",
                  "tiles": [
                    $tileUrls
                  ],
                  "tileSize": 256,
                  "minzoom": ${Constants.AMAP_TILE_MIN_ZOOM},
                  "maxzoom": ${Constants.AMAP_TILE_MAX_ZOOM},
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
                  "source": "amap-tiles",
                  "minzoom": 0,
                  "maxzoom": 22,
                  "paint": {
                    "raster-opacity": 1,
                    "raster-saturation": $saturation,
                    "raster-brightness-min": 0,
                    "raster-brightness-max": $brightnessMax,
                    "raster-contrast": $contrast
                  }
                }
              ]
            }
        """.trimIndent()
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
