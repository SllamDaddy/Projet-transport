package com.example.gareter.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
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

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CaisseVenteBottomSheet(
    onDismiss: () -> Unit,
    onSellTicket: (TicketType, Int) -> Unit,
    tariffs: Map<TicketType, Int>,
    viewModel: CaisseViewModel = viewModel(),
) {
    var selectedTicket by remember { mutableStateOf<TicketType?>(null) }
    var showConfirm by remember { mutableStateOf(false) }

    val session by viewModel.activeSession.collectAsStateWithLifecycle()

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        containerColor = MaterialTheme.colorScheme.surface,
        scrimColor = Color.Black.copy(alpha = 0.32f),
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp, vertical = 24.dp)
        ) {
            // Header
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    "Vendre un ticket",
                    style = MaterialTheme.typography.headlineSmall,
                    color = MaterialTheme.colorScheme.onSurface,
                    fontWeight = FontWeight.Bold,
                )
                IconButton(onClick = onDismiss) {
                    Icon(Icons.Default.Close, "Fermer")
                }
            }

            Spacer(Modifier.height(20.dp))

            // Liste des tarifs
            LazyColumn(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(12.dp),
                contentPadding = PaddingValues(bottom = 24.dp),
            ) {
                items(TicketType.entries) { type ->
                    val price = tariffs[type] ?: type.defaultPriceCents
                    val priceText = "€${price / 100}.${(price % 100).toString().padStart(2, '0')}"
                    val isSelected = selectedTicket == type

                    Surface(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(100.dp)
                            .clickable { selectedTicket = type }
                            .then(
                                if (isSelected)
                                    Modifier.background(
                                        Violet600.copy(alpha = 0.15f),
                                        RoundedCornerShape(16.dp)
                                    )
                                else
                                    Modifier.background(
                                        MaterialTheme.colorScheme.surfaceVariant,
                                        RoundedCornerShape(16.dp)
                                    )
                            ),
                        color = if (isSelected) Violet600.copy(alpha = 0.15f) else MaterialTheme.colorScheme.surfaceVariant,
                        shape = RoundedCornerShape(16.dp),
                    ) {
                        Column(
                            modifier = Modifier
                                .fillMaxSize()
                                .padding(16.dp),
                            verticalArrangement = Arrangement.SpaceBetween,
                        ) {
                            Text(
                                type.displayName,
                                style = MaterialTheme.typography.titleMedium,
                                color = MaterialTheme.colorScheme.onSurface,
                                fontWeight = FontWeight.SemiBold,
                            )

                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                Text(
                                    priceText,
                                    style = MaterialTheme.typography.headlineMedium,
                                    color = if (isSelected) Violet600 else MaterialTheme.colorScheme.primary,
                                    fontWeight = FontWeight.Bold,
                                )

                                if (isSelected) {
                                    Surface(
                                        color = Violet600,
                                        shape = RoundedCornerShape(8.dp),
                                    ) {
                                        Text(
                                            "✓",
                                            modifier = Modifier.padding(8.dp),
                                            color = Color.White,
                                            fontWeight = FontWeight.Bold,
                                            fontSize = 16.sp,
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }

            // Boutons action
            Button(
                onClick = { showConfirm = true },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(50.dp),
                enabled = selectedTicket != null,
                colors = ButtonDefaults.buttonColors(
                    containerColor = Violet600,
                    disabledContainerColor = MaterialTheme.colorScheme.surfaceVariant,
                ),
                shape = RoundedCornerShape(12.dp),
            ) {
                Text(
                    "Confirmer la vente",
                    style = MaterialTheme.typography.labelLarge,
                    color = if (selectedTicket != null) Color.White else MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }

            Spacer(Modifier.height(12.dp))

            Button(
                onClick = onDismiss,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(50.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = MaterialTheme.colorScheme.surfaceVariant,
                ),
                shape = RoundedCornerShape(12.dp),
            ) {
                Text(
                    "Annuler",
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.onSurface,
                )
            }

            Spacer(Modifier.height(24.dp))
        }

        // Dialog de confirmation
        if (showConfirm && selectedTicket != null) {
            AlertDialog(
                onDismissRequest = { showConfirm = false },
                confirmButton = {
                    Button(
                        onClick = {
                            val price = tariffs[selectedTicket] ?: selectedTicket!!.defaultPriceCents
                            onSellTicket(selectedTicket!!, price)
                            showConfirm = false
                            onDismiss()
                        },
                    ) {
                        Text("Vendre")
                    }
                },
                dismissButton = {
                    TextButton(onClick = { showConfirm = false }) {
                        Text("Annuler")
                    }
                },
                title = { Text("Confirmer la vente") },
                text = {
                    val price = tariffs[selectedTicket] ?: selectedTicket!!.defaultPriceCents
                    val priceText = "€${price / 100}.${(price % 100).toString().padStart(2, '0')}"
                    Text("Vendre ${selectedTicket!!.displayName} pour $priceText ?")
                },
            )
        }
    }
}

private val TicketType.displayName: String
    get() = when (this) {
        TicketType.PLEIN_TARIF -> "Plein tarif (1 voyage)"
        TicketType.CARNET -> "Carnet 10 voyages"
        TicketType.ABONNEMENT -> "Abonnement mensuel"
    }
