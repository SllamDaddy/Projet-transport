package com.example.gareter.data.db

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.TypeConverters
import com.example.gareter.data.model.CarnetTicket
import com.example.gareter.data.model.ServiceSession
import com.example.gareter.data.model.TicketSale

@Database(
    entities = [TicketSale::class, CarnetTicket::class, ServiceSession::class],
    version = 1,
    exportSchema = false,
)
@TypeConverters(CaisseConverters::class)
abstract class AppDatabase : RoomDatabase() {

    abstract fun caisseDao(): CaisseDao

    companion object {
        @Volatile private var INSTANCE: AppDatabase? = null

        fun getInstance(context: Context): AppDatabase =
            INSTANCE ?: synchronized(this) {
                Room.databaseBuilder(
                    context.applicationContext,
                    AppDatabase::class.java,
                    "girouette_bus.db",
                ).build().also { INSTANCE = it }
            }
    }
}
