package com.example.gareter.data.db

import androidx.room.TypeConverter
import com.example.gareter.data.model.TicketType

class CaisseConverters {
    @TypeConverter
    fun fromTicketType(type: TicketType): String = type.name

    @TypeConverter
    fun toTicketType(name: String): TicketType = TicketType.valueOf(name)
}
