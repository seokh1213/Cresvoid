class Namenode(private val print: (String) -> Unit) {
    private val namespace = Namespace()

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
            is Command.CreateFile -> namespace.createFile(command.path, command.size, command.replication)
            is Command.Remove -> namespace.remove(command.path)
            is Command.Move -> namespace.move(command.source, command.target)
            is Command.SetQuota -> namespace.setQuota(command.path, command.nameQuota, command.spaceQuota)
            is Command.ListEntries -> listingLines(namespace.find(command.path)).forEach(print)
            is Command.Count -> print(countLine(namespace.find(command.path)))
            is Command.ShowBlocks -> blockLines(namespace.findFile(command.path)).forEach(print)
        }
    }
}
