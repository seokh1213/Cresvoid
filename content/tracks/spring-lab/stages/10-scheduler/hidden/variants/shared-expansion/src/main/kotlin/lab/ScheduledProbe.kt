package lab

import org.springframework.scheduling.annotation.Scheduled
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit

class ScheduledProbe {
    private val release = CountDownLatch(1)
    private var startedAt = 0L
    private var finished = 0
    private var pulses = 0
    private var during = 0
    private var firstPulseMillis = -1L
    private var longThread = 0L
    private var pulseThread = 0L
    private var expired = 0

    @Scheduled(initialDelay = 0, fixedDelay = 60_000)
    fun slowJob() {
        synchronized(this) {
            startedAt = System.nanoTime()
            longThread = Thread.currentThread().threadId()
        }
        val released = release.await(15, TimeUnit.SECONDS)
        synchronized(this) {
            if (!released) expired++
            finished++
        }
    }

    @Scheduled(initialDelay = 200, fixedDelay = 100)
    @Synchronized
    fun pulse() {
        if (startedAt == 0L) return
        pulses++
        if (finished == 0) during++
        if (firstPulseMillis < 0) {
            firstPulseMillis = (System.nanoTime() - startedAt) / 1_000_000
            pulseThread = Thread.currentThread().threadId()
        }
    }

    fun releaseJob() = release.countDown()

    @Synchronized
    fun snapshot(): String = mapOf(
        "long_started" to if (startedAt == 0L) 0 else 1,
        "long_finished" to finished,
        "pulses" to pulses,
        "during" to during,
        "first_pulse_ms" to firstPulseMillis,
        "long_thread" to longThread,
        "pulse_thread" to pulseThread,
        "expired" to expired,
    ).entries.joinToString("\n") { "${it.key}=${it.value}" }
}
