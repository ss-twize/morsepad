package com.morsepad.core.diagnostics

import org.junit.Assert.assertEquals
import org.junit.Test

class LatencyStatsTest {

    @Test
    fun `empty stats report zero samples`() {
        assertEquals(0, LatencyStats().summary().count)
    }

    @Test
    fun `percentiles use the nearest rank method`() {
        val stats = LatencyStats()
        (1..100).shuffled().forEach { stats.record(it.toDouble()) }

        val summary = stats.summary()

        assertEquals(100, summary.count)
        assertEquals(1.0, summary.min, 0.0)
        assertEquals(50.0, summary.p50, 0.0)
        assertEquals(90.0, summary.p90, 0.0)
        assertEquals(95.0, summary.p95, 0.0)
        assertEquals(99.0, summary.p99, 0.0)
        assertEquals(100.0, summary.max, 0.0)
        assertEquals(50.5, summary.mean, 1e-9)
    }

    @Test
    fun `single sample is every percentile`() {
        val stats = LatencyStats()
        stats.record(12.5)

        val summary = stats.summary()

        assertEquals(12.5, summary.p50, 0.0)
        assertEquals(12.5, summary.p99, 0.0)
    }

    @Test
    fun `only the most recent samples are kept`() {
        val stats = LatencyStats(capacity = 10)
        (1..25).forEach { stats.record(it.toDouble()) }

        val summary = stats.summary()

        assertEquals(10, summary.count)
        assertEquals(16.0, summary.min, 0.0)
        assertEquals(25.0, summary.max, 0.0)
        assertEquals(25, summary.totalRecorded)
    }

    @Test
    fun `samples are returned oldest first`() {
        val stats = LatencyStats(capacity = 3)
        listOf(1.0, 2.0, 3.0, 4.0).forEach(stats::record)

        assertEquals(listOf(2.0, 3.0, 4.0), stats.samples())
    }

    @Test
    fun `clear forgets everything`() {
        val stats = LatencyStats()
        stats.record(5.0)
        stats.clear()

        assertEquals(0, stats.summary().count)
        assertEquals(0, stats.summary().totalRecorded)
    }
}
