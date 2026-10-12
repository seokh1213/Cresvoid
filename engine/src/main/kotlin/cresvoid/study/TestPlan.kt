package cresvoid.study

import cresvoid.content.Stage
import cresvoid.content.TestScope
import cresvoid.content.Track

fun Track.stagesToRun(target: Stage): List<Stage> = when (kind.testScope) {
    TestScope.CUMULATIVE -> stages.take(target.number)
    TestScope.TARGET_STAGE -> listOf(target)
    null -> emptyList()
}

sealed interface TestGate {
    data object Open : TestGate

    data class QuizPending(val stage: Stage) : TestGate
}

fun TrackState.testGate(target: Stage): TestGate {
    for (stage in track.stagesToRun(target)) {
        if (track.kind.quizRules != null && stageProgress(stage).quiz.completedAt == null) return TestGate.QuizPending(stage)
    }
    return TestGate.Open
}
