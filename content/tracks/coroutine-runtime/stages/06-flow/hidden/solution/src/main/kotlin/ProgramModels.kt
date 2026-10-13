sealed interface Command {
    val line: Int
    data class Log(val value: String, override val line: Int) : Command
    data class Delay(val millis: Long, override val line: Int) : Command
    data class Spawn(val definition: String, val name: String, val options: ContextOptions, override val line: Int) : Command
    data class Await(val name: String, override val line: Int) : Command
    data class Result(val value: String, override val line: Int) : Command
    data class State(val name: String, override val line: Int) : Command
    data class Start(val name: String, override val line: Int) : Command
    data class Join(val name: String, override val line: Int) : Command
    data class Scope(val definition: String, val name: String, val options: ContextOptions, override val line: Int) : Command
    data class Context(val key: String, override val line: Int) : Command
    data class Cancel(val name: String, override val line: Int) : Command
    data class Yield(override val line: Int) : Command
    data class Ensure(override val line: Int) : Command
    data class ShieldDelay(val millis: Long, override val line: Int) : Command
    data class OnComplete(val value: String, override val line: Int) : Command
    data class Timeout(val millis: Long, val definition: String, val name: String, override val line: Int) : Command
    data class Fail(val code: String, override val line: Int) : Command
    data class Supervisor(val definition: String, val name: String, val options: ContextOptions, override val line: Int) : Command
    data class AwaitCatch(val name: String, override val line: Int) : Command
    data class CancelSelf(override val line: Int) : Command
    data class SwallowCancel(override val line: Int) : Command
    data class Send(val channel: String, val value: String, override val line: Int) : Command
    data class Receive(val channel: String, override val line: Int) : Command
    data class Close(val channel: String, override val line: Int) : Command
    data class Lock(val mutex: String, override val line: Int) : Command
    data class Unlock(val mutex: String, override val line: Int) : Command
    data class Collect(val flow: String, val map: Int, val filter: FlowFilter, val take: Int, override val line: Int) : Command
    data class Emit(val value: Int, override val line: Int) : Command
}

enum class FlowFilter { ALL, EVEN, ODD }

data class FlowDefinition(
    val name: String,
    val commands: MutableList<Command> = mutableListOf(),
    val cleanup: MutableList<Command> = mutableListOf(),
    var hasFinally: Boolean = false,
)

data class ContextOptions(val lazy: Boolean = false, val tag: String? = null, val trace: String? = null)
data class TaskDefinition(
    val name: String,
    val async: Boolean,
    val commands: MutableList<Command> = mutableListOf(),
    val cleanup: MutableList<Command> = mutableListOf(),
    var hasFinally: Boolean = false,
)
data class RootDefinition(val definition: String, val name: String, val options: ContextOptions, val line: Int)
data class Program(
    val tasks: Map<String, TaskDefinition>,
    val roots: List<RootDefinition>,
    val channels: Map<String, Int>,
    val mutexes: Set<String>,
    val flows: Map<String, FlowDefinition>,
)
data class InputError(val line: Int, val code: String)

sealed interface ParseResult {
    data class Valid(val program: Program) : ParseResult
    data class Invalid(val error: InputError) : ParseResult
}
