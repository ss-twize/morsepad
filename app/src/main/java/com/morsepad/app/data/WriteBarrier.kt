package com.morsepad.app.data

import kotlinx.coroutines.Job
import kotlinx.coroutines.joinAll
import java.util.concurrent.ConcurrentHashMap

/**
 * Tracks background writes that outlive a screen (final save on leaving the editor) so that
 * the next screen reading the same note waits for them instead of seeing stale content.
 */
class WriteBarrier {
    private val inFlight = ConcurrentHashMap.newKeySet<Job>()

    fun track(job: Job): Job {
        inFlight += job
        job.invokeOnCompletion { inFlight -= job }
        return job
    }

    suspend fun awaitIdle() {
        inFlight.toList().joinAll()
    }
}
