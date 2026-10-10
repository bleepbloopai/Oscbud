package com.example.data.db

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase

@Database(entities = [MessageEntity::class, MemoryEntity::class], version = 1, exportSchema = false)
abstract class OscilloDatabase : RoomDatabase() {
    abstract fun oscilloDao(): OscilloDao

    companion object {
        @Volatile
        private var INSTANCE: OscilloDatabase? = null

        fun getDatabase(context: Context): OscilloDatabase {
            return INSTANCE ?: synchronized(this) {
                val instance = Room.databaseBuilder(
                    context.applicationContext,
                    OscilloDatabase::class.java,
                    "oscillo_encrypted_db"
                ).build()
                INSTANCE = instance
                instance
            }
        }
    }
}
