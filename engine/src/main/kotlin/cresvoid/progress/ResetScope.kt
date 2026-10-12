package cresvoid.progress

sealed interface ResetScope {
    val trackId: String?

    data object AllTracks : ResetScope {
        override val trackId: String? = null
    }

    data class WholeTrack(override val trackId: String) : ResetScope

    data class OneStage(override val trackId: String, val stageDir: String) : ResetScope
}

fun Progress.without(scope: ResetScope): Progress = when (scope) {
    ResetScope.AllTracks -> Progress()
    is ResetScope.WholeTrack -> copy(tracks = tracks - scope.trackId)
    is ResetScope.OneStage -> {
        val track = tracks[scope.trackId]?.takeIf { scope.stageDir in it.stages }
        val remaining = track?.copy(stages = track.stages - scope.stageDir)
        when {
            remaining == null -> this
            remaining == TrackProgress() -> copy(tracks = tracks - scope.trackId)
            else -> copy(tracks = tracks + (scope.trackId to remaining))
        }
    }
}

fun Progress.selectedBy(scope: ResetScope): Progress = when (scope) {
    ResetScope.AllTracks -> this
    is ResetScope.WholeTrack -> Progress(tracks.filterKeys { it == scope.trackId })
    is ResetScope.OneStage -> {
        val stage = tracks[scope.trackId]?.stages?.get(scope.stageDir)
        if (stage == null) Progress() else Progress(mapOf(scope.trackId to TrackProgress(stages = mapOf(scope.stageDir to stage))))
    }
}

fun Progress.workspaceSelections(): List<String> =
    tracks.values.flatMap { track -> listOfNotNull(track.solutionDir) + track.stages.values.mapNotNull { it.solutionDir } }
