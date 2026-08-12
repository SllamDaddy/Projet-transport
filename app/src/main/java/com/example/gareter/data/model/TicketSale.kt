package com.example.gareter.data.model

import androidx.room.Entity
import androidx.room.PrimaryKey
import java.util.UUID

@Entity(tableName = "ticket_sales")
data class TicketSale(
    @PrimaryKey val id: String = UUID.randomUUID().toString(),
    val sessionId: String,
    val type: TicketType,
    val priceCents: Int,
    val soldAt: Long = System.currentTimeMillis(),
    // null pour carnet/abonnement ; soldAt + 90min pour ticket simple et contremarque
    val expiresAt: Long? = null,
    // renseigné uniquement si type == CARNET, référence CarnetTicket.id
    val carnetId: String? = null,
)
