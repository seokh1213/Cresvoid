fun listingLines(entry: Entry): List<String> = when (entry) {
    is File -> listOf(listingLine(entry))
    is Directory -> entry.children.map(::listingLine).ifEmpty { listOf("(empty)") }
}

private fun listingLine(entry: Entry): String = when (entry) {
    is Directory -> "${entry.name}/"
    is File -> "${entry.name} ${entry.size} ${entry.replication}"
}

fun countLine(entry: Entry): String {
    val usage = entry.usage
    val quotas = when (entry) {
        is Directory -> "nsquota=${entry.nameQuota ?: "none"} dsquota=${entry.spaceQuota ?: "none"}"
        is File -> "nsquota=none dsquota=none"
    }
    return "dirs=${usage.directories} files=${usage.files} size=${usage.size} space=${usage.space} $quotas"
}

fun blockLines(file: File): List<String> = file.blocks.map { "${it.name} ${it.size}" }.ifEmpty { listOf("(none)") }

fun locationLines(file: File): List<String> =
    file.blocks.map { "${it.name} ${it.size} ${locationNames(it)}" }.ifEmpty { listOf("(none)") }

private fun locationNames(block: Block): String =
    block.locations.map { it.name }.sorted().joinToString(" ").ifEmpty { "(none)" }

fun nodeLine(node: DataNode): String =
    "${node.name} rack=${node.rack} state=live used=${node.used} remaining=${node.remaining} blocks=${node.replicaCount}"

fun fsckLine(health: BlockHealth): String =
    "blocks=${health.blocks} replicas=${health.replicas} under=${health.underReplicated} missing=${health.missing}"
