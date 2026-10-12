import kotlinx.coroutines.channels.ReceiveChannel
import kotlinx.coroutines.channels.SendChannel
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.launch

data class CollectedItem(val position: Int, val name: String)

data class TransformResult(val position: Int, val outcome: TransformOutcome)

suspend fun collectItems(items: List<String>, services: FeedServices, collected: SendChannel<CollectedItem>) {
    items.forEachIndexed { position, item ->
        services.fetch(item)
        collected.send(CollectedItem(position, item))
    }
    collected.close()
}

suspend fun transformItems(
    workers: Int,
    services: FeedServices,
    collected: ReceiveChannel<CollectedItem>,
    results: SendChannel<TransformResult>,
) {
    coroutineScope {
        repeat(workers) {
            launch {
                for (item in collected) {
                    results.send(TransformResult(item.position, services.transform(item.name)))
                }
            }
        }
    }
    results.close()
}

suspend fun storeInSourceOrder(
    items: List<String>,
    services: FeedServices,
    results: ReceiveChannel<TransformResult>,
): PipelineResult.Completed {
    val waiting = HashMap<Int, TransformOutcome>()
    val processed = ArrayList<TransformOutcome>(items.size)
    for (result in results) {
        waiting[result.position] = result.outcome
        while (true) {
            val nextPosition = processed.size
            val outcome = waiting.remove(nextPosition) ?: break
            if (outcome == TransformOutcome.TRANSFORMED) services.store(items[nextPosition])
            processed += outcome
        }
    }
    return PipelineResult.Completed(
        stored = processed.count { it == TransformOutcome.TRANSFORMED },
        skipped = processed.count { it == TransformOutcome.SKIPPED },
    )
}
