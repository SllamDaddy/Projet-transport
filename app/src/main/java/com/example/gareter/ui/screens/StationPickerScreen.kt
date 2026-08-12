package com.example.gareter.ui.screens

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.Card
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Slider
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.gareter.data.model.Station
import com.example.gareter.data.stations.SNCF_STATIONS
import com.example.gareter.data.stations.StationRef
import com.example.gareter.ui.viewmodel.CreateRouteViewModel
import com.example.gareter.ui.viewmodel.GeoSearchResult
import android.graphics.Color
import android.location.Location
import android.view.MotionEvent
import com.example.gareter.service.LocationService
import org.osmdroid.config.Configuration
import org.osmdroid.events.MapEventsReceiver
import org.osmdroid.tileprovider.tilesource.TileSourceFactory
import org.osmdroid.util.GeoPoint
import org.osmdroid.views.MapView
import org.osmdroid.views.overlay.MapEventsOverlay
import org.osmdroid.views.overlay.Marker
import org.osmdroid.views.overlay.Polygon
import java.util.UUID

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun StationPickerScreen(
    onBack: () -> Unit,
    viewModel: CreateRouteViewModel = viewModel()
) {
    var selectedTab by remember { mutableStateOf(0) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Ajouter un arrêt") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, "Retour")
                    }
                }
            )
        }
    ) { padding ->
        Column(Modifier.padding(padding)) {
            TabRow(selectedTabIndex = selectedTab) {
                Tab(selected = selectedTab == 0, onClick = { selectedTab = 0 }, text = { Text("Gares SNCF") })
                Tab(selected = selectedTab == 1, onClick = { selectedTab = 1 }, text = { Text("Mes arrêts") })
                Tab(selected = selectedTab == 2, onClick = { selectedTab = 2 }, text = { Text("Autre arrêt") })
            }

            when (selectedTab) {
                0 -> Column(Modifier.verticalScroll(rememberScrollState())) {
                    SNCFTab(onBack = onBack, viewModel = viewModel)
                }
                1 -> Column(Modifier.verticalScroll(rememberScrollState())) {
                    SavedStopsTab(onBack = onBack, viewModel = viewModel)
                }
                2 -> Column(Modifier.verticalScroll(rememberScrollState())) {
                    OtherStopTab(onBack = onBack, viewModel = viewModel)
                }
            }
        }
    }
}

// ─────────────────────────────────────────────
// TAB 0 : Gares SNCF (autocomplete depuis liste locale)
// ─────────────────────────────────────────────
@Composable
private fun SNCFTab(onBack: () -> Unit, viewModel: CreateRouteViewModel) {
    var query by remember { mutableStateOf("") }
    var selectedRef by remember { mutableStateOf<StationRef?>(null) }
    var approachRadius by remember { mutableStateOf(300f) }
    var arrivalRadius by remember { mutableStateOf(80f) }

    val suggestions = remember(query) {
        if (query.length < 2) emptyList()
        else SNCF_STATIONS.filter { it.name.contains(query, ignoreCase = true) }.take(20)
    }

    Column(Modifier.padding(16.dp)) {
        OutlinedTextField(
            value = query,
            onValueChange = { query = it; selectedRef = null },
            label = { Text("Rechercher (ex: Grenoble, Lyon…)") },
            modifier = Modifier.fillMaxWidth(),
            singleLine = true
        )

        if (selectedRef == null && suggestions.isNotEmpty()) {
            Spacer(Modifier.height(8.dp))
            Column {
                suggestions.forEach { ref ->
                    ListItem(
                        headlineContent = { Text(ref.name) },
                        modifier = Modifier.clickable { selectedRef = ref; query = ref.name }
                    )
                    HorizontalDivider()
                }
            }
        }

        selectedRef?.let { ref ->
            Spacer(Modifier.height(8.dp))
            StationMapAndSliders(
                name = ref.name,
                lat = ref.latitude,
                lon = ref.longitude,
                approachRadius = approachRadius,
                arrivalRadius = arrivalRadius,
                onApproachChange = { approachRadius = it },
                onArrivalChange = { if (it < approachRadius) arrivalRadius = it },
                onAdd = {
                    viewModel.addStation(
                        Station(UUID.randomUUID().toString(), ref.name, ref.latitude, ref.longitude,
                            approachRadius.toInt(), arrivalRadius.toInt())
                    )
                    onBack()
                }
            )
        }
    }
}

// ─────────────────────────────────────────────
// TAB 1 : Mes arrêts (base de données personnelle)
// ─────────────────────────────────────────────
@Composable
private fun SavedStopsTab(onBack: () -> Unit, viewModel: CreateRouteViewModel) {
    val customStations by viewModel.customStations.collectAsState()
    val currentStations by viewModel.stations.collectAsState()

    Column(Modifier.padding(16.dp)) {
        if (customStations.isEmpty()) {
            Box(Modifier.fillMaxWidth().padding(32.dp), contentAlignment = Alignment.Center) {
                Text(
                    "Aucun arrêt sauvegardé.\nAjoutez des arrêts via l'onglet « Autre arrêt »,\nils apparaîtront ici pour être réutilisés.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center
                )
            }
        } else {
            Text(
                "${customStations.size} arrêt(s) dans votre base personnelle",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(Modifier.height(8.dp))
            customStations.forEach { station ->
                val alreadyInRoute = currentStations.any { it.name == station.name && it.latitude == station.latitude }
                
                Card(Modifier.fillMaxWidth().padding(vertical = 4.dp)) {
                    Row(Modifier.padding(horizontal = 12.dp, vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                        Column(Modifier.weight(1f)) {
                            Text(station.name, fontWeight = FontWeight.Bold)
                            Text(
                                "${"%.4f".format(station.latitude)}, ${"%.4f".format(station.longitude)}",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        if (alreadyInRoute) {
                            Text(
                                "Déjà ajouté",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.padding(end = 8.dp)
                            )
                        } else {
                            OutlinedButton(
                                onClick = {
                                    viewModel.addStation(
                                        Station(UUID.randomUUID().toString(), station.name,
                                            station.latitude, station.longitude,
                                            station.approachRadius, station.arrivalRadius)
                                    )
                                    onBack()
                                }
                            ) { Text("Ajouter") }
                        }
                        IconButton(onClick = { viewModel.deleteCustomStation(station.id) }) {
                            Icon(Icons.Default.Delete, "Retirer de mes arrêts", tint = MaterialTheme.colorScheme.error)
                        }
                    }
                }
            }
        }
    }
}

// ─────────────────────────────────────────────
// TAB 2 : Autre arrêt (Nominatim + saisie manuelle sur carte)
// ─────────────────────────────────────────────
@Composable
private fun OtherStopTab(onBack: () -> Unit, viewModel: CreateRouteViewModel) {
    var isManualMode by remember { mutableStateOf(false) }

    fun addAndSave(station: Station) {
        viewModel.addStation(station)
        viewModel.saveToCustomDb(station)
        onBack()
    }

    if (isManualMode) {
        ManualMapEntry(
            onBack = { isManualMode = false },
            onAdd = ::addAndSave
        )
    } else {
        NominatimSearch(
            viewModel = viewModel,
            onSwitchManual = { isManualMode = true },
            onAdd = { name, lat, lon, approach, arrival ->
                addAndSave(Station(UUID.randomUUID().toString(), name, lat, lon, approach, arrival))
            }
        )
    }
}

@Composable
private fun NominatimSearch(
    viewModel: CreateRouteViewModel,
    onSwitchManual: () -> Unit,
    onAdd: (name: String, lat: Double, lon: Double, approach: Int, arrival: Int) -> Unit
) {
    val geoResults by viewModel.geoResults.collectAsState()
    val geoSearching by viewModel.geoSearching.collectAsState()
    val geoError by viewModel.geoError.collectAsState()

    var query by remember { mutableStateOf("") }
    var selectedResult by remember { mutableStateOf<GeoSearchResult?>(null) }
    var approachRadius by remember { mutableStateOf(300f) }
    var arrivalRadius by remember { mutableStateOf(80f) }

    Column(Modifier.padding(16.dp)) {
        Text("Recherche par adresse ou lieu", fontWeight = FontWeight.Bold, style = MaterialTheme.typography.titleSmall)
        Spacer(Modifier.height(8.dp))

        Row(verticalAlignment = Alignment.CenterVertically) {
            OutlinedTextField(
                value = query,
                onValueChange = { query = it; selectedResult = null; viewModel.clearGeoResults() },
                label = { Text("Ex: Montmélian, Savoie") },
                modifier = Modifier.weight(1f),
                singleLine = true
            )
            Spacer(Modifier.padding(4.dp))
            Button(
                onClick = { viewModel.searchGeocode(query) },
                enabled = query.length >= 3 && !geoSearching
            ) { Text("Chercher") }
        }

        if (geoSearching) {
            Box(Modifier.fillMaxWidth().padding(16.dp), contentAlignment = Alignment.Center) {
                CircularProgressIndicator()
            }
        }

        geoError?.let {
            Text(it, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall,
                modifier = Modifier.padding(top = 4.dp))
        }

        if (selectedResult == null && geoResults.isNotEmpty()) {
            Spacer(Modifier.height(8.dp))
            Column {
                geoResults.forEach { result ->
                    ListItem(
                        headlineContent = { Text(result.name) },
                        supportingContent = {
                            Text(result.fullAddress, maxLines = 1, style = MaterialTheme.typography.bodySmall)
                        },
                        modifier = Modifier.clickable { selectedResult = result }
                    )
                    HorizontalDivider()
                }
            }
        }

        selectedResult?.let { result ->
            Spacer(Modifier.height(8.dp))
            StationMapAndSliders(
                name = result.name,
                lat = result.latitude,
                lon = result.longitude,
                approachRadius = approachRadius,
                arrivalRadius = arrivalRadius,
                onApproachChange = { approachRadius = it },
                onArrivalChange = { if (it < approachRadius) arrivalRadius = it },
                onAdd = { onAdd(result.name, result.latitude, result.longitude, approachRadius.toInt(), arrivalRadius.toInt()) }
            )
        }

        Spacer(Modifier.height(16.dp))
        HorizontalDivider()
        Spacer(Modifier.height(8.dp))
        OutlinedButton(onClick = onSwitchManual, modifier = Modifier.fillMaxWidth()) {
            Text("Placer un arrêt sur la carte manuellement")
        }
    }
}

// Saisie manuelle : tap sur la carte pour définir les coordonnées
@Composable
private fun ManualMapEntry(
    onBack: () -> Unit,
    onAdd: (Station) -> Unit
) {
    var stationName by remember { mutableStateOf("") }
    var approachRadius by remember { mutableStateOf(300f) }
    var arrivalRadius by remember { mutableStateOf(80f) }
    val tappedPoint = remember { mutableStateOf<GeoPoint?>(null) }
    val markerHolder = remember { arrayOfNulls<Marker>(1) }   // non-reactive — évite les recompositions parasites

    Column(Modifier.padding(16.dp)) {
        OutlinedButton(onClick = onBack) { Text("← Retour à la recherche") }
        Spacer(Modifier.height(8.dp))

        Text("Tapez sur la carte pour placer l'arrêt", fontWeight = FontWeight.Bold, style = MaterialTheme.typography.titleSmall)
        Spacer(Modifier.height(8.dp))

        Box(modifier = Modifier.fillMaxWidth().height(280.dp).clipToBounds()) {
            AndroidView(
                factory = { ctx ->
                    Configuration.getInstance().userAgentValue = ctx.packageName
                    MapView(ctx).apply {
                        setTileSource(TileSourceFactory.MAPNIK)
                        setMultiTouchControls(true)
                        controller.setZoom(6.0)
                        controller.setCenter(GeoPoint(46.6, 2.3))

                        // Zoom directement sur la position actuelle de l'utilisateur si disponible
                        val lastGps = LocationService.currentLocation.value
                        if (lastGps != null) {
                            controller.setZoom(15.0)
                            controller.setCenter(GeoPoint(lastGps.latitude, lastGps.longitude))
                        } else {
                            try {
                                com.google.android.gms.location.LocationServices
                                    .getFusedLocationProviderClient(ctx)
                                    .lastLocation
                                    .addOnSuccessListener { loc ->
                                        if (loc != null) {
                                            controller.setZoom(15.0)
                                            controller.setCenter(GeoPoint(loc.latitude, loc.longitude))
                                        }
                                    }
                            } catch (e: SecurityException) {
                                // Pas de permission ou service indisponible, on reste sur le fallback national
                            }
                        }

                        // Empêche le conteneur scrollable d'intercepter les gestes sur la carte
                        setOnTouchListener { v, event ->
                            when (event.action) {
                                MotionEvent.ACTION_DOWN,
                                MotionEvent.ACTION_MOVE -> v.parent?.requestDisallowInterceptTouchEvent(true)
                                MotionEvent.ACTION_UP,
                                MotionEvent.ACTION_CANCEL -> v.parent?.requestDisallowInterceptTouchEvent(false)
                            }
                            false
                        }

                        val receiver = object : MapEventsReceiver {
                            override fun singleTapConfirmedHelper(p: GeoPoint?): Boolean {
                                p ?: return false
                                markerHolder[0]?.let { overlays.remove(it) }
                                val m = Marker(this@apply).apply {
                                    position = p
                                    title = "Arrêt sélectionné"
                                    setAnchor(Marker.ANCHOR_CENTER, Marker.ANCHOR_BOTTOM)
                                }
                                overlays.add(m)
                                markerHolder[0] = m
                                tappedPoint.value = p
                                postInvalidate()
                                return true
                            }
                            override fun longPressHelper(p: GeoPoint?): Boolean = false
                        }
                        overlays.add(0, MapEventsOverlay(receiver))
                    }
                },
                update = { mapView ->
                    val center = tappedPoint.value ?: return@AndroidView
                    mapView.overlays.removeAll { it is Polygon }
                    mapView.overlays.add(0, Polygon(mapView).apply {
                        points = Polygon.pointsAsCircle(center, approachRadius.toDouble())
                        fillPaint.color = Color.argb(30, 0, 120, 220)
                        outlinePaint.color = Color.argb(160, 0, 120, 220)
                        outlinePaint.strokeWidth = 2f
                    })
                    mapView.overlays.add(1, Polygon(mapView).apply {
                        points = Polygon.pointsAsCircle(center, arrivalRadius.toDouble())
                        fillPaint.color = Color.argb(40, 220, 60, 0)
                        outlinePaint.color = Color.argb(180, 220, 60, 0)
                        outlinePaint.strokeWidth = 2f
                    })
                    mapView.postInvalidate()
                },
                modifier = Modifier.fillMaxSize()
            )
        }

        tappedPoint.value?.let { point ->
            Spacer(Modifier.height(8.dp))
            Text(
                "Position : ${"%.5f".format(point.latitude)}, ${"%.5f".format(point.longitude)}",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(Modifier.height(8.dp))
            OutlinedTextField(
                value = stationName,
                onValueChange = { stationName = it },
                label = { Text("Nom de l'arrêt") },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true
            )
            Spacer(Modifier.height(8.dp))
            Text("Rayon d'approche : ${approachRadius.toInt()} m", fontWeight = FontWeight.Medium)
            Slider(value = approachRadius, onValueChange = { approachRadius = it }, valueRange = 100f..1500f, steps = 27, modifier = Modifier.fillMaxWidth())
            Text("Rayon d'arrivée : ${arrivalRadius.toInt()} m", fontWeight = FontWeight.Medium)
            Slider(value = arrivalRadius, onValueChange = { if (it < approachRadius) arrivalRadius = it }, valueRange = 20f..300f, steps = 27, modifier = Modifier.fillMaxWidth())
            Spacer(Modifier.height(8.dp))
            Button(
                onClick = {
                    onAdd(Station(
                        UUID.randomUUID().toString(),
                        stationName.ifBlank { "Arrêt (${"%.3f".format(point.latitude)}, ${"%.3f".format(point.longitude)})" },
                        point.latitude, point.longitude,
                        approachRadius.toInt(), arrivalRadius.toInt()
                    ))
                },
                modifier = Modifier.fillMaxWidth(),
                enabled = true
            ) { Text("Ajouter cet arrêt") }
        } ?: run {
            Box(Modifier.fillMaxWidth().padding(24.dp), contentAlignment = Alignment.Center) {
                Text("Tapez sur la carte pour choisir une position", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    }
}

// ─────────────────────────────────────────────
// Composant partagé : carte + sliders + bouton Ajouter
// ─────────────────────────────────────────────
@Composable
private fun StationMapAndSliders(
    name: String,
    lat: Double,
    lon: Double,
    approachRadius: Float,
    arrivalRadius: Float,
    onApproachChange: (Float) -> Unit,
    onArrivalChange: (Float) -> Unit,
    onAdd: () -> Unit
) {
    Column {
        Box(modifier = Modifier.fillMaxWidth().height(200.dp).clipToBounds()) {
            val center = GeoPoint(lat, lon)
            AndroidView(
                factory = { ctx ->
                    Configuration.getInstance().userAgentValue = ctx.packageName
                    MapView(ctx).apply {
                        setTileSource(TileSourceFactory.MAPNIK)
                        setMultiTouchControls(true)
                        controller.setZoom(14.0)
                        controller.setCenter(center)

                        // Empêche le conteneur scrollable d'intercepter les gestes sur la carte
                        setOnTouchListener { v, event ->
                            when (event.action) {
                                MotionEvent.ACTION_DOWN,
                                MotionEvent.ACTION_MOVE -> v.parent?.requestDisallowInterceptTouchEvent(true)
                                MotionEvent.ACTION_UP,
                                MotionEvent.ACTION_CANCEL -> v.parent?.requestDisallowInterceptTouchEvent(false)
                            }
                            false
                        }

                        overlays.add(Marker(this).apply {
                            position = center
                            title = name
                            setAnchor(Marker.ANCHOR_CENTER, Marker.ANCHOR_BOTTOM)
                        })
                    }
                },
                update = { mapView ->
                    mapView.overlays.removeAll { it is Polygon }
                    mapView.overlays.add(0, Polygon(mapView).apply {
                        points = Polygon.pointsAsCircle(center, approachRadius.toDouble())
                        fillPaint.color = Color.argb(30, 0, 120, 220)
                        outlinePaint.color = Color.argb(160, 0, 120, 220)
                        outlinePaint.strokeWidth = 2f
                    })
                    mapView.overlays.add(1, Polygon(mapView).apply {
                        points = Polygon.pointsAsCircle(center, arrivalRadius.toDouble())
                        fillPaint.color = Color.argb(40, 220, 60, 0)
                        outlinePaint.color = Color.argb(180, 220, 60, 0)
                        outlinePaint.strokeWidth = 2f
                    })
                    mapView.postInvalidate()
                },
                modifier = Modifier.fillMaxSize()
            )
        }
        Spacer(Modifier.height(12.dp))
        Text("Rayon d'approche : ${approachRadius.toInt()} m", fontWeight = FontWeight.Medium)
        Slider(value = approachRadius, onValueChange = onApproachChange, valueRange = 100f..1500f, steps = 27, modifier = Modifier.fillMaxWidth())
        Text("Rayon d'arrivée : ${arrivalRadius.toInt()} m", fontWeight = FontWeight.Medium)
        Slider(value = arrivalRadius, onValueChange = onArrivalChange, valueRange = 20f..300f, steps = 27, modifier = Modifier.fillMaxWidth())
        Spacer(Modifier.height(8.dp))
        Button(onClick = onAdd, modifier = Modifier.fillMaxWidth()) {
            Text("Ajouter « $name »")
        }
    }
}
