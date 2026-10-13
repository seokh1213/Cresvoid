import java.util.PriorityQueue
import kotlin.coroutines.Continuation
import kotlin.coroutines.CoroutineContext
import kotlin.coroutines.EmptyCoroutineContext

fun runManualInput(lines: List<String>): String = when (val parsed = ManualInputParser(lines).parse()) {
    is ManualParseResult.Invalid -> "ERR ${parsed.error.line} ${parsed.error.code}\n"
    is ManualParseResult.Valid -> ManualRunner(parsed.declarations).run()
}

private data class ManualTimer(val at: Long, val order: Long, val action: () -> Unit)

class ManualRunner(private val declarations: List<ManualDeclaration>) {
    private val ready = ArrayDeque<() -> Unit>()
    private val timers = PriorityQueue(compareBy<ManualTimer>({ it.at }, { it.order }))
    private val output = StringBuilder()
    private var nextOrder = 0L
    private var now = 0L

    fun run(): String {
        declarations.forEach { declaration -> start(declaration) }
        while (ready.isNotEmpty() || timers.isNotEmpty()) {
            if (ready.isEmpty()) advance()
            ready.removeFirst().invoke()
        }
        return output.toString()
    }

    private fun start(declaration: ManualDeclaration) {
        val name = declaration.name
        val steps = object : ManualSteps {
            override fun first(continuation: Continuation<Int>) {
                schedule(declaration.firstDelay) {
                    if (declaration.outcome == ManualOutcome.FAIL_FIRST) {
                        record(name, "FAILED FIRST")
                        continuation.resumeWith(Result.failure(IllegalStateException("FIRST")))
                    } else {
                        record(name, "FIRST ${declaration.first}")
                        continuation.resumeWith(Result.success(declaration.first))
                    }
                }
            }

            override fun second(first: Int, continuation: Continuation<Int>) {
                schedule(declaration.secondDelay) {
                    if (declaration.outcome == ManualOutcome.FAIL_SECOND) {
                        record(name, "FAILED SECOND")
                        continuation.resumeWith(Result.failure(IllegalStateException("SECOND")))
                    } else {
                        val product = first * declaration.multiplier
                        record(name, "SECOND $product")
                        continuation.resumeWith(Result.success(product))
                    }
                }
            }
        }
        val completion = object : Continuation<Int> {
            override val context: CoroutineContext = EmptyCoroutineContext

            override fun resumeWith(result: Result<Int>) {
                result.onSuccess { record(name, "DONE $it") }
            }
        }
        record(name, "START")
        ManualSumMachine(steps, completion).start()
    }

    private fun schedule(delay: Long, action: () -> Unit) {
        if (delay == 0L) ready.addLast(action)
        else timers.add(ManualTimer(now + delay, nextOrder++, action))
    }

    private fun advance() {
        now = timers.peek().at
        do {
            ready.addLast(timers.remove().action)
        } while (timers.peek()?.at == now)
    }

    private fun record(name: String, event: String) {
        output.append(now).append(' ').append(name).append(' ').append(event).append('\n')
    }
}
