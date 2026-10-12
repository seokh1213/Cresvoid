import kotlin.random.Random

class Options(val nodes: Int, val ticks: Long, val requests: Int, val queries: Int, val seed: Long)

fun parseOptions(arguments: Array<String>): Options {
    val values = mutableMapOf(
        "--nodes" to "5",
        "--ticks" to "3000",
        "--requests" to "300",
        "--queries" to "150",
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
    require(options.ticks in 1..900_000) { "흐르는 시각은 1~900,000 틱이다" }
    return options
}

val options = parseOptions(args)
val largeScale = options.requests >= 50_000

val FOLLOWER = 0
val CANDIDATE = 1
val LEADER = 2
val MAX_ENTRIES = 4

sealed interface Message {
    val term: Int
}

class RequestVote(override val term: Int, val lastIndex: Int, val lastTerm: Int) : Message

class VoteReply(override val term: Int, val granted: Boolean) : Message

class AppendEntries(override val term: Int, val prevIndex: Int, val prevTerm: Int, val entries: IntArray) : Message

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

class NewerLogVote : RuntimeException()

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
            send(node.id, peer.id, AppendEntries(node.term, next - 1, node.termAt(next - 1), entries))
        }
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
                val granted = message.term == node.term && node.votedFor == -1
                if (granted && node.isMoreUpToDateThan(message.lastTerm, message.lastIndex)) throw NewerLogVote()
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
        send(node.id, from, AppendReply(node.term, true, message.prevIndex + message.entries.size))
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
    "tick", "STATUS", "VOTE", "APPEND", "request", "Log",
    "next", "PUT", "SET", "SNAPSHOT", "CONFIG", "LEADER",
)
val COMMON_KEYS = listOf("x", "y", "k", "a", "user", "cart", "x", "k")
val RARE_KEYS = listOf("key12345", "z9", "b0", "q", "k1", "abcdefgh", "m", "z0000000")
val COMMON_VALUES = listOf("1", "2", "3", "ok", "v", "10", "1", "0")
val RARE_VALUES = listOf("00000000", "zz99", "abcdefgh", "9", "x1y2z3", "a0", "12345678", "z")
val BAD_KEYS = listOf("X", "1x", "key123456", "k-1", "é", "_a", "Key")
val BAD_VALUES = listOf("A", "123456789", "-", "v_1", "Ok", "1.5")

class Scenario(private val random: Random) {
    private val sim = Simulation()
    private val output = StringBuilder()
    private val names: List<String>
    private val ghosts: List<String>
    private var requested = 0
    private var asked = 0
    private var logsShown = 0
    private var oldLeaderSide: List<SimNode>? = null
    private var isolatedStandby: List<SimNode> = emptyList()

    init {
        val style = if (largeScale) NAME_STYLES[0] else NAME_STYLES[random.nextInt(NAME_STYLES.size)]
        names = style.shuffled(random).take(options.nodes)
        ghosts = GHOST_NAMES.filter { it !in names }
    }

    fun run(): String? =
        try {
            setUp()
            awaitEstablishedLeader()
            while (sim.now < options.ticks) {
                replicationPhase()
                convergePhase()
                if (sim.now < options.ticks) electionPhase()
            }
            finish()
            output.toString()
        } catch (vote: NewerLogVote) {
            null
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
        if (!largeScale && chance(0.3)) emit("NODE ${pick(names)} ${coreTimeout()}")
        if (!largeScale && chance(0.3)) showLog(pick(sim.nodes))
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
        emit(spaced(listOf("REQUEST", node.name, key(), value())))
        if (sim.request(node)) requested++
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

    private fun query() {
        val leaders = sim.nodes.filter { it.role == LEADER }
        val target = if (leaders.isNotEmpty() && chance(0.6)) pick(leaders) else pick(sim.nodes)
        val logWeight = when {
            !largeScale -> 30
            target.lastIndex <= 3_000 && logsShown < 30 -> 12
            else -> 0
        }
        when (weighted("next" to if (largeScale) 70 else 45, "log" to logWeight, "state" to 25)) {
            "next" -> showNext(target)
            "log" -> showLog(target)
            else -> showState(target)
        }
    }

    private fun errorLine(): String {
        val member = pick(sim.nodes).name
        val other = pick(sim.nodes).name
        val logErrorWeight = if (largeScale) 0 else 1
        return weighted(
            { "NODE $member ${coreTimeout()}" } to 2,
            { "NODE ${pick(ghosts)} ${coreTimeout()}" } to if (sim.now > 0) 2 else 0,
            { "NODE ${pick(BAD_NAMES)} 5" } to 1,
            { "TICK ${pick(BAD_NUMBERS)}" } to 3,
            { "DELAY $member $member 2" } to 1,
            { "DELAY $member ${pick(ghosts)} 2" } to 1,
            { "DROP $member $other ${pick(listOf("-1", "1000001", "01"))}" } to 1,
            { "PARTITION $member,$member" } to 1,
            { "PARTITION $member, $other" } to 1,
            { "PARTITION $member ${pick(ghosts)}" } to 1,
            { "HEAL now" } to 1,
            { "STATE ${pick(ghosts)}" } to 1,
            { "REQUEST $member ${key()}" } to 3,
            { "REQUEST $member ${key()} ${value()} extra" } to 2,
            { "REQUEST $member ${pick(BAD_KEYS)} ${value()}" } to 3,
            { "REQUEST $member ${key()} ${pick(BAD_VALUES)}" } to 3,
            { "REQUEST ${pick(ghosts)} ${key()} ${value()}" } to 3,
            { "REQUEST ${pick(BAD_NAMES)} ${key()} ${value()}" } to 1,
            { "REQUEST" } to 1,
            { "LOG" } to logErrorWeight,
            { "LOG $member $other" } to logErrorWeight,
            { "LOG ${pick(ghosts)}" } to 2 * logErrorWeight,
            { "NEXT" } to 1,
            { "NEXT ${pick(ghosts)}" } to 2,
            { "NEXT ${pick(BAD_NAMES)}" } to 1,
            { "${pick(UNKNOWN_WORDS)} $member" } to 3,
            { "REQUEST $member ${key()} ${value()}\r" } to 1,
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
            } else if (node.term > leader.term || (node.core && node.role != LEADER)) {
                return null
            }
        }
        if (sim.inFlight().any { it is RequestVote || it.term > leader.term }) return null
        return leader
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

    private fun replicationPhase() {
        val length = if (largeScale) random.nextLong(1_500, 5_000) else random.nextLong(30, 250)
        val end = sim.now + length
        val leader = established() ?: awaitEstablishedLeader()
        val quota = options.requests.toLong() * minOf(end, options.ticks) / options.ticks - requested
        val rate = quota.coerceAtLeast(0).toDouble() / length
        val oldLeaderShare = random.nextDouble(0.1, 0.5)
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
        val tweak = weighted(
            "reply-delay" to 30,
            "reply-drop" to 20,
            "standby-delay" to 20,
            "standby-drop" to 15,
            "core-delay" to 15,
        )
        when (tweak) {
            "reply-delay" -> setDelay(follower, leaderNow, weighted({ random.nextLong(2, 9) } to 70, { 1L } to 30)())
            "reply-drop" -> setDrops(follower, leaderNow, weighted({ random.nextLong(1, 11) } to 80, { 0L } to 20)())
            "standby-delay" -> if (!follower.core) {
                setDelay(leaderNow, follower, weighted({ random.nextLong(2, 11) } to 70, { 1L } to 30)())
            }
            "standby-drop" -> if (!follower.core) setDrops(leaderNow, follower, random.nextLong(0, 11))
            else -> {
                val current = sim.delayOf(leaderNow, follower)
                val bound = minOf(5L, follower.deadline - sim.now - 1)
                if (current > 1) {
                    setDelay(leaderNow, follower, 1)
                } else if (bound >= 2 && sim.dropsOf(leaderNow, follower) == 0L) {
                    setDelay(leaderNow, follower, random.nextLong(2, bound + 1))
                }
            }
        }
    }

    private fun needsNewerEntries(leader: SimNode): Boolean =
        sim.nodes.any { node ->
            node.isMoreUpToDateThan(leader.lastTerm, leader.lastIndex) ||
                (sim.connected(leader, node) && node.lastIndex > leader.lastIndex)
        }

    private fun converged(leader: SimNode): Boolean =
        sim.nodes.all { node ->
            if (node.core) node.hasSameLogAs(leader) else !node.isMoreUpToDateThan(leader.lastTerm, leader.lastIndex)
        }

    // 2단계 5.4 가 출력을 정하지 않는 투표가 생기지 않도록 코어 노드의 로그가 모두 리더 로그와 같아진 뒤에만 선거를 일으킨다
    private fun convergePhase() {
        resetLinks()
        oldLeaderSide = null
        if (isolatedStandby.isNotEmpty() && chance(0.6)) isolatedStandby = emptyList()
        refreshPartition()
        while (true) {
            val leader = established() ?: awaitEstablishedLeader()
            if (converged(leader)) return
            if (needsNewerEntries(leader) && chance(0.6)) request(leader)
            tick(random.nextLong(1, 4))
        }
    }

    private fun electionPhase() {
        val leader = established() ?: return
        val reachable = sim.nodes.filter { it !in isolatedStandby }
        val connectedStandby = reachable.filter { !it.core && it !== leader }
        val leaderSide = listOf(leader) + connectedStandby.filter { chance(0.3) }
        val restAfterIsolation = reachable.filter { it !in leaderSide }
        val canIsolateLeader = restAfterIsolation.size * 2 > sim.nodes.size && restAfterIsolation.any { it.core }
        val coreFollowers = core.filter { it !== leader }
        when (
            weighted(
                "isolate-leader" to if (canIsolateLeader) 45 else 0,
                "isolate-core" to if (coreFollowers.isNotEmpty()) 15 else 0,
                "drop-heartbeats" to if (coreFollowers.isNotEmpty()) 15 else 0,
                "delay-heartbeats" to if (coreFollowers.isNotEmpty()) 10 else 0,
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
                stormTicks(isolated)
                refreshPartition()
            }
            "drop-heartbeats" -> {
                val victim = pick(coreFollowers)
                setDrops(leader, victim, victim.deadline - sim.now + random.nextLong(0, 5))
                stormTicks(listOf(victim))
                resetLinks()
            }
            "delay-heartbeats" -> {
                val victim = pick(coreFollowers)
                setDelay(leader, victim, victim.deadline - sim.now + random.nextLong(1, 20))
                stormTicks(listOf(victim))
                resetLinks()
            }
            "split" -> {
                val shuffled = reachable.shuffled(random)
                val first = random.nextInt(1, shuffled.size - 1)
                val second = random.nextInt(first + 1, shuffled.size)
                val groups = listOf(shuffled.take(first), shuffled.subList(first, second), shuffled.drop(second))
                partition(groups + listOf(isolatedStandby))
                stormTicks(core)
                refreshPartition()
            }
            else -> tick(idleTicks())
        }
        awaitEstablishedLeader()
    }

    private fun stormTicks(victims: List<SimNode>) {
        val lastDeadline = victims.maxOf { it.deadline }
        val end = maxOf(lastDeadline, sim.now) + if (largeScale) random.nextLong(1, 500) else random.nextLong(1, 100)
        while (sim.now < end) {
            tick(minOf(end - sim.now, idleTicks()))
            if (!largeScale && chance(0.2)) query()
            if (chance(0.15)) requestToFollower()
            if (chance(0.05)) emit(errorLine())
        }
    }

    private fun finish() {
        for (node in sim.nodes) {
            showState(node)
            if (!largeScale) showLog(node)
            showNext(node)
        }
    }
}

fun generate(): String {
    repeat(1_000) { attempt ->
        val random = if (attempt == 0) Random(options.seed) else Random(options.seed * 1_000_003 + attempt)
        Scenario(random).run()?.let { return it }
    }
    error("2단계 5.4 의 일을 피하는 입력을 만들지 못했다")
}

System.out.write(generate().encodeToByteArray())
System.out.flush()
