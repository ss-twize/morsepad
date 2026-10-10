package com.morsepad.app.ui.editor

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
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
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalView
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
import com.morsepad.app.input.TouchSignals
import com.morsepad.app.ui.theme.LocalPadColors
import com.morsepad.core.codec.ChordMask
import com.morsepad.core.codec.ChordTables
import com.morsepad.core.codec.TableChordCodec
import com.morsepad.core.notes.SaveStatus
import com.morsepad.core.session.SpecialKey
import kotlinx.coroutines.delay

private const val PREFIX_CANCEL_NOTICE_MILLIS = 1200L

/**
 * Landscape input screen: note text on top, five chord keys in the middle, special keys at
 * the bottom (spec 4.2). Area heights are fixed fractions of the screen, so neither a long
 * note nor status messages ever resize the keys.
 */
@Composable
fun EditorScreen(
    viewModel: NoteViewModel,
    latencyTracker: LatencyTracker,
    touchSignals: TouchSignals,
    onExit: () -> Unit,
) {
    val colors = LocalPadColors.current
    val settings by viewModel.settings.collectAsStateWithLifecycle()
    val saveStatus by viewModel.saveStatus.collectAsStateWithLifecycle()

    val view = LocalView.current
    val feedback = remember(view) { InputFeedback(view) }
    feedback.enabled = settings.vibrationEnabled

    val padState = remember(viewModel, feedback) {
        ChordPadState().apply { onChord = { chord -> viewModel.onChord(chord)?.let(feedback::after) } }
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

    Column(
        Modifier
            .fillMaxSize()
            .background(colors.background)
            .testTag(if (viewModel.isReady) TAG_EDITOR_READY else TAG_EDITOR_LOADING),
    ) {
        // Only text is kept clear of a display cutout; the keys use the full width (spec 4.1),
        // touches next to a camera hole still register.
        Row(Modifier.fillMaxWidth().weight(0.30f).windowInsetsPadding(WindowInsets.displayCutout)) {
            NoteTextArea(
                // Lambdas: a new character recomposes only the text area, not this screen.
                text = { viewModel.text },
                revision = { viewModel.revision },
                textSizeSp = settings.textSizeSp,
                latencyTracker = latencyTracker,
                modifier = Modifier.weight(1f).fillMaxHeight(),
            )
            AuxPanel(
                saveStatus = saveStatus,
                prefixPending = viewModel.prefixPending,
                prefixCancelCount = viewModel.prefixCancelCount,
                // Read inside AuxPanel: a finger press recomposes only the hint line.
                chordHintBits = { if (settings.showChordHint) padState.accumulatedBits else 0 },
                codeTableVersion = settings.codeTableVersion,
                onUndo = { time -> viewModel.onSpecialKey(SpecialKey.UNDO, time)?.let(feedback::after) },
                onDone = { exit() },
                modifier = Modifier.width(196.dp).fillMaxHeight(),
            )
        }
        ChordPad(
            state = padState,
            touchSignals = touchSignals,
            showKeyLabels = settings.showKeyLabels,
            prefixPending = viewModel.prefixPending,
            extensionHints = viewModel.extensionHints,
            // Android honours ~200 dp of gesture exclusion per edge; spend it on the chord keys,
            // where an accidental Back would cancel a multi-finger chord.
            modifier = Modifier
                .fillMaxWidth()
                .weight(0.52f)
                .systemGestureExclusion(),
        )
        SpecialKeysRow(
            onKey = { key, time -> viewModel.onSpecialKey(key, time)?.let(feedback::after) },
            modifier = Modifier
                .fillMaxWidth()
                .weight(0.18f),
        )
    }
}

/** Compact auxiliary panel: undo, done, save status and chord/prefix hints. */
@Composable
private fun AuxPanel(
    saveStatus: SaveStatus,
    prefixPending: Boolean,
    prefixCancelCount: Int,
    chordHintBits: () -> Int,
    codeTableVersion: String,
    onUndo: (Long) -> Unit,
    onDone: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = LocalPadColors.current
    var showCancelNotice by remember { mutableStateOf(false) }
    LaunchedEffect(prefixCancelCount) {
        if (prefixCancelCount == 0) return@LaunchedEffect
        showCancelNotice = true
        delay(PREFIX_CANCEL_NOTICE_MILLIS)
        showCancelNotice = false
    }
    val codec = remember(codeTableVersion) { TableChordCodec(ChordTables.byVersion(codeTableVersion) ?: ChordTables.default) }

    Column(modifier.padding(4.dp), verticalArrangement = Arrangement.SpaceBetween) {
        Row(Modifier.fillMaxWidth().height(52.dp)) {
            PadKey("↶", onUndo, Modifier.weight(1f).fillMaxHeight().testTag(TAG_KEY_UNDO), fontSize = 24.sp)
            PadKey(stringResource(R.string.editor_done), { onDone() }, Modifier.weight(1.6f).fillMaxHeight().testTag(TAG_KEY_DONE), fontSize = 16.sp)
        }
        HintLine(chordHintBits, prefixPending, showCancelNotice, codec)
        Text(
            text = when (saveStatus) {
                SaveStatus.Idle -> ""
                SaveStatus.Saved -> stringResource(R.string.save_saved)
                SaveStatus.Pending, SaveStatus.Saving -> stringResource(R.string.save_saving)
                is SaveStatus.Failed -> stringResource(R.string.save_failed)
            },
            color = if (saveStatus is SaveStatus.Failed) colors.warning else colors.textSecondary,
            fontSize = 12.sp,
            maxLines = 1,
            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
        )
    }
}

/** Isolated so that finger presses (which change [chordHintBits]) recompose only this line. */
@Composable
private fun HintLine(chordHintBits: () -> Int, prefixPending: Boolean, showCancelNotice: Boolean, codec: TableChordCodec) {
    val colors = LocalPadColors.current
    val bits = chordHintBits()
    val (line, color) = when {
        showCancelNotice -> stringResource(R.string.editor_prefix_cancelled) to colors.warning
        prefixPending -> stringResource(R.string.editor_prefix_pending) to colors.accent
        bits != 0 -> {
            val mask = ChordMask(bits)
            val letter = codec.decode(mask)?.toString() ?: if (codec.isPrefix(mask)) "Ф/Ъ…" else "—"
            "${mask.toCode()} → $letter" to colors.text
        }
        else -> "" to colors.textSecondary
    }
    Text(line, color = color, fontSize = 15.sp, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.padding(horizontal = 6.dp).testTag(TAG_HINT))
}

const val TAG_EDITOR_READY = "editor_ready"
const val TAG_EDITOR_LOADING = "editor_loading"
const val TAG_KEY_UNDO = "key_undo"
const val TAG_KEY_DONE = "key_done"
const val TAG_HINT = "chord_hint"
