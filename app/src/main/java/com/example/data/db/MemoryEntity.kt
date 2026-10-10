package com.example.data.db

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "memories")
data class MemoryEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val encryptedFact: String,
    val category: String = "General",
    val timestamp: Long = System.currentTimeMillis()
)
