package com.awlaunch.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.PointerInputChange
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlin.math.abs

private val ALPHABET: List<Char> = listOf('#') + ('A'..'Z').toList()

@Composable
fun FastScrollBar(
    presentLetters: Set<Char>,
    onLetterSelected: (Char) -> Unit,
    modifier: Modifier = Modifier,
    barWidth: Dp = 22.dp
) {
    val haptics = LocalHapticFeedback.current
    var lastLetter by remember { mutableStateOf<Char?>(null) }
    var touchY by remember { mutableStateOf<Float?>(null) }
    var barHeightPx by remember { mutableFloatStateOf(0f) }

    fun handle(y: Float) {
        val h = barHeightPx.takeIf { it > 0f } ?: return
        val frac = (y / h).coerceIn(0f, 0.9999f)
        val idx = (frac * ALPHABET.size).toInt().coerceIn(0, ALPHABET.size - 1)
        val letter = ALPHABET[idx]
        if (letter != lastLetter && letter in presentLetters) {
            lastLetter = letter
            haptics.performHapticFeedback(HapticFeedbackType.TextHandleMove)
            onLetterSelected(letter)
        }
    }

    Box(
        modifier = modifier
            .width(barWidth + 12.dp)
            .fillMaxHeight()
            .onGloballyPositioned { barHeightPx = it.size.height.toFloat() }
            .pointerInput(presentLetters) {
                awaitEachGesture {
                    val down = awaitFirstDown(requireUnconsumed = false)
                    lastLetter = null
                    touchY = down.position.y
                    handle(down.position.y)
                    down.consume()
                    while (true) {
                        val event = awaitPointerEvent()
                        val change: PointerInputChange = event.changes.firstOrNull() ?: break
                        if (!change.pressed) break
                        touchY = change.position.y
                        handle(change.position.y)
                        change.consume()
                    }
                    touchY = null
                }
            }
            .padding(end = 4.dp)
            .clip(RoundedCornerShape(12.dp))
            .background(Color.White.copy(alpha = 0.04f)),
        contentAlignment = Alignment.Center
    ) {
        Column(
            modifier = Modifier.fillMaxHeight().fillMaxWidth(),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            val slot = if (barHeightPx > 0f) barHeightPx / ALPHABET.size else 0f
            val radius = slot * 3.5f
            val maxBoost = 1.9f

            ALPHABET.forEachIndexed { i, letter ->
                val center = i * slot + slot / 2f
                val ty = touchY
                val scale = if (ty == null || slot == 0f) {
                    1f
                } else {
                    val d = abs(ty - center)
                    if (d >= radius) 1f
                    else {
                        val k = 1f - (d / radius)
                        1f + (maxBoost - 1f) * k * k
                    }
                }
                val present = letter in presentLetters
                Box(
                    modifier = Modifier.weight(1f).fillMaxWidth(),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = letter.toString(),
                        color = if (present) Cream.copy(alpha = 0.85f)
                                else Cream.copy(alpha = 0.25f),
                        fontSize = 12.sp,
                        fontFamily = CormorantGaramond,
                        fontWeight = if (scale > 1.2f) FontWeight.SemiBold else FontWeight.Medium,
                        modifier = Modifier.graphicsLayer {
                            scaleX = scale
                            scaleY = scale
                            transformOrigin = TransformOrigin(1f, 0.5f)
                        }
                    )
                }
            }
        }
    }
}
