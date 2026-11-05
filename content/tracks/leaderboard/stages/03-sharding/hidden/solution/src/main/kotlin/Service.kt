class Service {
    private data class TimedBoard(val period: Long, val leaderboard: Leaderboard = Leaderboard())

    private val boards = hashMapOf("all" to TimedBoard(0))
    private var time = 0L

    fun execute(command: Command): String? = when (command) {
        is Command.Create -> create(command)
        is Command.Tick -> tick(command.time)
        is Command.Update -> update(command)
        is Command.Rank -> boards[command.board]?.leaderboard?.rank(command.user) ?: "ERR"
        is Command.Top -> boards[command.board]?.leaderboard?.top(command.count) ?: "ERR"
        is Command.Around -> boards[command.board]?.leaderboard?.around(command.user, command.radius) ?: "ERR"
        is Command.Reset -> reset(command.board)
        is Command.Distribute -> distribute(command)
        is Command.DistributedRank -> boards[command.board]?.leaderboard?.distributedRank(command.mode, command.user) ?: "ERR"
        is Command.Counts -> boards[command.board]?.leaderboard?.counts(command.mode) ?: "ERR"
        Command.Invalid -> "ERR"
    }

    private fun create(command: Command.Create): String? {
        if (command.board in boards) return "ERR"
        boards[command.board] = TimedBoard(command.period)
        return null
    }

    private fun tick(nextTime: Long): String? {
        if (nextTime < time) return "ERR"
        for (board in boards.values) {
            if (board.period > 0 && time / board.period != nextTime / board.period) {
                board.leaderboard.clear()
            }
        }
        time = nextTime
        return null
    }

    private fun update(command: Command.Update): String? {
        val board = boards[command.board]?.leaderboard ?: return "ERR"
        return if (board.update(command.user, command.value, command.kind)) null else "ERR"
    }

    private fun reset(name: String): String? {
        val board = boards[name]?.leaderboard ?: return "ERR"
        board.clear()
        return null
    }

    private fun distribute(command: Command.Distribute): String? {
        val board = boards[command.board]?.leaderboard ?: return "ERR"
        board.distribute(command.boundaries)
        return null
    }
}
