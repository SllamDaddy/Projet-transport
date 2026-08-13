package com.example.gareter.ui.screens

import android.graphics.Color
import android.speech.tts.TextToSpeech
import android.view.MotionEvent
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import com.example.gareter.data.model.Station
import com.example.gareter.ui.viewmodel.CreateRouteViewModel
import kotlinx.coroutines.delay
import org.osmdroid.events.MapEventsReceiver
import org.osmdroid.tileprovider.tilesource.TileSourceFactory
import org.osmdroid.util.GeoPoint
import org.osmdroid.views.MapView
import org.osmdroid.views.overlay.MapEventsOverlay
import org.osmdroid.views.overlay.Marker
import org.osmdroid.views.overlay.Polygon
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun StationEditScreen(
    stationId: String,
    onBack: () -> Unit,
    viewModel: CreateRouteViewModel,
) {
    val station = remember(stationId) { viewModel.getStation(stationId) } ?: run {
        onBack(); return
    }

    var stationName by remember { mutableStateOf(station.name) }
    var approachMsg by remember { mutableStateOf(station.approachMessage ?: "") }
    var arrivalMsg by remember { mutableStateOf(station.arrivalMessage ?: "") }
    var approachRadius by remember { mutableFloatStateOf(station.approachRadius.toFloat()) }
    var arrivalRadius by remember { mutableFloatStateOf(station.arrivalRadius.toFloat()) }

    val tappedPoint = remember { mutableStateOf<GeoPoint?>(null) }
    val markerHolder = remember { arrayOfNulls<Marker>(1) }

    var cachedApproachPoints by remember { mutableStateOf<List<GeoPoint>?>(null) }
    var cachedArrivalPoints by remember { mutableStateOf<List<GeoPoint>?>(null) }
    var lastCachedApproachRadius by remember { mutableFloatStateOf(-1f) }
    var lastCachedArrivalRadius by remember { mutableFloatStateOf(-1f) }
    var lastCachedCenter by remember { mutableStateOf<GeoPoint?>(null) }

    val initialCenter = remember { GeoPoint(station.latitude, station.longitude) }

    // Précalcul des cercles hors du LazyColumn pour éviter les recalculs à chaque recomposition
    LaunchedEffect(approachRadius, arrivalRadius, tappedPoint.value) {
        delay(300)
        val center = tappedPoint.value ?: initialCenter
        if (lastCachedCenter != center ||
            lastCachedApproachRadius != approachRadius ||
            lastCachedArrivalRadius != arrivalRadius
        ) {
            lastCachedCenter = center
            lastCachedApproachRadius = approachRadius
            lastCachedArrivalRadius = arrivalRadius
            cachedApproachPoints = Polygon.pointsAsCircle(center, approachRadius.toDouble())
            cachedArrivalPoints = Polygon.pointsAsCircle(center, arrivalRadius.toDouble())
        }
    }

    val context = LocalContext.current
    val ttsReady = remember { mutableStateOf(false) }
    val tts = remember {
        var instance: TextToSpeech? = null
        instance = TextToSpeech(context) { status ->
            if (status == TextToSpeech.SUCCESS) {
                instance?.language = Locale.FRENCH
                ttsReady.value = true
            }
        }
        instance
    }
    DisposableEffect(Unit) { onDispose { tts.shutdown() } }

    fun speakApproach() {
        if (!ttsReady.value) return
        val msg = approachMsg.ifBlank { "Prochain arrêt : {nom}" }
            .replace("{nom}", stationName.ifBlank { station.name })
            .replace("{distance}", "350")
        tts.speak(msg, TextToSpeech.QUEUE_FLUSH, null, "test_approach")
    }

    fun speakArrival() {
        if (!ttsReady.value) return
        val msg = arrivalMsg.ifBlank { "Arrêt : {nom}" }
            .replace("{nom}", stationName.ifBlank { station.name })
        tts.speak(msg, TextToSpeech.QUEUE_FLUSH, null, "test_arrival")
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Modifier l'arrêt") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, "Retour")
                    }
                },
                actions = {
                    TextButton(onClick = {
                        viewModel.updateStation(
                            station.copy(
                                name = stationName.trim().ifBlank { station.name },
                                latitude = tappedPoint.value?.latitude ?: station.latitude,
                                longitude = tappedPoint.value?.longitude ?: station.longitude,
                                approachMessage = approachMsg.ifBlank { null },
                                arrivalMessage = arrivalMsg.ifBlank { null },
                                approachRadius = approachRadius.toInt(),
                                arrivalRadius = arrivalRadius.toInt(),
                            )
                        )
                        onBack()
                    }) {
                        Text("Enregistrer", fontWeight = FontWeight.Bold)
                    }
                },
            )
        },
    ) { padding ->
        // LazyColumn au lieu de Column+verticalScroll : les AndroidView (MapView) sont
        // positionnés à leurs coordonnées Y réelles et scrollent correctement avec le contenu.
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {

            // ── Nom de l'arrêt ──
            item {
                OutlinedTextField(
                    value = stationName,
                    onValueChange = { stationName = it },
                    label = { Text("Nom de l'arrêt") },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true,
                )
            }

            // ── Position GPS ──
            item {
                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text("Position GPS", fontWeight = FontWeight.SemiBold, style = MaterialTheme.typography.bodyMedium)
                    Text(
                        "Tapez sur la carte pour repositionner l'arrêt",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }

            // ── Carte ──
            item {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(260.dp)
                        .clip(RectangleShape),
                ) {
                    AndroidView(
                        factory = { ctx ->
                            MapView(ctx).apply {
                                setTileSource(TileSourceFactory.MAPNIK)
                                setMultiTouchControls(true)
                                controller.setZoom(14.0)
                                controller.setCenter(initialCenter)
                                minZoomLevel = 5.0
                                maxZoomLevel = 19.0

                                setOnTouchListener { v, event ->
                                    when (event.action) {
                                        MotionEvent.ACTION_DOWN,
                                        MotionEvent.ACTION_MOVE -> v.parent?.requestDisallowInterceptTouchEvent(true)
                                        MotionEvent.ACTION_UP,
                                        MotionEvent.ACTION_CANCEL -> v.parent?.requestDisallowInterceptTouchEvent(false)
                                    }
                                    false
                                }

                                val initMarker = Marker(this).apply {
                                    position = initialCenter
                                    title = station.name
                                    setAnchor(Marker.ANCHOR_CENTER, Marker.ANCHOR_BOTTOM)
                                }
                                overlays.add(initMarker)
                                markerHolder[0] = initMarker

                                val receiver = object : MapEventsReceiver {
                                    override fun singleTapConfirmedHelper(p: GeoPoint?): Boolean {
                                        p ?: return false
                                        markerHolder[0]?.let { overlays.remove(it) }
                                        val m = Marker(this@apply).apply {
                                            position = p
                                            setAnchor(Marker.ANCHOR_CENTER, Marker.ANCHOR_BOTTOM)
                                        }
                                        overlays.add(m)
                                        markerHolder[0] = m
                                        tappedPoint.value = p
                                        invalidate()
                                        return true
                                    }
                                    override fun longPressHelper(p: GeoPoint?): Boolean = false
                                }
                                overlays.add(0, MapEventsOverlay(receiver))
                            }
                        },
                        update = { mapView ->
                            val center = tappedPoint.value ?: initialCenter
                            mapView.overlays.removeAll { it is Polygon }

                            val approachPts = cachedApproachPoints
                                ?: Polygon.pointsAsCircle(center, approachRadius.toDouble())
                            mapView.overlays.add(0, Polygon(mapView).apply {
                                points = approachPts
                                fillPaint.color = Color.argb(30, 0, 120, 220)
                                outlinePaint.color = Color.argb(160, 0, 120, 220)
                                outlinePaint.strokeWidth = 2f
                            })

                            val arrivalPts = cachedArrivalPoints
                                ?: Polygon.pointsAsCircle(center, arrivalRadius.toDouble())
                            mapView.overlays.add(1, Polygon(mapView).apply {
                                points = arrivalPts
                                fillPaint.color = Color.argb(40, 220, 60, 0)
                                outlinePaint.color = Color.argb(180, 220, 60, 0)
                                outlinePaint.strokeWidth = 2f
                            })

                            mapView.invalidate()
                        },
                        modifier = Modifier.fillMaxSize(),
                    )
                }
            }

            // ── Coordonnées actuelles ──
            item {
                val displayLat = tappedPoint.value?.latitude ?: station.latitude
                val displayLon = tappedPoint.value?.longitude ?: station.longitude
                Text(
                    if (tappedPoint.value != null)
                        "✓ Nouvelle position : ${"%.5f".format(displayLat)}, ${"%.5f".format(displayLon)}"
                    else
                        "Actuel : ${"%.5f".format(displayLat)}, ${"%.5f".format(displayLon)}",
                    style = MaterialTheme.typography.bodySmall,
                    color = if (tappedPoint.value != null) MaterialTheme.colorScheme.primary
                            else MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }

            item { HorizontalDivider() }

            // ── Annonce d'approche ──
            item {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("Annonce d'approche", fontWeight = FontWeight.SemiBold, style = MaterialTheme.typography.bodyMedium)
                    OutlinedTextField(
                        value = approachMsg,
                        onValueChange = { approachMsg = it },
                        placeholder = { Text("Prochain arrêt dans environ {distance} mètres : {nom}") },
                        label = { Text("Message (vide = défaut)") },
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = false,
                        minLines = 2,
                    )
                    Text(
                        "Variables : {nom}  ·  {distance}",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    OutlinedButton(
                        onClick = ::speakApproach,
                        enabled = ttsReady.value,
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Icon(Icons.Default.PlayArrow, null, Modifier.size(16.dp))
                        Spacer(Modifier.width(4.dp))
                        Text(if (ttsReady.value) "Tester l'annonce d'approche" else "TTS en cours d'init…")
                    }
                }
            }

            item { HorizontalDivider() }

            // ── Annonce d'arrivée ──
            item {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("Annonce d'arrivée", fontWeight = FontWeight.SemiBold, style = MaterialTheme.typography.bodyMedium)
                    OutlinedTextField(
                        value = arrivalMsg,
                        onValueChange = { arrivalMsg = it },
                        placeholder = { Text("Arrêt : {nom}") },
                        label = { Text("Message (vide = défaut)") },
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true,
                    )
                    Text(
                        "Variable : {nom}",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    OutlinedButton(
                        onClick = ::speakArrival,
                        enabled = ttsReady.value,
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Icon(Icons.Default.PlayArrow, null, Modifier.size(16.dp))
                        Spacer(Modifier.width(4.dp))
                        Text(if (ttsReady.value) "Tester l'annonce d'arrivée" else "TTS en cours d'init…")
                    }
                }
            }

            item { HorizontalDivider() }

            // ── Rayons ──
            item {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("Rayon d'approche : ${approachRadius.toInt()} m", style = MaterialTheme.typography.bodyMedium)
                    Slider(
                        value = approachRadius,
                        onValueChange = { approachRadius = it },
                        valueRange = 100f..1500f,
                        steps = 27,
                        modifier = Modifier.fillMaxWidth(),
                    )
                    Text("Rayon d'arrivée : ${arrivalRadius.toInt()} m", style = MaterialTheme.typography.bodyMedium)
                    Slider(
                        value = arrivalRadius,
                        onValueChange = { if (it < approachRadius) arrivalRadius = it },
                        valueRange = 20f..300f,
                        steps = 27,
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
            }

            // ── Bouton Enregistrer ──
            item {
                Button(
                    onClick = {
                        viewModel.updateStation(
                            station.copy(
                                name = stationName.trim().ifBlank { station.name },
                                latitude = tappedPoint.value?.latitude ?: station.latitude,
                                longitude = tappedPoint.value?.longitude ?: station.longitude,
                                approachMessage = approachMsg.ifBlank { null },
                                arrivalMessage = arrivalMsg.ifBlank { null },
                                approachRadius = approachRadius.toInt(),
                                arrivalRadius = arrivalRadius.toInt(),
                            )
                        )
                        onBack()
                    },
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Text("Enregistrer les modifications")
                }
            }
        }
    }
}
