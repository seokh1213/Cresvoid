sealed interface Command {
    sealed interface TransactionCommand : Command {
        val transaction: String
    }

    data class Begin(val transaction: String) : Command

    data class Append(override val transaction: String, val file: String, val rows: List<Row>) : TransactionCommand

    data class Remove(override val transaction: String, val file: String) : TransactionCommand

    data class Commit(override val transaction: String) : TransactionCommand

    data object ListSnapshots : Command

    data class ListManifests(val snapshot: String) : Command

    data class ListEntries(val manifest: String) : Command

    data object ListObjects : Command

    data class Scan(val snapshot: String?, val conditions: List<Condition>) : Command
}
