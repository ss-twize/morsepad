package com.morsepad.app.ui.editor

import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import android.os.Build
import android.os.Handler
import android.os.HandlerThread
import android.view.HapticFeedbackConstants
import android.view.View
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalView
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import androidx.lifecycle.compose.LifecycleResumeEffect
import com.morsepad.app.diagnostics.LatencyTracker
import com.morsepad.core.session.SessionUpdate

/**
 * Hides status and navigation bars while the input screen is shown (spec 4.1).
 * A swipe from the edge reveals them temporarily; they are hidden again on every resume.
 */
@Composable
fun ImmersiveSystemBars() {
    val view = LocalView.current
    val window = view.context.findActivity()?.window ?: return
    val controller = remember(window, view) { WindowCompat.getInsetsController(window, view) }

    DisposableEffect(controller) {
        controller.systemBarsBehavior = WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
        controller.hide(WindowInsetsCompat.Type.systemBars())
        onDispose { controller.show(WindowInsetsCompat.Type.systemBars()) }
    }
    LifecycleResumeEffect(controller) {
        controller.hide(WindowInsetsCompat.Type.systemBars())
        onPauseOrDispose { }
    }
}

/** Feeds per-frame timings (HWUI FrameMetrics) to the tracker while the input screen is shown. */
@Composable
fun FrameTimingProbe(tracker: LatencyTracker) {
    val window = LocalView.current.context.findActivity()?.window ?: return
    DisposableEffect(window, tracker) {
        val thread = HandlerThread("frame-metrics").apply { start() }
        window.addOnFrameMetricsAvailableListener(tracker.frameMetricsListener, Handler(thread.looper))
        onDispose {
            window.removeOnFrameMetricsAvailableListener(tracker.frameMetricsListener)
            thread.quitSafely()
        }
    }
}

tailrec fun Context.findActivity(): Activity? = when (this) {
    is Activity -> this
    is ContextWrapper -> baseContext.findActivity()
    else -> null
}

/** Tactile confirmation of input; the cancelled-prefix signal differs from a normal tap. */
class InputFeedback(private val view: View) {
    var enabled: Boolean = true

    fun after(update: SessionUpdate) {
        if (!enabled) return
        when {
            update.prefixCancelled -> view.performHapticFeedback(
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) HapticFeedbackConstants.REJECT
                else HapticFeedbackConstants.LONG_PRESS,
            )
            update.textChanged || update.prefixPending -> view.performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP)
        }
    }
}
