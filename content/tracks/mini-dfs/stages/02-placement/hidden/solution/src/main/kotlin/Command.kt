sealed interface Command {
    data class MakeDirectory(val path: DfsPath) : Command

    data class CreateFile(val path: DfsPath, val size: Long, val replication: Int, val writer: String?) : Command

    data class Remove(val path: DfsPath) : Command

    data class Move(val source: DfsPath, val target: DfsPath) : Command

    data class SetQuota(val path: DfsPath, val nameQuota: Long?, val spaceQuota: Long?) : Command

    data class ListEntries(val path: DfsPath) : Command

    data class Count(val path: DfsPath) : Command

    data class ShowBlocks(val path: DfsPath) : Command

    data class RegisterNode(val node: String, val rack: String, val capacity: Long) : Command

    data class Locate(val path: DfsPath) : Command

    data class ShowNode(val node: String) : Command

    data object Fsck : Command
}
