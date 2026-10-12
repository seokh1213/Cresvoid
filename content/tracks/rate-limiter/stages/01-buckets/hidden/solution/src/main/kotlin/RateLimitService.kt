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
            is Command.Request -> request(command)
        }
    }

    private fun define(name: String, limiter: Limiter) {
        if (name in limiters) reject(ErrorCode.EXISTS)
        limiters[name] = limiter
    }

    private fun request(command: Command.Request) {
        val limiter = limiters[command.limiter] ?: reject(ErrorCode.NO_LIMITER)
        val time = clock.advanceTo(command.time)
        print(verdictLine(limiter.request(command.key, time)))
    }
}
