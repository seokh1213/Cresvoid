import java.nio.charset.StandardCharsets
import java.util.concurrent.CancellationException
import kotlin.coroutines.CoroutineContext
import kotlin.coroutines.EmptyCoroutineContext
import kotlin.coroutines.resume
import kotlin.coroutines.suspendCoroutine

fun main() {
    val lines = System.`in`.bufferedReader(StandardCharsets.UTF_8).readLines()
    if (lines.firstOrNull { it.trim(' ').isNotEmpty() }?.trim(' ')?.startsWith("MANUAL") == true) {
        print(runManualInput(lines))
        return
    }
    when (val parsed = ProgramParser(lines).parse()) {
        is ParseResult.Invalid -> println("ERR ${parsed.error.line} ${parsed.error.code}")
        is ParseResult.Valid -> print(ProgramRunner(parsed.program).run())
    }
}

class ProgramRunner(private val program: Program) {
    private val output = StringBuilder()
    private val runtime = VirtualRuntime { time, path, event ->
        output.append(time).append(' ').append(path).append(' ').append(event).append('\n')
    }
    private val channels = program.channels.mapValues { VirtualChannel<String>(runtime, it.value) }
    private val mutexes = program.mutexes.associateWith { VirtualMutex(runtime) }
    private val flowExecutor = FlowExecutor(runtime, program.flows)
    private val sequenceExecutor = SequenceExecutor(runtime, program.sequences)

    fun run(): String {
        runtime.run {
            program.roots.forEach { root ->
                val task = program.tasks.getValue(root.definition)
                runtime.launchRoot(root.name, root.options.toContext()) { execute(task) }
            }
        }
        if (runtime.hasUnfinishedJobs()) output.append("DEADLOCK\n")
        return output.toString()
    }

    private suspend fun RuntimeScope.execute(task: TaskDefinition): String {
        val children = mutableMapOf<String, ChildHandle>()
        var result = ""
        try {
            ensureActive()
            result = executeBody(task, children)
        } catch (failure: JobFailure) {
            if (!failure.reported) (job as TaskJob<*>).fail(failure.code)
        } catch (_: CancellationException) {
            job.cancel()
        }
        try {
            for (command in task.cleanup) when (command) {
                is Command.Log -> runtime.record(context, "LOG ${command.value}")
                is Command.Delay -> delay(command.millis)
                is Command.ShieldDelay -> shieldDelay(command.millis)
                is Command.Fail -> throw JobFailure(command.code)
                is Command.Close -> channels.getValue(command.channel).close()
                is Command.Unlock -> mutexes.getValue(command.mutex).unlock()
                else -> error("Invalid cleanup command")
            }
        } catch (failure: JobFailure) {
            (job as TaskJob<*>).fail(failure.code)
        } catch (_: CancellationException) {
            job.cancel()
        }
        return result
    }

    private suspend fun RuntimeScope.executeBody(task: TaskDefinition, children: MutableMap<String, ChildHandle>): String {
        var result = ""
        val iterators = mutableMapOf<String, Iterator<Int>>()
        for (command in task.commands) when (command) {
            is Command.Log -> runtime.record(context, "LOG ${command.value}")
            is Command.Delay -> delay(command.millis)
            is Command.Spawn -> {
                val childTask = program.tasks.getValue(command.definition)
                val path = context[PathElement]!!.path + "." + command.name
                val start = if (command.options.lazy) StartMode.LAZY else StartMode.EAGER
                val addition = PathElement(path) + command.options.toContext()
                if (childTask.async) {
                    val deferred = async(start, addition) { execute(childTask) }
                    children[command.name] = ChildHandle(deferred, deferred)
                } else {
                    children[command.name] = ChildHandle(launch(start, addition) { execute(childTask) })
                }
            }
            is Command.Await -> {
                val value = children.getValue(command.name).deferred!!.await()
                runtime.record(context, "VALUE ${command.name} $value")
            }
            is Command.Result -> result = command.value
            is Command.State -> runtime.record(context, "STATE ${command.name} ${children.getValue(command.name).job.state}")
            is Command.Start -> children.getValue(command.name).job.start()
            is Command.Join -> children.getValue(command.name).job.join()
            is Command.Scope -> {
                val childTask = program.tasks.getValue(command.definition)
                val path = context[PathElement]!!.path + "." + command.name
                val child = launch(context = PathElement(path) + command.options.toContext()) { execute(childTask) }
                children[command.name] = ChildHandle(child)
                child.join()
            }
            is Command.Context -> {
                val value = when (command.key) {
                    "tag" -> context[TagElement]?.value
                    else -> context[TraceElement]?.value
                } ?: "-"
                runtime.record(context, "CONTEXT ${command.key} $value")
            }
            is Command.Cancel -> children.getValue(command.name).job.cancel()
            is Command.Yield -> yieldNow()
            is Command.Ensure -> ensureActive()
            is Command.OnComplete -> job.invokeOnCompletion { state ->
                val outcome = when (state) {
                    JobState.COMPLETED -> "DONE"
                    JobState.FAILED -> "FAILED ${(job as TaskJob<*>).failureCode}"
                    else -> "CANCELLED"
                }
                runtime.record(context, "HANDLER ${command.value} $outcome")
            }
            is Command.Timeout -> timeout(command, children)
            is Command.Fail -> throw JobFailure(command.code)
            is Command.Supervisor -> {
                ensureActive()
                val childTask = program.tasks.getValue(command.definition)
                val path = context[PathElement]!!.path + "." + command.name
                val addition = PathElement(path) + command.options.toContext() + SupervisorElement
                val child = launch(context = addition) { execute(childTask) }
                children[command.name] = ChildHandle(child)
                child.join()
            }
            is Command.AwaitCatch -> awaitCatching(command.name, children.getValue(command.name))
            is Command.CancelSelf -> job.cancel()
            is Command.SwallowCancel -> {
                val event = try { ensureActive(); "ACTIVE" } catch (_: CancellationException) { "CAUGHT_CANCEL" }
                runtime.record(context, event)
            }
            is Command.ShieldDelay -> error("Shielded delay belongs in FINALLY")
            is Command.Send -> channels.getValue(command.channel).send(command.value)
            is Command.Receive -> {
                val value = channels.getValue(command.channel).receive()
                runtime.record(context, "RECEIVE ${command.channel} $value")
            }
            is Command.Close -> channels.getValue(command.channel).close()
            is Command.Lock -> mutexes.getValue(command.mutex).lock()
            is Command.Unlock -> mutexes.getValue(command.mutex).unlock()
            is Command.Collect -> flowExecutor.collect(this, command)
            is Command.Emit -> error("EMIT belongs in Flow")
            is Command.Iterate -> iterators[command.name] = sequenceExecutor.iterator(this, command.sequence)
            is Command.HasNext -> runtime.record(context, "HAS_NEXT ${command.name} ${iterators.getValue(command.name).hasNext()}")
            is Command.Next -> {
                val iterator = iterators.getValue(command.name)
                val value = try { iterator.next() } catch (_: NoSuchElementException) { throw JobFailure("SEQUENCE_END") }
                runtime.record(context, "ITEM ${command.name} $value")
            }
            is Command.SequenceYield -> error("YIELD with value belongs in Sequence")
        }
        return result
    }

    private suspend fun RuntimeScope.awaitCatching(name: String, handle: ChildHandle) {
        try {
            val value = handle.deferred!!.await()
            runtime.record(context, "VALUE $name $value")
        } catch (failure: JobFailure) {
            if ((job as TaskJob<*>).isCancelling()) throw failure
            runtime.record(context, "CAUGHT $name FAILED ${failure.code}")
        } catch (cancelled: CancellationException) {
            if ((job as TaskJob<*>).isCancelling()) throw cancelled
            runtime.record(context, "CAUGHT $name CANCELLED -")
        }
    }

    private suspend fun RuntimeScope.timeout(command: Command.Timeout, children: MutableMap<String, ChildHandle>) {
        ensureActive()
        var expired = false
        lateinit var child: RuntimeJob
        val timer = runtime.schedule(command.millis) { expired = true; child.cancel() }
        val childTask = program.tasks.getValue(command.definition)
        val path = context[PathElement]!!.path + "." + command.name
        child = launch(context = PathElement(path)) { execute(childTask) }
        children[command.name] = ChildHandle(child)
        try {
            child.join()
        } catch (cancelled: CancellationException) {
            child.cancel()
            suspendCoroutine<Unit> { continuation ->
                child.invokeOnCompletion { runtime.enqueue { continuation.resume(Unit) } }
            }
            throw cancelled
        } finally {
            timer.cancel()
        }
        if (expired) runtime.record(context, "TIMEOUT ${command.name}")
    }
}

private data class ChildHandle(val job: RuntimeJob, val deferred: RuntimeDeferred<String>? = null)

private fun ContextOptions.toContext(): CoroutineContext {
    var context: CoroutineContext = EmptyCoroutineContext
    tag?.let { context += TagElement(it) }
    trace?.let { context += TraceElement(it) }
    return context
}
