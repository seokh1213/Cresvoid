import java.io.File
import kotlin.system.exitProcess

val requiredKeys = listOf("id", "title", "kind", "track", "stage", "status", "wave")
val optionalKeys = listOf("env", "prereq")
val kinds = listOf("reading", "build", "code", "fix", "lab")
val kindLetters = mapOf("reading" to "R", "build" to "B", "code" to "C", "fix" to "F", "lab" to "L")
val envTools = listOf("docker", "k6", "redis", "maven", "gradle", "spark", "llm-api", "kotlinx-coroutines")
val areas = listOf("reading", "implementation", "backend")
val statusGroups = listOf("후보", "제작 중", "완료", "보류")
val madeGroups = setOf("제작 중", "완료")
val waves = 1..5
val noEnv = "없음"

val idPattern = Regex("""([RBCFL])-([a-z0-9]+(?:-[a-z0-9]+)*)-([0-9]{2,3})""")
val slugPattern = Regex("""[a-z0-9]+(-[a-z0-9]+)*""")
val numberedStagePattern = Regex("""([0-9]{2})-([a-z0-9]+(-[a-z0-9]+)*)""")
val statusPattern = Regex("""후보|제작 중|완료\([0-9]{4}-[0-9]{2}-[0-9]{2}\)|보류\(.+\)""")
val trackKindPattern = Regex(""""kind"\s*:\s*"([a-z]+)"""")
val trackDirPattern = Regex(""""dir"\s*:\s*"([^"]+)"""")
val headingPattern = Regex("""#{2,6} (?:[0-9]+(?:\.[0-9]+)*\.? )?(.+)""")
val numberedSectionPattern = Regex("""[0-9]+(?:\.[0-9]+)*절""")
val inlineCodePattern = Regex("""`[^`]*`""")
// [문서](경로.md) 의 '제목' 절, <이름> README 의 '제목' 절, '제목' 절(같은 문서)
val sectionRefPattern = Regex("""(?:\[[^\]]*\]\(([^)\s]+\.md)\)|(이 도메인|이 영역|[a-z0-9-]+) README)?\s*(?:의\s+)?'([^'\n]+)' 절""")

fun splitList(value: String?): List<String> =
    value.orEmpty().split(',').map { it.trim() }.filter { it.isNotEmpty() }

fun stageNumber(stage: String): String? = numberedStagePattern.matchEntire(stage)?.groupValues?.get(1)

fun stageSlug(stage: String): String = numberedStagePattern.matchEntire(stage)?.groupValues?.get(2) ?: stage

class Card(val file: File, val path: String, val domainPath: String, val fields: Map<String, String>) {
    val id = fields["id"].orEmpty()
    private val idParts = idPattern.matchEntire(id)?.groupValues
    val idKind = idParts?.get(1).orEmpty()
    val idTopic = idParts?.get(2).orEmpty()
    val idOrder = idParts?.get(3)?.toInt() ?: 0
    val title = fields["title"].orEmpty()
    val kind = fields["kind"].orEmpty()
    val track = fields["track"].orEmpty()
    val stages = splitList(fields["stage"])
    val status = fields["status"].orEmpty()
    val statusGroup = status.substringBefore('(')
    val made = statusGroup in madeGroups
    val wave = fields["wave"]?.toIntOrNull()
    val env = splitList(fields["env"]).filter { it != noEnv }
    val prereq = splitList(fields["prereq"])
}

fun readFrontMatter(lines: List<String>, path: String, problems: MutableList<String>): Map<String, String>? {
    if (lines.firstOrNull() != "---") {
        problems += "$path: 첫 줄은 --- 이어야 한다"
        return null
    }
    val closing = lines.drop(1).indexOf("---")
    if (closing < 0) {
        problems += "$path: front matter 를 닫는 --- 가 없다"
        return null
    }
    val fields = linkedMapOf<String, String>()
    lines.subList(1, closing + 1).forEach { line ->
        val key = line.substringBefore(':', missingDelimiterValue = "").trim()
        when {
            key.isEmpty() -> problems += "$path: '$line' 은 key: value 꼴이 아니다"
            key in fields -> problems += "$path: $key 가 두 번 나온다"
            else -> fields[key] = line.substringAfter(':').trim()
        }
    }
    return fields
}

fun cardDomainOf(path: String): String? {
    val parts = path.split('/')
    return when {
        parts.size == 3 && parts[1] == "cards" -> parts[0]
        parts.size == 4 && parts[2] == "cards" -> "${parts[0]}/${parts[1]}"
        else -> null
    }
}

fun readCard(file: File, catalogDir: File, problems: MutableList<String>): Card? {
    val path = file.relativeTo(catalogDir).invariantSeparatorsPath
    val domainPath = cardDomainOf(path)
    if (domainPath == null || path.substringBefore('/') !in areas) {
        problems += "$path: 카드는 <영역>/cards/ 나 <영역>/<도메인>/cards/ 아래에 둔다. 영역은 ${areas.joinToString()} 이다"
        return null
    }
    val fields = readFrontMatter(file.readLines(), path, problems) ?: return null
    return Card(file, path, domainPath, fields)
}

fun expectedTopicOf(card: Card): String? = when {
    card.domainPath.startsWith("backend/") -> card.domainPath.removePrefix("backend/")
    card.domainPath == "implementation" && card.kind == "build" -> card.track
    card.domainPath == "implementation" -> "kotlin"
    else -> null
}

fun problemsOf(card: Card): List<String> = buildList {
    val where = card.path
    (card.fields.keys - (requiredKeys + optionalKeys).toSet()).forEach { add("$where: 모르는 필드 $it") }
    requiredKeys.filter { card.fields[it].isNullOrBlank() }.forEach { add("$where: $it 가 비어 있다") }
    if (!idPattern.matches(card.id)) add("$where: id '${card.id}' 의 꼴이 틀렸다. 종류 글자(R, B, C, F, L), 소문자 영문 주제, 두 자리 이상 순서를 - 로 잇는다. 예: B-coroutines-20")
    if (card.idKind.isNotEmpty() && kindLetters[card.kind]?.let { it != card.idKind } == true) add("$where: kind ${card.kind} 카드의 id 는 ${kindLetters[card.kind]} 로 시작한다")
    expectedTopicOf(card)?.takeIf { card.idKind.isNotEmpty() && it != card.idTopic }?.let { add("$where: id 의 주제가 틀렸다. 맞는 주제: $it") }
    val slug = card.file.name.removePrefix("${card.id}-").removeSuffix(".md")
    if (!card.file.name.startsWith("${card.id}-") || !slugPattern.matches(slug)) add("$where: 파일 이름은 ${card.id}-<영문 slug>.md 다")
    if (card.kind !in kinds) add("$where: kind '${card.kind}' 는 ${kinds.joinToString()} 가운데 하나다")
    if (!slugPattern.matches(card.track)) add("$where: track '${card.track}' 는 영문 소문자 kebab-case 다")
    card.stages.forEach { stage ->
        val numbered = stageNumber(stage) != null
        when {
            card.kind == "build" || card.made ->
                if (!numbered) add("$where: 단계 '$stage' 는 두 자리 번호-영문 slug 꼴이다")
            numbered || !slugPattern.matches(stage) ->
                add("$where: 만들기 전 카드의 단계 '$stage' 는 번호 없이 영문 slug 로 쓴다. 번호는 제작 중으로 바꿀 때 붙인다")
        }
    }
    if (card.stages.size > 1 && card.kind != "build") add("$where: 단계를 여럿 적을 수 있는 종류는 build 뿐이다")
    if (!statusPattern.matches(card.status)) add("$where: status '${card.status}' 는 후보, 제작 중, 완료(YYYY-MM-DD), 보류(이유) 가운데 하나다")
    if (card.wave == null || card.wave !in waves) add("$where: wave 는 ${waves.first}~${waves.last} 사이 정수다")
    card.env.filterNot { it in envTools }.forEach { add("$where: env '$it' 는 $noEnv 이거나 ${envTools.joinToString()} 가운데 하나다") }
}

fun trackFileOf(track: String, projectDir: File): File? =
    File(projectDir, "content/tracks/$track/track.json").takeIf { it.isFile }

fun contentKindOf(track: String, projectDir: File): String? =
    trackFileOf(track, projectDir)?.let { trackKindPattern.find(it.readText())?.groupValues?.get(1) }

fun contentDirsOf(track: String, projectDir: File): List<String> =
    trackFileOf(track, projectDir)?.let { file -> trackDirPattern.findAll(file.readText()).map { it.groupValues[1] }.toList() }.orEmpty()

fun crossProblems(cards: List<Card>, projectDir: File): List<String> = buildList {
    cards.groupBy { it.id }.filterValues { it.size > 1 }.forEach { (id, same) ->
        add("id $id 가 겹친다: ${same.joinToString { it.path }}")
    }
    cards.filter { it.domainPath.startsWith("backend/") && it.idKind.isNotEmpty() }
        .groupBy { it.idTopic to it.idOrder }
        .values
        .filter { it.size > 1 }
        .forEach { same -> add("주제 ${same.first().idTopic} 에서 여러 카드가 같은 순서 번호를 쓴다: ${same.joinToString { it.id }}") }
    cards.flatMap { card -> card.stages.map { stage -> "${card.track}/${stageSlug(stage)}" to card.id } }
        .groupBy({ it.first }, { it.second })
        .filterValues { it.size > 1 }
        .forEach { (stage, ids) -> add("단계 $stage 를 여러 카드가 쓴다: ${ids.joinToString()}") }
    cards.groupBy { it.track }.forEach { (track, trackCards) ->
        val declaredKinds = trackCards.map { it.kind }.distinct()
        if (declaredKinds.size > 1) add("트랙 $track 에 종류가 다른 카드가 섞여 있다: ${declaredKinds.joinToString()}")
        val contentKind = contentKindOf(track, projectDir)
        if (contentKind != null && contentKind !in declaredKinds) add("트랙 $track 카드의 종류가 track.json 의 $contentKind 와 다르다")
        val contentDirs = contentDirsOf(track, projectDir)
        (contentDirs.map { it to "track.json" } + trackCards.flatMap { card -> card.stages.map { it to card.id } })
            .filter { stageNumber(it.first) != null }
            .distinct()
            .groupBy { stageNumber(it.first)!! }
            .filterValues { same -> same.map { stageSlug(it.first) }.distinct().size > 1 }
            .forEach { (number, same) -> add("트랙 $track 의 번호 $number 를 여러 단계가 쓴다: ${same.joinToString { "${it.first}(${it.second})" }}") }
        trackCards.filter { it.statusGroup == "완료" }.forEach { card ->
            card.stages.filterNot { it in contentDirs }.forEach { add("${card.path}: 완료한 단계 $it 가 content/tracks/$track/track.json 에 없다") }
        }
    }
    val ids = cards.map { it.id }.toSet()
    cards.forEach { card ->
        card.prereq.filterNot { it in ids }.forEach { add("${card.path}: 선행 카드 $it 가 없다") }
    }
}

fun sectionTitlesOf(file: File): List<String> {
    var fenced = false
    return file.readLines().mapNotNull { line ->
        if (line.startsWith("```")) fenced = !fenced
        if (fenced) null else headingPattern.matchEntire(line)?.groupValues?.get(1)?.trim()
    }
}

fun referencedFileOf(file: File, catalogDir: File, linkPath: String?, name: String?): File? {
    val path = file.relativeTo(catalogDir).invariantSeparatorsPath
    val target = when {
        linkPath != null -> File(file.parentFile, linkPath)
        name == null -> file
        name == "이 도메인" -> cardDomainOf(path)?.let { File(catalogDir, "$it/README.md") }
        name == "이 영역" -> path.substringBefore('/').takeIf { it in areas }?.let { File(catalogDir, "$it/README.md") }
        name == "ideas" -> File(catalogDir, "README.md")
        name in areas -> File(catalogDir, "$name/README.md")
        else -> File(catalogDir, "backend/$name/README.md")
    }
    return target?.canonicalFile?.takeIf { it.isFile }
}

fun referenceProblems(catalogDir: File): List<String> = buildList {
    val titlesByFile = mutableMapOf<File, List<String>>()
    catalogDir.walkTopDown()
        .filter { it.isFile && it.extension == "md" && it.name != "INDEX.md" }
        .sortedBy { it.path }
        .forEach { file ->
            val path = file.relativeTo(catalogDir).invariantSeparatorsPath
            var fenced = false
            file.readLines().forEachIndexed { index, line ->
                if (line.startsWith("```")) fenced = !fenced
                if (fenced) return@forEachIndexed
                val where = "$path:${index + 1}"
                val text = line.replace(inlineCodePattern, "")
                numberedSectionPattern.findAll(text).forEach { add("$where: '${it.value}' 처럼 번호로 가리키지 않고 '제목' 절 꼴로 가리킨다") }
                sectionRefPattern.findAll(text).forEach { match ->
                    val title = match.groupValues[3]
                    val target = referencedFileOf(file, catalogDir, match.groups[1]?.value, match.groups[2]?.value)
                    if (target == null) {
                        add("$where: '$title' 절이 있는 문서를 찾지 못했다")
                    } else {
                        val targetPath = target.relativeTo(catalogDir).invariantSeparatorsPath
                        val count = titlesByFile.getOrPut(target) { sectionTitlesOf(target) }.count { it == title }
                        if (count == 0) add("$where: $targetPath 에 '$title' 절이 없다")
                        if (count > 1) add("$where: $targetPath 에 '$title' 절이 여럿이다. 절 제목을 서로 다르게 고친다")
                    }
                }
            }
        }
}

fun domainTitleOf(catalogDir: File, domainPath: String): String? =
    File(catalogDir, "$domainPath/README.md").takeIf { it.isFile }
        ?.useLines { lines -> lines.firstOrNull { it.startsWith("# ") } }
        ?.removePrefix("# ")

val cardOrder = compareBy<Card>({ it.idTopic }, { it.idOrder }, { it.id })

fun cell(text: String): String = text.replace("|", "\\|")

fun envText(card: Card): String = if (card.env.isEmpty()) noEnv else card.env.joinToString(", ")

fun StringBuilder.appendSummary(domains: List<String>, cardsByDomain: Map<String, List<Card>>, titles: Map<String, String>) {
    appendLine("## 요약")
    appendLine()
    appendLine("| 도메인 | 카드 | ${statusGroups.joinToString(" | ")} |")
    appendLine("|---|---|${statusGroups.joinToString("") { "---|" }}")
    domains.forEach { domain ->
        val domainCards = cardsByDomain.getValue(domain)
        val counts = statusGroups.map { group -> domainCards.count { it.statusGroup == group } }
        appendLine("| [${cell(titles.getValue(domain))}]($domain/README.md) | ${domainCards.size} | ${counts.joinToString(" | ")} |")
    }
    val all = cardsByDomain.values.flatten()
    appendLine("| 합계 | ${all.size} | ${statusGroups.joinToString(" | ") { group -> all.count { it.statusGroup == group }.toString() }} |")
    appendLine()
}

fun StringBuilder.appendQueue(cards: List<Card>) {
    val queue = cards.filter { it.statusGroup == "후보" }
        .sortedWith(compareBy<Card>({ it.wave }, { it.env.isNotEmpty() }).then(cardOrder))
    appendLine("## 다음 후보")
    appendLine()
    appendLine("상태가 후보인 카드를 차수 순서로 늘어놓았다. 같은 차수에서는 추가 환경이 필요 없는 카드가 먼저다.")
    appendLine()
    appendLine("| 차수 | 카드 | 제목 | 종류 | 트랙 | 단계 | 환경 |")
    appendLine("|---|---|---|---|---|---|---|")
    queue.forEach { card ->
        appendLine("| ${card.wave} | [${card.id}](${card.path}) | ${cell(card.title)} | ${card.kind} | ${card.track} | ${card.stages.joinToString(", ")} | ${envText(card)} |")
    }
    appendLine()
}

fun StringBuilder.appendTracks(cards: List<Card>, projectDir: File) {
    appendLine("## 트랙")
    appendLine()
    appendLine("컨텐츠 칸은 `content/tracks/<트랙>/track.json` 이 있는지를 보여 준다. 다음 번호는 단계 하나짜리 카드를 제작 중으로 바꿀 때 붙일 번호다. 예정 단계는 차수 순서다.")
    appendLine()
    appendLine("| 트랙 | 종류 | 컨텐츠 | 다음 번호 | 만든 단계 | 예정 단계 |")
    appendLine("|---|---|---|---|---|---|")
    cards.groupBy { it.track }.toSortedMap().forEach { (track, trackCards) ->
        val contentDirs = contentDirsOf(track, projectDir)
        val lastNumber = (contentDirs + trackCards.flatMap { it.stages }).mapNotNull { stageNumber(it)?.toInt() }.maxOrNull() ?: 0
        val next = (lastNumber + 1).toString().padStart(2, '0')
        val made = trackCards.filter { it.made }
            .flatMap { card -> card.stages.map { it to card } }
            .sortedBy { it.first }
            .joinToString(", ") { (stage, card) -> "$stage(${card.id} ${card.statusGroup})" }
        val planned = trackCards.filterNot { it.made }
            .sortedWith(compareBy<Card> { it.wave }.then(cardOrder))
            .flatMap { card -> card.stages.map { it to card } }
            .joinToString(", ") { (stage, card) -> "$stage(${card.id} ${card.wave}차)" }
        val exists = if (trackFileOf(track, projectDir) != null) "있음" else "없음"
        appendLine("| $track | ${trackCards.first().kind} | $exists | $next | $made | $planned |")
    }
    appendLine()
}

fun StringBuilder.appendDomains(domains: List<String>, cardsByDomain: Map<String, List<Card>>, titles: Map<String, String>) {
    appendLine("## 도메인별 카드")
    appendLine()
    domains.forEach { domain ->
        appendLine("### ${titles.getValue(domain)}")
        appendLine()
        appendLine("도메인 안내: [$domain/README.md]($domain/README.md)")
        appendLine()
        appendLine("| 카드 | 제목 | 종류 | 트랙 | 단계 | 상태 | 차수 | 환경 | 선행 |")
        appendLine("|---|---|---|---|---|---|---|---|---|")
        cardsByDomain.getValue(domain).sortedWith(cardOrder).forEach { card ->
            appendLine("| [${card.id}](${card.path}) | ${cell(card.title)} | ${card.kind} | ${card.track} | ${card.stages.joinToString(", ")} | ${cell(card.status)} | ${card.wave} | ${envText(card)} | ${card.prereq.joinToString(", ")} |")
        }
        appendLine()
    }
}

fun renderIndex(cards: List<Card>, titles: Map<String, String>, projectDir: File): String {
    val cardsByDomain = cards.groupBy { it.domainPath }
    val domains = cardsByDomain.keys.sortedWith(compareBy({ areas.indexOf(it.substringBefore('/')) }, { it }))
    return buildString {
        appendLine("# 카드 색인")
        appendLine()
        appendLine("`kotlin content/ideas/index.main.kts` 가 카드 front matter 로 만든 파일이다. 손으로 고치지 않는다. 카드 형식과 차수의 뜻은 [README.md](README.md) 에 있다.")
        appendLine()
        appendSummary(domains, cardsByDomain, titles)
        appendQueue(cards)
        appendTracks(cards, projectDir)
        appendDomains(domains, cardsByDomain, titles)
    }
}

// kotlin 스크립트 캐시를 쓰면 __FILE__ 이 처음 컴파일한 경로로 남는다
val scriptFile: File = ProcessHandle.current().info().arguments().orElse(emptyArray())
    .lastOrNull { it.endsWith(".main.kts") }
    ?.let { File(it).absoluteFile }
    ?: __FILE__.absoluteFile
val checkOnly = "--check" in args
val catalogDir = (args.firstOrNull { !it.startsWith("--") }?.let(::File) ?: scriptFile.parentFile).canonicalFile
val projectDir = catalogDir.parentFile.parentFile
if (!catalogDir.isDirectory) {
    System.err.println("카탈로그 디렉터리가 없다: $catalogDir")
    exitProcess(2)
}

val problems = mutableListOf<String>()
val cardFiles = catalogDir.walkTopDown().filter { it.isFile && it.extension == "md" && it.parentFile.name == "cards" }.toList()
val readCards = cardFiles.mapNotNull { readCard(it, catalogDir, problems) }
readCards.forEach { problems += problemsOf(it) }
problems += crossProblems(readCards, projectDir)
problems += referenceProblems(catalogDir)
val titles = readCards.map { it.domainPath }.distinct().associateWith { domainTitleOf(catalogDir, it) }
titles.filterValues { it == null }.keys.forEach { problems += "$it/README.md 에 # 제목이 없다" }

val indexFile = File(catalogDir, "INDEX.md")
val rendered = if (problems.isEmpty()) renderIndex(readCards, titles.mapValues { it.value.orEmpty() }, projectDir) else null
if (rendered != null && checkOnly && (!indexFile.isFile || indexFile.readText() != rendered)) {
    problems += "INDEX.md 가 카드와 맞지 않는다. kotlin content/ideas/index.main.kts 로 다시 만든다"
}

if (problems.isNotEmpty()) {
    problems.forEach { System.err.println(it) }
    System.err.println("문제 ${problems.size}건")
    exitProcess(1)
}
if (!checkOnly) indexFile.writeText(rendered!!)
println("카드 ${readCards.size}장, 도메인 ${titles.size}개. ${if (checkOnly) "INDEX.md 가 최신이다" else "INDEX.md 를 만들었다"}")
