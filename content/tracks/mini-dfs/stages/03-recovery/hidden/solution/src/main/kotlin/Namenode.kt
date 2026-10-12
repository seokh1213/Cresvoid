private const val REDUNDANCY_INTERVAL = 3L

class Namenode(private val print: (String) -> Unit) {
    private val clock = Clock()
    private val namespace = Namespace()
    private val cluster = Cluster(clock)
    private val blockManager = BlockManager(cluster, clock)
    private val heartbeats = HeartbeatMonitor(cluster, blockManager, clock)
    private val safeMode = SafeMode()

    fun handle(request: String) {
        try {
            execute(parseCommand(request))
        } catch (failure: CommandFailure) {
            print(failure.line)
        }
    }

    private fun execute(command: Command) {
        if (command is Command.NamespaceChange && safeMode.isOn) reject(ErrorCode.SAFE_MODE)
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
            is Command.ShowNode -> showNode(cluster.find(command.node))
            Command.Fsck -> print(fsckLine(blockManager.health))
            is Command.Tick -> tick(command.seconds)
            is Command.StopNode -> cluster.stop(cluster.find(command.node))
            is Command.StartNode -> start(cluster.find(command.node))
            Command.Restart -> restart()
            Command.ShowSafeMode -> print(safeModeLine(safeMode.state, blockManager.health))
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

    private fun showNode(node: DataNode) {
        print(nodeLine(node, cluster.stateOf(node)))
    }

    private fun tick(seconds: Long) {
        val end = clock.now + seconds
        while (clock.now < end) {
            clock.advanceTo(clock.now + 1)
            publish(heartbeats.detectStaleNodes())
            if (safeMode.exitsAt(clock.now)) leaveSafeMode()
            if (clock.now % RECHECK_INTERVAL == 0L && !safeMode.isOn) publish(heartbeats.markDeadNodes())
            if (clock.now % REDUNDANCY_INTERVAL == 0L && !safeMode.isOn) publish(blockManager.runReplicationRound())
        }
    }

    private fun leaveSafeMode() {
        safeMode.leave()
        publish(listOf(Event.SafeModeOff(clock.now)))
        publish(blockManager.deleteExcessReplicas(blockManager.health.overReplicatedBlocks()))
    }

    private fun start(node: DataNode) {
        cluster.start(node)
        blockManager.receiveBlockReport(node)
        if (safeMode.isOn) {
            safeMode.checkThreshold(clock.now, blockManager.health)
        } else {
            publish(blockManager.deleteExcessReplicas(blockManager.overReplicatedBlocksOn(node)))
        }
    }

    private fun restart() {
        safeMode.enter()
        blockManager.forgetAllAndReceiveReportsFromRunningNodes(namespace.allBlocks())
        safeMode.checkThreshold(clock.now, blockManager.health)
    }

    private fun publish(events: List<Event>) {
        events.forEach { print(eventLine(it)) }
    }
}
