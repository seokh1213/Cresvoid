package cresvoid.terminal

import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe
import java.time.Duration

class TextTest : FunSpec({
    test("한글은 두 칸으로 세어 열을 맞춘다") {
        displayWidth("ab한글cd가") shouldBe 10
        padRight("색인과 불리언 검색", 23) shouldBe "색인과 불리언 검색     "
        truncateDisplay("가나다라", 6) shouldBe "가나…"
        truncateDisplay("가나다라", 8) shouldBe "가나다라"
    }
    test("초 단위 표시는 정확한 값을 짝수 쪽으로 반올림한다") {
        formatElapsed(Duration.ofMillis(2500)) shouldBe "2.5초"
        formatElapsed(Duration.ofMillis(250)) shouldBe "0.2초"
        formatElapsed(Duration.ofMillis(350)) shouldBe "0.4초"
        formatElapsed(Duration.ofSeconds(10)) shouldBe "10.0초"
        formatSeconds(65) shouldBe "1분 5초"
        formatSeconds(59) shouldBe "59초"
    }
    test("따옴표로 감싸고 보이지 않는 문자를 드러낸다") {
        quoted("a\"b\\c\n\t") shouldBe "\"a\\\"b\\\\c\\n\\t\""
        quoted("한글 ㉠") shouldBe "\"한글 ㉠\""
        quoted("${Char(0xA0)}${Char(1)}${Char(0xFEFF)}") shouldBe "\"\\" + "u00a0\\" + "u0001\\" + "ufeff\""
    }
    test("들여쓰기는 끝 줄바꿈을 지우고 줄마다 붙인다") {
        indent("a\nb\n", "  ") shouldBe "  a\n  b"
        formatRefs(listOf("1", "2.3")) shouldBe "[1] [2.3]"
    }
})
