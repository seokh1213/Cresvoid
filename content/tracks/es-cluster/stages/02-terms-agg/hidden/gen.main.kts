import kotlin.random.Random

data class Options(val shards: Int, val puts: Int, val dels: Int, val searches: Int, val terms: Int, val seed: Long)

fun parseOptions(arguments: Array<String>): Options {
    val values = mutableMapOf(
        "--shards" to "0",
        "--puts" to "250",
        "--dels" to "40",
        "--searches" to "60",
        "--terms" to "80",
        "--seed" to "1",
    )
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
        terms = values.getValue("--terms").toInt(),
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
val NAMED_BRANDS = listOf("acme", "zeta", "nova", "orbit", "peak", "terra", "lumen", "brio", "k2", "a1", "ab", "abc", "a10", "b")

fun syntheticWord(number: Int): String =
    SYLLABLES[number % 16] + SYLLABLES[number / 16 % 16] + SYLLABLES[number / 256 % 16]

fun modelCode(number: Int): String = SYLLABLES[number % 16] + (number / 16 + 10)

val midWords: List<String> = PRODUCT_WORDS + (0 until if (largeScale) 3000 else 40).map(::syntheticWord)
val midSampler = ZipfSampler(midWords, if (largeScale) 0.8 else 1.0)
val headSampler = ZipfSampler(HEAD_WORDS, 1.0)
val tailSpace = if (largeScale) 60_000 else 30

val globalBrands: List<String> = NAMED_BRANDS + (0 until if (largeScale) 2000 else 3).map { "g" + syntheticWord(it) }
val globalBrandSampler = ZipfSampler(globalBrands, if (largeScale) 0.9 else 0.8)
val houseBrandSpace = if (largeScale) 60_000 else 5
val tailBrandSpace = if (largeScale) 400_000 else 12

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

val shardCount = if (options.shards > 0) options.shards else weighted(2 to 5, 3 to 5, 1 to 3, 4 to 3, 5 to 2, 7 to 1)
val model = IndexModel(shardCount)
val sellers: List<String> = (1..if (largeScale) 30_000 else 9).map { "store$it" } + listOf("seller1", "a", "b", "d", "zz9")
val sellerSampler = ZipfSampler(sellers, if (largeScale) 0.9 else 0.7)
val lastRoutingOfId = HashMap<Int, String>()
val knownIds = ArrayList<Int>()
var nextNewId = 1

fun themeWord(routing: String): String = midWords[Math.floorMod(routing.hashCode(), if (largeScale) 400 else PRODUCT_WORDS.size)]

fun houseBrand(routing: String): String = "h" + Math.floorMod(routing.hashCode() * 31 + 7, houseBrandSpace)

fun chooseBrand(routing: String): String =
    weighted(
        { houseBrand(routing) } to 35,
        { globalBrandSampler.sample() } to 25,
        { "t" + random.nextInt(tailBrandSpace) } to 40,
    )()

fun titleTokens(routing: String): List<String> {
    val tokens = ArrayList<String>()
    if (largeScale) {
        val target = 1 + random.nextInt(4)
        if (chance(0.5)) tokens += headSampler.sample()
        if (chance(0.3)) tokens += themeWord(routing)
        while (tokens.size < target) tokens += midSampler.sample()
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
    if (knownIds.isNotEmpty() && chance(if (largeScale) 0.15 else 0.3)) return pick(knownIds)
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

fun spacing(): String = if (!largeScale && chance(0.05)) "   " else " "

fun putDocument() {
    val id = chooseId()
    val previousRouting = lastRoutingOfId[id]
    val routingArgument = when {
        previousRouting != null && chance(0.8) -> previousRouting
        chance(0.25) -> "-"
        else -> sellerSampler.sample()
    }
    lastRoutingOfId[id] = routingArgument
    val routing = if (routingArgument == "-") id.toString() else routingArgument
    val tokens = titleTokens(routing)
    model.put(DocumentState(id, routingArgument, tokens.toSet()))
    val price = weighted({ random.nextInt(1, 100) * 100 } to 8, { 0 } to 1, { random.nextInt(1_000_000_000) } to 1)()
    val fields = listOf("PUT", id.toString(), routingArgument, chooseBrand(routing), price.toString())
    emit(fields.joinToString(spacing()) + " " + renderTitle(tokens))
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
        val token = weighted(
            { midSampler.sample() } to 14,
            { pick(midWords) } to 6,
            { modelCode(random.nextInt(tailSpace)) } to 5,
            { pick(MISSING_WORDS) } to 2,
            { headSampler.sample() } to 6,
        )()
        if (!largeScale || model.frequency(token) <= remainingBudget) return token
    }
    return pick(MISSING_WORDS)
}

fun queryValue(): String {
    val tokens = ArrayList<String>()
    var resultBudget = 2000
    repeat(weighted(1 to 5, 2 to 3, 3 to 2)) {
        val token = queryToken(resultBudget)
        if (token !in tokens) resultBudget -= model.frequency(token)
        tokens += token
    }
    if (!largeScale && chance(0.08)) tokens += tokens.first()
    return tokens.joinToString("") { pick(QUERY_SEPARATORS) + renderToken(it) }.drop(1)
}

fun searchLine() {
    val parameters = ArrayList<String>()
    if (largeScale || chance(0.88)) {
        val value = if (!largeScale && chance(0.07)) pick(listOf("", "!!!", ",,", "한글", "-_-", "a=b")) else queryValue()
        parameters += "q=$value"
    }
    weighted<String?>(null to 8, "0" to 1, "1" to 2, "2" to 3, "3" to 3, "5" to 2, "10" to 1, "10000" to 1)
        ?.let { parameters += "size=$it" }
    weighted<String?>(null to 5, "query_then_fetch" to 2, "dfs_query_then_fetch" to 4)?.let { parameters += "search_type=$it" }
    parameters.shuffle(random)
    emit(listOf("SEARCH").plus(parameters).joinToString(spacing()))
}

fun termsLine() {
    val parameters = ArrayList<String>()
    val size = if (largeScale) {
        weighted<Int?>(null to 1, 1 to 4, 2 to 3, 3 to 3, 5 to 2, 8 to 1, 10 to 1)
    } else {
        weighted<Int?>(null to 3, 1 to 3, 2 to 4, 3 to 4, 4 to 2, 5 to 2, 6 to 1, 1000 to 1)
    }
    size?.let { parameters += "size=$it" }
    val shardSize = if (largeScale) {
        weighted<Int?>(null to 4, 1 to 1, 2 to 1, 3 to 1, 5 to 2, 10 to 2, 20 to 1, 30 to 1)
    } else {
        weighted<Int?>(null to 3, 1 to 3, 2 to 4, 3 to 3, 4 to 2, 6 to 1, 10_000 to 1)
    }
    shardSize?.let { parameters += "shard_size=$it" }
    parameters.shuffle(random)
    emit(listOf("TERMS").plus(parameters).joinToString(spacing()))
}

val MALFORMED_SEARCHES = listOf(
    "SEARCH trail",
    "SEARCH q=trail size",
    "SEARCH =trail",
    "SEARCH Q=trail",
    "SEARCH q=a q=b",
    "SEARCH size=10001",
    "SEARCH size=01",
    "SEARCH search_type=dfs",
    "SEARCH q=red shard_size=2",
    "SEARCH q=red limit=2",
)

val MALFORMED_TERMS = listOf(
    "TERMS 5",
    "TERMS field=brand",
    "TERMS size=0",
    "TERMS size=1001",
    "TERMS shard_size=010",
    "TERMS size=2 size=3",
    "TERMS shard_size=0",
    "TERMS shard_size=10001",
    "TERMS size=-1",
    "TERMS =3",
    "TERMS size",
    "TERMS size=1e1",
    "TERMS size=99999999999999999999",
    "TERMS shard_size=",
    "TERMS Size=3",
    "TERMS size=3\r",
    "TERMS size=3 shard_size=2 shard_size=4",
    "TERMS q=red",
)

fun malformedLine() {
    weighted(
        { emit(pick(MALFORMED_TERMS)) } to 7,
        { emit(pick(MALFORMED_SEARCHES)) } to 2,
        {
            emit(
                pick(
                    listOf(
                        "PUT 1 Store1 acme 100 x", "PUT 1 a Acme 100 x", "PUT 1 a acme 0100 x", "PUT 1 a acme",
                        "PUT 1 a ${"b".repeat(33)} 100 x", "PUT 1 a a-b 100 x", "PUT 1 a acme -5 x",
                    ),
                ),
            )
        } to 3,
        { emit(pick(listOf("DEL 1", "DEL 1 a b", "DEL 01 a", "STATS now", "CREATE 3", "CREATE"))) } to 2,
        { emit(pick(listOf("terms size=2", "Terms", "TERM", "AGG size=2", "TERMS\r", "\tTERMS", "search q=red"))) } to 3,
        { emit(pick(listOf("", "   ", "  \t  "))) } to 1,
    )()
}

val NO_INDEX_LINES = listOf(
    "TERMS", "TERMS size=0", "TERMS 5", "TERMS size=2 shard_size=1", "STATS", "SEARCH q=shoes",
    "PUT 1 a acme 100 trail shoes", "DEL 1 a", "CREATE 0", "CREATE 65", "CREATE", "terms",
)

fun generateMixed() {
    repeat(random.nextInt(2, 6)) { emit(pick(NO_INDEX_LINES)) }
    emit(listOf("CREATE", shardCount.toString()).joinToString(spacing()))
    if (chance(0.5)) emit(weighted("TERMS" to 2, "TERMS size=3" to 1, "TERMS shard_size=1" to 1))
    var puts = options.puts
    var dels = options.dels
    var searches = options.searches
    var terms = options.terms
    var malformed = maxOf(4, (options.puts + options.dels + options.searches + options.terms) / 14)
    var stats = maxOf(1, options.searches / 15)
    while (puts + dels + searches + terms + malformed + stats > 0) {
        val warmingUp = puts > 0 && options.puts - puts < 8
        val putWeight = puts * 3
        val delWeight = if (warmingUp) 0 else dels * 2
        val searchWeight = if (warmingUp) 0 else searches * 2
        val termsWeight = if (warmingUp) 0 else terms * 3
        val malformedWeight = if (warmingUp) 0 else malformed
        val statsWeight = if (warmingUp) 0 else stats
        val choice = random.nextInt(putWeight + delWeight + searchWeight + termsWeight + malformedWeight + statsWeight)
        when {
            choice < putWeight -> { putDocument(); puts-- }
            choice < putWeight + delWeight -> { deleteDocument(); dels-- }
            choice < putWeight + delWeight + searchWeight -> { searchLine(); searches-- }
            choice < putWeight + delWeight + searchWeight + termsWeight -> { termsLine(); terms-- }
            choice < putWeight + delWeight + searchWeight + termsWeight + malformedWeight -> { malformedLine(); malformed-- }
            else -> { emit("STATS"); stats-- }
        }
    }
    emit("TERMS size=1000 shard_size=10000")
}

fun generateLargeScale() {
    emit("CREATE $shardCount")
    val bulkPuts = options.puts * 8 / 10
    val bulkDels = options.dels / 4
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
    var terms = options.terms
    while (puts + dels + searches + terms > 0) {
        val choice = random.nextInt(puts + dels + searches + terms)
        when {
            choice < puts -> { putDocument(); puts-- }
            choice < puts + dels -> { deleteDocument(); dels-- }
            choice < puts + dels + searches -> { searchLine(); searches-- }
            else -> { termsLine(); terms-- }
        }
    }
}

if (largeScale) generateLargeScale() else generateMixed()
System.out.write(output.toString().encodeToByteArray())
System.out.flush()
