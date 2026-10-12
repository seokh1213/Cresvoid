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
    block.knownLocations.map { it.name }.sorted().joinToString(" ").ifEmpty { "(none)" }

fun nodeLine(node: DataNode, state: NodeState): String =
    "${node.name} rack=${node.rack} state=${state.keyword} used=${node.used} remaining=${node.remaining} " +
        "blocks=${node.knownReplicaCount}"

fun fsckLine(health: BlockHealth): String =
    "blocks=${health.blocks} replicas=${health.replicas} under=${health.underReplicated} missing=${health.missing}"

fun safeModeLine(state: SafeModeState, health: BlockHealth): String = when (state) {
    SafeModeState.Off -> "off"
    SafeModeState.AwaitingReports -> safeModeOnLine(health, exit = "-")
    is SafeModeState.Extending -> safeModeOnLine(health, exit = state.exitAt.toString())
}

private fun safeModeOnLine(health: BlockHealth, exit: String): String =
    "on safe=${health.safeBlocks} needed=${requiredSafeBlocks(health.blocks)} total=${health.blocks} exit=$exit"

fun eventLine(event: Event): String = when (event) {
    is Event.Stale -> "${event.time} STALE ${event.node.name}"
    is Event.Dead -> "${event.time} DEAD ${event.node.name}"
    is Event.Replicate -> "${event.time} REPLICATE ${event.block.name} ${event.source.name} ${event.target.name}"
    is Event.Delete -> "${event.time} DELETE ${event.block.name} ${event.node.name}"
    is Event.SafeModeOff -> "${event.time} SAFEMODE OFF"
}
