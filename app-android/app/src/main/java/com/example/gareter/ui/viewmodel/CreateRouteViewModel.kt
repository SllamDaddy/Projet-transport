package com.example.gareter.ui.viewmodel

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.gareter.data.model.Route
import com.example.gareter.data.model.Station
import com.example.gareter.data.repository.RouteRepository
import com.example.gareter.data.stations.SNCF_STATIONS
import com.google.gson.Gson
import com.google.gson.annotations.SerializedName
import com.google.gson.reflect.TypeToken
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder
import java.util.UUID

data class GeoSearchResult(
    val name: String,
    val fullAddress: String,
    val latitude: Double,
    val longitude: Double
)

private data class NominatimResult(
    @SerializedName("display_name") val displayName: String,
    val lat: String,
    val lon: String
)

class CreateRouteViewModel(application: Application) : AndroidViewModel(application) {

    private val repository = RouteRepository(application)
    private val gson = Gson()

    private val _title = MutableStateFlow("")
    val title: StateFlow<String> = _title.asStateFlow()

    private val _isEditing = MutableStateFlow(false)
    val isEditing: StateFlow<Boolean> = _isEditing.asStateFlow()

    private var editingId: String? = null

    private val _stations = MutableStateFlow<List<Station>>(emptyList())
    val stations: StateFlow<List<Station>> = _stations.asStateFlow()

    private val _importText = MutableStateFlow("")
    val importText: StateFlow<String> = _importText.asStateFlow()

    private val _importError = MutableStateFlow<String?>(null)
    val importError: StateFlow<String?> = _importError.asStateFlow()

    // Geocoding state
    private val _geoResults = MutableStateFlow<List<GeoSearchResult>>(emptyList())
    val geoResults: StateFlow<List<GeoSearchResult>> = _geoResults.asStateFlow()

    private val _geoSearching = MutableStateFlow(false)
    val geoSearching: StateFlow<Boolean> = _geoSearching.asStateFlow()

    private val _geoError = MutableStateFlow<String?>(null)
    val geoError: StateFlow<String?> = _geoError.asStateFlow()

    // Station en cours d'édition (partagée avec StationEditScreen via navigation)
    private val _editingStationId = MutableStateFlow<String?>(null)
    val editingStationId: StateFlow<String?> = _editingStationId.asStateFlow()

    fun setEditingStationId(stationId: String?) { _editingStationId.value = stationId }

    fun getStation(stationId: String): Station? = _stations.value.find { it.id == stationId }

    // Base de données d'arrêts personnalisés
    private val _customStations = MutableStateFlow<List<Station>>(emptyList())
    val customStations: StateFlow<List<Station>> = _customStations.asStateFlow()

    init {
        viewModelScope.launch {
            repository.customStationsFlow.collect { _customStations.value = it }
        }
    }

    fun setTitle(t: String) { _title.value = t }
    fun setImportText(t: String) { _importText.value = t }

    fun loadRoute(routeId: String) {
        viewModelScope.launch {
            val route = repository.routesFlow.first().find { it.id == routeId }
            route?.let {
                editingId = it.id
                _title.value = it.title
                _stations.value = it.stations
                _isEditing.value = true
            }
        }
    }

    // Quick import "Lyon-Perrache > Avignon TGV > Mon arrêt bus"
    fun importStations() {
        val names = _importText.value.split(">").map { it.trim() }.filter { it.isNotEmpty() }
        val matched = mutableListOf<Station>()
        val unmatched = mutableListOf<String>()

        names.forEach { name ->
            val clean = name.replace(Regex("^(gare|station)\\s+", RegexOption.IGNORE_CASE), "").trim()
            val sncfRef = SNCF_STATIONS.find { it.name.equals(clean, ignoreCase = true) }
                ?: SNCF_STATIONS.find { it.name.contains(clean, ignoreCase = true) }
                ?: SNCF_STATIONS.find { it.name.equals(name, ignoreCase = true) }
                ?: SNCF_STATIONS.find { it.name.contains(name, ignoreCase = true) }

            val customRef = if (sncfRef == null) {
                _customStations.value.find { it.name.equals(clean, ignoreCase = true) }
                    ?: _customStations.value.find { it.name.contains(clean, ignoreCase = true) }
                    ?: _customStations.value.find { it.name.equals(name, ignoreCase = true) }
                    ?: _customStations.value.find { it.name.contains(name, ignoreCase = true) }
            } else null

            when {
                sncfRef != null -> matched.add(
                    Station(UUID.randomUUID().toString(), sncfRef.name, sncfRef.latitude, sncfRef.longitude)
                )
                customRef != null -> matched.add(
                    Station(UUID.randomUUID().toString(), customRef.name, customRef.latitude, customRef.longitude,
                        customRef.approachRadius, customRef.arrivalRadius)
                )
                else -> unmatched.add(name)
            }
        }

        _importError.value = if (unmatched.isNotEmpty())
            "Non trouvé : ${unmatched.joinToString(", ")}\n→ Utilisez + › Autre arrêt pour créer ces stops"
        else null
        if (matched.isNotEmpty()) {
            _stations.value = _stations.value + matched
            _importText.value = ""
        }
    }

    fun searchGeocode(query: String) {
        if (query.isBlank()) return
        viewModelScope.launch {
            _geoSearching.value = true
            _geoError.value = null
            try {
                _geoResults.value = fetchNominatim(query)
                if (_geoResults.value.isEmpty()) _geoError.value = "Aucun résultat pour « $query »"
            } catch (e: Exception) {
                _geoError.value = "Erreur réseau : ${e.message}"
                _geoResults.value = emptyList()
            }
            _geoSearching.value = false
        }
    }

    fun clearGeoResults() {
        _geoResults.value = emptyList()
        _geoError.value = null
    }

    private suspend fun fetchNominatim(query: String): List<GeoSearchResult> = withContext(Dispatchers.IO) {
        val encoded = URLEncoder.encode(query, "UTF-8")
        val url = URL("https://nominatim.openstreetmap.org/search?q=$encoded&format=json&countrycodes=fr&limit=8")
        val connection = url.openConnection() as HttpURLConnection
        connection.setRequestProperty("User-Agent", "GareTER Android/1.0 madi.douhouchina@gmail.com")
        connection.connectTimeout = 8000
        connection.readTimeout = 8000
        val response = connection.inputStream.bufferedReader().readText()
        val type = object : TypeToken<List<NominatimResult>>() {}.type
        val raw: List<NominatimResult> = gson.fromJson(response, type) ?: emptyList()
        raw.map { r ->
            val shortName = r.displayName.split(",").firstOrNull()?.trim() ?: r.displayName
            GeoSearchResult(shortName, r.displayName, r.lat.toDouble(), r.lon.toDouble())
        }
    }

    fun addStation(station: Station) {
        _stations.value = _stations.value + station
    }

    fun updateStation(updated: Station) {
        _stations.value = _stations.value.map { if (it.id == updated.id) updated else it }
    }

    fun removeStation(stationId: String) {
        _stations.value = _stations.value.filter { it.id != stationId }
    }

    fun moveStationUp(stationId: String) {
        val list = _stations.value.toMutableList()
        val idx = list.indexOfFirst { it.id == stationId }
        if (idx > 0) {
            val tmp = list[idx]; list[idx] = list[idx - 1]; list[idx - 1] = tmp
        }
        _stations.value = list
    }

    fun moveStationDown(stationId: String) {
        val list = _stations.value.toMutableList()
        val idx = list.indexOfFirst { it.id == stationId }
        if (idx in 0 until list.size - 1) {
            val tmp = list[idx]; list[idx] = list[idx + 1]; list[idx + 1] = tmp
        }
        _stations.value = list
    }

    fun saveToCustomDb(station: Station) {
        viewModelScope.launch { repository.saveCustomStation(station) }
    }

    fun deleteCustomStation(stationId: String) {
        viewModelScope.launch { repository.deleteCustomStation(stationId) }
    }

    fun reset() {
        _title.value = ""
        _stations.value = emptyList()
        _importText.value = ""
        _importError.value = null
        _isEditing.value = false
        editingId = null
        clearGeoResults()
    }

    fun saveRoute(onDone: () -> Unit) {
        viewModelScope.launch {
            repository.saveRoute(Route(
                id = editingId ?: UUID.randomUUID().toString(),
                title = _title.value.trim(),
                stations = _stations.value
            ))
            onDone()
        }
    }
}
