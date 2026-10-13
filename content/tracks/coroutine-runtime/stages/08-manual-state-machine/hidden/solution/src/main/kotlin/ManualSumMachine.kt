import kotlin.coroutines.Continuation
import kotlin.coroutines.CoroutineContext

interface ManualSteps {
    fun first(continuation: Continuation<Int>)
    fun second(first: Int, continuation: Continuation<Int>)
}

class ManualSumMachine(
    private val steps: ManualSteps,
    private val completion: Continuation<Int>,
) : Continuation<Int> {
    private enum class Phase { NEW, FIRST, SECOND, DONE }

    private var phase = Phase.NEW
    private var firstValue = 0
    private var active: StepContinuation? = null

    override val context: CoroutineContext
        get() = completion.context

    fun start() {
        val first = synchronized(this) {
            check(phase == Phase.NEW) { "Machine already started" }
            phase = Phase.FIRST
            StepContinuation(Phase.FIRST).also { active = it }
        }
        steps.first(first)
    }

    override fun resumeWith(result: Result<Int>) {
        val waiting = synchronized(this) {
            active ?: throw IllegalStateException("Machine is not waiting")
        }
        accept(waiting, result)
    }

    private fun accept(step: StepContinuation, result: Result<Int>) {
        val next: () -> Unit = synchronized(this) {
            check(active === step && phase == step.phase && !step.used) { "Step already resumed" }
            step.used = true
            when {
                result.isFailure -> {
                    phase = Phase.DONE
                    active = null
                    { completion.resumeWith(result) }
                }
                phase == Phase.FIRST -> {
                    val first = result.getOrThrow()
                    firstValue = first
                    phase = Phase.SECOND
                    val second = StepContinuation(Phase.SECOND)
                    active = second
                    { steps.second(first, second) }
                }
                else -> {
                    val sum = firstValue + result.getOrThrow()
                    phase = Phase.DONE
                    active = null
                    { completion.resumeWith(Result.success(sum)) }
                }
            }
        }
        next()
    }

    private inner class StepContinuation(val phase: Phase) : Continuation<Int> {
        var used = false

        override val context: CoroutineContext
            get() = this@ManualSumMachine.context

        override fun resumeWith(result: Result<Int>) {
            accept(this, result)
        }
    }
}
