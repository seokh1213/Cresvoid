class SequenceExecutor(
    private val runtime: VirtualRuntime,
    private val definitions: Map<String, SequenceDefinition>,
) {
    fun iterator(scope: RuntimeScope, name: String): Iterator<Int> {
        val definition = definitions.getValue(name)
        return pullSequence<Int> {
            for (command in definition.commands) when (command) {
                is Command.Log -> runtime.record(scope.context, "SEQLOG $name ${command.value}")
                is Command.SequenceYield -> yield(command.value)
                else -> error("Invalid Sequence command")
            }
        }.iterator()
    }
}
