package com.example.gareter.data.model

import androidx.room.Entity
import androidx.room.PrimaryKey
import java.util.UUID

@Entity(tableName = "carnet_tickets")
data class CarnetTicket(
    @PrimaryKey val id: String = UUID.randomUUID().toString(), // = contenu du QR code
    val saleId: String,
    val totalUnits: Int = 10,
    val usedUnits: Int = 0,
    val createdAt: Long = System.currentTimeMillis(),
    // Pas d'expiration pour les carnets
) {
    val remainingUnits: Int get() = totalUnits - usedUnits
    val isValid: Boolean get() = remainingUnits > 0
}
