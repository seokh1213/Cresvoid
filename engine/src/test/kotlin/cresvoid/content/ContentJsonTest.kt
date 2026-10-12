package cresvoid.content

import cresvoid.CresvoidError
import cresvoid.project.Project
import cresvoid.stageIn
import cresvoid.withTempDir
import io.kotest.assertions.throwables.shouldThrow
import io.kotest.assertions.withClue
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe
import io.kotest.matchers.string.shouldContain
import java.nio.file.Files

class ContentJsonTest : FunSpec({
    test("겹친 키, 대문자 키, 값 뒤 내용을 거부한다") {
        withTempDir { dir ->
            val rejected = mapOf(
                "duplicate" to """{"tests": [{"name": "a", "name": "b"}]}""",
                "case" to """{"Tests": []}""",
                "trailing" to """{"tests": []} {"tests": []}""",
                "garbage" to """{"tests": []} x""",
            )
            for ((label, content) in rejected) {
                val stage = stageIn(dir.resolve(label), "hidden/tests.json" to content)
                withClue(label) { shouldThrow<CresvoidError> { loadTestSuite(stage) } }
            }
            val valid = stageIn(
                dir.resolve("valid"),
                "hidden/tests.json" to """{"tests": [{"name": "a", "refs": ["1"], "quote": "q", "input": ["x"], "expected": []}]}""" + "\n",
            )
            withClue("테스트 수") { loadTestSuite(valid).tests.size shouldBe 1 }
        }
    }
    test("키 오류와 겹친 키는 키 이름과 함께 알린다") {
        withTempDir { dir ->
            val duplicate = stageIn(dir.resolve("dup"), "hidden/tests.json" to """{"tests": [{"name": "a", "name": "b"}]}""")
            val error = withClue("겹친 키") { shouldThrow<CresvoidError> { loadTestSuite(duplicate) } }
            withClue("오류 문구") { error.message shouldContain "JSON 을 읽지 못했습니다: ${duplicate.testsPath}: 키가 겹칩니다: \"name\"" }
            val upper = stageIn(dir.resolve("upper"), "hidden/tests.json" to """{"Tests": []}""")
            val upperError = withClue("대문자 키") { shouldThrow<CresvoidError> { loadTestSuite(upper) } }
            withClue("오류 문구") { upperError.message shouldContain "키는 영문 소문자 snake_case 여야 합니다: \"Tests\"" }
            val trailing = stageIn(dir.resolve("trailing"), "hidden/tests.json" to """{"tests": []} []""")
            val trailingError = withClue("뒤 내용") { shouldThrow<CresvoidError> { loadTestSuite(trailing) } }
            withClue("오류 문구") { trailingError.message shouldBe "JSON 값 뒤에 다른 내용이 있습니다: ${trailing.testsPath}" }
        }
    }
    test("모르는 필드, 틀린 타입, 빠진 필수 필드, null 을 거부한다") {
        withTempDir { dir ->
            val cases = mapOf(
                "unknown" to ("""{"tests": [{"name": "a", "refs": [], "quote": "q", "hint": "x"}]}""" to "모르는 필드입니다: tests[0].hint"),
                "type" to ("""{"tests": [{"name": "a", "refs": [], "quote": "q", "repeat": "3"}]}""" to "정수가 아닙니다: tests[0].repeat"),
                "fraction" to ("""{"tests": [{"name": "a", "refs": [], "quote": "q", "repeat": 1.5}]}""" to "정수가 아닙니다: tests[0].repeat"),
                "missing" to ("""{"tests": [{"name": "a", "refs": []}]}""" to "필수 필드가 없습니다: tests[0].quote"),
                "null" to ("""{"tests": [{"name": null, "refs": [], "quote": "q"}]}""" to "문자열이 아닙니다: tests[0].name"),
                "element" to ("""{"tests": [{"name": "a", "refs": [1], "quote": "q"}]}""" to "문자열이 아닙니다: tests[0].refs[0]"),
                "top" to ("""[]""" to "객체가 아닙니다: 최상위 값"),
            )
            for ((label, case) in cases) {
                val (content, message) = case
                val stage = stageIn(dir.resolve(label), "hidden/tests.json" to content)
                val error = withClue(label) { shouldThrow<CresvoidError> { loadTestSuite(stage) } }
                withClue(label) { error.message shouldContain message }
            }
        }
    }
    test("빈 배열과 없는 필드를 구분한다") {
        withTempDir { dir ->
            val stage = stageIn(
                dir,
                "hidden/tests.json" to """{"tests": [{"name": "a", "refs": [], "quote": "q", "expected": []}, {"name": "b", "refs": [], "quote": "q"}]}""",
            )
            val tests = loadTestSuite(stage).tests
            withClue("빈 expected") { tests[0].expected shouldBe emptyList<String>() }
            withClue("없는 expected") { tests[1].expected shouldBe null }
        }
    }
    test("track.json 에 남은 design_memo 는 모르는 필드로 거절한다") {
        withTempDir { root ->
            val definition = root.resolve("content/tracks/t/track.json")
            Files.createDirectories(definition.parent)
            Files.writeString(
                definition,
                """{"title": "트랙", "kind": "build", "summary": "요약", "stages": [{"dir": "01-a", "title": "단계", "design_memo": true}]}""",
            )
            val error = shouldThrow<CresvoidError> { ContentLibrary(Project(root)).track("t") }
            withClue("오류 문구") { error.message shouldContain "모르는 필드입니다: stages[0].design_memo" }
        }
    }
    test("진행 기록 디렉터리와 겹치는 트랙 id 는 거절한다") {
        withTempDir { root ->
            val definition = root.resolve("content/tracks/progress/track.json")
            Files.createDirectories(definition.parent)
            Files.writeString(
                definition,
                """{"title": "트랙", "kind": "build", "summary": "요약", "stages": [{"dir": "01-a", "title": "단계"}]}""",
            )
            val error = shouldThrow<CresvoidError> { ContentLibrary(Project(root)).track("progress") }
            withClue("오류 문구") { error.message shouldBe "content/tracks/progress: 트랙 id 로 progress 는 쓸 수 없습니다. practice/progress 는 진행 기록 디렉터리입니다" }
        }
    }
})
