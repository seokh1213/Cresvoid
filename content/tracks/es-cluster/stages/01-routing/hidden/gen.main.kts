import kotlin.random.Random

data class Options(val shards: Int, val puts: Int, val dels: Int, val searches: Int, val seed: Long)

fun parseOptions(arguments: Array<String>): Options {
    val values = mutableMapOf("--shards" to "0", "--puts" to "250", "--dels" to "40", "--searches" to "120", "--seed" to "1")
    require(arguments.size % 2 == 0) { "옵션은 --이름 값 쌍으로 준다: ${arguments.joinToString(" ")}" }
    for ((name, value) in arguments.toList().chunked(2)) {
        require(name in values) { "모르는 옵션: $name" }
        values[name] = value
    }
    return Options(
        shards = values.getValue("--shards").toInt(),
        puts = values.getValue("--puts").toInt(),
        dels = values.getValue("--dels").toInt(),
        searches = values.getValue("--searches").toInt(),
        seed = values.getValue("--seed").toLong(),
    )
}

val options = parseOptions(args)
val random = Random(options.seed)
val largeScale = options.puts >= 10_000
val output = StringBuilder()

fun chance(probability: Double): Boolean = random.nextDouble() < probability

fun emit(line: String) {
    val padded = if (!largeScale && chance(0.03)) "  $line " else line
    output.append(padded).append('\n')
}

fun <T> pick(items: List<T>): T = items[random.nextInt(items.size)]

fun <T> weighted(vararg choices: Pair<T, Int>): T {
    var target = random.nextInt(choices.sumOf { it.second })
    for ((value, weight) in choices) {
        if (target < weight) return value
        target -= weight
    }
    error("가중치 합이 맞지 않는다")
}

class ZipfSampler<T>(private val values: List<T>, exponent: Double) {
    private val cumulativeWeights = values.indices.map { 1.0 / Math.pow(it + 1.0, exponent) }.runningReduce(Double::plus)

    fun sample(): T {
        val target = random.nextDouble() * cumulativeWeights.last()
        var low = 0
        var high = cumulativeWeights.lastIndex
        while (low < high) {
            val middle = (low + high) / 2
            if (cumulativeWeights[middle] > target) high = middle else low = middle + 1
        }
        return values[low]
    }
}

class Pool<T : Any> {
    private val items = ArrayList<T>()
    private val positions = HashMap<T, Int>()

    fun add(item: T) {
        if (item in positions) return
        positions[item] = items.size
        items += item
    }

    fun remove(item: T) {
        val index = positions.remove(item) ?: return
        val last = items.removeAt(items.lastIndex)
        if (index < items.size) {
            items[index] = last
            positions[last] = index
        }
    }

    fun randomOrNull(): T? = if (items.isEmpty()) null else items[random.nextInt(items.size)]
}

val HEAD_WORDS = listOf(
    "shoes", "new", "sale", "men", "women", "black", "white", "kids", "classic", "pro",
    "set", "pack", "lite", "max", "original", "premium", "outdoor", "sports", "edition", "2024",
)
val PRODUCT_WORDS = listOf(
    "trail", "running", "rain", "jacket", "boots", "coat", "socks", "red", "blue", "green",
    "wool", "cotton", "leather", "sneaker", "sandal", "hiking", "waterproof", "light", "warm", "slim",
    "wide", "gore", "tex", "fleece", "down", "vest", "cap", "bag", "tent", "mat",
)
val SYLLABLES = listOf("ka", "lo", "mi", "ra", "tu", "ne", "so", "vi", "de", "ga", "po", "ri", "xe", "zu", "bo", "fe")
val MISSING_WORDS = listOf("zzz", "qwerty", "nothing", "x9q", "absent", "void")
val SEPARATORS = listOf(" ", " ", " ", " ", " ", "-", "_", ", ", " / ", " · ", " 한정 ", "+", " (", ") ", "!")
val QUERY_SEPARATORS = listOf(",", ",", "+", "+", "-", "_", ".", "/", "|", "한")
val BRANDS = listOf("acme", "zeta", "nova", "orbit", "peak", "terra", "lumen", "brio", "k2", "a1")

fun syntheticWord(number: Int): String =
    SYLLABLES[number % 16] + SYLLABLES[number / 16 % 16] + SYLLABLES[number / 256 % 16]

fun modelCode(number: Int): String = SYLLABLES[number % 16] + (number / 16 + 10)

val midWords: List<String> = PRODUCT_WORDS + (0 until if (largeScale) 3000 else 40).map(::syntheticWord)
val midSampler = ZipfSampler(midWords, if (largeScale) 0.8 else 1.0)
val headSampler = ZipfSampler(HEAD_WORDS, 1.0)
val tailSpace = if (largeScale) 60_000 else 30

fun renderToken(token: String): String =
    weighted(
        { token } to 80,
        { token.replaceFirstChar(Char::uppercaseChar) } to 15,
        { token.uppercase() } to 5,
    )()

class DocumentState(val id: Int, val routingArgument: String, val tokens: Set<String>) {
    val routing: String = if (routingArgument == "-") id.toString() else routingArgument
}

class IndexModel(val shardCount: Int) {
    private val shards = List(shardCount) { HashMap<Int, DocumentState>() }
    private val documentFrequency = HashMap<String, Int>()
    private val documents = Pool<Long>()

    fun shardOf(routing: String): Int =
        (routing.fold(0L) { hash, character -> (hash * 31 + character.code) % 4_294_967_296L } % shardCount).toInt()

    fun put(document: DocumentState) {
        val shard = shardOf(document.routing)
        shards[shard][document.id]?.let { forget(shard, it) }
        shards[shard][document.id] = document
        document.tokens.forEach { documentFrequency.merge(it, 1, Int::plus) }
        documents.add(key(shard, document.id))
    }

    fun delete(shard: Int, id: Int) {
        shards[shard][id]?.let { forget(shard, it) }
    }

    fun randomDocument(): Pair<Int, DocumentState>? {
        val key = documents.randomOrNull() ?: return null
        val shard = (key / ID_SPACE).toInt()
        return shard to shards[shard].getValue((key % ID_SPACE).toInt())
    }

    fun frequency(token: String): Int = documentFrequency[token] ?: 0

    private fun forget(shard: Int, document: DocumentState) {
        shards[shard].remove(document.id)
        document.tokens.forEach { token ->
            documentFrequency.merge(token, -1) { before, change -> (before + change).takeIf { it > 0 } }
        }
        documents.remove(key(shard, document.id))
    }

    private fun key(shard: Int, id: Int): Long = shard * ID_SPACE + id

    companion object {
        const val ID_SPACE = 1_000_000_000L
    }
}

val shardCount = if (options.shards > 0) options.shards else weighted(2 to 5, 3 to 5, 1 to 2, 4 to 3, 5 to 2, 7 to 1, 64 to 1)
val model = IndexModel(shardCount)
val sellers: List<String> = (1..if (largeScale) 4000 else 9).map { "store$it" } + listOf("seller1", "a", "b", "d", "zz9")
val sellerSampler = ZipfSampler(sellers, if (largeScale) 1.0 else 0.7)
val lastRoutingOfId = HashMap<Int, String>()
val knownIds = ArrayList<Int>()
var nextNewId = 1

fun themeWord(routing: String): String = midWords[Math.floorMod(routing.hashCode(), if (largeScale) 400 else PRODUCT_WORDS.size)]

fun titleTokens(routing: String): List<String> {
    val tokens = ArrayList<String>()
    if (largeScale) {
        val target = 4 + random.nextInt(9)
        if (chance(0.8)) tokens += headSampler.sample()
        if (chance(0.25)) tokens += headSampler.sample()
        if (chance(0.5)) tokens += modelCode(random.nextInt(tailSpace))
        if (chance(0.3)) tokens += themeWord(routing)
        while (tokens.size < target) tokens += if (tokens.isNotEmpty() && chance(0.06)) tokens.random(random) else midSampler.sample()
        while (tokens.size > target) tokens.removeAt(tokens.lastIndex)
    } else {
        val target = weighted(0 to 1, 1 to 3, 2 to 6, 3 to 6, 4 to 4, 6 to 2, 9 to 1)
        repeat(target) {
            tokens += weighted(
                { headSampler.sample() } to 3,
                { midSampler.sample() } to 7,
                { themeWord(routing) } to 3,
                { modelCode(random.nextInt(tailSpace)) } to 1,
                { tokens.lastOrNull() ?: midSampler.sample() } to 1,
            )()
        }
    }
    tokens.shuffle(random)
    return tokens
}

fun renderTitle(tokens: List<String>): String {
    if (tokens.isEmpty()) return if (largeScale) "" else pick(listOf("", "", "!!!", "한글 제목", "-- __ ++"))
    val text = StringBuilder(renderToken(tokens.first()))
    for (token in tokens.drop(1)) text.append(if (largeScale) " " else pick(SEPARATORS)).append(renderToken(token))
    if (!largeScale && chance(0.05)) text.append(pick(listOf("\t", "\r", " 🙂", "…", " (정품)")))
    return text.toString()
}

fun chooseId(): Int {
    if (knownIds.isNotEmpty() && chance(if (largeScale) 0.12 else 0.3)) return pick(knownIds)
    val id = if (!largeScale && chance(0.03)) pick(listOf(999_999_999, 100_000_000, 123_456_789)) else nextNewId++
    if (id !in lastRoutingOfId) knownIds += id
    return id
}

fun alternativeRouting(shard: Int, avoid: String): String? {
    repeat(40) {
        val candidate = pick(sellers)
        if (candidate != avoid && model.shardOf(candidate) == shard) return candidate
    }
    return null
}

fun putDocument() {
    val id = chooseId()
    val previousRouting = lastRoutingOfId[id]
    val routingArgument = when {
        previousRouting != null && chance(0.8) -> previousRouting
        chance(0.25) -> "-"
        else -> sellerSampler.sample()
    }
    lastRoutingOfId[id] = routingArgument
    val tokens = titleTokens(if (routingArgument == "-") id.toString() else routingArgument)
    model.put(DocumentState(id, routingArgument, tokens.toSet()))
    val price = weighted({ random.nextInt(1, 100) * 100 } to 8, { 0 } to 1, { random.nextInt(1_000_000_000) } to 1)()
    emit(listOf("PUT", id.toString(), routingArgument, pick(BRANDS), price.toString()).joinToString(spacing()) + " " + renderTitle(tokens))
}

fun deleteDocument() {
    val target = model.randomDocument()
    val (id, routingArgument) = if (target == null || chance(0.1)) {
        random.nextInt(1, nextNewId + 5) to sellerSampler.sample()
    } else {
        val (shard, document) = target
        document.id to weighted(
            { document.routingArgument } to 75,
            { alternativeRouting(shard, document.routing) ?: document.routingArgument } to 12,
            { sellerSampler.sample() } to 13,
        )()
    }
    val routing = if (routingArgument == "-") id.toString() else routingArgument
    model.delete(model.shardOf(routing), id)
    emit(listOf("DEL", id.toString(), routingArgument).joinToString(spacing()))
}

fun queryToken(remainingBudget: Int): String {
    repeat(30) {
        val token = if (largeScale) {
            weighted(
                { midSampler.sample() } to 10,
                { pick(midWords) } to 20,
                { modelCode(random.nextInt(tailSpace)) } to 12,
                { pick(MISSING_WORDS) } to 3,
                { headSampler.sample() } to 1,
            )()
        } else {
            weighted(
                { midSampler.sample() } to 14,
                { pick(midWords) } to 6,
                { modelCode(random.nextInt(tailSpace)) } to 5,
                { pick(MISSING_WORDS) } to 2,
                { headSampler.sample() } to 6,
            )()
        }
        if (!largeScale || model.frequency(token) <= remainingBudget) return token
    }
    return pick(MISSING_WORDS)
}

fun queryValue(): String {
    val tokens = ArrayList<String>()
    var resultBudget = 500
    repeat(weighted(1 to 5, 2 to 3, 3 to 2)) {
        val token = queryToken(resultBudget)
        if (token !in tokens) resultBudget -= model.frequency(token)
        tokens += token
    }
    if (!largeScale && chance(0.08)) tokens += tokens.first()
    return tokens.joinToString("") { pick(QUERY_SEPARATORS) + renderToken(it) }.drop(1)
}

fun spacing(): String = if (!largeScale && chance(0.05)) "   " else " "

fun searchLine() {
    val parameters = ArrayList<String>()
    if (largeScale || chance(0.88)) {
        val value = if (!largeScale && chance(0.07)) pick(listOf("", "!!!", ",,", "한글", "-_-", "a=b")) else queryValue()
        parameters += "q=$value"
    }
    val size = if (largeScale) {
        weighted<String?>(null to 3, "1" to 1, "2" to 1, "3" to 2, "5" to 2, "10" to 3, "0" to 1)
    } else {
        weighted<String?>(null to 8, "0" to 1, "1" to 2, "2" to 3, "3" to 3, "5" to 2, "10" to 1, "30" to 1, "10000" to 1)
    }
    if (size != null) parameters += "size=$size"
    weighted<String?>(null to 5, "query_then_fetch" to 2, "dfs_query_then_fetch" to 4)?.let { parameters += "search_type=$it" }
    parameters.shuffle(random)
    emit(listOf("SEARCH").plus(parameters).joinToString(spacing()))
}

val MALFORMED_SEARCHES = listOf(
    "SEARCH trail",
    "SEARCH q=trail size",
    "SEARCH =trail",
    "SEARCH Q=trail",
    "SEARCH query=trail",
    "SEARCH q=a q=b",
    "SEARCH size=3 size=3",
    "SEARCH q=rain size=01",
    "SEARCH size=-1",
    "SEARCH size=10001",
    "SEARCH size=1e3",
    "SEARCH size=",
    "SEARCH size=99999999999999999999",
    "SEARCH size=+5",
    "SEARCH search_type=dfs",
    "SEARCH search_type=QUERY_THEN_FETCH",
    "SEARCH search_type=",
    "SEARCH q=red search_type=dfs_query_then_fetch search_type=query_then_fetch",
    "SEARCH offset=3",
    "SEARCH q=red limit=2",
    "SEARCH q=trail size=5\r",
)

fun malformedPut() {
    val title = renderTitle(titleTokens("a"))
    emit(
        pick(
            listOf(
                "PUT 1 Store1 acme 100 $title",
                "PUT 1 a Acme 100 $title",
                "PUT 1 a acme 0100 $title",
                "PUT 1 a acme",
                "PUT",
                "PUT 0 a acme 100 $title",
                "PUT 01 a acme 100 $title",
                "PUT 1000000000 a acme 100 $title",
                "PUT -1 a acme 100 $title",
                "PUT 1 a acme -5 $title",
                "PUT 1 a acme 1000000000 $title",
                "PUT 1 a acme 1.5 $title",
                "PUT 1 a_b acme 100 $title",
                "PUT 1 ${"a".repeat(33)} acme 100 $title",
                "PUT 1 a ${"b".repeat(33)} 100 $title",
                "PUT 1 -- acme 100 $title",
                "PUT 1 a acme 100\t$title",
                "PUT x1 - acme 100 $title",
            ),
        ),
    )
}

fun malformedLine() {
    weighted(
        { emit(pick(MALFORMED_SEARCHES)) } to 6,
        { malformedPut() } to 5,
        { emit(pick(listOf("DEL 1", "DEL", "DEL 1 a b", "DEL 01 a", "DEL 1 A", "DEL 0 -", "DEL a 1"))) } to 3,
        { emit(pick(listOf("STATS now", "STATS 1", "STATS -"))) } to 2,
        { emit(pick(listOf("CREATE 3", "CREATE", "CREATE 0", "CREATE x"))) } to 2,
        {
            emit(
                pick(
                    listOf(
                        "put 1 a acme 100 x", "Put 1 a acme 100 x", "search q=red", "stats", "STAT", "GET 1",
                        "SEARCHES", "\tSTATS", "STATS\r", "DELETE 1 a", "INDEX", "terms size=2",
                    ),
                ),
            )
        } to 4,
        { emit(pick(listOf("", "   ", "  \t  "))) } to 2,
    )()
}

val NO_INDEX_LINES = listOf(
    "STATS", "STATS extra", "SEARCH q=shoes", "SEARCH", "SEARCH size=01", "SEARCH q=shoes from=5",
    "PUT 1 a acme 100 trail shoes", "PUT x", "DEL 1 a", "DEL", "CREATE 0", "CREATE 03", "CREATE 65",
    "CREATE 2 3", "CREATE", "CREATE -1", "create 2", "",
)

fun generateMixed() {
    repeat(random.nextInt(2, 6)) { emit(pick(NO_INDEX_LINES)) }
    emit(listOf("CREATE", shardCount.toString()).joinToString(spacing()))
    var puts = options.puts
    var dels = options.dels
    var searches = options.searches
    var malformed = maxOf(4, (options.puts + options.dels + options.searches) / 12)
    var stats = maxOf(2, options.searches / 10)
    while (puts + dels + searches + malformed + stats > 0) {
        val warmingUp = puts > 0 && options.puts - puts < 8
        val putWeight = puts * 3
        val delWeight = if (warmingUp) 0 else dels * 2
        val searchWeight = if (warmingUp) 0 else searches * 3
        val malformedWeight = if (warmingUp) 0 else malformed
        val statsWeight = if (warmingUp) 0 else stats
        val choice = random.nextInt(putWeight + delWeight + searchWeight + malformedWeight + statsWeight)
        when {
            choice < putWeight -> { putDocument(); puts-- }
            choice < putWeight + delWeight -> { deleteDocument(); dels-- }
            choice < putWeight + delWeight + searchWeight -> { searchLine(); searches-- }
            choice < putWeight + delWeight + searchWeight + malformedWeight -> { malformedLine(); malformed-- }
            else -> { emit("STATS"); stats-- }
        }
    }
    emit("STATS")
}

fun generateLargeScale() {
    emit("CREATE $shardCount")
    val bulkPuts = options.puts * 7 / 10
    val bulkDels = options.dels / 5
    var bulkDeleted = 0
    repeat(bulkPuts) { index ->
        putDocument()
        if ((index + 1).toLong() * bulkDels / bulkPuts > bulkDeleted) {
            deleteDocument()
            bulkDeleted++
        }
    }
    var puts = options.puts - bulkPuts
    var dels = options.dels - bulkDels
    var searches = options.searches
    while (puts + dels + searches > 0) {
        val choice = random.nextInt(puts + dels + searches)
        when {
            choice < puts -> { putDocument(); puts-- }
            choice < puts + dels -> { deleteDocument(); dels-- }
            else -> { searchLine(); searches-- }
        }
    }
}

if (largeScale) generateLargeScale() else generateMixed()
System.out.write(output.toString().encodeToByteArray())
System.out.flush()
