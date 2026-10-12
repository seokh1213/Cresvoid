package cresvoid.runner

import cresvoid.stageIn
import cresvoid.withTempDir
import io.kotest.assertions.withClue
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe
import io.kotest.matchers.string.shouldContain
import java.nio.file.Files
import java.nio.file.Path

class StarterDiffTest : FunSpec({
    test("바뀐 줄은 hunk 안에서만 세고 --- 로 시작하는 내용 줄도 센다") {
        val diff = """
            diff -ruN -x build starter/Main.kt solution/Main.kt
            --- starter/Main.kt
            +++ solution/Main.kt
            @@ -1,5 +1,5 @@
             fun main() {
            -    val x = 1
            ---x
            +    val x = 2
            +---x
                 println(x)
             }
            diff -ruN -x build starter/New.kt solution/New.kt
            --- starter/New.kt
            +++ solution/New.kt
            @@ -0,0 +1,2 @@
            +a
            +b
            \ No newline at end of file
        """.trimIndent() + "\n"
        withClue("바뀐 줄") { countChangedLines(diff) shouldBe ChangedLines(added = 4, removed = 2) }
    }
    test("줄 수를 생략한 hunk 와 빈 차이도 센다") {
        withClue("한 줄 hunk") { countChangedLines("@@ -3 +3 @@\n-old\n+new\n") shouldBe ChangedLines(added = 1, removed = 1) }
        withClue("차이 없음") { countChangedLines("") shouldBe ChangedLines(added = 0, removed = 0) }
    }
    test("시작 코드와 hidden 의 참조 풀이는 build 디렉터리와 .DS_Store 를 빼고 비교한다") {
        withTempDir { dir ->
            val stage = stageIn(dir.resolve("01-fix"))
            write(stage.starterDir, "src/main/kotlin/Total.kt", "fun total(values: List<Int>) = values.drop(1).sum()\n")
            write(stage.solutionDir, "src/main/kotlin/Total.kt", "fun total(values: List<Int>) = values.sum()\n")
            write(stage.starterDir, "build/app.jar", "starter jar\n")
            write(stage.solutionDir, "build/app.jar", "solution jar\n")
            write(stage.starterDir, ".DS_Store", "finder\n")
            val shown = starterDiff(stage, SystemProcessRunner())
            withClue("차이") { shown shouldContain "-fun total(values: List<Int>) = values.drop(1).sum()\n+fun total(values: List<Int>) = values.sum()\n" }
            withClue("빼고 비교한 파일:\n$shown") { ("Only in" !in shown && "jar" !in shown) shouldBe true }
            withClue("바뀐 줄") { countChangedLines(shown) shouldBe ChangedLines(added = 1, removed = 1) }
            write(stage.solutionDir, "src/main/kotlin/Report.kt", "fun report() = Unit\n")
            val withNewFile = starterDiff(stage, SystemProcessRunner())
            withClue("참조 풀이에만 있는 파일") { withNewFile shouldContain "+++ hidden/solution/src/main/kotlin/Report.kt" }
            withClue("참조 풀이에만 있는 파일의 내용") { withNewFile shouldContain "+fun report() = Unit\n" }
            withClue("참조 풀이에만 있는 파일까지 센 줄") { countChangedLines(withNewFile) shouldBe ChangedLines(added = 2, removed = 1) }
        }
    }
})

private fun write(projectDir: Path, relative: String, content: String) {
    val file = projectDir.resolve(relative)
    Files.createDirectories(file.parent)
    Files.writeString(file, content)
}
