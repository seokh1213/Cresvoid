import kotlin.random.Random

// --orders counts the scalable workload; the fixed edge cases are additional.
val options = args.toList().chunked(2).associate { pair ->
    require(pair.size == 2) { "Expected --name value" }
    pair[0] to pair[1]
}
require(options.keys.all { it in setOf("--seed", "--orders", "--mode") })
val seed = options["--seed"]?.toInt() ?: 1
val orders = options["--orders"]?.toInt() ?: 400
val mode = options["--mode"] ?: "mixed"
require(orders in 1..1_000_000)
require(mode in setOf("mixed", "scale"))
val random = Random(seed)
val output = System.out.bufferedWriter()
var sequence = 0L
val mutationNames = setOf("LIMIT", "CANCEL", "MARKET", "IOC", "FOK", "AMEND")

fun raw(line: String) { output.appendLine(line) }

fun emit(line: String) {
    val name = line.trim().substringBefore(' ').substringBefore('\t')
    if (name in mutationNames) {
        sequence++
        raw(line)
    } else {
        raw(line)
    }
}

fun boundaryCases() {
    emit("BOOK")
    emit("LIMIT Zeta same SELL 100 5")
    emit("LIMIT Alpha same SELL 100 4")
    emit("LIMIT Rare_9 owner9 SELL 99 2")
    emit("LIMIT Take same BUY 101 8")
    emit("BOOK")
    emit("CANCEL Alpha")
    emit("CANCEL Alpha")
    emit("LIMIT Zeta same BUY 100 1")
    emit("LIMIT Tiny owner BUY 0001 0001")
    emit("LIMIT Huge owner BUY 1 1000000000")
    emit("LIMIT Huge2 owner BUY 1 1000000000")
    emit("LIMIT Huge3 owner BUY 1 1000000000")
    emit("BOOK")
    listOf("Tiny", "Huge", "Huge2", "Huge3").forEach { emit("CANCEL $it") }
    val invalid = listOf(
        "unknown", "BOOK extra", "CANCEL", "CANCEL 9bad", "CANCEL Missing",
        "LIMIT", "LIMIT bad! owner BUY x 0", "LIMIT bad! owner BUY 1 1",
        "LIMIT Id bad! BUY 1 1", "LIMIT Id owner buy 1 1",
        "LIMIT Id owner BUY +1 1", "LIMIT Id owner BUY 1 1.0",
        "LIMIT Id owner BUY --1 1", "LIMIT Id owner BUY 1 1 extra",
        "LIMIT Id owner BUY 0 1", "LIMIT Id owner BUY -0 1",
        "LIMIT Id owner BUY 1 0", "LIMIT Id owner BUY -1 1",
        "LIMIT Id owner BUY 1000000001 1", "LIMIT Id owner BUY 1 1000000001",
        "LIMIT Id owner BUY 9223372036854775808 1",
        "LIMIT Id owner BUY -9223372036854775809 1",
        "LIMIT Id owner BUY 1 9999999999999999999999999999999",
        "LIMIT abcdefghijklm owner BUY 1 1", "LIMIT Zeta same BUY 1 0",
    )
    invalid.forEach(::emit)
    raw(" \t  ")
    emit("\tLIMIT\tA12345678901\tZ___________ BUY 000000001 01\t")
    emit("CANCEL A12345678901")
    emit("MARKET Empty same BUY 2")
    emit("LIMIT Old u SELL 100 8")
    emit("LIMIT Young v SELL 100 5")
    emit("AMEND Old 100 6")
    emit("AMEND Old 100 6")
    emit("FOK Fail same BUY 100 12")
    emit("FOK Fail same BUY 100 1")
    emit("FOK Fill same BUY 100 7")
    emit("AMEND Young 100 7")
    emit("LIMIT Later u SELL 100 2")
    emit("AMEND Young 100 9")
    emit("MARKET Sweep u BUY 3")
    emit("LIMIT Bid u BUY 90 2")
    emit("AMEND Young 90 4")
    emit("IOC Outside u BUY 89 9")
    emit("IOC Partial u BUY 90 9")
    emit("LIMIT Zero u SELL 101 5")
    emit("AMEND Zero 1 0")
    listOf("MARKET", "IOC", "FOK").forEach { kind ->
        emit(if (kind == "MARKET") "$kind Zero u BUY 0" else "$kind Zero u BUY 100 0")
        emit(if (kind == "MARKET") "$kind Malformed u BUY +2" else "$kind Malformed u BUY 100 +2")
    }
    listOf("AMEND", "AMEND Missing 0 1", "AMEND Missing 1 -1", "AMEND Young 1 1000000001",
        "AMEND Missing 1 x", "AMEND Missing 1 1", "AMEND Young 0 1").forEach(::emit)
    emit("BOOK")
    
}

fun mixedOrders() {
    var generated = 0
    var block = 0
    while (generated < orders) {
        val ids = ArrayList<String>()
        repeat(minOf(48, orders - generated)) {
            val id = "R${generated++}"
            ids.add(id)
            val owner = if (random.nextInt(4) == 0) "Q9_uncommon" else "user"
            val side = if (random.nextBoolean()) "BUY" else "SELL"
            val price = listOf(1L, 99L, 100L, 101L, 1_000_000_000L)[random.nextInt(5)]
            val quantity = listOf(1L, 2L, 7L, 1_000_000_000L)[random.nextInt(4)]
            val kind = listOf("LIMIT", "LIMIT", "LIMIT", "MARKET", "IOC", "FOK").random(random)
            emit(if (kind == "MARKET") "$kind $id $owner $side $quantity" else "$kind $id $owner $side $price $quantity")
            when (random.nextInt(8)) {
                0 -> emit("CANCEL ${ids.random(random)}")
                1 -> emit("LIMIT $id user BUY 100 0")
                2 -> emit("LIMIT $id user SELL 100 1")
                3 -> emit("AMEND ${ids.random(random)} $price ${listOf(0, 1, 2, 9).random(random)}")
                4 -> emit("BOOK")
                5 -> emit("LIMIT Bad_${block} user BUY 1e2 2")
            }
        }
        emit("BOOK")
        ids.forEach { emit("CANCEL $it") }
        emit("BOOK")
        
        block++
    }
}

fun scaleOrders() {
    val makers = orders / 2
    val crowded = makers / 2
    val offset = random.nextInt(maxOf(1, makers))
    repeat(makers) { index ->
        val price = if (index < crowded) 1 else (index + offset) % makers + 3
        emit("LIMIT S${index.toString(36)} u SELL $price 1")
    }
    val changes = minOf(200_000, crowded / 2)
    val targets = (crowded / 4 until crowded / 4 + changes).shuffled(random)
    targets.forEachIndexed { index, target ->
        val id = "S${target.toString(36)}"
        when (index % 3) {
            0 -> emit("CANCEL $id")
            1 -> emit("AMEND $id 1 2")
            else -> emit("AMEND $id 2 1")
        }
    }
    repeat(orders - makers) { index ->
        val id = "B${index.toString(36)}"
        when (index % 4) {
            0 -> emit("MARKET $id u BUY 1")
            1 -> emit("IOC $id u BUY 1000000000 1")
            2 -> emit("FOK $id u BUY 1000000000 1")
            else -> emit("LIMIT $id u BUY 1000000000 1")
        }
    }
    emit("BOOK")
}

if (mode == "scale") {
    
    scaleOrders()
} else {
    boundaryCases()
    mixedOrders()
}
output.flush()
