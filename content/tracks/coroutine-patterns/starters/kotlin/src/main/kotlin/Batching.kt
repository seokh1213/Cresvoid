import kotlinx.coroutines.flow.Flow

data class BatchChange(val id: String)

data class BatchPolicy(val size: Int, val window: Long, val waiting: Int)

interface BatchServices {
    fun now(): Long

    fun take(change: BatchChange)

    fun seal(batch: List<BatchChange>)

    suspend fun save(batch: List<BatchChange>)
}

class SourceFailure : RuntimeException("원본이 실패했다")

class SaveFailure(val ordinal: Int) : RuntimeException("$ordinal 번째 저장이 실패했다")

data class BatchReport(val saved: Int, val changes: Int)

suspend fun Flow<BatchChange>.persistBatches(
    policy: BatchPolicy,
    services: BatchServices,
): BatchReport {
    TODO("5단계: 개수 또는 첫 항목의 마감 시각으로 묶고, 대기 한도를 지키며 순서대로 저장한다")
}
