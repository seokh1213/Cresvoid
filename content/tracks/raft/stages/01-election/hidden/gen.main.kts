import java.util.TreeMap
import kotlin.random.Random

class Options(val nodes: Int, val ticks: Long, val queries: Int, val seed: Long)

fun parseOptions(arguments: Array<String>): Options {
    val values = mutableMapOf("--nodes" to "5", "--ticks" to "3000", "--queries" to "150", "--seed" to "1")
    require(arguments.size % 2 == 0) { "옵션은 --이름 값 쌍으로 준다: ${arguments.joinToString(" ")}" }
    for ((name, value) in arguments.toList().chunked(2)) {
        require(name in values) { "모르는 옵션: $name" }
        values[name] = value
    }
    val options = Options(
        nodes = values.getValue("--nodes").toInt(),
        ticks = values.getValue("--ticks").toLong(),
        queries = values.getValue("--queries").toInt(),
        seed = values.getValue("--seed").toLong(),
    )
    require(options.nodes in 1..15) { "노드 수는 1~15 다" }
    require(options.ticks in 1..1_000_000) { "흐르는 시각은 1~1,000,000 틱이다" }
    return options
}

val options = parseOptions(args)
val random = Random(options.seed)
val largeScale = options.ticks >= 50_000
val output = StringBuilder()

fun emit(line: String) {
    output.append(line).append('\n')
}

fun chance(probability: Double): Boolean = random.nextDouble() < probability

fun <T> pick(items: List<T>): T = items[random.nextInt(items.size)]

fun <T> weighted(vararg choices: Pair<T, Int>): T {
    var target = random.nextInt(choices.sumOf { it.second })
    for ((value, weight) in choices) {
        if (target < weight) return value
        target -= weight
    }
    error("가중치 합이 맞지 않는다")
}

val FOLLOWER = 0
val CANDIDATE = 1
val LEADER = 2

sealed interface Message {
    val term: Int
}

class RequestVote(override val term: Int) : Message

class VoteReply(override val term: Int, val granted: Boolean) : Message

class AppendEntries(override val term: Int) : Message

class AppendReply(override val term: Int) : Message

class Envelope(val from: Int, val to: Int, val message: Message)

class SimNode(val id: Int, val name: String, val timeouts: List<Long>) {
    var term = 0
    var votedFor = -1
    var role = FOLLOWER
    var deadline = timeouts.first()
    var elections = 0
    var electedAt = -1L
    var votes = 0
}

class Simulation {
    val nodes = ArrayList<SimNode>()
    var now = 0L
    private val delays = HashMap<Int, Long>()
    private val drops = HashMap<Int, Long>()
    private val cut = HashSet<Int>()
    private val arrivals = TreeMap<Long, ArrayList<Envelope>>()

    private fun linkKey(from: Int, to: Int) = from * 64 + to

    fun addNode(name: String, timeouts: List<Long>) {
        nodes += SimNode(nodes.size, name, timeouts)
    }

    fun setDelay(from: Int, to: Int, ticks: Long) {
        delays[linkKey(from, to)] = ticks
    }

    fun delayOf(from: Int, to: Int): Long = delays[linkKey(from, to)] ?: 1L

    fun setDrops(from: Int, to: Int, count: Long) {
        drops[linkKey(from, to)] = count
    }

    fun partition(groups: List<List<Int>>) {
        val groupOf = HashMap<Int, Int>()
        groups.forEachIndexed { group, members -> members.forEach { groupOf[it] = group } }
        for (from in nodes.indices) {
            for (to in nodes.indices) {
                if (from == to) continue
                val fromGroup = groupOf[from]
                val toGroup = groupOf[to]
                val separated = fromGroup != null && toGroup != null && fromGroup != toGroup
                if (separated) cut += linkKey(from, to) else cut -= linkKey(from, to)
            }
        }
    }

    fun heal() {
        cut.clear()
    }

    fun nextArrival(): Long? = arrivals.firstEntry()?.key

    fun leader(): SimNode? = nodes.filter { it.role == LEADER }.maxByOrNull { it.term }

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
        arrivals.getOrPut(now + delayOf(from, to)) { ArrayList() } += Envelope(from, to, message)
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
        node.elections++
        restartTimer(node)
        if (isMajority(1)) {
            becomeLeader(node)
        } else {
            for (peer in nodes) if (peer !== node) send(node.id, peer.id, RequestVote(node.term))
        }
    }

    private fun becomeLeader(node: SimNode) {
        node.role = LEADER
        node.electedAt = now
        heartbeats(node)
    }

    private fun heartbeats(node: SimNode) {
        for (peer in nodes) if (peer !== node) send(node.id, peer.id, AppendEntries(node.term))
    }

    private fun receive(node: SimNode, from: Int, message: Message) {
        if (message.term > node.term) {
            node.term = message.term
            node.votedFor = -1
            if (node.role != FOLLOWER) becomeFollower(node)
        }
        when (message) {
            is RequestVote -> {
                val granted = message.term == node.term && node.votedFor == -1
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
            is AppendEntries -> {
                if (message.term == node.term) {
                    if (node.role == CANDIDATE) becomeFollower(node)
                    restartTimer(node)
                }
                send(node.id, from, AppendReply(node.term))
            }
            is AppendReply -> Unit
        }
    }
}

val sim = Simulation()

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
val BAD_NAMES = listOf("A", "Node1", "1a", "abcdefghi", "a-b", "a_b", "é", "a.b", "-", "aB")
val BAD_NUMBERS = listOf("0", "05", "-1", "1000001", "99999999999999999999", "1.5", "x", "+3", "007", "1e3", "")
val UNKNOWN_WORDS = listOf(
    "tick", "Tick", "STATUS", "VOTE", "HEARTBEAT", "KILL", "heal",
    "state", "node", "SNAPSHOT", "CONFIG", "RESTART", "Partition", "LEADER",
)

val names = run {
    val style = if (largeScale) NAME_STYLES[1] else pick(NAME_STYLES)
    style.shuffled(random).take(options.nodes)
}
val ghosts = GHOST_NAMES.filter { it !in names }

fun timeoutValue(): Long = if (largeScale) {
    weighted(
        { random.nextLong(40, 401) } to 82,
        { random.nextLong(401, 2_001) } to 10,
        { random.nextLong(8, 40) } to 6,
        { 1_000_000L } to 2,
    )()
} else {
    weighted(
        { random.nextLong(3, 26) } to 74,
        { random.nextLong(26, 61) } to 12,
        { pick(listOf(1L, 2L)) } to 6,
        { pick(listOf(100L, 1_000L, 1_000_000L)) } to 3,
        { 10L } to 5,
    )()
}

fun timeoutList(): List<Long> {
    val length = weighted(1 to 38, 2 to 30, 3 to 14, 4 to 8, random.nextInt(5, 10) to 6, 10 to 4)
    val first = timeoutValue()
    return List(length) { index -> if (index > 0 && chance(0.15)) first else timeoutValue() }
}

fun nodeId(name: String): Int = names.indexOf(name)

fun randomName(): String = sim.nodes[random.nextInt(sim.nodes.size)].name

fun spaced(words: List<String>): String {
    if (!chance(0.06)) return words.joinToString(" ")
    val gaps = words.joinToString("") { word -> word + " ".repeat(random.nextInt(1, 4)) }.trimEnd()
    return " ".repeat(random.nextInt(0, 3)) + gaps + " ".repeat(random.nextInt(0, 3))
}

fun createNode(name: String) {
    val timeouts = timeoutList()
    emit(spaced(listOf("NODE", name) + timeouts.map(Long::toString)))
    sim.addNode(name, timeouts)
}

fun tick(ticks: Long) {
    emit(spaced(listOf("TICK", ticks.toString())))
    repeat(ticks.toInt()) { sim.tick() }
}

fun setDelay(from: String, to: String, ticks: Long) {
    emit(spaced(listOf("DELAY", from, to, ticks.toString())))
    sim.setDelay(nodeId(from), nodeId(to), ticks)
}

fun setDrops(from: String, to: String, count: Long) {
    emit(spaced(listOf("DROP", from, to, count.toString())))
    sim.setDrops(nodeId(from), nodeId(to), count)
}

fun partition(groups: List<List<String>>) {
    emit(spaced(listOf("PARTITION") + groups.map { it.joinToString(",") }))
    sim.partition(groups.map { group -> group.map(::nodeId) })
}

fun heal() {
    emit(spaced(listOf("HEAL")))
    sim.heal()
}

var asked = 0

fun state(name: String) {
    asked++
    emit(spaced(listOf("STATE", name)))
}

fun randomLink(): Pair<String, String> {
    val from = randomName()
    val to = sim.nodes.map { it.name }.filter { it != from }.let(::pick)
    return from to to
}

fun randomGroups(): List<List<String>> {
    val shuffled = sim.nodes.map { it.name }.shuffled(random)
    val leader = sim.leader()?.name
    return weighted(
        {
            if (leader == null) listOf(shuffled.take(1), shuffled.drop(1))
            else listOf(listOf(leader), shuffled.filter { it != leader })
        } to 30,
        {
            val cutAt = random.nextInt(1, shuffled.size)
            listOf(shuffled.take(cutAt), shuffled.drop(cutAt))
        } to 30,
        {
            val first = random.nextInt(1, shuffled.size - 1)
            val second = random.nextInt(first + 1, shuffled.size)
            listOf(shuffled.take(first), shuffled.subList(first, second), shuffled.drop(second))
        } to if (shuffled.size >= 3) 15 else 0,
        {
            val listed = random.nextInt(2, shuffled.size + 1)
            listOf(shuffled.take(1), shuffled.subList(1, listed))
        } to 15,
        { shuffled.map { listOf(it) } } to 6,
        { listOf(shuffled) } to 4,
    )()
}

fun badPartition(): String {
    val members = sim.nodes.map { it.name }
    val first = pick(members)
    return weighted(
        { "PARTITION $first,${pick(members)},$first" } to 3,
        { "PARTITION $first ${members.filter { it != first }.joinToString(",")},$first" } to 2,
        { "PARTITION $first ${pick(ghosts)}" } to 3,
        { "PARTITION ${pick(ghosts)},${pick(ghosts)}" } to 1,
        { "PARTITION $first, ${pick(members)}" } to 2,
        { "PARTITION $first,,${pick(members)}" } to 1,
        { "PARTITION ,$first" } to 1,
        { "PARTITION ${pick(BAD_NAMES)}" } to 1,
        { "PARTITION" } to 2,
    )()
}

fun errorLine(): String {
    val member = randomName()
    val other = randomName()
    return weighted(
        { "NODE $member ${timeoutValue()}" } to 4,
        { "NODE ${pick(ghosts)} ${timeoutValue()}" } to if (sim.now > 0) 3 else 0,
        { "NODE ${pick(BAD_NAMES)} 5" } to 3,
        { "NODE ${pick(ghosts)} ${pick(BAD_NUMBERS)}" } to 3,
        { "NODE ${pick(ghosts)}" } to 2,
        { "NODE ${pick(ghosts)} ${List(11) { random.nextInt(1, 30) }.joinToString(" ")}" } to 2,
        { "TICK ${pick(BAD_NUMBERS)}" } to 6,
        { "TICK" } to 2,
        { "TICK 1 2" } to 2,
        { "TICK 1\r" } to 1,
        { "DELAY $member $member ${random.nextInt(1, 9)}" } to 3,
        { "DELAY $member ${pick(ghosts)} ${random.nextInt(1, 9)}" } to 3,
        { "DELAY ${pick(ghosts)} ${pick(ghosts)} 3" } to 1,
        { "DELAY $member $other ${pick(BAD_NUMBERS)}" } to 3,
        { "DELAY $member $other" } to 1,
        { "DELAY ${pick(BAD_NAMES)} $other 2" } to 1,
        { "DROP $member $member 0" } to 2,
        { "DROP ${pick(ghosts)} $other 1" } to 2,
        { "DROP $member $other ${pick(listOf("-1", "1000001", "1.0", "01", "x"))}" } to 3,
        { badPartition() } to 10,
        { "HEAL ${pick(listOf("now", "all", member))}" } to 2,
        { "STATE ${pick(ghosts)}" } to 4,
        { "STATE" } to 2,
        { "STATE $member $other" } to 2,
        { "STATE ${pick(BAD_NAMES)}" } to 2,
        { "${pick(UNKNOWN_WORDS)} $member" } to 6,
        { "\tTICK 1" } to 1,
        { "\t" } to 1,
    )()
}

fun emitErrors(count: Int) {
    repeat(count) { emit(errorLine()) }
}

fun boundaryTicks(): Long {
    val targets = sim.nodes.filter { it.role != LEADER }.map { it.deadline }.toMutableList()
    sim.nextArrival()?.let { targets += it }
    val target = targets.filter { it > sim.now }.minOrNull() ?: return 1
    val offset = weighted(0L to 5, -1L to 3, 1L to 2)
    return (target - sim.now + offset).coerceAtLeast(1)
}

fun smallTicks(): Long = weighted(
    { random.nextLong(1, 6) } to 34,
    { random.nextLong(6, 31) } to 30,
    { boundaryTicks() } to 20,
    { random.nextLong(31, 201) } to 12,
    { random.nextLong(201, 1_001) } to 4,
)()

fun setUpCluster() {
    for (name in names) {
        createNode(name)
        if (chance(0.15)) state(pick(sim.nodes).name)
        if (!largeScale && chance(0.1)) emitErrors(1)
    }
    if (largeScale) return
    if (sim.nodes.size >= 2 && chance(0.35)) {
        val (from, to) = randomLink()
        if (chance(0.5)) setDelay(from, to, random.nextLong(1, 6)) else setDrops(from, to, random.nextLong(0, 4))
    }
    if (sim.nodes.size >= 3 && chance(0.15)) partition(randomGroups())
    if (chance(0.3)) emit("NODE ${pick(names)} ${timeoutValue()}")
    if (chance(0.3)) state(pick(names))
}

fun networkChange() {
    if (sim.nodes.size < 2) return tick(smallTicks())
    when (weighted("partition" to 26, "heal" to 20, "delay" to 24, "drop" to 18, "reset" to 12)) {
        "partition" -> if (sim.nodes.size >= 2) partition(randomGroups())
        "heal" -> heal()
        "delay" -> {
            val (from, to) = randomLink()
            val ticks = weighted({ random.nextLong(1, 9) } to 70, { random.nextLong(9, 41) } to 20, { 1L } to 10)()
            setDelay(from, to, ticks)
        }
        "drop" -> {
            val (from, to) = randomLink()
            val count = weighted(
                { random.nextLong(1, 6) } to 60,
                { 0L } to 15,
                { random.nextLong(6, 60) } to 20,
                { 1_000_000L } to 5,
            )()
            setDrops(from, to, count)
        }
        else -> {
            val (from, to) = randomLink()
            if (chance(0.5)) setDelay(from, to, 1) else setDrops(from, to, 0)
        }
    }
}

fun runSmall() {
    setUpCluster()
    if (chance(0.2)) emit("")
    while (sim.now < options.ticks || asked < options.queries) {
        when (
            weighted(
                "tick" to if (sim.now < options.ticks) 40 else 2,
                "state" to if (asked < options.queries) 28 else 4,
                "network" to 18,
                "errors" to 6,
                "blank" to 1,
                "late-node" to 1,
            )
        ) {
            "tick" -> tick(minOf(smallTicks(), 1_000_000L))
            "state" -> repeat(weighted(1 to 70, 2 to 20, sim.nodes.size to 10)) { state(randomName()) }
            "network" -> networkChange()
            "errors" -> emitErrors(random.nextInt(1, 3))
            "blank" -> emit(pick(listOf("", "   ", " ")))
            else -> if (sim.now > 0) emit("NODE ${pick(ghosts)} ${timeoutValue()}")
        }
    }
    for (node in sim.nodes) state(node.name)
}

var slowTarget: SimNode? = null

// 리더로 가는 응답 링크만 늦춰 선거를 흔들지 않고 아직 도착하지 않은 메시지를 쌓는다
fun slowDownRepliesTo(target: SimNode?) {
    val previous = slowTarget
    if (previous === target) return
    if (previous != null) {
        for (node in sim.nodes) {
            if (node !== previous && sim.delayOf(node.id, previous.id) > 1) setDelay(node.name, previous.name, 1)
        }
    }
    if (target != null) {
        for (node in sim.nodes) if (node !== target) setDelay(node.name, target.name, random.nextLong(30_000, 80_001))
    }
    slowTarget = target
}

fun largeTicks(stableUntil: Long) {
    val budget = stableUntil - sim.now
    if (budget <= 0) return
    val ticks = weighted(
        { random.nextLong(1, 40) } to 50,
        { random.nextLong(40, 400) } to 40,
        { boundaryTicks() } to 10,
    )()
    tick(minOf(budget, ticks))
}

fun queryBurst() {
    repeat(weighted(1 to 50, 2 to 30, sim.nodes.size to 20)) { state(randomName()) }
}

fun disruption() {
    val leader = sim.leader()
    val others = sim.nodes.filter { it !== leader }.map { it.name }.shuffled(random)
    when (weighted("isolate-leader" to 30, "split" to 20, "drop-heartbeats" to 25, "minority" to 15, "scatter" to 10)) {
        "isolate-leader" -> {
            if (leader == null) return
            partition(listOf(listOf(leader.name), others))
            phase(random.nextLong(300, 2_500))
            heal()
        }
        "split" -> {
            val cutAt = random.nextInt(1, sim.nodes.size)
            val shuffled = sim.nodes.map { it.name }.shuffled(random)
            partition(listOf(shuffled.take(cutAt), shuffled.drop(cutAt)))
            phase(random.nextLong(300, 3_000))
            heal()
        }
        "drop-heartbeats" -> {
            if (leader == null) return
            val victim = pick(others)
            setDrops(leader.name, victim, random.nextLong(200, 2_500))
            phase(random.nextLong(200, 2_000))
            setDrops(leader.name, victim, 0)
        }
        "minority" -> {
            val minority = others.take(sim.nodes.size / 2)
            partition(listOf(minority, sim.nodes.map { it.name }.filter { it !in minority }))
            phase(random.nextLong(500, 4_000))
            heal()
        }
        else -> {
            partition(sim.nodes.map { listOf(it.name) })
            phase(random.nextLong(300, 1_500))
            heal()
        }
    }
}

fun phase(length: Long) {
    val end = minOf(sim.now + length, options.ticks)
    while (sim.now < end) {
        largeTicks(end)
        while (asked < options.queries * sim.now / options.ticks) queryBurst()
        if (chance(0.01)) emitErrors(1)
    }
}

fun runLarge() {
    setUpCluster()
    val slowStretches = listOf(0.1 to 0.45, 0.55 to 0.9).map { (start, end) ->
        (options.ticks * start).toLong() to (options.ticks * end).toLong()
    }
    while (sim.now < options.ticks) {
        val inSlowStretch = slowStretches.any { (start, end) -> sim.now in start until end }
        slowDownRepliesTo(if (inSlowStretch) sim.leader() else null)
        when (weighted("stable" to 66, "disrupt" to 30, "errors" to 4)) {
            "stable" -> phase(random.nextLong(100, 1_500))
            "disrupt" -> disruption()
            else -> emitErrors(random.nextInt(1, 4))
        }
    }
    for (node in sim.nodes) state(node.name)
}

if (largeScale) runLarge() else runSmall()
System.out.write(output.toString().encodeToByteArray())
System.out.flush()
