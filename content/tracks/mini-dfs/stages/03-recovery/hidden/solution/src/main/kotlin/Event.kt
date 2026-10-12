sealed interface Event {
    val time: Long

    class Stale(override val time: Long, val node: DataNode) : Event

    class Dead(override val time: Long, val node: DataNode) : Event

    class Replicate(override val time: Long, val block: Block, val source: DataNode, val target: DataNode) : Event

    class Delete(override val time: Long, val block: Block, val node: DataNode) : Event

    class SafeModeOff(override val time: Long) : Event
}
