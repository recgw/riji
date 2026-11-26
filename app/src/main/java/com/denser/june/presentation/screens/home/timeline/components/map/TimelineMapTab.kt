package com.denser.june.presentation.screens.home.timeline.components.map

import android.Manifest
import android.content.pm.PackageManager
import android.graphics.Bitmap
import androidx.compose.foundation.layout.*
import androidx.core.content.ContextCompat
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.denser.june.core.R
import com.denser.june.core.domain.model.Journal
import com.denser.june.core.domain.model.enums.MapStyleProvider
import com.denser.june.core.domain.model.enums.MapTheme
import com.denser.june.core.domain.preferences.JournalPreferences
import com.denser.june.core.utils.toDayOfMonth
import com.denser.june.core.utils.toShortMonth
import com.denser.june.presentation.components.*
import com.denser.june.presentation.screens.home.timeline.TimelineVM
import com.denser.june.presentation.screens.home.timeline.components.common.EmptyStateMessage
import com.denser.june.presentation.theme.LocalInternetAllowed
import com.denser.june.presentation.utils.MapAccentColorizer
import com.denser.june.presentation.utils.MapCoordinates
import com.denser.june.presentation.utils.MapProviderUtils
import com.denser.june.presentation.utils.MapStyleSource
import com.denser.june.presentation.utils.TimelineMapUtils
import com.denser.june.presentation.utils.applyToMap
import com.denser.june.presentation.utils.updateUserLocationOnMap
import org.koin.compose.koinInject
import org.koin.compose.viewmodel.koinViewModel
import org.maplibre.android.camera.CameraPosition
import org.maplibre.android.geometry.LatLng
import org.maplibre.android.maps.MapLibreMap
import org.maplibre.android.maps.MapLibreMapOptions
import org.maplibre.android.maps.MapView
import org.maplibre.android.maps.Style
import org.maplibre.android.style.expressions.Expression
import org.maplibre.android.style.layers.LineLayer
import org.maplibre.android.style.layers.Property
import org.maplibre.android.style.layers.PropertyFactory
import org.maplibre.android.style.layers.SymbolLayer
import org.maplibre.android.style.sources.GeoJsonSource
import org.maplibre.geojson.Feature
import org.maplibre.geojson.FeatureCollection
import org.maplibre.geojson.LineString
import org.maplibre.geojson.Point

@Composable
fun TimelineMapTab(
    journals: List<Journal>,
    bottomPadding: Dp,
    viewModel: TimelineVM = koinViewModel()
) {
    val isInternetAllowed = LocalInternetAllowed.current
    val context = LocalContext.current

    val isCalendarExpanded by viewModel.isCalendarExpanded.collectAsStateWithLifecycle()
    val isMapExpanded = !isCalendarExpanded

    val journalPreferences = koinInject<JournalPreferences>()
    val savedMapTheme by journalPreferences.mapTheme()
        .collectAsStateWithLifecycle(initialValue = MapTheme.APP)
    val mapStyleProvider by journalPreferences.mapStyleProvider()
        .collectAsStateWithLifecycle(initialValue = MapStyleProvider.AMAP)
    val isMapThemeAccentEnabled by journalPreferences.isMapThemeAccentEnabled()
        .collectAsStateWithLifecycle(initialValue = true)
    val isPlus = com.denser.june.presentation.plus.LocalIsPlus.current
    val colorScheme = MaterialTheme.colorScheme

    MapLibreInitializer(isInternetAllowed)

    val validPoints = remember(journals) {
        journals.filter { it.location?.let { loc -> loc.latitude != 0.0 && loc.longitude != 0.0 } == true }
    }

    val sortedPoints = remember(validPoints) {
        validPoints.sortedBy { it.dateTime }
    }

    val locationGroups = remember(sortedPoints) {
        TimelineMapUtils.buildLocationGroups(sortedPoints)
    }

    // Journal locations are stored as WGS-84; AMap tiles are GCJ-02, so shift them for display.
    val displayPoints = remember(locationGroups, mapStyleProvider) {
        locationGroups.map { group ->
            val (lat, lon) = MapCoordinates.toDisplay(
                mapStyleProvider,
                group.location.latitude,
                group.location.longitude
            )
            LatLng(lat, lon)
        }
    }

    var selectedIndex by remember { mutableIntStateOf(0) }

    LaunchedEffect(sortedPoints) {
        selectedIndex = 0
    }

    var isDarkMap by rememberMapDarkMode(savedMapTheme)

    // Current position dot: only drawn once location permission has been granted elsewhere
    // in the app, so the timeline never triggers a permission prompt on its own.
    // Not remembered: granting the permission later (in the editor) is picked up on the next
    // recomposition instead of staying stale for the lifetime of the tab.
    val hasLocationPermission =
        ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED ||
            ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_COARSE_LOCATION) == PackageManager.PERMISSION_GRANTED
    var userLocationFix by remember { mutableStateOf<Pair<LatLng, Float?>?>(null) }

    val primaryColor = MaterialTheme.colorScheme.primary.toArgb()
    val onPrimaryColor = MaterialTheme.colorScheme.onPrimary.toArgb()
    val mutedLineColor = if (isMapThemeAccentEnabled) {
        MaterialTheme.colorScheme.primary.copy(alpha = 0.65f).toArgb()
    } else if (isDarkMap) {
        Color.White.copy(alpha = 0.3f).toArgb()
    } else {
        Color.Black.copy(alpha = 0.3f).toArgb()
    }

    val styleSource by produceState<MapStyleSource?>(initialValue = null, mapStyleProvider, isDarkMap) {
        value = MapProviderUtils.getStyleSource(mapStyleProvider, isDarkMap)
    }
    val mapView = remember(isInternetAllowed) {
        if (isInternetAllowed) {
            val options = MapLibreMapOptions.createFromAttributes(context).apply {
                textureMode(true)
            }
            MapView(context, options).apply { isClickable = true; isFocusable = true }
        } else null
    }

    val markerBitmapCache = remember(primaryColor, onPrimaryColor) { mutableMapOf<String, Bitmap>() }

    fun getMarkerBitmap(text: String): Bitmap {
        return markerBitmapCache.getOrPut(text) {
            TimelineMapUtils.createMarkerBitmap(context, text, primaryColor, onPrimaryColor)
        }
    }

    LaunchedEffect(selectedIndex, sortedPoints, mapView, mapStyleProvider) {
        val target = sortedPoints.getOrNull(selectedIndex)?.location ?: return@LaunchedEffect
        val display = MapCoordinates.toDisplay(mapStyleProvider, target.latitude, target.longitude)
        mapView?.getMapAsync { map ->
            map.animateCamera(
                org.maplibre.android.camera.CameraUpdateFactory.newCameraPosition(
                    CameraPosition.Builder()
                        .target(LatLng(display.first, display.second))
                        .zoom(15.0)
                        .build()
                ),
                800
            )
        }
    }

    // Coordinates only: on this screen the dot needs no address, so we skip the network
    // round trip and stay usable offline.
    LaunchedEffect(mapView, mapStyleProvider, hasLocationPermission) {
        if (!hasLocationPermission || mapView == null) return@LaunchedEffect
        val fix = MapProviderUtils.fetchCurrentCoordinates(context) ?: return@LaunchedEffect
        val display = MapCoordinates.toDisplay(mapStyleProvider, fix.latitude, fix.longitude)
        val target = LatLng(display.first, display.second)
        userLocationFix = target to fix.accuracyMeters
        mapView.getMapAsync { map ->
            map.style?.let { style ->
                updateUserLocationOnMap(style, target, fix.accuracyMeters)
            }
        }
    }

    fun renderMapContent(mapboxMap: MapLibreMap, style: Style) {
        if (isPlus && isMapThemeAccentEnabled) {
            MapAccentColorizer.applyThemeAccents(
                style = style,
                colorScheme = colorScheme,
                provider = mapStyleProvider,
                isDark = isDarkMap
            )
        }
        val pathPoints = displayPoints

        val lines = if (pathPoints.size > 1) {
            (0 until pathPoints.size - 1).map { i ->
                val arcPoints = TimelineMapUtils.createArchedPoints(pathPoints[i], pathPoints[i + 1])
                LineString.fromLngLats(arcPoints.map { Point.fromLngLat(it.longitude, it.latitude) })
            }
        } else emptyList()

        val featureCollection = FeatureCollection.fromFeatures(lines.map { Feature.fromGeometry(it) })
        val sourceId = "timeline-source"
        val layerId = "timeline-layer"

        val existingSource = style.getSource(sourceId) as? GeoJsonSource
        if (existingSource != null) {
            existingSource.setGeoJson(featureCollection)
        } else {
            style.addSource(GeoJsonSource(sourceId, featureCollection))
        }

        if (style.getLayer(layerId) == null) {
            style.addLayer(
                LineLayer(layerId, sourceId).apply {
                    setProperties(
                        PropertyFactory.lineColor(mutedLineColor),
                        PropertyFactory.lineWidth(1.8f),
                        PropertyFactory.lineDasharray(arrayOf(2f, 2f))
                    )
                }
            )
        } else {
            style.getLayer(layerId)?.setProperties(PropertyFactory.lineColor(mutedLineColor))
        }

        val markerSourceId = "timeline-marker-source"
        val markerLayerId = "timeline-marker-layer"

        locationGroups.forEach { group ->
            val locationName = group.location.name?.ifBlank { "Location" } ?: "Location"
            val imageId = "marker-icon-$locationName"
            style.addImage(imageId, getMarkerBitmap(locationName))
        }

        val markerFeatures = locationGroups.mapIndexed { index, group ->
            val locationName = group.location.name?.ifBlank { "Location" } ?: "Location"
            val imageId = "marker-icon-$locationName"
            val point = displayPoints[index]
            Feature.fromGeometry(Point.fromLngLat(point.longitude, point.latitude)).apply {
                addStringProperty("icon-image", imageId)
            }
        }
        val markerFeatureCollection = FeatureCollection.fromFeatures(markerFeatures)

        val existingMarkerSource = style.getSource(markerSourceId) as? GeoJsonSource
        if (existingMarkerSource != null) {
            existingMarkerSource.setGeoJson(markerFeatureCollection)
        } else {
            style.addSource(GeoJsonSource(markerSourceId, markerFeatureCollection))
        }

        if (style.getLayer(markerLayerId) == null) {
            style.addLayer(
                SymbolLayer(markerLayerId, markerSourceId).apply {
                    setProperties(
                        PropertyFactory.iconImage(Expression.get("icon-image")),
                        PropertyFactory.iconAllowOverlap(true),
                        PropertyFactory.iconIgnorePlacement(true),
                        PropertyFactory.iconAnchor(Property.ICON_ANCHOR_BOTTOM)
                    )
                }
            )
        }

        // Drawn last so the position dot sits above the trajectory and the journal markers.
        userLocationFix?.let { (target, accuracy) ->
            updateUserLocationOnMap(style, target, accuracy)
        }
    }

    var hasInitialBoundsAnimated by remember(sortedPoints) { mutableStateOf(false) }

    fun animateToInitialBounds(mapboxMap: MapLibreMap) {
        val pathPoints = displayPoints
        if (pathPoints.size == 1) {
            mapboxMap.animateCamera(
                org.maplibre.android.camera.CameraUpdateFactory.newCameraPosition(
                    CameraPosition.Builder().target(pathPoints.first()).zoom(14.0).build()
                ),
                800
            )
        } else if (pathPoints.size > 1) {
            val boundsBuilder = org.maplibre.android.geometry.LatLngBounds.Builder()
            pathPoints.forEach { boundsBuilder.include(it) }
            val padding = (64 * context.resources.displayMetrics.density).toInt()
            try {
                mapboxMap.animateCamera(
                    org.maplibre.android.camera.CameraUpdateFactory.newLatLngBounds(boundsBuilder.build(), padding),
                    1000
                )
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
    }

    if (validPoints.isEmpty()) {
        EmptyStateMessage(stringResource(R.string.no_locations_this_month))
        return
    }

    val mapShape = RoundedCornerShape(topStart = 18.dp, topEnd = 18.dp)

    Box(
        modifier = Modifier
            .fillMaxSize()
            .graphicsLayer {
                shape = mapShape
                clip = true
            }
            .clip(mapShape)
    ) {
        if (isInternetAllowed && mapView != null) {
            MapViewLifecycleEffect(mapView)

            LaunchedEffect(styleSource, sortedPoints, mapView, isMapThemeAccentEnabled, colorScheme) {
                val source = styleSource ?: return@LaunchedEffect
                mapView.getMapAsync { mapboxMap ->
                    val previousCamera = mapboxMap.cameraPosition
                    val shouldRestoreCamera = hasInitialBoundsAnimated && previousCamera.zoom > 0
                    mapboxMap.uiSettings.isAttributionEnabled = false
                    mapboxMap.uiSettings.isLogoEnabled = false
                    mapboxMap.uiSettings.isCompassEnabled = false
                    source.applyToMap(mapboxMap) { style ->
                        renderMapContent(mapboxMap, style)
                        if (shouldRestoreCamera) {
                            mapboxMap.moveCamera(
                                org.maplibre.android.camera.CameraUpdateFactory.newCameraPosition(previousCamera)
                            )
                        } else {
                            animateToInitialBounds(mapboxMap)
                            hasInitialBoundsAnimated = true
                        }
                    }
                }
            }

            AndroidView(
                factory = { mapView },
                modifier = Modifier.fillMaxSize()
            )

            Column(
                modifier = Modifier
                    .align(Alignment.TopStart)
                    .padding(16.dp),
                horizontalAlignment = Alignment.Start,
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                val currentJournal = sortedPoints.getOrNull(selectedIndex)
                val dayText = remember(currentJournal) { currentJournal?.dateTime?.toDayOfMonth() ?: "" }
                val monthText = remember(currentJournal) { currentJournal?.dateTime?.toShortMonth()?.uppercase() ?: "" }

                MapNavigationPill(
                    totalCount = sortedPoints.size,
                    dayText = dayText,
                    monthText = monthText,
                    onPrevious = {
                        selectedIndex = if (selectedIndex > 0) selectedIndex - 1 else sortedPoints.size - 1
                    },
                    onNext = {
                        selectedIndex = (selectedIndex + 1) % sortedPoints.size
                    }
                )
            }

            Column(
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .padding(16.dp),
                horizontalAlignment = Alignment.End,
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                MapControlColumn(
                    isMapExpanded = isMapExpanded,
                    isDarkMode = isDarkMap,
                    onToggleDarkMode = { isDarkMap = !isDarkMap },
                    onToggleFullscreen = { viewModel.setCalendarExpanded(isMapExpanded) }
                )
            }

            Box(
                modifier = Modifier
                    .align(Alignment.BottomStart)
                    .padding(start = 16.dp, bottom = 16.dp)
            ) {
                MapAttributions(
                    provider = mapStyleProvider,
                    isDarkMode = isDarkMap
                )
            }
        } else {
            Box(
                modifier = Modifier.fillMaxSize(),
                contentAlignment = Alignment.Center
            ) {
                InternetRestrictedIndicator(
                    modifier = Modifier
                        .padding(horizontal = 24.dp)
                        .padding(bottom = bottomPadding)
                )
            }
        }
    }
}

@Composable
fun MapNavigationPill(
    totalCount: Int,
    dayText: String,
    monthText: String,
    onPrevious: () -> Unit,
    onNext: () -> Unit,
    modifier: Modifier = Modifier
) {
    Surface(
        modifier = modifier.widthIn(min = 120.dp),
        shape = CircleShape,
        color = MaterialTheme.colorScheme.surfaceContainerHigh,
        tonalElevation = 6.dp,
        shadowElevation = 4.dp
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween,
            modifier = Modifier.padding(horizontal = 4.dp, vertical = 6.dp)
        ) {
            IconButton(
                onClick = onPrevious,
                enabled = totalCount > 1
            ) {
                Icon(
                    painter = painterResource(R.drawable.chevron_left_24px),
                    contentDescription = "Previous",
                    tint = if (totalCount > 1) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.38f)
                )
            }

            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                modifier = Modifier.padding(horizontal = 8.dp)
            ) {
                Text(
                    text = dayText.ifBlank { "--" },
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurface,
                    textAlign = TextAlign.Center
                )
                Text(
                    text = monthText.ifBlank { "---" },
                    style = MaterialTheme.typography.bodySmall,
                    fontWeight = FontWeight.Medium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center
                )
            }

            IconButton(
                onClick = onNext,
                enabled = totalCount > 1
            ) {
                Icon(
                    painter = painterResource(R.drawable.chevron_right_24px),
                    contentDescription = "Next",
                    tint = if (totalCount > 1) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.38f)
                )
            }
        }
    }
}
