package cresvoid.progress

import cresvoid.CresvoidError
import cresvoid.withTempDir
import io.kotest.assertions.throwables.shouldThrow
import io.kotest.assertions.withClue
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe
import io.kotest.matchers.string.shouldContain
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.attribute.PosixFilePermissions
import java.time.Clock
import java.time.OffsetDateTime
import java.time.ZoneOffset
import java.util.concurrent.CountDownLatch
import kotlin.concurrent.thread

private val seoul: ZoneOffset = ZoneOffset.ofHours(9)

class ProgressTest : FunSpec({
    test("복습은 처음 통과하고 14일이 지나야 하고 한 번만 한다") {
        val passed = OffsetDateTime.of(2026, 10, 1, 9, 0, 0, 0, ZoneOffset.UTC)
        val stage = StageProgress(passedAt = passed)
        withClue("14일 전에는 아니다") { !stage.isReviewDue(passed.plus(REVIEW_INTERVAL).minusMinutes(1)) shouldBe true }
        withClue("14일이 되면 복습할 차례다") { stage.isReviewDue(passed.plus(REVIEW_INTERVAL)) shouldBe true }
        val reviewed = stage.copy(reviewedAt = passed.plus(REVIEW_INTERVAL))
        withClue("복습한 단계는 다시 하지 않는다") { !reviewed.isReviewDue(passed.plus(REVIEW_INTERVAL).plusHours(1)) shouldBe true }
    }
    test("읽기를 다시 열면 한 시간 안에는 시작 시각을 유지한다") {
        val opened = OffsetDateTime.of(2026, 10, 8, 9, 0, 0, 0, seoul)
        val first = StageProgress().markedRead(opened)
        val within = first.markedRead(opened.plus(READING_SESSION_GAP))
        withClue("한 시간 안") { within.openedAt shouldBe opened }
        withClue("마지막으로 읽은 시각") { within.lastReadAt shouldBe opened.plus(READING_SESSION_GAP) }
        val later = within.markedRead(opened.plus(READING_SESSION_GAP).plus(READING_SESSION_GAP).plusSeconds(1))
        withClue("한 시간이 지나면 다시 잰다") { later.openedAt shouldBe later.lastReadAt }
    }
    test("여러 스레드가 동시에 고쳐도 기록을 잃지 않는다") {
        withTempDir { root ->
            val store = repositoryIn(root)
            val writers = 20
            val start = CountDownLatch(1)
            val threads = List(writers) {
                thread {
                    start.await()
                    store.update { progress ->
                        progress.updateStage("track", "01") { stage ->
                            val count = stage.quiz.wrong["q1"] ?: 0
                            stage.copy(quiz = stage.quiz.copy(wrong = stage.quiz.wrong + ("q1" to count + 1)))
                        }
                    }
                }
            }
            start.countDown()
            threads.forEach { it.join() }
            withClue("q1 오답 수") { store.load().stageOrEmpty("track", "01").quiz.wrong["q1"] shouldBe writers }
        }
    }
    test("진행 기록은 정해진 필드 순서의 두 칸 들여쓰기 JSON 으로 쓴다") {
        withTempDir { root ->
            val store = repositoryIn(root)
            val opened = OffsetDateTime.of(2026, 10, 7, 17, 21, 29, 277_307_000, seoul)
            store.update {
                Progress(
                    mapOf(
                        "reading" to TrackProgress(
                            stages = mapOf(
                                "01-auction" to StageProgress(
                                    openedAt = opened,
                                    quiz = QuizProgress(wrong = mapOf("q2" to 1, "q1" to 1), firstCorrect = 0, firstTotal = 5, firstSeconds = 18),
                                ),
                            ),
                        ),
                        "mini" to TrackProgress(solutionDir = "practice/mini"),
                    ),
                )
            }
            val expected = """
                {
                  "tracks": {
                    "mini": {
                      "solution_dir": "practice/mini"
                    },
                    "reading": {
                      "stages": {
                        "01-auction": {
                          "opened_at": "2026-10-07T17:21:29.277307+09:00",
                          "quiz": {
                            "wrong": {
                              "q1": 1,
                              "q2": 1
                            },
                            "first_correct": 0,
                            "first_total": 5,
                            "first_seconds": 18
                          }
                        }
                      }
                    }
                  }
                }
            """.trimIndent() + "\n"
            withClue("progress.json") { Files.readString(root.resolve("progress/progress.json")) shouldBe expected }
            withClue("권한") { PosixFilePermissions.toString(Files.getPosixFilePermissions(root.resolve("progress/progress.json"))) shouldBe "rw-r--r--" }
        }
    }
    test("진행 기록을 읽을 때 모르는 필드는 무시하고 틀린 타입은 거부한다") {
        withTempDir { root ->
            Files.createDirectories(root.resolve("progress"))
            Files.writeString(
                root.resolve("progress/progress.json"),
                """{"tracks": {"t": {"future": 1, "stages": {"01": {"quiz": {"first_correct": 2}, "opened_at": "2026-10-08T09:00:00Z", "note": null}}}}, "Extra": true}""",
            )
            val stage = repositoryIn(root).load().stageOrEmpty("t", "01")
            withClue("first_correct") { stage.quiz.firstCorrect shouldBe 2 }
            withClue("opened_at") { stage.openedAt shouldBe OffsetDateTime.of(2026, 10, 8, 9, 0, 0, 0, ZoneOffset.UTC) }
            Files.writeString(root.resolve("progress/progress.json"), """{"tracks": {"t": {"stages": {"01": {"quiz": {"first_total": "x"}}}}}}""")
            val error = withClue("틀린 타입") { shouldThrow<CresvoidError> { repositoryIn(root).load() } }
            withClue("오류 문구") { error.message shouldContain "진행 기록을 읽지 못했습니다:" }
            withClue("오류 위치") { error.message shouldContain "tracks.t.stages.01.quiz.first_total" }
            withClue("복구 안내") { error.message shouldContain "사본을 남기고 모든 기록을 지우려면: cresvoid reset" }
        }
    }
    test("예전 기록의 test_failures 는 모르는 필드로 무시하고 다시 쓸 때 남기지 않는다") {
        withTempDir { root ->
            val progressFile = root.resolve("progress/progress.json")
            Files.createDirectories(progressFile.parent)
            Files.writeString(
                progressFile,
                """{"tracks": {"mini": {"stages": {"01": {"test_failures": {"basic": {"count": 2, "signature": "a"}}, "passed_at": "2026-10-08T09:00:00Z"}}}}}""",
            )
            val store = repositoryIn(root)
            val passedAt = OffsetDateTime.of(2026, 10, 8, 9, 0, 0, 0, ZoneOffset.UTC)
            withClue("다른 기록") { store.load().stageOrEmpty("mini", "01").passedAt shouldBe passedAt }
            store.update { progress -> progress.updateStage("mini", "01") { it.copy(reviewedAt = passedAt) } }
            withClue("다시 쓴 기록") { ("test_failures" in Files.readString(progressFile)) shouldBe false }
        }
    }
    test("단계 작업 공간은 단계 기록의 solution_dir 로 쓰고 읽는다") {
        withTempDir { root ->
            val store = repositoryIn(root)
            store.update { progress -> progress.updateStage("patch", "01-total") { it.copy(solutionDir = "practice/patch/01-total") } }
            val text = Files.readString(root.resolve("progress/progress.json"))
            withClue("progress.json") { text shouldContain "\"01-total\": {\n          \"solution_dir\": \"practice/patch/01-total\",\n          \"quiz\": {}\n        }" }
            withClue("다시 읽은 작업 공간") { store.load().stageOrEmpty("patch", "01-total").solutionDir shouldBe "practice/patch/01-total" }
        }
    }
    test("고치지 않은 기록은 다시 쓰지 않는다") {
        withTempDir { root ->
            val store = repositoryIn(root)
            store.update { it }
            withClue("빈 기록은 파일을 만들지 않는다") { !Files.exists(root.resolve("progress/progress.json")) shouldBe true }
            withClue("잠금 파일은 만든다") { Files.exists(root.resolve("progress/progress.lock")) shouldBe true }
        }
    }
    test("시각은 RFC 3339 로 쓰고 소수점 아래 끝의 0 을 지운다") {
        withTempDir { root ->
            val store = repositoryIn(root)
            val time = OffsetDateTime.of(2026, 1, 2, 3, 4, 5, 120_000_000, ZoneOffset.UTC)
            store.update {
                Progress(mapOf("t" to TrackProgress(stages = mapOf("01" to StageProgress(passedAt = time, lastReadAt = time.withNano(0))))))
            }
            val text = Files.readString(root.resolve("progress/progress.json"))
            withClue("progress.json") { text shouldContain "\"last_read_at\": \"2026-01-02T03:04:05Z\"" }
            withClue("progress.json") { text shouldContain "\"passed_at\": \"2026-01-02T03:04:05.12Z\"" }
            withClue("다시 읽은 시각") { store.load().stageOrEmpty("t", "01").passedAt shouldBe time }
        }
    }
})

fun repositoryIn(root: Path): FileProgressRepository = FileProgressRepository(root.resolve("progress"), Clock.systemDefaultZone())
