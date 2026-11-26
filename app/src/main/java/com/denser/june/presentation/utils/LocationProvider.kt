package com.denser.june.presentation.utils

import android.content.Context
import com.denser.june.core.domain.model.JournalLocation

/** A resolved location plus the horizontal accuracy reported by the platform, if any. */
data class LocationFix(
    val location: JournalLocation,
    val accuracyMeters: Float? = null
)

interface LocationProvider {
    suspend fun fetchCurrentLocation(context: Context): JournalLocation?

    /**
     * Same fix as [fetchCurrentLocation], but keeps the accuracy radius so the map can
     * draw the halo around the "you are here" dot.
     */
    suspend fun fetchCurrentFix(context: Context): LocationFix? =
        fetchCurrentLocation(context)?.let { LocationFix(it) }
}
