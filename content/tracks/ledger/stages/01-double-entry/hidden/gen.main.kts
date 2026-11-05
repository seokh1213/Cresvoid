import kotlin.random.Random

val options = args.toList().chunked(2).associate { it[0] to it[1] }
val random = Random(options["--seed"]?.toInt() ?: 1)
val rounds = options["--rounds"]?.toInt() ?: 20
require(rounds in 1..200)
val output = System.out.bufferedWriter()
fun emit(command: String) { output.appendLine(command) }
val currencies = listOf("KRW", "USD", "XTS", "ZZZ")
val amounts = listOf(1L, 7L, 100L, 999_999_999_999L, 1_000_000_000_000L)
for (currency in currencies) {
    emit("OPEN cash_$currency $currency D NONNEG")
    emit("OPEN owed_$currency $currency C NONNEG")
    emit("OPEN bank_$currency $currency D ANY")
    emit("OPEN debt_$currency $currency C ANY")
}
emit("OPEN cash_KRW USD C ANY")
emit("POST unfunded KRW 2 cash_KRW C 1 owed_KRW D 1")
emit("OPEN A1234567890123456789012345678901 XTS C ANY")
emit("BALANCE absent")
emit("JOURNAL absent")
emit("TRIAL EUR")
repeat(rounds) { index ->
    val currency = currencies.random(random)
    val other = currencies.first { it != currency }
    val amount = amounts.random(random)
    val id = if (index % 2 == 0) "sale$index" else "Z_rare-$index"
    val post = "POST $id $currency 2 cash_$currency D $amount owed_$currency C $amount"
    emit("  $post  ")
    emit(post)
    emit("POST $id $currency 2 owed_$currency C $amount cash_$currency D $amount")
    emit("POST bad$index $currency 2 absent D 0 bank_$other C 1")
    emit("POST bad$index $currency 2 absent D 1 bank_$other C 1")
    emit("POST bad$index $currency 2 bank_$currency D 1 bank_$other C 1")
    emit("POST bad$index $currency 2 bank_$currency D 1 debt_$currency C 2")
    emit("POST bad$index $currency 2 bank_$currency C $amount debt_$currency D $amount")
    emit("POST empty$index $currency 2 cash_$other C 1000000000000 owed_$other D 1000000000000")
    emit("POST rotate$index $currency 4 cash_$currency C 1000000000000 cash_$currency D 1000000000000 owed_$currency D 1 owed_$currency C 1")
    emit("POST many$index $currency 100 " + List(50) { "bank_$currency D 1 bank_$currency C 1" }.joinToString(" "))
    emit("POST negative$index $currency 2 bank_$currency D -1000000000000 debt_$currency C -1000000000000")
    emit("POST insufficient$index $other 2 cash_$other C 1000000000000 owed_$other D 1000000000000")
    emit("BALANCE cash_$currency")
    emit("BALANCE owed_$currency")
    emit("BALANCE bank_$currency")
    emit("BALANCE debt_$currency")
    emit("JOURNAL $id")
    emit("JOURNAL bad$index")
    emit("JOURNAL negative$index")
    emit("")
}
for (currency in currencies) emit("TRIAL $currency")
output.flush()
