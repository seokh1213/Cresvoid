package cresvoid.command

import cresvoid.CresvoidError
import cresvoid.cli.Command
import cresvoid.cli.parseTrackArguments
import cresvoid.cli.stageFlag
import cresvoid.content.CodeLocation
import cresvoid.content.Question
import cresvoid.content.QuestionType
import cresvoid.content.Quiz
import cresvoid.content.Stage
import cresvoid.content.Track
import cresvoid.content.isCodeLocation
import cresvoid.content.loadQuiz
import cresvoid.progress.ProgressRepository
import cresvoid.progress.QuizProgress
import cresvoid.progress.StageProgress
import cresvoid.progress.Updated
import cresvoid.project.Project
import cresvoid.study.QuizGrade
import cresvoid.study.QuizSubmission
import cresvoid.study.QuizTiming
import cresvoid.study.REVEAL_AFTER_WRONG
import cresvoid.study.TrackState
import cresvoid.study.TrackStates
import cresvoid.study.allClosed
import cresvoid.study.grade
import cresvoid.study.quizTiming
import cresvoid.terminal.AnswerSource
import cresvoid.terminal.Console
import cresvoid.terminal.Terminal
import cresvoid.terminal.bold
import cresvoid.terminal.dim
import cresvoid.terminal.formatRefs
import cresvoid.terminal.formatSeconds
import cresvoid.terminal.green
import cresvoid.terminal.indent
import cresvoid.terminal.red
import cresvoid.terminal.yellow
import java.time.Clock
import java.time.OffsetDateTime

class QuizCommand(
    private val project: Project,
    private val states: TrackStates,
    private val progress: ProgressRepository,
    private val console: Console,
    private val terminal: Terminal,
    private val clock: Clock,
) : Command {
    override val name = "quiz"
    override val usage = "cresvoid quiz <트랙> [-stage N]"

    override fun run(args: List<String>) {
        val arguments = parseTrackArguments(this, args, listOf(stageFlag))
        val state = states.load(arguments.track)
        val track = state.track
        if (track.kind.quizRules == null) {
            throw CresvoidError("${track.kind.label} 트랙에는 확인 문항이 없습니다. ${state.nextLine(OffsetDateTime.now(clock))}")
        }
        val stage = state.selectStage(arguments.stage)
        val quiz = loadQuiz(stage)
        console.println("${bold("${track.id} ${stage.dir}")} ${stage.title} · ${dim(project.rel(stage.documentPath))}")
        val snapshot = state.stageProgress(stage)
        val open = quiz.questions.filterNot { snapshot.quiz.isClosed(it.id) }
        if (open.isEmpty()) {
            showClosedQuiz(track, stage, quiz, snapshot)
            return
        }
        val firstAttempt = snapshot.quiz.firstCorrect == null
        val timing = quizTiming(snapshot, OffsetDateTime.now(clock))
        if (firstAttempt && timing is QuizTiming.Unmeasured) console.println(yellow(timing.reason))
        console.println("남은 문항 ${open.size}개. 모두 답한 뒤 한꺼번에 채점합니다. 문서는 다른 창에서 다시 봐도 됩니다.")
        val answers = askQuestions(open)
        val submission = QuizSubmission(quiz, open, answers.responses, answers.aborted, timing)
        val now = OffsetDateTime.now(clock)
        val update = progress.updateWith { latest ->
            val result = grade(latest.stageOrEmpty(track.id, stage.dir).quiz, submission, now)
            val next = when (result) {
                QuizGrade.NotSaved -> latest
                is QuizGrade.Graded -> latest.updateStage(track.id, stage.dir) { it.copy(quiz = result.progress) }
            }
            Updated(next, result)
        }
        when (val result = update.result) {
            QuizGrade.NotSaved -> console.println("\n중단했습니다. 이번 답은 저장하지 않았습니다.")
            is QuizGrade.Graded -> {
                if (answers.aborted) console.println("\n중단했습니다. 첫 시도라서 답하지 않은 문항은 틀린 것으로 채점했습니다.")
                reportGrade(state, stage, submission, result, state.withProgress(update.progress), now)
            }
        }
    }

    private class Answers(val responses: List<String>, val aborted: Boolean)

    private fun askQuestions(open: List<Question>): Answers {
        terminal.answers().use { source ->
            val responses = mutableListOf<String>()
            open.forEachIndexed { index, question ->
                printQuestion(index + 1, open.size, question)
                val response = ask(source, question) ?: return Answers(responses, aborted = true)
                responses += response
            }
            return Answers(responses, aborted = false)
        }
    }

    private fun ask(source: AnswerSource, question: Question): String? {
        while (true) {
            console.print(if (question.type == QuestionType.CHOICE) "답 (1-${question.choices.size}): " else "답: ")
            val answer = source.nextLine()?.trim() ?: return null
            if (question.accepts(answer)) return answer
            console.println("다시 입력하세요.")
        }
    }

    private fun reportGrade(
        snapshot: TrackState,
        stage: Stage,
        submission: QuizSubmission,
        result: QuizGrade.Graded,
        fresh: TrackState,
        now: OffsetDateTime,
    ) {
        console.println("\n채점: ${submission.open.size}문항 중 ${result.correct}문항 정답")
        if (result.firstAttempt) printQuizSummary(result.progress, stage)
        for (question in result.wrong) {
            printQuizHint(stage, question, result.progress.wrong[question.id] ?: 0)
        }
        if (!result.completed) {
            console.println("\n해당 문단을 다시 읽고 틀린 문항을 다시 푸세요: cresvoid quiz ${snapshot.track.id}${snapshot.stageFlag(stage)}")
            return
        }
        console.println(green("\n이 단계 문항을 모두 마쳤습니다."))
        printExplanations(submission.quiz)
        console.println()
        console.println(fresh.nextLine(now))
    }

    private fun showClosedQuiz(track: Track, stage: Stage, quiz: Quiz, snapshot: StageProgress) {
        if (snapshot.quiz.completedAt == null) {
            val now = OffsetDateTime.now(clock)
            progress.update { latest ->
                latest.updateStage(track.id, stage.dir) { stageProgress ->
                    val quizProgress = stageProgress.quiz
                    if (quizProgress.completedAt == null && quiz.allClosed(quizProgress)) {
                        stageProgress.copy(quiz = quizProgress.copy(completedAt = now))
                    } else {
                        stageProgress
                    }
                }
            }
        }
        console.println("이 단계 문항은 모두 마쳤습니다.")
        printQuizSummary(snapshot.quiz, stage)
        printExplanations(quiz)
    }

    private fun printQuestion(position: Int, total: Int, question: Question) {
        console.println("\n${bold("[$position/$total]")} ${question.prompt}")
        if (question.context.isNotEmpty()) {
            console.println()
            console.println(indent(question.context, "    "))
        }
        if (question.input.isNotEmpty()) {
            console.println()
            console.println("    입력:")
            question.input.forEach { console.println("      $it") }
        }
        if (question.type == QuestionType.CHOICE) {
            console.println()
            question.choices.forEachIndexed { index, choice -> console.println("  ${index + 1}) $choice") }
        }
    }

    private fun printQuizSummary(quiz: QuizProgress, stage: Stage) {
        val firstCorrect = quiz.firstCorrect ?: return
        val firstSeconds = quiz.firstSeconds
        val time = when {
            firstSeconds == null -> ", 시간은 재지 않음"
            stage.targetMinutes > 0 -> ", 걸린 시간 ${formatSeconds(firstSeconds)} (목표 ${stage.targetMinutes}분)"
            else -> ", 걸린 시간 ${formatSeconds(firstSeconds)}"
        }
        console.println("첫 시도 $firstCorrect/${quiz.firstTotal}$time")
    }

    private fun printQuizHint(stage: Stage, question: Question, wrongCount: Int) {
        console.println("  ${red("✗")} ${question.id}: ${rereadingPlaces(project.rel(stage.documentPath), question.refs)}을 다시 읽으세요.")
        if (wrongCount >= 2) console.println("    핵심 문장: ${question.quote}")
        if (wrongCount >= REVEAL_AFTER_WRONG) {
            console.println("    정답: ${question.correctAnswerText()}")
            console.println(indent("해설: ${question.explanation}", "    "))
            console.println(dim("    세 번 틀려서 정답을 공개하고 이 문항을 닫았습니다."))
        }
    }

    private fun printExplanations(quiz: Quiz) {
        console.println(bold("\n해설"))
        for (question in quiz.questions) {
            console.println("\n${bold(question.id)} ${question.prompt}")
            console.println("  정답: ${question.correctAnswerText()}")
            console.println(indent(question.explanation, "  "))
        }
    }
}

fun rereadingPlaces(document: String, refs: List<String>): String {
    val (codeRefs, paragraphs) = refs.partition(::isCodeLocation)
    val places = listOfNotNull(
        paragraphs.takeIf { it.isNotEmpty() }?.let { "$document 의 ${formatRefs(it)} 문단" },
        codeRefs.takeIf { it.isNotEmpty() }?.joinToString(", ") { ref -> CodeLocation.parse(ref)?.label ?: ref },
    )
    return places.joinToString("과 ")
}
