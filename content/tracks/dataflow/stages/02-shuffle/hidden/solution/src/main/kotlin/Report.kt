fun planLines(plan: Plan): List<String> = plan.stages.map { stageLine(it, it.output.partitions) }

fun jobHeader(job: Int, outcome: JobOutcome): String =
    "job $job stages ${outcome.stages.size} tasks ${outcome.tasks} rows ${outcome.rows}"

fun profileLines(job: Int, outcome: JobOutcome): List<String> =
    listOf(jobHeader(job, outcome)) + outcome.stages.flatMap(::stageProfileLines)

private fun stageProfileLines(report: StageReport): List<String> = buildList {
    add(stageLine(report.stage, report.tasks))
    when (val output = report.output) {
        is StageOutput.Shuffled -> add(shuffleLine(output.shuffle))
        is StageOutput.Result -> {}
    }
}

private fun stageLine(stage: Stage, tasks: Int): String =
    "stage ${stage.number} tasks $tasks ${stage.nodes.joinToString(" ") { it.label }} -> ${destinationText(stage.destination)}"

private fun destinationText(destination: Destination): String = when (destination) {
    is Destination.Shuffle -> "shuffle ${destination.stage}"
    Destination.Result -> "result"
}

private fun shuffleLine(shuffle: ShuffleOutput): String =
    "write records ${shuffle.records} bytes ${shuffle.bytes} spills ${shuffle.spills} sizes ${shuffle.sizes.joinToString(",")}"
