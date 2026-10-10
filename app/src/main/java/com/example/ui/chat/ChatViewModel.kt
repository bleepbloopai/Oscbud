package com.example.ui.chat

import android.app.Application
import android.content.Context
import android.content.Intent
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.data.api.GeminiContent
import com.example.data.api.GeminiPart
import com.example.data.api.GeminiRepository
import com.example.data.crypto.CryptoManager
import com.example.data.db.MemoryEntity
import com.example.data.db.MessageEntity
import com.example.data.db.OscilloDatabase
import com.example.ui.face.CrtTheme
import com.example.ui.face.Emotion
import com.example.voice.VoiceManager
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

data class ChatMessageUi(
    val id: Long,
    val role: String,
    val text: String,
    val emotion: Emotion,
    val timestamp: Long
)

class ChatViewModel(application: Application) : AndroidViewModel(application) {

    private val db = OscilloDatabase.getDatabase(application)
    private val dao = db.oscilloDao()
    private val geminiRepo = GeminiRepository()

    val voiceManager = VoiceManager(application, viewModelScope)

    val messages: StateFlow<List<ChatMessageUi>> = dao.getAllMessages()
        .map { list ->
            list.map { entity ->
                ChatMessageUi(
                    id = entity.id,
                    role = entity.role,
                    text = CryptoManager.decrypt(entity.encryptedText),
                    emotion = Emotion.fromString(entity.emotion),
                    timestamp = entity.timestamp
                )
            }
        }.stateIn(viewModelScope, SharingStarted.Lazily, emptyList())

    val memories: StateFlow<List<MemoryEntity>> = dao.getAllMemories()
        .map { list ->
            list.map { entity ->
                entity.copy(encryptedFact = CryptoManager.decrypt(entity.encryptedFact))
            }
        }.stateIn(viewModelScope, SharingStarted.Lazily, emptyList())

    private val _currentEmotion = MutableStateFlow(Emotion.NEUTRAL)
    val currentEmotion: StateFlow<Emotion> = _currentEmotion.asStateFlow()

    private val _crtTheme = MutableStateFlow(CrtTheme.PHOSPHOR_GREEN)
    val crtTheme: StateFlow<CrtTheme> = _crtTheme.asStateFlow()

    private val _autoSpeak = MutableStateFlow(true)
    val autoSpeak: StateFlow<Boolean> = _autoSpeak.asStateFlow()

    private val _isLoading = MutableStateFlow(false)
    val isLoading: StateFlow<Boolean> = _isLoading.asStateFlow()

    private val _statusNotice = MutableStateFlow<String?>("AES-256 Storage Synchronized")
    val statusNotice: StateFlow<String?> = _statusNotice.asStateFlow()

    init {
        voiceManager.onSpeechRecognized = { text ->
            sendMessage(text)
        }
        voiceManager.onSpeechError = { error ->
            _statusNotice.value = error
        }

        // Initialize welcome message if db is empty
        viewModelScope.launch {
            val count = dao.getRecentMessages(1).size
            if (count == 0) {
                val welcome = "Oscilloscope neural matrix online. I am OSCILLO! My visual traces adapt to our dialogue, and all conversation history & personalized memories are encrypted at rest with AES-256."
                dao.insertMessage(
                    MessageEntity(
                        role = "model",
                        encryptedText = CryptoManager.encrypt(welcome),
                        emotion = "HAPPY",
                        timestamp = System.currentTimeMillis()
                    )
                )
                // Add initial memory note
                dao.insertMemory(
                    MemoryEntity(
                        encryptedFact = CryptoManager.encrypt("Assistant initialized in encrypted local sandbox."),
                        category = "System",
                        timestamp = System.currentTimeMillis()
                    )
                )
            }
        }
    }

    fun sendMessage(userText: String) {
        val trimmed = userText.trim()
        if (trimmed.isEmpty()) return

        viewModelScope.launch {
            _isLoading.value = true
            _currentEmotion.value = Emotion.THINKING

            // 1. Save user message encrypted
            val userMsg = MessageEntity(
                role = "user",
                encryptedText = CryptoManager.encrypt(trimmed),
                emotion = "NEUTRAL",
                timestamp = System.currentTimeMillis()
            )
            dao.insertMessage(userMsg)

            // 2. Fetch recent conversation history and decrypted memories
            val recentEntities = dao.getRecentMessages(12).reversed()
            val historyContent = recentEntities.map { entity ->
                GeminiContent(
                    role = if (entity.role == "user") "user" else "model",
                    parts = listOf(GeminiPart(text = CryptoManager.decrypt(entity.encryptedText)))
                )
            }

            val currentMemoriesList = dao.getAllMemoriesList().map {
                CryptoManager.decrypt(it.encryptedFact)
            }

            // 3. Request AI persona response
            val result = geminiRepo.generateOscilloResponse(
                history = historyContent,
                userMessage = trimmed,
                memories = currentMemoriesList
            )

            result.onSuccess { (emotionStr, fullReply) ->
                val detectedEmotion = Emotion.fromString(emotionStr)
                _currentEmotion.value = detectedEmotion

                // Check for extracted memory tag: [MEMORY: ...]
                val memoryRegex = Regex("""\[MEMORY:\s*([^\]]+)\]""", RegexOption.IGNORE_CASE)
                val memMatch = memoryRegex.find(fullReply)
                var cleanReply = fullReply
                if (memMatch != null) {
                    val extractedFact = memMatch.groupValues[1].trim()
                    cleanReply = cleanReply.replace(memMatch.value, "").trim()
                    if (extractedFact.isNotEmpty()) {
                        dao.insertMemory(
                            MemoryEntity(
                                encryptedFact = CryptoManager.encrypt(extractedFact),
                                category = "User Fact",
                                timestamp = System.currentTimeMillis()
                            )
                        )
                        _statusNotice.value = "Memory saved: $extractedFact"
                    }
                }

                // 4. Save AI reply encrypted
                dao.insertMessage(
                    MessageEntity(
                        role = "model",
                        encryptedText = CryptoManager.encrypt(cleanReply),
                        emotion = emotionStr,
                        timestamp = System.currentTimeMillis()
                    )
                )

                // 5. Speak via TTS if enabled
                if (_autoSpeak.value) {
                    voiceManager.speak(cleanReply)
                }
            }.onFailure { err ->
                _currentEmotion.value = Emotion.NEUTRAL
                val errorMsg = "Trace disruption: ${err.message ?: "Unable to complete vector query."}"
                _statusNotice.value = errorMsg
                dao.insertMessage(
                    MessageEntity(
                        role = "model",
                        encryptedText = CryptoManager.encrypt(errorMsg),
                        emotion = "NEUTRAL",
                        timestamp = System.currentTimeMillis()
                    )
                )
            }

            _isLoading.value = false
        }
    }

    fun startVoiceInput() {
        voiceManager.startListening()
    }

    fun stopVoiceInput() {
        voiceManager.stopListening()
    }

    fun toggleAutoSpeak() {
        _autoSpeak.value = !_autoSpeak.value
        if (!_autoSpeak.value) {
            voiceManager.stopSpeaking()
        }
    }

    fun speakMessage(text: String) {
        voiceManager.speak(text)
    }

    fun setCrtTheme(theme: CrtTheme) {
        _crtTheme.value = theme
    }

    fun addManualMemory(fact: String, category: String = "User Note") {
        if (fact.isBlank()) return
        viewModelScope.launch {
            dao.insertMemory(
                MemoryEntity(
                    encryptedFact = CryptoManager.encrypt(fact.trim()),
                    category = category,
                    timestamp = System.currentTimeMillis()
                )
            )
            _statusNotice.value = "Memory core updated"
        }
    }

    fun deleteMemory(id: Long) {
        viewModelScope.launch {
            dao.deleteMemory(id)
        }
    }

    fun clearAllMemories() {
        viewModelScope.launch {
            dao.deleteAllMemories()
            _statusNotice.value = "Memory core purged"
        }
    }

    fun clearAllMessages() {
        viewModelScope.launch {
            dao.deleteAllMessages()
            voiceManager.stopSpeaking()
            _statusNotice.value = "Conversation history reset"
        }
    }

    fun dismissNotice() {
        _statusNotice.value = null
    }

    // Export chat logs as Markdown or Encrypted text
    fun exportChatLogs(context: Context, asEncryptedArchive: Boolean) {
        viewModelScope.launch {
            val allMsgs = messages.value
            val allMems = memories.value
            val dateFormat = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault())

            val exportContent = if (asEncryptedArchive) {
                // Build a structured raw JSON string and encrypt it with CryptoManager
                val builder = StringBuilder()
                builder.append("=== OSCILLO ENCRYPTED BACKUP ARCHIVE ===\n")
                builder.append("Export Timestamp: ${dateFormat.format(Date())}\n")
                builder.append("Encryption: AES-256-GCM Hardware-Backed\n\n")

                val payload = StringBuilder()
                payload.append("MEMORIES:\n")
                allMems.forEach { m ->
                    payload.append("- [${m.category}] ${m.encryptedFact}\n")
                }
                payload.append("\nMESSAGES:\n")
                allMsgs.forEach { msg ->
                    payload.append("[${dateFormat.format(Date(msg.timestamp))}] ${msg.role.uppercase()} (${msg.emotion}): ${msg.text}\n")
                }

                val cipherText = CryptoManager.encrypt(payload.toString())
                builder.append(cipherText)
                builder.toString()
            } else {
                // Human-readable formatted log
                val sb = StringBuilder()
                sb.append("# Oscillo AI Chat Log Export\n")
                sb.append("Generated on: ${dateFormat.format(Date())}\n")
                sb.append("Security: AES-256 Encrypted Storage\n\n")

                sb.append("## Stored Memories\n")
                if (allMems.isEmpty()) {
                    sb.append("*(No stored memories)*\n")
                } else {
                    allMems.forEach { mem ->
                        sb.append("- [${mem.category}] ${mem.encryptedFact}\n")
                    }
                }

                sb.append("\n## Conversation Log\n\n")
                allMsgs.forEach { msg ->
                    val sender = if (msg.role == "user") "USER" else "OSCILLO"
                    val time = dateFormat.format(Date(msg.timestamp))
                    sb.append("**[$time] $sender** [Emotion: ${msg.emotion.name}]\n")
                    sb.append("${msg.text}\n\n---\n\n")
                }
                sb.toString()
            }

            // Launch share sheet
            val sendIntent = Intent(Intent.ACTION_SEND).apply {
                action = Intent.ACTION_SEND
                putExtra(Intent.EXTRA_TEXT, exportContent)
                putExtra(Intent.EXTRA_TITLE, "Oscillo_Chat_Export_${System.currentTimeMillis()}.txt")
                type = "text/plain"
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            context.startActivity(Intent.createChooser(sendIntent, "Export Oscillo Chat Log").apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            })
        }
    }

    override fun onCleared() {
        super.onCleared()
        voiceManager.destroy()
    }
}
