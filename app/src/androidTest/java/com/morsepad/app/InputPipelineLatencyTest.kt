package com.morsepad.app

import android.graphics.Rect
import android.os.Bundle
import android.os.SystemClock
import android.util.Log
import android.view.InputDevice
import android.view.MotionEvent
import androidx.test.core.app.ActivityScenario
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.uiautomator.By
import androidx.test.uiautomator.UiDevice
import androidx.test.uiautomator.Until
import com.morsepad.app.diagnostics.LatencyReport
import com.morsepad.app.ui.editor.TAG_CHORD_PAD
import com.morsepad.app.ui.editor.TAG_EDITOR_READY
import com.morsepad.app.ui.editor.TAG_NOTE_TEXT
import com.morsepad.app.ui.notes.TAG_NEW_NOTE
import com.morsepad.core.codec.ChordMask
import com.morsepad.core.codec.ChordTables
import com.morsepad.core.codec.TableChordCodec
import com.morsepad.core.editor.TextEditor
import com.morsepad.core.session.TypingSession
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import kotlin.random.Random

/**
 * Drives the real app through the system input pipeline (UiAutomation → InputDispatcher →
 * window) with genuine multi-pointer MotionEvents, and checks that
 * 1) fast sequential chords lose no characters, and
 * 2) the in-app instrumentation records release → text-draw latency.
 *
 * Deliberately no Compose test rule here: it replaces the frame clock with a virtual one,
 * which would batch frames and make draw timings meaningless. Nodes are located with
 * UiAutomator through `testTagsAsResourceId`.
 *
 * Emulator numbers are not representative of a phone; the same test runs unchanged on a
 * real device (`./gradlew connectedDebugAndroidTest`).
 */
@RunWith(AndroidJUnit4::class)
class InputPipelineLatencyTest {

    private val instrumentation = InstrumentationRegistry.getInstrumentation()
    private val automation = instrumentation.uiAutomation
    private val device = UiDevice.getInstance(instrumentation)
    private lateinit var scenario: ActivityScenario<MainActivity>

    @Before
    fun setUp() {
        // Android shows a one-time "Viewing full screen" hint over the middle of the screen the
        // first time an app hides the system bars. It would swallow injected touches on K2–K4,
        // so mark it as already confirmed (standard practice for UI tests).
        automation.executeShellCommand("settings put secure immersive_mode_confirmations confirmed").close()
        resetAppData()
        scenario = ActivityScenario.launch(MainActivity::class.java)
        device.wait(Until.findObject(By.res(TAG_NEW_NOTE)), TIMEOUT).click()
        assertTrue("editor opened", device.wait(Until.hasObject(By.res(TAG_EDITOR_READY)), TIMEOUT))
        device.waitForIdle()
    }

    @After
    fun tearDown() = scenario.close()

    @Test
    fun steadyTypingAtEightChordsPerSecond() = typeAndMeasure(chords = 240, pauseBetweenChordsMillis = 80, label = "steady")

    @Test
    fun burstTypingLosesNothing() = typeAndMeasure(chords = 300, pauseBetweenChordsMillis = 0, label = "burst")

    private fun typeAndMeasure(chords: Int, pauseBetweenChordsMillis: Long, label: String) {
        val pad: Rect = device.findObject(By.res(TAG_CHORD_PAD)).visibleBounds
        fun keyOnScreen(key: Int) = Point(pad.left + pad.width() * (key + 0.5f) / 5f, pad.exactCenterY())

        val random = Random(label.hashCode())
        val masks = List(chords) { ChordMask(random.nextInt(1, 32)) }
        val started = SystemClock.uptimeMillis()
        masks.forEach { mask ->
            val keys = (0 until 5).filter { mask.isPressed(it) }.shuffled(random)
            injectChord(keys.map(::keyOnScreen))
            if (pauseBetweenChordsMillis > 0) SystemClock.sleep(pauseBetweenChordsMillis)
        }
        val elapsed = SystemClock.uptimeMillis() - started
        SystemClock.sleep(1_000) // let the last frames, vsync callbacks and the debounced save run

        val expected = TypingSession(TableChordCodec(ChordTables.V1_0), TextEditor()).run {
            masks.forEach(::onChord)
            editor.text
        }
        val shown = device.findObject(By.res(TAG_NOTE_TEXT)).text.trimEnd('▏')
        assertEquals("every chord must produce exactly its character", expected, shown)

        val tracker = testContainer.latencyTracker
        val results = Bundle()
        val rate = "%.1f chords/s".format(chords * 1000.0 / elapsed)
        Log.i(TAG, "[$label] $chords chords in $elapsed ms ($rate)")
        results.putString("$label:rate", rate)
        LatencyReport.metrics(tracker).forEach { (name, stats) ->
            val line = LatencyReport.summaryLine(name, stats.summary())
            Log.i(TAG, "[$label] $line")
            results.putString("$label:$name", line)
        }
        val file = LatencyReport.write(instrumentation.targetContext, tracker)
        Log.i(TAG, "[$label] report: ${file.absolutePath}")
        instrumentation.sendStatus(0, results)

        // Several chords can land inside one frame; each frame yields one sample.
        assertTrue("latency samples recorded", tracker.chordToDraw.summary().count > 0)
    }

    private data class Point(val x: Float, val y: Float)

    /** Fingers land 4 ms apart, are held briefly, then lift in landing order. */
    private fun injectChord(points: List<Point>) {
        val downTime = SystemClock.uptimeMillis()
        val active = mutableListOf<Pair<Int, Point>>()
        points.forEachIndexed { index, point ->
            active += index to point
            val action = if (active.size == 1) MotionEvent.ACTION_DOWN else pointerAction(MotionEvent.ACTION_POINTER_DOWN, active.lastIndex)
            inject(downTime, action, active)
            SystemClock.sleep(4)
        }
        SystemClock.sleep(16)
        while (active.isNotEmpty()) {
            val action = if (active.size == 1) MotionEvent.ACTION_UP else pointerAction(MotionEvent.ACTION_POINTER_UP, 0)
            inject(downTime, action, active)
            active.removeAt(0)
            if (active.isNotEmpty()) SystemClock.sleep(4)
        }
    }

    private fun pointerAction(action: Int, index: Int) = action or (index shl MotionEvent.ACTION_POINTER_INDEX_SHIFT)

    private fun inject(downTime: Long, action: Int, pointers: List<Pair<Int, Point>>) {
        val properties = pointers.map { (id, _) ->
            MotionEvent.PointerProperties().apply {
                this.id = id
                toolType = MotionEvent.TOOL_TYPE_FINGER
            }
        }.toTypedArray()
        val coords = pointers.map { (_, p) ->
            MotionEvent.PointerCoords().apply {
                x = p.x
                y = p.y
                pressure = 1f
                size = 1f
            }
        }.toTypedArray()
        val event = MotionEvent.obtain(
            downTime, SystemClock.uptimeMillis(), action, pointers.size, properties, coords,
            0, 0, 1f, 1f, 0, 0, InputDevice.SOURCE_TOUCHSCREEN, 0,
        )
        try {
            check(automation.injectInputEvent(event, true)) { "injection failed for action $action" }
        } finally {
            event.recycle()
        }
    }

    private companion object {
        const val TAG = "MorsePadLatency"
        const val TIMEOUT = 5_000L
    }
}
