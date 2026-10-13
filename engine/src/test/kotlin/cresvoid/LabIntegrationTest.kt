package cresvoid

import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe

class LabIntegrationTest : FunSpec({
    test("lab 트랙은 문항 뒤 실험 지표를 보여 주고, 실패한 시작 코드와 변형을 3회 검증한다") {
        Sandbox(engineJar).use { sandbox ->
            addLabTrack(sandbox)
            sandbox.cresvoid("start", "mini-lab").expectExit(0, "시작")
                .expectOutput("practice/mini-lab/01-observe", "단계 작업 공간")
            sandbox.cresvoid("test", "mini-lab").expectExit(1, "문항 관문")
                .expectOutput("확인 문항을 먼저 마치세요", "문항 필요")
            sandbox.cresvoid("read", "mini-lab").expectExit(0, "문서")
            sandbox.cresvoid("quiz", "mini-lab", input = "1\nready\n").expectExit(0, "문항")
            sandbox.cresvoid("test", "mini-lab").expectExit(1, "실패하는 시작 코드")
                .expectOutput("requests 3.0", "지표 값")
                .expectOutput("불변식을 만족하지 못했습니다", "실험 실패")
                .expectOutput("핵심 문장: 응답은 ready 여야 한다.", "문서 참조")
                .expectNoOutput("hidden-secret", "숨은 스크립트 내용")
            sandbox.file("practice/mini-lab/01-observe/run.sh", "#!/bin/sh\necho ready\n", executable = true)
            sandbox.cresvoid("test", "mini-lab").expectExit(0, "실험 통과")
                .expectOutput("requests 3.0", "지표 값")
            (sandbox.stage("mini-lab", "01-observe").passedAt != null) shouldBe true
            sandbox.cresvoid("solution", "mini-lab").expectExit(0, "참조 풀이")
                .expectOutput("Main.kt", "소스 표시")
            sandbox.cresvoid("validate", "mini-lab").expectExit(0, "3회 검증")
                .expectOutput("참조 풀이 실험 3회 검증", "참조 풀이 반복")
                .expectOutput("시작 코드 실험 3회 검증", "시작 코드 반복")
                .expectOutput("variants/bad 실험 3회 검증", "변형 반복")
        }
    }

    test("lab 실험의 원시 출력은 실패 보고에 드러나지 않는다") {
        Sandbox(engineJar).use { sandbox ->
            addLabTrack(sandbox)
            sandbox.cresvoid("start", "mini-lab").expectExit(0, "시작")
            sandbox.cresvoid("quiz", "mini-lab", input = "1\nready\n").expectExit(0, "문항")
            sandbox.file("content/tracks/mini-lab/stages/01-observe/hidden/experiment.main.kts", "println(\"hidden-secret\")\nkotlin.system.exitProcess(1)\n")
            sandbox.cresvoid("test", "mini-lab").expectExit(1, "잘못된 출력")
                .expectOutput("지표 출력 형식이 잘못되었습니다", "프로토콜 오류")
                .expectNoOutput("hidden-secret", "실험 원시 출력")
        }
    }

    test("lab 검증은 통과하는 must_fail 변형을 거절한다") {
        Sandbox(engineJar).use { sandbox ->
            addLabTrack(sandbox)
            sandbox.file("content/tracks/mini-lab/stages/01-observe/hidden/variants/bad/run.sh", "#!/bin/sh\necho ready\n", executable = true)
            sandbox.cresvoid("validate", "mini-lab").expectExit(1, "변형이 통과하면 실패")
                .expectOutput("variants/bad 1회차: 모든 실험을 통과합니다", "변형 검증")
        }
    }
})

private fun addLabTrack(sandbox: Sandbox) {
    val stage = "content/tracks/mini-lab/stages/01-observe"
    sandbox.file(
        "content/tracks/mini-lab/track.json",
        """{"title":"미니 실험","kind":"lab","summary":"실험한다.","stages":[{"dir":"01-observe","title":"관찰"}]}""",
    )
    sandbox.file("$stage/spec.md", "[1] 응답은 ready 여야 한다.\n")
    sandbox.file(
        "$stage/hidden/quiz.json",
        """{"questions":[{"id":"q1","type":"choice","prompt":"응답은?","choices":["ready","bad"],"answer":1,"refs":["1"],"quote":"응답은 ready 여야 한다.","explanation":"ready다."},{"id":"q2","type":"short","prompt":"입력 뒤 응답은?","input":["PING"],"answers":["ready"],"refs":["1"],"quote":"응답은 ready 여야 한다.","explanation":"ready다."}]}""",
    )
    sandbox.file(
        "$stage/hidden/lab.json",
        """{"scenarios":[{"name":"response","refs":["1"],"quote":"응답은 ready 여야 한다.","timeout_sec":30}],"must_fail":["variants/bad"]}""",
    )
    sandbox.file(
        "$stage/hidden/experiment.main.kts",
        """
        val process = ProcessBuilder(args[0] + "/run.sh").start()
        val output = process.inputStream.bufferedReader().readText().trim()
        process.waitFor()
        println("METRIC requests 3")
        if (output != "ready") kotlin.system.exitProcess(1)
        """.trimIndent(),
    )
    for ((program, output) in listOf("starter" to "bad", "hidden/solution" to "ready", "hidden/variants/bad" to "bad")) {
        sandbox.file("$stage/$program/run.sh", "#!/bin/sh\necho $output\n", executable = true)
        sandbox.file("$stage/$program/build.sh", "#!/bin/sh\nexit 0\n", executable = true)
        sandbox.file("$stage/$program/src/main/kotlin/Main.kt", "fun main() = println(\"$output\")\n")
    }
}
