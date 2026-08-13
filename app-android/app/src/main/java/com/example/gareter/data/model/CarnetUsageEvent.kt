package com.example.gareter.data.model

import androidx.room.Entity
import androidx.room.PrimaryKey
import java.util.UUID

// Une unité de carnet consommée (scan retour) — CarnetTicket ne garde que le total
// consommé, cet événement capture chaque montée individuellement pour les stats.
@Entity(tableName = "carnet_usage_events")
data class CarnetUsageEvent(
    @PrimaryKey val id: String = UUID.randomUUID().toString(),
    val carnetId: String,
    val stationId: String? = null,
    val stationName: String? = null,
    val routeId: String? = null,
    val routeTitle: String? = null,
    val usedAt: Long = System.currentTimeMillis(),
    val synced: Boolean = false,
)
