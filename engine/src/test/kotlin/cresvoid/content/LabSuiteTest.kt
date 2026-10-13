package cresvoid.content

import cresvoid.CresvoidError
import io.kotest.assertions.throwables.shouldThrow
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe
import java.nio.file.Files

class LabSuiteTest : FunSpec({
    test("lab.json 은 기본 반복·제한 시간과 strict 필드를 읽는다") {
        val root = Files.createTempDirectory("cresvoid-lab-suite-")
        try {
            val stage = Stage(1, "01-a", "실험", 0, root, root.resolve("spec.md"))
            Files.createDirectories(stage.hiddenDir)
            Files.writeString(stage.labPath, """{"scenarios":[{"name":"response","refs":["1"],"quote":"응답"}],"must_fail":["variants/bad"]}""")
            val suite = loadLabSuite(stage)
            suite.scenarios.single().timeoutSec shouldBe 60
            suite.scenarios.single().repeat shouldBe 1
            suite.mustFail shouldBe listOf("variants/bad")
            Files.writeString(stage.labPath, """{"scenarios":[],"must_fail":[],"extra":1}""")
            shouldThrow<CresvoidError> { loadLabSuite(stage) }.message.contains("모르는 필드") shouldBe true
            Files.writeString(stage.labPath, """{"scenarios":[{"name":"response","refs":[],"quote":"x","repeat":0}],"must_fail":[]}""")
            shouldThrow<CresvoidError> { loadLabSuite(stage) }.message.contains("repeat 는 양수") shouldBe true
            Files.writeString(stage.labPath, """{"scenarios":[{"name":"response","refs":[],"quote":"x"}],"must_fail":["variants/../escape"]}""")
            shouldThrow<CresvoidError> { loadLabSuite(stage) }.message.contains("variants/<slug>") shouldBe true
        } finally {
            root.toFile().deleteRecursively()
        }
    }
})
