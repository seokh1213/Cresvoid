object CommandParser {
    private val fieldSeparator = Regex("\\s+")
    private val productIdPattern = Regex("[a-z][a-z0-9-]{0,15}")
    private val integerPattern = Regex("[0-9]+")

    fun parse(line: String): PlacementRequest? {
        val fields = line.trim().split(fieldSeparator)
        if (fields.size != 6 || fields[0] != "PLACE") return null
        val status = when (fields[1]) {
            "OK" -> OptimizerStatus.OK
            "FAIL" -> OptimizerStatus.FAIL
            else -> return null
        }
        val slots = parseInteger(fields[2], 1..8) ?: return null
        val limit = parseInteger(fields[3], 0..slots) ?: return null
        val organic = parseCandidates(fields[4]) ?: return null
        val recommended = parseCandidates(fields[5]) ?: return null
        val allCandidates = organic + recommended
        if (allCandidates.map { it.productId }.toSet().size != allCandidates.size) return null
        return PlacementRequest(status, slots, limit, organic, recommended)
    }

    private fun parseCandidates(field: String): List<Candidate>? {
        if (field == "-") return emptyList()
        val entries = field.split(',')
        if (entries.size > 8) return null
        val candidates = mutableListOf<Candidate>()
        for (entry in entries) {
            val parts = entry.split(':')
            if (parts.size != 2) return null
            val productId = parts[0]
            if (!productIdPattern.matches(productId)) return null
            val score = parseInteger(parts[1], 0..1_000_000) ?: return null
            candidates.add(Candidate(productId, score))
        }
        return candidates.toList()
    }

    private fun parseInteger(field: String, range: IntRange): Int? {
        if (!integerPattern.matches(field)) return null
        val value = field.trimStart('0').ifEmpty { "0" }.toIntOrNull() ?: return null
        return value.takeIf { it in range }
    }
}
