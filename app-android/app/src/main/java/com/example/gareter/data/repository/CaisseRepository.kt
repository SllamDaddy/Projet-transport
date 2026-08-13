package com.example.gareter.data.repository

import com.example.gareter.data.db.CaisseDao
import com.example.gareter.data.model.AbonnementPass
import com.example.gareter.data.model.AbonnementScan
import com.example.gareter.data.model.CarnetTicket
import com.example.gareter.data.model.CarnetUsageEvent
import com.example.gareter.data.model.ServiceSession
import com.example.gareter.data.model.TicketSale
import com.example.gareter.data.model.TicketType
import kotlinx.coroutines.flow.Flow
import java.util.UUID
import java.util.concurrent.TimeUnit

class CaisseRepository(private val dao: CaisseDao) {

    // ── Session ──────────────────────────────────────────────────────────────────

    fun getActiveSession(): Flow<ServiceSession?> = dao.getActiveSession()

    suspend fun startSession(lineLabel: String? = null): ServiceSession {
        val session = ServiceSession(lineLabel = lineLabel)
        dao.insertSession(session)
        return session
    }

    suspend fun endSession(session: ServiceSession) {
        dao.updateSession(session.copy(endedAt = System.currentTimeMillis(), isActive = false))
    }

    // ── Ventes ───────────────────────────────────────────────────────────────────

    fun getSalesBySession(sessionId: String): Flow<List<TicketSale>> =
        dao.getSalesBySession(sessionId)

    fun getLastSale(sessionId: String): Flow<TicketSale?> =
        dao.getLastSale(sessionId)

    fun getTotalCentsBySession(sessionId: String): Flow<Int> =
        dao.getTotalCentsBySession(sessionId)

    fun getCountBySession(sessionId: String): Flow<Int> =
        dao.getCountBySession(sessionId)

    fun getCountByType(sessionId: String, type: TicketType): Flow<Int> =
        dao.getCountByType(sessionId, type)

    suspend fun sellTicket(
        sessionId: String,
        type: TicketType,
        priceCents: Int,
        stationId: String? = null,
        stationName: String? = null,
        routeId: String? = null,
        routeTitle: String? = null,
    ): TicketSale {
        val now = System.currentTimeMillis()
        val expiresAt = type.validityMinutes?.let { now + it * 60_000L }
        val carnetId = if (type == TicketType.CARNET) UUID.randomUUID().toString() else null

        val sale = TicketSale(
            sessionId = sessionId,
            type = type,
            priceCents = priceCents,
            soldAt = now,
            expiresAt = expiresAt,
            carnetId = carnetId,
            stationId = stationId,
            stationName = stationName,
            routeId = routeId,
            routeTitle = routeTitle,
        )
        dao.insertSale(sale)

        if (type == TicketType.CARNET && carnetId != null) {
            dao.insertCarnet(CarnetTicket(id = carnetId, saleId = sale.id))
        }
        if (type == TicketType.ABONNEMENT_MENSUEL) {
            dao.insertAbonnementPass(
                AbonnementPass(
                    saleId = sale.id,
                    startedAt = now,
                    expiresAt = now + TimeUnit.DAYS.toMillis(30),
                )
            )
        }
        return sale
    }

    suspend fun cancelLastSale(sale: TicketSale) {
        dao.deleteSale(sale)
    }

    suspend fun getUnsyncedSales(): List<TicketSale> = dao.getUnsyncedSales()

    suspend fun markSalesSynced(ids: List<String>) {
        if (ids.isNotEmpty()) dao.markSalesSynced(ids)
    }

    // ── Carnet ───────────────────────────────────────────────────────────────────

    suspend fun getCarnet(carnetId: String): CarnetTicket? =
        dao.getCarnetById(carnetId)

    suspend fun useCarnetUnit(
        carnet: CarnetTicket,
        stationId: String? = null,
        stationName: String? = null,
        routeId: String? = null,
        routeTitle: String? = null,
    ): CarnetTicket {
        val updated = carnet.copy(usedUnits = carnet.usedUnits + 1)
        dao.updateCarnet(updated)
        dao.insertCarnetUsageEvent(
            CarnetUsageEvent(
                carnetId = carnet.id,
                stationId = stationId,
                stationName = stationName,
                routeId = routeId,
                routeTitle = routeTitle,
            )
        )
        return updated
    }

    suspend fun getUnsyncedCarnetUsageEvents(): List<CarnetUsageEvent> = dao.getUnsyncedCarnetUsageEvents()

    suspend fun markCarnetUsageEventsSynced(ids: List<String>) {
        if (ids.isNotEmpty()) dao.markCarnetUsageEventsSynced(ids)
    }

    // ── Abonnement ───────────────────────────────────────────────────────────────

    suspend fun getAbonnementPass(passId: String): AbonnementPass? =
        dao.getAbonnementPassById(passId)

    suspend fun scanAbonnement(
        pass: AbonnementPass,
        stationId: String? = null,
        stationName: String? = null,
        routeId: String? = null,
        routeTitle: String? = null,
    ): AbonnementScan {
        val scan = AbonnementScan(
            passId = pass.id,
            stationId = stationId,
            stationName = stationName,
            routeId = routeId,
            routeTitle = routeTitle,
        )
        dao.insertAbonnementScan(scan)
        return scan
    }

    suspend fun getUnsyncedAbonnementScans(): List<AbonnementScan> = dao.getUnsyncedAbonnementScans()

    suspend fun markAbonnementScansSynced(ids: List<String>) {
        if (ids.isNotEmpty()) dao.markAbonnementScansSynced(ids)
    }
}
