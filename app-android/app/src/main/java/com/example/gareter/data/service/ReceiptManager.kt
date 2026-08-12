package com.example.gareter.data.service

import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.core.content.FileProvider
import com.example.gareter.data.model.TicketType
import com.example.gareter.data.model.TicketSale
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class ReceiptManager(private val context: Context) {

    suspend fun generateReceiptText(
        sale: TicketSale,
        agentNumber: String,
        lineNumber: String,
    ): String = withContext(Dispatchers.IO) {
        val priceText = "€${sale.priceCents / 100}.${(sale.priceCents % 100).toString().padStart(2, '0')}"
        val date = SimpleDateFormat("dd/MM/yyyy HH:mm:ss", Locale.FRANCE).format(Date(sale.soldAt))

        buildString {
            appendLine("═══════════════════════════════════════")
            appendLine("         GIROUETTE BUS - TER")
            appendLine("═══════════════════════════════════════")
            appendLine()
            appendLine("REÇU DE VENTE")
            appendLine()
            appendLine("Type de ticket : ${sale.type.displayName}")
            appendLine("Prix           : $priceText")
            appendLine()
            appendLine("Numéro agent   : $agentNumber")
            appendLine("Ligne          : $lineNumber")
            appendLine()
            appendLine("Date et heure  : $date")
            appendLine("Référence      : ${sale.id.take(8)}")
            appendLine()
            appendLine("═══════════════════════════════════════")
            appendLine()
            appendLine("Conditions générales :")
            appendLine("- Ticket valide une seule fois")
            appendLine("- Conservez ce reçu en cas de contrôle")
            appendLine()
            appendLine("═══════════════════════════════════════")
            appendLine("     MERCI DE VOTRE VISITE !")
            appendLine("═══════════════════════════════════════")
            appendLine()
        }
    }

    // Envoyer reçu par SMS
    suspend fun sendReceiptViaSMS(phoneNumber: String, receiptText: String) {
        withContext(Dispatchers.Main) {
            try {
                val intent = Intent(Intent.ACTION_VIEW).apply {
                    data = Uri.parse("sms:$phoneNumber")
                    putExtra("sms_body", "Votre reçu Girouette Bus :\n\n$receiptText")
                }
                context.startActivity(intent)
            } catch (e: Exception) {
                // SMS app not available
            }
        }
    }

    // Envoyer reçu par email
    suspend fun sendReceiptViaEmail(
        emailAddress: String,
        receiptText: String,
        ticketType: String,
    ) {
        withContext(Dispatchers.Main) {
            try {
                val intent = Intent(Intent.ACTION_SEND).apply {
                    type = "text/plain"
                    putExtra(Intent.EXTRA_EMAIL, arrayOf(emailAddress))
                    putExtra(Intent.EXTRA_SUBJECT, "Votre reçu Girouette Bus - $ticketType")
                    putExtra(Intent.EXTRA_TEXT, receiptText)
                }
                context.startActivity(Intent.createChooser(intent, "Envoyer le reçu"))
            } catch (e: Exception) {
                // Email app not available
            }
        }
    }

    // Proposer d'envoyer le reçu (sheet de choix)
    suspend fun showReceiptOptions(
        receiptText: String,
        ticketType: String,
    ) {
        // Ce composable sera appelé depuis TrackingScreen
        // Pour afficher un BottomSheet avec les options : SMS / Email / Imprimer / Rien
    }
}

private val TicketType.displayName: String
    get() = when (this) {
        TicketType.PLEIN_TARIF -> "Plein tarif (1 voyage)"
        TicketType.CARNET -> "Carnet 10 voyages"
        TicketType.ABONNEMENT_MENSUEL -> "Abonnement mensuel"
        TicketType.CONTREMARQUE -> "Contremarque"
    }
