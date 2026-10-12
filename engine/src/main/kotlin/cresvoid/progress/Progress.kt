package cresvoid.progress

import java.time.Duration
import java.time.OffsetDateTime

val READING_SESSION_GAP: Duration = Duration.ofHours(1)
val REVIEW_INTERVAL: Duration = Duration.ofDays(14)

data class Progress(val tracks: Map<String, TrackProgress> = emptyMap()) {
    fun stageOrEmpty(trackId: String, stageDir: String): StageProgress =
        tracks[trackId]?.stages?.get(stageDir) ?: StageProgress()

    fun updateTrack(trackId: String, change: (TrackProgress) -> TrackProgress): Progress =
        copy(tracks = tracks + (trackId to change(tracks[trackId] ?: TrackProgress())))

    fun updateStage(trackId: String, stageDir: String, change: (StageProgress) -> StageProgress): Progress =
        updateTrack(trackId) { track ->
            track.copy(stages = track.stages + (stageDir to change(track.stages[stageDir] ?: StageProgress())))
        }
}

data class TrackProgress(
    val solutionDir: String? = null,
    val stages: Map<String, StageProgress> = emptyMap(),
)

data class StageProgress(
    val solutionDir: String? = null,
    val openedAt: OffsetDateTime? = null,
    val lastReadAt: OffsetDateTime? = null,
    val quiz: QuizProgress = QuizProgress(),
    val passedAt: OffsetDateTime? = null,
    val passedDir: String? = null,
    val reviewedAt: OffsetDateTime? = null,
) {
    fun markedRead(now: OffsetDateTime): StageProgress {
        val sessionExpired = lastReadAt == null || Duration.between(lastReadAt, now) > READING_SESSION_GAP
        return copy(openedAt = if (sessionExpired) now else openedAt, lastReadAt = now)
    }

    fun isReviewDue(now: OffsetDateTime): Boolean =
        passedAt != null && reviewedAt == null && !now.isBefore(passedAt.plus(REVIEW_INTERVAL))
}

data class QuizProgress(
    val resolved: List<String> = emptyList(),
    val revealed: List<String> = emptyList(),
    val wrong: Map<String, Int> = emptyMap(),
    val firstCorrect: Int? = null,
    val firstTotal: Int = 0,
    val firstSeconds: Int? = null,
    val completedAt: OffsetDateTime? = null,
) {
    fun isClosed(questionId: String): Boolean = questionId in resolved || questionId in revealed
}
