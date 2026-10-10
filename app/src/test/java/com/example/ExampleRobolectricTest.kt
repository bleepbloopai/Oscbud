package com.example

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.example.data.api.GeminiRepository
import com.example.data.crypto.CryptoManager
import com.example.ui.face.Emotion
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class ExampleRobolectricTest {

    @Test
    fun `read string from context`() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val appName = context.getString(R.string.app_name)
        assertEquals("Oscillo AI", appName)
    }

    @Test
    fun `test crypto encryption and decryption roundtrip`() {
        val original = "Neural memory test string: User loves synthesizers."
        val encrypted = CryptoManager.encrypt(original)
        assertNotEquals(original, encrypted)

        val decrypted = CryptoManager.decrypt(encrypted)
        assertEquals(original, decrypted)
    }

    @Test
    fun `test emotion parser from model response`() {
        val repo = GeminiRepository()
        val (emotion, cleanText) = repo.parseEmotionAndMemory("[EMOTION: HAPPY] Signal received and processed!")
        assertEquals("HAPPY", emotion)
        assertEquals("Signal received and processed!", cleanText)
    }

    @Test
    fun `test emotion from string helper`() {
        assertEquals(Emotion.HAPPY, Emotion.fromString("HAPPY"))
        assertEquals(Emotion.THINKING, Emotion.fromString("thinking"))
        assertEquals(Emotion.NEUTRAL, Emotion.fromString("unknown_emotion"))
    }
}
