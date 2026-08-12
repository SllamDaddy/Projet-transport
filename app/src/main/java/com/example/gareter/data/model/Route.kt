package com.example.gareter.data.model

enum class RouteDirection {
    FORWARD,   // Arrêt 1 → Dernier arrêt
    BACKWARD   // Dernier arrêt → Arrêt 1
}

data class Route(
    val id: String,
    val title: String,
    val stations: List<Station> = emptyList(),
    val createdAt: Long = System.currentTimeMillis(),
    val enabled: Boolean = true
)
