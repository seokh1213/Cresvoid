import kotlinx.coroutines.CoroutineScope

data class SeedPrice(val product: String, val value: Int, val writtenAt: Long)

enum class LookupSource { FETCHED, FRESH, STALE }

data class LookupValue(val value: Int, val source: LookupSource)

class LookupFailure(val code: Int) : RuntimeException("조회가 $code 로 실패했다")

interface LookupServices {
    fun now(): Long

    suspend fun fetch(product: String): Int
}

class SingleFlightLookup(
    private val owner: CoroutineScope,
    private val ttl: Long,
    private val services: LookupServices,
    initialCache: List<SeedPrice>,
) {
    suspend fun lookup(product: String): LookupValue {
        TODO("6단계: 상품별 공유 호출과 캐시 만료·배경 갱신을 구현한다")
    }
}
