class Simulator(private val print: (String) -> Unit) {
    private val clock = Clock()
    private val network = Network(clock)
    private val nodesByName = LinkedHashMap<String, RaftNode>()

    fun handle(line: String) {
        try {
            execute(parseCommand(line))
        } catch (failure: CommandFailure) {
            print(failure.line)
        }
    }

    private fun execute(command: Command) {
        when (command) {
            is Command.CreateNode -> createNode(command.node, command.electionTimeouts)
            is Command.Tick -> tick(command.ticks)
            is Command.SetDelay -> linkBetween(command.sender, command.receiver).delay = command.ticks
            is Command.SetDrop -> linkBetween(command.sender, command.receiver).dropsLeft = command.count
            is Command.Partition -> partition(command.groups)
            Command.Heal -> network.heal()
            is Command.ShowState -> print(stateLine(find(command.node)))
        }
    }

    private fun createNode(name: String, electionTimeouts: List<Long>) {
        if (clock.now > 0) reject(ErrorCode.STARTED)
        if (name in nodesByName) reject(ErrorCode.NODE_EXISTS)
        nodesByName[name] = RaftNode(name, electionTimeouts, nodesByName.keys, network, clock) { event ->
            print(eventLine(event))
        }
    }

    private fun tick(ticks: Long) {
        val end = clock.now + ticks
        while (clock.now < end) {
            clock.advance()
            deliverArrivals()
            runTimers()
        }
    }

    private fun deliverArrivals() {
        for (envelope in network.arrivalsAt(clock.now)) {
            nodesByName.getValue(envelope.receiver).receive(envelope.sender, envelope.message)
        }
    }

    private fun runTimers() {
        for (node in nodesByName.values) node.runTimer()
    }

    private fun linkBetween(sender: String, receiver: String): Link {
        find(sender)
        find(receiver)
        if (sender == receiver) reject(ErrorCode.SAME_NODE)
        return network.link(sender, receiver)
    }

    private fun partition(groups: List<List<String>>) {
        val listed = groups.flatten()
        listed.forEach(::find)
        if (listed.toSet().size != listed.size) reject(ErrorCode.BAD_PARTITION)
        network.partition(groups, nodesByName.keys)
    }

    private fun find(name: String): RaftNode = nodesByName[name] ?: reject(ErrorCode.NO_NODE)
}
