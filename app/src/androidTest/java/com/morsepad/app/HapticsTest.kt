package com.morsepad.app

import android.os.SystemClock
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.morsepad.app.input.Haptics
import org.junit.After
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import java.io.FileInputStream

/**
 * The in-app vibration must work even when the system "touch feedback" switch is off —
 * that switch silently dropped every tick before (status `ignored_for_settings`).
 * Checked through the vibrator service's own history (`dumpsys vibrator_manager`).
 */
@RunWith(AndroidJUnit4::class)
class HapticsTest {

    private val automation = InstrumentationRegistry.getInstrumentation().uiAutomation
    private val haptics = Haptics(ApplicationProvider.getApplicationContext())
    private var touchFeedbackBefore = "1"

    private fun shell(command: String): String =
        automation.executeShellCommand(command).use { fd -> FileInputStream(fd.fileDescriptor).bufferedReader().readText() }

    /** Status of this app's most recent vibration, e.g. "finished" or "ignored_for_settings". */
    private fun lastVibrationStatus(): String? =
        shell("dumpsys vibrator_manager")
            .substringAfter("Recent vibrations:")
            .lines()
            .filter { "com.morsepad" in it }
            .maxByOrNull { it.trim().take(18) } // lines start with "MM-dd HH:mm:ss.SSS"
            ?.split('|')
            ?.getOrNull(2)
            ?.trim()

    @Before
    fun setUp() {
        touchFeedbackBefore = shell("settings get system haptic_feedback_enabled").trim().ifEmpty { "1" }
        haptics.enabled = true
    }

    @After
    fun tearDown() {
        shell("settings put system haptic_feedback_enabled $touchFeedbackBefore")
    }

    @Test
    fun chordTickPlaysWhenSystemTouchFeedbackIsOff() {
        assertTrue("device reports a vibrator", haptics.isAvailable)
        shell("settings put system haptic_feedback_enabled 0")
        SystemClock.sleep(300)

        haptics.tick()
        SystemClock.sleep(600)

        val status = lastVibrationStatus()
        assertTrue("vibration status was '$status'", status == "finished" || status == "running")
    }
}
