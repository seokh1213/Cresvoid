package cresvoid.json

import io.kotest.assertions.throwables.shouldThrow
import io.kotest.assertions.withClue
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe

class JsonTest : FunSpec({
    test("문법 오류는 위치와 함께 알린다") {
        val error = withClue("쉼표 뒤 닫는 괄호") { shouldThrow<JsonError> { parseJson("{\n  \"a\": [1,]\n}", KeyRule.ANY) } }
        withClue("오류 문구") { error.message shouldBe "예상하지 못한 문자입니다: 2번째 줄 11번째 글자 ']'" }
        withClue("끝나지 않은 문자열") { shouldThrow<JsonError> { parseJson("\"abc", KeyRule.ANY) } }
        withClue("앞자리 0") { shouldThrow<JsonError> { parseJson("[01]", KeyRule.ANY) } }
        withClue("최상위 01 은 0 뒤에 내용이 있는 것이다") { parseJson("01", KeyRule.ANY).hasTrailingContent shouldBe true }
        withClue("제어 문자") { shouldThrow<JsonError> { parseJson("\"a\u0001\"", KeyRule.ANY) } }
    }
    test("문자열 이스케이프와 서로게이트 쌍을 읽는다") {
        val parsed = parseJson(""""é\n😀\ud800x"""", KeyRule.ANY).value
        withClue("문자열") { parsed shouldBe JsonString("é\n😀�x") }
    }
    test("진행 기록 출력은 두 칸 들여쓰기 JSON 이고 제어 문자만 이스케이프한다") {
        val value = JsonObject(
            linkedMapOf(
                "tracks" to JsonObject(
                    linkedMapOf(
                        "a" to JsonObject(emptyMap()),
                        "b" to JsonArray(listOf(JsonString("<x & y>\u0001"), JsonNumber("3"))),
                    ),
                ),
            ),
        )
        val expected = "{\n  \"tracks\": {\n    \"a\": {},\n    \"b\": [\n      \"<x & y>\\u0001\",\n      3\n    ]\n  }\n}"
        withClue("출력") { value.toIndentedJson() shouldBe expected }
    }})
