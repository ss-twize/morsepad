package com.morsepad.app.ui.editor

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.displayCutout
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.systemGestureExclusion
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalWindowInfo
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.morsepad.app.R
import com.morsepad.app.diagnostics.LatencyTracker
import com.morsepad.app.input.Haptics
import com.morsepad.app.input.TouchSignals
import com.morsepad.app.ui.theme.LocalPadColors
import com.morsepad.core.codec.ChordCodec
import com.morsepad.core.codec.ChordMask
import com.morsepad.core.notes.SaveStatus
import com.morsepad.core.session.SpecialKey
import kotlinx.coroutines.delay

private const val PREFIX_CANCEL_NOTICE_MILLIS = 1200L

/**
 * Landscape input screen: note text with Done and backspace on top, five chord keys below.
 * Space, comma and period are chords after the prefix, so the keys get the whole lower part
 * of the screen. Area heights are fixed fractions, so neither a long note nor status messages
 * ever resize the keys.
 */
@Composable
fun EditorScreen(
    viewModel: NoteViewModel,
    latencyTracker: LatencyTracker,
    touchSignals: TouchSignals,
    haptics: Haptics,
    onExit: () -> Unit,
) {
    val colors = LocalPadColors.current
    val settings by viewModel.settings.collectAsStateWithLifecycle()
    val saveStatus by viewModel.saveStatus.collectAsStateWithLifecycle()
    SideEffect { haptics.enabled = settings.vibrationEnabled }

    val padState = remember(viewModel, haptics) {
        ChordPadState().apply { onChord = { chord -> viewModel.onChord(chord)?.let(haptics::after) } }
    }

    ImmersiveSystemBars()
    FrameTimingProbe(latencyTracker)

    // An unfinished chord must never survive losing the screen (spec 5.3).
    LifecycleEventEffect(Lifecycle.Event.ON_PAUSE) {
        padState.cancel()
        viewModel.flushNow()
    }
    LifecycleEventEffect(Lifecycle.Event.ON_STOP) { viewModel.onBackgrounded() }
    val windowInfo = LocalWindowInfo.current
    LaunchedEffect(windowInfo) {
        snapshotFlow { windowInfo.isWindowFocused }.collect { focused -> if (!focused) padState.cancel() }
    }

    val currentOnExit by rememberUpdatedState(onExit)
    var exiting by remember { mutableStateOf(false) }
    val exit = {
        if (!exiting) { // a second Back/Done before the screen is gone must not navigate twice
            exiting = true
            padState.cancel()
            viewModel.close()
            currentOnExit()
        }
    }
    BackHandler(onBack = exit)

    var showCancelNotice by remember { mutableStateOf(false) }
    LaunchedEffect(viewModel.prefixCancelCount) {
        if (viewModel.prefixCancelCount == 0) return@LaunchedEffect
        showCancelNotice = true
        delay(PREFIX_CANCEL_NOTICE_MILLIS)
        showCancelNotice = false
    }

    Column(
        Modifier
            .fillMaxSize()
            .background(colors.background)
            .testTag(if (viewModel.isReady) TAG_EDITOR_READY else TAG_EDITOR_LOADING),
    ) {
        // Only text is kept clear of a display cutout; the keys use the full width (spec 4.1),
        // touches next to a camera hole still register.
        Row(Modifier.fillMaxWidth().weight(0.30f).windowInsetsPadding(WindowInsets.displayCutout)) {
            Column(Modifier.weight(1f).fillMaxHeight()) {
                NoteTextArea(
                    // Lambdas: a new character recomposes only the text area, not this screen.
                    text = { viewModel.text },
                    revision = { viewModel.revision },
                    textSizeSp = settings.textSizeSp,
                    latencyTracker = latencyTracker,
                    modifier = Modifier.weight(1f).fillMaxWidth(),
                )
                Row(Modifier.fillMaxWidth().height(22.dp).padding(horizontal = 16.dp), verticalAlignment = Alignment.CenterVertically) {
                    HintLine(
                        // Read inside HintLine: a finger press recomposes only that line.
                        chordBits = { if (settings.showChordHint) padState.accumulatedBits else 0 },
                        prefixPending = viewModel.prefixPending,
                        showCancelNotice = showCancelNotice,
                        codec = viewModel.codec,
                        modifier = Modifier.weight(1f),
                    )
                    SaveStatusText(saveStatus)
                }
            }
            Column(Modifier.width(200.dp).fillMaxHeight().padding(4.dp)) {
                PadKey(
                    stringResource(R.string.editor_done),
                    { exit() },
                    Modifier.fillMaxWidth().weight(0.42f).testTag(TAG_KEY_DONE),
                    fontSize = 17.sp,
                )
                PadKey(
                    "⌫",
                    { time -> viewModel.onSpecialKey(SpecialKey.BACKSPACE, time)?.let(haptics::after) },
                    Modifier.fillMaxWidth().weight(0.58f).testTag(TAG_KEY_BACKSPACE),
                    repeatOnHold = true,
                    fontSize = 34.sp,
                )
            }
        }
        ChordPad(
            state = padState,
            touchSignals = touchSignals,
            layout = settings.keyLayout,
            codec = viewModel.codec,
            prefixPending = viewModel.prefixPending,
            showKeyLabels = settings.showKeyLabels,
            showLetterHints = settings.showLetterHints,
            // Android honours ~200 dp of gesture exclusion per edge; spend it on the chord keys,
            // where an accidental Back would cancel a multi-finger chord.
            modifier = Modifier
                .fillMaxWidth()
                .weight(0.70f)
                .systemGestureExclusion(),
        )
    }
}

/** Code and result of the chord being pressed, or the prefix state. */
@Composable
private fun HintLine(
    chordBits: () -> Int,
    prefixPending: Boolean,
    showCancelNotice: Boolean,
    codec: ChordCodec,
    modifier: Modifier = Modifier,
) {
    val colors = LocalPadColors.current
    val bits = chordBits()
    val (line, color) = when {
        showCancelNotice -> stringResource(R.string.editor_prefix_cancelled) to colors.warning
        bits != 0 -> {
            val mask = ChordMask(bits)
            val result = when {
                prefixPending -> codec.decodeExtension(mask)?.let { describe(it) } ?: "—"
                codec.isPrefix(mask) -> stringResource(R.string.editor_prefix)
                else -> codec.decode(mask)?.let { describe(it) } ?: "—"
            }
            "${mask.toCode()} → $result" to if (prefixPending) colors.accent else colors.text
        }
        prefixPending -> stringResource(R.string.editor_prefix_pending) to colors.accent
        else -> "" to colors.textSecondary
    }
    Text(
        line,
        color = color,
        fontSize = 14.sp,
        maxLines = 1,
        overflow = TextOverflow.Ellipsis,
        modifier = modifier.testTag(TAG_HINT),
    )
}

@Composable
private fun describe(char: Char): String = when (char) {
    ' ' -> stringResource(R.string.symbol_space)
    ',' -> stringResource(R.string.symbol_comma)
    '.' -> stringResource(R.string.symbol_period)
    else -> char.toString()
}

@Composable
private fun SaveStatusText(status: SaveStatus) {
    val colors = LocalPadColors.current
    Spacer(Modifier.width(8.dp))
    Text(
        text = when (status) {
            SaveStatus.Idle -> ""
            SaveStatus.Saved -> stringResource(R.string.save_saved)
            SaveStatus.Pending, SaveStatus.Saving -> stringResource(R.string.save_saving)
            is SaveStatus.Failed -> stringResource(R.string.save_failed)
        },
        color = if (status is SaveStatus.Failed) colors.warning else colors.textSecondary,
        fontSize = 12.sp,
        maxLines = 1,
    )
}

const val TAG_EDITOR_READY = "editor_ready"
const val TAG_EDITOR_LOADING = "editor_loading"
const val TAG_KEY_DONE = "key_done"
const val TAG_HINT = "chord_hint"
