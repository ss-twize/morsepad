package com.morsepad.app

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.TouchInjectionScope
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.unit.dp
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.morsepad.app.input.TouchSignals
import com.morsepad.app.ui.editor.ChordPad
import com.morsepad.app.ui.editor.ChordPadState
import com.morsepad.app.ui.editor.TAG_CHORD_PAD
import com.morsepad.app.ui.theme.MorsePadTheme
import com.morsepad.core.codec.ChordMask
import com.morsepad.core.settings.ThemeMode
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import kotlin.random.Random

/**
 * Multitouch behaviour of the real chord pad composable, driven by multi-pointer
 * MotionEvents (ACTION_DOWN / POINTER_DOWN / POINTER_UP / UP / CANCEL) on a device or emulator.
 * Covers section 11.2 of the specification except what needs physical fingers.
 */
@RunWith(AndroidJUnit4::class)
class ChordPadMultitouchTest {

    @get:Rule
    val rule = createComposeRule()

    private val chords = mutableListOf<String>()
    private val signals = TouchSignals()
    private val state = ChordPadState().apply { onChord = { chords += it.mask.toCode() } }

    @Before
    fun setUp() {
        rule.setContent {
            MorsePadTheme(ThemeMode.DARK) {
                Column(Modifier.fillMaxSize()) {
                    Box(Modifier.fillMaxWidth().height(120.dp).testTag(OUTSIDE))
                    ChordPad(
                        state = state,
                        touchSignals = signals,
                        showKeyLabels = true,
                        prefixPending = false,
                        extensionHints = emptyMap(),
                        modifier = Modifier.fillMaxWidth().height(260.dp),
                    )
                }
            }
        }
    }

    private fun TouchInjectionScope.key(index: Int) = Offset(width * (index + 0.5f) / 5f, height / 2f)

    private fun keysOf(mask: ChordMask) = (0 until 5).filter { mask.isPressed(it) }

    private fun pad(block: TouchInjectionScope.() -> Unit) {
        rule.onNodeWithTag(TAG_CHORD_PAD).performTouchInput(block)
        rule.waitForIdle()
    }

    @Test
    fun everyCombinationOfOneToFiveFingersCommitsItsMask() {
        ChordMask.allNonEmpty.forEach { mask ->
            pad {
                keysOf(mask).forEachIndexed { pointer, key -> down(pointer, key(key)) }
                keysOf(mask).indices.forEach { up(it) }
            }
        }

        assertEquals(ChordMask.allNonEmpty.map { it.toCode() }, chords)
    }

    @Test
    fun fingersLandingAtDifferentTimesFormOneChord() {
        pad {
            down(0, key(0))
            advanceEventTime(180)
            down(1, key(2))
            advanceEventTime(40)
            down(2, key(4))
            advanceEventTime(300)
            up(2)
            up(1)
            up(0)
        }

        assertEquals(listOf("10101"), chords)
    }

    @Test
    fun everyReleaseOrderOfFiveFingersGivesTheSameChord() {
        permutations((0 until 5).toList()).forEach { order ->
            pad {
                (0 until 5).forEach { down(it, key(it)) }
                order.forEach { up(it) }
            }
        }

        assertEquals(120, chords.size)
        assertTrue(chords.all { it == "11111" })
    }

    @Test
    fun keyReleasedEarlyStaysInTheChord() {
        pad {
            down(0, key(0))
            down(1, key(2))
            up(0)
            down(2, key(4))
            up(1)
            up(2)
        }

        assertEquals(listOf("10101"), chords)
    }

    @Test
    fun nothingIsCommittedWhileAFingerRemains() {
        pad {
            down(0, key(1))
            down(1, key(3))
            up(1)
        }
        assertTrue(chords.isEmpty())
        assertEquals("01000", ChordMask(state.pressedBits).toCode())

        pad { up(0) }
        assertEquals(listOf("01010"), chords)
        assertEquals(0, state.pressedBits)
    }

    @Test
    fun systemCancelDropsTheChordAndInputRecovers() {
        pad {
            down(0, key(0))
            down(1, key(1))
            cancel()
        }
        assertTrue(chords.isEmpty())
        assertEquals(0, state.pressedBits)

        pad {
            down(0, key(3))
            up(0)
        }
        assertEquals(listOf("00010"), chords)
    }

    @Test
    fun singlePointerCancelledBySystemIsRemoved() {
        pad {
            down(0, key(0))
            down(1, key(2))
        }
        signals.pointerUpIsCanceled = true // as MainActivity sets it for FLAG_CANCELED
        pad { up(1) }
        signals.pointerUpIsCanceled = false
        pad { up(0) }

        assertEquals(listOf("10000"), chords)
    }

    @Test
    fun slidingFingerKeepsTheKeyItLandedOn() {
        pad {
            down(0, key(0))
            moveTo(0, key(3))
            moveTo(0, Offset(key(3).x, -200f)) // even outside the pad
            up(0)
        }

        assertEquals(listOf("10000"), chords)
    }

    @Test
    fun touchesOutsideThePadDoNotJoinTheChord() {
        val pad = rule.onNodeWithTag(TAG_CHORD_PAD).fetchSemanticsNode().boundsInRoot
        val outside = rule.onNodeWithTag(OUTSIDE).fetchSemanticsNode().boundsInRoot
        rule.onRoot().performTouchInput {
            down(0, outside.center)
            down(1, Offset(pad.left + pad.width * 0.3f, pad.center.y)) // K2
            up(1)
            up(0)
        }
        rule.waitForIdle()

        assertEquals(listOf("01000"), chords)
    }

    @Test
    fun twoFingersOnOneKeyCountOnce() {
        pad {
            down(0, Offset(key(2).x - 20f, key(2).y))
            down(1, Offset(key(2).x + 20f, key(2).y))
            up(0)
            up(1)
        }

        assertEquals(listOf("00100"), chords)
    }

    @Test
    fun rapidAlternatingChordsLoseNothing() {
        val random = Random(7)
        val expected = List(400) { ChordMask(random.nextInt(1, 32)) }
        expected.chunked(50).forEach { batch ->
            pad {
                batch.forEach { mask ->
                    val keys = keysOf(mask)
                    keys.forEachIndexed { pointer, key -> down(pointer, key(key)) }
                    keys.indices.shuffled(random).forEach { up(it) }
                    advanceEventTime(16)
                }
            }
        }

        assertEquals(expected.map { it.toCode() }, chords)
    }

    @Test
    fun keyColumnsSplitThePadIntoEqualFifths() {
        // Points near the inner edges of each column still map to that column.
        pad {
            (0 until 5).forEach { k ->
                val left = Offset(width * k / 5f + 2f, height / 2f)
                val right = Offset(width * (k + 1) / 5f - 2f, height / 2f)
                down(0, left)
                up(0)
                down(0, right)
                up(0)
            }
        }

        val expected = (0 until 5).flatMap { listOf(ChordMask.ofKey(it).toCode(), ChordMask.ofKey(it).toCode()) }
        assertEquals(expected, chords)
    }

    private fun <T> permutations(items: List<T>): List<List<T>> =
        if (items.size <= 1) listOf(items) else items.flatMap { head -> permutations(items - head).map { listOf(head) + it } }

    private companion object {
        const val OUTSIDE = "outside"
    }
}
