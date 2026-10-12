import kotlin.random.Random

data class Options(val runs: Int, val products: Int, val perProduct: Int, val mode: String, val seed: Long)

fun parseOptions(arguments: Array<String>): Options {
    val values = mutableMapOf(
        "--runs" to "6",
        "--products" to "12",
        "--per-product" to "4",
        "--mode" to "mixed",
        "--seed" to "1",
    )
    require(arguments.size % 2 == 0) { "옵션은 --이름 값 쌍이다" }
    for (pair in arguments.toList().chunked(2)) {
        require(pair[0] in values) { "모르는 옵션: ${pair[0]}" }
        values[pair[0]] = pair[1]
    }
    val mode = values.getValue("--mode")
    require(mode == "mixed" || mode == "wide")
    return Options(
        runs = values.getValue("--runs").toInt().also { require(it in 1..100) },
        products = values.getValue("--products").toInt().also { require(it in 1..20_000) },
        perProduct = values.getValue("--per-product").toInt().also { require(it in 1..10) },
        mode = mode,
        seed = values.getValue("--seed").toLong(),
    )
}

data class PlannedAsk(val at: Long, val id: String, val product: String, val timeout: Long, val cancelAt: Long) {
    fun line(): String = "ASK $at $id $product $timeout $cancelAt"
}

val options = parseOptions(args)
val random = Random(options.seed)
val output = StringBuilder()

fun emit(line: String) {
    output.append(line).append('\n')
}

fun addAsk(asks: MutableList<PlannedAsk>, run: Int, productIndex: Int, ordinal: Int, at: Long,
           timeout: Long = 0, cancelAt: Long = 0) {
    val product = "p$productIndex"
    asks += PlannedAsk(at, "r${run}p${productIndex}a$ordinal", product, timeout, cancelAt)
}

fun writeMixedRun(run: Int) {
    val pattern = run % 5
    val asks = ArrayList<PlannedAsk>()
    emit("TRACE ON")
    for (index in 0 until options.products) {
        val product = "p$index"
        val value = random.nextInt(1, 10_001)
        when (pattern) {
            0 -> {
                emit("SERVICE $product ${12 + random.nextInt(3)}:OK:$value")
                addAsk(asks, run, index, 0, 1, cancelAt = 4)
                addAsk(asks, run, index, 1, 2)
                addAsk(asks, run, index, 2, 3)
            }
            1 -> {
                emit("SERVICE $product 20:OK:$value 3:OK:${value + 1}")
                addAsk(asks, run, index, 0, 1, cancelAt = 4)
                addAsk(asks, run, index, 1, 2, timeout = 5)
                addAsk(asks, run, index, 2, 9)
            }
            2 -> {
                emit("SERVICE $product 4:FAIL:503 3:OK:$value")
                addAsk(asks, run, index, 0, 1)
                addAsk(asks, run, index, 1, 2)
                addAsk(asks, run, index, 2, 8)
            }
            3 -> {
                emit("SERVICE $product 5:OK:$value")
                emit("SEED $product ${value + 1} 0")
                addAsk(asks, run, index, 0, 2)
                addAsk(asks, run, index, 1, 10)
                addAsk(asks, run, index, 2, 11)
                addAsk(asks, run, index, 3, 16)
            }
            else -> {
                emit("SERVICE $product 4:FAIL:503 3:OK:$value")
                emit("SEED $product ${value + 1} 0")
                addAsk(asks, run, index, 0, 10)
                addAsk(asks, run, index, 1, 11)
                addAsk(asks, run, index, 2, 15)
                addAsk(asks, run, index, 3, 19)
            }
        }
    }
    asks.sortedWith(compareBy(PlannedAsk::at, PlannedAsk::id)).forEach { emit(it.line()) }
    emit("LOOKUPS 10")
}

fun writeWideRun(run: Int) {
    emit("TRACE OFF")
    for (index in 0 until options.products) {
        val value = random.nextInt(1, 10_001)
        emit("SERVICE p$index 100:OK:$value")
    }
    for (request in 0 until options.perProduct) {
        for (index in 0 until options.products) addWideAsk(run, index, request)
    }
    emit("LOOKUPS 10")
}

fun addWideAsk(run: Int, productIndex: Int, ordinal: Int) {
    emit("ASK 1 r${run}p${productIndex}a$ordinal p$productIndex 0 0")
}

repeat(options.runs) { run ->
    if (options.mode == "wide") writeWideRun(run) else writeMixedRun(run)
}
print(output)
