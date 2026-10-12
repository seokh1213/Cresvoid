import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.runTest
import kotlin.coroutines.cancellation.CancellationException

@OptIn(ExperimentalCoroutinesApi::class)
class VirtualTime private constructor(private val test: TestScope) : CoroutineScope by test {
    fun now(): Long = test.testScheduler.currentTime

    fun runUntilIdle() {
        test.testScheduler.advanceUntilIdle()
    }

    companion object {
        fun <T> simulate(block: suspend VirtualTime.() -> T): T {
            val outcomes = ArrayList<T>(1)
            runTest { outcomes += VirtualTime(this).block() }
            return outcomes.single()
        }
    }
}

sealed interface Ending<out T> {
    val time: Long

    data class Returned<T>(val value: T, override val time: Long) : Ending<T>

    data class Cancelled(override val time: Long) : Ending<Nothing>

    data class Stuck(override val time: Long) : Ending<Nothing>
}

fun <T> VirtualTime.runToEnd(cancelAt: Long? = null, work: suspend () -> T): Ending<T> {
    var ending: Ending<T>? = null
    val job = launch(start = CoroutineStart.LAZY) {
        try {
            val value = work()
            ending = Ending.Returned(value, now())
        } catch (cancellation: CancellationException) {
            ending = Ending.Cancelled(now())
            throw cancellation
        }
    }
    // 취소 타이머를 학습자 코드보다 먼저 등록해야 같은 시각의 응답보다 취소가 먼저 일어난다.
    if (cancelAt != null) {
        launch {
            delay(cancelAt)
            job.cancel()
        }
    }
    job.start()
    runUntilIdle()
    if (job.isActive) {
        val stuckAt = now()
        job.cancel()
        runUntilIdle()
        return Ending.Stuck(stuckAt)
    }
    return checkNotNull(ending) { "학습자 함수가 취소 예외가 아닌 예외로 끝났다" }
}
