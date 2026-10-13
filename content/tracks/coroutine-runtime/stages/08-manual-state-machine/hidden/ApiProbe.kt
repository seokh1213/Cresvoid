import kotlin.coroutines.AbstractCoroutineContextElement
import kotlin.coroutines.Continuation
import kotlin.coroutines.CoroutineContext
import kotlin.coroutines.EmptyCoroutineContext

private class ProbeElement : AbstractCoroutineContextElement(Key) {
    companion object Key : CoroutineContext.Key<ProbeElement>
}

private class RecordingCompletion(
    override val context: CoroutineContext = EmptyCoroutineContext,
) : Continuation<Int> {
    val results = mutableListOf<Result<Int>>()

    override fun resumeWith(result: Result<Int>) {
        results += result
    }
}

private class DeferredSteps : ManualSteps {
    var firstContinuation: Continuation<Int>? = null
    var secondContinuation: Continuation<Int>? = null
    var secondArgument: Int? = null
    var firstCalls = 0
    var secondCalls = 0

    override fun first(continuation: Continuation<Int>) {
        firstCalls++
        firstContinuation = continuation
    }

    override fun second(first: Int, continuation: Continuation<Int>) {
        secondCalls++
        secondArgument = first
        secondContinuation = continuation
    }
}

private fun expectIllegalState(action: () -> Unit) {
    var rejected = false
    try {
        action()
    } catch (_: IllegalStateException) {
        rejected = true
    }
    check(rejected) { "Expected IllegalStateException" }
}

private fun stagedResultsAndContext() {
    val context = EmptyCoroutineContext + ProbeElement()
    val completion = RecordingCompletion(context)
    val steps = DeferredSteps()
    val machine = ManualSumMachine(steps, completion)

    check(machine.context == context)
    machine.start()
    val first = checkNotNull(steps.firstContinuation)
    check(first.context == context)
    check(steps.secondCalls == 0 && completion.results.isEmpty())

    first.resumeWith(Result.success(7))
    val second = checkNotNull(steps.secondContinuation)
    check(steps.secondArgument == 7 && second.context == context)
    check(completion.results.isEmpty())

    second.resumeWith(Result.success(11))
    check(completion.results.size == 1)
    check(completion.results.single().getOrThrow() == 18)
}

private fun immediateResults() {
    val calls = mutableListOf<String>()
    val completion = RecordingCompletion()
    val steps = object : ManualSteps {
        override fun first(continuation: Continuation<Int>) {
            calls += "first"
            continuation.resumeWith(Result.success(6))
        }

        override fun second(first: Int, continuation: Continuation<Int>) {
            calls += "second:$first"
            continuation.resumeWith(Result.success(first * 3))
        }
    }

    ManualSumMachine(steps, completion).start()
    check(calls == listOf("first", "second:6"))
    check(completion.results.single().getOrThrow() == 24)
}

private fun failureIdentity() {
    val firstFailure = IllegalArgumentException("first")
    val firstSteps = DeferredSteps()
    val firstCompletion = RecordingCompletion()
    ManualSumMachine(firstSteps, firstCompletion).start()
    checkNotNull(firstSteps.firstContinuation).resumeWith(Result.failure(firstFailure))
    check(firstSteps.secondCalls == 0)
    check(firstCompletion.results.single().exceptionOrNull() === firstFailure)

    val secondFailure = IllegalArgumentException("second")
    val secondSteps = DeferredSteps()
    val secondCompletion = RecordingCompletion()
    ManualSumMachine(secondSteps, secondCompletion).start()
    checkNotNull(secondSteps.firstContinuation).resumeWith(Result.success(5))
    checkNotNull(secondSteps.secondContinuation).resumeWith(Result.failure(secondFailure))
    check(secondCompletion.results.single().exceptionOrNull() === secondFailure)
}

private fun invalidCallsPreservePendingWork() {
    val steps = DeferredSteps()
    val completion = RecordingCompletion()
    val machine = ManualSumMachine(steps, completion)

    expectIllegalState { machine.resumeWith(Result.success(1)) }
    machine.start()
    expectIllegalState { machine.start() }
    check(steps.firstCalls == 1 && steps.secondCalls == 0)

    val first = checkNotNull(steps.firstContinuation)
    machine.resumeWith(Result.success(4))
    check(steps.secondArgument == 4)
    expectIllegalState { first.resumeWith(Result.success(99)) }
    check(completion.results.isEmpty())

    val second = checkNotNull(steps.secondContinuation)
    second.resumeWith(Result.success(6))
    check(completion.results.single().getOrThrow() == 10)
    expectIllegalState { second.resumeWith(Result.success(8)) }
    expectIllegalState { machine.resumeWith(Result.success(8)) }
    expectIllegalState { machine.start() }
    check(completion.results.size == 1)

    val directSteps = DeferredSteps()
    val directCompletion = RecordingCompletion()
    val directMachine = ManualSumMachine(directSteps, directCompletion)
    directMachine.start()
    val directFirst = checkNotNull(directSteps.firstContinuation)
    directFirst.resumeWith(Result.success(2))
    expectIllegalState { directFirst.resumeWith(Result.success(99)) }
    val staleSecond = checkNotNull(directSteps.secondContinuation)
    directMachine.resumeWith(Result.success(3))
    expectIllegalState { staleSecond.resumeWith(Result.success(99)) }
    check(directCompletion.results.single().getOrThrow() == 5)
}

private fun separateMachines() {
    val leftSteps = DeferredSteps()
    val rightSteps = DeferredSteps()
    val leftCompletion = RecordingCompletion()
    val rightCompletion = RecordingCompletion()
    val left = ManualSumMachine(leftSteps, leftCompletion)
    val right = ManualSumMachine(rightSteps, rightCompletion)

    left.start()
    right.start()
    checkNotNull(rightSteps.firstContinuation).resumeWith(Result.success(2))
    checkNotNull(leftSteps.firstContinuation).resumeWith(Result.success(10))
    check(leftSteps.secondArgument == 10 && rightSteps.secondArgument == 2)
    checkNotNull(leftSteps.secondContinuation).resumeWith(Result.success(7))
    checkNotNull(rightSteps.secondContinuation).resumeWith(Result.success(8))
    check(leftCompletion.results.single().getOrThrow() == 17)
    check(rightCompletion.results.single().getOrThrow() == 10)
}

fun main() {
    stagedResultsAndContext()
    immediateResults()
    failureIdentity()
    invalidCallsPreservePendingWork()
    separateMachines()
}
