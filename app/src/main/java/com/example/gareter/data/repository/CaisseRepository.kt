package com.example.gareter.data.repository

import com.example.gareter.data.db.CaisseDao
import com.example.gareter.data.model.CarnetTicket
import com.example.gareter.data.model.ServiceSession
import com.example.gareter.data.model.TicketSale
import com.example.gareter.data.model.TicketType
import kotlinx.coroutines.flow.Flow
import java.util.UUID

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

    suspend fun sellTicket(sessionId: String, type: TicketType, priceCents: Int): TicketSale {
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
        )
        dao.insertSale(sale)

        if (type == TicketType.CARNET && carnetId != null) {
            dao.insertCarnet(CarnetTicket(id = carnetId, saleId = sale.id))
        }
        return sale
    }

    suspend fun cancelLastSale(sale: TicketSale) {
        dao.deleteSale(sale)
    }

    // ── Carnet ───────────────────────────────────────────────────────────────────

    suspend fun getCarnet(carnetId: String): CarnetTicket? =
        dao.getCarnetById(carnetId)

    suspend fun useCarnetUnit(carnet: CarnetTicket): CarnetTicket {
        val updated = carnet.copy(usedUnits = carnet.usedUnits + 1)
        dao.updateCarnet(updated)
        return updated
    }
}
