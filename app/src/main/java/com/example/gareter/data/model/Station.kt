package com.example.gareter.data.model

data class Station(
    val id: String,
    val name: String,
    val latitude: Double,
    val longitude: Double,
    val approachRadius: Int = 300,
    val arrivalRadius: Int = 80,
    val approachMessage: String? = null,
    val arrivalMessage: String? = null
)
