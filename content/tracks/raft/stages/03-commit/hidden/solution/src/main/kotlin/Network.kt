class Link {
    var delay: Long = 1
    var dropsLeft: Long = 0
    var cut: Boolean = false
}

class Network(private val clock: Clock) {
    private val links = HashMap<Pair<String, String>, Link>()
    private val inFlightByArrival = HashMap<Long, ArrayDeque<Envelope>>()

    fun link(sender: String, receiver: String): Link = links.getOrPut(sender to receiver) { Link() }

    fun send(sender: String, receiver: String, message: Message) {
        val link = link(sender, receiver)
        if (link.cut) return
        if (link.dropsLeft > 0) {
            link.dropsLeft--
            return
        }
        // 보낸 차례대로 덧붙이므로 같은 도착 시각의 메시지는 보낸 순서로 남는다
        inFlightByArrival.getOrPut(clock.now + link.delay) { ArrayDeque() }.addLast(Envelope(sender, receiver, message))
    }

    fun arrivalsAt(time: Long): List<Envelope> = inFlightByArrival.remove(time) ?: emptyList()

    fun partition(groups: List<List<String>>, nodes: Collection<String>) {
        val groupOfNode = groups.withIndex().flatMap { (group, members) -> members.map { it to group } }.toMap()
        for (sender in nodes) {
            for (receiver in nodes) {
                if (sender == receiver) continue
                val senderGroup = groupOfNode[sender]
                val receiverGroup = groupOfNode[receiver]
                val separated = senderGroup != null && receiverGroup != null && senderGroup != receiverGroup
                link(sender, receiver).cut = separated
            }
        }
    }

    fun heal() {
        links.values.forEach { it.cut = false }
    }
}
