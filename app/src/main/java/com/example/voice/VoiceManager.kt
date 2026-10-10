package com.example.voice

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.speech.RecognitionListener
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import android.speech.tts.TextToSpeech
import android.speech.tts.UtteranceProgressListener
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import java.util.Locale
import kotlin.math.cos
import kotlin.math.sin
import kotlin.random.Random

class VoiceManager(
    private val context: Context,
    private val scope: CoroutineScope
) : TextToSpeech.OnInitListener {

    private var speechRecognizer: SpeechRecognizer? = null
    private var textToSpeech: TextToSpeech? = null
    private var isTtsReady = false

    private val _isListening = MutableStateFlow(false)
    val isListening: StateFlow<Boolean> = _isListening.asStateFlow()

    private val _isSpeaking = MutableStateFlow(false)
    val isSpeaking: StateFlow<Boolean> = _isSpeaking.asStateFlow()

    private val _audioLevel = MutableStateFlow(0f) // 0f to 1f normalized
    val audioLevel: StateFlow<Float> = _audioLevel.asStateFlow()

    // 16 dynamic frequency bins for the oscilloscope mouth waveform (0f to 1f)
    private val _frequencyBins = MutableStateFlow(FloatArray(16) { 0f })
    val frequencyBins: StateFlow<FloatArray> = _frequencyBins.asStateFlow()

    private var speechSimulationJob: Job? = null
    var onSpeechRecognized: ((String) -> Unit)? = null
    var onSpeechError: ((String) -> Unit)? = null

    init {
        try {
            textToSpeech = TextToSpeech(context, this)
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    override fun onInit(status: Int) {
        if (status == TextToSpeech.SUCCESS) {
            textToSpeech?.language = Locale.getDefault()
            textToSpeech?.setPitch(0.95f) // slightly modulated retro robotic/resonant tone
            textToSpeech?.setSpeechRate(1.05f)
            textToSpeech?.setOnUtteranceProgressListener(object : UtteranceProgressListener() {
                override fun onStart(utteranceId: String?) {
                    _isSpeaking.value = true
                    startSpeakingWaveformModulation()
                }

                override fun onDone(utteranceId: String?) {
                    _isSpeaking.value = false
                    stopSpeakingWaveformModulation()
                }

                @Deprecated("Deprecated in Java")
                override fun onError(utteranceId: String?) {
                    _isSpeaking.value = false
                    stopSpeakingWaveformModulation()
                }

                override fun onError(utteranceId: String?, errorCode: Int) {
                    _isSpeaking.value = false
                    stopSpeakingWaveformModulation()
                }
            })
            isTtsReady = true
        }
    }

    fun startListening() {
        if (_isSpeaking.value) {
            stopSpeaking()
        }

        if (!SpeechRecognizer.isRecognitionAvailable(context)) {
            onSpeechError?.invoke("Speech recognition is not available on this device.")
            return
        }

        if (speechRecognizer == null) {
            speechRecognizer = SpeechRecognizer.createSpeechRecognizer(context).apply {
                setRecognitionListener(object : RecognitionListener {
                    override fun onReadyForSpeech(params: Bundle?) {
                        _isListening.value = true
                    }

                    override fun onBeginningOfSpeech() {
                        _isListening.value = true
                    }

                    override fun onRmsChanged(rmsdB: Float) {
                        // rmsdB typically ranges from -2 to 10 dB
                        val normalized = ((rmsdB + 2f) / 12f).coerceIn(0f, 1f)
                        _audioLevel.value = normalized

                        // Modulate frequency bins based on mic input
                        val bins = FloatArray(16)
                        for (i in 0 until 16) {
                            val factor = sin((i * 0.4f + System.currentTimeMillis() * 0.01f).toDouble()).toFloat()
                            bins[i] = (normalized * (0.5f + 0.5f * kotlin.math.abs(factor))).coerceIn(0f, 1f)
                        }
                        _frequencyBins.value = bins
                    }

                    override fun onBufferReceived(buffer: ByteArray?) {}

                    override fun onEndOfSpeech() {
                        _isListening.value = false
                        _audioLevel.value = 0f
                    }

                    override fun onError(error: Int) {
                        _isListening.value = false
                        _audioLevel.value = 0f
                        _frequencyBins.value = FloatArray(16) { 0f }
                        val message = when (error) {
                            SpeechRecognizer.ERROR_NO_MATCH -> "No speech recognized."
                            SpeechRecognizer.ERROR_SPEECH_TIMEOUT -> "Speech input timed out."
                            SpeechRecognizer.ERROR_AUDIO -> "Audio recording error."
                            SpeechRecognizer.ERROR_NETWORK -> "Network error during recognition."
                            else -> "Voice recognition code: $error"
                        }
                        onSpeechError?.invoke(message)
                    }

                    override fun onResults(results: Bundle?) {
                        _isListening.value = false
                        _audioLevel.value = 0f
                        _frequencyBins.value = FloatArray(16) { 0f }
                        val matches = results?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
                        if (!matches.isNullOrEmpty()) {
                            val query = matches[0]
                            onSpeechRecognized?.invoke(query)
                        }
                    }

                    override fun onPartialResults(partialResults: Bundle?) {}

                    override fun onEvent(eventType: Int, params: Bundle?) {}
                })
            }
        }

        val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
            putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
            putExtra(RecognizerIntent.EXTRA_LANGUAGE, Locale.getDefault())
            putExtra(RecognizerIntent.EXTRA_MAX_RESULTS, 1)
        }

        try {
            speechRecognizer?.startListening(intent)
            _isListening.value = true
        } catch (e: Exception) {
            _isListening.value = false
            onSpeechError?.invoke(e.message ?: "Failed to start voice recognition.")
        }
    }

    fun stopListening() {
        try {
            speechRecognizer?.stopListening()
        } catch (_: Exception) {}
        _isListening.value = false
        _audioLevel.value = 0f
        _frequencyBins.value = FloatArray(16) { 0f }
    }

    fun speak(text: String, utteranceId: String = "oscillo_${System.currentTimeMillis()}") {
        if (!isTtsReady || textToSpeech == null) return
        stopListening()
        textToSpeech?.stop()

        // Clean out emotion and memory tags before speaking
        val cleanSpeech = text
            .replace(Regex("""\[EMOTION:\s*[A-Za-z]+\]""", RegexOption.IGNORE_CASE), "")
            .replace(Regex("""\[MEMORY:[^\]]+\]""", RegexOption.IGNORE_CASE), "")
            .replace(Regex("""[*#_`>~]"""), "")
            .trim()

        if (cleanSpeech.isNotEmpty()) {
            val params = Bundle()
            textToSpeech?.speak(cleanSpeech, TextToSpeech.QUEUE_FLUSH, params, utteranceId)
        }
    }

    fun stopSpeaking() {
        textToSpeech?.stop()
        _isSpeaking.value = false
        stopSpeakingWaveformModulation()
    }

    private fun startSpeakingWaveformModulation() {
        speechSimulationJob?.cancel()
        speechSimulationJob = scope.launch(Dispatchers.Default) {
            var phase = 0.0
            while (isActive && _isSpeaking.value) {
                phase += 0.25
                // Synthesize formant audio frequency spectrum
                val bins = FloatArray(16)
                // Simulated speech envelope with vowel/consonant modulation
                val syllablePulse = ((sin(phase * 0.7) + 1.0) / 2.0).toFloat()
                val flutter = Random.nextFloat() * 0.25f

                val baseAmp = (syllablePulse * 0.75f + flutter).coerceIn(0.15f, 1.0f)
                _audioLevel.value = baseAmp

                for (i in 0 until 16) {
                    val formant1 = sin(phase * 1.2 + i * 0.5).toFloat()
                    val formant2 = cos(phase * 2.3 + i * 0.9).toFloat()
                    val formant3 = sin(phase * 3.7 + i * 1.4).toFloat()
                    val harmonic = (formant1 * 0.5f + formant2 * 0.3f + formant3 * 0.2f + 1f) / 2f
                    bins[i] = (harmonic * baseAmp).coerceIn(0.05f, 1f)
                }
                _frequencyBins.value = bins
                delay(30)
            }
            _frequencyBins.value = FloatArray(16) { 0f }
            _audioLevel.value = 0f
        }
    }

    private fun stopSpeakingWaveformModulation() {
        speechSimulationJob?.cancel()
        speechSimulationJob = null
        _audioLevel.value = 0f
        _frequencyBins.value = FloatArray(16) { 0f }
    }

    fun destroy() {
        speechSimulationJob?.cancel()
        try {
            speechRecognizer?.destroy()
        } catch (_: Exception) {}
        try {
            textToSpeech?.stop()
            textToSpeech?.shutdown()
        } catch (_: Exception) {}
    }
}
