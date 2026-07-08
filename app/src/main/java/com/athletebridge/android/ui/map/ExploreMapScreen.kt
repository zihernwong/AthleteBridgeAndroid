package com.athletebridge.android.ui.map

import android.graphics.drawable.GradientDrawable
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.hilt.navigation.compose.hiltViewModel
import com.athletebridge.android.ui.theme.LogoBlue
import com.athletebridge.android.ui.theme.LogoGreen
import org.osmdroid.config.Configuration
import org.osmdroid.tileprovider.tilesource.TileSourceFactory
import org.osmdroid.util.BoundingBox
import org.osmdroid.util.GeoPoint
import org.osmdroid.views.MapView
import org.osmdroid.views.overlay.Marker

/**
 * Discovery map: badminton venues (green pins) and coaches (blue pins).
 * Uses OpenStreetMap tiles via osmdroid — no API key required.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ExploreMapScreen(
    onBack: () -> Unit,
    onPlaceDetail: (String) -> Unit,
    onCoachDetail: (String) -> Unit,
    viewModel: ExploreMapViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsState()

    LaunchedEffect(Unit) { viewModel.load() }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Explore Map") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                }
            )
        }
    ) { padding ->
        Column(Modifier.fillMaxSize().padding(padding)) {
            // Legend
            Row(
                Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
                horizontalArrangement = Arrangement.spacedBy(16.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                LegendDot(color = LogoGreen.toArgb(), label = "Places to Play")
                LegendDot(color = LogoBlue.toArgb(), label = "Coaches")
                if (uiState.isLoading) {
                    Spacer(Modifier.weight(1f))
                    CircularProgressIndicator(Modifier.size(18.dp), strokeWidth = 2.dp)
                }
            }

            AndroidView(
                modifier = Modifier.fillMaxSize(),
                factory = { context ->
                    Configuration.getInstance().userAgentValue = context.packageName
                    MapView(context).apply {
                        setTileSource(TileSourceFactory.MAPNIK)
                        setMultiTouchControls(true)
                        controller.setZoom(10.0)
                        // Default center: Minneapolis (app's home market) until pins load
                        controller.setCenter(GeoPoint(44.9778, -93.2650))
                    }
                },
                update = { mapView ->
                    mapView.overlays.clear()
                    for (pin in uiState.pins) {
                        val marker = Marker(mapView).apply {
                            position = GeoPoint(pin.latitude, pin.longitude)
                            title = pin.title
                            snippet = pin.subtitle
                            setAnchor(Marker.ANCHOR_CENTER, Marker.ANCHOR_BOTTOM)
                            icon = pinDrawable(
                                if (pin.type == MapPinType.PLACE) LogoGreen.toArgb() else LogoBlue.toArgb()
                            )
                            setOnMarkerClickListener { m, _ ->
                                if (m.isInfoWindowShown) {
                                    // Second tap opens the detail screen
                                    when (pin.type) {
                                        MapPinType.PLACE -> onPlaceDetail(pin.id)
                                        MapPinType.COACH -> onCoachDetail(pin.id)
                                    }
                                } else {
                                    m.showInfoWindow()
                                }
                                true
                            }
                        }
                        mapView.overlays.add(marker)
                    }
                    if (uiState.pins.isNotEmpty()) {
                        val lats = uiState.pins.map { it.latitude }
                        val lons = uiState.pins.map { it.longitude }
                        val box = BoundingBox(
                            lats.max() + 0.05, lons.max() + 0.05,
                            lats.min() - 0.05, lons.min() - 0.05
                        )
                        mapView.post { mapView.zoomToBoundingBox(box, false) }
                    }
                    mapView.invalidate()
                }
            )
        }
    }
}

private fun pinDrawable(color: Int): GradientDrawable =
    GradientDrawable().apply {
        shape = GradientDrawable.OVAL
        setColor(color)
        setStroke(4, android.graphics.Color.WHITE)
        setSize(48, 48)
    }

@Composable
private fun LegendDot(color: Int, label: String) {
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        Surface(
            color = androidx.compose.ui.graphics.Color(color),
            shape = androidx.compose.foundation.shape.CircleShape,
            modifier = Modifier.size(12.dp)
        ) {}
        Text(label, style = MaterialTheme.typography.labelMedium)
    }
}
