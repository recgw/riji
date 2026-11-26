package com.denser.june.presentation.utils

import android.content.Context
import com.denser.june.core.domain.model.JournalLocation

/** Raw coordinates from the platform, before any reverse geocoding. */
data class CoordinateFix(
    val latitude: Double,
    val longitude: Double,
    val accuracyMeters: Float? = null
)

/** A resolved location plus the horizontal accuracy reported by the platform, if any. */
data class LocationFix(
    val location: JournalLocation,
    val accuracyMeters: Float? = null
)

interface LocationProvider {
    suspend fun fetchCurrentLocation(context: Context): JournalLocation?

    /**
     * Coordinates only: no network round trip, so screens that just draw the position
     * dot stay offline-friendly.
     */
    suspend fun fetchCurrentCoordinates(context: Context): CoordinateFix? = null

    /**
     * Same fix as [fetchCurrentLocation], but keeps the accuracy radius so the map can
     * draw the halo around the "you are here" dot.
     */
    suspend fun fetchCurrentFix(context: Context): LocationFix? =
        fetchCurrentLocation(context)?.let { LocationFix(it) }
}
