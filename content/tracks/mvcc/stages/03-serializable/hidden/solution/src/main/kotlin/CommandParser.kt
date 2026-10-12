private val TRANSACTION_NAME = Regex("[a-z][a-z0-9]{0,15}")
private val KEY_OR_VALUE = Regex("[a-z0-9]{1,16}")

private val ISOLATION_WORDS = mapOf(
    emptyList<String>() to IsolationLevel.READ_COMMITTED,
    listOf("READ", "COMMITTED") to IsolationLevel.READ_COMMITTED,
    listOf("REPEATABLE", "READ") to IsolationLevel.REPEATABLE_READ,
    listOf("SERIALIZABLE") to IsolationLevel.SERIALIZABLE,
)

private val LOCK_CLAUSES: Map<List<String>, LockMode?> = mapOf(
    emptyList<String>() to null,
    listOf("FOR", "SHARE") to LockMode.SHARED,
    listOf("FOR", "UPDATE") to LockMode.EXCLUSIVE,
)

sealed interface ParsedLine {
    data class Valid(val command: Command) : ParsedLine

    data class Invalid(val error: SyntaxError) : ParsedLine
}

fun parseLine(line: String): ParsedLine {
    val words = line.split(' ').filter(String::isNotEmpty)
    val arguments = words.drop(1)
    val command = when (words.first()) {
        "BEGIN" -> parseBegin(arguments)
        "READ" -> parseRead(arguments)
        "WRITE" -> parseWrite(arguments)
        "SCAN" -> parseScan(arguments)
        "COMMIT" -> parseTransactionOnly(arguments, Command::Commit)
        "ROLLBACK" -> parseTransactionOnly(arguments, Command::Rollback)
        else -> return ParsedLine.Invalid(SyntaxError.UNKNOWN_COMMAND)
    }
    return if (command == null) ParsedLine.Invalid(SyntaxError.BAD_ARGUMENT) else ParsedLine.Valid(command)
}

private fun parseBegin(arguments: List<String>): Command.Begin? {
    val transaction = arguments.firstOrNull()?.takeIf(TRANSACTION_NAME::matches) ?: return null
    val isolation = ISOLATION_WORDS[arguments.drop(1)] ?: return null
    return Command.Begin(transaction, isolation)
}

private fun parseRead(arguments: List<String>): Command.Read? {
    if (arguments.size < 2) return null
    val (transaction, key) = arguments
    val lockClause = arguments.drop(2)
    if (!TRANSACTION_NAME.matches(transaction) || !KEY_OR_VALUE.matches(key) || lockClause !in LOCK_CLAUSES) return null
    return Command.Read(transaction, key, LOCK_CLAUSES.getValue(lockClause))
}

private fun parseWrite(arguments: List<String>): Command.Write? {
    if (arguments.size != 3) return null
    val (transaction, key, value) = arguments
    if (!TRANSACTION_NAME.matches(transaction) || !KEY_OR_VALUE.matches(key) || !KEY_OR_VALUE.matches(value)) return null
    return Command.Write(transaction, key, value)
}

private fun parseScan(arguments: List<String>): Command.Scan? {
    if (arguments.size < 3) return null
    val (transaction, start, end) = arguments
    val lockClause = arguments.drop(3)
    if (!TRANSACTION_NAME.matches(transaction) || !KEY_OR_VALUE.matches(start) || !KEY_OR_VALUE.matches(end)) return null
    if (start >= end || lockClause !in LOCK_CLAUSES) return null
    return Command.Scan(transaction, KeyRange(start, end), LOCK_CLAUSES.getValue(lockClause))
}

private fun parseTransactionOnly(arguments: List<String>, build: (String) -> Command.Statement): Command.Statement? {
    val transaction = arguments.singleOrNull()?.takeIf(TRANSACTION_NAME::matches) ?: return null
    return build(transaction)
}
