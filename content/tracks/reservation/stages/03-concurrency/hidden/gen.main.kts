import java.time.LocalDate
import kotlin.random.Random

val options = args.toList().chunked(2)
require(options.all { it.size == 2 && it[0] in setOf("--seed", "--cases") })
require(options.map { it[0] }.distinct().size == options.size)
val settings = options.associate { it[0] to it[1].toInt() }
val random = Random(settings["--seed"] ?: 1)
val cases = settings["--cases"] ?: 8
require(cases in 1..30)
val lines = mutableListOf<String>()
fun emit(vararg commands: String) { lines.addAll(commands) }

fun boundaries() {
    emit(
        "", " \t ", "TYPE Edge 25", "TYPE Edge 25",
        "STOCK Edge 2028-02-28 2028-02-29 3", "STOCK Edge 2028-02-29 2028-03-01 4",
        "BOOK Leap Edge 2028-02-28 2028-03-01 3", "AVAIL Edge 2028-03-01",
        "BOOK Retry Edge 2028-02-28 2028-03-01 1", "STATUS Retry",
        "CANCEL Leap 2028-02-29 2028-03-01 2", "CANCEL Leap 2028-02-28 2028-03-01 2",
        "STATUS Leap", "STOCK Edge 2028-02-28 2028-03-01 0", "AVAIL Edge 2028-02-29",
        "CANCEL Leap 2028-02-28 2028-02-29 3", "CANCEL Leap 2028-02-29 2028-03-01 1",
        "BOOK Leap Edge 2028-02-28 2028-03-01 1", "BOOK Retry Edge 2028-02-28 2028-03-01 1",
        "TYPE Huge 100", "STOCK Huge 2000-01-01 2000-01-02 1000000000",
        "BOOK Big Huge 2000-01-01 2000-01-02 1000000000",
        "BOOK Bigger Huge 2000-01-01 2000-01-02 1000000000", "AVAIL Huge 2000-01-01",
        "TYPE LongStay 0", "STOCK LongStay 2028-01-01 2029-01-01 1",
        "BOOK Year LongStay 2028-01-01 2029-01-01 1", "STATUS Year",
        "CANCEL Year 2027-12-31 2028-01-02 1", "STATUS Missing", "AVAIL Missing 2099-12-31",
        "STOCK Missing 2030-01-01 2030-01-02 1", "CANCEL Missing 2030-01-01 2030-01-02 1",
        "BOOK Leap Missing 2030-01-01 2030-01-02 1", "TYPE Edge -0"
    )
    emit(
        "WHAT Edge", "type Edge 0", "TYPE", "TYPE A 0 extra", "TYPE 9bad 0", "TYPE é 0",
        "TYPE A +0", "TYPE A 9223372036854775808", "TYPE A -9223372036854775808",
        "TYPE A 101", "TYPE A -1", "TYPE ${"A".repeat(33)} 0", "TYPE ${"Z".repeat(32)} 0",
        "AVAIL Edge 2027-02-29", "AVAIL Edge 2100-02-29", "AVAIL Edge 2028-2-29",
        "AVAIL Edge 1999-12-31", "AVAIL Edge 2100-01-01", "AVAIL Edge 0000-01-01",
        "STOCK Edge 2030-01-01 2030-01-01 0", "STOCK Edge 2030-01-02 2030-01-01 0",
        "STOCK Edge 2028-01-01 2029-01-02 0", "STOCK Edge 2030-01-01 2030-01-02 -1",
        "STOCK Edge 2030-01-01 2030-01-02 1000000001",
        "BOOK Bad Edge 2030-01-01 2030-01-02 0", "CANCEL Missing 2030-01-01 2030-01-02 -1",
        "BOOK Bad 9bad 1999-01-01 1999-01-02 0", "BOOK Bad Edge 1999-01-01 1999-01-02 nope"
    )
}

fun inventoryCase(index: Int) {
    val type = listOf("Twin", "Deluxe", "Ziggurat_7", "Q", "room-rare")[random.nextInt(5)] + index
    val id = "stay_$index"
    val from = LocalDate.of(2040, 1, 1).plusDays(random.nextInt(100).toLong())
    val to = from.plusDays(random.nextInt(2, 9).toLong())
    val middle = from.plusDays(1)
    val percent = listOf(0, 1, 25, 33, 99, 100)[random.nextInt(6)]
    val base = random.nextInt(2, 8)
    val quantity = random.nextInt(1, base + 1)
    emit(
        "TYPE $type $percent", "STOCK $type $from $to $base",
        "\tBOOK\t$id  $type $from $to 00$quantity\t", "AVAIL $type $from",
        "STOCK $type $from $to 0", "STATUS $id",
        "CANCEL $id $middle $to 1", "STATUS $id", "CANCEL $id $from $to $quantity",
        "STATUS $id", "BOOK $id $type $from $to 1", "CANCEL $id $from $middle $quantity",
        "CANCEL $id $middle $to ${quantity - 1}", "AVAIL $type $from",
        "BOOK next_$index $type $from $to 1", "STOCK $type $from $to $base",
        "AVAIL $type $to", "BOOK missing_$index Ghost $from $to 1",
        "BOOK failed_$index $type $from $to 1000000000",
        "BOOK failed_$index $type $from $to 1", "STATUS failed_$index"
    )
}

boundaries()
repeat(cases, ::inventoryCase)
fun requestCase(index: Int) {
    val type = "Req_$index"
    val request = "key_$index"
    val id = "requestStay_$index"
    emit(
        "TYPE $type 0", "REQUEST $request BOOK $id $type 2050-01-01 2050-01-03 1",
        "STOCK $type 2050-01-01 2050-01-03 2",
        "REQUEST $request BOOK $id $type 2050-01-01 2050-01-03 0001",
        "REQUEST new_$index BOOK $id $type 2050-01-01 2050-01-03 1",
        "REQUEST cancel_$index CANCEL $id 2050-01-02 2050-01-03 1",
        "REQUEST cancel_$index CANCEL $id 2050-01-02 2050-01-03 01",
        "REQUEST new_$index BOOK $id $type 2050-01-01 2050-01-03 1",
        "REQUEST new_$index CANCEL $id 2050-01-01 2050-01-03 1",
        "REQUEST new_$index BOOK $id $type 2050-01-01 2050-01-03 0",
        "REQUEST new_$index BOOK $id $type 2050-01-01 2050-01-03 +1",
        "REQUEST cancel_$index CANCEL $id 2050-01-01 2050-01-03 1", "STATUS $id",
        "REQUEST absent_$index CANCEL absent_$index 2050-01-01 2050-01-03 1",
        "BOOK absent_$index $type 2050-01-01 2050-01-03 1",
        "REQUEST absent_$index CANCEL absent_$index 2050-01-01 2050-01-03 1",
        "REQUEST wrong_$index CANCEL $id 2050-01-01 2050-01-03 2",
        "REQUEST period_$index CANCEL $id 2049-12-31 2050-01-02 1",
        "REQUEST used_$index BOOK $id $type 2050-01-01 2050-01-03 1",
        "AVAIL $type 2050-01-02"
    )
}
repeat(cases, ::requestCase)
emit(
    "REQUEST k TYPE X 0", "REQUEST k REQUEST inner BOOK r Edge 2030-01-01 2030-01-02 1",
    "REQUEST 9bad BOOK r Edge 2030-01-01 2030-01-02 1", "REQUEST k BOOK r Edge 2030-01-01 2030-01-02",
    "REQUEST repair BOOK Repair Ghost 2030-01-01 2030-01-02 0",
    "REQUEST repair BOOK Repair Ghost 2030-01-01 2030-01-02 1", "TYPE Ghost 0",
    "REQUEST repair BOOK Repair Ghost 2030-01-01 2030-01-02 1"
)
require(lines.count { it.isNotBlank() } <= 3000)
fun concurrencyCase(index: Int) {
    val mode = listOf("PESSIMISTIC", "OPTIMISTIC", "CONSTRAINT")[index % 3]
    val type = "Req_$index"
    val capacity = random.nextInt(1, 3)
    val quantity = if (index % 4 == 0) capacity + 1 else 1
    emit(
        "STOCK $type 2060-01-01 2060-01-04 $capacity", "BEGIN $mode",
        "TX a batchA_$index $type 2060-01-02 2060-01-03 1",
        "TX b batchB_$index $type 2060-01-01 2060-01-03 $quantity",
        "TX c batchC_$index $type 2060-01-03 2060-01-04 1",
        "TX a unused Ghost 2060-01-01 2060-01-02 1",
        "TX a unused $type 2060-01-01 2060-01-02 1",
        "TX duplicate batchB_$index $type 2060-01-01 2060-01-02 1",
        "STEP missing", "END", "STEP a", "STEP b", "STEP c",
        "BOOK bad $type 2060-01-01 2060-01-02 0", "TYPE $type 0", "BEGIN $mode",
        "AVAIL $type 2060-01-01", "STATUS batchA_$index",
        "CANCEL batchA_$index 2060-01-02 2060-01-03 1",
        "REQUEST batched_$index BOOK after_$index $type 2060-01-01 2060-01-02 1",
        "TX d batchD_$index $type 2060-01-04 2060-01-05 1", "STEP d", "STEP d",
        "STEP a", "STEP c", "STEP b", "STEP b", "STEP b", "STEP b", "STEP a",
        "TX stillUsed batchB_$index $type 2060-01-01 2060-01-02 1", "END",
        "AVAIL $type 2060-01-01", "STATUS batchB_$index", "STATUS batchA_$index",
        "BOOK batchB_$index $type 2060-01-01 2060-01-02 1",
        "CANCEL batchA_$index 2060-01-02 2060-01-03 1",
        "REQUEST batched_$index BOOK after_$index $type 2060-01-01 2060-01-02 1",
        "REQUEST batched_$index BOOK after_$index $type 2060-01-01 2060-01-02 01",
        "BEGIN $mode", "END"
    )
}
repeat(cases, ::concurrencyCase)
emit(
    "STOCK Edge 2070-01-01 2070-01-03 2", "STOCK Huge 2070-01-01 2070-01-03 1",
    "BEGIN PESSIMISTIC", "TX hold held Edge 2070-01-02 2070-01-03 1",
    "TX wait waited Edge 2070-01-01 2070-01-03 1", "TX free freeRoom Edge 2070-01-01 2070-01-02 1",
    "TX other otherRoom Huge 2070-01-02 2070-01-03 1",
    "STEP hold", "STEP wait", "STEP wait", "STEP free", "STEP other", "STEP hold",
    "STEP free", "STEP other", "STEP wait", "STEP wait", "END", "STATUS waited"
)
emit(
    "TX a outside Edge 2030-01-01 2030-01-02 1", "STEP a", "END", "BEGIN unknown",
    "BEGIN PESSIMISTIC extra", "TX a bad Edge 2030-01-01 2030-01-02 nope", "STEP 9bad",
    "BEGIN OPTIMISTIC", "TX a bad Edge 2030-01-01 2030-01-02 0", "AVAIL Edge 2100-01-01",
    "REQUEST new BOOK bad Edge 2030-01-01 2030-01-02 +1", "STOCK Edge 2030-01-01 2030-01-02 0", "END"
)
require(lines.count { it.isNotBlank() } <= 3000)
print(lines.joinToString("\n", postfix = "\n"))
