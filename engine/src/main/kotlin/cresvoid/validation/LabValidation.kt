package cresvoid.validation

import cresvoid.CresvoidError
import cresvoid.content.LabSuite
import cresvoid.content.Stage
import cresvoid.content.Track
import cresvoid.content.containsQuote
import cresvoid.content.loadLabSuite
import cresvoid.project.copyTree
import cresvoid.project.isExecutableFile
import cresvoid.runner.Harness
import cresvoid.runner.LabHarness
import cresvoid.runner.LabOutcome
import cresvoid.runner.ProcessRunner
import cresvoid.terminal.Console
import java.io.IOException
import java.nio.file.Files
import java.nio.file.Path
import kotlin.io.path.exists
import kotlin.io.path.isDirectory
import kotlin.io.path.isRegularFile

class LabValidation(private val track: Track, runner: ProcessRunner, private val console: Console) {
    private val build = Harness(runner)
    private val experiments = LabHarness(runner)
    private val problems = mutableListOf<String>()
    private val suites = mutableMapOf<Stage, LabSuite>()

    val hasContentProblems: Boolean get() = problems.isNotEmpty()

    fun checkContent(contents: List<StageContent>): List<String> {
        for (content in contents) {
            val stage = content.stage
            if (!stage.experimentPath.isRegularFile()) problems += "${stage.dir}: hidden/experiment.main.kts 파일이 없습니다"
            if (stage.testsPath.exists()) problems += "${stage.dir}: lab 트랙에는 tests.json 대신 lab.json 을 씁니다"
            val suite = try {
                loadLabSuite(stage)
            } catch (error: CresvoidError) {
                problems += error.message
                continue
            } catch (error: IOException) {
                problems += "${stage.dir}: lab.json 을 읽지 못했습니다: ${error.message}"
                continue
            }
            suites[stage] = suite
            val document = content.document ?: continue
            for (scenario in suite.scenarios) {
                val where = "${stage.dir}/hidden/lab.json ${scenario.name}"
                if (scenario.refs.isEmpty()) problems += "$where: refs 가 비어 있습니다"
                val passages = scenario.refs.mapNotNull { ref ->
                    document.paragraphs[ref] ?: run {
                        problems += "$where: 문단 [$ref] 이 문서에 없습니다"
                        null
                    }
                }
                if (scenario.quote.isBlank()) {
                    problems += "$where: quote 가 비어 있습니다"
                } else if (!containsQuote(passages, scenario.quote)) {
                    problems += "$where: quote 가 refs 문단에 없습니다"
                }
            }
            for (variant in suite.mustFail) {
                val variantDir = stage.hiddenDir.resolve(variant)
                if (!variantDir.isDirectory()) problems += "${stage.dir}: must_fail 디렉터리가 없습니다: $variant"
            }
        }
        return problems.toList()
    }

    fun checkPrograms(): List<String> {
        for (stage in track.stages) {
            val suite = suites[stage] ?: continue
            checkProgram(stage, stage.solutionDir, "참조 풀이", suite, shouldPass = true)
            checkCopiedProgram(stage, stage.starterDir, "시작 코드", suite)
            for (variant in suite.mustFail) {
                checkCopiedProgram(stage, stage.hiddenDir.resolve(variant), variant, suite)
            }
        }
        return problems.toList()
    }

    private fun checkCopiedProgram(stage: Stage, source: Path, label: String, suite: LabSuite) {
        val workspace = try {
            Files.createTempDirectory("cresvoid-lab-")
        } catch (error: IOException) {
            problems += "${stage.dir} $label 작업 공간을 만들지 못했습니다: ${error.message}"
            return
        }
        try {
            copyTree(source, workspace)
            checkProgram(stage, workspace, label, suite, shouldPass = false)
        } catch (error: IOException) {
            problems += "${stage.dir} $label 복사에 실패했습니다: ${error.message}"
        } finally {
            workspace.toFile().deleteRecursively()
        }
    }

    private fun checkProgram(stage: Stage, dir: Path, label: String, suite: LabSuite, shouldPass: Boolean) {
        if (!dir.resolve("run.sh").isExecutableFile() || !dir.resolve("build.sh").isExecutableFile()) {
            problems += "${stage.dir} $label: 실행 가능한 run.sh 와 build.sh 가 필요합니다"
            return
        }
        build.buildFailure(dir)?.let {
            problems += "${stage.dir} $label 빌드 실패: $it"
            return
        }
        console.println("· ${stage.dir} $label 실험 3회 검증")
        repeat(3) { round ->
            var failedInvariant = false
            for (scenario in suite.scenarios) {
                repeat(scenario.repeat) {
                    val result = experiments.run(stage, dir, scenario)
                    when (val outcome = result.outcome) {
                        LabOutcome.Passed -> Unit
                        LabOutcome.InvariantFailed -> failedInvariant = true
                        is LabOutcome.Invalid -> {
                            problems += "${stage.dir} $label ${round + 1}회차 ${scenario.name}: ${outcome.reason}"
                            return
                        }
                    }
                    if (shouldPass && result.outcome != LabOutcome.Passed) {
                        problems += "${stage.dir} $label ${round + 1}회차 ${scenario.name}: 불변식 실패"
                        return
                    }
                }
            }
            if (!shouldPass && !failedInvariant) {
                problems += "${stage.dir} $label ${round + 1}회차: 모든 실험을 통과합니다 (실패해야 정상)"
            }
        }
    }
}
