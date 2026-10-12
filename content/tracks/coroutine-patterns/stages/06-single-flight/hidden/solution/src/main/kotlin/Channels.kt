import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.launch

suspend fun runFeedPipeline(items: List<String>, services: FeedServices, settings: PipelineSettings): PipelineResult =
    try {
        coroutineScope {
            val collected = Channel<CollectedItem>(settings.capacity)
            val results = Channel<TransformResult>(Channel.UNLIMITED)
            launch { collectItems(items, services, collected) }
            launch { transformItems(settings.workers, services, collected, results) }
            storeInSourceOrder(items, services, results)
        }
    } catch (failure: TransformFailure) {
        PipelineResult.Failed(failure.item)
    }
