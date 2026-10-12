const val STALE_INTERVAL = 30L

sealed interface Liveness {
    data object Running : Liveness

    data class Stopped(val lastHeartbeat: Long) : Liveness

    data object Dead : Liveness
}

enum class NodeState(val keyword: String) {
    LIVE("live"),
    STALE("stale"),
    DEAD("dead"),
}

class DataNode(val name: String, val rack: String, val capacity: Long) {
    private val storedReplicas = mutableSetOf<Replica>()

    var liveness: Liveness = Liveness.Running
        private set
    var used: Long = 0
        private set
    var knownReplicaCount: Int = 0
        private set

    val remaining: Long get() = capacity - used

    val isRunning: Boolean get() = liveness == Liveness.Running

    val replicas: Set<Replica> get() = storedReplicas

    fun stateAt(now: Long): NodeState = when (val current = liveness) {
        Liveness.Running -> NodeState.LIVE
        is Liveness.Stopped -> if (now - current.lastHeartbeat >= STALE_INTERVAL) NodeState.STALE else NodeState.LIVE
        Liveness.Dead -> NodeState.DEAD
    }

    fun stop(now: Long) {
        liveness = Liveness.Stopped(lastHeartbeat = now)
    }

    fun start() {
        liveness = Liveness.Running
    }

    fun markDead() {
        liveness = Liveness.Dead
    }

    fun store(replica: Replica) {
        storedReplicas += replica
        used += replica.block.size
        if (replica.known) knownReplicaCount++
    }

    fun delete(replica: Replica) {
        storedReplicas -= replica
        used -= replica.block.size
        if (replica.known) knownReplicaCount--
    }

    fun learn(replica: Replica) {
        if (replica.known) return
        replica.known = true
        knownReplicaCount++
    }

    fun forget(replica: Replica) {
        if (!replica.known) return
        replica.known = false
        knownReplicaCount--
    }
}
