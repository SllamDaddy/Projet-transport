package com.example.gareter.data.repository

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import com.example.gareter.data.model.TicketType
import androidx.datastore.preferences.preferencesDataStore
import com.example.gareter.data.model.Route
import com.example.gareter.data.model.Station
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.net.HttpURLConnection
import java.net.URL

val Context.dataStore: DataStore<Preferences> by preferencesDataStore(name = "gare_ter_prefs")

class RouteRepository(private val context: Context) {

    private val gson = Gson()

    companion object {
        private val ROUTES_KEY = stringPreferencesKey("routes")
        private val TRACKING_KEY = booleanPreferencesKey("tracking")
        private val ACTIVE_ROUTE_KEY = stringPreferencesKey("active_route_id")
        private val COOLDOWNS_KEY = stringPreferencesKey("cooldowns")
        private val AUDIO_DUCKING_KEY = booleanPreferencesKey("audio_ducking")
        private val PREFERRED_DEVICE_ID_KEY = stringPreferencesKey("preferred_device_id")
        private val CUSTOM_STATIONS_KEY = stringPreferencesKey("custom_stations")
        private val THEME_MODE_KEY = stringPreferencesKey("theme_mode")
        private val TARIFFS_KEY = stringPreferencesKey("tariffs_json")
    }

    val routesFlow: Flow<List<Route>> = context.dataStore.data.map { prefs ->
        val json = prefs[ROUTES_KEY]
        if (json == null) {
            loadDefaultRoutes()
        } else {
            val type = object : TypeToken<List<Route>>() {}.type
            gson.fromJson<List<Route>>(json, type) ?: emptyList()
        }
    }

    private fun loadDefaultRoutes(): List<Route> {
        return try {
            context.assets.open("default_routes.json").bufferedReader().use { reader ->
                val type = object : TypeToken<List<Route>>() {}.type
                gson.fromJson<List<Route>>(reader.readText(), type) ?: emptyList()
            }
        } catch (e: Exception) {
            emptyList()
        }
    }

    private suspend fun getRoutes(): List<Route> = routesFlow.first()

    suspend fun saveRoutes(routes: List<Route>) {
        context.dataStore.edit { it[ROUTES_KEY] = gson.toJson(routes) }
    }

    suspend fun saveRoute(route: Route) {
        val current = getRoutes().toMutableList()
        val idx = current.indexOfFirst { it.id == route.id }
        if (idx >= 0) current[idx] = route else current.add(route)
        saveRoutes(current)
    }

    suspend fun deleteRoute(routeId: String) {
        val current = getRoutes().filter { it.id != routeId }
        context.dataStore.edit { it[ROUTES_KEY] = gson.toJson(current) }
    }

    val trackingStateFlow: Flow<Boolean> = context.dataStore.data.map { prefs ->
        prefs[TRACKING_KEY] ?: false
    }

    suspend fun saveTrackingState(active: Boolean) {
        context.dataStore.edit { it[TRACKING_KEY] = active }
    }

    val activeRouteIdFlow: Flow<String?> = context.dataStore.data.map { prefs ->
        prefs[ACTIVE_ROUTE_KEY]
    }

    suspend fun saveActiveRouteId(routeId: String?) {
        context.dataStore.edit { prefs ->
            if (routeId != null) prefs[ACTIVE_ROUTE_KEY] = routeId
            else prefs.remove(ACTIVE_ROUTE_KEY)
        }
    }

    suspend fun loadCooldowns(): Map<String, Map<String, Long>> {
        val json = context.dataStore.data.first()[COOLDOWNS_KEY] ?: return emptyMap()
        val type = object : TypeToken<Map<String, Map<String, Long>>>() {}.type
        return gson.fromJson(json, type) ?: emptyMap()
    }

    suspend fun saveCooldowns(cooldowns: Map<String, Map<String, Long>>) {
        context.dataStore.edit { it[COOLDOWNS_KEY] = gson.toJson(cooldowns) }
    }

    val audioDuckingFlow: Flow<Boolean> = context.dataStore.data.map { prefs ->
        prefs[AUDIO_DUCKING_KEY] ?: true // Ducking by default
    }

    suspend fun saveAudioDucking(enabled: Boolean) {
        context.dataStore.edit { it[AUDIO_DUCKING_KEY] = enabled }
    }

    val preferredDeviceIdFlow: Flow<Int?> = context.dataStore.data.map { prefs ->
        prefs[PREFERRED_DEVICE_ID_KEY]?.toIntOrNull()
    }

    suspend fun savePreferredDeviceId(deviceId: Int?) {
        context.dataStore.edit { prefs ->
            if (deviceId != null) prefs[PREFERRED_DEVICE_ID_KEY] = deviceId.toString()
            else prefs.remove(PREFERRED_DEVICE_ID_KEY)
        }
    }

    val customStationsFlow: Flow<List<Station>> = context.dataStore.data.map { prefs ->
        val json = prefs[CUSTOM_STATIONS_KEY] ?: return@map emptyList()
        val type = object : TypeToken<List<Station>>() {}.type
        gson.fromJson<List<Station>>(json, type) ?: emptyList()
    }

    suspend fun saveCustomStation(station: Station) {
        val current = customStationsFlow.first().toMutableList()
        val idx = current.indexOfFirst { it.id == station.id }
        if (idx >= 0) current[idx] = station else current.add(station)
        context.dataStore.edit { it[CUSTOM_STATIONS_KEY] = gson.toJson(current) }
    }

    suspend fun deleteCustomStation(stationId: String) {
        val current = customStationsFlow.first().filter { it.id != stationId }
        context.dataStore.edit { it[CUSTOM_STATIONS_KEY] = gson.toJson(current) }
    }

    val themeModeFlow: Flow<String> = context.dataStore.data.map { prefs ->
        prefs[THEME_MODE_KEY] ?: "SYSTEM"
    }

    suspend fun saveThemeMode(mode: String) {
        context.dataStore.edit { it[THEME_MODE_KEY] = mode }
    }

    val tariffsFlow: Flow<Map<TicketType, Int>> = context.dataStore.data.map { prefs ->
        val json = prefs[TARIFFS_KEY]
        if (json == null) {
            TicketType.entries.associate { it to it.defaultPriceCents }
        } else {
            val type = object : com.google.gson.reflect.TypeToken<Map<String, Int>>() {}.type
            val raw: Map<String, Int> = gson.fromJson(json, type) ?: emptyMap()
            TicketType.entries.associate { t -> t to (raw[t.name] ?: t.defaultPriceCents) }
        }
    }

    suspend fun saveTariffs(tariffs: Map<TicketType, Int>) {
        val raw = tariffs.entries.associate { (k, v) -> k.name to v }
        context.dataStore.edit { it[TARIFFS_KEY] = gson.toJson(raw) }
    }

    suspend fun exportRoutesToJson(): String {
        return gson.toJson(routesFlow.first())
    }

    suspend fun importRoutesFromJson(json: String): Result<Int> {
        return try {
            val type = object : TypeToken<List<Route>>() {}.type
            val imported: List<Route> = gson.fromJson(json, type) ?: return Result.failure(Exception("Données invalides"))
            if (imported.isEmpty()) return Result.success(0)

            val current = routesFlow.first().toMutableList()
            var addedOrUpdated = 0
            imported.forEach { route ->
                val idx = current.indexOfFirst { it.id == route.id }
                if (idx >= 0) {
                    current[idx] = route
                } else {
                    current.add(route)
                }
                addedOrUpdated++
            }
            saveRoutes(current)
            Result.success(addedOrUpdated)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    private class SupabaseLigne(
        val id: String,
        val nom: String,
        val stations: List<Station>,
        val actif: Boolean
    )

    suspend fun fetchRoutesFromSupabase(): Result<List<Route>> = withContext(Dispatchers.IO) {
        val urlString = "https://pnqdwreqxdwcyggdioba.supabase.co/rest/v1/lignes?select=*"
        try {
            val url = URL(urlString)
            val connection = url.openConnection() as HttpURLConnection
            connection.requestMethod = "GET"
            connection.connectTimeout = 5000
            connection.readTimeout = 5000
            connection.setRequestProperty("apikey", "sb_publishable_whHuPPByZUTGq3qsMsEpFw_UdV-fAZR")
            connection.setRequestProperty("Authorization", "Bearer sb_publishable_whHuPPByZUTGq3qsMsEpFw_UdV-fAZR")
            connection.setRequestProperty("User-Agent", "GareTER/1.0 (Android)")

            if (connection.responseCode == 200) {
                val json = connection.inputStream.bufferedReader().use { it.readText() }
                val type = object : TypeToken<List<SupabaseLigne>>() {}.type
                val rawLignes: List<SupabaseLigne> = gson.fromJson(json, type) ?: emptyList()
                
                val routes = rawLignes.map { l ->
                    Route(
                        id = l.id,
                        title = l.nom,
                        stations = l.stations,
                        enabled = l.actif,
                        createdAt = System.currentTimeMillis()
                    )
                }
                saveRoutes(routes)
                Result.success(routes)
            } else {
                Result.failure(Exception("HTTP Error: ${connection.responseCode}"))
            }
        } catch (e: Exception) {
            e.printStackTrace()
            Result.failure(e)
        }
    }
}
