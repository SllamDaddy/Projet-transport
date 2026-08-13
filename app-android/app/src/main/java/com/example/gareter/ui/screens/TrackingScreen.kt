package com.example.gareter.ui.screens

import android.bluetooth.BluetoothDevice
import android.location.Location
import android.view.MotionEvent
import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.gareter.data.model.TicketSale
import com.example.gareter.data.model.TicketType
import com.example.gareter.data.repository.OSRMService
import com.example.gareter.data.service.PrinterManager
import com.example.gareter.data.service.ReceiptManager
import com.example.gareter.service.LocationService
import com.example.gareter.ui.theme.*
import com.example.gareter.ui.viewmodel.CaisseViewModel
import com.example.gareter.ui.viewmodel.HomeViewModel
import kotlinx.coroutines.launch
import org.osmdroid.config.Configuration
import org.osmdroid.tileprovider.tilesource.TileSourceFactory
import org.osmdroid.util.GeoPoint
import org.osmdroid.views.MapView
import org.osmdroid.views.overlay.Marker
import org.osmdroid.views.overlay.Polygon
import org.osmdroid.views.overlay.Polyline
import kotlin.math.roundToInt

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TrackingScreen(
    onBack: () -> Unit,
    viewModel: HomeViewModel = viewModel(),
) {
    val trackingState by viewModel.trackingState.collectAsStateWithLifecycle()
    val activeRoute by viewModel.activeRoute.collectAsStateWithLifecycle()
    val currentLocation by viewModel.currentLocation.collectAsStateWithLifecycle()
    val driverAgent by viewModel.driverAgent.collectAsStateWithLifecycle()

    val context = LocalContext.current
    var showVenteBottomSheet by remember { mutableStateOf(false) }
    var showReceiptOptions by remember { mutableStateOf(false) }
    var lastSale by remember { mutableStateOf<com.example.gareter.data.model.TicketSale?>(null) }

    LaunchedEffect(trackingState) {
        if (trackingState is LocationService.ServiceState.Idle) onBack()
    }

    // BottomSheet Vente
    if (showVenteBottomSheet) {
        CaisseVenteBottomSheet(
            onDismiss = { showVenteBottomSheet = false },
            onSellTicket = { type, price ->
                // 1. Créer la vente dans Caisse
                // 2. Afficher options reçu
                // 3. Imprimer ticket (si imprimante disponible)
                // lastSale = sale
                // showReceiptOptions = true
                // TODO: Intégrer avec CaisseViewModel.sellTicket()
            },
        )
    }

    // Options de reçu (SMS / Email / Imprimer / Rien)
    if (showReceiptOptions && lastSale != null) {
        ReceiptOptionsBottomSheet(
            sale = lastSale!!,
            agentNumber = driverAgent ?: "N/A",
            onDismiss = { showReceiptOptions = false },
        )
    }

    val state = trackingState as? LocationService.ServiceState.Tracking

    val lifecycleOwner = LocalLifecycleOwner.current

    // Auto-center state
    var isAutoCenterEnabled by remember { mutableStateOf(true) }

    // OSRM routing points
    var osrmPoints by remember { mutableStateOf<List<GeoPoint>>(emptyList()) }

    LaunchedEffect(activeRoute) {
        val route = activeRoute
        if (route != null && route.stations.size >= 2) {
            val points = OSRMService.getRoutePoints(route.stations)
            if (points.isNotEmpty()) {
                osrmPoints = points
            }
        }
    }

    // MapView instance instantiated once and remembered
    val mapView = remember {
        MapView(context).apply {
            setTileSource(TileSourceFactory.MAPNIK)
            setMultiTouchControls(true)
            minZoomLevel = 5.0
            maxZoomLevel = 19.0
            controller.setZoom(14.0)
        }
    }

    // Center to first station initially when route is loaded
    val firstStation = activeRoute?.stations?.firstOrNull()
    LaunchedEffect(firstStation) {
        firstStation?.let {
            mapView.controller.setCenter(GeoPoint(it.latitude, it.longitude))
        }
    }

    // Centrer automatiquement sur la position du bus si activé
    val location = currentLocation
    LaunchedEffect(location, isAutoCenterEnabled) {
        if (location != null) {
            if (isAutoCenterEnabled) {
                mapView.controller.animateTo(GeoPoint(location.latitude, location.longitude))
                if (location.hasBearing()) {
                    mapView.mapOrientation = -location.bearing
                } else {
                    mapView.mapOrientation = 0f
                }
            } else {
                mapView.mapOrientation = 0f
            }
        } else {
            mapView.mapOrientation = 0f
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
    ) {
        // ── En-tête gradient ──
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .background(Brush.verticalGradient(listOf(Violet700, Violet600)))
                .padding(top = 48.dp, start = 8.dp, end = 16.dp, bottom = 16.dp),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                IconButton(onClick = onBack) {
                    Icon(Icons.AutoMirrored.Filled.ArrowBack, "Retour", tint = Color.White)
                }
                Column(Modifier.weight(1f)) {
                    Text(
                        "Trajet en cours",
                        style = MaterialTheme.typography.bodyMedium,
                        color = Color.White.copy(alpha = 0.8f),
                    )
                    Text(
                        activeRoute?.title ?: state?.routeTitle ?: "",
                        style = MaterialTheme.typography.titleLarge,
                        color = Color.White,
                    )
                }
                // Bouton arrêt
                FilledTonalButton(
                    onClick = { viewModel.stopTracking() },
                    colors = ButtonDefaults.filledTonalButtonColors(
                        containerColor = DangerRed,
                        contentColor = Color.White,
                    ),
                    shape = RoundedCornerShape(12.dp),
                ) {
                    Icon(Icons.Default.Stop, null, modifier = Modifier.size(16.dp))
                    Spacer(Modifier.width(4.dp))
                    Text("Arrêter", fontWeight = FontWeight.SemiBold)
                }
            }
        }

        // ── Zone Carte (Écran divisé - Hauteur fixe 300dp) ──
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(300.dp)
        ) {
            AndroidView(
                modifier = Modifier.fillMaxSize(),
                factory = {
                    Configuration.getInstance().userAgentValue = context.packageName
                    mapView.apply {
                        setOnTouchListener { v, event ->
                            when (event.action) {
                                MotionEvent.ACTION_DOWN,
                                MotionEvent.ACTION_MOVE -> {
                                    v.parent?.requestDisallowInterceptTouchEvent(true)
                                    isAutoCenterEnabled = false
                                }
                                MotionEvent.ACTION_UP,
                                MotionEvent.ACTION_CANCEL -> {
                                    v.parent?.requestDisallowInterceptTouchEvent(false)
                                }
                            }
                            false
                        }
                    }
                },
                update = { view ->
                    view.overlays.clear()

                    val route = activeRoute
                    val busLoc = currentLocation
                    val nextStopName = state?.nextStationName

                    // 1. Dessiner le tracé de la ligne (Polyline)
                    if (route != null && route.stations.isNotEmpty()) {
                        val routePoints = if (osrmPoints.isNotEmpty()) osrmPoints else route.stations.map { GeoPoint(it.latitude, it.longitude) }
                        val polyline = Polyline(view).apply {
                            setPoints(routePoints)
                            outlinePaint.color = android.graphics.Color.argb(180, 100, 100, 255)
                            outlinePaint.strokeWidth = 6f
                        }
                        view.overlays.add(polyline)

                        // 2. Dessiner les marqueurs de gares
                        val nextIdx = route.stations.indexOfFirst { it.name == nextStopName }
                        route.stations.forEachIndexed { idx, station ->
                            val stationPoint = GeoPoint(station.latitude, station.longitude)
                            val marker = Marker(view).apply {
                                position = stationPoint
                                title = station.name
                                subDescription = "Arrêt ${idx + 1}/${route.stations.size}"
                                setAnchor(Marker.ANCHOR_CENTER, Marker.ANCHOR_BOTTOM)

                                val markerColor = when {
                                    nextIdx >= 0 && idx < nextIdx -> android.graphics.Color.GREEN
                                    idx == nextIdx -> android.graphics.Color.BLUE
                                    else -> android.graphics.Color.GRAY
                                }

                                val size = 40
                                val bitmap = android.graphics.Bitmap.createBitmap(size, size, android.graphics.Bitmap.Config.ARGB_8888)
                                val canvas = android.graphics.Canvas(bitmap)
                                val paint = android.graphics.Paint().apply {
                                    color = markerColor
                                    style = android.graphics.Paint.Style.FILL
                                    isAntiAlias = true
                                }
                                canvas.drawCircle(size / 2f, size / 2f, size / 2f - 4, paint)

                                paint.color = android.graphics.Color.WHITE
                                canvas.drawCircle(size / 2f, size / 2f, size / 6f, paint)

                                icon = android.graphics.drawable.BitmapDrawable(view.context.resources, bitmap)
                            }
                            view.overlays.add(marker)

                            // 3. Dessiner les cercles d'approche et d'arrivée de l'arrêt suivant actif
                            if (idx == nextIdx) {
                                val approachPoints = Polygon.pointsAsCircle(stationPoint, station.approachRadius.toDouble())
                                view.overlays.add(Polygon(view).apply {
                                    setPoints(approachPoints)
                                    fillPaint.color = android.graphics.Color.argb(25, 0, 120, 220)
                                    outlinePaint.color = android.graphics.Color.argb(120, 0, 120, 220)
                                    outlinePaint.strokeWidth = 2f
                                })

                                val arrivalPoints = Polygon.pointsAsCircle(stationPoint, station.arrivalRadius.toDouble())
                                view.overlays.add(Polygon(view).apply {
                                    setPoints(arrivalPoints)
                                    fillPaint.color = android.graphics.Color.argb(35, 220, 60, 0)
                                    outlinePaint.color = android.graphics.Color.argb(150, 220, 60, 0)
                                    outlinePaint.strokeWidth = 2f
                                })
                            }
                        }
                    }

                    // 4. Dessiner le bus
                    if (busLoc != null) {
                        val busPoint = GeoPoint(busLoc.latitude, busLoc.longitude)
                        val busMarker = Marker(view).apply {
                            position = busPoint
                            title = "Mon bus"
                            setAnchor(Marker.ANCHOR_CENTER, Marker.ANCHOR_CENTER)

                            if (busLoc.hasBearing()) {
                                rotation = if (isAutoCenterEnabled) 0f else -busLoc.bearing
                            }

                            val size = 48
                            val bitmap = android.graphics.Bitmap.createBitmap(size, size, android.graphics.Bitmap.Config.ARGB_8888)
                            val canvas = android.graphics.Canvas(bitmap)

                            val paint = android.graphics.Paint().apply {
                                color = android.graphics.Color.argb(60, 21, 101, 192)
                                style = android.graphics.Paint.Style.FILL
                                isAntiAlias = true
                            }
                            // Halo bleu
                            canvas.drawCircle(size / 2f, size / 2f, size / 2f, paint)

                            // Rond central bleu
                            paint.color = android.graphics.Color.argb(255, 21, 101, 192)
                            canvas.drawCircle(size / 2f, size / 2f, size / 4.5f, paint)

                            // Bordure blanche du rond
                            paint.color = android.graphics.Color.WHITE
                            paint.style = android.graphics.Paint.Style.STROKE
                            paint.strokeWidth = 2f
                            canvas.drawCircle(size / 2f, size / 2f, size / 4.5f, paint)

                            // Flèche directionnelle si un cap est disponible
                            if (busLoc.hasBearing()) {
                                paint.style = android.graphics.Paint.Style.FILL
                                paint.color = android.graphics.Color.argb(255, 21, 101, 192)
                                val path = android.graphics.Path().apply {
                                    moveTo(size / 2f, size / 6f)
                                    lineTo(size / 2f - size / 6f, size / 2f)
                                    lineTo(size / 2f + size / 6f, size / 2f)
                                    close()
                                }
                                canvas.drawPath(path, paint)

                                paint.color = android.graphics.Color.WHITE
                                paint.style = android.graphics.Paint.Style.STROKE
                                paint.strokeWidth = 1.5f
                                canvas.drawPath(path, paint)
                            }

                            icon = android.graphics.drawable.BitmapDrawable(view.context.resources, bitmap)
                        }
                        view.overlays.add(busMarker)
                    }

                    view.invalidate()
                }
            )

            val lifecycleObserver = remember(mapView) {
                LifecycleEventObserver { _, event ->
                    when (event) {
                        Lifecycle.Event.ON_RESUME -> mapView.onResume()
                        Lifecycle.Event.ON_PAUSE -> mapView.onPause()
                        else -> {}
                    }
                }
            }
            DisposableEffect(lifecycleOwner) {
                lifecycleOwner.lifecycle.addObserver(lifecycleObserver)
                onDispose {
                    lifecycleOwner.lifecycle.removeObserver(lifecycleObserver)
                    mapView.onDetach()
                }
            }

            // Bouton flottant de recentrage
            if (!isAutoCenterEnabled && location != null) {
                FloatingActionButton(
                    onClick = { isAutoCenterEnabled = true },
                    modifier = Modifier
                        .align(Alignment.BottomEnd)
                        .padding(16.dp)
                        .size(44.dp),
                    containerColor = MaterialTheme.colorScheme.surface,
                    contentColor = MaterialTheme.colorScheme.primary,
                    shape = CircleShape,
                    elevation = FloatingActionButtonDefaults.elevation(4.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.MyLocation,
                        contentDescription = "Recentrer sur le bus",
                        modifier = Modifier.size(20.dp)
                    )
                }
            }
        }

        // ── Zone Liste (Moitié inférieure défilante) ──
        LazyColumn(
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f),
            contentPadding = PaddingValues(bottom = 24.dp),
        ) {
            // ── Hero prochain arrêt ──
            item {
                if (state == null) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(32.dp),
                        contentAlignment = Alignment.Center,
                    ) {
                        CircularProgressIndicator(color = Violet600)
                    }
                } else {
                    NextStopHero(state)
                }
            }

            // ── Liste des arrêts ──
            activeRoute?.let { route ->
                item {
                    Text(
                        "Arrêts de la ligne",
                        style = MaterialTheme.typography.titleSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(horizontal = 20.dp, vertical = 8.dp),
                    )
                }

                val nextIdx = route.stations.indexOfFirst { it.name == state?.nextStationName }

                itemsIndexed(route.stations) { idx, station ->
                    val isPassed = nextIdx >= 0 && idx < nextIdx
                    val isCurrent = idx == nextIdx

                    StopRow(
                        name = station.name,
                        isPassed = isPassed,
                        isCurrent = isCurrent,
                        isLast = idx == route.stations.size - 1,
                    )
                }
            }

            // ── Boutons Caisse (Vente + Scan) ──
            item {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 20.dp, vertical = 16.dp),
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    Button(
                        onClick = { showVenteBottomSheet = true },
                        modifier = Modifier
                            .weight(1f)
                            .height(48.dp),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = MaterialTheme.colorScheme.primary
                        ),
                        shape = RoundedCornerShape(12.dp),
                    ) {
                        Icon(
                            imageVector = Icons.Default.ShoppingCart,
                            contentDescription = "Vente",
                            modifier = Modifier
                                .size(20.dp)
                                .padding(end = 8.dp)
                        )
                        Text("Vente", style = MaterialTheme.typography.labelLarge)
                    }

                    Button(
                        onClick = { /* TODO: Action Scan QR - ouvrir CameraScreen */ },
                        modifier = Modifier
                            .weight(1f)
                            .height(48.dp),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = MaterialTheme.colorScheme.secondary
                        ),
                        shape = RoundedCornerShape(12.dp),
                    ) {
                        Icon(
                            imageVector = Icons.Default.QrCode2,
                            contentDescription = "Scan",
                            modifier = Modifier
                                .size(20.dp)
                                .padding(end = 8.dp)
                        )
                        Text("Scan", style = MaterialTheme.typography.labelLarge)
                    }
                }
            }
        }
    }
}

@Composable
private fun NextStopHero(state: LocationService.ServiceState.Tracking) {
    val dist = state.distanceMeters

    val (distColor, distText) = when {
        dist < 1    -> WarnOrange to "En attente GPS…"
        dist < 50   -> DangerRed  to "Arrivée !"
        dist < 300  -> DangerRed  to "${dist.roundToInt()} m"
        dist < 1000 -> WarnOrange to "${dist.roundToInt()} m"
        else        -> SuccessGreen to "${"%.2f".format(dist / 1000)} km"
    }

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 20.dp, vertical = 16.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        shape = RoundedCornerShape(24.dp),
        elevation = CardDefaults.cardElevation(8.dp),
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 28.dp, horizontal = 24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            // Pastille "PROCHAIN ARRÊT"
            Surface(
                color = Violet700.copy(alpha = 0.1f),
                shape = RoundedCornerShape(50.dp),
            ) {
                Text(
                    "PROCHAIN ARRÊT",
                    modifier = Modifier.padding(horizontal = 14.dp, vertical = 6.dp),
                    style = MaterialTheme.typography.labelSmall,
                    color = Violet700,
                    fontWeight = FontWeight.Bold,
                )
            }

            Text(
                text = state.nextStationName.ifEmpty { "—" },
                style = MaterialTheme.typography.headlineMedium,
                fontWeight = FontWeight.ExtraBold,
                color = MaterialTheme.colorScheme.onSurface,
                textAlign = TextAlign.Center,
            )

            // Distance en grand
            Text(
                text = distText,
                style = MaterialTheme.typography.displaySmall,
                color = distColor,
                fontWeight = FontWeight.Black,
            )

            // Indicateur couleur sous la distance
            Box(
                modifier = Modifier
                    .width(60.dp)
                    .height(4.dp)
                    .clip(RoundedCornerShape(2.dp))
                    .background(distColor),
            )
        }
    }
}

@Composable
private fun StopRow(name: String, isPassed: Boolean, isCurrent: Boolean, isLast: Boolean) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(if (isCurrent) MaterialTheme.colorScheme.primary.copy(alpha = 0.08f) else Color.Transparent)
            .padding(horizontal = 20.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        // Icône
        Box(
            modifier = Modifier
                .size(32.dp)
                .clip(CircleShape)
                .background(
                    when {
                        isPassed  -> MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.12f)
                        isCurrent -> MaterialTheme.colorScheme.primary
                        else      -> Color.Transparent
                    }
                ),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                imageVector = when {
                    isPassed  -> Icons.Default.Check
                    isCurrent -> Icons.Default.LocationOn
                    else      -> Icons.Default.RadioButtonUnchecked
                },
                contentDescription = null,
                tint = when {
                    isPassed  -> MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f)
                    isCurrent -> Color.White
                    else      -> MaterialTheme.colorScheme.onSurfaceVariant
                },
                modifier = Modifier.size(18.dp),
            )
        }

        // Ligne verticale connecteur
        if (!isLast) {
            Spacer(Modifier.width(10.dp))
        } else {
            Spacer(Modifier.width(10.dp))
        }

        Text(
            text = name,
            style = if (isCurrent) MaterialTheme.typography.bodyLarge else MaterialTheme.typography.bodyMedium,
            fontWeight = if (isCurrent) FontWeight.Bold else FontWeight.Normal,
            color = when {
                isPassed  -> MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f)
                isCurrent -> MaterialTheme.colorScheme.onSurface
                else      -> MaterialTheme.colorScheme.onSurface.copy(alpha = 0.75f)
            },
        )
    }

    if (!isLast) {
        Box(
            modifier = Modifier
                .padding(start = 36.dp)
                .width(2.dp)
                .height(8.dp)
                .background(if (isPassed) MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.15f) else MaterialTheme.colorScheme.outline.copy(alpha = 0.5f)),
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ReceiptOptionsBottomSheet(
    sale: TicketSale,
    agentNumber: String,
    onDismiss: () -> Unit,
) {
    val context = LocalContext.current
    val receiptManager = remember { ReceiptManager(context) }
    val printerManager = remember { PrinterManager(context) }
    val coroutineScope = rememberCoroutineScope()

    var receiptText by remember { mutableStateOf<String?>(null) }
    var pairedPrinters by remember { mutableStateOf<List<BluetoothDevice>>(emptyList()) }
    var selectedPrinter by remember { mutableStateOf<BluetoothDevice?>(null) }
    var isPrinting by remember { mutableStateOf(false) }
    var showPrinterDropdown by remember { mutableStateOf(false) }

    LaunchedEffect(Unit) {
        coroutineScope.launch {
            receiptText = receiptManager.generateReceiptText(sale, agentNumber, "N/A")
            pairedPrinters = printerManager.getPairedPrinters()
            if (pairedPrinters.isNotEmpty()) {
                selectedPrinter = pairedPrinters.first()
            }
        }
    }

    if (receiptText == null) {
        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            CircularProgressIndicator()
        }
        return
    }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        containerColor = MaterialTheme.colorScheme.surface,
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Text(
                "Envoyer le reçu ?",
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.Bold,
            )

            Spacer(Modifier.height(8.dp))

            Button(
                onClick = {
                    coroutineScope.launch {
                        receiptManager.sendReceiptViaSMS("+33612345678", receiptText!!)
                    }
                    onDismiss()
                },
                modifier = Modifier.fillMaxWidth(),
                colors = ButtonDefaults.buttonColors(containerColor = Violet600),
            ) {
                Icon(Icons.Default.Message, "SMS", modifier = Modifier.padding(end = 8.dp))
                Text("Envoyer par SMS")
            }

            Button(
                onClick = {
                    coroutineScope.launch {
                        receiptManager.sendReceiptViaEmail("client@example.com", receiptText!!, sale.type.displayName)
                    }
                    onDismiss()
                },
                modifier = Modifier.fillMaxWidth(),
                colors = ButtonDefaults.buttonColors(containerColor = Violet500),
            ) {
                Icon(Icons.Default.Email, "Email", modifier = Modifier.padding(end = 8.dp))
                Text("Envoyer par Email")
            }

            if (pairedPrinters.isNotEmpty()) {
                Column(modifier = Modifier.fillMaxWidth()) {
                    Box(modifier = Modifier.fillMaxWidth()) {
                        Button(
                            onClick = { showPrinterDropdown = !showPrinterDropdown },
                            modifier = Modifier.fillMaxWidth(),
                            colors = ButtonDefaults.buttonColors(containerColor = Violet500),
                        ) {
                            Icon(Icons.Default.Print, null, modifier = Modifier.padding(end = 8.dp))
                            Text(selectedPrinter?.name ?: "Sélectionner imprimante")
                            Spacer(Modifier.weight(1f))
                            Icon(Icons.Default.ArrowDropDown, null)
                        }

                        DropdownMenu(
                            expanded = showPrinterDropdown,
                            onDismissRequest = { showPrinterDropdown = false },
                            modifier = Modifier.fillMaxWidth(0.9f),
                        ) {
                            pairedPrinters.forEach { printer ->
                                DropdownMenuItem(
                                    text = { Text(printer.name ?: "Unknown") },
                                    onClick = {
                                        selectedPrinter = printer
                                        showPrinterDropdown = false
                                    }
                                )
                            }
                        }
                    }

                    Button(
                        onClick = {
                            if (selectedPrinter != null) {
                                isPrinting = true
                                coroutineScope.launch {
                                    try {
                                        if (!printerManager.isBluetoothEnabled()) {
                                            Toast.makeText(context, "Bluetooth désactivé", Toast.LENGTH_SHORT).show()
                                            isPrinting = false
                                            return@launch
                                        }

                                        val connectResult = printerManager.connectToPrinter(selectedPrinter!!)
                                        if (connectResult.isFailure) {
                                            Toast.makeText(context, "Erreur de connexion", Toast.LENGTH_SHORT).show()
                                            isPrinting = false
                                            return@launch
                                        }

                                        val socket = connectResult.getOrNull()!!
                                        val formattedText = printerManager.formatTicketForPrinting(
                                            type = sale.type.displayName,
                                            price = sale.priceCents,
                                            agentNumber = agentNumber,
                                            lineNumber = "N/A",
                                            timestamp = sale.soldAt,
                                            transactionId = sale.id.take(8)
                                        )

                                        val printResult = printerManager.printText(socket, formattedText)
                                        printerManager.disconnect(socket)

                                        if (printResult.isSuccess) {
                                            Toast.makeText(context, "Impression réussie", Toast.LENGTH_SHORT).show()
                                            onDismiss()
                                        } else {
                                            Toast.makeText(context, "Erreur d'impression", Toast.LENGTH_SHORT).show()
                                        }
                                    } catch (e: Exception) {
                                        Toast.makeText(context, "Erreur: ${e.message}", Toast.LENGTH_SHORT).show()
                                    } finally {
                                        isPrinting = false
                                    }
                                }
                            }
                        },
                        modifier = Modifier.fillMaxWidth(),
                        enabled = !isPrinting && selectedPrinter != null,
                        colors = ButtonDefaults.buttonColors(containerColor = Violet600),
                    ) {
                        if (isPrinting) {
                            CircularProgressIndicator(modifier = Modifier.size(20.dp), color = Color.White)
                            Spacer(Modifier.width(8.dp))
                        }
                        Text(if (isPrinting) "Impression..." else "Imprimer")
                    }
                }
            } else {
                Button(
                    onClick = { /* Pas d'imprimante disponible */ },
                    modifier = Modifier.fillMaxWidth(),
                    enabled = false,
                    colors = ButtonDefaults.buttonColors(containerColor = Violet500),
                ) {
                    Icon(Icons.Default.Print, null, modifier = Modifier.padding(end = 8.dp))
                    Text("Aucune imprimante appairée")
                }
            }

            Button(
                onClick = onDismiss,
                modifier = Modifier.fillMaxWidth(),
                colors = ButtonDefaults.outlinedButtonColors(),
            ) {
                Text("Fermer")
            }

            Spacer(Modifier.height(16.dp))
        }
    }
}

private val TicketType.displayName: String
    get() = when (this) {
        TicketType.PLEIN_TARIF -> "Plein tarif"
        TicketType.CARNET -> "Carnet"
        TicketType.ABONNEMENT_MENSUEL -> "Abonnement"
        TicketType.CONTREMARQUE -> "Contremarque"
    }
