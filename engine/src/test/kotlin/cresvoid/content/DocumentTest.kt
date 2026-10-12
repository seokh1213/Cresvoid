package cresvoid.content

import io.kotest.assertions.withClue
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe
import kotlin.io.path.Path

class DocumentTest : FunSpec({
    test("문단 번호로 문단을 나누고 코드 펜스 안과 겹친 번호를 가린다") {
        val text = "# 제목\n\n[1] 첫 문단\n이어지는 줄\n\n[2.1] 둘째 문단\n```\n[9] 코드 안\n```\n[2.2] 셋째\n# 다음 절\n본문 밖\n[1] 중복\n"
        val document = parseDocument(Path("spec.md"), text)
        withClue("1 문단") { document.paragraphs["1"] shouldBe "첫 문단\n이어지는 줄" }
        withClue("코드 펜스 안의 번호는 문단이 아니다") { ("9" !in document.paragraphs) shouldBe true }
        withClue("2.2 문단") { document.paragraphs["2.2"] shouldBe "셋째" }
        withClue("겹친 번호") { document.duplicates shouldBe listOf("1") }
    }
    test("CRLF 줄 끝과 물결 펜스도 처리한다") {
        val document = parseDocument(Path("spec.md"), "[1] 첫째\r\n~~~\r\n[2] 펜스 안\r\n~~~\r\n[3]\t탭 뒤 문단\r\n")
        withClue("문단 번호") { document.paragraphs.keys.toList() shouldBe listOf("1", "3") }
        withClue("3 문단") { document.paragraphs["3"] shouldBe "탭 뒤 문단" }
    }
    test("quote 는 백틱과 굵게 표시와 공백 차이를 무시하고 refs 구간에서만 찾는다") {
        val document = parseDocument(Path("spec.md"), "[3] 결과가 없으면 `(none)` 을\n**출력한다**.\n\n[4] 다른 문단이다.\n")
        val refs = listOfNotNull(document.paragraphs["3"])
        withClue("정규화 뒤 일치해야 한다") { containsQuote(refs, "결과가 없으면 (none) 을 출력한다.") shouldBe true }
        withClue("refs 밖 문단은 보지 않는다") { !containsQuote(refs, "다른 문단이다.") shouldBe true }
    }
    test("백틱과 별표는 원문 위치 기준으로 한 번에 지운다") {
        withClue("별표 사이 백틱") { normalizeForMatch("*`*") shouldBe "**" }
        withClue("별표 셋") { normalizeForMatch("***") shouldBe "*" }
    }
    test("공백 정규화는 유니코드 공백을 한 칸으로 줄인다") {
        collapseSpaces("${Char(0x3000)}a ${Char(0x85)} b\t") shouldBe "a b"
        collapseSpaces("  ") shouldBe ""
    }
})
