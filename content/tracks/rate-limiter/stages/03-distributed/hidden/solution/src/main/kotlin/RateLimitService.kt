class RateLimitService(private val print: (String) -> Unit) {
    private val clock = Clock()
    private val limiters = HashMap<String, Limiter>()
    private val gateways = HashMap<String, Gateway>()
    private val distributed = DistributedLimiting(clock, print)

    fun handle(line: String) {
        try {
            execute(parseCommand(line))
        } catch (failure: CommandFailure) {
            print(failure.line)
        }
    }

    fun finishInput() {
        distributed.runUntil(Long.MAX_VALUE)
    }

    private fun execute(command: Command) {
        when (command) {
            is Command.DefineTokenBucket -> define(command.name, TokenBucket(command.capacity, command.refillPerSecond))
            is Command.DefineLeakyBucket -> define(command.name, LeakyBucket(command.queueLength, command.leakInterval))
            is Command.DefineWindow -> define(command.name, WindowLimiter(command.limit, command.windowLength))
            is Command.DefineReadWrite -> define(command.name, ReadWriteLimiter(command.limit, command.windowLength))
            is Command.DefineAtomic -> define(command.name, AtomicLimiter(command.limit, command.windowLength))
            is Command.DefineLocalCopy ->
                define(command.name, LocalCopyLimiter(command.limit, command.windowLength, command.syncPeriod))
            is Command.RegisterGateway -> registerGateway(command)
            is Command.Request -> request(command)
            is Command.Report -> reportLines(findLimiterOfKind<WindowLimiter>(command.limiter).report).forEach(print)
            is Command.Call -> call(command)
            is Command.Peek -> peek(command)
            is Command.StopStore -> {
                advanceClock(command.time)
                distributed.stopStore()
            }
            is Command.StartStore -> {
                advanceClock(command.time)
                distributed.startStore()
            }
        }
    }

    private fun define(name: String, limiter: Limiter) {
        if (name in limiters) reject(ErrorCode.EXISTS)
        limiters[name] = limiter
    }

    private fun registerGateway(command: Command.RegisterGateway) {
        if (command.name in gateways) reject(ErrorCode.EXISTS)
        gateways[command.name] = Gateway(command.delay, command.workers, command.processingTime)
    }

    private fun request(command: Command.Request) {
        val limiter = findLimiterOfKind<RequestLimiter>(command.limiter)
        val time = advanceClock(command.time)
        print(verdictLine(limiter.request(command.key, time)))
    }

    private fun call(command: Command.Call) {
        val limiter = findLimiterOfKind<SharedLimiter>(command.limiter)
        val gateway = gateways[command.gateway] ?: reject(ErrorCode.NO_GATEWAY)
        advanceClock(command.time)
        distributed.call(limiter, command.key, gateway, command.priority)
    }

    private fun peek(command: Command.Peek) {
        val limiter = findLimiterOfKind<SharedLimiter>(command.limiter)
        val time = advanceClock(command.time)
        print(distributed.peek(CounterKey(limiter, command.key, limiter.windowOf(time))).toString())
    }

    private fun advanceClock(timeArgument: Long): Long {
        val time = maxOf(clock.now, timeArgument)
        distributed.runUntil(time)
        return clock.advanceTo(time)
    }

    private fun findLimiter(name: String): Limiter = limiters[name] ?: reject(ErrorCode.NO_LIMITER)

    private inline fun <reified T : Limiter> findLimiterOfKind(name: String): T =
        findLimiter(name) as? T ?: reject(ErrorCode.WRONG_KIND)
}
