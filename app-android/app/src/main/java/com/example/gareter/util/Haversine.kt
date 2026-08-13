package com.example.gareter.util

import com.example.gareter.data.model.Station
import kotlin.math.*

object Haversine {
    private const val R = 6_371_000.0 // mètres

    fun distance(lat1: Double, lon1: Double, lat2: Double, lon2: Double): Double {
        val dLat = Math.toRadians(lat2 - lat1)
        val dLon = Math.toRadians(lon2 - lon1)
        val a = sin(dLat / 2).pow(2) +
                cos(Math.toRadians(lat1)) * cos(Math.toRadians(lat2)) * sin(dLon / 2).pow(2)
        return R * 2 * atan2(sqrt(a), sqrt(1 - a))
    }

    fun nearestStation(stations: List<Station>, lat: Double, lon: Double): Station? =
        stations.minByOrNull { distance(lat, lon, it.latitude, it.longitude) }
}
