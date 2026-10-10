package com.example.data.db

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "messages")
data class MessageEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val role: String, // "user" or "model"
    val encryptedText: String,
    val emotion: String = "NEUTRAL",
    val timestamp: Long = System.currentTimeMillis()
)
