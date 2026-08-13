package com.example.gareter.data.repository

import com.example.gareter.data.model.AbonnementScan
import com.example.gareter.data.model.CarnetUsageEvent
import com.example.gareter.data.model.TicketSale
import com.google.gson.Gson
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext
import java.net.HttpURLConnection
import java.net.URL
import java.time.Instant

// Remonte vers Supabase les ventes/scans effectués localement (Room), pour
// alimenter la page Statistiques du web-admin. Chaque événement est marqué
// `synced` en local une fois l'upload confirmé ; les échecs (hors-ligne) sont
// simplement retentés au prochain appel (à la vente suivante, au démarrage de
// l'app ou via le bouton ↻ de Home).
class VenteSyncRepository(
    private val caisseRepo: CaisseRepository,
    private val routeRepo: RouteRepository,
) {
    private val gson = Gson()

    private companion object {
        const val TABLE_URL = "https://pnqdwreqxdwcyggdioba.supabase.co/rest/v1/evenements_billettique?on_conflict=id"
        const val API_KEY = "sb_publishable_whHuPPByZUTGq3qsMsEpFw_UdV-fAZR"
    }

    private data class EvenementPayload(
        val id: String,
        val type_evenement: String,
        val type_ticket: String,
        val prix_cents: Int,
        val station_id: String?,
        val station_nom: String?,
        val ligne_id: String?,
        val ligne_nom: String?,
        val agent_id: String?,
        val horodatage: String,
    )

    suspend fun syncPending(): Unit = withContext(Dispatchers.IO) {
        val agentId = routeRepo.driverAgentFlow.first()

        val sales = caisseRepo.getUnsyncedSales()
        if (sales.isNotEmpty()) {
            val payload = sales.map { it.toPayload(agentId) }
            if (postBatch(payload)) {
                caisseRepo.markSalesSynced(sales.map { it.id })
            }
        }

        val carnetUsages = caisseRepo.getUnsyncedCarnetUsageEvents()
        if (carnetUsages.isNotEmpty()) {
            val payload = carnetUsages.map { it.toPayload(agentId) }
            if (postBatch(payload)) {
                caisseRepo.markCarnetUsageEventsSynced(carnetUsages.map { it.id })
            }
        }

        val abonnementScans = caisseRepo.getUnsyncedAbonnementScans()
        if (abonnementScans.isNotEmpty()) {
            val payload = abonnementScans.map { it.toPayload(agentId) }
            if (postBatch(payload)) {
                caisseRepo.markAbonnementScansSynced(abonnementScans.map { it.id })
            }
        }
    }

    private fun TicketSale.toPayload(agentId: String?) = EvenementPayload(
        id = id,
        type_evenement = "VENTE",
        type_ticket = type.name,
        prix_cents = priceCents,
        station_id = stationId,
        station_nom = stationName,
        ligne_id = routeId,
        ligne_nom = routeTitle,
        agent_id = agentId,
        horodatage = Instant.ofEpochMilli(soldAt).toString(),
    )

    private fun CarnetUsageEvent.toPayload(agentId: String?) = EvenementPayload(
        id = id,
        type_evenement = "SCAN_CARNET",
        type_ticket = "CARNET",
        prix_cents = 0,
        station_id = stationId,
        station_nom = stationName,
        ligne_id = routeId,
        ligne_nom = routeTitle,
        agent_id = agentId,
        horodatage = Instant.ofEpochMilli(usedAt).toString(),
    )

    private fun AbonnementScan.toPayload(agentId: String?) = EvenementPayload(
        id = id,
        type_evenement = "SCAN_ABONNEMENT",
        type_ticket = "ABONNEMENT_MENSUEL",
        prix_cents = 0,
        station_id = stationId,
        station_nom = stationName,
        ligne_id = routeId,
        ligne_nom = routeTitle,
        agent_id = agentId,
        horodatage = Instant.ofEpochMilli(scannedAt).toString(),
    )

    private fun postBatch(payload: List<EvenementPayload>): Boolean {
        return try {
            val connection = URL(TABLE_URL).openConnection() as HttpURLConnection
            connection.requestMethod = "POST"
            connection.doOutput = true
            connection.connectTimeout = 8000
            connection.readTimeout = 8000
            connection.setRequestProperty("apikey", API_KEY)
            connection.setRequestProperty("Authorization", "Bearer $API_KEY")
            connection.setRequestProperty("Content-Type", "application/json")
            connection.setRequestProperty("Prefer", "resolution=merge-duplicates,return=minimal")
            connection.setRequestProperty("User-Agent", "GareTER/1.0 (Android)")

            connection.outputStream.bufferedWriter().use { it.write(gson.toJson(payload)) }
            val ok = connection.responseCode in 200..299
            if (!ok) connection.errorStream?.bufferedReader()?.use { it.readText() }
            ok
        } catch (e: Exception) {
            e.printStackTrace()
            false
        }
    }
}
