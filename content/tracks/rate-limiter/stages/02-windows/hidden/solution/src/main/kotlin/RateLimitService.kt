class RateLimitService(private val print: (String) -> Unit) {
    private val clock = Clock()
    private val limiters = HashMap<String, Limiter>()

    fun handle(line: String) {
        try {
            execute(parseCommand(line))
        } catch (failure: CommandFailure) {
            print(failure.line)
        }
    }

    private fun execute(command: Command) {
        when (command) {
            is Command.DefineTokenBucket -> define(command.name, TokenBucket(command.capacity, command.refillPerSecond))
            is Command.DefineLeakyBucket -> define(command.name, LeakyBucket(command.queueLength, command.leakInterval))
            is Command.DefineWindow -> define(command.name, WindowLimiter(command.limit, command.windowLength))
            is Command.Request -> request(command)
            is Command.Report -> reportLines(findLimiterOfKind<WindowLimiter>(command.limiter).report).forEach(print)
        }
    }

    private fun define(name: String, limiter: Limiter) {
        if (name in limiters) reject(ErrorCode.EXISTS)
        limiters[name] = limiter
    }

    private fun request(command: Command.Request) {
        val limiter = findLimiter(command.limiter)
        val time = clock.advanceTo(command.time)
        print(verdictLine(limiter.request(command.key, time)))
    }

    private fun findLimiter(name: String): Limiter = limiters[name] ?: reject(ErrorCode.NO_LIMITER)

    private inline fun <reified T : Limiter> findLimiterOfKind(name: String): T =
        findLimiter(name) as? T ?: reject(ErrorCode.WRONG_KIND)
}
