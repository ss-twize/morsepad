package com.morsepad.app.ui.editor

import androidx.compose.foundation.layout.Box
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
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
import androidx.compose.ui.text.TextMeasurer
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.util.fastForEach
import com.morsepad.app.input.TouchSignals
import com.morsepad.app.ui.theme.LocalPadColors
import com.morsepad.app.ui.theme.PadColors
import com.morsepad.core.codec.ChordCodec
import com.morsepad.core.codec.ChordMask
import com.morsepad.core.codec.KeyHint
import com.morsepad.core.codec.KeyHints
import com.morsepad.core.input.Chord
import com.morsepad.core.input.InputEngine
import com.morsepad.core.input.InputEngineListener
import com.morsepad.core.layout.KeyLayout

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

    /** Union of keys pressed during the current cycle — the chord that will be committed. */
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
 * Five independent touch zones in one row, sized by [layout]. Every point of the pad belongs to
 * exactly one key, so no touch inside it is ever "between keys".
 *
 * Hint mode ([showLetterHints]): each key shows the character the chord would type if that key
 * joined it; keys already in the chord turn white. While the prefix is pending the keys always
 * show what the second chord types (Ъ, Ф, space, comma, period).
 */
@Composable
fun ChordPad(
    state: ChordPadState,
    touchSignals: TouchSignals,
    layout: KeyLayout,
    codec: ChordCodec,
    prefixPending: Boolean,
    showKeyLabels: Boolean,
    showLetterHints: Boolean,
    modifier: Modifier = Modifier,
) {
    val colors = LocalPadColors.current
    val measurer = rememberTextMeasurer()
    val labels = rememberKeyLabels(measurer)
    val glyphs = remember(measurer) { GlyphCache(measurer) }
    val currentLayout by rememberUpdatedState(layout)
    val showHints = showLetterHints || prefixPending
    // Measure every hint glyph up front, off the frame that first shows it.
    LaunchedEffect(glyphs, codec) {
        (codec.table.letters.values + codec.table.extensions.values + PREFIX_GLYPH).forEach { glyphs[it] }
    }

    Box(
        modifier
            .testTag(TAG_CHORD_PAD)
            .pointerInput(state, touchSignals) { trackChords(state, touchSignals) { currentLayout } }
            .drawBehind {
                // Snapshot state read here, in the draw phase: a press repaints, nothing recomposes.
                val chord = ChordMask(state.accumulatedBits)
                drawChordKeys(
                    layout = layout,
                    heldBits = state.pressedBits,
                    chordBits = chord.bits,
                    colors = colors,
                    labels = if (showKeyLabels) labels else null,
                    accent = prefixPending,
                ) { key ->
                    if (!showHints) return@drawChordKeys null
                    when (val hint = KeyHints.hintFor(codec, chord, prefixPending, key)) {
                        is KeyHint.Output -> glyphs[hint.char]
                        KeyHint.Prefix -> glyphs[PREFIX_GLYPH]
                        KeyHint.InChord, KeyHint.None -> null
                    }
                }
            },
    )
}

const val TAG_CHORD_PAD = "chord_pad"

/** Shown on the key that would complete the 11111 prefix. */
private const val PREFIX_GLYPH = '…'

/** How a character is written on a key: space gets a visible sign. */
fun keyGlyphText(char: Char): String = if (char == ' ') "␣" else char.toString()

/** Measures each hint character once and reuses the layout in every frame. */
internal class GlyphCache(private val measurer: TextMeasurer) {
    private val letterStyle = TextStyle(fontSize = 34.sp, fontWeight = FontWeight.Medium)

    // Comma and period are tiny glyphs; they need a larger size to read as well as letters.
    private val punctuationStyle = TextStyle(fontSize = 64.sp, fontWeight = FontWeight.Bold)
    private val cache = HashMap<Char, TextLayoutResult>()

    operator fun get(char: Char): TextLayoutResult = cache.getOrPut(char) {
        measurer.measure(keyGlyphText(char), if (char == ',' || char == '.') punctuationStyle else letterStyle)
    }
}

@Composable
internal fun rememberKeyLabels(measurer: TextMeasurer): List<TextLayoutResult> = remember(measurer) {
    (1..ChordMask.KEY_COUNT).map { measurer.measure("K$it", TextStyle(fontSize = 13.sp, fontWeight = FontWeight.Medium)) }
}

private suspend fun PointerInputScope.trackChords(state: ChordPadState, signals: TouchSignals, layout: () -> KeyLayout) {
    awaitPointerEventScope {
        try {
            while (true) {
                val event = awaitPointerEvent()
                event.changes.fastForEach { change ->
                    val id = change.id.value
                    when {
                        change.changedToDownIgnoreConsumed() -> {
                            state.pointerDown(id, layout().keyAt(change.position.x / size.width), change.uptimeMillis)
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

/**
 * Draws the five keys. Shared by the pad and the key-width preview in settings, so the preview
 * looks exactly like the real keys.
 *
 * @param heldBits keys under a finger (white); [chordBits] keys counted in the chord (keys
 *   released early are drawn half-lit); [glyphFor] the hint drawn in the middle of a key.
 */
internal fun DrawScope.drawChordKeys(
    layout: KeyLayout,
    heldBits: Int,
    chordBits: Int,
    colors: PadColors,
    labels: List<TextLayoutResult>?,
    accent: Boolean,
    glyphFor: (key: Int) -> TextLayoutResult?,
) {
    val gap = 3.dp.toPx()
    val corner = CornerRadius(10.dp.toPx())
    val stroke = Stroke(width = (if (accent) 2.dp else 1.dp).toPx())
    val height = size.height - 2 * gap

    for (key in 0 until ChordMask.KEY_COUNT) {
        val bit = ChordMask.bitOf(key)
        val held = heldBits and bit != 0
        val inChord = chordBits and bit != 0
        val left = layout.keyStart(key) * size.width + gap
        val keySize = Size(layout.keyEnd(key) * size.width - gap - left, height)
        val topLeft = Offset(left, gap)

        val fill = when {
            held -> colors.keyActive
            inChord -> colors.keyActive.copy(alpha = 0.45f)
            else -> colors.keyFill
        }
        drawRoundRect(fill, topLeft, keySize, corner)
        drawRoundRect(if (accent) colors.accent else colors.keyBorder, topLeft, keySize, corner, style = stroke)

        labels?.get(key)?.let { label ->
            val at = Offset(
                topLeft.x + (keySize.width - label.size.width) / 2,
                topLeft.y + keySize.height - label.size.height - 10.dp.toPx(),
            )
            drawText(label, color = if (held) colors.labelActive else colors.label, topLeft = at)
        }
        glyphFor(key)?.let { glyph ->
            val at = Offset(
                topLeft.x + (keySize.width - glyph.size.width) / 2,
                topLeft.y + (keySize.height - glyph.size.height) / 2,
            )
            drawText(glyph, color = if (accent) colors.accent else colors.text, topLeft = at)
        }
    }
}
