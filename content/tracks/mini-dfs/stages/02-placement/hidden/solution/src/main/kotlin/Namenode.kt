class Namenode(private val print: (String) -> Unit) {
    private val namespace = Namespace()
    private val cluster = Cluster()
    private val blockManager = BlockManager(cluster)

    fun handle(request: String) {
        try {
            execute(parseCommand(request))
        } catch (failure: CommandFailure) {
            print(failure.line)
        }
    }

    private fun execute(command: Command) {
        when (command) {
            is Command.MakeDirectory -> namespace.makeDirectory(command.path)
            is Command.CreateFile -> createFile(command)
            is Command.Remove -> remove(command.path)
            is Command.Move -> namespace.move(command.source, command.target)
            is Command.SetQuota -> namespace.setQuota(command.path, command.nameQuota, command.spaceQuota)
            is Command.ListEntries -> listingLines(namespace.find(command.path)).forEach(print)
            is Command.Count -> print(countLine(namespace.find(command.path)))
            is Command.ShowBlocks -> blockLines(namespace.findFile(command.path)).forEach(print)
            is Command.RegisterNode -> cluster.register(command.node, command.rack, command.capacity)
            is Command.Locate -> locationLines(namespace.findFile(command.path)).forEach(print)
            is Command.ShowNode -> print(nodeLine(cluster.find(command.node)))
            Command.Fsck -> print(fsckLine(blockManager.health))
        }
    }

    private fun createFile(command: Command.CreateFile) {
        val writer = command.writer?.let(cluster::find)
        val file = namespace.createFile(command.path, command.size, command.replication)
        blockManager.placeNewBlocks(file.blocks, writer)
    }

    private fun remove(path: DfsPath) {
        val removed = namespace.remove(path)
        blockManager.removeBlocks(removed.files().flatMap { it.blocks })
    }
}
