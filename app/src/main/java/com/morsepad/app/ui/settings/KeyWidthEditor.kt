package com.morsepad.app.ui.settings

import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.awaitHorizontalTouchSlopOrCancellation
import androidx.compose.foundation.gestures.horizontalDrag
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.morsepad.app.ui.editor.GlyphCache
import com.morsepad.app.ui.editor.drawChordKeys
import com.morsepad.app.ui.editor.rememberKeyLabels
import com.morsepad.app.ui.theme.LocalPadColors
import com.morsepad.core.codec.ChordCodec
import com.morsepad.core.codec.ChordMask
import com.morsepad.core.layout.KeyLayout
import kotlin.math.abs
import kotlin.math.roundToInt

/**
 * Key-width slider with four handles — one on each boundary between neighbouring keys.
 * Dragging a handle resizes the two keys around it. The keys are drawn exactly like on the
 * input screen (same renderer, letters each key types alone), so the user sees the real
 * proportions while dragging. The new layout is reported once, when the finger is lifted.
 *
 * A gesture becomes a drag only after it moves sideways past the touch slop, so a vertical
 * swipe that starts on a handle still scrolls the settings page.
 */
@Composable
fun KeyWidthEditor(
    layout: KeyLayout,
    codec: ChordCodec,
    onChange: (KeyLayout) -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = LocalPadColors.current
    val measurer = rememberTextMeasurer()
    val labels = rememberKeyLabels(measurer)
    val glyphs = remember(measurer) { GlyphCache(measurer) }
    val percentStyle = remember { TextStyle(fontSize = 12.sp) }
    val percentLabels = remember(measurer) { HashMap<Int, TextLayoutResult>() }
    val currentOnChange by rememberUpdatedState(onChange)
    val currentLayout by rememberUpdatedState(layout)

    // The layout being dragged; it stays on screen after the drag until the stored layout
    // catches up (no flicker back), and is dropped whenever the stored layout changes.
    var draft by remember { mutableStateOf<KeyLayout?>(null) }
    var activeHandle by remember { mutableIntStateOf(NO_HANDLE) }
    LaunchedEffect(layout) { if (activeHandle == NO_HANDLE) draft = null }
    val shown = draft ?: layout

    Box(
        modifier
            .fillMaxWidth()
            .height(150.dp)
            .testTag(TAG_KEY_WIDTH_EDITOR)
            .semantics { contentDescription = "key widths ${shown.serialize()}" }
            .pointerInput(Unit) {
                awaitEachGesture {
                    val down = awaitFirstDown(requireUnconsumed = false)
                    val base = draft ?: currentLayout
                    val handle = base.boundaries.indices.minBy { abs(base.boundaries[it] * size.width - down.position.x) }
                    if (abs(base.boundaries[handle] * size.width - down.position.x) > HANDLE_GRAB_RADIUS.toPx()) {
                        return@awaitEachGesture
                    }
                    // Not consumed yet: until it moves sideways the gesture may still be a page scroll.
                    val start = awaitHorizontalTouchSlopOrCancellation(down.id) { change, _ -> change.consume() }
                        ?: return@awaitEachGesture
                    activeHandle = handle
                    var current = base.moveBoundary(handle, start.position.x / size.width)
                    draft = current
                    val finished = horizontalDrag(start.id) { change ->
                        current = current.moveBoundary(handle, change.position.x / size.width)
                        draft = current
                        change.consume()
                    }
                    activeHandle = NO_HANDLE
                    if (finished) currentOnChange(current) else draft = null
                }
            }
            .drawBehind {
                drawChordKeys(shown, heldBits = 0, chordBits = 0, colors = colors, labels = labels, accent = false) { key ->
                    codec.decode(ChordMask.ofKey(key))?.let { glyphs[it] }
                }
                shown.widths.forEachIndexed { key, width ->
                    val percent = (width * 100).roundToInt()
                    val text = percentLabels.getOrPut(percent) { measurer.measure("$percent%", percentStyle) }
                    val centre = (shown.keyStart(key) + shown.keyEnd(key)) / 2 * size.width
                    drawText(text, color = colors.textSecondary, topLeft = Offset(centre - text.size.width / 2, 10.dp.toPx()))
                }
                shown.boundaries.forEachIndexed { index, boundary ->
                    val centre = Offset(boundary * size.width, size.height / 2)
                    val radius = (if (index == activeHandle) HANDLE_RADIUS_ACTIVE else HANDLE_RADIUS).toPx()
                    drawCircle(colors.accent, radius, centre)
                    drawCircle(colors.background, radius * 0.35f, centre)
                }
            },
    )
}

const val TAG_KEY_WIDTH_EDITOR = "key_width_editor"

private const val NO_HANDLE = -1
private val HANDLE_RADIUS = 12.dp
private val HANDLE_RADIUS_ACTIVE = 16.dp
private val HANDLE_GRAB_RADIUS = 36.dp
