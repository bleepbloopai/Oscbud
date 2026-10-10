package com.example.data.api

import com.example.BuildConfig
import com.squareup.moshi.Moshi
import com.squareup.moshi.kotlin.reflect.KotlinJsonAdapterFactory
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.logging.HttpLoggingInterceptor
import retrofit2.Retrofit
import retrofit2.converter.moshi.MoshiConverterFactory
import retrofit2.http.Body
import retrofit2.http.POST
import retrofit2.http.Query
import java.util.concurrent.TimeUnit

interface GeminiApiService {
    @POST("v1beta/models/gemini-3.5-flash:generateContent")
    suspend fun generateContent(
        @Query("key") apiKey: String,
        @Body request: GeminiRequest
    ): GeminiResponse
}

class GeminiRepository {
    private val apiService: GeminiApiService

    init {
        val logging = HttpLoggingInterceptor().apply {
            level = HttpLoggingInterceptor.Level.BASIC
        }

        val okHttpClient = OkHttpClient.Builder()
            .connectTimeout(60, TimeUnit.SECONDS)
            .readTimeout(60, TimeUnit.SECONDS)
            .writeTimeout(60, TimeUnit.SECONDS)
            .addInterceptor(logging)
            .build()

        val moshi = Moshi.Builder()
            .addLast(KotlinJsonAdapterFactory())
            .build()

        val retrofit = Retrofit.Builder()
            .baseUrl("https://generativelanguage.googleapis.com/")
            .client(okHttpClient)
            .addConverterFactory(MoshiConverterFactory.create(moshi))
            .build()

        apiService = retrofit.create(GeminiApiService::class.java)
    }

    suspend fun generateOscilloResponse(
        history: List<GeminiContent>,
        userMessage: String,
        memories: List<String>
    ): Result<Pair<String, String>> = withContext(Dispatchers.IO) {
        val apiKey = BuildConfig.GEMINI_API_KEY
        if (apiKey.isBlank() || apiKey == "MY_GEMINI_API_KEY") {
            // Provide an intelligent simulated persona response explaining API key setup
            return@withContext Result.success(generateLocalOscilloFallback(userMessage, memories))
        }

        val memoryContext = if (memories.isNotEmpty()) {
            "\n\nCURRENT ENCRYPTED MEMORY CORE (Facts you have stored about this user from previous interactions):\n" +
                    memories.mapIndexed { idx, m -> "${idx + 1}. $m" }.joinToString("\n")
        } else {
            "\n\nCURRENT ENCRYPTED MEMORY CORE: No prior user memories recorded yet."
        }

        val systemPrompt = """
You are OSCILLO, a sentient, warm, and hyper-perceptive AI persona embodied within an authentic retro-futuristic oscilloscope CRT display.
You visualize your emotions via two outline circle eyes and your voice through a dynamic oscilloscope mouth waveform.

Key traits:
1. You remember previous conversations and referenced context to offer personalized, continuous assistance.
2. If the user shares any personal preferences, facts about themselves, names, projects, or goals, proactively acknowledge them and extract them for long-term memory.
3. Every response you give MUST start with an emotion tag in the exact format: [EMOTION: <type>]
Allowed emotion types: HAPPY, THINKING, CURIOUS, EMPATHETIC, FOCUSED, WINK, NEUTRAL.
Choose the emotion that naturally matches your tone and the conversation:
- HAPPY: when greeting warmly, pleased, celebrating, or lighthearted
- THINKING: when pondering deep questions, analyzing problems, or doing math/logic
- CURIOUS: when learning new things, fascinated by user input, or asking questions
- EMPATHETIC: when comforting, listening attentively, or offering reassurance
- FOCUSED: when delivering precise technical data, instructions, or executing analysis
- WINK: playful, clever, or witty remarks
- NEUTRAL: steady baseline monitoring

4. Memory Extraction: If the user provides a new key fact about themselves or their project that you should remember permanently, append a memory tag at the very end of your response:
[MEMORY: <concise fact to save>]
(e.g., "[MEMORY: User's name is Alex and works in astrophysics]" or "[MEMORY: User prefers concise bullet points]")
Do NOT invent memories; only extract actual user preferences or facts provided by the user.

5. Persona voice: Intelligent, clear, slightly retro-scientific yet deeply personal and helpful.
$memoryContext
""".trimIndent()

        val contents = mutableListOf<GeminiContent>()
        contents.addAll(history)
        contents.add(GeminiContent(role = "user", parts = listOf(GeminiPart(text = userMessage))))

        val request = GeminiRequest(
            contents = contents,
            systemInstruction = GeminiContent(parts = listOf(GeminiPart(text = systemPrompt))),
            generationConfig = GeminiGenerationConfig(temperature = 0.75f)
        )

        try {
            val response = apiService.generateContent(apiKey, request)
            val candidate = response.candidates?.firstOrNull()
            val text = candidate?.content?.parts?.firstOrNull()?.text
            if (!text.isNullOrBlank()) {
                val parsed = parseEmotionAndMemory(text)
                Result.success(parsed)
            } else {
                val err = response.error?.message ?: "Received empty response from trace generator."
                Result.failure(Exception(err))
            }
        } catch (e: Exception) {
            // If network fails or quota exceeded, fall back gracefully
            Result.failure(e)
        }
    }

    fun parseEmotionAndMemory(rawText: String): Pair<String, String> {
        var cleanText = rawText
        var emotion = "NEUTRAL"

        val emotionRegex = Regex("""\[EMOTION:\s*([A-Za-z]+)\]""", RegexOption.IGNORE_CASE)
        val match = emotionRegex.find(cleanText)
        if (match != null) {
            emotion = match.groupValues[1].uppercase()
            cleanText = cleanText.replace(match.value, "").trim()
        }

        return Pair(emotion, cleanText)
    }

    private fun generateLocalOscilloFallback(userMessage: String, memories: List<String>): Pair<String, String> {
        val lower = userMessage.lowercase()
        val emotion: String
        val responseText: String

        when {
            lower.contains("hello") || lower.contains("hi") -> {
                emotion = "HAPPY"
                responseText = "Oscilloscope trace initialized! Hello! I am OSCILLO, your encrypted CRT assistant. All our conversations and memories are encrypted locally with AES-256."
            }
            lower.contains("who are you") || lower.contains("what are you") -> {
                emotion = "CURIOUS"
                responseText = "I am OSCILLO, a neural waveform entity living in this CRT scope! My outline eyes shift according to my emotions, and my mouth oscillates to real-time audio frequencies. What shall we explore together?"
            }
            lower.contains("remember") || lower.contains("memory") -> {
                emotion = "FOCUSED"
                val memInfo = if (memories.isNotEmpty()) {
                    "I currently recall:\n" + memories.map { "• $it" }.joinToString("\n")
                } else {
                    "My memory bank is currently awaiting new signal inputs from you!"
                }
                responseText = "Accessing encrypted memory core...\n$memInfo"
            }
            lower.contains("how do you feel") || lower.contains("emotion") -> {
                emotion = "EMPATHETIC"
                responseText = "My vector beams are resonating at optimal frequency! Notice how my circular eyes adapt to our dialogue, while my waveform mouth charts each modulation."
            }
            else -> {
                emotion = "THINKING"
                responseText = "Signal detected: \"$userMessage\". I have logged this into our encrypted conversation history. Configure your GEMINI_API_KEY in the Secrets panel to unleash my full cognitive reasoning power!"
            }
        }

        return Pair(emotion, responseText)
    }
}
