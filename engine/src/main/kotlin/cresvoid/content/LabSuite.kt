package cresvoid.content

import cresvoid.CresvoidError
import java.time.Duration

private val slug = Regex("[a-z0-9]+(-[a-z0-9]+)*")

data class LabScenario(
    val name: String,
    val refs: List<String>,
    val quote: String,
    val timeoutSec: Int,
    val repeat: Int,
) {
    val timeout: Duration get() = Duration.ofSeconds(timeoutSec.toLong())
}

data class LabSuite(val scenarios: List<LabScenario>, val mustFail: List<String>)

fun loadLabSuite(stage: Stage): LabSuite {
    val suite = readContentJson(stage.labPath) { fields ->
        LabSuite(
            scenarios = fields.objectList("scenarios") { scenario ->
                LabScenario(
                    name = scenario.string("name"),
                    refs = scenario.stringList("refs"),
                    quote = scenario.string("quote"),
                    timeoutSec = scenario.optionalInt("timeout_sec") ?: 60,
                    repeat = scenario.optionalInt("repeat") ?: 1,
                )
            },
            mustFail = fields.stringList("must_fail"),
        )
    }
    fun reject(message: String): Nothing = throw CresvoidError("${stage.labPath}: $message")
    if (suite.scenarios.isEmpty()) reject("scenarios 가 비어 있습니다")
    val names = mutableSetOf<String>()
    for (scenario in suite.scenarios) {
        if (!slug.matches(scenario.name)) reject("시나리오 이름은 영문 kebab-case 여야 합니다: ${scenario.name}")
        if (!names.add(scenario.name)) reject("시나리오 이름이 겹칩니다: ${scenario.name}")
        if (scenario.timeoutSec <= 0) reject("${scenario.name}: timeout_sec 는 양수여야 합니다")
        if (scenario.repeat <= 0) reject("${scenario.name}: repeat 는 양수여야 합니다")
    }
    val variants = mutableSetOf<String>()
    for (variant in suite.mustFail) {
        if (!variant.startsWith("variants/") || !slug.matches(variant.removePrefix("variants/"))) {
            reject("must_fail 은 variants/<slug> 형식이어야 합니다: $variant")
        }
        if (!variants.add(variant)) reject("must_fail 이 겹칩니다: $variant")
    }
    return suite
}
