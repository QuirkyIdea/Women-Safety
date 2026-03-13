package com.example.suraksha

import android.app.Application
import androidx.room.Room
import com.example.suraksha.data.SurakshaDatabase

class SurakshaApplication : Application() {
    
    companion object {
        lateinit var database: SurakshaDatabase
            private set
    }
    
    override fun onCreate() {
        super.onCreate()
        
        // Initialize Room database with migration support
        database = Room.databaseBuilder(
            applicationContext,
            SurakshaDatabase::class.java,
            "suraksha_database"
        )
            .addMigrations(SurakshaDatabase.MIGRATION_1_2)
            .fallbackToDestructiveMigration()
            .build()
    }
}
