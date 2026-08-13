package com.example.gareter.data.model

import androidx.room.Entity
import androidx.room.PrimaryKey
import java.util.UUID

@Entity(tableName = "abonnement_passes")
data class AbonnementPass(
    @PrimaryKey val id: String = UUID.randomUUID().toString(), // = contenu du QR code
    val saleId: String,
    val startedAt: Long = System.currentTimeMillis(),
    val expiresAt: Long,
) {
    val isValid: Boolean get() = System.currentTimeMillis() < expiresAt
}
