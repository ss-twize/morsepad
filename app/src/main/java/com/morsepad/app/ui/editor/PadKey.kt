package com.morsepad.app.ui.editor

import android.os.SystemClock
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.text.BasicText
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.waitForUpOrCancellation
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.morsepad.app.R
import com.morsepad.app.ui.theme.LocalPadColors
import com.morsepad.core.session.SpecialKey

private val KEY_CORNER = 10.dp
private const val REPEAT_START_DELAY_MILLIS = 450L
private const val REPEAT_INTERVAL_MILLIS = 60L

/**
 * A single-action key. Each gesture fires at most once: on release inside the key
 * (sliding off cancels), or — for [repeatOnHold] keys like backspace — on touch down and
 * then repeatedly while held. Keys are independent, so they work while fingers rest on
 * the chord pad.
 *
 * @param onPress receives the input event time (uptime millis) for latency measurement.
 */
@Composable
fun PadKey(
    label: String,
    onPress: (eventTimeMillis: Long) -> Unit,
    modifier: Modifier = Modifier,
    repeatOnHold: Boolean = false,
    fontSize: TextUnit = 26.sp,
) {
    val colors = LocalPadColors.current
    var pressed by remember { mutableStateOf(false) }
    val currentOnPress by rememberUpdatedState(onPress)

    Box(
        modifier
            .padding(3.dp)
            // `pressed` is read only while drawing: pressing a key repaints it without recomposing,
            // so the release that inserts a character costs no extra composition in that frame.
            .drawBehind {
                val corner = CornerRadius(KEY_CORNER.toPx())
                val stroke = 1.dp.toPx()
                drawRoundRect(if (pressed) colors.keyActive else colors.keyFill, cornerRadius = corner)
                drawRoundRect(
                    colors.keyBorder,
                    topLeft = Offset(stroke / 2, stroke / 2),
                    size = Size(size.width - stroke, size.height - stroke),
                    cornerRadius = corner,
                    style = Stroke(stroke),
                )
            }
            .pointerInput(repeatOnHold) {
                awaitEachGesture {
                    val down = awaitFirstDown(requireUnconsumed = false)
                    down.consume()
                    pressed = true
                    try {
                        if (repeatOnHold) {
                            currentOnPress(down.uptimeMillis)
                            var wait = REPEAT_START_DELAY_MILLIS
                            while (true) {
                                val ended = withTimeoutOrNull(wait) { waitForUpOrCancellation(); true }
                                if (ended == true) break
                                currentOnPress(SystemClock.uptimeMillis())
                                wait = REPEAT_INTERVAL_MILLIS
                            }
                        } else {
                            val up = waitForUpOrCancellation()
                            if (up != null) {
                                up.consume()
                                currentOnPress(up.uptimeMillis)
                            }
                        }
                    } finally {
                        pressed = false
                    }
                }
            },
        contentAlignment = Alignment.Center,
    ) {
        BasicText(
            text = label,
            style = TextStyle(fontSize = fontSize, fontWeight = FontWeight.Medium),
            color = { if (pressed) colors.labelActive else colors.text },
        )
    }
}

/** Bottom row: comma, a wider space, period and backspace (spec 4.2). */
@Composable
fun SpecialKeysRow(
    onKey: (SpecialKey, eventTimeMillis: Long) -> Unit,
    modifier: Modifier = Modifier,
) {
    Row(modifier) {
        PadKey(",", { onKey(SpecialKey.COMMA, it) }, Modifier.weight(1.2f).fillMaxHeight().testTag(TAG_KEY_COMMA), fontSize = 32.sp)
        PadKey(
            stringResource(R.string.key_space),
            { onKey(SpecialKey.SPACE, it) },
            Modifier.weight(4f).fillMaxHeight().testTag(TAG_KEY_SPACE),
            fontSize = 18.sp,
        )
        PadKey(".", { onKey(SpecialKey.PERIOD, it) }, Modifier.weight(1.2f).fillMaxHeight().testTag(TAG_KEY_PERIOD), fontSize = 32.sp)
        PadKey(
            "⌫",
            { onKey(SpecialKey.BACKSPACE, it) },
            Modifier.weight(1.2f).fillMaxHeight().testTag(TAG_KEY_BACKSPACE),
            repeatOnHold = true,
        )
    }
}

const val TAG_KEY_COMMA = "key_comma"
const val TAG_KEY_SPACE = "key_space"
const val TAG_KEY_PERIOD = "key_period"
const val TAG_KEY_BACKSPACE = "key_backspace"
