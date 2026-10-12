package cresvoid.study

import cresvoid.content.Completion
import cresvoid.content.ContentLibrary
import cresvoid.content.Stage
import cresvoid.content.Track
import cresvoid.content.WorkspaceScope
import cresvoid.progress.Progress
import cresvoid.progress.ProgressRepository
import cresvoid.progress.StageProgress
import cresvoid.project.Project

class TrackState(
    val project: Project,
    val track: Track,
    val progress: Progress,
) {
    val solutionDir: String? get() = progress.tracks[track.id]?.solutionDir

    val currentStage: Stage? get() = track.stages.firstOrNull { !isDone(it) }

    fun stageProgress(stage: Stage): StageProgress = progress.stageOrEmpty(track.id, stage.dir)

    fun workspace(stage: Stage): String? = when (track.kind.workspaceScope) {
        WorkspaceScope.TRACK -> solutionDir
        WorkspaceScope.STAGE -> stageProgress(stage).solutionDir
        null -> null
    }

    fun isDone(stage: Stage): Boolean {
        val progress = stageProgress(stage)
        return when (track.kind.completion) {
            Completion.TESTS_PASSED -> progress.passedAt != null
            Completion.QUIZ_COMPLETED -> progress.quiz.completedAt != null
        }
    }

    fun selectStage(selector: String?): Stage =
        if (selector != null) track.stageBySelector(selector) else currentStage ?: track.stages.last()

    fun withProgress(latest: Progress): TrackState = TrackState(project, track, latest)
}

class TrackStates(
    private val project: Project,
    private val library: ContentLibrary,
    private val progress: ProgressRepository,
) {
    fun load(trackId: String): TrackState = of(library.track(trackId), progress.load())

    fun of(track: Track, progress: Progress): TrackState = TrackState(project, track, progress)
}
