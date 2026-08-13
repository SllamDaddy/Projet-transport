package com.example.gareter.data.db

import androidx.room.*
import com.example.gareter.data.model.AbonnementPass
import com.example.gareter.data.model.AbonnementScan
import com.example.gareter.data.model.CarnetTicket
import com.example.gareter.data.model.CarnetUsageEvent
import com.example.gareter.data.model.ServiceSession
import com.example.gareter.data.model.TicketSale
import com.example.gareter.data.model.TicketType
import kotlinx.coroutines.flow.Flow

@Dao
interface CaisseDao {

    // ── Sessions ────────────────────────────────────────────────────────────────

    @Insert
    suspend fun insertSession(session: ServiceSession)

    @Update
    suspend fun updateSession(session: ServiceSession)

    @Query("SELECT * FROM service_sessions WHERE isActive = 1 LIMIT 1")
    fun getActiveSession(): Flow<ServiceSession?>

    @Query("SELECT * FROM service_sessions ORDER BY startedAt DESC LIMIT 50")
    fun getAllSessions(): Flow<List<ServiceSession>>

    // ── Ventes tickets ───────────────────────────────────────────────────────────

    @Insert
    suspend fun insertSale(sale: TicketSale)

    @Delete
    suspend fun deleteSale(sale: TicketSale)

    @Query("SELECT * FROM ticket_sales WHERE sessionId = :sessionId ORDER BY soldAt DESC")
    fun getSalesBySession(sessionId: String): Flow<List<TicketSale>>

    @Query("SELECT * FROM ticket_sales WHERE sessionId = :sessionId ORDER BY soldAt DESC LIMIT 1")
    fun getLastSale(sessionId: String): Flow<TicketSale?>

    @Query("SELECT COALESCE(SUM(priceCents), 0) FROM ticket_sales WHERE sessionId = :sessionId")
    fun getTotalCentsBySession(sessionId: String): Flow<Int>

    @Query("SELECT COUNT(*) FROM ticket_sales WHERE sessionId = :sessionId")
    fun getCountBySession(sessionId: String): Flow<Int>

    @Query("SELECT COUNT(*) FROM ticket_sales WHERE sessionId = :sessionId AND type = :type")
    fun getCountByType(sessionId: String, type: TicketType): Flow<Int>

    @Query("SELECT * FROM ticket_sales WHERE synced = 0 ORDER BY soldAt ASC LIMIT 200")
    suspend fun getUnsyncedSales(): List<TicketSale>

    @Query("UPDATE ticket_sales SET synced = 1 WHERE id IN (:ids)")
    suspend fun markSalesSynced(ids: List<String>)

    // ── Carnets ─────────────────────────────────────────────────────────────────

    @Insert
    suspend fun insertCarnet(carnet: CarnetTicket)

    @Update
    suspend fun updateCarnet(carnet: CarnetTicket)

    @Query("SELECT * FROM carnet_tickets WHERE id = :carnetId LIMIT 1")
    suspend fun getCarnetById(carnetId: String): CarnetTicket?

    @Insert
    suspend fun insertCarnetUsageEvent(event: CarnetUsageEvent)

    @Query("SELECT * FROM carnet_usage_events WHERE synced = 0 ORDER BY usedAt ASC LIMIT 200")
    suspend fun getUnsyncedCarnetUsageEvents(): List<CarnetUsageEvent>

    @Query("UPDATE carnet_usage_events SET synced = 1 WHERE id IN (:ids)")
    suspend fun markCarnetUsageEventsSynced(ids: List<String>)

    // ── Abonnements ─────────────────────────────────────────────────────────────

    @Insert
    suspend fun insertAbonnementPass(pass: AbonnementPass)

    @Query("SELECT * FROM abonnement_passes WHERE id = :passId LIMIT 1")
    suspend fun getAbonnementPassById(passId: String): AbonnementPass?

    @Insert
    suspend fun insertAbonnementScan(scan: AbonnementScan)

    @Query("SELECT * FROM abonnement_scans WHERE synced = 0 ORDER BY scannedAt ASC LIMIT 200")
    suspend fun getUnsyncedAbonnementScans(): List<AbonnementScan>

    @Query("UPDATE abonnement_scans SET synced = 1 WHERE id IN (:ids)")
    suspend fun markAbonnementScansSynced(ids: List<String>)
}
