package com.morsepad.app.ui.editor

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.morsepad.app.R
import com.morsepad.app.diagnostics.LatencyTracker
import com.morsepad.app.ui.theme.LocalPadColors

/** Longest tail of a note laid out on the input screen; keeps per-character cost flat. */
private const val VISIBLE_TAIL_CHARS = 3000
private const val CARET = '▏'

/**
 * The note text, anchored to its end. The area has a fixed height given by its parent, so a
 * growing note never moves the keys.
 *
 * Only the last [VISIBLE_TAIL_CHARS] characters are laid out: re-measuring a whole long note
 * on every keystroke would make latency grow with note length. A static caret shows where
 * the next character goes (and makes trailing spaces visible); there is no blinking.
 */
@Composable
fun NoteTextArea(
    text: () -> String,
    revision: () -> Long,
    textSizeSp: Int,
    latencyTracker: LatencyTracker,
    modifier: Modifier = Modifier,
) {
    val colors = LocalPadColors.current
    val text = text()
    val revision = revision()
    val scroll = rememberScrollState()
    val shown = remember(text, colors) {
        buildAnnotatedString {
            append(visibleTail(text))
            withStyle(SpanStyle(color = colors.textSecondary)) { append(CARET) }
        }
    }
    // reverseScrolling: offset 0 means "pinned to the end"; new text resets a manual scroll-back.
    LaunchedEffect(revision) {
        if (scroll.value != 0) scroll.scrollTo(0)
    }

    Box(modifier.padding(horizontal = 16.dp, vertical = 8.dp)) {
        if (text.isEmpty()) {
            Text(
                text = stringResource(R.string.editor_placeholder),
                color = colors.textSecondary,
                fontSize = (textSizeSp * 0.8f).sp,
                modifier = Modifier.padding(start = 12.dp),
            )
        }
        Text(
            text = shown,
            style = TextStyle(color = colors.text, fontSize = textSizeSp.sp, lineHeight = (textSizeSp * 1.3f).sp),
            modifier = Modifier
                .fillMaxWidth()
                .verticalScroll(scroll, reverseScrolling = true)
                .testTag(TAG_NOTE_TEXT)
                .drawWithContent {
                    drawContent()
                    latencyTracker.onTextDrawn(revision)
                },
        )
    }
}

const val TAG_NOTE_TEXT = "note_text"

internal fun visibleTail(text: String): String {
    if (text.length <= VISIBLE_TAIL_CHARS) return text
    val start = text.length - VISIBLE_TAIL_CHARS
    val wordStart = text.indexOf(' ', start).takeIf { it in start until start + 64 }?.plus(1) ?: start
    return "… " + text.substring(wordStart)
}
