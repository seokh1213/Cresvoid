private const val CREATE = "CREATE"

class Session(private val output: Output) {
    private var table: Table? = null

    fun handle(line: String) {
        val words = line.trim(' ').split(' ').filter(String::isNotEmpty)
        if (words.isEmpty()) return
        val commandWord = words.first()
        val arguments = words.drop(1)
        if (commandWord == CREATE) return create(arguments)
        val parser = COMMAND_PARSERS[commandWord] ?: return output.error(ErrorCode.UNKNOWN_COMMAND)
        val table = table ?: return output.error(ErrorCode.NO_TABLE)
        val command = parser(arguments, table.schema) ?: return output.error(ErrorCode.BAD_ARGUMENT)
        table.execute(command)
    }

    private fun create(arguments: List<String>) {
        if (table != null) return output.error(ErrorCode.TABLE_EXISTS)
        val schema = parseSchema(arguments) ?: return output.error(ErrorCode.BAD_ARGUMENT)
        table = Table.create(schema, output)
    }
}
