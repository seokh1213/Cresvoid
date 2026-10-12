sealed interface Command {
    data class CreateNode(val node: String, val electionTimeouts: List<Long>) : Command

    data class Tick(val ticks: Long) : Command

    data class SetDelay(val sender: String, val receiver: String, val ticks: Long) : Command

    data class SetDrop(val sender: String, val receiver: String, val count: Long) : Command

    data class Partition(val groups: List<List<String>>) : Command

    data object Heal : Command

    data class ShowState(val node: String) : Command
}
