package com.example.ui.face

enum class Emotion(val displayName: String, val badge: String) {
    NEUTRAL("Neutral", "STABLE"),
    HAPPY("Happy", "PEAK +"),
    THINKING("Thinking", "RESOLVING"),
    CURIOUS("Curious", "SCANNING"),
    EMPATHETIC("Empathetic", "RESONANT"),
    FOCUSED("Focused", "TUNED"),
    WINK("Playful", "MODULATED");

    companion object {
        fun fromString(name: String?): Emotion {
            if (name == null) return NEUTRAL
            return try {
                valueOf(name.trim().uppercase())
            } catch (_: Exception) {
                NEUTRAL
            }
        }
    }
}

enum class CrtTheme(val title: String, val hexColor: Long, val glowHex: Long, val bgHex: Long, val gridHex: Long) {
    PHOSPHOR_GREEN("P31 Green", 0xFF00FF66, 0x5500FF66, 0xFF040D08, 0x2200FF66),
    CRT_CYAN("P4 Cyan", 0xFF00F0FF, 0x5500F0FF, 0xFF030D14, 0x2200F0FF),
    VECTOR_AMBER("P20 Amber", 0xFFFFB300, 0x55FFB300, 0xFF140D03, 0x22FFB300)
}
