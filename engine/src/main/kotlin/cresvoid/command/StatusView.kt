package cresvoid.command

import cresvoid.content.Stage
import cresvoid.content.TrackKind
import cresvoid.progress.REVIEW_INTERVAL
import cresvoid.progress.StageProgress
import cresvoid.study.TrackState
import cresvoid.terminal.dim
import cresvoid.terminal.formatSeconds
import cresvoid.terminal.green
import cresvoid.terminal.yellow
import java.time.OffsetDateTime
import java.time.format.DateTimeFormatter

internal class StatusView(private val state: TrackState, private val now: OffsetDateTime) {
    private val kind = state.track.kind

    fun workspaceLines(): List<String> {
        val workspace = when (kind) {
            TrackKind.Build -> state.solutionDir
            TrackKind.Fix -> state.currentStage?.let(state::workspace)
            TrackKind.Reading, TrackKind.Code -> null
        }
        return listOfNotNull(workspace?.let { "${dim("풀이:")} $it" })
    }

    fun stageMarks(stage: Stage): List<String> {
        val progress = state.stageProgress(stage)
        return when (kind) {
            TrackKind.Build -> listOf(readMark(progress), quizMark(progress, stage), testMark(progress))
            TrackKind.Fix -> listOf(readMark(progress), testMark(progress))
            TrackKind.Reading, TrackKind.Code -> listOf(readMark(progress), quizMark(progress, stage))
        }
    }

    fun reviewLines(): List<String> = when (kind) {
        TrackKind.Build -> reviewSchedule(redo = "빈 작업 공간에서 다시 구현하고")
        TrackKind.Fix -> reviewSchedule(redo = "새 작업 공간에서 다시 고치고")
        TrackKind.Reading, TrackKind.Code -> emptyList()
    }

    private fun reviewSchedule(redo: String): List<String> =
        state.track.stages.mapNotNull { stage ->
            val progress = state.stageProgress(stage)
            val passedAt = progress.passedAt
            when {
                passedAt == null || progress.reviewedAt != null -> null
                progress.isReviewDue(now) -> yellow("복습할 차례: ${stage.dir}. $redo ${stage.number}단계 테스트를 통과하세요.")
                else -> {
                    val due = passedAt.plus(REVIEW_INTERVAL).format(DateTimeFormatter.ISO_LOCAL_DATE)
                    dim("복습 예정: ${stage.dir}, ${due}부터")
                }
            }
        }
}

internal fun mark(done: Boolean): String = if (done) green("✓") else dim("·")

internal fun readMark(progress: StageProgress): String = "읽기 " + mark(progress.openedAt != null)

internal fun testMark(progress: StageProgress): String = "테스트 " + mark(progress.passedAt != null)

internal fun quizMark(progress: StageProgress, stage: Stage): String = buildString {
    val quiz = progress.quiz
    append("문항 ").append(mark(quiz.completedAt != null))
    val firstCorrect = quiz.firstCorrect ?: return@buildString
    append(" $firstCorrect/${quiz.firstTotal}")
    if (quiz.revealed.isNotEmpty()) append(" 공개 ${quiz.revealed.size}")
    val firstSeconds = quiz.firstSeconds ?: return@buildString
    append(" ").append(formatSeconds(firstSeconds))
    if (stage.targetMinutes > 0) append("/목표 ${stage.targetMinutes}분")
}
