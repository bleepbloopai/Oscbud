package com.example.ui.face

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin

@Composable
fun OscilloscopeFace(
    emotion: Emotion,
    audioLevel: Float, // 0f to 1f
    frequencyBins: FloatArray, // 16 dynamic bins
    isSpeaking: Boolean,
    isListening: Boolean,
    crtTheme: CrtTheme = CrtTheme.PHOSPHOR_GREEN,
    modifier: Modifier = Modifier
) {
    val infiniteTransition = rememberInfiniteTransition(label = "scope_anim")

    // Phase progression for real-time wave drift
    val phase by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = (2 * PI).toFloat(),
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 2400, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "phase"
    )

    // Orbiting angle for THINKING emotion
    val orbitAngle by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = 360f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 1800, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "orbit"
    )

    // Trace micro-jitter for organic CRT beam feel
    val jitter by infiniteTransition.animateFloat(
        initialValue = -0.5f,
        targetValue = 0.5f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 80, easing = LinearEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "jitter"
    )

    val primaryColor = Color(crtTheme.hexColor)
    val glowColor = Color(crtTheme.glowHex)
    val bgColor = Color(crtTheme.bgHex)
    val gridColor = Color(crtTheme.gridHex)

    Box(
        modifier = modifier
            .clip(RoundedCornerShape(16.dp))
            .background(
                Brush.radialGradient(
                    colors = listOf(
                        bgColor.copy(alpha = 0.95f),
                        Color(0xFF020503)
                    )
                )
            )
            .border(2.dp, primaryColor.copy(alpha = 0.35f), RoundedCornerShape(16.dp))
            .testTag("oscilloscope_face_container")
    ) {
        Canvas(
            modifier = Modifier
                .fillMaxSize()
                .testTag("oscilloscope_canvas")
        ) {
            val width = size.width
            val height = size.height

            // 1. Draw CRT graticule grid
            drawGraticule(width, height, gridColor, primaryColor)

            // 2. Draw Eyes (2 circle outlines that adapt to emotion)
            val eyeY = height * 0.36f
            val leftEyeX = width * 0.33f
            val rightEyeX = width * 0.67f
            val baseRadius = width * 0.088f

            drawEye(
                centerX = leftEyeX,
                centerY = eyeY,
                baseRadius = baseRadius,
                emotion = emotion,
                isLeftEye = true,
                orbitAngle = orbitAngle,
                jitter = jitter,
                primaryColor = primaryColor,
                glowColor = glowColor
            )

            drawEye(
                centerX = rightEyeX,
                centerY = eyeY,
                baseRadius = baseRadius,
                emotion = emotion,
                isLeftEye = false,
                orbitAngle = orbitAngle,
                jitter = jitter,
                primaryColor = primaryColor,
                glowColor = glowColor
            )

            // 3. Draw Mouth (Single line waveform responding dynamically to audio frequencies)
            drawWaveformMouth(
                width = width,
                height = height,
                mouthY = height * 0.72f,
                phase = phase,
                audioLevel = audioLevel,
                frequencyBins = frequencyBins,
                isSpeaking = isSpeaking,
                isListening = isListening,
                jitter = jitter,
                primaryColor = primaryColor,
                glowColor = glowColor
            )
        }

        // Oscilloscope Telemetry Overlay HUD
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 12.dp, vertical = 8.dp)
        ) {
            // Top HUD Bar
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "CH1: 500mV/DIV",
                    color = primaryColor.copy(alpha = 0.8f),
                    fontSize = 10.sp,
                    fontFamily = FontFamily.Monospace,
                    fontWeight = FontWeight.Bold
                )
                Spacer(modifier = Modifier.weight(1f))
                Text(
                    text = "STATUS: ${emotion.badge}",
                    color = primaryColor,
                    fontSize = 10.sp,
                    fontFamily = FontFamily.Monospace,
                    fontWeight = FontWeight.Bold
                )
                Spacer(modifier = Modifier.weight(1f))
                Text(
                    text = if (isSpeaking) "TTS: SYNTH" else if (isListening) "MIC: REC" else "TRIG: AUTO",
                    color = if (isSpeaking || isListening) Color(0xFFFF5252) else primaryColor.copy(alpha = 0.8f),
                    fontSize = 10.sp,
                    fontFamily = FontFamily.Monospace,
                    fontWeight = FontWeight.Bold
                )
            }

            Spacer(modifier = Modifier.weight(1f))

            // Bottom HUD Bar
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "XY-TRACE: ON",
                    color = primaryColor.copy(alpha = 0.6f),
                    fontSize = 9.sp,
                    fontFamily = FontFamily.Monospace
                )
                Spacer(modifier = Modifier.weight(1f))
                Text(
                    text = "EMOTION: ${emotion.name}",
                    color = primaryColor.copy(alpha = 0.85f),
                    fontSize = 9.sp,
                    fontFamily = FontFamily.Monospace,
                    fontWeight = FontWeight.Medium
                )
                Spacer(modifier = Modifier.weight(1f))
                Text(
                    text = "AES-256 SECURED",
                    color = primaryColor.copy(alpha = 0.6f),
                    fontSize = 9.sp,
                    fontFamily = FontFamily.Monospace
                )
            }
        }
    }
}

private fun DrawScope.drawGraticule(
    width: Float,
    height: Float,
    gridColor: Color,
    primaryColor: Color
) {
    val hDivisions = 8
    val vDivisions = 10

    val dashEffect = PathEffect.dashPathEffect(floatArrayOf(4f, 8f), 0f)

    // Vertical grid lines
    val colWidth = width / vDivisions
    for (i in 1 until vDivisions) {
        val x = i * colWidth
        drawLine(
            color = gridColor,
            start = Offset(x, 0f),
            end = Offset(x, height),
            strokeWidth = 1f,
            pathEffect = dashEffect
        )
    }

    // Horizontal grid lines
    val rowHeight = height / hDivisions
    for (i in 1 until hDivisions) {
        val y = i * rowHeight
        drawLine(
            color = gridColor,
            start = Offset(0f, y),
            end = Offset(width, y),
            strokeWidth = 1f,
            pathEffect = dashEffect
        )
    }

    // Center crosshair axis tick marks
    val centerX = width / 2f
    val centerY = height / 2f

    // Center vertical axis ticks
    for (i in 0..hDivisions * 5) {
        val y = i * (height / (hDivisions * 5))
        val tickLen = if (i % 5 == 0) 6f else 3f
        drawLine(
            color = primaryColor.copy(alpha = 0.35f),
            start = Offset(centerX - tickLen, y),
            end = Offset(centerX + tickLen, y),
            strokeWidth = 1f
        )
    }

    // Center horizontal axis ticks
    for (i in 0..vDivisions * 5) {
        val x = i * (width / (vDivisions * 5))
        val tickLen = if (i % 5 == 0) 6f else 3f
        drawLine(
            color = primaryColor.copy(alpha = 0.35f),
            start = Offset(x, centerY - tickLen),
            end = Offset(x, centerY + tickLen),
            strokeWidth = 1f
        )
    }
}

private fun DrawScope.drawEye(
    centerX: Float,
    centerY: Float,
    baseRadius: Float,
    emotion: Emotion,
    isLeftEye: Boolean,
    orbitAngle: Float,
    jitter: Float,
    primaryColor: Color,
    glowColor: Color
) {
    val cx = centerX + jitter
    val cy = centerY + jitter

    when (emotion) {
        Emotion.NEUTRAL -> {
            // Crisp glowing circular outline with subtle phosphor trace
            drawVectorCircle(cx, cy, baseRadius, primaryColor, glowColor, strokeWidth = 3f)
        }

        Emotion.HAPPY -> {
            // Joyful curved crescent / arch outline
            val path = Path().apply {
                val r = baseRadius * 1.05f
                val ovalRect = Rect(cx - r, cy - r * 0.8f, cx + r, cy + r * 1.1f)
                arcTo(ovalRect, 180f, 180f, false)
            }
            // Draw glow layer
            drawPath(
                path = path,
                color = glowColor,
                style = Stroke(width = 8f, cap = StrokeCap.Round)
            )
            // Draw core beam
            drawPath(
                path = path,
                color = primaryColor,
                style = Stroke(width = 3.5f, cap = StrokeCap.Round)
            )
        }

        Emotion.THINKING -> {
            // Circle outline with an orbiting vector radar pulse
            drawVectorCircle(cx, cy, baseRadius, primaryColor.copy(alpha = 0.7f), glowColor, strokeWidth = 2.5f)

            // Orbiting beam pip on the circular perimeter
            val rad = Math.toRadians((orbitAngle + if (isLeftEye) 0f else 180f).toDouble())
            val dotX = cx + (baseRadius * cos(rad)).toFloat()
            val dotY = cy + (baseRadius * sin(rad)).toFloat()

            drawCircle(color = glowColor, radius = 7f, center = Offset(dotX, dotY))
            drawCircle(color = Color.White, radius = 3f, center = Offset(dotX, dotY))
        }

        Emotion.CURIOUS -> {
            // Dilated wide circle outline with secondary outer ring (dual halo)
            drawVectorCircle(cx, cy, baseRadius * 1.15f, primaryColor, glowColor, strokeWidth = 3f)
            drawVectorCircle(cx, cy, baseRadius * 1.35f, primaryColor.copy(alpha = 0.35f), glowColor.copy(alpha = 0.2f), strokeWidth = 1.5f)
        }

        Emotion.EMPATHETIC -> {
            // Gentle oval circle outline with warm trace
            val path = Path().apply {
                addOval(Rect(cx - baseRadius * 1.1f, cy - baseRadius * 0.95f, cx + baseRadius * 1.1f, cy + baseRadius * 0.95f))
            }
            drawPath(path, glowColor, style = Stroke(width = 7f))
            drawPath(path, primaryColor, style = Stroke(width = 2.8f))
        }

        Emotion.FOCUSED -> {
            // Condensed sharp circle outline with center focal point
            drawVectorCircle(cx, cy, baseRadius * 0.85f, primaryColor, glowColor, strokeWidth = 3.5f)
            drawCircle(color = primaryColor, radius = 2.5f, center = Offset(cx, cy))
        }

        Emotion.WINK -> {
            if (isLeftEye) {
                // Left eye open
                drawVectorCircle(cx, cy, baseRadius, primaryColor, glowColor, strokeWidth = 3f)
            } else {
                // Right eye playful wink dash
                val path = Path().apply {
                    moveTo(cx - baseRadius, cy)
                    quadraticTo(cx, cy - baseRadius * 0.5f, cx + baseRadius, cy)
                }
                drawPath(path, glowColor, style = Stroke(width = 8f, cap = StrokeCap.Round))
                drawPath(path, primaryColor, style = Stroke(width = 3.5f, cap = StrokeCap.Round))
            }
        }
    }
}

private fun DrawScope.drawVectorCircle(
    cx: Float,
    cy: Float,
    radius: Float,
    coreColor: Color,
    glowColor: Color,
    strokeWidth: Float
) {
    // Outer phosphor glow
    drawCircle(
        color = glowColor,
        radius = radius,
        center = Offset(cx, cy),
        style = Stroke(width = strokeWidth * 2.8f)
    )
    // Intense core beam
    drawCircle(
        color = coreColor,
        radius = radius,
        center = Offset(cx, cy),
        style = Stroke(width = strokeWidth)
    )
}

private fun DrawScope.drawWaveformMouth(
    width: Float,
    height: Float,
    mouthY: Float,
    phase: Float,
    audioLevel: Float,
    frequencyBins: FloatArray,
    isSpeaking: Boolean,
    isListening: Boolean,
    jitter: Float,
    primaryColor: Color,
    glowColor: Color
) {
    val startX = width * 0.16f
    val endX = width * 0.84f
    val span = endX - startX
    val steps = 80

    val path = Path()
    val activeAmp = if (isSpeaking || isListening) {
        (audioLevel * height * 0.14f).coerceAtLeast(height * 0.035f)
    } else {
        height * 0.012f // gentle idle baseline trace
    }

    for (i in 0..steps) {
        val t = i.toFloat() / steps // 0f to 1f
        val x = startX + t * span

        // Window envelope: smooth taper at the ends so mouth lines taper neatly
        val window = sin(t * PI).toFloat()

        // Harmonic modulation derived from real-time frequency bins
        var harmonicDisplacement = 0f
        if (isSpeaking || isListening) {
            val binCount = frequencyBins.size
            for (b in 0 until binCount) {
                val binWeight = frequencyBins.getOrElse(b) { 0f }
                if (binWeight > 0.01f) {
                    val k = (b + 1) * 1.8f
                    val harmPhase = phase * (1f + b * 0.4f)
                    harmonicDisplacement += (binWeight * sin((t * k * 2 * PI + harmPhase).toDouble()).toFloat()) * 0.45f
                }
            }
        }

        // Primary carrier wave + harmonics
        val carrier = sin((t * 4 * PI + phase * 2f).toDouble()).toFloat()
        val yOffset = (carrier * 0.4f + harmonicDisplacement) * activeAmp * window + jitter

        val y = mouthY + yOffset

        if (i == 0) {
            path.moveTo(x, y)
        } else {
            path.lineTo(x, y)
        }
    }

    // 1. Draw phosphor glow path (wide diffuse beam)
    val glowWidth = if (isSpeaking) 9f else if (isListening) 7f else 5f
    drawPath(
        path = path,
        color = glowColor,
        style = Stroke(
            width = glowWidth,
            cap = StrokeCap.Round,
            join = StrokeJoin.Round
        )
    )

    // 2. Draw intense electron core path
    val coreWidth = if (isSpeaking) 3.5f else 2.5f
    drawPath(
        path = path,
        color = if (isSpeaking) Color.White.copy(alpha = 0.95f) else primaryColor,
        style = Stroke(
            width = coreWidth,
            cap = StrokeCap.Round,
            join = StrokeJoin.Round
        )
    )
}
