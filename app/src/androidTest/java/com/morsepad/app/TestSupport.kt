package com.morsepad.app

import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.semantics.getOrNull
import androidx.compose.ui.test.SemanticsNodeInteraction
import androidx.compose.ui.test.TouchInjectionScope
import androidx.compose.ui.test.junit4.ComposeTestRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.geometry.Offset
import androidx.test.core.app.ApplicationProvider
import com.morsepad.app.ui.editor.TAG_CHORD_PAD
import com.morsepad.app.ui.editor.TAG_EDITOR_READY
import com.morsepad.app.ui.editor.TAG_NOTE_TEXT
import com.morsepad.core.codec.ChordMask
import com.morsepad.core.settings.AppSettings
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking

val testContainer: AppContainer
    get() = ApplicationProvider.getApplicationContext<MorsePadApp>().container

/** Empties the app's storage through its own repositories (the database stays open). */
fun resetAppData() = runBlocking {
    val container = testContainer
    container.writeBarrier.awaitIdle()
    container.noteRepository.observeNotes().first().forEach { container.noteRepository.delete(it.id) }
    container.settingsRepository.setOpenNoteId(null)
    // Default settings: tests compute key positions for equal key widths.
    container.settingsRepository.update { AppSettings() }
    container.latencyTracker.clear()
}

fun ComposeTestRule.waitForEditor() {
    waitUntil(5_000) { onAllNodesWithTag(TAG_EDITOR_READY).fetchSemanticsNodes().isNotEmpty() }
}

/** Presses all keys of [code] with one finger each, then lifts them. */
fun ComposeTestRule.chord(code: String) {
    val mask = ChordMask.parse(code)
    val keys = (0 until 5).filter { mask.isPressed(it) }
    onNodeWithTag(TAG_CHORD_PAD).performTouchInput {
        keys.forEachIndexed { pointer, key -> down(pointer, keyCenter(key)) }
        keys.indices.forEach { up(it) }
    }
    waitForIdle()
}

fun TouchInjectionScope.keyCenter(key: Int) = Offset(width * (key + 0.5f) / 5f, height / 2f)

/** Taps a pointer-input key (special keys have no click semantics on purpose). */
fun ComposeTestRule.tapKey(tag: String) {
    onNodeWithTag(tag).performTouchInput {
        down(center)
        up()
    }
    waitForIdle()
}

/** The note text as displayed, without the caret. */
fun ComposeTestRule.noteText(): String = onNodeWithTag(TAG_NOTE_TEXT).textContent().trimEnd('▏')

fun SemanticsNodeInteraction.textContent(): String =
    fetchSemanticsNode().config.getOrNull(SemanticsProperties.Text)?.joinToString("") { it.text }.orEmpty()
