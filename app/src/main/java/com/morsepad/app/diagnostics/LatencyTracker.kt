package com.morsepad.app.diagnostics

import android.view.FrameMetrics
import android.view.Window
import android.view.animation.AnimationUtils
import com.morsepad.core.diagnostics.LatencyStats
import kotlin.math.abs

/**
 * Measures how long it takes from the input event to the new text.
 *
 * All timestamps share CLOCK_MONOTONIC: MotionEvent times (and Compose's
 * PointerInputChange.uptimeMillis) use the SystemClock.uptimeMillis base, which on Android is
 * CLOCK_MONOTONIC — the same clock as System.nanoTime(), Choreographer and FrameMetrics.
 *
 * Stages, each measured from the event timestamp assigned by the input system:
 * - [inputDispatch]: the app has processed the event and changed the text;
 * - [chordToDraw] / [keyToDraw]: the new text is being drawn (UI thread draw pass);
 * - [chordToFrame]: the frame containing the new text is complete and handed to the system
 *   compositor (HWUI FrameMetrics: intended vsync + total duration). It appears on the
 *   display at the following vsync, i.e. at most one refresh period later.
 *
 * [onTextCommitted] and [onTextDrawn] run on the main thread; [frameMetricsListener] runs on
 * its own handler thread.
 */
class LatencyTracker {

    enum class Source { CHORD, KEY }

    val inputDispatch = LatencyStats()
    val chordToDraw = LatencyStats()
    val keyToDraw = LatencyStats()
    val chordToFrame = LatencyStats()

    private var pendingRevision = NONE
    private var pendingEventMillis = 0L
    private var pendingSource = Source.CHORD

    /** Drawn chords waiting for the FrameMetrics of their frame: (frame vsync ms, event ms). */
    private val awaitingFrame = ArrayDeque<LongArray>()

    /** The text changed because of an input event that happened at [eventUptimeMillis]. */
    fun onTextCommitted(eventUptimeMillis: Long, revision: Long, source: Source) {
        inputDispatch.record(nowMillis() - eventUptimeMillis)
        pendingRevision = revision
        pendingEventMillis = eventUptimeMillis
        pendingSource = source
    }

    /** Called from the draw pass of the note text with the text revision being drawn. */
    fun onTextDrawn(revision: Long) {
        if (revision != pendingRevision) return
        pendingRevision = NONE
        val drawnAfter = nowMillis() - pendingEventMillis
        when (pendingSource) {
            Source.KEY -> keyToDraw.record(drawnAfter)
            Source.CHORD -> {
                chordToDraw.record(drawnAfter)
                // During a frame the animation clock is locked to that frame's vsync time,
                // which is how FrameMetrics identifies the frame.
                val frameVsync = AnimationUtils.currentAnimationTimeMillis()
                synchronized(awaitingFrame) {
                    if (awaitingFrame.size == MAX_AWAITING) awaitingFrame.removeFirst()
                    awaitingFrame.addLast(longArrayOf(frameVsync, pendingEventMillis))
                }
            }
        }
    }

    /** Attach with Window.addOnFrameMetricsAvailableListener while the input screen is shown. */
    val frameMetricsListener = Window.OnFrameMetricsAvailableListener { _, metrics, _ ->
        val vsyncMillis = metrics.getMetric(FrameMetrics.VSYNC_TIMESTAMP) / NANOS_PER_MILLI_L
        val completedMillis =
            (metrics.getMetric(FrameMetrics.INTENDED_VSYNC_TIMESTAMP) + metrics.getMetric(FrameMetrics.TOTAL_DURATION)) / NANOS_PER_MILLI
        synchronized(awaitingFrame) {
            val iterator = awaitingFrame.iterator()
            while (iterator.hasNext()) {
                val (frameVsync, eventMillis) = iterator.next()
                when {
                    abs(frameVsync - vsyncMillis) <= 1 -> {
                        chordToFrame.record(completedMillis - eventMillis)
                        iterator.remove()
                    }
                    frameVsync < vsyncMillis - STALE_MILLIS -> iterator.remove()
                }
            }
        }
    }

    fun clear() {
        listOf(inputDispatch, chordToDraw, keyToDraw, chordToFrame).forEach(LatencyStats::clear)
        pendingRevision = NONE
        synchronized(awaitingFrame) { awaitingFrame.clear() }
    }

    private fun nowMillis(): Double = System.nanoTime() / NANOS_PER_MILLI

    private companion object {
        const val NONE = -1L
        const val NANOS_PER_MILLI = 1_000_000.0
        const val NANOS_PER_MILLI_L = 1_000_000L
        const val MAX_AWAITING = 64
        const val STALE_MILLIS = 2_000L
    }
}
