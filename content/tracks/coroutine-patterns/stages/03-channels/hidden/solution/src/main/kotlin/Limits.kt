import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit

suspend fun refreshPrices(products: List<String>, services: RefreshServices, policy: RefreshPolicy): RefreshReport =
    coroutineScope {
        // Semaphore 는 기다리기 시작한 순서대로 허가를 넘겨주므로 상품이 ITEM 순서대로 시작한다.
        val slots = Semaphore(policy.concurrency)
        val outcomes = products
            .map { product -> async { slots.withPermit { refreshProduct(product, services, policy) } } }
            .awaitAll()
        RefreshReport(
            succeeded = outcomes.count { it == RefreshOutcome.SUCCEEDED },
            failed = outcomes.count { it == RefreshOutcome.FAILED },
        )
    }
