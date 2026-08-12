package com.example.gareter.data.model

import androidx.room.Entity
import androidx.room.PrimaryKey
import java.util.UUID

@Entity(tableName = "service_sessions")
data class ServiceSession(
    @PrimaryKey val id: String = UUID.randomUUID().toString(),
    val lineLabel: String? = null,
    val startedAt: Long = System.currentTimeMillis(),
    val endedAt: Long? = null,
    val isActive: Boolean = true,
)
