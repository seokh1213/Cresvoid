import kotlin.time.Duration
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.runTest

@OptIn(ExperimentalCoroutinesApi::class)
fun main() {
    val lines = generateSequence(::readlnOrNull)
        .filter { it.isNotBlank() }
        .toList()
    if (lines.isEmpty()) return

    runTest(timeout = Duration.INFINITE) {
        val scheduler = testScheduler
        val dispatcher = when (lines.first().trim().split(Regex("\\s+")).last()) {
            "INLINE" -> Dispatchers.Unconfined
            else -> StandardTestDispatcher(scheduler)
        }
        val time = object : EventTime {
            override fun now(): Long = scheduler.currentTime

            override fun advanceBy(milliseconds: Long) {
                scheduler.advanceTimeBy(milliseconds)
                scheduler.runCurrent()
            }

            // Unconfined로 시작해도 대기는 가상 시간에 연결한다.
            override suspend fun pause(milliseconds: Long) {
                withContext(StandardTestDispatcher(scheduler)) {
                    delay(milliseconds)
                }
            }
        }
        val eventScope = CoroutineScope(coroutineContext + dispatcher)
        val manager = EventManager(eventScope, time)
        val commands = EventCommands(manager, time)
        println("READY")
        try {
            for (line in lines.drop(1)) {
                val response = commands.execute(line)
                scheduler.runCurrent()
                println(response)
            }
        } finally {
            manager.close()
        }
    }
}
