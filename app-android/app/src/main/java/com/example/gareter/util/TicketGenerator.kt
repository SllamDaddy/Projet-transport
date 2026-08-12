package com.example.gareter.util

import com.example.gareter.data.model.CarnetTicket
import com.example.gareter.data.model.ServiceSession
import com.example.gareter.data.model.TicketSale
import com.example.gareter.data.model.TicketType
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

object TicketGenerator {

    data class TicketData(
        val appName: String = "GIROUETTE BUS",
        val lineLabel: String,
        val typeName: String,
        val priceText: String,
        val dateTime: String,
        val validityText: String,
        val ticketNumber: String,
        val qrContent: String?,        // null si pas de QR (abonnement)
        val carnetUnitsLeft: Int? = null,
    )

    private val dtFmt = SimpleDateFormat("dd/MM/yy HH:mm", Locale.FRANCE)

    fun generate(sale: TicketSale, session: ServiceSession, carnet: CarnetTicket? = null): TicketData {
        val line = session.lineLabel?.let { "Ligne $it" } ?: "Service"
        val dateTime = dtFmt.format(Date(sale.soldAt))
        val priceText = formatCents(sale.priceCents)

        val (typeName, validityText, qrContent, unitsLeft) = when (sale.type) {
            TicketType.PLEIN_TARIF -> Quad(
                "PLEIN TARIF",
                "Valable 1h30 · 1 trajet",
                sale.id,
                null,
            )
            TicketType.CONTREMARQUE -> Quad(
                "CONTREMARQUE",
                "Valable 1h30 · correspondance",
                sale.id,
                null,
            )
            TicketType.CARNET -> Quad(
                "CARNET 10 TRAJETS",
                "Pas d'expiration · ${carnet?.remainingUnits ?: 10}/10 unités",
                carnet?.id ?: sale.carnetId,
                carnet?.remainingUnits,
            )
            TicketType.ABONNEMENT_MENSUEL -> Quad(
                "ABONNEMENT MENSUEL",
                "Valable jusqu'au ${monthEnd(sale.soldAt)}",
                null,
                null,
            )
        }

        val ticketNum = sale.id.takeLast(8).uppercase()

        return TicketData(
            lineLabel = line,
            typeName = typeName,
            priceText = priceText,
            dateTime = dateTime,
            validityText = validityText,
            ticketNumber = "N° $ticketNum",
            qrContent = qrContent,
            carnetUnitsLeft = unitsLeft,
        )
    }

    fun formatCents(cents: Int): String =
        if (cents == 0) "Gratuit" else "${"%.2f".format(cents / 100.0)} €"

    private fun monthEnd(ts: Long): String {
        val cal = java.util.Calendar.getInstance()
        cal.timeInMillis = ts
        cal.set(java.util.Calendar.DAY_OF_MONTH, cal.getActualMaximum(java.util.Calendar.DAY_OF_MONTH))
        return SimpleDateFormat("dd/MM/yyyy", Locale.FRANCE).format(cal.time)
    }

    private data class Quad<A, B, C, D>(val a: A, val b: B, val c: C, val d: D)
    private operator fun <A, B, C, D> Quad<A, B, C, D>.component1() = a
    private operator fun <A, B, C, D> Quad<A, B, C, D>.component2() = b
    private operator fun <A, B, C, D> Quad<A, B, C, D>.component3() = c
    private operator fun <A, B, C, D> Quad<A, B, C, D>.component4() = d
}
