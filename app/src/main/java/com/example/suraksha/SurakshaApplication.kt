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
        
        // Initialize Room database
        database = Room.databaseBuilder(
            applicationContext,
            SurakshaDatabase::class.java,
            "suraksha_database"
        ).build()
    }
}
