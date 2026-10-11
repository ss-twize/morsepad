package com.morsepad.app

import androidx.compose.foundation.ScrollState
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.semantics.getOrNull
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.unit.dp
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.morsepad.app.ui.settings.KeyWidthEditor
import com.morsepad.app.ui.settings.TAG_KEY_WIDTH_EDITOR
import com.morsepad.app.ui.theme.MorsePadTheme
import com.morsepad.core.codec.ChordTables
import com.morsepad.core.codec.TableChordCodec
import com.morsepad.core.layout.KeyLayout
import com.morsepad.core.settings.ThemeMode
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/** The editor is hosted like in settings: what it reports is stored and fed back into it. */
@RunWith(AndroidJUnit4::class)
class KeyWidthEditorTest {

    @get:Rule
    val rule = createComposeRule()

    private var stored by mutableStateOf(KeyLayout.EQUAL)
    private var commits = 0
    private val scroll = ScrollState(0)

    private fun show(scrollable: Boolean = false) {
        rule.setContent {
            MorsePadTheme(ThemeMode.DARK) {
                val editor = @androidx.compose.runtime.Composable {
                    KeyWidthEditor(stored, TableChordCodec(ChordTables.default), onChange = {
                        stored = it
                        commits++
                    })
                }
                if (scrollable) {
                    Column(Modifier.width(700.dp).height(260.dp).verticalScroll(scroll)) {
                        Spacer(Modifier.height(60.dp))
                        editor()
                        Spacer(Modifier.height(600.dp))
                    }
                } else {
                    Box(Modifier.width(700.dp)) { editor() }
                }
            }
        }
    }

    private fun drag(from: Float, to: Float) {
        rule.onNodeWithTag(TAG_KEY_WIDTH_EDITOR).performTouchInput {
            down(Offset(width * from, height / 2f))
            moveTo(Offset(width * (from + to) / 2, height / 2f))
            moveTo(Offset(width * to, height / 2f))
            up()
        }
        rule.waitForIdle()
    }

    /** The layout the editor is currently drawing (exposed through its content description). */
    private fun shownLayout(): KeyLayout {
        val description = rule.onNodeWithTag(TAG_KEY_WIDTH_EDITOR).fetchSemanticsNode()
            .config.getOrNull(SemanticsProperties.ContentDescription)!!.single()
        return KeyLayout.parse(description.removePrefix("key widths "))
    }

    @Test
    fun draggingAHandleResizesOnlyTheTwoKeysAroundIt() {
        show()
        drag(from = 0.2f, to = 0.3f)

        assertEquals(0.3f, stored.boundaries[0], 0.01f)
        assertEquals(listOf(0.4f, 0.6f, 0.8f), stored.boundaries.drop(1))
    }

    @Test
    fun aHandleStopsBeforeAKeyGetsTooNarrow() {
        show()
        drag(from = 0.4f, to = 0.95f)

        assertEquals(0.6f - KeyLayout.MIN_KEY_WIDTH, stored.boundaries[1], 0.01f)
    }

    @Test
    fun touchingAKeyAwayFromTheHandlesChangesNothing() {
        show()
        drag(from = 0.5f, to = 0.7f)

        assertEquals(0, commits)
    }

    @Test
    fun resetBetweenDragsIsNotUndoneByTheNextDrag() {
        show()
        drag(from = 0.2f, to = 0.3f)
        stored = KeyLayout.EQUAL // "Сделать одинаковыми"
        rule.waitForIdle()

        drag(from = 0.8f, to = 0.7f)

        assertEquals(listOf(0.2f, 0.4f, 0.6f), stored.boundaries.take(3))
        assertEquals(0.7f, stored.boundaries[3], 0.01f)
    }

    @Test
    fun keysFollowTheFingerDuringEveryDrag() {
        show()
        drag(from = 0.2f, to = 0.3f)

        rule.onNodeWithTag(TAG_KEY_WIDTH_EDITOR).performTouchInput {
            down(Offset(width * 0.6f, height / 2f))
            moveTo(Offset(width * 0.65f, height / 2f))
            moveTo(Offset(width * 0.7f, height / 2f))
        }
        rule.waitForIdle()
        assertEquals("live while the finger is down", 0.7f, shownLayout().boundaries[2], 0.01f)

        rule.onNodeWithTag(TAG_KEY_WIDTH_EDITOR).performTouchInput { up() }
        rule.waitForIdle()
        assertEquals(0.7f, stored.boundaries[2], 0.01f)
    }

    @Test
    fun verticalSwipeOnAHandleScrollsThePage() {
        show(scrollable = true)

        rule.onNodeWithTag(TAG_KEY_WIDTH_EDITOR).performTouchInput {
            val x = width * 0.4f
            down(Offset(x, height * 0.8f))
            moveTo(Offset(x, height * 0.5f))
            moveTo(Offset(x, height * 0.1f))
            up()
        }
        rule.waitForIdle()

        assertEquals(0, commits)
        assertTrue("page scrolled", scroll.value > 0)
    }
}
