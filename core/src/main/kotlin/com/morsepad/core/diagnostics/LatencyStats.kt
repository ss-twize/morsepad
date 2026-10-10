package com.morsepad.core.diagnostics

import kotlin.math.ceil

data class LatencySummary(
    /** Samples currently in the window. */
    val count: Int,
    /** Samples recorded since the last clear, including those that left the window. */
    val totalRecorded: Long,
    val min: Double,
    val mean: Double,
    val p50: Double,
    val p90: Double,
    val p95: Double,
    val p99: Double,
    val max: Double,
)

/**
 * Sliding window of latency samples (milliseconds) with tail percentiles.
 * High percentiles matter more than the mean: they expose rare stalls (section 11.4).
 * Recording is O(1) and allocation-free; [summary] sorts a copy and is meant for reports.
 */
class LatencyStats(private val capacity: Int = DEFAULT_CAPACITY) {

    init {
        require(capacity > 0) { "capacity must be positive" }
    }

    private val ring = DoubleArray(capacity)
    private var next = 0
    private var size = 0
    private var total = 0L

    @Synchronized
    fun record(valueMillis: Double) {
        ring[next] = valueMillis
        next = (next + 1) % capacity
        if (size < capacity) size++
        total++
    }

    /** Samples in the window, oldest first. */
    @Synchronized
    fun samples(): List<Double> {
        val start = (next - size + capacity) % capacity
        return List(size) { ring[(start + it) % capacity] }
    }

    @Synchronized
    fun summary(): LatencySummary {
        if (size == 0) return LatencySummary(0, total, 0.0, 0.0, 0.0, 0.0, 0.0, 0.0, 0.0)
        val sorted = samples().sorted()
        fun percentile(p: Double): Double {
            val rank = ceil(p / 100.0 * sorted.size).toInt().coerceIn(1, sorted.size)
            return sorted[rank - 1]
        }
        return LatencySummary(
            count = size,
            totalRecorded = total,
            min = sorted.first(),
            mean = sorted.average(),
            p50 = percentile(50.0),
            p90 = percentile(90.0),
            p95 = percentile(95.0),
            p99 = percentile(99.0),
            max = sorted.last(),
        )
    }

    @Synchronized
    fun clear() {
        next = 0
        size = 0
        total = 0
    }

    private companion object {
        const val DEFAULT_CAPACITY = 4096
    }
}
