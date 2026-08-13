package com.example.gareter.data.db

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.TypeConverters
import com.example.gareter.data.model.AbonnementPass
import com.example.gareter.data.model.AbonnementScan
import com.example.gareter.data.model.CarnetTicket
import com.example.gareter.data.model.CarnetUsageEvent
import com.example.gareter.data.model.ServiceSession
import com.example.gareter.data.model.TicketSale

@Database(
    entities = [
        TicketSale::class,
        CarnetTicket::class,
        ServiceSession::class,
        AbonnementPass::class,
        AbonnementScan::class,
        CarnetUsageEvent::class,
    ],
    version = 2,
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
                )
                    // MVP en cours de déploiement : les ventes du jour non encore
                    // synchronisées peuvent être perdues lors d'une montée de version
                    // du schéma. Pas de migration formelle tant que l'app n'est pas
                    // en production à grande échelle.
                    .fallbackToDestructiveMigration(dropAllTables = true)
                    .build().also { INSTANCE = it }
            }
    }
}
