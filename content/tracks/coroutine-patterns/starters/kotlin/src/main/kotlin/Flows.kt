import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.StateFlow

fun currentPrice(
    scope: CoroutineScope,
    events: Flow<PriceEvent>,
    product: String,
    initial: Int,
    stopTimeout: Long,
): StateFlow<Int> {
    TODO("4단계: 현재 판매가를 여러 수집자가 함께 쓰는 StateFlow 로 내보낸다")
}

fun auditFeed(events: Flow<PriceEvent>, product: String, capacity: Int): Flow<Int> {
    TODO("4단계: 판매가 변화를 하나도 빠뜨리지 않는 감사 흐름을 만든다")
}

fun monitorFeed(events: Flow<PriceEvent>, product: String): Flow<Int> {
    TODO("4단계: 느린 수집자가 마지막 판매가만 받는 흐름을 만든다")
}
