package com.morsepad.app.diagnostics

import android.content.Context
import android.os.Build
import com.morsepad.core.diagnostics.LatencyStats
import com.morsepad.core.diagnostics.LatencySummary
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/** Writes the collected samples to app-specific external storage (readable with `adb pull`). */
object LatencyReport {

    fun metrics(tracker: LatencyTracker): List<Pair<String, LatencyStats>> = listOf(
        "chord_release_to_text_draw_ms" to tracker.chordToDraw,
        "chord_release_to_frame_ready_ms" to tracker.chordToFrame,
        "special_key_to_text_draw_ms" to tracker.keyToDraw,
        "input_event_to_text_change_ms" to tracker.inputDispatch,
    )

    fun summaryLine(name: String, s: LatencySummary): String =
        "%s n=%d mean=%.1f p50=%.1f p90=%.1f p95=%.1f p99=%.1f max=%.1f".format(
            Locale.US, name, s.count, s.mean, s.p50, s.p90, s.p95, s.p99, s.max,
        )

    /** Blocking file IO — call from a background dispatcher. */
    fun write(context: Context, tracker: LatencyTracker): File {
        val dir = context.getExternalFilesDir(null) ?: context.filesDir
        val stamp = SimpleDateFormat("yyyyMMdd-HHmmss", Locale.US).format(Date())
        val file = File(dir, "latency-$stamp.csv")
        file.bufferedWriter().use { out ->
            out.appendLine("# MorsePad latency report $stamp")
            out.appendLine("# device=${Build.MANUFACTURER} ${Build.MODEL} sdk=${Build.VERSION.SDK_INT}")
            metrics(tracker).forEach { (name, stats) -> out.appendLine("# " + summaryLine(name, stats.summary())) }
            out.appendLine("metric,index,value_ms")
            metrics(tracker).forEach { (name, stats) ->
                stats.samples().forEachIndexed { i, v -> out.appendLine("$name,$i,${"%.3f".format(Locale.US, v)}") }
            }
        }
        return file
    }
}
