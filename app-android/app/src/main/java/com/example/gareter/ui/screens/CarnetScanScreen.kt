package com.example.gareter.ui.screens

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.gareter.data.model.AbonnementPass
import com.example.gareter.data.model.CarnetTicket
import com.example.gareter.ui.theme.*
import com.example.gareter.ui.viewmodel.CaisseViewModel
import com.journeyapps.barcodescanner.ScanContract
import com.journeyapps.barcodescanner.ScanOptions
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

private sealed interface ScanState {
    data object Idle : ScanState
    data object Scanning : ScanState
    data class ValidCarnet(val carnet: CarnetTicket, val unitsLeft: Int) : ScanState
    data class ValidAbonnement(val pass: AbonnementPass) : ScanState
    data class Invalid(val reason: String) : ScanState
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CarnetScanScreen(
    onBack: () -> Unit,
    viewModel: CaisseViewModel = viewModel(),
) {
    var scanState by remember { mutableStateOf<ScanState>(ScanState.Idle) }
    val scope = rememberCoroutineScope()

    val scanLauncher = rememberLauncherForActivityResult(ScanContract()) { result ->
        val raw = result.contents
        if (raw == null) {
            scanState = ScanState.Idle
            return@rememberLauncherForActivityResult
        }
        scope.launch {
            val carnet = viewModel.getCarnet(raw)
            scanState = when {
                carnet != null && carnet.isValid -> ScanState.ValidCarnet(carnet, carnet.remainingUnits)
                carnet != null -> ScanState.Invalid("Carnet épuisé.\n0 unité restante.")
                else -> {
                    val pass = viewModel.getAbonnementPass(raw)
                    when {
                        pass != null && pass.isValid -> ScanState.ValidAbonnement(pass)
                        pass != null -> ScanState.Invalid("Abonnement expiré.")
                        else -> ScanState.Invalid("QR code non reconnu.\nAucun carnet ou abonnement associé.")
                    }
                }
            }
        }
    }

    fun launchScan() {
        scanState = ScanState.Scanning
        val opts = ScanOptions().apply {
            setPrompt("Scannez le QR du titre de transport")
            setBeepEnabled(true)
            setOrientationLocked(true)
            setBarcodeImageEnabled(false)
        }
        scanLauncher.launch(opts)
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Validation titre de transport") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, "Retour")
                    }
                },
            )
        },
    ) { padding ->
        Box(
            Modifier
                .fillMaxSize()
                .padding(padding),
            contentAlignment = Alignment.Center,
        ) {
            when (val state = scanState) {
                is ScanState.Idle, is ScanState.Scanning -> {
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(24.dp),
                        modifier = Modifier.padding(32.dp),
                    ) {
                        Surface(
                            shape = CircleShape,
                            color = Blue700.copy(alpha = 0.1f),
                            modifier = Modifier.size(120.dp),
                        ) {
                            Icon(
                                Icons.Default.QrCodeScanner,
                                null,
                                modifier = Modifier
                                    .fillMaxSize()
                                    .padding(28.dp),
                                tint = Blue700,
                            )
                        }

                        Text(
                            "Scanner un titre de transport",
                            style = MaterialTheme.typography.titleLarge,
                            fontWeight = FontWeight.Bold,
                        )
                        Text(
                            "Présentez le QR code du carnet ou de l'abonnement du voyageur pour valider la montée.",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            textAlign = TextAlign.Center,
                        )

                        Button(
                            onClick = { launchScan() },
                            shape = RoundedCornerShape(14.dp),
                            colors = ButtonDefaults.buttonColors(containerColor = Blue700),
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(54.dp),
                        ) {
                            Icon(Icons.Default.QrCodeScanner, null, modifier = Modifier.size(20.dp))
                            Spacer(Modifier.width(10.dp))
                            Text("Lancer le scanner", fontWeight = FontWeight.SemiBold)
                        }
                    }
                }

                is ScanState.ValidCarnet -> {
                    ValidCarnetCard(
                        unitsLeft = state.unitsLeft,
                        onConfirm = {
                            viewModel.useCarnetUnit(state.carnet)
                            scanState = ScanState.Idle
                        },
                        onRescan = { launchScan() },
                        onBack = onBack,
                    )
                }

                is ScanState.ValidAbonnement -> {
                    ValidAbonnementCard(
                        pass = state.pass,
                        onConfirm = {
                            viewModel.scanAbonnement(state.pass)
                            scanState = ScanState.Idle
                        },
                        onRescan = { launchScan() },
                        onBack = onBack,
                    )
                }

                is ScanState.Invalid -> {
                    InvalidCard(
                        reason = state.reason,
                        onRescan = { launchScan() },
                        onBack = onBack,
                    )
                }
            }
        }
    }
}

@Composable
private fun ValidCarnetCard(
    unitsLeft: Int,
    onConfirm: () -> Unit,
    onRescan: () -> Unit,
    onBack: () -> Unit,
) {
    ResultCard(
        icon = Icons.Default.CheckCircle,
        iconTint = SuccessGreen,
        iconBg = SuccessGreen.copy(alpha = 0.1f),
        title = "Carnet valide",
        subtitle = "$unitsLeft unité${if (unitsLeft > 1) "s" else ""} restante${if (unitsLeft > 1) "s" else ""}",
        subtitleColor = SuccessGreen,
        message = "Confirmez pour utiliser 1 unité (il en restera ${unitsLeft - 1}).",
        primaryLabel = "Valider le trajet",
        primaryColor = SuccessGreen,
        onPrimary = onConfirm,
        secondaryLabel = "Rescanner",
        onSecondary = onRescan,
    )
}

@Composable
private fun ValidAbonnementCard(
    pass: AbonnementPass,
    onConfirm: () -> Unit,
    onRescan: () -> Unit,
    onBack: () -> Unit,
) {
    val expiryText = remember(pass.expiresAt) {
        SimpleDateFormat("dd/MM/yyyy", Locale.FRANCE).format(Date(pass.expiresAt))
    }
    ResultCard(
        icon = Icons.Default.CheckCircle,
        iconTint = SuccessGreen,
        iconBg = SuccessGreen.copy(alpha = 0.1f),
        title = "Abonnement valide",
        subtitle = "Valable jusqu'au $expiryText",
        subtitleColor = SuccessGreen,
        message = "Confirmez pour valider la montée du voyageur.",
        primaryLabel = "Valider la montée",
        primaryColor = SuccessGreen,
        onPrimary = onConfirm,
        secondaryLabel = "Rescanner",
        onSecondary = onRescan,
    )
}

@Composable
private fun InvalidCard(
    reason: String,
    onRescan: () -> Unit,
    onBack: () -> Unit,
) {
    ResultCard(
        icon = Icons.Default.Cancel,
        iconTint = DangerRed,
        iconBg = DangerRed.copy(alpha = 0.1f),
        title = "Carnet invalide",
        subtitle = reason,
        subtitleColor = DangerRed,
        message = "Ce carnet ne peut pas être utilisé pour ce trajet.",
        primaryLabel = "Rescanner",
        primaryColor = Blue700,
        onPrimary = onRescan,
        secondaryLabel = "Retour",
        onSecondary = onBack,
    )
}

@Composable
private fun ResultCard(
    icon: ImageVector,
    iconTint: Color,
    iconBg: Color,
    title: String,
    subtitle: String,
    subtitleColor: Color,
    message: String,
    primaryLabel: String,
    primaryColor: Color,
    onPrimary: () -> Unit,
    secondaryLabel: String,
    onSecondary: () -> Unit,
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(24.dp),
        shape = RoundedCornerShape(20.dp),
        elevation = CardDefaults.cardElevation(4.dp),
    ) {
        Column(
            modifier = Modifier.padding(28.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            Surface(
                shape = CircleShape,
                color = iconBg,
                modifier = Modifier.size(80.dp),
            ) {
                Icon(
                    icon,
                    null,
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(18.dp),
                    tint = iconTint,
                )
            }

            Text(title, style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)

            Text(
                subtitle,
                style = MaterialTheme.typography.bodyMedium,
                color = subtitleColor,
                fontWeight = FontWeight.SemiBold,
                textAlign = TextAlign.Center,
            )

            Text(
                message,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
            )

            Spacer(Modifier.height(4.dp))

            Button(
                onClick = onPrimary,
                modifier = Modifier.fillMaxWidth().height(50.dp),
                shape = RoundedCornerShape(12.dp),
                colors = ButtonDefaults.buttonColors(containerColor = primaryColor),
            ) {
                Text(primaryLabel, fontWeight = FontWeight.SemiBold)
            }

            OutlinedButton(
                onClick = onSecondary,
                modifier = Modifier.fillMaxWidth().height(50.dp),
                shape = RoundedCornerShape(12.dp),
            ) {
                Text(secondaryLabel)
            }
        }
    }
}
