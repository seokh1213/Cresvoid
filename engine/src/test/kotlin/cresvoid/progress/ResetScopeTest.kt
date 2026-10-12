package cresvoid.progress

import cresvoid.CresvoidError
import cresvoid.withTempDir
import io.kotest.assertions.throwables.shouldThrow
import io.kotest.assertions.withClue
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe
import java.nio.file.Files
import java.time.OffsetDateTime
import java.time.ZoneOffset

private val recordedAt: OffsetDateTime = OffsetDateTime.of(2026, 10, 8, 9, 0, 0, 0, ZoneOffset.ofHours(9))

class ResetScopeTest : FunSpec({
    test("초기화 범위마다 지울 기록과 남길 기록을 나눈다") {
        val progress = Progress(
            mapOf(
                "mini" to TrackProgress(
                    solutionDir = "practice/mini",
                    stages = mapOf("01" to StageProgress(openedAt = recordedAt), "02" to StageProgress(passedAt = recordedAt)),
                ),
                "story" to TrackProgress(stages = mapOf("01" to StageProgress(openedAt = recordedAt))),
            ),
        )
        withClue("모든 트랙") { progress.without(ResetScope.AllTracks) shouldBe Progress() }
        withClue("트랙 하나") { progress.without(ResetScope.WholeTrack("mini")).tracks.keys shouldBe setOf("story") }
        val withoutStage = progress.without(ResetScope.OneStage("mini", "01"))
        withClue("단계 하나") { withoutStage.tracks["mini"]?.stages?.keys shouldBe setOf("02") }
        withClue("트랙 작업 공간 선택은 남는다") { withoutStage.tracks["mini"]?.solutionDir shouldBe "practice/mini" }
        withClue("빈 트랙 기록은 지운다") { progress.without(ResetScope.OneStage("story", "01")).tracks.keys shouldBe setOf("mini") }
        withClue("없는 단계") { progress.without(ResetScope.OneStage("story", "09")) shouldBe progress }
        val emptyTrack = Progress(mapOf("story" to TrackProgress()))
        withClue("기록이 없는 단계는 트랙 기록도 건드리지 않는다") { emptyTrack.without(ResetScope.OneStage("story", "01")) shouldBe emptyTrack }
        val selected = progress.selectedBy(ResetScope.OneStage("mini", "02"))
        withClue("고른 단계") { selected shouldBe Progress(mapOf("mini" to TrackProgress(stages = mapOf("02" to StageProgress(passedAt = recordedAt))))) }
        withClue("기록이 없는 트랙") { progress.selectedBy(ResetScope.WholeTrack("nope")) shouldBe Progress() }
    }
    test("지우기 전 기록을 그대로 사본으로 남기고 사본 이름은 겹치지 않는다") {
        withTempDir { root ->
            val store = repositoryIn(root)
            val progressFile = root.resolve("progress/progress.json")
            store.update { Progress(mapOf("t" to TrackProgress(solutionDir = "first"))) }
            val original = Files.readString(progressFile)
            val first = store.replaceKeepingBackup { Progress() } ?: throw AssertionError("사본이 없습니다")
            withClue("사본 내용") { Files.readString(first) shouldBe original }
            withClue("지운 뒤 기록") { Files.readString(progressFile) shouldBe "{\n  \"tracks\": {}\n}\n" }
            withClue("지울 것이 없으면 사본을 남기지 않는다") { (store.replaceKeepingBackup { Progress() }) shouldBe null }
            store.update { Progress(mapOf("t" to TrackProgress(solutionDir = "second"))) }
            val second = store.replaceKeepingBackup { Progress() }
            store.update { Progress(mapOf("t" to TrackProgress(solutionDir = "third"))) }
            val third = store.replaceKeepingBackup { Progress() }
            withClue("사본 이름") { setOf(first, second, third).size shouldBe 3 }
            withClue("사본 수") { (Files.list(root.resolve("progress/backups")).use { it.count() }.toInt()) shouldBe 3 }
        }
    }
    test("읽을 수 없는 진행 기록은 모두 지울 때만 원본을 사본으로 남기고 비운다") {
        withTempDir { root ->
            val store = repositoryIn(root)
            val progressFile = root.resolve("progress/progress.json")
            Files.createDirectories(progressFile.parent)
            Files.writeString(progressFile, "{\"tracks\": {\"mini\": ")
            withClue("읽은 결과") { store.read() shouldBe StoredProgress.Unreadable("JSON 이 중간에 끝났습니다") }
            withClue("트랙 범위 초기화") { shouldThrow<CresvoidError> { store.replaceKeepingBackup { it.without(ResetScope.WholeTrack("mini")) } } }
            val backup = store.clearKeepingBackup() ?: throw AssertionError("사본이 없습니다")
            withClue("사본은 읽을 수 없던 원본 그대로다") { Files.readString(backup) shouldBe "{\"tracks\": {\"mini\": " }
            withClue("비운 기록") { store.load() shouldBe Progress() }
            withClue("비운 기록은 다시 지우지 않는다") { store.clearKeepingBackup() shouldBe null }
        }
    }})
