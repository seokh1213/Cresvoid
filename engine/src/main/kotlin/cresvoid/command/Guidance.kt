package cresvoid.command

import cresvoid.content.Stage
import cresvoid.content.Track
import cresvoid.content.WorkspaceScope
import cresvoid.study.NextStep
import cresvoid.study.TrackState
import cresvoid.study.nextStep
import java.time.OffsetDateTime

internal fun startCommand(track: Track, stage: Stage?): String = when (track.kind.workspaceScope) {
    WorkspaceScope.STAGE -> "cresvoid start ${track.id} -stage ${checkNotNull(stage).number}"
    else -> "cresvoid start ${track.id}"
}

internal fun NextStep.describe(track: Track): String = when (this) {
    is NextStep.CreateWorkspace ->
        if (stage == null) "작업 공간 만들기. ${startCommand(track, null)}" else "${stage.dir} 작업 공간 만들기. ${startCommand(track, stage)}"
    is NextStep.Read -> "${stage.dir} 문서 읽기. cresvoid read ${track.id}"
    is NextStep.SolveQuiz -> "${stage.dir} 확인 문항 풀기. cresvoid quiz ${track.id}"
    is NextStep.PassTests -> "${stage.dir} 테스트 통과하기. cresvoid test ${track.id}"
    is NextStep.Review ->
        if (inNewWorkspace) "${stage.dir} 복습. ${startCommand(track, stage)} -dir <새 경로>" else "${stage.dir} 복습. cresvoid test ${track.id} -stage ${stage.number}"
    NextStep.Finished -> "모든 단계를 마쳤습니다."
}

internal fun TrackState.nextLine(now: OffsetDateTime): String = "다음: " + nextStep(now).describe(track)

internal fun TrackState.stageFlag(stage: Stage): String {
    val current = currentStage
    val isDefault = current == stage || (current == null && stage.number == track.stages.size)
    return if (isDefault) "" else " -stage ${stage.number}"
}
