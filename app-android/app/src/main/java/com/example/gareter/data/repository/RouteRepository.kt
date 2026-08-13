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
        private val THEME_MODE_KEY = stringPreferencesKey("theme_mode")
        private val TARIFFS_KEY = stringPreferencesKey("tariffs_json")
        private val DRIVER_AGENT_KEY = stringPreferencesKey("driver_agent")
        private val DRIVER_NAME_KEY = stringPreferencesKey("driver_name")
        private val DRIVER_TOKEN_KEY = stringPreferencesKey("driver_token")
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

    suspend fun saveRoutes(routes: List<Route>) {
        context.dataStore.edit { it[ROUTES_KEY] = gson.toJson(routes) }
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

    private class SupabaseTarif(
        val nom_ticket: String,
        val prix_cents: Int
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

    suspend fun fetchTariffsFromSupabase(): Result<Map<TicketType, Int>> = withContext(Dispatchers.IO) {
        val urlString = "https://pnqdwreqxdwcyggdioba.supabase.co/rest/v1/tarifs?select=*"
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
                val type = object : TypeToken<List<SupabaseTarif>>() {}.type
                val rawTarifs: List<SupabaseTarif> = gson.fromJson(json, type) ?: emptyList()

                val tariffs = mutableMapOf<TicketType, Int>()
                rawTarifs.forEach { tarif ->
                    try {
                        val ticketType = TicketType.valueOf(tarif.nom_ticket)
                        tariffs[ticketType] = tarif.prix_cents
                    } catch (e: Exception) {
                        // Skip invalid ticket types
                    }
                }

                saveTariffs(tariffs)
                Result.success(tariffs)
            } else {
                Result.failure(Exception("HTTP Error: ${connection.responseCode}"))
            }
        } catch (e: Exception) {
            e.printStackTrace()
            Result.failure(e)
        }
    }

    val driverAgentFlow: Flow<String?> = context.dataStore.data.map { prefs ->
        prefs[DRIVER_AGENT_KEY]
    }

    val driverNameFlow: Flow<String?> = context.dataStore.data.map { prefs ->
        prefs[DRIVER_NAME_KEY]
    }

    suspend fun saveDriverSession(agentId: String, name: String, token: String) {
        context.dataStore.edit { prefs ->
            prefs[DRIVER_AGENT_KEY] = agentId
            prefs[DRIVER_NAME_KEY] = name
            prefs[DRIVER_TOKEN_KEY] = token
        }
    }

    suspend fun clearDriverSession() {
        context.dataStore.edit { prefs ->
            prefs.remove(DRIVER_AGENT_KEY)
            prefs.remove(DRIVER_NAME_KEY)
            prefs.remove(DRIVER_TOKEN_KEY)
        }
    }

    private class SupabaseConducteur(
        val id: String,
        val nom: String,
        val email: String,
        val agent_id: String,
        val actif: Boolean
    )

    private class SupabaseAuthResponse(
        val access_token: String,
        val user: SupabaseUser
    )

    private class SupabaseUser(
        val id: String,
        val email: String
    )

    suspend fun loginDriver(agentId: String, motDePasse: String): Result<String> = withContext(Dispatchers.IO) {
        try {
            // 1. Fetch the driver corresponding to the agentId from database
            val getUrl = URL("https://pnqdwreqxdwcyggdioba.supabase.co/rest/v1/conducteurs?agent_id=eq.${agentId}&select=*")
            val connectionGet = getUrl.openConnection() as HttpURLConnection
            connectionGet.requestMethod = "GET"
            connectionGet.connectTimeout = 5000
            connectionGet.readTimeout = 5000
            connectionGet.setRequestProperty("apikey", "sb_publishable_whHuPPByZUTGq3qsMsEpFw_UdV-fAZR")
            connectionGet.setRequestProperty("Authorization", "Bearer sb_publishable_whHuPPByZUTGq3qsMsEpFw_UdV-fAZR")
            connectionGet.setRequestProperty("User-Agent", "GareTER/1.0 (Android)")

            if (connectionGet.responseCode != 200) {
                return@withContext Result.failure(Exception("Erreur de connexion Supabase (code ${connectionGet.responseCode})"))
            }

            val responseGetJson = connectionGet.inputStream.bufferedReader().use { it.readText() }
            val listType = object : TypeToken<List<SupabaseConducteur>>() {}.type
            val conducteurs: List<SupabaseConducteur> = gson.fromJson(responseGetJson, listType) ?: emptyList()

            if (conducteurs.isEmpty()) {
                return@withContext Result.failure(Exception("Identifiant Agent inconnu."))
            }

            val conducteur = conducteurs.first()
            if (!conducteur.actif) {
                return@withContext Result.failure(Exception("Ce compte conducteur est désactivé/bloqué."))
            }

            // 2. Perform authentication with the email and password
            val postUrl = URL("https://pnqdwreqxdwcyggdioba.supabase.co/auth/v1/token?grant_type=password")
            val connectionPost = postUrl.openConnection() as HttpURLConnection
            connectionPost.requestMethod = "POST"
            connectionPost.doOutput = true
            connectionPost.connectTimeout = 5000
            connectionPost.readTimeout = 5000
            connectionPost.setRequestProperty("apikey", "sb_publishable_whHuPPByZUTGq3qsMsEpFw_UdV-fAZR")
            connectionPost.setRequestProperty("Content-Type", "application/json")
            connectionPost.setRequestProperty("User-Agent", "GareTER/1.0 (Android)")

            val requestBody = gson.toJson(mapOf("email" to conducteur.email, "password" to motDePasse))
            connectionPost.outputStream.bufferedWriter().use { it.write(requestBody) }

            if (connectionPost.responseCode == 200) {
                val responsePostJson = connectionPost.inputStream.bufferedReader().use { it.readText() }
                val authResponse = gson.fromJson(responsePostJson, SupabaseAuthResponse::class.java)
                
                saveDriverSession(agentId, conducteur.nom, authResponse.access_token)
                Result.success(conducteur.nom)
            } else {
                val errorStream = connectionPost.errorStream
                val errorMessage = if (errorStream != null) {
                    try {
                        val errorJson = errorStream.bufferedReader().use { it.readText() }
                        val errorMap = gson.fromJson<Map<String, Any>>(errorJson, object : TypeToken<Map<String, Any>>() {}.type)
                        errorMap["error_description"] as? String ?: errorMap["message"] as? String ?: "Mot de passe incorrect."
                    } catch (e: Exception) {
                        "Identifiants invalides."
                    }
                } else {
                    "Identifiants invalides."
                }
                Result.failure(Exception(errorMessage))
            }
        } catch (e: Exception) {
            e.printStackTrace()
            Result.failure(Exception("Erreur de réseau : ${e.localizedMessage}"))
        }
    }
}
