import java.util.TreeMap
import java.util.TreeSet

class LockManager {
    private val keyLocks = TreeMap<String, KeyLock>()
    private val rangeLocks = ArrayList<RangeLock>()
    private val heldLocks = HashMap<Transaction, HeldLocks>()
    private val waitingRequests = HashMap<Transaction, WaitingRequest>()
    private val rangeWaitersByBlocker = HashMap<Transaction, MutableList<WaitingRequest>>()
    private val grantCandidates = TreeSet<WaitingRequest>(compareBy { it.waitOrder })
    private var waitCount = 0

    fun tryAcquire(transaction: Transaction, request: LockRequest): Boolean {
        if (conflictingHolders(transaction, request).isNotEmpty()) return false
        acquire(transaction, request)
        return true
    }

    fun conflictingHolders(transaction: Transaction, request: LockRequest): Set<Transaction> =
        holdersOverlapping(request.target)
            .filter { (holder, mode) -> holder != transaction && mode.conflictsWith(request.mode) }
            .keys

    fun blockersOf(transaction: Transaction): Set<Transaction> {
        val waiting = waitingRequests[transaction] ?: return emptySet()
        return conflictingHolders(transaction, waiting.request)
    }

    fun enqueue(transaction: Transaction, request: LockRequest) {
        val waiting = WaitingRequest(transaction, request, waitCount++)
        waitingRequests[transaction] = waiting
        when (val target = request.target) {
            is LockTarget.Key -> keyLocks.getOrPut(target.key) { KeyLock() }.waitersFor(request.mode).add(waiting)
            is LockTarget.Range -> watchBlocker(waiting, conflictingHolders(transaction, request).first())
        }
    }

    fun grantNext(): Transaction? {
        while (true) {
            val candidate = grantCandidates.pollFirst() ?: return null
            val blocker = conflictingHolders(candidate.transaction, candidate.request).firstOrNull()
            if (blocker == null) {
                grant(candidate)
                return candidate.transaction
            }
            when (val target = candidate.request.target) {
                is LockTarget.Key -> offerGrantableWaiters(target.key)
                is LockTarget.Range -> watchBlocker(candidate, blocker)
            }
        }
    }

    fun releaseAll(transaction: Transaction) {
        val waiting = waitingRequests[transaction]
        if (waiting != null) removeWaiting(waiting)
        val held = heldLocks.remove(transaction) ?: HeldLocks()
        held.keys.forEach { keyLocks.getValue(it).holders.remove(transaction) }
        rangeLocks.removeAll(held.ranges.toSet())
        val rangeKeys = held.ranges.flatMap { keyLocks.subMap(it.range.start, it.range.end).keys }
        val waitedKey = (waiting?.request?.target as? LockTarget.Key)?.key
        (held.keys + rangeKeys + listOfNotNull(waitedKey)).forEach(::offerGrantableWaiters)
        rangeWaitersByBlocker.remove(transaction)
            ?.filter { waitingRequests[it.transaction] === it }
            ?.let(grantCandidates::addAll)
    }

    private fun acquire(transaction: Transaction, request: LockRequest) {
        val held = heldLocks.getOrPut(transaction) { HeldLocks() }
        when (val target = request.target) {
            is LockTarget.Key -> {
                keyLocks.getOrPut(target.key) { KeyLock() }.holders.merge(transaction, request.mode, ::stronger)
                held.keys.add(target.key)
            }
            is LockTarget.Range -> {
                val rangeLock = RangeLock(transaction, request.mode, target.range)
                rangeLocks.add(rangeLock)
                held.ranges.add(rangeLock)
            }
        }
    }

    private fun grant(waiting: WaitingRequest) {
        removeWaiting(waiting)
        acquire(waiting.transaction, waiting.request)
        (waiting.request.target as? LockTarget.Key)?.let { offerGrantableWaiters(it.key) }
    }

    private fun removeWaiting(waiting: WaitingRequest) {
        waitingRequests.remove(waiting.transaction)
        grantCandidates.remove(waiting)
        val target = waiting.request.target
        if (target is LockTarget.Key) keyLocks.getValue(target.key).waitersFor(waiting.request.mode).remove(waiting)
    }

    private fun watchBlocker(waiting: WaitingRequest, blocker: Transaction) {
        rangeWaitersByBlocker.getOrPut(blocker) { ArrayList() }.add(waiting)
    }

    // 기다리는 요청은 서로 막지 않으므로 얻을 수 있는 요청은 X 줄 맨 앞, 유일한 보유자의 승격 요청, S 줄 맨 앞 가운데에 있다
    private fun offerGrantableWaiters(key: String) {
        val keyLock = keyLocks[key] ?: return
        if (!keyLock.hasWaiters) {
            if (keyLock.holders.isEmpty()) keyLocks.remove(key)
            return
        }
        val holders = holdersOverlapping(LockTarget.Key(key))
        val exclusiveWaiter = when (holders.size) {
            0 -> keyLock.exclusiveWaiters.firstOrNull()
            1 -> waitingRequests[holders.keys.single()]?.takeIf { it in keyLock.exclusiveWaiters }
            else -> null
        }
        val sharedWaiter = keyLock.sharedWaiters.firstOrNull()?.takeIf { LockMode.EXCLUSIVE !in holders.values }
        grantCandidates.addAll(listOfNotNull(exclusiveWaiter, sharedWaiter))
    }

    private fun holdersOverlapping(target: LockTarget): Map<Transaction, LockMode> {
        val holders = HashMap<Transaction, LockMode>()
        val keyLocksInTarget = when (target) {
            is LockTarget.Key -> listOfNotNull(keyLocks[target.key])
            is LockTarget.Range -> keyLocks.subMap(target.range.start, target.range.end).values
        }
        for (keyLock in keyLocksInTarget) {
            keyLock.holders.forEach { (holder, mode) -> holders.merge(holder, mode, ::stronger) }
        }
        for (rangeLock in rangeLocks) {
            if (target.overlaps(rangeLock.range)) holders.merge(rangeLock.holder, rangeLock.mode, ::stronger)
        }
        return holders
    }
}

private fun stronger(held: LockMode, requested: LockMode): LockMode =
    if (held == LockMode.EXCLUSIVE || requested == LockMode.EXCLUSIVE) LockMode.EXCLUSIVE else LockMode.SHARED

private class WaitingRequest(val transaction: Transaction, val request: LockRequest, val waitOrder: Int)

private class RangeLock(val holder: Transaction, val mode: LockMode, val range: KeyRange)

private class HeldLocks {
    val keys = HashSet<String>()
    val ranges = ArrayList<RangeLock>()
}

private class KeyLock {
    val holders = HashMap<Transaction, LockMode>()
    val sharedWaiters = LinkedHashSet<WaitingRequest>()
    val exclusiveWaiters = LinkedHashSet<WaitingRequest>()

    val hasWaiters: Boolean
        get() = sharedWaiters.isNotEmpty() || exclusiveWaiters.isNotEmpty()

    fun waitersFor(mode: LockMode): MutableSet<WaitingRequest> =
        if (mode == LockMode.SHARED) sharedWaiters else exclusiveWaiters
}
