const val RECHECK_INTERVAL = 300L
private const val DEAD_INTERVAL = 630L

class HeartbeatMonitor(
    private val cluster: Cluster,
    private val blockManager: BlockManager,
    private val clock: Clock,
) {
    fun detectStaleNodes(): List<Event> =
        cluster.stoppedNodes
            .filter { secondsSinceHeartbeat(it) == STALE_INTERVAL }
            .map { Event.Stale(clock.now, it) }

    fun markDeadNodes(): List<Event> {
        val expired = cluster.stoppedNodes.filter { (secondsSinceHeartbeat(it) ?: 0L) > DEAD_INTERVAL }
        for (node in expired) {
            cluster.markDead(node)
            blockManager.forgetReplicasOf(node)
        }
        return expired.map { Event.Dead(clock.now, it) }
    }

    private fun secondsSinceHeartbeat(node: DataNode): Long? =
        (node.liveness as? Liveness.Stopped)?.let { clock.now - it.lastHeartbeat }
}
