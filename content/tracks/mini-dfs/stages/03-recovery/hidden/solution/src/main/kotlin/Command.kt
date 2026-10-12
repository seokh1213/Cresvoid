sealed interface Command {
    sealed interface NamespaceChange : Command

    data class MakeDirectory(val path: DfsPath) : NamespaceChange

    data class CreateFile(val path: DfsPath, val size: Long, val replication: Int, val writer: String?) : NamespaceChange

    data class Remove(val path: DfsPath) : NamespaceChange

    data class Move(val source: DfsPath, val target: DfsPath) : NamespaceChange

    data class SetQuota(val path: DfsPath, val nameQuota: Long?, val spaceQuota: Long?) : NamespaceChange

    data class ListEntries(val path: DfsPath) : Command

    data class Count(val path: DfsPath) : Command

    data class ShowBlocks(val path: DfsPath) : Command

    data class RegisterNode(val node: String, val rack: String, val capacity: Long) : Command

    data class Locate(val path: DfsPath) : Command

    data class ShowNode(val node: String) : Command

    data object Fsck : Command

    data class Tick(val seconds: Long) : Command

    data class StopNode(val node: String) : Command

    data class StartNode(val node: String) : Command

    data object Restart : Command

    data object ShowSafeMode : Command
}
