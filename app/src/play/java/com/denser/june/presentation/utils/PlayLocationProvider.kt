package com.denser.june.presentation.utils

import android.Manifest
import android.content.Context
import androidx.annotation.RequiresPermission
import com.denser.june.core.domain.model.JournalLocation
import com.google.android.gms.location.LocationServices
import com.google.android.gms.location.Priority
import com.google.android.gms.tasks.CancellationTokenSource
import kotlinx.coroutines.tasks.await
import org.maplibre.android.geometry.LatLng

class PlayLocationProvider : LocationProvider {

    @RequiresPermission(anyOf = [Manifest.permission.ACCESS_FINE_LOCATION, Manifest.permission.ACCESS_COARSE_LOCATION])
    override suspend fun fetchCurrentLocation(context: Context): JournalLocation? {
        return fetchCurrentFix(context)?.location
    }

    @RequiresPermission(anyOf = [Manifest.permission.ACCESS_FINE_LOCATION, Manifest.permission.ACCESS_COARSE_LOCATION])
    override suspend fun fetchCurrentFix(context: Context): LocationFix? {
        return try {
            val fusedLocationClient = LocationServices.getFusedLocationProviderClient(context)
            val cancellationTokenSource = CancellationTokenSource()

            val location = fusedLocationClient.getCurrentLocation(
                Priority.PRIORITY_HIGH_ACCURACY,
                cancellationTokenSource.token
            ).await()

            location?.let {
                LocationFix(
                    location = MapProviderUtils.updateLocationFromCenter(
                        context,
                        LatLng(it.latitude, it.longitude)
                    ),
                    accuracyMeters = it.accuracy
                )
            }
        } catch (e: Exception) {
            e.printStackTrace()
            null
        }
    }
}
