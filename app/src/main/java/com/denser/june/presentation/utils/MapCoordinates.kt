package com.denser.june.presentation.utils

import com.denser.june.core.domain.model.enums.MapStyleProvider
import org.maplibre.android.geometry.LatLng
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.sin
import kotlin.math.sqrt

/**
 * Coordinate helpers for providers whose tiles are published in GCJ-02
 * (AMap / 高德). Journal data is always stored as WGS-84, so coordinates have to
 * be shifted when they are handed to the map and shifted back when a position
 * is read from the map.
 */
object MapCoordinates {

    private const val PI = 3.1415926535897932384626
    private const val A = 6378245.0
    private const val EE = 0.00669342162296594323

    /** Providers that expect GCJ-02 instead of WGS-84. */
    fun providerUsesGcj02(provider: MapStyleProvider): Boolean =
        provider == MapStyleProvider.AMAP

    /** WGS-84 (stored) -> map provider coordinate system (displayed). */
    fun toDisplay(
        provider: MapStyleProvider,
        latitude: Double,
        longitude: Double
    ): Pair<Double, Double> {
        if (!providerUsesGcj02(provider)) return latitude to longitude
        val shifted = wgsToGcj(latitude, longitude)
        return shifted[0] to shifted[1]
    }

    /** Map provider coordinate system (displayed) -> WGS-84 (stored). */
    fun fromDisplay(
        provider: MapStyleProvider,
        latitude: Double,
        longitude: Double
    ): Pair<Double, Double> {
        if (!providerUsesGcj02(provider)) return latitude to longitude
        val shifted = gcjToWgs(latitude, longitude)
        return shifted[0] to shifted[1]
    }

    fun toDisplay(provider: MapStyleProvider, latLng: LatLng): LatLng {
        val (lat, lon) = toDisplay(provider, latLng.latitude, latLng.longitude)
        return LatLng(lat, lon)
    }

    fun fromDisplay(provider: MapStyleProvider, latLng: LatLng): LatLng {
        val (lat, lon) = fromDisplay(provider, latLng.latitude, latLng.longitude)
        return LatLng(lat, lon)
    }

    fun isOutOfChina(latitude: Double, longitude: Double): Boolean =
        longitude < 72.004 || longitude > 137.8347 || latitude < 0.8293 || latitude > 55.8271

    private fun transformLat(x: Double, y: Double): Double {
        var ret = -100.0 + 2.0 * x + 3.0 * y + 0.2 * y * y + 0.1 * x * y + 0.2 * sqrt(abs(x))
        ret += (20.0 * sin(6.0 * x * PI) + 20.0 * sin(2.0 * x * PI)) * 2.0 / 3.0
        ret += (20.0 * sin(y * PI) + 40.0 * sin(y / 3.0 * PI)) * 2.0 / 3.0
        ret += (160.0 * sin(y / 12.0 * PI) + 320.0 * sin(y * PI / 30.0)) * 2.0 / 3.0
        return ret
    }

    private fun transformLon(x: Double, y: Double): Double {
        var ret = 300.0 + x + 2.0 * y + 0.1 * x * x + 0.1 * x * y + 0.1 * sqrt(abs(x))
        ret += (20.0 * sin(6.0 * x * PI) + 20.0 * sin(2.0 * x * PI)) * 2.0 / 3.0
        ret += (20.0 * sin(x * PI) + 40.0 * sin(x / 3.0 * PI)) * 2.0 / 3.0
        ret += (150.0 * sin(x / 12.0 * PI) + 300.0 * sin(x / 30.0 * PI)) * 2.0 / 3.0
        return ret
    }

    /** Standard GCJ-02 obfuscation used by Chinese map providers. */
    fun wgsToGcj(latitude: Double, longitude: Double): DoubleArray {
        if (isOutOfChina(latitude, longitude)) return doubleArrayOf(latitude, longitude)
        val dLat = transformLat(longitude - 105.0, latitude - 35.0)
        val dLon = transformLon(longitude - 105.0, latitude - 35.0)
        val radLat = latitude / 180.0 * PI
        var magic = sin(radLat)
        magic = 1 - EE * magic * magic
        val sqrtMagic = sqrt(magic)
        val latOffset = (dLat * 180.0) / ((A * (1 - EE)) / (magic * sqrtMagic) * PI)
        val lonOffset = (dLon * 180.0) / (A / sqrtMagic * cos(radLat) * PI)
        return doubleArrayOf(latitude + latOffset, longitude + lonOffset)
    }

    /** Numerical inverse of [wgsToGcj]; converges to sub-metre accuracy. */
    fun gcjToWgs(latitude: Double, longitude: Double): DoubleArray {
        if (isOutOfChina(latitude, longitude)) return doubleArrayOf(latitude, longitude)
        var wgsLat = latitude
        var wgsLon = longitude
        repeat(8) {
            val gcj = wgsToGcj(wgsLat, wgsLon)
            val latDiff = gcj[0] - latitude
            val lonDiff = gcj[1] - longitude
            if (abs(latDiff) < 1e-9 && abs(lonDiff) < 1e-9) {
                return doubleArrayOf(wgsLat, wgsLon)
            }
            wgsLat -= latDiff
            wgsLon -= lonDiff
        }
        return doubleArrayOf(wgsLat, wgsLon)
    }
}
