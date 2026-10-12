package cresvoid.command

import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe

class RereadingPlacesTest : FunSpec({
    test("문항 힌트는 문단 번호와 코드 위치를 함께 보여 준다") {
        rereadingPlaces("doc.md", listOf("1", "2.3")) shouldBe "doc.md 의 [1] [2.3] 문단"
        rereadingPlaces("doc.md", listOf("Cart.kt:12-30", "Order.kt:5")) shouldBe "Cart.kt 12~30행, Order.kt 5행"
        rereadingPlaces("doc.md", listOf("Cart.kt:3", "2")) shouldBe "doc.md 의 [2] 문단과 Cart.kt 3행"
    }
})
