package com.example.gareter.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.Undo
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.gareter.data.model.TicketType
import com.example.gareter.ui.theme.*
import com.example.gareter.ui.viewmodel.CaisseViewModel
import com.example.gareter.util.TicketGenerator

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CaisseScreen(
    onBack: () -> Unit,
    onEndSession: () -> Unit,
    onScanCarnet: () -> Unit,
    viewModel: CaisseViewModel = viewModel(),
) {
    val session by viewModel.activeSession.collectAsStateWithLifecycle()
    val totalCents by viewModel.totalCents.collectAsStateWithLifecycle()
    val countByType by viewModel.countByType.collectAsStateWithLifecycle()
    val lastSale by viewModel.lastSale.collectAsStateWithLifecycle()
    val tariffs by viewModel.tariffs.collectAsStateWithLifecycle()
    val lastSold by viewModel.lastSoldSale.collectAsStateWithLifecycle()

    var showEndConfirm by remember { mutableStateOf(false) }
    var showSaleSnack by remember { mutableStateOf(false) }

    // Snack de confirmation après vente
    LaunchedEffect(lastSold) {
        if (lastSold != null) {
            showSaleSnack = true
            kotlinx.coroutines.delay(1800)
            showSaleSnack = false
            viewModel.clearLastSold()
        }
    }

    if (showEndConfirm) {
        AlertDialog(
            onDismissRequest = { showEndConfirm = false },
            title = { Text("Fin de journée ?") },
            text = { Text("Le rapport de session sera généré.") },
            confirmButton = {
                TextButton(onClick = { showEndConfirm = false; onEndSession() }) {
                    Text("Confirmer", color = Blue700)
                }
            },
            dismissButton = {
                TextButton(onClick = { showEndConfirm = false }) { Text("Annuler") }
            },
            shape = RoundedCornerShape(20.dp),
        )
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Caisse") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, "Retour")
                    }
                },
                actions = {
                    // Indicateur session
                    Surface(
                        shape = RoundedCornerShape(50),
                        color = if (session != null) SuccessGreen.copy(alpha = 0.15f)
                                else MaterialTheme.colorScheme.surfaceVariant,
                        modifier = Modifier.padding(end = 12.dp),
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(5.dp),
                        ) {
                            Box(
                                Modifier.size(8.dp).clip(CircleShape)
                                    .background(if (session != null) SuccessGreen else MaterialTheme.colorScheme.outline)
                            )
                            Text(
                                if (session != null) "En service" else "Hors service",
                                style = MaterialTheme.typography.labelSmall,
                                color = if (session != null) SuccessGreen else MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }
                },
            )
        },
    ) { padding ->

        if (session == null) {
            // Pas de session active → écran de démarrage
            Box(
                Modifier.fillMaxSize().padding(padding),
                contentAlignment = Alignment.Center,
            ) {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(24.dp),
                ) {
                    Icon(
                        Icons.Default.PointOfSale,
                        null,
                        modifier = Modifier.size(72.dp),
                        tint = Blue700.copy(alpha = 0.4f),
                    )
                    Text(
                        "Aucune session active",
                        style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Button(
                        onClick = { viewModel.startSession() },
                        colors = ButtonDefaults.buttonColors(containerColor = Blue700),
                        shape = RoundedCornerShape(14.dp),
                        modifier = Modifier.padding(horizontal = 32.dp).fillMaxWidth(),
                    ) {
                        Icon(Icons.Default.PlayArrow, null, modifier = Modifier.size(20.dp))
                        Spacer(Modifier.width(8.dp))
                        Text("Démarrer la journée", fontWeight = FontWeight.SemiBold)
                    }
                }
            }
            return@Scaffold
        }

        Column(
            Modifier.fillMaxSize().padding(padding),
        ) {
            // ── Grille 2×2 tickets ────────────────────────────────────────────────
            LazyVerticalGrid(
                columns = GridCells.Fixed(2),
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f)
                    .padding(horizontal = 12.dp, vertical = 8.dp),
                horizontalArrangement = Arrangement.spacedBy(10.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                items(TicketType.entries) { type ->
                    TicketButton(
                        type = type,
                        count = countByType[type] ?: 0,
                        priceCents = tariffs[type] ?: type.defaultPriceCents,
                        highlighted = lastSold?.type == type && showSaleSnack,
                        onClick = { viewModel.sellTicket(type) },
                    )
                }
            }

            // ── Pied de page caisse ───────────────────────────────────────────────
            Surface(
                color = MaterialTheme.colorScheme.surface,
                shadowElevation = 8.dp,
                modifier = Modifier.fillMaxWidth(),
            ) {
                Column(Modifier.padding(horizontal = 20.dp, vertical = 16.dp)) {

                    // Total
                    Row(
                        Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text("💰 Total caisse", style = MaterialTheme.typography.titleMedium)
                        Text(
                            TicketGenerator.formatCents(totalCents),
                            style = MaterialTheme.typography.headlineSmall,
                            fontWeight = FontWeight.ExtraBold,
                            color = Blue700,
                        )
                    }

                    val totalPassengers = countByType.values.sum()
                    Text(
                        "$totalPassengers voyageur${if (totalPassengers > 1) "s" else ""}",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )

                    Spacer(Modifier.height(12.dp))

                    Row(
                        Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(10.dp),
                    ) {
                        // Scan carnet retour
                        OutlinedButton(
                            onClick = onScanCarnet,
                            modifier = Modifier.weight(1f),
                            shape = RoundedCornerShape(12.dp),
                        ) {
                            Icon(Icons.Default.QrCodeScanner, null, modifier = Modifier.size(16.dp))
                            Spacer(Modifier.width(6.dp))
                            Text("Carnet retour", fontWeight = FontWeight.Medium)
                        }

                        // Annuler dernier
                        OutlinedButton(
                            onClick = { viewModel.cancelLastSale() },
                            enabled = lastSale != null,
                            modifier = Modifier.weight(1f),
                            shape = RoundedCornerShape(12.dp),
                            colors = ButtonDefaults.outlinedButtonColors(contentColor = DangerRed),
                        ) {
                            Icon(Icons.AutoMirrored.Filled.Undo, null, modifier = Modifier.size(16.dp))
                            Spacer(Modifier.width(6.dp))
                            Text("Annuler", fontWeight = FontWeight.Medium)
                        }
                    }

                    Spacer(Modifier.height(8.dp))

                    Button(
                        onClick = { showEndConfirm = true },
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(12.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = Blue700),
                    ) {
                        Icon(Icons.Default.AssignmentTurnedIn, null, modifier = Modifier.size(18.dp))
                        Spacer(Modifier.width(8.dp))
                        Text("Fin de journée", fontWeight = FontWeight.SemiBold)
                    }
                }
            }
        }
    }
}

@Composable
private fun TicketButton(
    type: TicketType,
    count: Int,
    priceCents: Int,
    highlighted: Boolean,
    onClick: () -> Unit,
) {
    val bgColor = if (highlighted) Blue700 else MaterialTheme.colorScheme.surface
    val contentColor = if (highlighted) Color.White else MaterialTheme.colorScheme.onSurface

    Card(
        onClick = onClick,
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = 130.dp),
        shape = RoundedCornerShape(18.dp),
        colors = CardDefaults.cardColors(containerColor = bgColor),
        elevation = CardDefaults.cardElevation(
            defaultElevation = if (highlighted) 10.dp else 3.dp,
            pressedElevation = 2.dp,
        ),
    ) {
        Box(Modifier.fillMaxSize()) {
            // Badge compteur
            if (count > 0) {
                Surface(
                    modifier = Modifier.align(Alignment.TopEnd).padding(8.dp),
                    shape = CircleShape,
                    color = if (highlighted) Color.White.copy(alpha = 0.3f) else Blue700,
                ) {
                    Text(
                        "$count",
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp),
                        style = MaterialTheme.typography.labelMedium,
                        fontWeight = FontWeight.Bold,
                        color = if (highlighted) Color.White else Color.White,
                    )
                }
            }

            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(horizontal = 14.dp, vertical = 18.dp),
                verticalArrangement = Arrangement.Center,
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Text(
                    type.label.uppercase(),
                    style = MaterialTheme.typography.labelMedium,
                    fontWeight = FontWeight.Bold,
                    color = contentColor,
                    textAlign = TextAlign.Center,
                    lineHeight = 16.sp,
                )
                Spacer(Modifier.height(8.dp))
                Text(
                    TicketGenerator.formatCents(priceCents),
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.ExtraBold,
                    color = if (highlighted) Color.White else Blue700,
                )
            }
        }
    }
}
