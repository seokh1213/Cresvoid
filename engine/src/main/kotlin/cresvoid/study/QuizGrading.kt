package cresvoid.study

import cresvoid.content.Question
import cresvoid.content.Quiz
import cresvoid.progress.QuizProgress
import cresvoid.progress.READING_SESSION_GAP
import cresvoid.progress.StageProgress
import java.time.Duration
import java.time.OffsetDateTime

const val REVEAL_AFTER_WRONG = 3

fun Quiz.allClosed(progress: QuizProgress): Boolean = questions.all { progress.isClosed(it.id) }

sealed interface QuizTiming {
    data class Measured(val begin: OffsetDateTime) : QuizTiming

    data class Unmeasured(val reason: String) : QuizTiming
}

fun quizTiming(stage: StageProgress, started: OffsetDateTime): QuizTiming {
    val openedAt = stage.openedAt
    val lastReadAt = stage.lastReadAt
    return when {
        openedAt == null || lastReadAt == null ->
            QuizTiming.Unmeasured("읽기 기록이 없어 이번 풀이 시간은 재지 않습니다.")
        Duration.between(lastReadAt, started) > READING_SESSION_GAP ->
            QuizTiming.Unmeasured("마지막으로 읽은 지 한 시간이 넘어 이번 풀이 시간은 재지 않습니다.")
        else -> QuizTiming.Measured(openedAt)
    }
}

sealed interface QuizGrade {
    data object NotSaved : QuizGrade

    class Graded(
        val firstAttempt: Boolean,
        val correct: Int,
        val wrong: List<Question>,
        val progress: QuizProgress,
        val completed: Boolean,
    ) : QuizGrade
}

class QuizSubmission(
    val quiz: Quiz,
    val open: List<Question>,
    val responses: List<String>,
    val aborted: Boolean,
    val timing: QuizTiming,
)

fun grade(before: QuizProgress, submission: QuizSubmission, now: OffsetDateTime): QuizGrade {
    val firstAttempt = before.firstCorrect == null
    if (submission.aborted && (!firstAttempt || submission.responses.isEmpty())) return QuizGrade.NotSaved
    val resolved = before.resolved.toMutableList()
    val revealed = before.revealed.toMutableList()
    val wrongCounts = before.wrong.toMutableMap()
    val wrong = mutableListOf<Question>()
    submission.open.forEachIndexed { index, question ->
        if (question.id in resolved || question.id in revealed) return@forEachIndexed
        val response = submission.responses.getOrNull(index)
        if (response != null && question.isCorrect(response)) {
            resolved += question.id
            return@forEachIndexed
        }
        val count = (wrongCounts[question.id] ?: 0) + 1
        wrongCounts[question.id] = count
        if (count >= REVEAL_AFTER_WRONG) revealed += question.id
        wrong += question
    }
    val correct = resolved.size - before.resolved.size
    val measuredSeconds = (submission.timing as? QuizTiming.Measured)
        ?.let { (Duration.between(it.begin, now).toNanos() / 1_000_000_000).toInt() }
    val graded = before.copy(
        resolved = resolved,
        revealed = revealed,
        wrong = wrongCounts,
        firstCorrect = if (firstAttempt) correct else before.firstCorrect,
        firstTotal = if (firstAttempt) submission.open.size else before.firstTotal,
        firstSeconds = measuredSeconds?.takeIf { firstAttempt } ?: before.firstSeconds,
    )
    val completed = submission.quiz.allClosed(graded)
    val progress = if (completed && graded.completedAt == null) graded.copy(completedAt = now) else graded
    return QuizGrade.Graded(firstAttempt, correct, wrong, progress, completed)
}
