package cresvoid.command

import cresvoid.InMemoryProgressRepository
import cresvoid.RecordingConsole
import cresvoid.content.ContentLibrary
import cresvoid.progress.Progress
import cresvoid.progress.QuizProgress
import cresvoid.progress.StageProgress
import cresvoid.progress.TrackProgress
import cresvoid.project.Project
import cresvoid.study.TrackStates
import cresvoid.withTempDir
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.string.shouldContain
import java.nio.file.Files
import java.nio.file.Path
import java.time.Clock
import java.time.Instant
import java.time.OffsetDateTime
import java.time.ZoneOffset

private val clock: Clock = Clock.fixed(Instant.parse("2026-10-08T01:00:00Z"), ZoneOffset.ofHours(9))

private fun writeReadingTrack(root: Path) {
    val track = root.resolve("content/tracks/story")
    Files.createDirectories(track.resolve("stages/01-auction"))
    Files.writeString(
        track.resolve("track.json"),
        """{"title": "경매", "kind": "reading", "summary": "지문", "stages": [{"dir": "01-auction", "title": "경매 이론", "target_minutes": 12}]}""",
    )
    Files.writeString(track.resolve("stages/01-auction/passage.md"), "[1] 지문\n")
}

class StatusCommandTest : FunSpec({
    test("상태 화면은 기록과 현재 시각으로 표시와 다음 할 일을 정한다") {
        withTempDir { root ->
            writeReadingTrack(root)
            val project = Project(root)
            val library = ContentLibrary(project)
            val opened = OffsetDateTime.now(clock).minusMinutes(30)
            val repository = InMemoryProgressRepository(
                Progress(
                    mapOf(
                        "story" to TrackProgress(
                            stages = mapOf(
                                "01-auction" to StageProgress(
                                    openedAt = opened,
                                    lastReadAt = opened,
                                    quiz = QuizProgress(revealed = listOf("q1"), firstCorrect = 3, firstTotal = 5, firstSeconds = 600),
                                ),
                            ),
                        ),
                    ),
                ),
            )
            val console = RecordingConsole()
            val states = TrackStates(project, library, repository)
            StatusCommand(library, states, repository, console, clock).run(emptyList())
            console.text shouldContain "story 경매 (독해)"
            console.text shouldContain "01-auction  경매 이론  읽기 ✓  문항 · 3/5 공개 1 10분 0초/목표 12분\n"
            console.text shouldContain "다음: 01-auction 확인 문항 풀기. cresvoid quiz story"
        }
    }
})
