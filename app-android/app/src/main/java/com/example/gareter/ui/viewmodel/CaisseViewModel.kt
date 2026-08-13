package com.example.gareter.ui.viewmodel

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.gareter.data.db.AppDatabase
import com.example.gareter.data.model.AbonnementPass
import com.example.gareter.data.model.CarnetTicket
import com.example.gareter.data.model.Route
import com.example.gareter.data.model.ServiceSession
import com.example.gareter.data.model.TicketSale
import com.example.gareter.data.model.TicketType
import com.example.gareter.data.repository.CaisseRepository
import com.example.gareter.data.repository.RouteRepository
import com.example.gareter.data.repository.VenteSyncRepository
import com.example.gareter.service.LocationService
import com.example.gareter.util.Haversine
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch

@OptIn(ExperimentalCoroutinesApi::class)
class CaisseViewModel(application: Application) : AndroidViewModel(application) {

    private val db = AppDatabase.getInstance(application)
    private val caisseRepo = CaisseRepository(db.caisseDao())
    private val routeRepo = RouteRepository(application)
    private val venteSyncRepo = VenteSyncRepository(caisseRepo, routeRepo)

    init {
        // Rattrape les ventes/scans restés en attente après une coupure réseau.
        triggerSync()
    }

    val activeSession: StateFlow<ServiceSession?> = caisseRepo.getActiveSession()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), null)

    val tariffs: StateFlow<Map<TicketType, Int>> = routeRepo.tariffsFlow
        .stateIn(
            viewModelScope, SharingStarted.WhileSubscribed(5000),
            TicketType.entries.associate { it to it.defaultPriceCents },
        )

    val sales: StateFlow<List<TicketSale>> = activeSession
        .flatMapLatest { session ->
            if (session != null) caisseRepo.getSalesBySession(session.id)
            else flowOf(emptyList())
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val totalCents: StateFlow<Int> = activeSession
        .flatMapLatest { session ->
            if (session != null) caisseRepo.getTotalCentsBySession(session.id)
            else flowOf(0)
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), 0)

    val lastSale: StateFlow<TicketSale?> = activeSession
        .flatMapLatest { session ->
            if (session != null) caisseRepo.getLastSale(session.id)
            else flowOf(null)
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), null)

    val countByType: StateFlow<Map<TicketType, Int>> = sales
        .map { list -> TicketType.entries.associate { t -> t to list.count { it.type == t } } }
        .stateIn(
            viewModelScope, SharingStarted.WhileSubscribed(5000),
            TicketType.entries.associate { it to 0 },
        )

    // Dernier ticket vendu — affiché pour confirmation + impression
    private val _lastSoldSale = MutableStateFlow<TicketSale?>(null)
    val lastSoldSale: StateFlow<TicketSale?> = _lastSoldSale.asStateFlow()

    // Arrêt le plus proche de la position GPS courante, sur le trajet actif —
    // renseigné uniquement si une session de tracking (LocationService) tourne.
    private data class StationInfo(
        val stationId: String, val stationName: String, val routeId: String, val routeTitle: String,
    )

    private fun currentStationInfo(): StationInfo? {
        val route: Route = LocationService.activeRoute.value ?: return null
        val loc = LocationService.currentLocation.value ?: return null
        val station = Haversine.nearestStation(route.stations, loc.latitude, loc.longitude) ?: return null
        return StationInfo(station.id, station.name, route.id, route.title)
    }

    private fun triggerSync() {
        viewModelScope.launch { venteSyncRepo.syncPending() }
    }

    fun startSession(lineLabel: String? = null) {
        viewModelScope.launch { caisseRepo.startSession(lineLabel) }
    }

    fun endSession() {
        viewModelScope.launch {
            activeSession.value?.let { caisseRepo.endSession(it) }
        }
    }

    fun sellTicket(type: TicketType) {
        viewModelScope.launch {
            val station = currentStationInfo()
            val session = activeSession.value ?: caisseRepo.startSession(station?.routeTitle)
            val price = tariffs.value[type] ?: type.defaultPriceCents
            val sale = caisseRepo.sellTicket(
                sessionId = session.id,
                type = type,
                priceCents = price,
                stationId = station?.stationId,
                stationName = station?.stationName,
                routeId = station?.routeId,
                routeTitle = station?.routeTitle,
            )
            _lastSoldSale.value = sale
            triggerSync()
        }
    }

    fun clearLastSold() { _lastSoldSale.value = null }

    fun cancelLastSale() {
        viewModelScope.launch {
            lastSale.value?.let { caisseRepo.cancelLastSale(it) }
        }
    }

    suspend fun getCarnet(carnetId: String): CarnetTicket? = caisseRepo.getCarnet(carnetId)

    fun useCarnetUnit(carnet: CarnetTicket) {
        viewModelScope.launch {
            val station = currentStationInfo()
            caisseRepo.useCarnetUnit(
                carnet = carnet,
                stationId = station?.stationId,
                stationName = station?.stationName,
                routeId = station?.routeId,
                routeTitle = station?.routeTitle,
            )
            triggerSync()
        }
    }

    suspend fun getAbonnementPass(passId: String): AbonnementPass? = caisseRepo.getAbonnementPass(passId)

    fun scanAbonnement(pass: AbonnementPass) {
        viewModelScope.launch {
            val station = currentStationInfo()
            caisseRepo.scanAbonnement(
                pass = pass,
                stationId = station?.stationId,
                stationName = station?.stationName,
                routeId = station?.routeId,
                routeTitle = station?.routeTitle,
            )
            triggerSync()
        }
    }
}
