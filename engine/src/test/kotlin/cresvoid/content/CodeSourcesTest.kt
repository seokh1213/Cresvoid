package cresvoid.content

import cresvoid.terminal.quoted
import cresvoid.withTempDir
import io.kotest.assertions.withClue
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe
import java.nio.file.Files

class CodeSourcesTest : FunSpec({
    test("코드 위치는 파일과 줄이나 줄 범위로 읽는다") {
        withClue("범위") { CodeLocation.parse("Cart.kt:12-30") shouldBe CodeLocation("Cart.kt", 12, 30) }
        withClue("한 줄") { CodeLocation.parse("shop/Cart.kt:7") shouldBe CodeLocation("shop/Cart.kt", 7, 7) }
        withClue("범위 표시") { CodeLocation.parse("Cart.kt:12-30")?.label shouldBe "Cart.kt 12~30행" }
        withClue("같은 줄 범위 표시") { CodeLocation.parse("Cart.kt:7-7")?.label shouldBe "Cart.kt 7행" }
        for (malformed in listOf("Cart.kt:x", "Cart.kt:", ":3", "Cart.kt:1-", "Cart.kt:99999999999")) {
            withClue(malformed) { CodeLocation.parse(malformed) shouldBe null }
        }
        withClue("문단 번호와 구분") { (isCodeLocation("Cart.kt:3") && !isCodeLocation("2.4.1")) shouldBe true }
    }
    test("소스는 파일마다 줄 번호를 붙이고 빈 줄은 번호만 쓴다") {
        withTempDir { dir ->
            val root = kotlinSourceRoot(dir)
            Files.createDirectories(root.resolve("shop"))
            Files.writeString(root.resolve("Main.kt"), "fun main() {\n\n    run()\n}\n")
            Files.writeString(root.resolve("shop/Cart.kt"), (1..10).joinToString("\n") { "line $it" })
            Files.writeString(root.resolve("notes.txt"), "kotlin 파일이 아니다\n")
            withClue("파일 목록") { kotlinSourceFiles(dir) shouldBe listOf("Main.kt", "shop/Cart.kt") }
            val expected = "==> Main.kt <==\n1  fun main() {\n2\n3      run()\n4  }\n\n==> shop/Cart.kt <==\n 1  line 1\n"
            withClue("줄 번호를 붙인 소스:\n${numberedSources(dir)}") { numberedSources(dir).startsWith(expected) shouldBe true }
            withClue("두 자리 줄 번호") { numberedSources(dir).endsWith(" 9  line 9\n10  line 10\n") shouldBe true }
            withClue("소스가 없는 프로젝트") { numberedSources(dir.resolve("missing")) shouldBe "" }
        }
    }
    test("코드 줄은 끝 줄바꿈을 세지 않고 CR 을 지운다") {
        withTempDir { dir ->
            val cases = mapOf("a\r\nb\r\n" to listOf("a", "b"), "" to emptyList(), "a\n\n" to listOf("a", ""), "a" to listOf("a"))
            cases.entries.forEachIndexed { index, (text, lines) ->
                val file = dir.resolve("$index.kt")
                Files.writeString(file, text)
                withClue("줄: ${quoted(text)}") { sourceLines(file) shouldBe lines }
            }
        }
    }
    test("코드 위치는 소스 디렉터리 안의 파일만 가리킨다") {
        withTempDir { dir ->
            val code = dir.resolve("code")
            Files.createDirectories(kotlinSourceRoot(code))
            Files.writeString(kotlinSourceRoot(code).resolve("Cart.kt"), "class Cart\n")
            Files.writeString(code.resolve("run.sh"), "#!/bin/sh\n")
            val sources = CodeSources(code)
            withClue("안의 파일") { sources.lines("Cart.kt") shouldBe listOf("class Cart") }
            withClue("밖의 파일") { sources.lines("../../../run.sh") shouldBe null }
            withClue("없는 파일") { sources.lines("Nope.kt") shouldBe null }
        }
    }
})
