import kotlin.random.Random

class Options(val nodes: Int, val ticks: Long, val requests: Int, val queries: Int, val seed: Long)

fun parseOptions(arguments: Array<String>): Options {
    val values = mutableMapOf(
        "--nodes" to "5",
        "--ticks" to "3000",
        "--requests" to "300",
        "--queries" to "200",
        "--seed" to "1",
    )
    require(arguments.size % 2 == 0) { "옵션은 --이름 값 쌍으로 준다: ${arguments.joinToString(" ")}" }
    for ((name, value) in arguments.toList().chunked(2)) {
        require(name in values) { "모르는 옵션: $name" }
        values[name] = value
    }
    val options = Options(
        nodes = values.getValue("--nodes").toInt(),
        ticks = values.getValue("--ticks").toLong(),
        requests = values.getValue("--requests").toInt(),
        queries = values.getValue("--queries").toInt(),
        seed = values.getValue("--seed").toLong(),
    )
    require(options.nodes in 1..15) { "노드 수는 1~15 다" }
    require(options.ticks in 1..700_000) { "흐르는 시각은 1~700,000 틱이다" }
    return options
}

val options = parseOptions(args)
val largeScale = options.requests >= 50_000

val FOLLOWER = 0
val CANDIDATE = 1
val LEADER = 2
val MAX_ENTRIES = 4
// 팔로워는 한 왕복(2틱)에 항목을 4개 받으므로 이보다 자주 요청하면 복제가 계속 밀린다
val MAX_REQUEST_RATE = 1.6

sealed interface Message {
    val term: Int
}

class RequestVote(override val term: Int, val lastIndex: Int, val lastTerm: Int) : Message

class VoteReply(override val term: Int, val granted: Boolean) : Message

class AppendEntries(
    override val term: Int,
    val prevIndex: Int,
    val prevTerm: Int,
    val entries: IntArray,
    val leaderCommit: Int,
) : Message

class AppendReply(override val term: Int, val success: Boolean, val index: Int) : Message

class Envelope(val from: Int, val to: Int, val message: Message)

class SimNode(val id: Int, val name: String, val timeouts: List<Long>, val core: Boolean) {
    var term = 0
    var votedFor = -1
    var leader = -1
    var role = FOLLOWER
    var deadline = timeouts.first()
    var elections = 0
    var electedAt = -1L
    var votes = 0
    var log = IntArray(16)
    var lastIndex = 0
    var commitIndex = 0
    var nextIndex = IntArray(0)
    var matchIndex = IntArray(0)

    fun termAt(index: Int): Int = if (index == 0) 0 else log[index - 1]

    val lastTerm: Int get() = termAt(lastIndex)

    fun append(entryTerm: Int) {
        if (lastIndex == log.size) log = log.copyOf(log.size * 2)
        log[lastIndex++] = entryTerm
    }

    fun isMoreUpToDateThan(otherTerm: Int, otherIndex: Int): Boolean =
        lastTerm > otherTerm || (lastTerm == otherTerm && lastIndex > otherIndex)

    fun hasSameLogAs(other: SimNode): Boolean = lastIndex == other.lastIndex && lastTerm == other.lastTerm
}

class NoLeader : RuntimeException()

class Simulation {
    val nodes = ArrayList<SimNode>()
    var now = 0L
    private val delays = HashMap<Int, Long>()
    private val drops = HashMap<Int, Long>()
    private val cut = HashSet<Int>()
    private val arrivals = HashMap<Long, ArrayList<Envelope>>()

    private fun linkKey(from: Int, to: Int) = from * 64 + to

    fun addNode(name: String, timeouts: List<Long>, core: Boolean) {
        nodes += SimNode(nodes.size, name, timeouts, core)
    }

    fun delayOf(from: SimNode, to: SimNode): Long = delays[linkKey(from.id, to.id)] ?: 1L

    fun dropsOf(from: SimNode, to: SimNode): Long = drops[linkKey(from.id, to.id)] ?: 0L

    fun setDelay(from: SimNode, to: SimNode, ticks: Long) {
        delays[linkKey(from.id, to.id)] = ticks
    }

    fun setDrops(from: SimNode, to: SimNode, count: Long) {
        drops[linkKey(from.id, to.id)] = count
    }

    fun partition(groups: List<List<SimNode>>) {
        val groupOf = HashMap<Int, Int>()
        groups.forEachIndexed { group, members -> members.forEach { groupOf[it.id] = group } }
        for (from in nodes) {
            for (to in nodes) {
                if (from === to) continue
                val fromGroup = groupOf[from.id]
                val toGroup = groupOf[to.id]
                val key = linkKey(from.id, to.id)
                if (fromGroup != null && toGroup != null && fromGroup != toGroup) cut += key else cut -= key
            }
        }
    }

    fun heal() {
        cut.clear()
    }

    fun connected(first: SimNode, second: SimNode): Boolean =
        linkKey(first.id, second.id) !in cut && linkKey(second.id, first.id) !in cut

    fun inFlight(): Sequence<Message> = arrivals.values.asSequence().flatten().map { it.message }

    fun leader(): SimNode? = nodes.filter { it.role == LEADER }.maxByOrNull { it.term }

    fun request(node: SimNode): Boolean {
        if (node.role != LEADER) return false
        node.append(node.term)
        advanceCommit(node)
        return true
    }

    fun tick() {
        now++
        arrivals.remove(now)?.forEach { envelope -> receive(nodes[envelope.to], envelope.from, envelope.message) }
        for (node in nodes) {
            if (node.role == LEADER) {
                if (node.electedAt < now) heartbeats(node)
            } else if (node.deadline == now) {
                startElection(node)
            }
        }
    }

    private fun send(from: Int, to: Int, message: Message) {
        val key = linkKey(from, to)
        if (key in cut) return
        val dropsLeft = drops[key] ?: 0L
        if (dropsLeft > 0) {
            drops[key] = dropsLeft - 1
            return
        }
        arrivals.getOrPut(now + (delays[key] ?: 1L)) { ArrayList() } += Envelope(from, to, message)
    }

    private fun isMajority(votes: Int) = votes * 2 > nodes.size

    private fun restartTimer(node: SimNode) {
        node.deadline = now + node.timeouts[node.elections % node.timeouts.size]
    }

    private fun becomeFollower(node: SimNode) {
        node.role = FOLLOWER
        restartTimer(node)
    }

    private fun startElection(node: SimNode) {
        node.term++
        node.role = CANDIDATE
        node.votedFor = node.id
        node.votes = 1
        node.leader = -1
        node.elections++
        restartTimer(node)
        if (isMajority(1)) {
            becomeLeader(node)
        } else {
            val request = RequestVote(node.term, node.lastIndex, node.lastTerm)
            for (peer in nodes) if (peer !== node) send(node.id, peer.id, request)
        }
    }

    private fun becomeLeader(node: SimNode) {
        node.role = LEADER
        node.electedAt = now
        node.leader = node.id
        node.nextIndex = IntArray(nodes.size) { node.lastIndex + 1 }
        node.matchIndex = IntArray(nodes.size)
        heartbeats(node)
    }

    private fun heartbeats(node: SimNode) {
        for (peer in nodes) {
            if (peer === node) continue
            val next = node.nextIndex[peer.id]
            val entries = IntArray(minOf(MAX_ENTRIES, node.lastIndex - next + 1)) { node.log[next - 1 + it] }
            send(node.id, peer.id, AppendEntries(node.term, next - 1, node.termAt(next - 1), entries, node.commitIndex))
        }
    }

    private fun advanceCommit(leader: SimNode) {
        val replicated = IntArray(nodes.size) { if (it == leader.id) leader.lastIndex else leader.matchIndex[it] }
        replicated.sortDescending()
        val highest = replicated[nodes.size / 2]
        if (highest > leader.commitIndex && leader.termAt(highest) == leader.term) leader.commitIndex = highest
    }

    private fun receive(node: SimNode, from: Int, message: Message) {
        if (message.term > node.term) {
            node.term = message.term
            node.votedFor = -1
            node.leader = -1
            if (node.role != FOLLOWER) becomeFollower(node)
        }
        when (message) {
            is RequestVote -> {
                val unvoted = message.term == node.term && node.votedFor == -1
                val granted = unvoted && !node.isMoreUpToDateThan(message.lastTerm, message.lastIndex)
                if (granted) {
                    node.votedFor = from
                    restartTimer(node)
                }
                send(node.id, from, VoteReply(node.term, granted))
            }
            is VoteReply -> if (node.role == CANDIDATE && message.term == node.term && message.granted) {
                node.votes++
                if (isMajority(node.votes)) becomeLeader(node)
            }
            is AppendEntries -> answerAppend(node, from, message)
            is AppendReply -> if (node.role == LEADER && message.term == node.term) {
                if (message.success) {
                    if (message.index > node.matchIndex[from]) node.matchIndex[from] = message.index
                    if (message.index + 1 > node.nextIndex[from]) node.nextIndex[from] = message.index + 1
                    advanceCommit(node)
                } else if (message.index == node.nextIndex[from] - 1 && message.index > node.matchIndex[from]) {
                    node.nextIndex[from] = message.index
                }
            }
        }
    }

    private fun answerAppend(node: SimNode, from: Int, message: AppendEntries) {
        if (message.term < node.term) {
            send(node.id, from, AppendReply(node.term, false, message.prevIndex))
            return
        }
        if (node.role == CANDIDATE) becomeFollower(node)
        node.leader = from
        restartTimer(node)
        val consistent = message.prevIndex == 0 ||
            (message.prevIndex <= node.lastIndex && node.termAt(message.prevIndex) == message.prevTerm)
        if (!consistent) {
            send(node.id, from, AppendReply(node.term, false, message.prevIndex))
            return
        }
        message.entries.forEachIndexed { offset, entryTerm ->
            val index = message.prevIndex + 1 + offset
            if (index > node.lastIndex) {
                node.append(entryTerm)
            } else if (node.termAt(index) != entryTerm) {
                node.lastIndex = index - 1
                node.append(entryTerm)
            }
        }
        val responseIndex = message.prevIndex + message.entries.size
        node.commitIndex = maxOf(node.commitIndex, minOf(message.leaderCommit, responseIndex))
        send(node.id, from, AppendReply(node.term, true, responseIndex))
    }
}

val NAME_STYLES = listOf(
    listOf("a", "b", "c", "d", "e", "f", "g", "h", "i", "j", "k", "l", "m", "n", "o"),
    List(15) { "n${it + 1}" },
    listOf(
        "alpha", "bravo", "charlie", "delta", "echo", "foxtrot", "golf", "hotel",
        "india", "juliet", "kilo", "lima", "mike", "november", "oscar",
    ),
    listOf("z", "a1", "zz9", "node1234", "x0", "q", "abcdefgh", "m00", "s1", "s10", "s2", "y7y7y7y7", "b", "k9", "w"),
)
val GHOST_NAMES = listOf("ghost", "zzzz", "nobody", "x9", "p")
val BAD_NAMES = listOf("A", "Node1", "1a", "abcdefghi", "a-b", "é", "a.b", "aB")
val BAD_NUMBERS = listOf("0", "05", "-1", "1000001", "99999999999999999999", "1.5", "x", "+3", "007")
val UNKNOWN_WORDS = listOf(
    "commit", "Get", "get", "PUT", "SET", "DELETE",
    "SNAPSHOT", "APPLY", "CAS", "READ", "request", "Commit",
)
val COMMON_KEYS = listOf("x", "y", "k", "a", "user", "cart", "x", "k")
val RARE_KEYS = listOf("key12345", "z9", "b0", "q", "k1", "abcdefgh", "m", "z0000000")
val UNWRITTEN_KEYS = listOf("never", "zz", "nokey", "q0q0q0q0")
val COMMON_VALUES = listOf("1", "2", "3", "ok", "v", "10", "1", "0")
val RARE_VALUES = listOf("00000000", "zz99", "abcdefgh", "9", "x1y2z3", "a0", "12345678", "z")
val BAD_KEYS = listOf("X", "1x", "key123456", "k-1", "é", "_a", "Key")
val BAD_VALUES = listOf("A", "123456789", "-", "v_1", "Ok", "1.5")

class Scenario(private val random: Random) {
    private val sim = Simulation()
    private val output = StringBuilder()
    private val names: List<String>
    private val ghosts: List<String>
    private val writtenKeys = ArrayList<String>()
    private var requested = 0
    private var asked = 0
    private var logsShown = 0
    private var oldLeaderSide: List<SimNode>? = null
    private var isolatedStandby: List<SimNode> = emptyList()
    private var plannedRequests: (Long) -> Long = { time ->
        options.requests.toLong() * minOf(time, options.ticks) / options.ticks
    }

    init {
        val style = if (largeScale) NAME_STYLES[0] else NAME_STYLES[random.nextInt(NAME_STYLES.size)]
        names = style.shuffled(random).take(options.nodes)
        ghosts = GHOST_NAMES.filter { it !in names }
    }

    fun run(): String? =
        try {
            setUp()
            awaitEstablishedLeader()
            if (largeScale) runLarge() else runNormal()
            finish()
            output.toString()
        } catch (stuck: NoLeader) {
            null
        }

    private fun chance(probability: Double): Boolean = random.nextDouble() < probability

    private fun <T> pick(items: List<T>): T = items[random.nextInt(items.size)]

    private fun <T> weighted(vararg choices: Pair<T, Int>): T {
        var target = random.nextInt(choices.sumOf { it.second })
        for ((value, weight) in choices) {
            if (target < weight) return value
            target -= weight
        }
        error("가중치 합이 맞지 않는다")
    }

    private fun emit(line: String) {
        output.append(line).append('\n')
    }

    private fun spaced(words: List<String>): String {
        if (!chance(0.05)) return words.joinToString(" ")
        val gaps = words.joinToString("") { word -> word + " ".repeat(random.nextInt(1, 4)) }.trimEnd()
        return " ".repeat(random.nextInt(0, 3)) + gaps + " ".repeat(random.nextInt(0, 3))
    }

    private val core: List<SimNode> get() = sim.nodes.filter { it.core }

    private fun coreTimeout(): Long = if (largeScale) random.nextLong(20, 90) else random.nextLong(6, 30)

    private fun setUp() {
        val coreCount = when {
            options.nodes <= 2 -> options.nodes
            else -> minOf(options.nodes, options.nodes / 2 + 1 + random.nextInt(0, 2))
        }
        // 대기 노드의 첫 선거 타임아웃은 입력 전체보다 길어서 대기 노드는 후보가 되지 않는다
        val standbyTimeout = maxOf(options.ticks * 2, 1_000L).coerceAtMost(1_000_000)
        val firstTimeouts = generateSequence { coreTimeout() }.distinct().take(coreCount).toList()
        names.forEachIndexed { index, name ->
            val isCore = index < coreCount
            val timeouts = if (isCore) {
                listOf(firstTimeouts[index]) + List(weighted(0 to 30, 1 to 40, 2 to 20, 5 to 10)) { coreTimeout() }
            } else {
                listOf(random.nextLong(standbyTimeout, 1_000_001)) + List(random.nextInt(0, 3)) { coreTimeout() }
            }
            emit(spaced(listOf("NODE", name) + timeouts.map(Long::toString)))
            sim.addNode(name, timeouts, isCore)
            if (!largeScale && chance(0.1)) emit(errorLine())
        }
        if (!largeScale && chance(0.4)) request(pick(sim.nodes))
        if (!largeScale && chance(0.4)) showCommit(pick(sim.nodes))
        if (!largeScale && chance(0.4)) showValue(pick(sim.nodes))
    }

    private fun tick(ticks: Long) {
        emit(spaced(listOf("TICK", ticks.toString())))
        repeat(ticks.toInt()) { sim.tick() }
        while (asked < options.queries.toLong() * sim.now / options.ticks) query()
    }

    private fun setDelay(from: SimNode, to: SimNode, ticks: Long) {
        emit(spaced(listOf("DELAY", from.name, to.name, ticks.toString())))
        sim.setDelay(from, to, ticks)
    }

    private fun setDrops(from: SimNode, to: SimNode, count: Long) {
        emit(spaced(listOf("DROP", from.name, to.name, count.toString())))
        sim.setDrops(from, to, count)
    }

    private fun partition(groups: List<List<SimNode>>) {
        val listed = groups.filter { it.isNotEmpty() }
        if (listed.size <= 1) {
            emit("HEAL")
            sim.heal()
            return
        }
        emit(spaced(listOf("PARTITION") + listed.map { group -> group.joinToString(",") { it.name } }))
        sim.partition(listed)
    }

    private fun refreshPartition() {
        val away = (oldLeaderSide.orEmpty() + isolatedStandby).toSet()
        partition(listOf(sim.nodes.filter { it !in away }) + listOfNotNull(oldLeaderSide) + listOf(isolatedStandby))
    }

    private fun resetLinks() {
        for (from in sim.nodes) {
            for (to in sim.nodes) {
                if (from === to) continue
                if (sim.delayOf(from, to) != 1L) setDelay(from, to, 1)
                if (sim.dropsOf(from, to) > 0) setDrops(from, to, 0)
            }
        }
    }

    private fun key(): String = if (largeScale) {
        weighted({ ('a' + random.nextInt(26)).toString() } to 90, { "k${random.nextInt(10)}" } to 10)()
    } else {
        weighted({ pick(COMMON_KEYS) } to 80, { pick(RARE_KEYS) } to 20)()
    }

    private fun value(): String = if (largeScale) {
        random.nextInt(100).toString()
    } else {
        weighted(
            { pick(COMMON_VALUES) } to 70,
            { pick(RARE_VALUES) } to 20,
            { random.nextInt(1000).toString() } to 10,
        )()
    }

    private fun request(node: SimNode) {
        val key = key()
        emit(spaced(listOf("REQUEST", node.name, key, value())))
        if (sim.request(node)) {
            requested++
            if (writtenKeys.size < 64 && key !in writtenKeys) writtenKeys += key
        }
    }

    private fun showState(node: SimNode) {
        asked++
        emit(spaced(listOf("STATE", node.name)))
    }

    private fun showLog(node: SimNode) {
        asked++
        logsShown++
        emit(spaced(listOf("LOG", node.name)))
    }

    private fun showNext(node: SimNode) {
        asked++
        emit(spaced(listOf("NEXT", node.name)))
    }

    private fun showCommit(node: SimNode) {
        asked++
        emit(spaced(listOf("COMMIT", node.name)))
    }

    private fun showValue(node: SimNode) {
        asked++
        val key = if (writtenKeys.isNotEmpty() && chance(0.9)) pick(writtenKeys) else pick(UNWRITTEN_KEYS)
        emit(spaced(listOf("GET", node.name, key)))
    }

    private fun query() {
        val leaders = sim.nodes.filter { it.role == LEADER }
        val target = if (leaders.isNotEmpty() && chance(0.4)) pick(leaders) else pick(sim.nodes)
        val logWeight = when {
            !largeScale -> 10
            target.lastIndex <= 3_000 && logsShown < 30 -> 2
            else -> 0
        }
        val weights = if (largeScale) listOf(40, 40, 8, 10) else listOf(30, 30, 15, 15)
        val kind = weighted(
            "commit" to weights[0],
            "get" to weights[1],
            "next" to weights[2],
            "state" to weights[3],
            "log" to logWeight,
        )
        when (kind) {
            "commit" -> showCommit(target)
            "get" -> showValue(target)
            "next" -> showNext(target)
            "state" -> showState(target)
            else -> showLog(target)
        }
    }

    private fun errorLine(): String {
        val member = pick(sim.nodes).name
        val other = pick(sim.nodes).name
        return weighted(
            { "NODE $member ${coreTimeout()}" } to 1,
            { "NODE ${pick(ghosts)} ${coreTimeout()}" } to if (sim.now > 0) 1 else 0,
            { "TICK ${pick(BAD_NUMBERS)}" } to 2,
            { "DELAY $member $member 2" } to 1,
            { "DROP $member ${pick(ghosts)} 1" } to 1,
            { "PARTITION $member,$member" } to 1,
            { "PARTITION $member, $other" } to 1,
            { "STATE ${pick(ghosts)}" } to 1,
            { "REQUEST $member ${key()}" } to 1,
            { "REQUEST $member ${pick(BAD_KEYS)} ${value()}" } to 1,
            { "REQUEST $member ${key()} ${pick(BAD_VALUES)}" } to 1,
            { "REQUEST ${pick(ghosts)} ${key()} ${value()}" } to 1,
            { "LOG ${pick(ghosts)}" } to 1,
            { "NEXT" } to 1,
            { "COMMIT" } to 2,
            { "COMMIT $member $other" } to 2,
            { "COMMIT ${pick(ghosts)}" } to 3,
            { "COMMIT ${pick(BAD_NAMES)}" } to 2,
            { "GET $member" } to 3,
            { "GET $member ${key()} ${key()}" } to 2,
            { "GET $member ${pick(BAD_KEYS)}" } to 3,
            { "GET ${pick(ghosts)} ${key()}" } to 3,
            { "GET ${pick(BAD_NAMES)} ${key()}" } to 1,
            { "GET" } to 1,
            { "${pick(UNKNOWN_WORDS)} $member" } to 4,
            { "COMMIT $member\r" } to 1,
        )()
    }

    private fun requestToFollower() {
        val followers = sim.nodes.filter { it.role != LEADER }
        if (followers.isNotEmpty()) request(pick(followers))
    }

    private fun idleTicks(): Long = weighted(
        { random.nextLong(1, 4) } to 50,
        { random.nextLong(4, 12) } to 35,
        { random.nextLong(12, 40) } to 15,
    )()

    private fun established(): SimNode? {
        val leader = sim.leader() ?: return null
        for (node in sim.nodes) {
            if (node === leader) continue
            if (sim.connected(leader, node)) {
                if (node.role != FOLLOWER || node.term != leader.term || node.leader != leader.id) return null
                // 다음 하트비트가 닿기 전에 선거 시각이 오는 팔로워는 곧 선거를 시작한다
                val nextHeartbeat = sim.now + 1 + sim.delayOf(leader, node)
                if (node.deadline < nextHeartbeat || sim.dropsOf(leader, node) > 0) return null
            } else if (node.term > leader.term || (node.core && !leadsOrFollowsOnItsSide(node))) {
                return null
            }
        }
        if (sim.inFlight().any { it is RequestVote || it.term > leader.term }) return null
        return leader
    }

    private fun leadsOrFollowsOnItsSide(node: SimNode): Boolean {
        if (node.role == LEADER) return true
        val followed = sim.nodes.getOrNull(node.leader) ?: return false
        val followsLiveLeader = followed.role == LEADER && followed.term == node.term && sim.connected(node, followed)
        return node.role == FOLLOWER && followsLiveLeader
    }

    private fun awaitEstablishedLeader(): SimNode {
        val giveUpAt = sim.now + if (largeScale) 20_000 else 3_000
        while (true) {
            established()?.let { return it }
            if (sim.now > giveUpAt) throw NoLeader()
            tick(idleTicks())
            if (!largeScale && chance(0.1)) query()
            if (chance(0.1)) requestToFollower()
            if (chance(0.03)) emit(errorLine())
        }
    }

    private fun catchUpTicks(): Long = if (largeScale) random.nextLong(5, 40) else random.nextLong(1, 6)

    private fun awaitSameLog(follower: SimNode, leader: SimNode) {
        val giveUpAt = sim.now + if (largeScale) 300_000 else 5_000
        while (!follower.hasSameLogAs(leader)) {
            if (sim.now > giveUpAt) throw NoLeader()
            tick(catchUpTicks())
        }
    }

    private fun converge(nodes: List<SimNode>) {
        val giveUpAt = sim.now + if (largeScale) 300_000 else 5_000
        while (true) {
            val leader = established() ?: awaitEstablishedLeader()
            if (nodes.all { it.hasSameLogAs(leader) }) return
            if (sim.now > giveUpAt) throw NoLeader()
            val divergentTail = nodes.any { node ->
                node.isMoreUpToDateThan(leader.lastTerm, leader.lastIndex) || node.lastIndex > leader.lastIndex
            }
            if (divergentTail && chance(0.6)) request(leader)
            tick(catchUpTicks())
        }
    }

    private fun runNormal() {
        while (sim.now < options.ticks) {
            replicationPhase(if (chance(0.5)) random.nextLong(30, 250) else random.nextLong(5, 40))
            settle(fully = chance(0.5))
            if (sim.now >= options.ticks) break
            when (weighted("election" to 64, "inherited" to 12, "unacked" to 8, "late-append" to 8, "wild" to 8)) {
                "election" -> electionPhase()
                "inherited" -> inheritedEntries(entries = random.nextInt(3, 60), holdTicks = random.nextLong(1, 80))
                "unacked" -> unackedReplication()
                "late-append" -> lateAppend()
                else -> wildElection()
            }
        }
    }

    private fun runLarge() {
        val surge = 120_000
        val inherited = 70_000
        val firstPartEnd = options.ticks * 3 / 10
        requestSurge(surge)
        plannedRequests = { time ->
            surge + (options.requests - surge - inherited).toLong() * minOf(time, firstPartEnd) / firstPartEnd
        }
        while (sim.now < firstPartEnd) cycle(converged = true)
        inheritedEntries(entries = inherited, holdTicks = 55_000)
        plannedRequests = { options.requests.toLong() }
        while (sim.now < options.ticks) cycle(converged = false)
    }

    // 팔로워 넷이 수만 항목 뒤처진 채 틱마다 성공 응답을 보내 커밋 인덱스 뒤를 하나씩 확인하는 풀이를 드러낸다
    private fun requestSurge(entries: Int) {
        val leader = established() ?: awaitEstablishedLeader()
        repeat(entries) { request(leader) }
        converge(sim.nodes)
    }

    private fun cycle(converged: Boolean) {
        replicationPhase(random.nextLong(1_500, 5_000))
        settle(fully = converged)
        if (sim.now < options.ticks) electionPhase()
    }

    private fun replicationPhase(length: Long) {
        val end = sim.now + length
        val leader = established() ?: awaitEstablishedLeader()
        val quota = plannedRequests(end) - requested
        val rate = minOf(quota.coerceAtLeast(0).toDouble() / length, MAX_REQUEST_RATE)
        val oldLeaderShare = if (largeScale) random.nextDouble(0.02, 0.15) else random.nextDouble(0.1, 0.5)
        var credit = 0.0
        while (sim.now < end) {
            val oldLeader = oldLeaderSide?.firstOrNull { it.role == LEADER }
            when (
                weighted(
                    "tick" to 60,
                    "query" to if (largeScale) 0 else 8,
                    "standby" to 4,
                    "tweak" to 8,
                    "errors" to if (largeScale) 1 else 4,
                    "follower" to 3,
                )
            ) {
                "tick" -> {
                    val ticks = minOf(end - sim.now, if (largeScale) random.nextLong(1, 12) else idleTicks())
                    tick(ticks)
                    credit += rate * ticks
                    repeat(credit.toInt()) {
                        request(if (oldLeader != null && chance(oldLeaderShare)) oldLeader else leader)
                    }
                    credit -= credit.toInt()
                }
                "query" -> query()
                "standby" -> changeStandbyIsolation()
                "tweak" -> tweakLinks(leader, oldLeader)
                "errors" -> emit(errorLine())
                else -> requestToFollower()
            }
        }
    }

    private fun changeStandbyIsolation() {
        val awayFromLeader = oldLeaderSide.orEmpty().toSet()
        val candidates = sim.nodes.filter { !it.core && it !in awayFromLeader }
        if (candidates.isEmpty()) return
        isolatedStandby = if (isolatedStandby.isNotEmpty() && chance(0.5)) {
            isolatedStandby.filter { chance(0.4) }
        } else {
            candidates.filter { chance(0.5) }.ifEmpty { listOf(pick(candidates)) }
        }
        refreshPartition()
    }

    private fun tweakLinks(leader: SimNode, oldLeader: SimNode?) {
        val leaderNow = if (oldLeader != null && chance(0.3)) oldLeader else leader
        val followers = sim.nodes.filter { it !== leaderNow && it.leader == leaderNow.id }
        if (followers.isEmpty()) return
        val follower = pick(followers)
        val throttles = !largeScale || !follower.core
        val tweak = weighted(
            "reply-delay" to 30,
            "reply-drop" to 20,
            "follower-delay" to if (throttles) 30 else 0,
            "follower-drop" to 20,
        )
        val longestReplyDelay = if (throttles) 9L else 3L
        when (tweak) {
            "reply-delay" -> {
                val ticks = weighted({ random.nextLong(2, longestReplyDelay) } to 70, { 1L } to 30)()
                setDelay(follower, leaderNow, ticks)
            }
            "reply-drop" -> setDrops(follower, leaderNow, weighted({ random.nextLong(1, 11) } to 80, { 0L } to 20)())
            "follower-delay" -> {
                val ticks = weighted({ random.nextLong(2, 11) } to 70, { 1L } to 30)()
                setDelay(leaderNow, follower, ticks)
            }
            else -> setDrops(leaderNow, follower, random.nextLong(0, 11))
        }
    }

    private fun settle(fully: Boolean) {
        resetLinks()
        oldLeaderSide = null
        if (isolatedStandby.isNotEmpty() && (largeScale || chance(0.6))) isolatedStandby = emptyList()
        refreshPartition()
        val leader = awaitEstablishedLeader()
        if (fully) converge(sim.nodes.filter { (it.core || largeScale) && sim.connected(it, leader) })
    }

    private fun electionPhase() {
        val leader = established() ?: return
        val reachable = sim.nodes.filter { it !in isolatedStandby }
        val connectedStandby = reachable.filter { !it.core && it !== leader }
        val leaderSide = listOf(leader) + connectedStandby.filter { !largeScale && chance(0.3) }
        val restAfterIsolation = reachable.filter { it !in leaderSide }
        val canIsolateLeader = restAfterIsolation.size * 2 > sim.nodes.size && restAfterIsolation.any { it.core }
        val coreFollowers = core.filter { it !== leader }
        when (
            weighted(
                "isolate-leader" to if (canIsolateLeader) 45 else 0,
                "isolate-core" to if (coreFollowers.isNotEmpty()) 15 else 0,
                "drop-heartbeats" to if (coreFollowers.isNotEmpty()) 15 else 0,
                "split" to if (reachable.size >= 3) 10 else 0,
                "calm" to 5,
            )
        ) {
            "isolate-leader" -> {
                oldLeaderSide = leaderSide
                refreshPartition()
            }
            "isolate-core" -> {
                val isolated = coreFollowers.filter { chance(0.5) }.ifEmpty { listOf(pick(coreFollowers)) }
                partition(listOf(reachable.filter { it !in isolated }, isolated, isolatedStandby))
                stormTicks(isolated, requestsTo = leader.takeUnless { largeScale })
                refreshPartition()
            }
            "drop-heartbeats" -> {
                val victim = pick(coreFollowers)
                setDrops(leader, victim, victim.deadline - sim.now + random.nextLong(0, 5))
                stormTicks(listOf(victim), requestsTo = leader.takeUnless { largeScale })
                resetLinks()
            }
            "split" -> {
                val shuffled = reachable.shuffled(random)
                val first = random.nextInt(1, shuffled.size - 1)
                val second = random.nextInt(first + 1, shuffled.size)
                val groups = listOf(shuffled.take(first), shuffled.subList(first, second), shuffled.drop(second))
                partition(groups + listOf(isolatedStandby))
                stormTicks(core, requestsTo = null)
                refreshPartition()
            }
            else -> tick(idleTicks())
        }
        awaitEstablishedLeader()
    }

    private fun stormTicks(victims: List<SimNode>, requestsTo: SimNode?) {
        val lastDeadline = victims.maxOf { it.deadline }
        val end = maxOf(lastDeadline, sim.now) + if (largeScale) random.nextLong(1, 500) else random.nextLong(1, 100)
        while (sim.now < end) {
            tick(minOf(end - sim.now, idleTicks()))
            if (requestsTo != null && chance(0.4)) repeat(random.nextInt(1, 4)) { request(requestsTo) }
            if (!largeScale && chance(0.2)) query()
            if (chance(0.15)) requestToFollower()
            if (chance(0.05)) emit(errorLine())
        }
    }

    private fun wildElection() {
        val leader = established() ?: awaitEstablishedLeader()
        val coreFollowers = core.filter { it !== leader }
        if (coreFollowers.isEmpty()) return tick(idleTicks())
        val lagging = pick(coreFollowers)
        val others = sim.nodes.filter { it !== lagging && it !in isolatedStandby }
        partition(listOf(others, listOf(lagging), isolatedStandby))
        val end = maxOf(lagging.deadline, sim.now) + random.nextLong(1, 60)
        while (sim.now < end) {
            tick(minOf(end - sim.now, idleTicks()))
            repeat(random.nextInt(0, 3)) { request(leader) }
        }
        refreshPartition()
        stormTicks(listOf(lagging), requestsTo = leader)
        awaitEstablishedLeader()
    }

    private fun unackedReplication() {
        val leader = established() ?: awaitEstablishedLeader()
        val followers = sim.nodes.filter { it !== leader && sim.connected(it, leader) }
        if (followers.isEmpty()) return tick(idleTicks())
        val silenced = followers.filter { chance(0.7) }.ifEmpty { followers }
        for (follower in silenced) setDrops(follower, leader, random.nextLong(5, 60))
        repeat(random.nextInt(2, 12)) {
            tick(random.nextLong(1, 5))
            repeat(random.nextInt(0, 3)) { request(leader) }
            if (chance(0.5)) showCommit(pick(sim.nodes))
        }
        resetLinks()
    }

    private fun lateAppend() {
        val leader = established() ?: awaitEstablishedLeader()
        val candidates = sim.nodes.filter { it !== leader && sim.connected(it, leader) }
        val behind = candidates.firstOrNull { node ->
            val rest = sim.nodes.filter { it !== node && it !in isolatedStandby }
            rest.size * 2 > sim.nodes.size
        } ?: return tick(idleTicks())
        partition(listOf(sim.nodes.filter { it !== behind && it !in isolatedStandby }, listOf(behind), isolatedStandby))
        repeat(random.nextInt(4, 14)) { request(leader) }
        tick(random.nextLong(3, 8))
        refreshPartition()
        tick(1)
        setDelay(leader, behind, random.nextLong(2, 7))
        tick(1)
        setDelay(leader, behind, 1)
        repeat(random.nextInt(2, 6)) {
            tick(1)
            showCommit(behind)
            if (chance(0.5)) showValue(behind)
        }
    }

    // 소수 쪽 옛 리더의 항목을 다음 리더가 물려받아 과반수에 퍼뜨리게 해 이전 임기 항목의 커밋 규칙을 드러낸다
    private fun inheritedEntries(entries: Int, holdTicks: Long) {
        isolatedStandby = emptyList()
        settle(fully = true)
        converge(sim.nodes)
        val leader = established() ?: awaitEstablishedLeader()
        val partner = sim.nodes.filter { it !== leader }.randomOrNull(random) ?: return
        val majoritySide = sim.nodes.filter { it !== leader && it !== partner }
        if (majoritySide.size * 2 <= sim.nodes.size || majoritySide.none { it.core }) return
        oldLeaderSide = listOf(leader, partner)
        refreshPartition()
        val majorityLeader = awaitEstablishedLeader()
        val stretch = if (largeScale) (entries / 1.8).toLong() else entries.toLong() + random.nextLong(0, 20)
        val rate = entries.toDouble() / stretch
        var credit = 0.0
        var sent = 0
        while (sent < entries) {
            val ticks = if (largeScale) random.nextLong(1, 12) else random.nextLong(1, 4)
            tick(ticks)
            credit += rate * ticks
            while (credit >= 1 && sent < entries) {
                request(leader)
                sent++
                credit -= 1
            }
        }
        awaitSameLog(partner, leader)
        oldLeaderSide = null
        refreshPartition()
        awaitEstablishedLeader()
        oldLeaderSide = listOf(majorityLeader)
        refreshPartition()
        val heir = awaitEstablishedLeader()
        converge(sim.nodes.filter { it !== majorityLeader })
        val holdEnd = sim.now + holdTicks
        while (sim.now < holdEnd) {
            val ticks = if (largeScale) random.nextLong(10, 60) else idleTicks()
            tick(minOf(holdEnd - sim.now, ticks))
        }
        request(heir)
        val giveUpAt = sim.now + 1_000
        while (heir.role == LEADER && heir.commitIndex < heir.lastIndex) {
            if (sim.now > giveUpAt) throw NoLeader()
            tick(1)
        }
        oldLeaderSide = null
        refreshPartition()
    }

    private fun finish() {
        for (node in sim.nodes) {
            showState(node)
            showCommit(node)
            showValue(node)
            if (!largeScale) showLog(node)
        }
    }
}

fun generate(): String {
    repeat(1_000) { attempt ->
        val random = if (attempt == 0) Random(options.seed) else Random(options.seed * 1_000_003 + attempt)
        Scenario(random).run()?.let { return it }
    }
    error("리더를 뽑는 입력을 만들지 못했다")
}

System.out.write(generate().encodeToByteArray())
System.out.flush()
