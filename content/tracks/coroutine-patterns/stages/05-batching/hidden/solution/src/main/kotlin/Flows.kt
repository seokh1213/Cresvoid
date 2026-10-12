import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.buffer
import kotlinx.coroutines.flow.conflate
import kotlinx.coroutines.flow.stateIn

fun currentPrice(
    scope: CoroutineScope,
    events: Flow<PriceEvent>,
    product: String,
    initial: Int,
    stopTimeout: Long,
): StateFlow<Int> =
    events.salePriceChanges(product)
        // replayExpirationMillis 를 기본값으로 두어야 공유 구독이 멈춘 뒤 현재 판매가가 처음 가격으로 돌아가지 않는다.
        .stateIn(scope, SharingStarted.WhileSubscribed(stopTimeoutMillis = stopTimeout), initial)

fun auditFeed(events: Flow<PriceEvent>, product: String, capacity: Int): Flow<Int> =
    events.salePriceChanges(product).buffer(capacity)

fun monitorFeed(events: Flow<PriceEvent>, product: String): Flow<Int> =
    events.salePriceChanges(product).conflate()
