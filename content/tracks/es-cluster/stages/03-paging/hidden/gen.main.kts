import kotlin.random.Random

data class Options(
    val shards: Int,
    val puts: Int,
    val dels: Int,
    val searches: Int,
    val terms: Int,
    val pits: Int,
    val pitSearches: Int,
    val mutations: Int,
    val seed: Long,
)

fun parseOptions(arguments: Array<String>): Options {
    val values = mutableMapOf(
        "--shards" to "0",
        "--puts" to "150",
        "--dels" to "30",
        "--searches" to "80",
        "--terms" to "15",
        "--pits" to "3",
        "--pit-searches" to "10",
        "--mutations" to "0",
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
        pits = values.getValue("--pits").toInt(),
        pitSearches = values.getValue("--pit-searches").toInt(),
        mutations = values.getValue("--mutations").toInt(),
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
val BRANDS = listOf("acme", "zeta", "nova", "orbit", "peak", "terra", "lumen", "brio", "k2", "a1", "ab", "abc")

fun syntheticWord(number: Int): String =
    SYLLABLES[number % 16] + SYLLABLES[number / 16 % 16] + SYLLABLES[number / 256 % 16]

fun modelCode(number: Int): String = SYLLABLES[number % 16] + (number / 16 + 10)

val midWords: List<String> = PRODUCT_WORDS + (0 until if (largeScale) 3000 else 40).map(::syntheticWord)
val midSampler = ZipfSampler(midWords, if (largeScale) 0.8 else 1.0)
val headSampler = ZipfSampler(HEAD_WORDS, 1.0)
val brandSampler = ZipfSampler(BRANDS, 1.0)
val tailSpace = if (largeScale) 60_000 else 30

fun renderToken(token: String): String =
    weighted(
        { token } to 80,
        { token.replaceFirstChar(Char::uppercaseChar) } to 15,
        { token.uppercase() } to 5,
    )()

class DocumentState(
    val id: Int,
    val routingArgument: String,
    val tokens: Set<String>,
    val price: Int,
    val shard: Int,
    val internalNumber: Int,
) {
    val shardDoc: Long get() = shard * 4_294_967_296L + internalNumber
}

class IndexModel(val shardCount: Int) {
    private val shards = List(shardCount) { HashMap<Int, DocumentState>() }
    private val nextInternalNumbers = IntArray(shardCount)
    private val documentFrequency = HashMap<String, Int>()
    private val documents = Pool<Long>()

    fun shardOf(routing: String): Int =
        (routing.fold(0L) { hash, character -> (hash * 31 + character.code) % 4_294_967_296L } % shardCount).toInt()

    fun put(id: Int, routingArgument: String, tokens: Set<String>, price: Int) {
        val shard = shardOf(if (routingArgument == "-") id.toString() else routingArgument)
        shards[shard][id]?.let { forget(it) }
        val document = DocumentState(id, routingArgument, tokens, price, shard, nextInternalNumbers[shard]++)
        shards[shard][id] = document
        tokens.forEach { documentFrequency.merge(it, 1, Int::plus) }
        documents.add(key(shard, id))
    }

    fun delete(shard: Int, id: Int) {
        shards[shard][id]?.let { forget(it) }
    }

    fun randomDocument(): DocumentState? {
        val key = documents.randomOrNull() ?: return null
        return shards[(key / ID_SPACE).toInt()].getValue((key % ID_SPACE).toInt())
    }

    fun snapshot(): List<DocumentState> = shards.flatMap { it.values }

    fun frequency(token: String): Int = documentFrequency[token] ?: 0

    private fun forget(document: DocumentState) {
        shards[document.shard].remove(document.id)
        document.tokens.forEach { token ->
            documentFrequency.merge(token, -1) { before, change -> (before + change).takeIf { it > 0 } }
        }
        documents.remove(key(document.shard, document.id))
    }

    private fun key(shard: Int, id: Int): Long = shard * ID_SPACE + id

    companion object {
        const val ID_SPACE = 1_000_000_000L
    }
}

data class SortSpec(val fieldName: String, val direction: String?) {
    val text: String get() = if (direction == null) fieldName else "$fieldName:$direction"
}

class SearchShape(val query: String?, val searchType: String?, val sort: List<SortSpec>?) {
    fun actualFields(withPit: Boolean): List<String> {
        val fields = sort?.map { it.fieldName } ?: listOf("_score")
        return if (withPit && "_shard_doc" !in fields) fields + "_shard_doc" else fields
    }

    fun parameters(): List<String> =
        listOfNotNull(
            query?.let { "q=$it" },
            searchType?.let { "search_type=$it" },
            sort?.let { keys -> "sort=" + keys.joinToString(",") { it.text } },
        )
}

class PitState(val id: Int, val documents: List<DocumentState>, val shapes: List<SearchShape>)

val shardCount = if (options.shards > 0) options.shards else weighted(2 to 5, 3 to 4, 1 to 2, 4 to 2, 5 to 2)
val model = IndexModel(shardCount)
val sellers: List<String> = (1..if (largeScale) 4000 else 9).map { "store$it" } + listOf("seller1", "a", "b", "d", "zz9")
val sellerSampler = ZipfSampler(sellers, if (largeScale) 1.0 else 0.7)
val lastRoutingOfId = HashMap<Int, String>()
val knownIds = ArrayList<Int>()
var nextNewId = 1
val openPits = ArrayList<PitState>()
var nextPitId = 1

fun themeWord(routing: String): String = midWords[Math.floorMod(routing.hashCode(), if (largeScale) 400 else PRODUCT_WORDS.size)]

fun titleTokens(routing: String): List<String> {
    val tokens = ArrayList<String>()
    if (largeScale) {
        val target = 2 + random.nextInt(7)
        if (chance(0.8)) tokens += headSampler.sample()
        if (chance(0.3)) tokens += headSampler.sample()
        if (chance(0.3)) tokens += themeWord(routing)
        if (chance(0.3)) tokens += modelCode(random.nextInt(tailSpace))
        while (tokens.size < target) tokens += midSampler.sample()
        while (tokens.size > target) tokens.removeAt(tokens.lastIndex)
    } else {
        val target = weighted(0 to 1, 1 to 3, 2 to 6, 3 to 6, 4 to 4, 6 to 2)
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
    if (tokens.isEmpty()) return if (largeScale) "" else pick(listOf("", "!!!", "한글 제목"))
    val text = StringBuilder(renderToken(tokens.first()))
    for (token in tokens.drop(1)) text.append(if (largeScale) " " else pick(SEPARATORS)).append(renderToken(token))
    return text.toString()
}

fun chooseId(): Int {
    if (knownIds.isNotEmpty() && chance(if (largeScale) 0.12 else 0.3)) return pick(knownIds)
    val id = if (!largeScale && chance(0.03)) pick(listOf(999_999_999, 123_456_789)) else nextNewId++
    if (id !in lastRoutingOfId) knownIds += id
    return id
}

fun choosePrice(): Int =
    if (largeScale) {
        weighted({ random.nextInt(1, 2000) * 100 } to 9, { random.nextInt(1_000_000_000) } to 1)()
    } else {
        weighted({ pick(listOf(100, 200, 300, 500, 900, 1200)) } to 6, { random.nextInt(1, 40) * 100 } to 3, { 0 } to 1)()
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
    val price = choosePrice()
    model.put(id, routingArgument, tokens.toSet(), price)
    emit("PUT $id $routingArgument ${brandSampler.sample()} $price ${renderTitle(tokens)}")
}

fun deleteDocument() {
    val target = model.randomDocument()
    val (id, routingArgument) = if (target == null || chance(0.1)) {
        random.nextInt(1, nextNewId + 5) to sellerSampler.sample()
    } else {
        target.id to if (chance(0.9)) target.routingArgument else sellerSampler.sample()
    }
    model.delete(model.shardOf(if (routingArgument == "-") id.toString() else routingArgument), id)
    emit("DEL $id $routingArgument")
}

fun queryToken(remainingBudget: Int): String {
    repeat(30) {
        val token = weighted(
            { midSampler.sample() } to 12,
            { pick(midWords) } to 8,
            { modelCode(random.nextInt(tailSpace)) } to 5,
            { pick(MISSING_WORDS) } to 2,
            { headSampler.sample() } to if (largeScale) 1 else 6,
        )()
        if (model.frequency(token) <= remainingBudget) return token
    }
    return pick(MISSING_WORDS)
}

fun queryValue(resultLimit: Int): String {
    val tokens = ArrayList<String>()
    var resultBudget = resultLimit
    repeat(weighted(1 to 5, 2 to 3, 3 to 2)) {
        val token = queryToken(resultBudget)
        if (token !in tokens) resultBudget -= model.frequency(token)
        tokens += token
    }
    return tokens.joinToString("") { pick(QUERY_SEPARATORS) + renderToken(it) }.drop(1)
}

fun randomSort(allowShardDoc: Boolean): List<SortSpec>? {
    if (chance(0.25)) return null
    val fields = listOf("_score", "price", "id") + if (allowShardDoc) listOf("_shard_doc") else emptyList()
    val chosen = fields.shuffled(random).take(weighted(1 to 5, 2 to 4, 3 to 1))
    return chosen.map { SortSpec(it, weighted<String?>(null to 3, "asc" to 2, "desc" to 2)) }
}

fun randomScoreValue(): String =
    weighted(
        { "1.0000" } to 3,
        { "0.0000" } to 1,
        { "0.${random.nextInt(10_000).toString().padStart(4, '0')}" } to 4,
        { "${random.nextInt(1, 4)}.${random.nextInt(10_000).toString().padStart(4, '0')}" } to 3,
        { "999999999.9999" } to 1,
    )()

fun anchorValue(field: String, anchor: DocumentState): String =
    when (field) {
        "_score" -> randomScoreValue()
        "price" -> anchor.price.toString()
        "id" -> anchor.id.toString()
        else -> anchor.shardDoc.toString()
    }

fun randomValue(field: String): String =
    when (field) {
        "_score" -> randomScoreValue()
        "price" -> choosePrice().toString()
        "id" -> random.nextInt(0, nextNewId + 3).toString()
        else -> (random.nextLong(shardCount.toLong()) * 4_294_967_296L + random.nextInt(50)).toString()
    }

fun searchAfterValue(fields: List<String>, anchor: DocumentState?): String =
    fields.joinToString(",") { field ->
        if (anchor != null && chance(0.85)) anchorValue(field, anchor) else randomValue(field)
    }

fun liveSearch() {
    val shape = SearchShape(
        query = if (chance(if (largeScale) 1.0 else 0.75)) queryValue(if (largeScale) 500 else 2000) else null,
        searchType = weighted<String?>(null to 5, "query_then_fetch" to 1, "dfs_query_then_fetch" to 3),
        sort = randomSort(allowShardDoc = false),
    )
    val parameters = shape.parameters().toMutableList()
    weighted<String?>(null to 3, "1" to 2, "2" to 3, "3" to 3, "5" to 2, "10" to 1, "0" to 1)?.let { parameters += "size=$it" }
    when (weighted("plain" to 5, "from" to 3, "after" to 3)) {
        "from" -> parameters += "from=${weighted({ random.nextInt(1, 6) } to 6, { 0 } to 2, { random.nextInt(6, 40) } to 2)()}"
        "after" -> {
            parameters += "search_after=${searchAfterValue(shape.actualFields(withPit = false), model.randomDocument())}"
            if (chance(0.1)) parameters += "from=0"
        }
    }
    parameters.shuffle(random)
    emit((listOf("SEARCH") + parameters).joinToString(" "))
}

fun pitShape(): SearchShape =
    if (largeScale) {
        SearchShape(
            query = weighted({ null } to 4, { HEAD_WORDS[random.nextInt(2)] } to 3, { HEAD_WORDS.take(3).shuffled(random).take(2).joinToString(",") } to 2)(),
            searchType = weighted<String?>(null to 3, "dfs_query_then_fetch" to 2, "query_then_fetch" to 1),
            sort = weighted(
                { null } to 2,
                { listOf(SortSpec("price", null)) } to 3,
                { listOf(SortSpec("id", "desc")) } to 2,
                { listOf(SortSpec("_score", null), SortSpec("price", "desc")) } to 2,
                { listOf(SortSpec("price", "desc"), SortSpec("_shard_doc", "desc")) } to 1,
                { listOf(SortSpec("_shard_doc", null)) } to 1,
            )(),
        )
    } else {
        SearchShape(
            query = if (chance(0.4)) queryValue(2000) else null,
            searchType = weighted<String?>(null to 4, "query_then_fetch" to 1, "dfs_query_then_fetch" to 2),
            sort = randomSort(allowShardDoc = true),
        )
    }

fun openPit() {
    val pit = PitState(nextPitId++, model.snapshot(), List(weighted(1 to 1, 2 to 2, 3 to 2)) { pitShape() })
    openPits += pit
    emit("OPEN_PIT")
}

fun closePit(pit: PitState) {
    openPits.remove(pit)
    emit("CLOSE_PIT ${pit.id}")
}

fun pitSearch(pit: PitState) {
    val shape = pick(pit.shapes)
    val parameters = (shape.parameters() + "pit=${pit.id}").toMutableList()
    parameters += "size=${if (largeScale) random.nextInt(1, 6) else weighted(1 to 2, 2 to 4, 3 to 3, 5 to 1)}"
    if (largeScale || chance(0.8)) {
        val anchor = if (pit.documents.isEmpty()) null else pick(pit.documents)
        parameters += "search_after=${searchAfterValue(shape.actualFields(withPit = true), anchor)}"
    } else {
        parameters += "from=${random.nextInt(0, 6)}"
    }
    parameters.shuffle(random)
    emit((listOf("SEARCH") + parameters).joinToString(" "))
}

val MALFORMED_SEARCHES = listOf(
    "SEARCH sort=_shard_doc",
    "SEARCH sort=price:up",
    "SEARCH sort=price,price",
    "SEARCH sort=brand",
    "SEARCH sort=price,",
    "SEARCH sort=",
    "SEARCH sort=:asc",
    "SEARCH sort=price:",
    "SEARCH sort=PRICE",
    "SEARCH sort=price:ASC",
    "SEARCH sort=price:asc:desc",
    "SEARCH sort=price search_after=1,2",
    "SEARCH sort=price search_after=100,",
    "SEARCH sort=price search_after=,100",
    "SEARCH sort=price from=1 search_after=100",
    "SEARCH search_after=1.5",
    "SEARCH search_after=01.0000",
    "SEARCH search_after=1.00000",
    "SEARCH search_after=",
    "SEARCH sort=price search_after=0100",
    "SEARCH sort=id search_after=1234567890123456789",
    "SEARCH sort=price search_after=1.0000",
    "SEARCH from=01",
    "SEARCH from=-1",
    "SEARCH from=1000000000",
    "SEARCH from=",
    "SEARCH pit=0",
    "SEARCH pit=01",
    "SEARCH pit=",
    "SEARCH pit=1000000000",
    "SEARCH pit=99 search_after=1.0000",
    "SEARCH pit=99 sort=price search_after=100",
    "SEARCH from=1 from=2",
    "SEARCH sort=price sort=id",
    "SEARCH q=a size=10001 from=0",
    "SEARCH offset=3",
)

val EDGE_SEARCHES = listOf(
    "SEARCH from=9990",
    "SEARCH from=9991",
    "SEARCH size=10000 from=1",
    "SEARCH size=0 from=10000",
    "SEARCH size=0 from=10001",
    "SEARCH from=999999999",
    "SEARCH sort=id search_after=123456789012345678",
    "SEARCH search_after=999999999.9999",
    "SEARCH search_after=1.0000",
    "SEARCH search_after=0.0000 from=0",
    "SEARCH sort=_score:asc search_after=1.0000",
    "SEARCH pit=999 from=9991",
    "SEARCH pit=999",
)

fun malformedLine() {
    weighted(
        { emit(pick(MALFORMED_SEARCHES)) } to 8,
        { emit(pick(EDGE_SEARCHES)) } to 4,
        { emit(pick(listOf("OPEN_PIT now", "OPEN_PIT 1", "CLOSE_PIT", "CLOSE_PIT 01", "CLOSE_PIT 1 2", "CLOSE_PIT x", "CLOSE_PIT 0", "CLOSE_PIT 999"))) } to 4,
        { emit(pick(listOf("open_pit", "Open_Pit", "OPEN-PIT", "PIT", "CLOSEPIT 1", "OPEN_PIT\r", "close_pit 1"))) } to 2,
        { emit(pick(listOf("TERMS size=0", "TERMS 5", "PUT 1 a acme 0100 x", "DEL 1", "STATS now", "CREATE 2"))) } to 2,
        { emit(pick(listOf("", "   "))) } to 1,
    )()
}

val NO_INDEX_LINES = listOf(
    "OPEN_PIT", "OPEN_PIT now", "CLOSE_PIT 1", "CLOSE_PIT", "CLOSE_PIT 01", "SEARCH pit=1",
    "SEARCH sort=_shard_doc", "SEARCH from=99999", "STATS", "TERMS", "CREATE 0", "open_pit",
)

fun termsLine() {
    val parameters = listOfNotNull(
        weighted<String?>(null to 2, "size=1" to 2, "size=2" to 3, "size=3" to 2),
        weighted<String?>(null to 2, "shard_size=1" to 2, "shard_size=2" to 2, "shard_size=4" to 1),
    ).shuffled(random)
    emit((listOf("TERMS") + parameters).joinToString(" "))
}

fun generateMixed() {
    repeat(random.nextInt(2, 5)) { emit(pick(NO_INDEX_LINES)) }
    emit("CREATE $shardCount")
    if (chance(0.3)) openPit()
    var puts = options.puts
    var dels = options.dels
    var searches = options.searches
    var terms = options.terms
    var pitsToOpen = options.pits
    var pitSearches = options.pits * options.pitSearches
    var malformed = maxOf(6, (options.puts + options.searches + pitSearches) / 10)
    var stats = 2
    while (true) {
        if (openPits.isEmpty() && pitsToOpen == 0) pitSearches = 0
        if (puts + dels + searches + terms + pitsToOpen + pitSearches + malformed + stats == 0 && openPits.isEmpty()) break
        val warmingUp = puts > 0 && options.puts - puts < 10
        val openWeight = if (warmingUp || openPits.size >= 2) 0 else pitsToOpen * 4
        val pitSearchWeight = if (openPits.isEmpty()) 0 else maxOf(pitSearches * 2, 1)
        val closeWeight = if (openPits.isEmpty()) 0 else if (pitSearches == 0) 10 else 1
        val weights = listOf(
            "put" to puts * 3,
            "del" to if (warmingUp) 0 else dels * 2,
            "search" to if (warmingUp) 0 else searches * 2,
            "terms" to if (warmingUp) 0 else terms,
            "open" to openWeight,
            "pitSearch" to pitSearchWeight,
            "close" to closeWeight,
            "malformed" to if (warmingUp) 0 else malformed,
            "stats" to if (warmingUp) 0 else stats,
        )
        when (weighted(*weights.filter { it.second > 0 }.toTypedArray())) {
            "put" -> { putDocument(); puts-- }
            "del" -> { deleteDocument(); dels-- }
            "search" -> { liveSearch(); searches-- }
            "terms" -> { termsLine(); terms-- }
            "open" -> { openPit(); pitsToOpen-- }
            "pitSearch" -> { pitSearch(pick(openPits)); pitSearches = maxOf(0, pitSearches - 1) }
            "close" -> {
                val pit = pick(openPits)
                closePit(pit)
                if (chance(0.3)) emit(pick(listOf("CLOSE_PIT ${pit.id}", "SEARCH pit=${pit.id}", "SEARCH pit=${pit.id} sort=price search_after=100,0")))
            }
            "malformed" -> { malformedLine(); malformed-- }
            else -> { emit("STATS"); stats-- }
        }
    }
}

fun generateLargeScale() {
    emit("CREATE $shardCount")
    repeat(options.puts) { putDocument() }
    val mutationsPerPit = options.mutations / maxOf(1, options.pits)
    val liveSearchesPerPit = options.searches / maxOf(1, options.pits)
    repeat(options.pits) {
        openPit()
        val pit = openPits.last()
        var pitSearches = options.pitSearches
        var mutations = mutationsPerPit
        var liveSearches = liveSearchesPerPit
        while (pitSearches + mutations + liveSearches > 0) {
            val choice = random.nextInt(pitSearches + mutations + liveSearches)
            when {
                choice < pitSearches -> { pitSearch(pit); pitSearches-- }
                choice < pitSearches + mutations -> { if (chance(0.6)) putDocument() else deleteDocument(); mutations-- }
                else -> { liveSearch(); liveSearches-- }
            }
        }
        closePit(pit)
    }
}

if (largeScale) generateLargeScale() else generateMixed()
System.out.write(output.toString().encodeToByteArray())
System.out.flush()
