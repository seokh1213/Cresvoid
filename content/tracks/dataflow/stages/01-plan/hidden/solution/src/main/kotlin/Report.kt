fun planLines(plan: Plan): List<String> = plan.stages.map { stage ->
    "stage ${stage.number} tasks ${stage.output.partitions} ${stage.nodes.joinToString(" ") { it.label }} -> ${destinationText(stage.destination)}"
}

fun jobHeader(job: Int, outcome: JobOutcome): String =
    "job $job stages ${outcome.stages.size} tasks ${outcome.tasks} rows ${outcome.rows}"

private fun destinationText(destination: Destination): String = when (destination) {
    is Destination.Shuffle -> "shuffle ${destination.stage}"
    Destination.Result -> "result"
}
