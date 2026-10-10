package com.morsepad.app

import androidx.compose.ui.test.assert
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createEmptyComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextInput
import androidx.compose.ui.test.performTouchInput
import androidx.lifecycle.Lifecycle
import androidx.test.core.app.ActivityScenario
import androidx.test.espresso.Espresso
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.morsepad.app.ui.editor.TAG_CHORD_PAD
import com.morsepad.app.ui.editor.TAG_HINT
import com.morsepad.app.ui.editor.TAG_KEY_BACKSPACE
import com.morsepad.app.ui.editor.TAG_KEY_COMMA
import com.morsepad.app.ui.editor.TAG_KEY_DONE
import com.morsepad.app.ui.editor.TAG_KEY_PERIOD
import com.morsepad.app.ui.editor.TAG_KEY_SPACE
import com.morsepad.app.ui.editor.TAG_KEY_UNDO
import com.morsepad.app.ui.notes.TAG_CONFIRM_DELETE
import com.morsepad.app.ui.notes.TAG_DELETE_PREFIX
import com.morsepad.app.ui.notes.TAG_NEW_NOTE
import com.morsepad.app.ui.notes.TAG_SEARCH
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/** End-to-end flows in the real activity: typing, editing rules, persistence, lifecycle. */
@RunWith(AndroidJUnit4::class)
class EditorFlowTest {

    @get:Rule
    val compose = createEmptyComposeRule()

    private var scenario: ActivityScenario<MainActivity>? = null

    @Before
    fun setUp() {
        resetAppData()
        scenario = ActivityScenario.launch(MainActivity::class.java)
    }

    @After
    fun tearDown() {
        scenario?.close()
    }

    private fun openNewNote() {
        compose.onNodeWithTag(TAG_NEW_NOTE).performClick()
        compose.waitForEditor()
    }

    private fun storedNotes() = runBlocking {
        testContainer.writeBarrier.awaitIdle()
        testContainer.noteRepository.observeNotes().first()
    }

    @Test
    fun typesLettersPunctuationAndAutomaticCase() {
        openNewNote()
        // "да, ок. но" : Д=10010 А=01000 , О=00100 К=01010 . Н=10000 О=00100
        listOf("10010", "01000").forEach(compose::chord)
        compose.tapKey(TAG_KEY_COMMA)
        compose.tapKey(TAG_KEY_SPACE)
        listOf("00100", "01010").forEach(compose::chord)
        compose.tapKey(TAG_KEY_PERIOD)
        compose.tapKey(TAG_KEY_SPACE)
        listOf("10000", "00100").forEach(compose::chord)

        assertEquals("Да, ок. Но", compose.noteText())
    }

    @Test
    fun prefixTypesФandЪ() {
        openNewNote()
        compose.chord("11111")
        compose.onNodeWithTag(TAG_HINT).assert(hasText("Префикс", substring = true))
        compose.chord("01000")
        compose.chord("11111")
        compose.chord("10000")

        assertEquals("Фъ", compose.noteText())
    }

    @Test
    fun specialKeyDuringPrefixCancelsItVisiblyAndStillActs() {
        openNewNote()
        compose.chord("01000") // А
        compose.chord("11111")
        compose.tapKey(TAG_KEY_SPACE)

        compose.onNodeWithTag(TAG_HINT).assert(hasText("Префикс отменён"))
        compose.chord("01000")
        assertEquals("А а", compose.noteText())
    }

    @Test
    fun backspaceAndUndoTreatExtendedLetterAsOneCharacter() {
        openNewNote()
        listOf("10001", "11111", "01000").forEach(compose::chord) // У Ф
        assertEquals("Уф", compose.noteText())

        compose.tapKey(TAG_KEY_BACKSPACE)
        assertEquals("У", compose.noteText())

        compose.tapKey(TAG_KEY_UNDO)
        assertEquals("Уф", compose.noteText())
    }

    @Test
    fun deletingBackToAPeriodRestoresUppercase() {
        openNewNote()
        compose.chord("10010") // Д
        compose.tapKey(TAG_KEY_PERIOD)
        compose.tapKey(TAG_KEY_SPACE)
        compose.chord("10010") // Д
        compose.tapKey(TAG_KEY_BACKSPACE)
        compose.chord("01000") // А

        assertEquals("Д. А", compose.noteText())
    }

    @Test
    fun chordHeldWhileTheAppLosesForegroundIsDropped() {
        openNewNote()
        compose.onNodeWithTag(TAG_CHORD_PAD).performTouchInput { down(0, keyCenter(1)) }
        compose.waitForIdle()

        scenario!!.moveToState(Lifecycle.State.STARTED) // onPause
        scenario!!.moveToState(Lifecycle.State.RESUMED)
        compose.onNodeWithTag(TAG_CHORD_PAD).performTouchInput { up(0) }
        compose.waitForIdle()

        assertEquals("", compose.noteText())
        compose.chord("01000")
        assertEquals("А", compose.noteText())
    }

    @Test
    fun textSurvivesActivityRecreation() {
        openNewNote()
        listOf("10010", "01000").forEach(compose::chord)

        scenario!!.recreate()
        compose.waitForEditor()

        assertEquals("Да", compose.noteText())
    }

    @Test
    fun doneSavesTheNoteAndListShowsIt() {
        openNewNote()
        listOf("10010", "01000").forEach(compose::chord)
        compose.tapKey(TAG_KEY_DONE)

        compose.onNodeWithText("Да").assertIsDisplayed()
        assertEquals(listOf("Да"), storedNotes().map { it.content })
    }

    @Test
    fun doubleTapOnDoneLeavesOnlyOneNoteList() {
        openNewNote()
        compose.chord("01000")
        // Both taps are delivered before the screen changes.
        compose.onNodeWithTag(TAG_KEY_DONE).performTouchInput {
            down(center)
            up()
            down(center)
            up()
        }
        compose.waitForIdle()

        Espresso.pressBackUnconditionally() // with a duplicated list this would just pop it
        compose.waitForIdle()
        assertEquals(Lifecycle.State.DESTROYED, scenario!!.state)
    }

    @Test
    fun emptyNoteLeavesNothingBehind() {
        openNewNote()
        compose.tapKey(TAG_KEY_DONE)

        assertTrue(storedNotes().isEmpty())
    }

    @Test
    fun unfinishedNoteIsReopenedAfterRestart() {
        openNewNote()
        listOf("10010", "01000").forEach(compose::chord)

        scenario!!.moveToState(Lifecycle.State.CREATED) // backgrounded: flushes the save
        scenario!!.close() // process-style teardown without pressing Done
        scenario = ActivityScenario.launch(MainActivity::class.java)
        compose.waitForEditor()

        assertEquals("Да", compose.noteText())
    }

    @Test
    fun searchAndDeleteWithConfirmation() {
        runBlocking {
            testContainer.noteRepository.create("Купить Молоко")
            testContainer.noteRepository.create("Позвонить маме")
        }
        compose.waitUntil(5_000) { compose.onAllNodesWithTag(TAG_SEARCH).fetchSemanticsNodes().isNotEmpty() }

        compose.onNodeWithTag(TAG_SEARCH).performTextInput("молоко")
        compose.onNodeWithText("Купить Молоко").assertIsDisplayed()
        assertTrue(compose.onAllNodesWithText("Позвонить маме").fetchSemanticsNodes().isEmpty())

        val id = storedNotes().first { it.content == "Купить Молоко" }.id
        compose.onNodeWithTag("$TAG_DELETE_PREFIX$id").performClick()
        compose.onNodeWithTag(TAG_CONFIRM_DELETE).performClick()
        compose.waitForIdle()

        assertEquals(listOf("Позвонить маме"), storedNotes().map { it.content })
    }
}
