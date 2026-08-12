package com.example.gareter.ui.screens

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.ArrowDownward
import androidx.compose.material.icons.filled.ArrowUpward
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.gareter.ui.viewmodel.CreateRouteViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CreateRouteScreen(
    onBack: () -> Unit,
    onAddStation: () -> Unit,
    onEditStation: (String) -> Unit,
    viewModel: CreateRouteViewModel = viewModel()
) {
    val title by viewModel.title.collectAsStateWithLifecycle()
    val stations by viewModel.stations.collectAsStateWithLifecycle()
    val importText by viewModel.importText.collectAsStateWithLifecycle()
    val importError by viewModel.importError.collectAsStateWithLifecycle()
    val isEditing by viewModel.isEditing.collectAsStateWithLifecycle()
    var saveAttempted by remember { mutableStateOf(false) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(if (isEditing) "Modifier la ligne" else "Nouvelle ligne") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, "Retour")
                    }
                }
            )
        }
    ) { padding ->
        LazyColumn(Modifier.padding(padding).padding(16.dp)) {
            item {
                OutlinedTextField(
                    value = title,
                    onValueChange = viewModel::setTitle,
                    label = { Text("Nom de la ligne") },
                    modifier = Modifier.fillMaxWidth(),
                    isError = saveAttempted && title.isBlank(),
                    supportingText = if (saveAttempted && title.isBlank()) {
                        { Text("Nom requis") }
                    } else null,
                    singleLine = true
                )
                Spacer(Modifier.height(16.dp))
            }

            item {
                Text("Import rapide", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
                Spacer(Modifier.height(2.dp))
                Text(
                    "Noms de gares SNCF séparés par « > » (ex: Lyon-Perrache > Avignon TGV). Pour les autres arrêts, utilisez le bouton + ci-dessus.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(Modifier.height(4.dp))
                OutlinedTextField(
                    value = importText,
                    onValueChange = viewModel::setImportText,
                    label = { Text("Lyon-Perrache > Avignon TGV > Marseille-Saint-Charles") },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = false,
                    minLines = 2
                )
                if (importError != null) {
                    Text(importError!!, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
                }
                Spacer(Modifier.height(4.dp))
                Button(onClick = viewModel::importStations, enabled = importText.isNotBlank()) {
                    Text("Importer")
                }
                Spacer(Modifier.height(16.dp))
                HorizontalDivider()
                Spacer(Modifier.height(16.dp))
            }

            item {
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        "Arrêts (${stations.size})",
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.weight(1f)
                    )
                    TextButton(onClick = onAddStation) {
                        Icon(Icons.Default.Add, null)
                        Spacer(Modifier.width(4.dp))
                        Text("Ajouter")
                    }
                }
            }

            items(stations, key = { it.id }) { station ->
                Card(Modifier.fillMaxWidth().padding(vertical = 4.dp)) {
                    Row(Modifier.padding(8.dp), verticalAlignment = Alignment.CenterVertically) {
                        Column(Modifier.weight(1f)) {
                            Text(station.name, fontWeight = FontWeight.Medium)
                            Text(
                                "Approche : ${station.approachRadius} m  ·  Arrivée : ${station.arrivalRadius} m",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            // Indique si des messages personnalisés sont définis
                            if (station.approachMessage != null || station.arrivalMessage != null) {
                                Text(
                                    "✎ Annonces personnalisées",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.primary
                                )
                            }
                        }
                        IconButton(onClick = { onEditStation(station.id) }) {
                            Icon(Icons.Default.Edit, "Modifier l'annonce", tint = MaterialTheme.colorScheme.secondary)
                        }
                        IconButton(onClick = { viewModel.moveStationUp(station.id) }) {
                            Icon(Icons.Default.ArrowUpward, "Monter")
                        }
                        IconButton(onClick = { viewModel.moveStationDown(station.id) }) {
                            Icon(Icons.Default.ArrowDownward, "Descendre")
                        }
                        IconButton(onClick = { viewModel.removeStation(station.id) }) {
                            Icon(Icons.Default.Delete, "Supprimer")
                        }
                    }
                }
            }

            item {
                Spacer(Modifier.height(16.dp))
                Button(
                    onClick = {
                        saveAttempted = true
                        if (title.isNotBlank() && stations.isNotEmpty()) viewModel.saveRoute(onBack)
                    },
                    modifier = Modifier.fillMaxWidth(),
                    enabled = stations.isNotEmpty()
                ) {
                    Text(if (isEditing) "Enregistrer les modifications" else "Enregistrer la ligne")
                }
            }
        }
    }
}

