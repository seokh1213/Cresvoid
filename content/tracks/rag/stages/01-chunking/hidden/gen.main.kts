import kotlin.random.Random

val options = args.toList().chunked(2).associate { pair ->
    require(pair.size == 2) { "Options require a value" }
    pair[0] to pair[1]
}
require(options.keys.all { it in setOf("--seed", "--docs", "--queries", "--rounds", "--dimensions") })
val seed = options["--seed"]?.toInt() ?: 1
val docs = options["--docs"]?.toInt() ?: 40
val queries = options["--queries"]?.toInt() ?: 12
val rounds = options["--rounds"]?.toInt() ?: 12
val dimensions = options["--dimensions"]?.toInt() ?: 8
require(docs in 1..10_000 && queries in 0..100 && rounds in 0..50 && dimensions in 1..32)
val random = Random(seed)
val output = System.out.bufferedWriter(Charsets.UTF_8)
fun emit(vararg fields: Any) {
    output.write(fields.joinToString("\t"))
    output.newLine()
}
val words = listOf("red", "blue", "red", "search", "가나", "Ab12가나_é", "quasar97", "희귀어", "😀", "!!!", "v1.2", "a!b")
fun body(): String {
    val text = buildString {
        while (codePointCount(0, length) < 120) {
            if (isNotEmpty()) append(' ')
            append(words[random.nextInt(words.size)])
        }
    }
    return String(text.codePoints().toArray(), 0, 120)
}
fun vector(serial: Int): String = (0 until dimensions).joinToString(",") { component ->
    when {
        serial % 11 == 0 -> "0"
        serial % 11 == 1 -> if (component == 0) "1" else "0"
        serial % 11 == 2 -> if (component == 0) "-1" else "0"
        else -> listOf("-1000", "-0", "0.000001", "0.5", "1", "-1", "17.345678")[random.nextInt(7)]
    }
}
fun chunkingEdges() {
    emit("")
    emit("CHUNKS")
    emit("BUILD", "")
    emit("CONFIG", "0", "0", "CHAR", "NONE")
    emit("DOC", "01", "", "red")
    emit("DOC", "1000000001", "", "red")
    emit("DOC", "1", "")
    emit("DEL", "-1")
    emit("CHUNKS", "")
    emit("not-a-command", "red")
    emit("DEL", 999999999)
    emit("BUILD")
    emit("CHUNKS")
    emit("DOC", 900000000, "  제목   Context  ", "  aa.  bb! cc? v1.2 a!b 😀가나다.... tail ")
    emit("DOC", 900000001, "empty title", "   ")
    for (mode in listOf("CHAR", "SENTENCE")) {
        for (overlap in listOf(0, 4, 6)) {
            emit("CONFIG", 7, overlap, mode, "TITLE")
            emit("BUILD")
            emit("CHUNKS")
            emit("CONFIG", 7, 7, mode, "NONE")
            emit("CHUNKS")
        }
    }
    emit("CONFIG", 7, 8, "CHAR", "NONE")
    emit("CONFIG", "7", "0", "sentence", "NONE")
    emit("CONFIG", "7", "0", "CHAR", "title")
    emit("CONFIG", "7", "0", "CHAR", "NONE", "")
    emit("DOC", 900000000, "", "😀가😀 abcdefghij? z!")
    emit("CHUNKS")
    emit("CONFIG", 1, 0, "SENTENCE", "NONE")
    emit("BUILD")
    emit("CHUNKS")
    emit("DEL", 900000000)
    emit("DEL", 900000001)
}
fun chunkingRounds() {
    repeat(rounds) { iteration ->
        val id = random.nextInt(1, docs + 1)
        val sizes = if (docs <= 100) listOf(1, 7, 24, 200, 10_000) else listOf(200, 10_000)
        val size = sizes[random.nextInt(sizes.size)]
        emit("CONFIG", size, random.nextInt(size), if (iteration % 2 == 0) "CHAR" else "SENTENCE",
            if (iteration % 3 == 0) "TITLE" else "NONE")
        emit("DOC", id, "rewrite", "aa. bb! cc? abcdefghijklmno? 😀가나 v1.2")
        emit("BUILD")
        if (docs <= 100) emit("CHUNKS")
        emit("DEL", id)
        emit("CHUNKS")
        emit("DOC", id, "", "replacement")
    }
}

chunkingEdges()
emit("DEL", 900000000)
emit("DEL", 900000001)
emit("CONFIG", 24, 0, "CHAR", "NONE")
for (id in 1..docs) emit("DOC", id, if (id % 7 == 0) "희귀 제목" else "red guide", body())
emit("BUILD")
if (docs <= 100) emit("CHUNKS")
chunkingRounds()
emit("CONFIG", 200, 20, "SENTENCE", "TITLE")
emit("BUILD")
emit("BUILD")
output.flush()
