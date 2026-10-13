private val namePattern = Regex("[a-z][a-z0-9_-]{0,31}")
private val integerPattern = Regex("0|[1-9][0-9]*")
private val failurePattern = Regex("[A-Z][A-Z0-9_]{0,31}")
private val signedPattern = Regex("0|-?[1-9][0-9]*")
class ProgramParser(private val lines: List<String>) {
    private val tasks = linkedMapOf<String, TaskDefinition>()
    private val roots = mutableListOf<RootDefinition>()
    private val channels = linkedMapOf<String, Int>()
    private val mutexes = linkedSetOf<String>()
    private val flows = linkedMapOf<String, FlowDefinition>()
    private val errors = mutableListOf<InputError>()
    private var current: TaskDefinition? = null
    private var currentFlow: FlowDefinition? = null
    private var readingRoots = false
    private var readingResources = false
    private var readingFlows = false
    private var ran = false

    fun parse(): ParseResult {
        lines.forEachIndexed { index, raw -> readLine(raw, index + 1) }
        val missingLine = lines.size + 1
        if (current != null || currentFlow != null || !ran) fail(missingLine, "FORMAT")
        if (tasks.isEmpty() || roots.isEmpty()) fail(lines.indexOfLast { it.trim(' ') == "RUN" }.takeIf { it >= 0 }?.plus(1) ?: missingLine, "FORMAT")
        validateReferences()
        validateCycles()
        val first = errors.minWithOrNull(compareBy<InputError>({ it.line }, { priority(it.code) }))
        return if (first == null) ParseResult.Valid(Program(tasks, roots, channels, mutexes, flows)) else ParseResult.Invalid(first)
    }

    private fun readLine(raw: String, line: Int) {
        val trimmed = raw.trim(' ')
        if (trimmed.isEmpty()) return
        if (trimmed.any { it.isWhitespace() && it != ' ' } || ran) {
            fail(line, "FORMAT")
            return
        }
        val words = trimmed.split(Regex(" +"))
        val task = current
        if (task != null) {
            if (words == listOf("END")) {
                checkResult(task, line)
                current = null
            } else if (words.first() == "FINALLY") {
                if (words.size != 1 || task.hasFinally) fail(line, "FORMAT")
                else task.hasFinally = true
            } else {
                readCommand(task, words, line)
            }
            return
        }
        val flow = currentFlow
        if (flow != null) {
            readFlowLine(flow, words, line)
            return
        }
        when (words.first()) {
            "TASK" -> {
                if (readingRoots || readingResources || readingFlows || words.size != 3 || !name(words[1]) || words[2] !in setOf("LAUNCH", "ASYNC")) {
                    fail(line, "FORMAT")
                } else if (words[1] in tasks) {
                    fail(line, "DUPLICATE")
                } else {
                    current = TaskDefinition(words[1], words[2] == "ASYNC").also { tasks[it.name] = it }
                }
            }
            "ROOT" -> {
                readingRoots = true
                if (tasks.isEmpty()) fail(line, "FORMAT")
                val options = readOptions(words.drop(3), false)
                if (words.size < 3 || !name(words[1]) || !name(words[2]) || options == null) fail(line, "FORMAT")
                else if (roots.any { it.name == words[2] }) fail(line, "DUPLICATE")
                else roots.add(RootDefinition(words[1], words[2], options, line))
            }
            "FLOW" -> {
                readingFlows = true
                if (tasks.isEmpty() || readingResources || readingRoots || words.size != 2 || !name(words[1])) fail(line, "FORMAT")
                else if (words[1] in flows) fail(line, "DUPLICATE")
                else currentFlow = FlowDefinition(words[1]).also { flows[it.name] = it }
            }
            "CHANNEL" -> {
                readingResources = true
                val capacity = words.getOrNull(2)?.takeIf(integerPattern::matches)?.toIntOrNull()
                if (tasks.isEmpty() || readingRoots || words.size != 3 || !name(words[1]) || capacity == null || capacity !in 0..10_000) fail(line, "FORMAT")
                else if (words[1] in channels) fail(line, "DUPLICATE")
                else channels[words[1]] = capacity
            }
            "MUTEX" -> {
                readingResources = true
                if (tasks.isEmpty() || readingRoots || words.size != 2 || !name(words[1])) fail(line, "FORMAT")
                else if (!mutexes.add(words[1])) fail(line, "DUPLICATE")
            }
            "RUN" -> if (words.size == 1) ran = true else fail(line, "FORMAT")
            else -> fail(line, "FORMAT")
        }
    }

    private fun readFlowLine(flow: FlowDefinition, words: List<String>, line: Int) {
        if (words == listOf("END")) {
            currentFlow = null
            return
        }
        if (words.first() == "FINALLY") {
            if (words.size != 1 || flow.hasFinally) fail(line, "FORMAT")
            else flow.hasFinally = true
            return
        }
        val command = when (words.first()) {
            "EMIT" -> if (words.size == 2 && signedPattern.matches(words[1])) {
                words[1].toIntOrNull()?.takeIf { it in -1_000_000_000..1_000_000_000 }?.let { Command.Emit(it, line) }
            } else null
            "LOG" -> if (words.size == 2 && name(words[1])) Command.Log(words[1], line) else null
            "DELAY", "SHIELD_DELAY" -> if (words.size == 2 && integerPattern.matches(words[1])) {
                words[1].toLongOrNull()?.takeIf { it <= 1_000_000_000 }?.let {
                    if (words[0] == "DELAY") Command.Delay(it, line) else Command.ShieldDelay(it, line)
                }
            } else null
            "FAIL" -> if (words.size == 2 && failurePattern.matches(words[1])) Command.Fail(words[1], line) else null
            else -> null
        }
        if (command == null) fail(line, "FORMAT")
        else if (flow.hasFinally) {
            if (command is Command.Log || command is Command.Delay || command is Command.ShieldDelay || command is Command.Fail) flow.cleanup.add(command)
            else fail(line, "FORMAT")
        } else if (command is Command.ShieldDelay) fail(line, "FORMAT")
        else flow.commands.add(command)
    }

    private fun readCommand(task: TaskDefinition, words: List<String>, line: Int) {
        val command = when (words.first()) {
            "LOG" -> if (words.size == 2 && name(words[1])) Command.Log(words[1], line) else null
            "DELAY" -> if (words.size == 2 && integerPattern.matches(words[1])) {
                words[1].toLongOrNull()?.takeIf { it <= 1_000_000_000 }?.let { Command.Delay(it, line) }
            } else null
            "SHIELD_DELAY" -> if (words.size == 2 && integerPattern.matches(words[1])) {
                words[1].toLongOrNull()?.takeIf { it <= 1_000_000_000 }?.let { Command.ShieldDelay(it, line) }
            } else null
            "SPAWN" -> {
                val options = readOptions(words.drop(3), true)
                if (words.size >= 3 && name(words[1]) && name(words[2]) && options != null) Command.Spawn(words[1], words[2], options, line) else null
            }
            "AWAIT" -> if (words.size == 2 && name(words[1])) Command.Await(words[1], line) else null
            "RESULT" -> if (words.size == 2 && name(words[1])) Command.Result(words[1], line) else null
            "STATE" -> if (words.size == 2 && name(words[1])) Command.State(words[1], line) else null
            "START" -> if (words.size == 2 && name(words[1])) Command.Start(words[1], line) else null
            "JOIN" -> if (words.size == 2 && name(words[1])) Command.Join(words[1], line) else null
            "SCOPE" -> {
                val options = readOptions(words.drop(3), false)
                if (words.size >= 3 && name(words[1]) && name(words[2]) && options != null) Command.Scope(words[1], words[2], options, line) else null
            }
            "CONTEXT" -> if (words.size == 2 && words[1] in setOf("tag", "trace")) Command.Context(words[1], line) else null
            "CANCEL" -> if (words.size == 2 && name(words[1])) Command.Cancel(words[1], line) else null
            "YIELD" -> if (words.size == 1) Command.Yield(line) else null
            "ENSURE" -> if (words.size == 1) Command.Ensure(line) else null
            "ON_COMPLETE" -> if (words.size == 2 && name(words[1])) Command.OnComplete(words[1], line) else null
            "TIMEOUT" -> if (words.size == 4 && integerPattern.matches(words[1]) && name(words[2]) && name(words[3])) {
                words[1].toLongOrNull()?.takeIf { it in 1..1_000_000_000 }?.let { Command.Timeout(it, words[2], words[3], line) }
            } else null
            "FAIL" -> if (words.size == 2 && failurePattern.matches(words[1])) Command.Fail(words[1], line) else null
            "SUPERVISOR" -> {
                val options = readOptions(words.drop(3), false)
                if (words.size >= 3 && name(words[1]) && name(words[2]) && options != null) Command.Supervisor(words[1], words[2], options, line) else null
            }
            "AWAIT_CATCH" -> if (words.size == 2 && name(words[1])) Command.AwaitCatch(words[1], line) else null
            "CANCEL_SELF" -> if (words.size == 1) Command.CancelSelf(line) else null
            "SWALLOW_CANCEL" -> if (words.size == 1) Command.SwallowCancel(line) else null
            "SEND" -> if (words.size == 3 && name(words[1]) && name(words[2])) Command.Send(words[1], words[2], line) else null
            "RECV" -> if (words.size == 2 && name(words[1])) Command.Receive(words[1], line) else null
            "CLOSE" -> if (words.size == 2 && name(words[1])) Command.Close(words[1], line) else null
            "LOCK" -> if (words.size == 2 && name(words[1])) Command.Lock(words[1], line) else null
            "UNLOCK" -> if (words.size == 2 && name(words[1])) Command.Unlock(words[1], line) else null
            "COLLECT" -> parseCollect(words, line)
            else -> null
        }
        if (command == null) {
            fail(line, "FORMAT")
        } else if (task.hasFinally) {
            if (command is Command.Log || command is Command.Delay || command is Command.ShieldDelay || command is Command.Fail || command is Command.Close || command is Command.Unlock) task.cleanup.add(command)
            else fail(line, "FORMAT")
        } else if (command is Command.ShieldDelay) {
            fail(line, "FORMAT")
        } else {
            task.commands.add(command)
        }
    }

    private fun parseCollect(words: List<String>, line: Int): Command.Collect? {
        if (words.size < 2 || !name(words[1])) return null
        var index = 2
        var mapping = 0
        var filter = FlowFilter.ALL
        var take = 0
        if (words.getOrNull(index) == "MAP") {
            val value = words.getOrNull(index + 1)?.takeIf(signedPattern::matches)?.toIntOrNull() ?: return null
            if (value !in -1_000_000..1_000_000) return null
            mapping = value
            index += 2
        }
        if (words.getOrNull(index) == "FILTER") {
            val value = words.getOrNull(index + 1) ?: return null
            if (value !in setOf("ALL", "EVEN", "ODD")) return null
            filter = FlowFilter.valueOf(value)
            index += 2
        }
        if (words.getOrNull(index) == "TAKE") {
            val value = words.getOrNull(index + 1)?.takeIf(integerPattern::matches)?.toIntOrNull() ?: return null
            if (value !in 0..1_000_000) return null
            take = value
            index += 2
        }
        return if (index == words.size) Command.Collect(words[1], mapping, filter, take, line) else null
    }

    private fun readOptions(words: List<String>, lazyAllowed: Boolean): ContextOptions? {
        var index = 0
        var lazy = false
        if (lazyAllowed && words.firstOrNull() == "LAZY") {
            lazy = true
            index++
        }
        var tag: String? = null
        var trace: String? = null
        if (words.getOrNull(index)?.startsWith("tag=") == true) {
            tag = words[index++].substringAfter('=')
            if (!name(tag)) return null
        }
        if (words.getOrNull(index)?.startsWith("trace=") == true) {
            trace = words[index++].substringAfter('=')
            if (!name(trace)) return null
        }
        return if (index == words.size) ContextOptions(lazy, tag, trace) else null
    }

    private fun checkResult(task: TaskDefinition, endLine: Int) {
        val results = task.commands.filterIsInstance<Command.Result>()
        if (!task.async) results.forEach { fail(it.line, "FORMAT") }
        else {
            if (results.isEmpty()) fail(endLine, "FORMAT")
            results.drop(1).forEach { fail(it.line, "FORMAT") }
            results.firstOrNull()?.takeIf { it != task.commands.last() }?.let { fail(it.line, "FORMAT") }
        }
    }

    private fun validateReferences() {
        for (root in roots) {
            val task = tasks[root.definition]
            if (task == null) fail(root.line, "UNKNOWN")
            else if (task.async) fail(root.line, "FORMAT")
        }
        for (task in tasks.values) {
            val children = mutableMapOf<String, String>()
            val lazyChildren = mutableMapOf<String, Command.Spawn>()
            for (command in task.commands) when (command) {
                is Command.Spawn -> {
                    if (command.name in children) fail(command.line, "DUPLICATE")
                    else children[command.name] = command.definition
                    if (command.options.lazy) lazyChildren[command.name] = command
                    if (command.definition !in tasks) fail(command.line, "UNKNOWN")
                }
                is Command.Scope -> {
                    if (command.name in children) fail(command.line, "DUPLICATE")
                    else children[command.name] = command.definition
                    val target = tasks[command.definition]
                    if (target == null) fail(command.line, "UNKNOWN")
                    else if (target.async) fail(command.line, "FORMAT")
                }
                is Command.Timeout -> {
                    if (command.name in children) fail(command.line, "DUPLICATE")
                    else children[command.name] = command.definition
                    val target = tasks[command.definition]
                    if (target == null) fail(command.line, "UNKNOWN")
                    else if (target.async) fail(command.line, "FORMAT")
                }
                is Command.Supervisor -> {
                    if (command.name in children) fail(command.line, "DUPLICATE")
                    else children[command.name] = command.definition
                    val target = tasks[command.definition]
                    if (target == null) fail(command.line, "UNKNOWN")
                    else if (target.async) fail(command.line, "FORMAT")
                }
                is Command.Await -> {
                    val childDefinition = children[command.name]
                    if (childDefinition == null || tasks[childDefinition]?.async == false) fail(command.line, "REFERENCE")
                    lazyChildren.remove(command.name)
                }
                is Command.AwaitCatch -> {
                    val childDefinition = children[command.name]
                    if (childDefinition == null || tasks[childDefinition]?.async == false) fail(command.line, "REFERENCE")
                    lazyChildren.remove(command.name)
                }
                is Command.Start -> { if (command.name !in children) fail(command.line, "REFERENCE"); lazyChildren.remove(command.name) }
                is Command.Join -> { if (command.name !in children) fail(command.line, "REFERENCE"); lazyChildren.remove(command.name) }
                is Command.Cancel -> { if (command.name !in children) fail(command.line, "REFERENCE"); lazyChildren.remove(command.name) }
                is Command.State -> if (command.name !in children) fail(command.line, "REFERENCE")
                else -> Unit
            }
            lazyChildren.values.forEach { fail(it.line, "REFERENCE") }
            for (command in task.commands + task.cleanup) when (command) {
                is Command.Send -> if (command.channel !in channels) fail(command.line, "UNKNOWN")
                is Command.Receive -> if (command.channel !in channels) fail(command.line, "UNKNOWN")
                is Command.Close -> if (command.channel !in channels) fail(command.line, "UNKNOWN")
                is Command.Lock -> if (command.mutex !in mutexes) fail(command.line, "UNKNOWN")
                is Command.Unlock -> if (command.mutex !in mutexes) fail(command.line, "UNKNOWN")
                is Command.Collect -> if (command.flow !in flows) fail(command.line, "UNKNOWN")
                else -> Unit
            }
        }
    }

    private fun validateCycles() {
        val edges = mutableMapOf<String, MutableList<String>>()
        data class Edge(val source: String, val target: String, val line: Int)
        val references = tasks.values.flatMap { task ->
            task.commands.mapNotNull { command -> when (command) {
                is Command.Spawn -> Edge(task.name, command.definition, command.line)
                is Command.Scope -> Edge(task.name, command.definition, command.line)
                is Command.Timeout -> Edge(task.name, command.definition, command.line)
                is Command.Supervisor -> Edge(task.name, command.definition, command.line)
                else -> null
            } }
        }.sortedBy { it.line }
        for (edge in references) {
            if (edge.target !in tasks) continue
            if (reachable(edge.target, edge.source, edges)) fail(edge.line, "CYCLE")
            edges.getOrPut(edge.source) { mutableListOf() }.add(edge.target)
        }
    }

    private fun reachable(start: String, destination: String, edges: Map<String, List<String>>): Boolean {
        val stack = ArrayDeque<String>()
        val seen = mutableSetOf<String>()
        stack.add(start)
        while (stack.isNotEmpty()) {
            val node = stack.removeLast()
            if (node == destination) return true
            if (seen.add(node)) edges[node]?.forEach(stack::addLast)
        }
        return false
    }

    private fun fail(line: Int, code: String) { errors.add(InputError(line, code)) }
    private fun name(value: String): Boolean = namePattern.matches(value)
    private fun priority(code: String): Int = listOf("FORMAT", "DUPLICATE", "UNKNOWN", "REFERENCE", "CYCLE").indexOf(code)
}
