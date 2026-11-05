private val productCode = Regex("[A-Z0-9][A-Z0-9-]{0,23}")

fun main() {
    val catalog = ProductCatalog()
    var workerCount = 2
    generateSequence(::readlnOrNull).forEach { line ->
        val command = line.trim()
        if (command.isNotEmpty()) {
            val words = command.split(Regex("\\s+"))
            val reply = when {
                words[0] == "PUT" && words.size == 3 &&
                    productCode.matches(words[1]) && words[2].length in 1..40 ->
                    catalog.put(words[1], words[2])
                words[0] == "SHOW" && words.size == 2 && productCode.matches(words[1]) ->
                    catalog.show(words[1])
                words[0] == "WORKERS" && words.size == 2 && words[1] in setOf("1", "2") -> {
                    workerCount = words[1].toInt()
                    "WORKERS $workerCount"
                }
                words[0] == "REFRESH" -> refresh(words.drop(1), workerCount, catalog)
                else -> "ERROR command"
            }
            println(reply)
        }
    }
}

private fun refresh(codes: List<String>, workerCount: Int, catalog: ProductCatalog): String {
    val emptyCheck = codes == listOf("-")
    if (!emptyCheck && (codes.size !in 1..100 || codes.any { !productCode.matches(it) })) {
        return "ERROR command"
    }
    val requested = if (emptyCheck) emptyList() else codes
    val products = catalog.snapshot(requested) ?: return "ERROR missing-product"
    val report = RefreshWorkers(workerCount, catalog).execute(products)
    return report.render(products.size)
}
