package cresvoid.study

import cresvoid.content.Stage
import cresvoid.content.TrackKind
import cresvoid.progress.StageProgress
import cresvoid.project.isSameDirectory
import java.time.OffsetDateTime

sealed interface NextStep {
    data class CreateWorkspace(val stage: Stage?) : NextStep

    data class Read(val stage: Stage) : NextStep

    data class SolveQuiz(val stage: Stage) : NextStep

    data class PassTests(val stage: Stage) : NextStep

    data class Review(val stage: Stage, val inNewWorkspace: Boolean) : NextStep

    data object Finished : NextStep
}

fun TrackState.nextStep(now: OffsetDateTime): NextStep = when (track.kind) {
    TrackKind.Build -> buildStep(now)
    TrackKind.Fix -> fixStep(now)
    TrackKind.Reading, TrackKind.Code -> quizStep()
}

private fun TrackState.buildStep(now: OffsetDateTime): NextStep {
    if (solutionDir == null) return NextStep.CreateWorkspace(stage = null)
    val stage = currentStage ?: return reviewStep(now)
    return studyStep(stage, stageProgress(stage)) ?: NextStep.PassTests(stage)
}

private fun TrackState.fixStep(now: OffsetDateTime): NextStep {
    val stage = currentStage ?: return reviewStep(now)
    return when {
        workspace(stage) == null -> NextStep.CreateWorkspace(stage)
        stageProgress(stage).openedAt == null -> NextStep.Read(stage)
        else -> NextStep.PassTests(stage)
    }
}

private fun TrackState.quizStep(): NextStep {
    val stage = currentStage ?: return NextStep.Finished
    return studyStep(stage, stageProgress(stage)) ?: NextStep.SolveQuiz(stage)
}

private fun studyStep(stage: Stage, progress: StageProgress): NextStep? = when {
    progress.openedAt == null && progress.quiz.firstCorrect == null -> NextStep.Read(stage)
    progress.quiz.completedAt == null -> NextStep.SolveQuiz(stage)
    else -> null
}

private fun TrackState.reviewStep(now: OffsetDateTime): NextStep {
    val stage = track.stages.firstOrNull { stageProgress(it).isReviewDue(now) } ?: return NextStep.Finished
    val workspace = workspace(stage)
    val passedDir = project.abs(stageProgress(stage).passedDir.orEmpty())
    val inNewWorkspace = workspace == null || isSameDirectory(project.abs(workspace), passedDir)
    return NextStep.Review(stage, inNewWorkspace)
}
