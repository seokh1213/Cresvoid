package cresvoid.terminal

import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe

class WrapDisplayTest : FunSpec({
    test("한글은 두 칸으로 세어 넘치지 않게 자른다") {
        wrapDisplay("가나다라마바", 7) shouldBe listOf("가나다", "라마바")
    }

    test("가능하면 띄어쓰기에서 줄을 바꾼다") {
        wrapDisplay("alpha beta gamma", 11) shouldBe listOf("alpha beta", "gamma")
    }

    test("원래 줄바꿈을 지킨다") {
        wrapDisplay("하나\n둘", 10) shouldBe listOf("하나", "둘")
    }

    test("모든 줄이 너비 안에 든다") {
        val text = "복제본을 서로 다른 세 랙에 하나씩 두면 읽을 때 여러 랙의 대역폭을 쓸 수 있다. averyveryverylongtoken"
        wrapDisplay(text, 13).forEach { (displayWidth(it) <= 13) shouldBe true }
    }
})
