package com.example.gareter.data.model

import androidx.room.Entity
import androidx.room.PrimaryKey
import java.util.UUID

// Une montée validée par scan d'un abonnement mensuel — pas de décompte d'unités,
// juste un événement de montée pour les statistiques par arrêt.
@Entity(tableName = "abonnement_scans")
data class AbonnementScan(
    @PrimaryKey val id: String = UUID.randomUUID().toString(),
    val passId: String,
    val stationId: String? = null,
    val stationName: String? = null,
    val routeId: String? = null,
    val routeTitle: String? = null,
    val scannedAt: Long = System.currentTimeMillis(),
    val synced: Boolean = false,
)
