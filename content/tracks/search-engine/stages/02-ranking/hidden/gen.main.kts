import java.math.BigDecimal
import java.math.RoundingMode
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.ln
import kotlin.math.roundToInt
import kotlin.math.sqrt
import kotlin.random.Random

fun wordsOf(text: String): List<String> = text.trim().split(Regex("\\s+"))

val ENGLISH_WORDS = wordsOf(
    """
    the of and or not to in is for on with data search index query engine kotlin java flow coroutine
    stream guide apple pie red green cherry grape juice new york city pizza delhi jersey test code build
    run fast slow open close file text word token rank score list map set tree node graph cache disk
    memory server client user admin shop price brand product catalog item order cart pay ship http api
    json xml sql log error bug fix merge sort hash key value page link web app mobile cloud cpu gpu ram
    time date year day week coffee tea milk bread rice noodle music movie book game sport ball near far
    left right top bottom first last big small hot cold blue black white gold silver iron stone river
    lake sea mountain forest garden window door table chair phone camera laptop watch shoe bag shirt hat
    sale deal coupon review star
    """,
)
val KOREAN_WORDS = wordsOf(
    """
    검색 엔진 색인 문서 데이터 서버 사용자 상품 가격 브랜드 카탈로그 쇼핑 주문 배송 결제 장바구니 리뷰 평점
    할인 쿠폰 이벤트 카테고리 의류 신발 가방 노트북 휴대폰 커피 우유 라면 김치 서울 부산 제주 날씨 오늘
    내일 사과 포도 딸기 바나나 개발 코드 테스트 빌드 배포 장애 로그 오류 질의 순위 점수 토큰 구문 근접
    위치 거리 그리고 또는 아니다 새로운 도시 피자 맛집 여행 호텔 항공 기차 버스 지하철 학교 회사 회의
    보고서 일정 메일 전화 사진 영상 음악 영화 게임 운동 축구 야구 농구 수영 등산 캠핑 요리 빵집 카페
    시장 마트 편의점 약국 병원 은행
    """,
)
val ALNUM_WORDS = wordsOf("k8s v2 utf8 ipv6 mp3 x86 2024 404 3d b2b h264 5g top10 1st a1b2 r2d2 covid19 web3 i18n s3 7")
val PARTICLES = wordsOf("은 는 이 가 을 를 와 과 의 에 도 로 만 개 번 년 명 분")
val OPERATOR_TOKENS = setOf("and", "or", "not")
val ENGLISH_ONSETS = wordsOf("b c d f g h j k l m n p r s t v w z br cr dr fl gr pl pr sk st tr")
val ENGLISH_VOWELS = wordsOf("a e i o u ai ea io ou")
val ENGLISH_CODAS = listOf("", "", "", "n", "r", "s", "t", "x", "ng", "ck")
val KOREAN_SYLLABLES = "가나다라마바사아자차카타파하고노도로모보소오조초코토포호구누두루무부수우주추쿠투푸후기니디리미비시이지치키티피히강남동산성장정진한현민수영준혁"
val BODY_SEPARATORS = weightedChoice(
    " " to 60, "  " to 4, ", " to 6, ". " to 4, "-" to 4, "_" to 3, "/" to 2, "\t" to 2,
    " · " to 1, "!" to 1, "? " to 1, ": " to 1, "; " to 1, "~" to 1, "'" to 1, " (" to 1,
    ") " to 1, "é" to 1, "ü" to 1, "\u00a0" to 1, "\u3000" to 1, "ㅋ" to 1, "…" to 1, " & " to 1,
    "+" to 1, "=" to 1, "Ａ" to 1, "１" to 1, " → " to 1, "@" to 1, "#" to 1, "*" to 1,
    " | " to 1, "ß" to 1, "ㅏ" to 1, "¿" to 1, "🙂" to 1, "\"" to 1,
)
val WORD_JOINERS = weightedChoice(
    "-" to 8, "_" to 4, "." to 4, "/" to 3, "'" to 3, ":" to 2, "+" to 2, "·" to 2, "é" to 2,
    "ㅋ" to 2, "&" to 1, "@" to 1, "~" to 1, "Ａ" to 1, "\t" to 1,
)
val DECORATIONS = listOf(
    "!", "?", ".", ",", "'", "…", "~", "*", "#", "@", "_", ":", ";", "/", "-", "+", "=", "[", "]",
    "{", "}", "<", ">", "|", "&", "%", "$", "^", "é", "ㅋ", "ㅎㅎ", "Ａ", "１", "¿", "¡", "·", "°",
    "★", "\\", "`", "!!",
)
val SEARCH_DECORATIONS = DECORATIONS + listOf("(", ")", "\"", "((", "\"!")
val ZERO_TOKEN_WORDS = listOf(
    "!!!", "ㅋㅋ", "---", "é", "…", "¿?", "_", "Ａ１", "~~", "@#", "ㅏㅏ", "°", "€", "™", "—", "★",
    "·", "%%", "&", "=", "ＡＢＣ", "１２３", "ñ", "¡¡", "'", "ㅎ", "🙂",
)
val SIGN_ONLY_WORDS = listOf("+", "-", "+!!!", "-ㅋㅋ", "+-", "-+", "++")
val EMPTY_BODIES = listOf("", "", "!!! ㅋㅋ", "Ａ１", "---", "é", "\t", "…", "ㅏㅏ — ★", "\"\"")
val ZIPF_OFFSET = 2.7
val LARGE_DOCUMENTS = 1000
val LARGE_MEAN_LENGTH = 33
val NORMAL_MEAN_LENGTH = 7
val LARGE_ANCHOR_LIMIT = 80
val LARGE_REQUIRED_LIMIT = 400
val LARGE_OPTIONAL_LIMIT = 150
val OTHER_COMMAND_RATE = 0.38
val NEAR_TIE_RATE = 0.03
val MAX_LEXEMES = 100
val K1 = 1.2
val B = 0.75

data class Options(val documents: Int, val queries: Int, val seed: Long)

fun parseOptions(arguments: Array<String>): Options {
    val values = mutableMapOf("--docs" to "40", "--queries" to "60", "--seed" to "1")
    require(arguments.size % 2 == 0) { "옵션은 --이름 값 쌍으로 준다: ${arguments.joinToString(" ")}" }
    for ((name, value) in arguments.toList().chunked(2)) {
        require(name in values) { "모르는 옵션: $name" }
        values[name] = value
    }
    return Options(
        documents = values.getValue("--docs").toInt(),
        queries = values.getValue("--queries").toInt(),
        seed = values.getValue("--seed").toLong(),
    )
}

class WeightedSampler<T>(private val values: List<T>, weights: List<Double>) {
    private val cumulativeWeights = weights.runningReduce(Double::plus)

    fun sample(random: Random): T {
        val target = random.nextDouble() * cumulativeWeights.last()
        var low = 0
        var high = cumulativeWeights.lastIndex
        while (low < high) {
            val middle = (low + high) / 2
            if (cumulativeWeights[middle] > target) high = middle else low = middle + 1
        }
        return values[low]
    }
}

fun weightedChoice(vararg entries: Pair<String, Int>): WeightedSampler<String> =
    WeightedSampler(entries.map { it.first }, entries.map { it.second.toDouble() })

fun <T> zipfSampler(valuesByRank: List<T>): WeightedSampler<T> =
    WeightedSampler(valuesByRank, valuesByRank.indices.map { index -> 1.0 / (index + 1 + ZIPF_OFFSET) })

fun Random.nextGaussian(mean: Double, deviation: Double): Double {
    val radius = sqrt(-2.0 * ln(1.0 - nextDouble()))
    return mean + deviation * radius * cos(2.0 * PI * nextDouble())
}

fun isHangul(token: String): Boolean = token[0] in '가'..'힣'

fun bm25(idf: Double, termFrequency: Int, length: Int, averageLength: Double): Double =
    idf * termFrequency * (K1 + 1) / (termFrequency + K1 * (1 - B + B * length / averageLength))

fun roundedScore(score: Double): BigDecimal = BigDecimal(score).setScale(4, RoundingMode.HALF_UP)

class Vocabulary(private val random: Random, size: Int) {
    private val knownWords = mutableSetOf<String>()
    private val wordSampler: WeightedSampler<String>
    private val collocationSampler: WeightedSampler<List<String>>

    init {
        val words = (ENGLISH_WORDS + KOREAN_WORDS + ALNUM_WORDS).distinct().shuffled(random).take(size).toMutableList()
        knownWords += words
        while (words.size < size) {
            val word = syntheticWord()
            if (knownWords.add(word)) words += word
        }
        wordSampler = zipfSampler(words)
        val collocations = List(maxOf(3, size / 25)) { List(listOf(2, 2, 3).random(random)) { zipfWord() } }
        collocationSampler = zipfSampler(collocations)
    }

    fun zipfWord(): String = wordSampler.sample(random)

    fun collocation(): List<String> = collocationSampler.sample(random)

    fun unknownWord(): String {
        while (true) {
            val word = "qx${random.nextInt(10, 10_000)}"
            if (word !in knownWords) return word
        }
    }

    private fun syntheticWord(): String {
        val roll = random.nextDouble()
        return when {
            roll < 0.55 -> {
                val stem = List(random.nextInt(1, 4)) { ENGLISH_ONSETS.random(random) + ENGLISH_VOWELS.random(random) }
                stem.joinToString("") + ENGLISH_CODAS.random(random)
            }
            roll < 0.9 -> List(random.nextInt(2, 4)) { KOREAN_SYLLABLES.random(random) }.joinToString("")
            else -> "${('a'..'z').random(random)}${random.nextInt(0, 1000)}${listOf("", "x", "k", "v").random(random)}"
        }
    }
}

data class Occurrence(val document: List<String>, val position: Int)

class Corpus {
    private val documents = mutableMapOf<Int, List<String>>()
    private val slots = mutableMapOf<Int, Int>()
    private val documentFrequency = mutableMapOf<String, Int>()
    private val usedIds = mutableSetOf<Int>()
    private val _liveIds = mutableListOf<Int>()
    private val _deletedIds = mutableListOf<Int>()
    private val _retiredTokens = mutableListOf<String>()

    var totalLength = 0
        private set

    val documentCount: Int get() = documents.size
    val liveIds: List<Int> get() = _liveIds
    val deletedIds: List<Int> get() = _deletedIds
    val retiredTokens: List<String> get() = _retiredTokens

    fun isUsed(id: Int): Boolean = id in usedIds

    fun tokensOf(id: Int): List<String> = documents.getValue(id)

    fun add(id: Int, tokens: List<String>) {
        if (id in documents) remove(id)
        documents[id] = tokens
        slots[id] = _liveIds.size
        _liveIds += id
        usedIds += id
        totalLength += tokens.size
        for (token in tokens.distinct()) {
            documentFrequency[token] = documentFrequency.getOrDefault(token, 0) + 1
        }
    }

    fun delete(id: Int) {
        remove(id)
        _deletedIds += id
    }

    fun takeDeletedId(random: Random): Int = _deletedIds.removeAt(random.nextInt(_deletedIds.size))

    fun randomDocument(random: Random): List<String> =
        if (_liveIds.isEmpty()) emptyList() else documents.getValue(_liveIds.random(random))

    fun boundedToken(random: Random, frequencyLimit: Int): String? {
        repeat(8) {
            val tokens = randomDocument(random)
            if (tokens.isNotEmpty()) {
                val token = tokens.random(random)
                if (documentFrequency.getValue(token) <= frequencyLimit) return token
            }
        }
        val occurrence = rareOccurrence(random, frequencyLimit) ?: return null
        return occurrence.document[occurrence.position]
    }

    fun rareOccurrence(random: Random, frequencyLimit: Int?): Occurrence? {
        repeat(8) {
            val tokens = randomDocument(random)
            if (tokens.isNotEmpty()) {
                val position = tokens.indices.minBy { documentFrequency.getValue(tokens[it]) }
                if (frequencyLimit == null || documentFrequency.getValue(tokens[position]) <= frequencyLimit) {
                    return Occurrence(tokens, position)
                }
            }
        }
        return null
    }

    private fun remove(id: Int) {
        val tokens = checkNotNull(documents.remove(id))
        val slot = checkNotNull(slots.remove(id))
        val lastId = _liveIds.removeAt(_liveIds.lastIndex)
        if (lastId != id) {
            _liveIds[slot] = lastId
            slots[lastId] = slot
        }
        totalLength -= tokens.size
        for (token in tokens.distinct()) {
            val remaining = documentFrequency.getValue(token) - 1
            if (remaining > 0) documentFrequency[token] = remaining else documentFrequency.remove(token)
        }
        _retiredTokens += tokens.take(4)
        if (_retiredTokens.size > 40) _retiredTokens.subList(0, _retiredTokens.size - 40).clear()
    }
}

sealed class QueryTree(val precedence: Int) {
    class Term(val word: String) : QueryTree(4)
    class Not(val operand: QueryTree) : QueryTree(3)
    class And(val operands: List<QueryTree>) : QueryTree(2)
    class Or(val operands: List<QueryTree>) : QueryTree(1)
}

enum class LexemeKind { OPEN, CLOSE, OPERATOR, WORD, NOISE }

val PAREN_KINDS = setOf(LexemeKind.OPEN, LexemeKind.CLOSE)
val MERGEABLE_BEFORE_NOISE = setOf(LexemeKind.OPERATOR, LexemeKind.WORD)

data class Lexeme(val kind: LexemeKind, val text: String)

class Writer(
    private val random: Random,
    private val vocabulary: Vocabulary,
    private val corpus: Corpus,
    private val large: Boolean,
    private val meanLength: Int,
) {
    fun documentTokens(): List<String> {
        val length = documentLength()
        val tokens = mutableListOf<String>()
        while (tokens.size < length) {
            if (random.nextDouble() < 0.1) {
                tokens += vocabulary.collocation()
                continue
            }
            val zipfWord = vocabulary.zipfWord()
            val word = if (isHangul(zipfWord) && random.nextDouble() < 0.15) zipfWord + PARTICLES.random(random) else zipfWord
            tokens += word
            if (!isHangul(word) && random.nextDouble() < 0.06) tokens += PARTICLES.random(random)
        }
        return tokens.take(length)
    }

    fun body(tokens: List<String>): String {
        if (tokens.isEmpty()) return EMPTY_BODIES.random(random)
        val text = buildString {
            append(styled(tokens.first(), allowNear = true))
            for ((previous, current) in tokens.zipWithNext()) {
                append(bodySeparator(previous, current))
                append(styled(current, allowNear = true))
            }
        }
        return decorated(text, DECORATIONS, prefixRate = 0.1, suffixRate = 0.15)
    }

    fun queryWord(tokens: List<String>): String {
        val text = if (tokens.size == 1) queryToken(tokens.first()) else joined(tokens, allowNear = false)
        return decorated(text, DECORATIONS, prefixRate = 0.12, suffixRate = 0.12)
    }

    fun searchWord(prefix: String, tokens: List<String>): String {
        val text = decorated(joined(tokens, allowNear = true), SEARCH_DECORATIONS, prefixRate = 0.12, suffixRate = 0.12)
        if (prefix.isEmpty() && text[0] in "+-") return listOf("(", "'", "*").random(random) + text
        return prefix + text
    }

    fun searchLimit(): Int {
        val roll = random.nextDouble()
        if (large) {
            return when {
                roll < 0.85 -> random.nextInt(1, 11)
                roll < 0.98 -> random.nextInt(11, 101)
                else -> random.nextInt(101, 1001)
            }
        }
        return when {
            roll < 0.3 -> random.nextInt(1, 4)
            roll < 0.7 -> random.nextInt(4, 11)
            roll < 0.85 -> random.nextInt(11, 51)
            roll < 0.95 -> random.nextInt(51, 1000)
            else -> 1000
        }
    }

    fun searchWords(): List<String> {
        val roll = random.nextDouble()
        if (roll < 0.04) return emptyList()
        val words = when {
            roll < 0.16 -> edgeSearchWords()
            large -> largeSearchWords()
            else -> List(listOf(1, 1, 2, 2, 2, 3, 3, 4).random(random)) { searchWord(randomPrefix(), freeSearchTokens()) }
        }.toMutableList()
        if (random.nextDouble() < 0.12) {
            words.add(random.nextInt(0, words.size + 1), (ZERO_TOKEN_WORDS + SIGN_ONLY_WORDS).random(random))
        }
        return words
    }

    fun brokenSearch(): String {
        val word = searchWord("", listOf(vocabulary.zipfWord()))
        return listOf(
            "SEARCH", "SEARCH ", "SEARCH 0 $word", "SEARCH 01 $word", "SEARCH 1001 $word", "SEARCH -1 $word",
            "SEARCH +5 $word", "SEARCH $word", "SEARCH 10x $word", "SEARCH 1e3 $word", "SEARCH １０ $word",
            "SEARCH 9999 $word", "SEARCH 10000 $word", "SEARCH 5\t$word", "SEARCH 0", "SEARCH 00 $word",
            "SEARCH 1001", "SEARCH 3.5 $word", "SEARCH k $word", "SEARCH 2,3 $word", "SEARCH  0010  $word",
        ).random(random)
    }

    fun zeroTokenWord(): String = ZERO_TOKEN_WORDS.random(random)

    fun termTokens(): List<String> {
        val roll = random.nextDouble()
        val document = corpus.randomDocument(random)
        return when {
            roll < 0.5 || document.isEmpty() -> listOf(vocabulary.zipfWord())
            roll < 0.72 -> listOf(document.random(random))
            roll < 0.84 -> List(listOf(2, 2, 3).random(random)) { document.random(random) }
            roll < 0.9 && corpus.retiredTokens.isNotEmpty() -> listOf(corpus.retiredTokens.random(random))
            roll < 0.98 -> listOf(vocabulary.zipfWord(), vocabulary.zipfWord())
            else -> listOf(vocabulary.unknownWord())
        }
    }

    fun expression(): String {
        while (true) {
            val tree = if (large || random.nextDouble() < 0.3) anchoredTree(depth = 0) else freeTree(depth = 0)
            val lexemes = scatterNoise(buildList { emit(tree, this, requiredPrecedence = 1) })
            if (lexemes.size <= MAX_LEXEMES) return render(lexemes)
        }
    }

    fun brokenExpression(): String {
        val first = queryWord(termTokens())
        val second = queryWord(termTokens())
        val third = queryWord(termTokens())
        val zero = zeroTokenWord()
        return listOf(
            "", zero, "$zero $zero", "()", "( $zero )", "($first", "$first)", ")$first(",
            "$first OR OR $second", "$first AND", "AND $first", "NOT", "$first NOT", "$first OR",
            "OR", "$first AND $zero", "($first OR $second", "$first ($second OR $third))", "NOT ()",
            "$first AND OR $second", "$first ( )", "NOT NOT", "($first) ($second", "$first OR NOT",
            "$first $zero OR", "(($first)", "NOT $zero", "$first OR $zero", "$first AND AND $second",
            "( $first OR ) $second", "$first NOT AND $second",
        ).random(random)
    }

    private fun documentLength(): Int {
        val roll = random.nextDouble()
        return when {
            roll < 0.04 -> 0
            roll < 0.1 -> random.nextInt(1, 4)
            else -> random.nextGaussian(meanLength.toDouble(), meanLength * 0.4).roundToInt().coerceIn(1, 3 * meanLength)
        }
    }

    private fun styled(token: String, allowNear: Boolean): String {
        if (isHangul(token)) return token
        val roll = random.nextDouble()
        val text = when {
            roll < 0.72 -> token
            roll < 0.86 -> token.replaceFirstChar { it.uppercaseChar() }
            roll < 0.95 -> token.uppercase()
            else -> token.map { letter -> if (random.nextDouble() < 0.5) letter.uppercaseChar() else letter }.joinToString("")
        }
        return if (text == "NEAR" && !allowNear) "Near" else text
    }

    private fun queryToken(token: String): String {
        val text = styled(token, allowNear = false)
        val looksLikeOperator = token in OPERATOR_TOKENS && text == token.uppercase()
        return if (looksLikeOperator) token.replaceFirstChar { it.uppercaseChar() } else text
    }

    private fun joined(tokens: List<String>, allowNear: Boolean): String = buildString {
        append(styled(tokens.first(), allowNear))
        for ((previous, current) in tokens.zipWithNext()) {
            val joinsWithoutMark = isHangul(previous) != isHangul(current) && random.nextDouble() < 0.6
            if (!joinsWithoutMark) append(WORD_JOINERS.sample(random))
            append(styled(current, allowNear))
        }
    }

    private fun bodySeparator(previous: String, current: String): String {
        if (isHangul(previous) != isHangul(current)) {
            if (current in PARTICLES && random.nextDouble() < 0.85) return ""
            if (random.nextDouble() < 0.3) return ""
        }
        return BODY_SEPARATORS.sample(random)
    }

    private fun decorated(text: String, decorations: List<String>, prefixRate: Double, suffixRate: Double): String {
        val prefix = if (random.nextDouble() < prefixRate) decorations.random(random) else ""
        val suffix = if (random.nextDouble() < suffixRate) decorations.random(random) else ""
        return prefix + text + suffix
    }

    private fun randomPrefix(): String {
        val roll = random.nextDouble()
        return when {
            roll < 0.6 -> ""
            roll < 0.82 -> "+"
            else -> "-"
        }
    }

    private fun freeSearchTokens(): List<String> {
        val roll = random.nextDouble()
        val document = corpus.randomDocument(random)
        return when {
            roll < 0.45 || document.isEmpty() -> listOf(vocabulary.zipfWord())
            roll < 0.8 -> listOf(document.random(random))
            roll < 0.9 -> listOf(document.random(random), document.random(random))
            roll < 0.96 && corpus.retiredTokens.isNotEmpty() -> listOf(corpus.retiredTokens.random(random))
            else -> listOf(vocabulary.unknownWord())
        }
    }

    private fun boundedSearchToken(frequencyLimit: Int): String =
        corpus.boundedToken(random, frequencyLimit) ?: vocabulary.unknownWord()

    private fun largeSearchWords(): List<String> {
        val words = mutableListOf<String>()
        if (random.nextDouble() < 0.5) {
            repeat(listOf(1, 2, 2, 3).random(random)) { words += searchWord("", listOf(boundedSearchToken(LARGE_OPTIONAL_LIMIT))) }
            if (random.nextDouble() < 0.1) words += searchWord("", listOf(vocabulary.zipfWord()))
        } else {
            words += searchWord("+", listOf(boundedSearchToken(LARGE_REQUIRED_LIMIT)))
            if (random.nextDouble() < 0.25) words += searchWord("+", listOf(vocabulary.zipfWord()))
            repeat(listOf(0, 1, 2, 3).random(random)) { words += searchWord("", listOf(vocabulary.zipfWord())) }
        }
        if (random.nextDouble() < 0.3) words += searchWord("-", listOf(vocabulary.zipfWord()))
        words.shuffle(random)
        return words
    }

    private fun edgeSearchWords(): List<String> {
        val first = if (large) boundedSearchToken(LARGE_OPTIONAL_LIMIT) else freeSearchTokens().first()
        val second = if (large) boundedSearchToken(LARGE_OPTIONAL_LIMIT) else freeSearchTokens().first()
        val distance = random.nextInt(1, 10)
        return listOf(
            listOf("+$first", "-$first"), listOf("-$first"), listOf("-$first", "-$second"), listOf("+$first", first),
            listOf(first, "-$first", second), listOf(first, first.uppercase(), styled(first, allowNear = true)), listOf("+", "-"),
            listOf("+!!!", "-ㅋㅋ", "!!!"), listOf("++$first"), listOf("+-$first", second), listOf("-+$first", second),
            listOf("--$first", second), listOf("(+$first)", second), listOf("+($first)", second), listOf("-($first)", second),
            listOf("+$first-$second"), listOf("-$first-$second", first), listOf("$first-$second"), listOf("\"$first", "$second\""),
            listOf("NEAR/$distance", first), listOf(first, "AND", second, "OR"), listOf("NOT", first), listOf("($first", "$second)"),
            listOf("+$first\t$second"), listOf("$first\t+$second"), listOf("-$first\t$second", first), listOf("+$first", "+${first.uppercase()}"),
            listOf("+$first", "-$second", second), listOf("\"$first $second\""),
        ).random(random)
    }

    private fun anchoredTree(depth: Int): QueryTree {
        if (depth == 0 && random.nextDouble() < 0.15) {
            return QueryTree.Or(listOf(anchoredTree(depth + 1), anchoredTree(depth + 1)))
        }
        val occurrence = corpus.rareOccurrence(random, if (large) LARGE_ANCHOR_LIMIT else null)
            ?: return QueryTree.Term(queryWord(listOf(vocabulary.unknownWord())))
        val operands = mutableListOf<QueryTree>(QueryTree.Term(queryWord(anchorTokens(occurrence))))
        repeat(listOf(0, 1, 1, 2).random(random)) {
            operands += if (random.nextDouble() < 0.6) companion(occurrence.document) else freeTree(depth + 1)
        }
        operands.shuffle(random)
        val tree = operands.singleOrNull() ?: QueryTree.And(operands)
        return if (random.nextDouble() < 0.05) QueryTree.Not(QueryTree.Not(tree)) else tree
    }

    private fun anchorTokens(occurrence: Occurrence): List<String> {
        val anchor = occurrence.document[occurrence.position]
        return if (random.nextDouble() < 0.2) listOf(anchor, occurrence.document.random(random)) else listOf(anchor)
    }

    private fun companion(document: List<String>): QueryTree {
        val roll = random.nextDouble()
        return when {
            roll < 0.6 -> QueryTree.Term(queryWord(listOf(document.random(random))))
            roll < 0.8 -> QueryTree.Term(queryWord(listOf(document.random(random), document.random(random))))
            else -> QueryTree.Not(QueryTree.Term(queryWord(listOf(vocabulary.zipfWord()))))
        }
    }

    private fun freeTree(depth: Int): QueryTree {
        val roll = random.nextDouble()
        return when {
            depth >= 3 || roll < 0.4 -> QueryTree.Term(queryWord(termTokens()))
            roll < 0.55 -> QueryTree.Not(freeTree(depth + 1))
            roll < 0.8 -> QueryTree.And(List(listOf(2, 2, 3).random(random)) { freeTree(depth + 1) })
            else -> QueryTree.Or(List(listOf(2, 2, 3).random(random)) { freeTree(depth + 1) })
        }
    }

    private fun emit(tree: QueryTree, lexemes: MutableList<Lexeme>, requiredPrecedence: Int) {
        val grouped = tree.precedence < requiredPrecedence || random.nextDouble() < 0.07
        if (grouped) lexemes += Lexeme(LexemeKind.OPEN, "(")
        when (tree) {
            is QueryTree.Term -> lexemes += Lexeme(LexemeKind.WORD, tree.word)
            is QueryTree.Not -> {
                lexemes += Lexeme(LexemeKind.OPERATOR, "NOT")
                emit(tree.operand, lexemes, requiredPrecedence = 3)
            }
            is QueryTree.And -> tree.operands.forEachIndexed { index, operand ->
                if (index > 0 && random.nextDouble() < 0.5) lexemes += Lexeme(LexemeKind.OPERATOR, "AND")
                emit(operand, lexemes, requiredPrecedence = 2)
            }
            is QueryTree.Or -> tree.operands.forEachIndexed { index, operand ->
                if (index > 0) lexemes += Lexeme(LexemeKind.OPERATOR, "OR")
                emit(operand, lexemes, requiredPrecedence = 1)
            }
        }
        if (grouped) lexemes += Lexeme(LexemeKind.CLOSE, ")")
    }

    private fun scatterNoise(lexemes: List<Lexeme>): List<Lexeme> {
        if (random.nextDouble() > 0.25) return lexemes
        val scattered = lexemes.toMutableList()
        repeat(random.nextInt(1, 3)) {
            scattered.add(random.nextInt(0, scattered.size + 1), Lexeme(LexemeKind.NOISE, zeroTokenWord()))
        }
        return scattered
    }

    private fun render(lexemes: List<Lexeme>): String = buildString {
        append(lexemes.first().text)
        for ((previous, current) in lexemes.zipWithNext()) {
            append(gap(previous.kind, current.kind))
            append(current.text)
        }
    }

    private fun gap(previous: LexemeKind, current: LexemeKind): String {
        val besideParen = previous in PAREN_KINDS || current in PAREN_KINDS
        if (besideParen && random.nextDouble() < 0.5) return ""
        if (!large && current == LexemeKind.NOISE && previous in MERGEABLE_BEFORE_NOISE && random.nextDouble() < 0.2) return ""
        if (!large && previous == LexemeKind.NOISE && current == LexemeKind.WORD && random.nextDouble() < 0.2) return ""
        return if (random.nextDouble() < 0.05) "  " else " "
    }
}

data class DocumentShape(val sharedCount: Int, val extraCount: Int, val length: Int)

data class ScoredShape(val shape: DocumentShape, val score: Double)

class CommandScript(
    private val random: Random,
    private val writer: Writer,
    private val corpus: Corpus,
    private val vocabulary: Vocabulary,
    private val large: Boolean,
) {
    private val _lines = mutableListOf<String>()
    private var idCeiling = 30

    val lines: List<String> get() = _lines

    fun load(documentCount: Int) {
        idCeiling = maxOf(30, documentCount * 3)
        repeat(documentCount) {
            _lines += addLine(freshId(), writer.documentTokens())
        }
    }

    fun run(searchCount: Int) {
        repeat(searchCount) {
            while (random.nextDouble() < OTHER_COMMAND_RATE) _lines += otherCommand()
            _lines += searchCommandLines()
        }
    }

    private fun searchCommandLines(): List<String> {
        if (!large && random.nextDouble() < NEAR_TIE_RATE) {
            nearTieLines()?.let { return it }
        }
        return listOf(searchLine())
    }

    private fun searchLine(): String {
        if (random.nextDouble() < 0.07) return writer.brokenSearch()
        return spaced("SEARCH", "${writer.searchLimit()}", *writer.searchWords().toTypedArray())
    }

    private fun nearTieLines(): List<String>? {
        val shared = vocabulary.unknownWord()
        val extra = vocabulary.unknownWord()
        if (shared == extra) return null
        val documentCount = corpus.documentCount + 5
        repeat(12) {
            val helperLength = random.nextInt(3, 9)
            val budget = random.nextInt(30, 61)
            val averageLength = (corpus.totalLength + 2 * helperLength + budget).toDouble() / documentCount
            for (secondExtraCount in 0..2) {
                val sharedIdf = ln(1 + (documentCount - 3 + 0.5) / (3 + 0.5))
                val extraFrequency = if (secondExtraCount > 0) 3 else 2
                val extraIdf = ln(1 + (documentCount - extraFrequency + 0.5) / (extraFrequency + 0.5))
                fun score(shape: DocumentShape): Double {
                    val sharedPart = sharedIdf * bm25(1.0, shape.sharedCount, shape.length, averageLength)
                    val extraPart = if (shape.extraCount > 0) extraIdf * bm25(1.0, shape.extraCount, shape.length, averageLength) else 0.0
                    return sharedPart + extraPart
                }
                val secondShapes = (1..3)
                    .flatMap { sharedCount -> (sharedCount + secondExtraCount..25).map { length -> DocumentShape(sharedCount, secondExtraCount, length) } }
                    .map { ScoredShape(it, score(it)) }
                    .sortedWith(compareBy<ScoredShape>({ it.score }, { it.shape.sharedCount }, { it.shape.length }))
                val firstShapes = (1..3)
                    .flatMap { sharedCount -> (1..3).flatMap { extraCount -> (sharedCount + extraCount..25).map { length -> DocumentShape(sharedCount, extraCount, length) } } }
                    .shuffled(random)
                for (first in firstShapes) {
                    val firstScore = score(first)
                    val second = secondShapes.firstOrNull { candidate ->
                        candidate.score - firstScore > 1e-9 &&
                            first.length + candidate.shape.length <= budget &&
                            roundedScore(candidate.score) == roundedScore(firstScore)
                    }
                    if (second != null) return nearTieScript(shared, extra, helperLength, budget, first, second.shape)
                }
            }
        }
        return null
    }

    private fun nearTieScript(
        shared: String,
        extra: String,
        helperLength: Int,
        budget: Int,
        first: DocumentShape,
        second: DocumentShape,
    ): List<String> {
        val ids = mutableListOf<Int>()
        while (ids.size < 5) {
            val candidate = freshId()
            if (candidate !in ids) ids += candidate
        }
        ids.sort()
        val firstSlot = random.nextInt(0, 4)
        val secondSlot = random.nextInt(firstSlot + 1, 5)
        val helperIds = ids.filterIndexed { slot, _ -> slot != firstSlot && slot != secondSlot }
        val documents = listOf(
            ids[firstSlot] to tieTokens(mapOf(shared to first.sharedCount, extra to first.extraCount), first.length),
            ids[secondSlot] to tieTokens(mapOf(shared to second.sharedCount, extra to second.extraCount), second.length),
            helperIds[0] to tieTokens(mapOf(shared to 1), helperLength),
            helperIds[1] to tieTokens(mapOf(extra to 1), helperLength),
            helperIds[2] to tieTokens(emptyMap(), budget - first.length - second.length),
        ).shuffled(random)
        val addLines = documents.map { (id, tokens) -> addLine(id, tokens) }
        val words = listOf(writer.searchWord("", listOf(shared)), writer.searchWord("", listOf(extra))).shuffled(random)
        return addLines + spaced("SEARCH", "${random.nextInt(5, 21)}", *words.toTypedArray())
    }

    private fun tieTokens(counts: Map<String, Int>, length: Int): List<String> {
        val tokens = counts.flatMap { (token, count) -> List(count) { token } }.toMutableList()
        while (tokens.size < length) {
            val filler = vocabulary.zipfWord()
            if (filler !in counts) tokens += filler
        }
        tokens.shuffle(random)
        return tokens
    }

    private fun queryLine(): String {
        val expression = if (random.nextDouble() < 0.1) writer.brokenExpression() else writer.expression()
        if (expression.isEmpty()) return listOf("QUERY", "QUERY   ", "  QUERY").random(random)
        return spaced("QUERY", expression)
    }

    private fun otherCommand(): String {
        val roll = random.nextDouble()
        return when {
            roll < 0.18 -> addNew()
            roll < 0.34 -> addReplace()
            roll < 0.47 -> deleteExisting()
            roll < 0.52 -> deleteMissing()
            roll < 0.6 -> spaced("STATS")
            roll < 0.63 -> spaced("STATS", listOf("now", "0", "x", "-v", "STATS", "1 2").random(random))
            roll < 0.76 -> badArgument()
            roll < 0.86 -> unknownCommand()
            roll < 0.88 -> listOf("", " ", "    ").random(random)
            else -> queryLine()
        }
    }

    private fun addNew(): String {
        val reusesDeletedId = corpus.deletedIds.isNotEmpty() && random.nextDouble() < 0.3
        val id = if (reusesDeletedId) corpus.takeDeletedId(random) else freshId()
        val roll = random.nextDouble()
        val tokens = when {
            roll < 0.12 && corpus.liveIds.isNotEmpty() -> corpus.randomDocument(random)
            roll < 0.18 -> emptyList()
            else -> writer.documentTokens()
        }
        return addLine(id, tokens)
    }

    private fun addReplace(): String {
        if (corpus.liveIds.isEmpty()) return addNew()
        val id = corpus.liveIds.random(random)
        val roll = random.nextDouble()
        val tokens = when {
            roll < 0.1 -> corpus.tokensOf(id)
            roll < 0.2 -> emptyList()
            else -> writer.documentTokens()
        }
        return addLine(id, tokens)
    }

    private fun deleteExisting(): String {
        if (corpus.liveIds.isEmpty()) return deleteMissing()
        val id = corpus.liveIds.random(random)
        corpus.delete(id)
        return spaced("DEL", "$id")
    }

    private fun deleteMissing(): String {
        val id = if (corpus.deletedIds.isNotEmpty() && random.nextDouble() < 0.6) corpus.deletedIds.random(random) else freshId()
        return spaced("DEL", "$id")
    }

    private fun badArgument(): String {
        val word = plainWord()
        val known = knownId()
        val missing = freshId()
        return listOf(
            "ADD", "ADD ", "ADD 0 $word", "ADD 01 $word", "ADD 007 $word", "ADD 1234567890 $word",
            "ADD 1,2 $word", "ADD $word $word", "ADD -1 $word", "ADD +5 $word", "ADD ５ $word",
            "ADD 1.0 $word", "ADD $known\t$word", "ADD $known,$word", "ADD 99999999999 $word",
            "DEL", "DEL 0", "DEL 0$known", "DEL $known $known", "DEL $known 8", "DEL $missing 8", "DEL $word", "DEL -5",
            "DEL 1234567890", "DEL $known\t", "DEL ${known}x", "DEL １", "DEL 3.5", "DEL  $known  $known",
        ).random(random)
    }

    private fun unknownCommand(): String {
        val word = plainWord()
        val id = knownId()
        return listOf(
            "add $id $word", "Add $id $word", "del $id", "Del $id", "stats", "Stats", "query $word",
            "Query $word", "QUERY($word)", "QUERY\t$word", "STATS\t", "\tSTATS", "DELETE $id",
            "INSERT $id $word", "FIND $word", "STAT", "ADDD $id $word", "QUERY$word", "search 3 $word",
            "ＡＤＤ $id $word", "ADD\u00a0$id $word", "DEL\u00a0$id", "STATS\u00a0", "?", "ㅋㅋ", "-",
            "ADD\t$id $word", "QUERIES $word", "Q $word", "Search 3 $word", "SEARCH\t3 $word",
            "SEARCH3 $word", "SEARCHES 3 $word", "SEARCH(3) $word",
        ).random(random)
    }

    private fun addLine(id: Int, tokens: List<String>): String {
        corpus.add(id, tokens)
        val body = writer.body(tokens)
        return when {
            body.isEmpty() && random.nextDouble() < 0.5 -> spaced("ADD", "$id")
            body.trim(' ').isNotEmpty() -> spaced("ADD", "$id", body)
            else -> "ADD $id $body"
        }
    }

    private fun freshId(): Int {
        while (true) {
            val candidate = if (random.nextDouble() < 0.15) random.nextInt(1, 1_000_000_000) else random.nextInt(1, idCeiling + 1)
            if (!corpus.isUsed(candidate)) return candidate
        }
    }

    private fun knownId(): String = if (corpus.liveIds.isEmpty()) "1" else "${corpus.liveIds.random(random)}"

    private fun plainWord(): String {
        val word = writer.queryWord(listOf(vocabulary.zipfWord()))
        return if (word[0].isDigit() || word[0] in "+-") "x$word" else word
    }

    private fun spaced(vararg parts: String): String {
        val roll = random.nextDouble()
        return when {
            roll < 0.85 -> parts.joinToString(" ")
            roll < 0.93 -> parts.joinToString("  ")
            else -> "  " + parts.joinToString(" ") + "  "
        }
    }
}

val options = parseOptions(args)
val random = Random(options.seed)
val large = options.documents >= LARGE_DOCUMENTS
val meanLength = if (large) LARGE_MEAN_LENGTH else NORMAL_MEAN_LENGTH
val vocabulary = Vocabulary(random, maxOf(30, (3.5 * sqrt(options.documents.toDouble() * meanLength)).toInt()))
val corpus = Corpus()
val writer = Writer(random, vocabulary, corpus, large, meanLength)
val commandScript = CommandScript(random, writer, corpus, vocabulary, large)
commandScript.load(options.documents)
commandScript.run(options.queries)
System.out.write(commandScript.lines.joinToString("\n", postfix = "\n").encodeToByteArray())
System.out.flush()
