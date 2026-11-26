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
    override suspend fun fetchCurrentCoordinates(context: Context): CoordinateFix? {
        return try {
            val fusedLocationClient = LocationServices.getFusedLocationProviderClient(context)
            val cancellationTokenSource = CancellationTokenSource()

            val location = fusedLocationClient.getCurrentLocation(
                Priority.PRIORITY_HIGH_ACCURACY,
                cancellationTokenSource.token
            ).await()

            location?.let {
                CoordinateFix(
                    latitude = it.latitude,
                    longitude = it.longitude,
                    accuracyMeters = it.accuracy
                )
            }
        } catch (e: Exception) {
            e.printStackTrace()
            null
        }
    }

    @RequiresPermission(anyOf = [Manifest.permission.ACCESS_FINE_LOCATION, Manifest.permission.ACCESS_COARSE_LOCATION])
    override suspend fun fetchCurrentFix(context: Context): LocationFix? {
        val fix = fetchCurrentCoordinates(context) ?: return null
        return LocationFix(
            location = MapProviderUtils.updateLocationFromCenter(
                context,
                LatLng(fix.latitude, fix.longitude)
            ),
            accuracyMeters = fix.accuracyMeters
        )
    }
}
