class DistributedLimiting(private val clock: Clock, private val print: (String) -> Unit) {
    private val store = Store()
    private val scheduler = Scheduler()
    private var lastRequestNumber = 0L

    fun runUntil(time: Long) {
        while (true) {
            val next = scheduler.pollUntil(time) ?: return
            clock.advanceTo(next.time)
            handle(next.event)
        }
    }

    fun call(limiter: SharedLimiter, key: String, gateway: Gateway, priority: Priority) {
        lastRequestNumber++
        val request = SharedRequest(lastRequestNumber, gateway, CounterKey(limiter, key, limiter.windowOf(clock.now)))
        if (gateway.mustShed(priority)) {
            publish(request, RequestResult.Shed)
            return
        }
        when (limiter) {
            is ReadWriteLimiter -> {
                gateway.takeWorker()
                send(gateway, GetCounter(request))
            }
            is AtomicLimiter -> {
                gateway.takeWorker()
                send(gateway, IncrementCounter(request))
            }
            is LocalCopyLimiter -> judgeWithCopy(limiter, request)
        }
    }

    fun peek(counter: CounterKey): Long = store.valueOf(counter)

    fun stopStore() {
        store.stop()
    }

    fun startStore() {
        store.start()
    }

    private fun judgeWithCopy(limiter: LocalCopyLimiter, request: SharedRequest) {
        val gateway = request.gateway
        val copy = gateway.copyFor(request.counter)
        val result = limiter.judge(copy.countWithNewRequest())
        if (result is RequestResult.Allow) {
            copy.addAllowed()
            gateway.takeWorker()
            returnWorkerAfter(gateway, gateway.processingTime)
        }
        publish(request, result)
        if (copy.canSync(clock.now, limiter.syncPeriod)) {
            val amount = copy.startSync(clock.now)
            send(gateway, AddToCounter(copy, amount))
        }
    }

    private fun handle(event: ScheduledEvent) {
        when (event) {
            is ScheduledEvent.OperationArrival -> receiveAtStore(event.operation)
            is ScheduledEvent.ReplyArrival -> receiveAtGateway(event.query, event.reply)
            is ScheduledEvent.WorkerReturn -> event.gateway.returnWorker()
        }
    }

    private fun receiveAtStore(operation: StoreOperation) {
        when (operation) {
            is SetCounter -> store.write(operation)
            is CounterQuery -> {
                val reply = store.answer(operation)
                scheduler.schedule(clock.now + operation.gateway.delay, ScheduledEvent.ReplyArrival(operation, reply))
            }
        }
    }

    private fun receiveAtGateway(query: CounterQuery, reply: StoreReply) {
        when (query) {
            is GetCounter -> concludeReadWrite(query.request, reply)
            is IncrementCounter -> concludeAtomic(query.request, reply)
            is AddToCounter -> receiveSyncReply(query.copy, reply)
        }
    }

    private fun concludeReadWrite(request: SharedRequest, reply: StoreReply) {
        val result = when (reply) {
            is StoreReply.Value -> request.counter.limiter.judge(reply.value + 1)
            StoreReply.Failure -> RequestResult.Open
        }
        publish(request, result)
        if (result is RequestResult.Allow) send(request.gateway, SetCounter(request.counter, result.count))
        releaseWorker(request.gateway, result)
    }

    private fun concludeAtomic(request: SharedRequest, reply: StoreReply) {
        val result = when (reply) {
            is StoreReply.Value -> request.counter.limiter.judge(reply.value)
            StoreReply.Failure -> RequestResult.Open
        }
        publish(request, result)
        releaseWorker(request.gateway, result)
    }

    private fun receiveSyncReply(copy: LocalCopy, reply: StoreReply) {
        if (!copy.gateway.holdsCopyOfWindow(copy.counter)) return
        when (reply) {
            is StoreReply.Value -> copy.acceptStoreTotal(reply.value)
            StoreReply.Failure -> copy.takeBackSentShare()
        }
    }

    private fun releaseWorker(gateway: Gateway, result: RequestResult) {
        returnWorkerAfter(gateway, if (result is RequestResult.Deny) 0 else gateway.processingTime)
    }

    private fun returnWorkerAfter(gateway: Gateway, holdTime: Long) {
        if (holdTime == 0L) {
            gateway.returnWorker()
        } else {
            scheduler.schedule(clock.now + holdTime, ScheduledEvent.WorkerReturn(gateway))
        }
    }

    private fun send(gateway: Gateway, operation: StoreOperation) {
        scheduler.schedule(clock.now + gateway.delay, ScheduledEvent.OperationArrival(operation))
    }

    private fun publish(request: SharedRequest, result: RequestResult) {
        print(eventLine(clock.now, request.number, result))
    }
}
