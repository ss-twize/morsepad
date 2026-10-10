package com.morsepad.app.ui.editor

import androidx.compose.foundation.layout.Box
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.PointerInputScope
import androidx.compose.ui.input.pointer.changedToDownIgnoreConsumed
import androidx.compose.ui.input.pointer.changedToUpIgnoreConsumed
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.util.fastForEach
import com.morsepad.app.input.TouchSignals
import com.morsepad.app.ui.theme.LocalPadColors
import com.morsepad.app.ui.theme.PadColors
import com.morsepad.core.codec.ChordMask
import com.morsepad.core.input.Chord
import com.morsepad.core.input.InputEngine
import com.morsepad.core.input.InputEngineListener

/**
 * Touch-side state of the five-key pad: owns the [InputEngine] and mirrors the pressed
 * keys into snapshot state that is read only in the draw phase, so a key press repaints
 * the pad without recomposing anything.
 */
@Stable
class ChordPadState : InputEngineListener {

    var onChord: (Chord) -> Unit = {}

    private val engine = InputEngine(this)

    /** Keys under a finger right now (drawn as active). */
    var pressedBits by mutableIntStateOf(0)
        private set

    /** Union of keys pressed during the current cycle (shown as a hint). */
    var accumulatedBits by mutableIntStateOf(0)
        private set

    fun pointerDown(pointerId: Long, key: Int, eventTimeMillis: Long) {
        if (engine.pointerDown(pointerId, key, eventTimeMillis)) sync()
    }

    fun pointerUp(pointerId: Long, eventTimeMillis: Long) {
        if (engine.pointerUp(pointerId, eventTimeMillis)) sync()
    }

    fun pointerCancelled(pointerId: Long) {
        if (engine.pointerCancelled(pointerId)) sync()
    }

    /** Drops an unfinished chord without committing it. */
    fun cancel() {
        if (engine.cancel()) sync()
    }

    override fun onChordCommitted(chord: Chord) {
        sync()
        onChord(chord)
    }

    override fun onChordCancelled() = sync()

    private fun sync() {
        pressedBits = engine.pressedKeys.bits
        accumulatedBits = engine.accumulatedMask.bits
    }
}

/**
 * Five equal, independent touch zones in one row. Every point of the pad belongs to exactly
 * one key, so no touch inside it is ever "between keys".
 *
 * @param extensionHints key index → letter typed by that key after the prefix (drawn while
 *   the prefix is pending), derived from the active encoding table.
 */
@Composable
fun ChordPad(
    state: ChordPadState,
    touchSignals: TouchSignals,
    showKeyLabels: Boolean,
    prefixPending: Boolean,
    extensionHints: Map<Int, Char>,
    modifier: Modifier = Modifier,
) {
    val colors = LocalPadColors.current
    val measurer = rememberTextMeasurer()
    val labels = remember(measurer) {
        (1..ChordMask.KEY_COUNT).map { measurer.measure("K$it", TextStyle(fontSize = 13.sp, fontWeight = FontWeight.Medium)) }
    }
    val hints = remember(measurer, extensionHints) {
        extensionHints.mapValues { (_, letter) -> measurer.measure(letter.toString(), TextStyle(fontSize = 34.sp)) }
    }

    Box(
        modifier
            .testTag(TAG_CHORD_PAD)
            .pointerInput(state, touchSignals) { trackChords(state, touchSignals) }
            .drawBehind {
                drawKeys(state.pressedBits, colors, if (showKeyLabels) labels else null, if (prefixPending) hints else null)
            },
    )
}

const val TAG_CHORD_PAD = "chord_pad"

/** Maps an x coordinate to K1..K5 (0..4) by equal-width columns. */
internal fun keyAt(x: Float, width: Int): Int =
    (x / width * ChordMask.KEY_COUNT).toInt().coerceIn(0, ChordMask.KEY_COUNT - 1)

private suspend fun PointerInputScope.trackChords(state: ChordPadState, signals: TouchSignals) {
    awaitPointerEventScope {
        try {
            while (true) {
                val event = awaitPointerEvent()
                event.changes.fastForEach { change ->
                    val id = change.id.value
                    when {
                        change.changedToDownIgnoreConsumed() -> {
                            state.pointerDown(id, keyAt(change.position.x, size.width), change.uptimeMillis)
                            change.consume()
                        }
                        change.changedToUpIgnoreConsumed() -> {
                            when {
                                // Compose turns ACTION_CANCEL into an "up" that arrives already
                                // consumed (SuspendingPointerInputFilter.onCancelPointerInput).
                                change.isConsumed -> state.cancel()
                                signals.pointerUpIsCanceled -> state.pointerCancelled(id)
                                else -> state.pointerUp(id, change.uptimeMillis)
                            }
                            change.consume()
                        }
                    }
                }
            }
        } finally {
            // The pad left the composition or its input was reset mid-chord.
            state.cancel()
        }
    }
}

private fun DrawScope.drawKeys(
    pressedBits: Int,
    colors: PadColors,
    labels: List<TextLayoutResult>?,
    hints: Map<Int, TextLayoutResult>?,
) {
    val keyWidth = size.width / ChordMask.KEY_COUNT
    val gap = 3.dp.toPx()
    val corner = CornerRadius(10.dp.toPx())
    val stroke = Stroke(width = (if (hints != null) 2.dp else 1.dp).toPx())
    val keySize = Size(keyWidth - 2 * gap, size.height - 2 * gap)

    for (key in 0 until ChordMask.KEY_COUNT) {
        val pressed = pressedBits and ChordMask.bitOf(key) != 0
        val topLeft = Offset(key * keyWidth + gap, gap)
        drawRoundRect(if (pressed) colors.keyActive else colors.keyFill, topLeft, keySize, corner)
        drawRoundRect(if (hints != null) colors.accent else colors.keyBorder, topLeft, keySize, corner, style = stroke)

        val ink = if (pressed) colors.labelActive else colors.label
        labels?.get(key)?.let { label ->
            val at = Offset(
                topLeft.x + (keySize.width - label.size.width) / 2,
                topLeft.y + keySize.height - label.size.height - 10.dp.toPx(),
            )
            drawText(label, color = ink, topLeft = at)
        }
        hints?.get(key)?.let { hint ->
            val at = Offset(
                topLeft.x + (keySize.width - hint.size.width) / 2,
                topLeft.y + (keySize.height - hint.size.height) / 2,
            )
            drawText(hint, color = if (pressed) colors.labelActive else colors.accent, topLeft = at)
        }
    }
}
