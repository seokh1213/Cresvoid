fun planLines(plan: Plan): List<String> = plan.stages.map { stage ->
    stageLine(stage, stage.output.partitions, stage.nodes.map { it.label })
}

fun jobHeader(job: Int, outcome: JobOutcome): String =
    "job $job stages ${outcome.stages.size} tasks ${outcome.tasks} rows ${outcome.rows}"

fun profileLines(job: Int, outcome: JobOutcome, dictionary: KeyDictionary): List<String> =
    listOf(jobHeader(job, outcome)) + outcome.stages.flatMap { stageProfileLines(it, dictionary) }

private fun stageProfileLines(report: StageReport, dictionary: KeyDictionary): List<String> = buildList {
    add(stageLine(report.stage, report.tasks, report.labels))
    report.decisions.skew?.let { addAll(skewLines(it)) }
    report.decisions.merge?.takeIf { it.reducesTasks }?.let { add(mergeLine(it)) }
    when (val output = report.output) {
        is StageOutput.Shuffled -> add(shuffleLine(output.shuffle))
        is StageOutput.Broadcast -> add(broadcastLine(output.rows, dictionary))
        is StageOutput.Result -> {}
    }
}

private fun stageLine(stage: Stage, tasks: Int, labels: List<String>): String =
    "stage ${stage.number} tasks $tasks ${labels.joinToString(" ")} -> ${destinationText(stage.destination)}"

private fun destinationText(destination: Destination): String = when (destination) {
    is Destination.Shuffle -> "shuffle ${destination.stage}"
    is Destination.Broadcast -> "broadcast ${destination.stage}"
    Destination.Result -> "result"
}

private fun skewLines(split: SkewSplit): List<String> = split.left.indices.flatMap { partition ->
    listOfNotNull(
        split.left[partition].size.takeIf { it > 1 }?.let { "skew left $partition $it" },
        split.right[partition].size.takeIf { it > 1 }?.let { "skew right $partition $it" },
    )
}

private fun mergeLine(merge: PartitionMerge): String =
    "merge " + merge.groups.joinToString(",") { "${it.first}-${it.last}" }

private fun shuffleLine(shuffle: ShuffleOutput): String =
    "write records ${shuffle.records} bytes ${shuffle.bytes} spills ${shuffle.spills} sizes ${shuffle.sizes.joinToString(",")}"

private fun broadcastLine(rows: Partition, dictionary: KeyDictionary): String =
    "broadcast rows ${rows.size} bytes ${(0 until rows.size).sumOf { rows.rowBytes(it, dictionary).toLong() }}"
