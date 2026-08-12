package com.example.gareter.data.repository

import com.example.gareter.data.model.Station
import com.google.gson.Gson
import com.google.gson.annotations.SerializedName
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.osmdroid.util.GeoPoint
import java.net.HttpURLConnection
import java.net.URL

object OSRMService {
    private val gson = Gson()

    private class OSRMResponse(
        @SerializedName("routes") val routes: List<OSRMRoute>?
    )

    private class OSRMRoute(
        @SerializedName("geometry") val geometry: OSRMGeometry?
    )

    private class OSRMGeometry(
        @SerializedName("coordinates") val coordinates: List<List<Double>>?
    )

    suspend fun getRoutePoints(stations: List<Station>): List<GeoPoint> = withContext(Dispatchers.IO) {
        if (stations.size < 2) return@withContext emptyList()

        // Contruit l'URL avec toutes les gares : lon1,lat1;lon2,lat2;...
        val coordsString = stations.joinToString(";") { "${it.longitude},${it.latitude}" }
        val urlString = "https://router.project-osrm.org/route/v1/driving/$coordsString?overview=full&geometries=geojson"

        try {
            val url = URL(urlString)
            val connection = url.openConnection() as HttpURLConnection
            connection.requestMethod = "GET"
            connection.connectTimeout = 5000
            connection.readTimeout = 5000
            connection.setRequestProperty("User-Agent", "GareTER/1.0 (Android)")

            if (connection.responseCode == 200) {
                val json = connection.inputStream.bufferedReader().use { it.readText() }
                val response = gson.fromJson(json, OSRMResponse::class.java)
                val coordinates = response?.routes?.firstOrNull()?.geometry?.coordinates
                if (coordinates != null) {
                    return@withContext coordinates.map { point ->
                        // GeoJSON renvoie [longitude, latitude]
                        GeoPoint(point[1], point[0])
                    }
                }
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
        return@withContext emptyList()
    }
}
