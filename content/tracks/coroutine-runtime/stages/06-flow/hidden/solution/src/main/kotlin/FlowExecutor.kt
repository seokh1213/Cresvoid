class FlowExecutor(
    private val runtime: VirtualRuntime,
    private val definitions: Map<String, FlowDefinition>,
) {
    suspend fun collect(scope: RuntimeScope, command: Command.Collect) {
        scope.ensureActive()
        val definition = definitions.getValue(command.flow)
        val source = scope.flow<Int> { executeDefinition(scope, definition, this) }
        val mapped = source.map { it + command.map }
        val filtered = mapped.filter { value -> when (command.filter) {
            FlowFilter.ALL -> true
            FlowFilter.EVEN -> value % 2 == 0
            FlowFilter.ODD -> value % 2 != 0
        } }
        val limited = filtered.take(command.take)
        limited.collect { value -> runtime.record(scope.context, "FLOW ${command.flow} $value") }
        scope.ensureActive()
    }

    private suspend fun executeDefinition(scope: RuntimeScope, definition: FlowDefinition, emitter: FlowEmitter<Int>) {
        var pending: Exception? = null
        try {
            for (command in definition.commands) when (command) {
                is Command.Log -> runtime.record(scope.context, "FLOWLOG ${definition.name} ${command.value}")
                is Command.Delay -> scope.delay(command.millis)
                is Command.Emit -> { scope.ensureActive(); emitter.emit(command.value) }
                is Command.Fail -> throw JobFailure(command.code)
                else -> error("Invalid Flow command")
            }
        } catch (failure: Exception) {
            if (failure is JobFailure) reportFailure(scope, failure)
            pending = failure
        }
        try {
            for (command in definition.cleanup) when (command) {
                is Command.Log -> runtime.record(scope.context, "FLOWLOG ${definition.name} ${command.value}")
                is Command.Delay -> scope.delay(command.millis)
                is Command.ShieldDelay -> scope.shieldDelay(command.millis)
                is Command.Fail -> throw JobFailure(command.code)
                else -> error("Invalid Flow cleanup")
            }
        } catch (failure: Exception) {
            if (failure is JobFailure) reportFailure(scope, failure)
            if (pending !is JobFailure) pending = failure
        }
        pending?.let { throw it }
    }

    private fun reportFailure(scope: RuntimeScope, failure: JobFailure) {
        (scope.job as TaskJob<*>).fail(failure.code)
        failure.reported = true
    }
}
