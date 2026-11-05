import kotlin.random.Random

val options = args.toList().chunked(2).associate { it[0] to it[1] }
val random = Random(options["--seed"]?.toInt() ?: 1)
val requestCount = options["--requests"]?.toInt() ?: 120
val rowCount = options["--rows"]?.toInt() ?: requestCount
require(requestCount in 3..100_000 && rowCount in 0..100_000)
val output = System.out.bufferedWriter()
fun emit(command: String) { output.appendLine(command) }
val currencies = listOf("KRW", "USD", "XTS", "ZZZ")
data class Expected(val key: String, val currency: String, val amount: Long)
val expected = List(requestCount) { index ->
    val key = (if (index % 2 == 0) "p" else "Z") + index.toString(36)
    val currency = if (index == requestCount - 1) "KRW" else currencies[index % currencies.size]
    val amount = when (index) {
        0 -> 1_000_000_000_000L
        requestCount - 1 -> -1_000_000_000_000L
        else -> listOf(1L, 7L, 125L, 99_999L, 1_000_000_000_000L).random(random)
    }
    Expected(key, currency, amount)
}
for (currency in currencies) {
    emit("OPEN bank_$currency $currency D ANY")
    emit("OPEN debt_$currency $currency C ANY")
}
emit("OPEN bank_KRW KRW D ANY")
emit("POST manual KRW 2 bank_KRW D 1 debt_KRW C 1")
emit("POST manual KRW 2 bank_KRW D 1 debt_KRW C 1")
emit("POST invalid KRW 2 bank_KRW D 0 debt_KRW C 0")
emit("BALANCE absent")
emit("JOURNAL absent")
emit("RECONCILE absent")
emit("IMPORT empty 0")
emit("RECONCILE empty")
val first = expected.first()
val firstPay = "PAY ${first.key} bank_KRW debt_KRW KRW ${first.amount}"
emit("PSP ${first.key} TIMEOUT_NONE")
emit(firstPay)
emit("CALL ${first.key}")
emit("QUERY ${first.key}")
emit("STATUS ${first.key}")
emit("COMMIT ${first.key}")
emit("CALL ${first.key}")
emit("QUERY ${first.key}")
emit("COMMIT ${first.key}")
emit("CALL ${first.key}")
emit("PSP ${first.key} RETRY")
emit("CALL ${first.key}")
emit("COMMIT ${first.key}")
emit("PSP ${first.key} TIMEOUT_OK")
emit("CALL ${first.key}")
emit("CRASH")
emit("QUERY ${first.key}")
emit("IMPORT snap 1")
emit("${first.key},KRW,${first.amount}")
emit("RECONCILE snap")
emit("COMMIT ${first.key}")
emit("RECONCILE snap")
emit("IMPORT snap 1")
emit("${first.key},KRW,${first.amount}")
emit("IMPORT snap 0")
emit(firstPay)
emit("PAY ${first.key} absent absent USD 0")
emit("REFUND invalid ${first.key} KRW 0")
emit("REFUND invalid ${first.key} USD 1")
emit("STATUS ${first.key}")
emit("REMOTE ${first.key}")
emit("JOURNAL @${first.key}")
for (request in expected.drop(1).shuffled(random)) {
    if (request.amount < 0) {
        emit("REFUND ${request.key} ${first.key} ${request.currency} ${-request.amount}")
    } else {
        emit("PAY ${request.key} bank_${request.currency} debt_${request.currency} ${request.currency} ${request.amount}")
    }
    emit("PSP ${request.key} OK")
    emit("CALL ${request.key}")
    emit("COMMIT ${request.key}")
}
val rows = List(rowCount) { index ->
    val request = expected[index % expected.size]
    when (index % 4096) {
        0 -> {
            val duplicate = expected[(index + 1) % expected.size]
            "${duplicate.key},EUR,0"
        }
        2 -> "${request.key},${request.currency},${request.amount - 1}"
        3 -> "${request.key},EUR,${request.amount}"
        4 -> "ghost${index.toString(36)},${request.currency},${request.amount}"
        5 -> "${request.key},${request.currency},0"
        else -> "${request.key},${request.currency},${request.amount}"
    }
}.shuffled(random)
emit("IMPORT batch $rowCount")
rows.forEach(::emit)
emit("RECONCILE batch")
emit("RECONCILE batch")
emit("BALANCE bank_KRW")
emit("BALANCE debt_KRW")
emit("JOURNAL manual")
for (currency in currencies + "EUR") emit("TRIAL $currency")
output.flush()
