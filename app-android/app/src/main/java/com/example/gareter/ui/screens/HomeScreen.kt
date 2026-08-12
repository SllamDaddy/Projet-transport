package com.example.gareter.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.gareter.data.model.Route
import com.example.gareter.data.model.RouteDirection
import com.example.gareter.service.LocationService
import com.example.gareter.ui.theme.*
import com.example.gareter.ui.viewmodel.HomeViewModel
import kotlin.math.roundToInt

@Composable
fun HomeScreen(
    onCreateRoute: () -> Unit,
    onEditRoute: (String) -> Unit,
    onGoToSettings: () -> Unit,
    onGoToCaisse: () -> Unit,
    onStartTracking: (Route, RouteDirection) -> Unit,
    onGoToTracking: () -> Unit,
    viewModel: HomeViewModel = viewModel(),
) {
    val routes by viewModel.routes.collectAsStateWithLifecycle()
    val trackingState by viewModel.trackingState.collectAsStateWithLifecycle()
    val activeRoute by viewModel.activeRoute.collectAsStateWithLifecycle()
    val syncLoading by viewModel.syncLoading.collectAsStateWithLifecycle()
    val isTracking = trackingState is LocationService.ServiceState.Tracking

    LaunchedEffect(trackingState) {
        if (trackingState is LocationService.ServiceState.Tracking) onGoToTracking()
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background),
    ) {
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(bottom = 88.dp),
        ) {
            item {
                HomeHeader(
                    onGoToSettings = onGoToSettings,
                    routeCount = routes.size,
                    onSync = { viewModel.syncRoutesFromSupabase() },
                    syncLoading = syncLoading
                )
            }

            (trackingState as? LocationService.ServiceState.Tracking)?.let { state ->
                item { TrackingBanner(state = state, onClick = onGoToTracking) }
            }

            item { CaisseShortcut(onClick = onGoToCaisse) }

            item {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 20.dp, vertical = 8.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text("Mes lignes", style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.onBackground)
                    Text(
                        "${routes.size} ligne${if (routes.size > 1) "s" else ""}",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }

            if (routes.isEmpty()) {
                item { EmptyState() }
            } else {
                items(routes, key = { it.id }) { route ->
                    val routeIsActive = activeRoute?.id == route.id
                    RouteCard(
                        route = route,
                        isTracking = routeIsActive,
                        trackingBusy = isTracking && !routeIsActive,
                        onStart = { direction -> onStartTracking(route, direction) },
                        onStop = { viewModel.stopTracking() },
                        onEdit = { onEditRoute(route.id) },
                        onDelete = { viewModel.deleteRoute(route.id) },
                        onOpenTracking = onGoToTracking,
                    )
                }
            }
        }

        if (!isTracking) {
            FloatingActionButton(
                onClick = onCreateRoute,
                modifier = Modifier
                    .align(Alignment.BottomEnd)
                    .padding(24.dp)
                    .shadow(12.dp, CircleShape),
                containerColor = Violet700,
                contentColor = Color.White,
                shape = CircleShape,
            ) {
                Icon(Icons.Default.Add, "Créer une ligne", modifier = Modifier.size(28.dp))
            }
        }
    }
}

@Composable
private fun HomeHeader(
    onGoToSettings: () -> Unit,
    routeCount: Int,
    onSync: () -> Unit,
    syncLoading: Boolean
) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .background(Brush.verticalGradient(listOf(Violet700, Violet600)))
            .padding(top = 48.dp, start = 20.dp, end = 20.dp, bottom = 28.dp),
    ) {
        Column {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column {
                    Text(
                        "Bonjour,",
                        style = MaterialTheme.typography.bodyLarge,
                        color = Color.White.copy(alpha = 0.8f),
                    )
                    Text(
                        "Conducteur",
                        style = MaterialTheme.typography.headlineMedium,
                        color = Color.White,
                        fontWeight = FontWeight.ExtraBold,
                    )
                }
                Row(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    IconButton(
                        onClick = onSync,
                        modifier = Modifier
                            .clip(CircleShape)
                            .background(Color.White.copy(alpha = 0.15f)),
                    ) {
                        if (syncLoading) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(18.dp),
                                color = Color.White,
                                strokeWidth = 2.dp
                            )
                        } else {
                            Icon(Icons.Default.Refresh, "Synchroniser", tint = Color.White)
                        }
                    }
                    IconButton(
                        onClick = onGoToSettings,
                        modifier = Modifier
                            .clip(CircleShape)
                            .background(Color.White.copy(alpha = 0.15f)),
                    ) {
                        Icon(Icons.Default.Settings, "Paramètres", tint = Color.White)
                    }
                }
            }

            Spacer(Modifier.height(20.dp))

            Surface(
                modifier = Modifier.fillMaxWidth(),
                color = Color.White.copy(alpha = 0.18f),
                shape = RoundedCornerShape(14.dp),
            ) {
                Row(
                    modifier = Modifier.padding(16.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(14.dp),
                ) {
                    Icon(Icons.Default.DirectionsBus, null, tint = Color.White, modifier = Modifier.size(28.dp))
                    Column {
                        Text(
                            "$routeCount ligne${if (routeCount > 1) "s" else ""} enregistrée${if (routeCount > 1) "s" else ""}",
                            style = MaterialTheme.typography.titleSmall,
                            color = Color.White,
                        )
                        Text(
                            "Appuie sur ▶ pour lancer les annonces",
                            style = MaterialTheme.typography.bodySmall,
                            color = Color.White.copy(alpha = 0.75f),
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun TrackingBanner(state: LocationService.ServiceState.Tracking, onClick: () -> Unit) {
    val distanceText = when {
        state.distanceMeters < 1   -> "GPS…"
        state.distanceMeters < 1000 -> "${state.distanceMeters.roundToInt()} m"
        else -> "${"%.1f".format(state.distanceMeters / 1000)} km"
    }
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 20.dp, vertical = 12.dp)
            .clickable(onClick = onClick),
        colors = CardDefaults.cardColors(containerColor = SuccessGreen),
        shape = RoundedCornerShape(16.dp),
        elevation = CardDefaults.cardElevation(6.dp),
    ) {
        Row(modifier = Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
            Box(
                modifier = Modifier
                    .size(40.dp)
                    .clip(CircleShape)
                    .background(Color.White.copy(alpha = 0.25f)),
                contentAlignment = Alignment.Center,
            ) {
                Icon(Icons.Default.LocationOn, null, tint = Color.White, modifier = Modifier.size(22.dp))
            }
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text("Trajet en cours", style = MaterialTheme.typography.labelLarge, color = Color.White.copy(alpha = 0.85f))
                Text("Prochain arrêt : ${state.nextStationName}", style = MaterialTheme.typography.titleSmall, color = Color.White)
            }
            Text(distanceText, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.ExtraBold, color = Color.White)
        }
    }
}

@Composable
private fun RouteCard(
    route: Route,
    isTracking: Boolean,
    trackingBusy: Boolean,
    onStart: (RouteDirection) -> Unit,
    onStop: () -> Unit,
    onEdit: () -> Unit,
    onDelete: () -> Unit,
    onOpenTracking: () -> Unit,
) {
    var showConfirmDelete by remember { mutableStateOf(false) }
    var showDirectionPicker by remember { mutableStateOf(false) }

    if (showConfirmDelete) {
        AlertDialog(
            onDismissRequest = { showConfirmDelete = false },
            title = { Text("Supprimer la ligne ?") },
            text = { Text("« ${route.title} » sera supprimée définitivement.") },
            confirmButton = {
                TextButton(onClick = { onDelete(); showConfirmDelete = false }) {
                    Text("Supprimer", color = DangerRed)
                }
            },
            dismissButton = { TextButton(onClick = { showConfirmDelete = false }) { Text("Annuler") } },
            shape = RoundedCornerShape(20.dp),
        )
    }

    if (showDirectionPicker) {
        AlertDialog(
            onDismissRequest = { showDirectionPicker = false },
            title = { Text("Choisir le sens") },
            text = { Text("Dans quel sens allez-vous ?") },
            confirmButton = {
                TextButton(onClick = { onStart(RouteDirection.FORWARD); showDirectionPicker = false }) {
                    Text("${route.stations.firstOrNull()?.name ?: "Début"} → Fin")
                }
            },
            dismissButton = {
                TextButton(onClick = { onStart(RouteDirection.BACKWARD); showDirectionPicker = false }) {
                    Text("Fin → ${route.stations.firstOrNull()?.name ?: "Début"}")
                }
            },
            shape = RoundedCornerShape(20.dp),
        )
    }

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 20.dp, vertical = 6.dp),
        colors = CardDefaults.cardColors(containerColor = if (isTracking) Violet700 else MaterialTheme.colorScheme.surface),
        shape = RoundedCornerShape(20.dp),
        elevation = CardDefaults.cardElevation(defaultElevation = if (isTracking) 8.dp else 3.dp),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 14.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(
                modifier = Modifier
                    .size(48.dp)
                    .clip(RoundedCornerShape(14.dp))
                    .background(if (isTracking) Color.White.copy(alpha = 0.2f) else Violet700),
                contentAlignment = Alignment.Center,
            ) {
                Icon(Icons.Default.DirectionsBus, null, tint = Color.White, modifier = Modifier.size(24.dp))
            }
            Spacer(Modifier.width(14.dp))
            Column(Modifier.weight(1f)) {
                Text(
                    route.title,
                    style = MaterialTheme.typography.titleMedium,
                    color = if (isTracking) Color.White else MaterialTheme.colorScheme.onSurface,
                )
                Text(
                    "${route.stations.size} arrêt${if (route.stations.size > 1) "s" else ""}",
                    style = MaterialTheme.typography.bodySmall,
                    color = if (isTracking) Color.White.copy(alpha = 0.7f) else MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }

            if (isTracking) {
                IconButton(onClick = onOpenTracking) {
                    Icon(Icons.Default.Visibility, "Voir le tracking", tint = Color.White)
                }
                IconButton(onClick = onStop) {
                    Box(
                        modifier = Modifier.size(36.dp).clip(CircleShape).background(DangerRed),
                        contentAlignment = Alignment.Center,
                    ) {
                        Icon(Icons.Default.Stop, "Arrêter", tint = Color.White, modifier = Modifier.size(20.dp))
                    }
                }
            } else {
                IconButton(
                    onClick = { showDirectionPicker = true },
                    enabled = route.stations.isNotEmpty() && !trackingBusy,
                ) {
                    Box(
                        modifier = Modifier
                            .size(36.dp)
                            .clip(CircleShape)
                            .background(if (route.stations.isNotEmpty() && !trackingBusy) SuccessGreen else SuccessGreen.copy(alpha = 0.4f)),
                        contentAlignment = Alignment.Center,
                    ) {
                        Icon(Icons.Default.PlayArrow, "Démarrer", tint = Color.White, modifier = Modifier.size(20.dp))
                    }
                }
                IconButton(onClick = onEdit) {
                    Icon(Icons.Default.Edit, "Modifier", tint = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                IconButton(onClick = { showConfirmDelete = true }) {
                    Icon(Icons.Default.Delete, "Supprimer", tint = DangerRed.copy(alpha = 0.7f))
                }
            }
        }
    }
}

@Composable
private fun CaisseShortcut(onClick: () -> Unit) {
    Card(
        onClick = onClick,
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 20.dp, vertical = 8.dp),
        shape = RoundedCornerShape(18.dp),
        colors = CardDefaults.cardColors(containerColor = Blue700),
        elevation = CardDefaults.cardElevation(6.dp),
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 20.dp, vertical = 16.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            Icon(Icons.Default.PointOfSale, null, tint = Color.White, modifier = Modifier.size(32.dp))
            Column(Modifier.weight(1f)) {
                Text("Caisse", style = MaterialTheme.typography.titleMedium, color = Color.White, fontWeight = FontWeight.Bold)
                Text("Vendre des tickets · Valider carnets", style = MaterialTheme.typography.bodySmall, color = Color.White.copy(alpha = 0.75f))
            }
            Icon(Icons.Default.ChevronRight, null, tint = Color.White.copy(alpha = 0.7f))
        }
    }
}

@Composable
private fun EmptyState() {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = 60.dp, start = 32.dp, end = 32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        Box(
            modifier = Modifier
                .size(80.dp)
                .clip(CircleShape)
                .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.12f)),
            contentAlignment = Alignment.Center,
        ) {
            Icon(Icons.Default.DirectionsBus, null, modifier = Modifier.size(40.dp), tint = MaterialTheme.colorScheme.primary)
        }
        Text("Aucune ligne enregistrée", style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.onBackground)
        Text(
            "Appuie sur le bouton + pour\ncréer ta première ligne",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
        )
    }
}
