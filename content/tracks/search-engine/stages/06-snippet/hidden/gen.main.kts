import kotlin.math.PI
import kotlin.math.abs
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
    " [" to 1, "] " to 1, "[" to 1, "]" to 1, "][" to 1, "\r" to 1, "   " to 1,
    "+" to 1, "=" to 1, "Ａ" to 1, "１" to 1, " → " to 1, "@" to 1, "#" to 1, "*" to 1,
    " | " to 1, "ß" to 1, "ㅏ" to 1, "¿" to 1, "🙂" to 1, "\"" to 1,
)
val WORD_JOINERS = weightedChoice(
    "-" to 8, "_" to 4, "." to 4, "/" to 3, "'" to 3, ":" to 2, "+" to 2, "·" to 2, "é" to 2,
    "ㅋ" to 2, "&" to 1, "@" to 1, "~" to 1, "Ａ" to 1, "\t" to 1,
)
val PHRASE_SEPARATORS = weightedChoice(
    " " to 70, "  " to 4, "-" to 5, ", " to 3, " (" to 2, ") " to 2, "/" to 2, "_" to 2, "'" to 2,
    ": " to 1, "\t" to 1, "·" to 1, "é" to 1, "!" to 1, "+" to 1, "ㅋ" to 1,
)
val PHRASE_EDGES = listOf("", "", "", " ", "(", ")", "'", "!", "?", ".", "  ")
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
val ZERO_TOKEN_PHRASES = listOf("\"\"", "\"!!!\"", "\" \"", "\"ㅋㅋ\"", "\"()\"", "\"…\"", "\"Ａ１\"", "\"  -  \"")
val SIGN_ONLY_WORDS = listOf("+", "-", "+!!!", "-ㅋㅋ", "+-", "-+", "++")
val EMPTY_BODIES = listOf("", "", "!!! ㅋㅋ", "Ａ１", "---", "é", "\t", "…", "ㅏㅏ — ★", "\"\"")
val ZIPF_OFFSET = 2.7
val LARGE_DOCUMENTS = 1000
val LARGE_MEAN_LENGTH = 36
val NORMAL_MEAN_LENGTH = 16
val LONG_DOCUMENT_TOKENS = 2600
val LONG_DOCUMENT_MEAN_TOKENS = 1400
val LONG_DOCUMENT_MIN_BYTES = 8_800
val LONG_DOCUMENT_MAX_BYTES = 9_950
val LARGE_ANCHOR_LIMIT = 80
val LARGE_REQUIRED_LIMIT = 400
val LARGE_OPTIONAL_LIMIT = 150
val OTHER_COMMAND_RATE = 0.45
val LARGE_OTHER_COMMAND_RATE = 0.0015
val BROKEN_SNIPPET_RATE = 0.08
val LARGE_BROKEN_SNIPPET_RATE = 0.01
val RARE_IN_DOCUMENT_COUNT = 3
val BROKEN_FUZZY_RATE = 0.1
val BROKEN_SUGGEST_RATE = 0.1
val LARGE_BROKEN_SUGGEST_RATE = 0.02
val LONG_FUZZY_LENGTH = 5
val RARE_WORD_RATE = 0.04
val LARGE_RARE_WORD_RATE = 0.12
val FRESH_RARE_WORD_RATE = 0.9
val VARIANT_RATE = 0.06
val LATIN_EDIT_LETTERS = "abcdefghijklmnopqrstuvwxyzaeiouaeiounrstl0123456789"
val FUZZY_KEYWORDS = listOf("AND", "OR", "NOT", "NEAR", "Near", "and", "not")
val SHORT_FUZZY_WORDS = listOf("a", "k", "x", "go", "ab", "고", "검", "9", "1", "10", "on", "it", "가나")
data class RenderedDocument(val tokens: List<String>, val body: String)

val SUGGEST_ZERO_TOKEN_WORDS = listOf("ㄱ", "ㅋ", "ㄱㄴ", "!!!", "-", "+", "\"\"", "()", "ㅏ", "Ａ", "é")
val MULTI_TOKEN_WORDS = listOf("e-mail", "Kotlin은", "NEAR/3", "a1-b2", "k8s_v2", "new-york", "검색-엔진", "data.base", "x86/64", "C++14")
val MAX_LEXEMES = 100
val MAX_NEAR_DISTANCE = 50

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

fun quoted(content: String): String = "\"$content\""

fun editLetter(random: Random, hangul: Boolean): Char =
    if (hangul) KOREAN_SYLLABLES.random(random) else LATIN_EDIT_LETTERS.random(random)

fun editedOnce(random: Random, word: String): String {
    val hangul = isHangul(word)
    val roll = random.nextDouble()
    if (word.length == 1 || roll < 0.3) {
        val insertAt = random.nextInt(word.length + 1)
        return word.substring(0, insertAt) + editLetter(random, hangul) + word.substring(insertAt)
    }
    val position = random.nextInt(word.length)
    return when {
        roll < 0.55 -> word.removeRange(position, position + 1)
        roll < 0.85 || position == word.lastIndex -> word.replaceRange(position, position + 1, editLetter(random, hangul).toString())
        else -> word.substring(0, position) + word[position + 1] + word[position] + word.substring(position + 2)
    }
}

fun misspelled(random: Random, word: String, editCount: Int): String =
    (1..editCount).fold(word) { current, _ -> editedOnce(random, current) }

fun neighborPosition(position: Int, offset: Int, documentSize: Int): Int {
    val forward = position + offset
    val neighbor = if (forward < documentSize) forward else maxOf(0, position - offset)
    return if (neighbor == position) (position + 1) % documentSize else neighbor
}

class Vocabulary(private val random: Random, size: Int) {
    private val knownWords = mutableSetOf<String>()
    private val rareWords = mutableListOf<String>()
    private val variantsByWord = mutableMapOf<String, List<String>>()
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

    fun variantOf(word: String): String =
        variantsByWord.getOrPut(word) { List(random.nextInt(1, 4)) { misspelled(random, word, random.nextInt(1, 3)) } }.random(random)

    fun rareWord(): String {
        if (rareWords.isNotEmpty() && random.nextDouble() > FRESH_RARE_WORD_RATE) return rareWords.random(random)
        while (true) {
            val word = rareSyntheticWord()
            if (knownWords.add(word)) {
                rareWords += word
                return word
            }
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

    private fun rareSyntheticWord(): String {
        val roll = random.nextDouble()
        return when {
            roll < 0.72 -> {
                val stem = List(random.nextInt(2, 5)) { ENGLISH_ONSETS.random(random) + ENGLISH_VOWELS.random(random) }
                stem.joinToString("") + ENGLISH_CODAS.random(random)
            }
            roll < 0.88 -> List(random.nextInt(2, 5)) { KOREAN_SYLLABLES.random(random) }.joinToString("")
            else -> "${ENGLISH_ONSETS.random(random)}${ENGLISH_VOWELS.random(random)}${random.nextInt(0, 10_000)}"
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

    val liveIds: List<Int> get() = _liveIds
    val deletedIds: List<Int> get() = _deletedIds
    val retiredTokens: List<String> get() = _retiredTokens

    fun isUsed(id: Int): Boolean = id in usedIds

    private val tokenCountsById = mutableMapOf<Int, Map<String, Int>>()

    fun tokensOf(id: Int): List<String> = documents.getValue(id)

    fun tokenCountsOf(id: Int): Map<String, Int> =
        tokenCountsById.getOrPut(id) { documents.getValue(id).groupingBy { it }.eachCount() }

    fun add(id: Int, tokens: List<String>) {
        if (id in documents) remove(id)
        documents[id] = tokens
        slots[id] = _liveIds.size
        _liveIds += id
        usedIds += id
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

    fun liveToken(random: Random, minLength: Int): String? {
        repeat(12) {
            val tokens = randomDocument(random)
            if (tokens.isNotEmpty()) {
                val token = tokens.random(random)
                if (token.length >= minLength) return token
            }
        }
        return null
    }

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
        tokenCountsById.remove(id)
        val slot = checkNotNull(slots.remove(id))
        val lastId = _liveIds.removeAt(_liveIds.lastIndex)
        if (lastId != id) {
            _liveIds[slot] = lastId
            slots[lastId] = slot
        }
        for (token in tokens.distinct()) {
            val remaining = documentFrequency.getValue(token) - 1
            if (remaining > 0) documentFrequency[token] = remaining else documentFrequency.remove(token)
        }
        _retiredTokens += tokens.take(4)
        if (_retiredTokens.size > 40) _retiredTokens.subList(0, _retiredTokens.size - 40).clear()
    }
}

enum class LexemeKind { OPEN, CLOSE, OPERATOR, WORD, PHRASE, NEAR, NOISE }

val PAREN_KINDS = setOf(LexemeKind.OPEN, LexemeKind.CLOSE)
val MERGEABLE_BEFORE_NOISE = setOf(LexemeKind.OPERATOR, LexemeKind.WORD)

data class Lexeme(val kind: LexemeKind, val text: String)

sealed class QueryTree(val precedence: Int) {
    class Term(val word: String) : QueryTree(5)
    class Phrase(val text: String) : QueryTree(5)
    class Near(val left: Lexeme, val maxDistance: Int, val right: Lexeme) : QueryTree(4)
    class Not(val operand: QueryTree) : QueryTree(3)
    class And(val operands: List<QueryTree>) : QueryTree(2)
    class Or(val operands: List<QueryTree>) : QueryTree(1)
}

data class NearPair(val left: String, val right: String, val maxDistance: Int)

class Writer(
    private val random: Random,
    private val vocabulary: Vocabulary,
    private val corpus: Corpus,
    private val large: Boolean,
    private val meanLength: Int,
) {
    fun documentTokens(length: Int = documentLength()): List<String> {
        val rareWordRate = if (large) LARGE_RARE_WORD_RATE else RARE_WORD_RATE
        val tokens = mutableListOf<String>()
        while (tokens.size < length) {
            if (random.nextDouble() < rareWordRate) {
                tokens += vocabulary.rareWord()
                continue
            }
            if (random.nextDouble() < 0.1) {
                tokens += vocabulary.collocation()
                continue
            }
            val zipfWord = vocabulary.zipfWord()
            val baseWord = if (random.nextDouble() < VARIANT_RATE) vocabulary.variantOf(zipfWord) else zipfWord
            val word = if (isHangul(baseWord) && random.nextDouble() < 0.15) baseWord + PARTICLES.random(random) else baseWord
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

    fun fuzzyLimit(): Int = searchLimit()

    fun longDocument(): RenderedDocument {
        val byteLimit = random.nextInt(LONG_DOCUMENT_MIN_BYTES, LONG_DOCUMENT_MAX_BYTES + 1)
        val tokens = mutableListOf<String>()
        val body = StringBuilder()
        var bytes = 0
        var previous: String? = null
        for (token in documentTokens(LONG_DOCUMENT_TOKENS)) {
            val separator = previous?.let { bodySeparator(it, token) }.orEmpty()
            val piece = separator + styled(token, allowNear = true)
            val pieceBytes = piece.encodeToByteArray().size
            if (bytes + pieceBytes > byteLimit) break
            body.append(piece)
            bytes += pieceBytes
            tokens += token
            previous = token
        }
        return RenderedDocument(tokens, body.toString())
    }

    fun snippetWords(document: List<String>, tokenCounts: Map<String, Int>): List<String> =
        if (large) largeSnippetWords(document, tokenCounts) else normalSnippetWords(document)

    fun brokenSnippet(knownId: String): String {
        val word = queryWord(listOf(vocabulary.zipfWord()))
        val other = queryWord(listOf(vocabulary.zipfWord()))
        val plain = vocabulary.zipfWord().let { if (it[0].isDigit()) "x$it" else it }
        return listOf(
            "SNIPPET", "SNIPPET ", "SNIPPET  ", "SNIPPET 0 $word", "SNIPPET 07 $word", "SNIPPET 007", "SNIPPET -1 $word",
            "SNIPPET +5 $word", "SNIPPET 1234567890 $word", "SNIPPET $plain", "SNIPPET $plain $knownId", "SNIPPET ５ $word",
            "SNIPPET 3.5 $word", "SNIPPET $knownId\t$word", "SNIPPET ${knownId}x $word", "SNIPPET $knownId,$word",
            "SNIPPET 0", "SNIPPET 1e3 $word", "SNIPPET 0$knownId $word $other", "SNIPPET \"$knownId\" $word",
            "SNIPPET ($knownId) $word", "SNIPPET 99999999999 $word",
        ).random(random)
    }

    private fun normalSnippetWords(document: List<String>): List<String> {
        if (document.isEmpty() || random.nextDouble() < 0.06) return List(random.nextInt(0, 3)) { snippetNoiseWord(document) }
        val anchor = random.nextInt(document.size)
        val words = mutableListOf<String>()
        repeat(listOf(1, 1, 2, 2, 2, 3, 3, 4, 5).random(random)) {
            val roll = random.nextDouble()
            words += when {
                roll < 0.35 -> snippetWord(document[anchorNeighbor(anchor, document.size)])
                roll < 0.58 -> snippetWord(document.random(random))
                roll < 0.7 -> snippetWord(vocabulary.zipfWord())
                roll < 0.76 -> snippetWord(vocabulary.unknownWord())
                roll < 0.82 -> zeroTokenWord()
                roll < 0.92 && document.size > 1 -> joinedSnippetWord(document, anchor)
                else -> words.lastOrNull()?.uppercase() ?: snippetWord(document.random(random))
            }
        }
        words.shuffle(random)
        return words
    }

    private fun largeSnippetWords(document: List<String>, tokenCounts: Map<String, Int>): List<String> {
        val count = listOf(1, 1, 1, 1, 2, 2, 2, 3, 3, 4).random(random)
        return List(count) {
            val roll = random.nextDouble()
            val token = when {
                roll < 0.55 -> rareTokenIn(document, tokenCounts)
                roll < 0.9 -> absentToken(tokenCounts)
                else -> document.randomOrNull(random)
            } ?: vocabulary.unknownWord()
            snippetWord(token)
        }
    }

    private fun rareTokenIn(document: List<String>, tokenCounts: Map<String, Int>): String? {
        repeat(6) {
            val token = document.randomOrNull(random) ?: return null
            if (tokenCounts.getValue(token) <= RARE_IN_DOCUMENT_COUNT) return token
        }
        return null
    }

    private fun absentToken(tokenCounts: Map<String, Int>): String? {
        repeat(4) {
            val token = if (random.nextDouble() < 0.5) vocabulary.zipfWord() else corpus.randomDocument(random).randomOrNull(random)
            if (token != null && token !in tokenCounts) return token
        }
        return null
    }

    private fun anchorNeighbor(anchor: Int, documentSize: Int): Int {
        val offset = listOf(0, 1, 2, 3, 5, 8, 9, 10, 11).random(random)
        return if (offset == 0) anchor else neighborPosition(anchor, offset, documentSize)
    }

    private fun snippetWord(token: String): String =
        decorated(styled(token, allowNear = true), SEARCH_DECORATIONS, prefixRate = 0.1, suffixRate = 0.1)

    private fun joinedSnippetWord(document: List<String>, anchor: Int): String {
        val next = neighborPosition(anchor, 1, document.size)
        return joined(listOf(document[minOf(anchor, next)], document[maxOf(anchor, next)]), allowNear = true)
    }

    private fun snippetNoiseWord(document: List<String>): String {
        val roll = random.nextDouble()
        return when {
            roll < 0.5 -> zeroTokenWord()
            roll < 0.8 || document.isEmpty() -> snippetWord(vocabulary.unknownWord())
            else -> snippetWord(document.random(random))
        }
    }

    fun suggestLimit(): Int {
        val roll = random.nextDouble()
        if (large) {
            return when {
                roll < 0.45 -> random.nextInt(1, 4)
                roll < 0.8 -> random.nextInt(4, 7)
                roll < 0.975 -> random.nextInt(7, 11)
                roll < 0.999 -> random.nextInt(11, 31)
                else -> random.nextInt(31, 1001)
            }
        }
        return searchLimit()
    }

    fun suggestPrefix(): String {
        val source = suggestSourceToken()
        val roll = random.nextDouble()
        val wholeTokenShare = if (large) 0.06 else 0.12
        val prefix = when {
            roll < wholeTokenShare -> source
            roll < wholeTokenShare + 0.04 -> source + editLetter(random, isHangul(source))
            else -> source.take(suggestPrefixLength())
        }
        return decorated(styled(prefix, allowNear = true), SEARCH_DECORATIONS, prefixRate = 0.08, suffixRate = 0.08)
    }

    fun brokenSuggest(): String {
        val word = queryWord(listOf(vocabulary.zipfWord())).take(2).ifEmpty { "ko" }
        val other = queryWord(listOf(vocabulary.zipfWord()))
        val multiple = joined(listOf(vocabulary.zipfWord(), vocabulary.zipfWord()), allowNear = true)
        val zero = SUGGEST_ZERO_TOKEN_WORDS.random(random)
        val limit = random.nextInt(1, 11)
        return listOf(
            "SUGGEST", "SUGGEST ", "SUGGEST $limit", "SUGGEST  $limit  ", "SUGGEST $limit $word $other", "SUGGEST $word",
            "SUGGEST 0 $word", "SUGGEST 01 $word", "SUGGEST 007 $word", "SUGGEST 1001 $word", "SUGGEST -1 $word",
            "SUGGEST +5 $word", "SUGGEST 1e3 $word", "SUGGEST １０ $word", "SUGGEST 10000 $word", "SUGGEST 2.5 $word",
            "SUGGEST $limit\t$word", "SUGGEST $limit $zero", "SUGGEST $limit $multiple", "SUGGEST $limit ${MULTI_TOKEN_WORDS.random(random)}",
            "SUGGEST $limit \"$word $other\"", "SUGGEST 0 $zero", "SUGGEST 1001 $multiple", "SUGGEST $limit $zero $word",
            "SUGGEST k $word", "SUGGEST $limit,$word", "SUGGEST $limit\t", "SUGGEST $limit $word\t$other", "SUGGEST $limit ㄱ",
        ).random(random)
    }

    private fun suggestPrefixLength(): Int {
        val roll = random.nextDouble()
        val oneLetterShare = if (large) 0.72 else 0.38
        val twoLetterShare = if (large) 0.2 else 0.3
        return when {
            roll < oneLetterShare -> 1
            roll < oneLetterShare + twoLetterShare -> 2
            else -> random.nextInt(3, 6)
        }
    }

    private fun suggestSourceToken(): String {
        val roll = random.nextDouble()
        val picked = when {
            large && roll < 0.9 -> corpus.liveToken(random, 1)?.takeIf { !isHangul(it) || random.nextDouble() < 0.35 }
            roll < 0.5 -> corpus.liveToken(random, 1)
            roll < 0.6 -> vocabulary.zipfWord()
            roll < 0.7 -> corpus.retiredTokens.takeLast(6).randomOrNull(random)
            roll < 0.76 -> vocabulary.variantOf(vocabulary.zipfWord())
            roll < 0.8 -> vocabulary.unknownWord()
            roll < 0.9 -> KOREAN_WORDS.random(random)
            else -> ALNUM_WORDS.random(random)
        }
        return picked ?: corpus.liveToken(random, 1) ?: vocabulary.zipfWord()
    }

    fun fuzzyWord(): String {
        if (random.nextDouble() < 0.03) return FUZZY_KEYWORDS.random(random)
        val word = misspelled(random, fuzzySourceToken(), fuzzyEditCount())
        return decorated(styled(word, allowNear = true), SEARCH_DECORATIONS, prefixRate = 0.1, suffixRate = 0.1)
    }

    fun brokenFuzzy(): String {
        val word = queryWord(listOf(vocabulary.zipfWord()))
        val other = queryWord(listOf(vocabulary.zipfWord()))
        val multiple = joined(listOf(vocabulary.zipfWord(), vocabulary.zipfWord()), allowNear = true)
        val zero = zeroTokenWord()
        val limit = random.nextInt(1, 11)
        return listOf(
            "FUZZY", "FUZZY ", "FUZZY $limit", "FUZZY  $limit  ", "FUZZY $limit $word $other", "FUZZY $word",
            "FUZZY 0 $word", "FUZZY 01 $word", "FUZZY 007 $word", "FUZZY 1001 $word", "FUZZY -1 $word",
            "FUZZY +5 $word", "FUZZY 1e3 $word", "FUZZY １０ $word", "FUZZY 10000 $word", "FUZZY 3.5 $word",
            "FUZZY $limit\t$word", "FUZZY $limit $zero", "FUZZY $limit $multiple", "FUZZY $limit ${MULTI_TOKEN_WORDS.random(random)}",
            "FUZZY $limit \"$word $other\"", "FUZZY 0 $zero", "FUZZY 1001 $multiple", "FUZZY $limit $zero $word",
            "FUZZY k $word", "FUZZY $limit,$word", "FUZZY $limit\t", "FUZZY $limit $word\t$other",
        ).random(random)
    }

    private fun fuzzyEditCount(): Int {
        val roll = random.nextDouble()
        return when {
            roll < 0.25 -> 0
            roll < 0.65 -> 1
            roll < 0.92 -> 2
            else -> 3
        }
    }

    private fun fuzzySourceToken(): String {
        val minLength = if (large && random.nextDouble() < 0.95) LONG_FUZZY_LENGTH else 1
        val roll = random.nextDouble()
        val picked = when {
            roll < 0.5 -> corpus.liveToken(random, minLength)
            roll < 0.6 -> vocabulary.zipfWord().takeIf { it.length >= minLength }
            roll < 0.72 -> corpus.retiredTokens.takeLast(6).filter { it.length >= minLength }.randomOrNull(random)
            roll < 0.82 -> vocabulary.variantOf(vocabulary.zipfWord()).takeIf { it.length >= minLength }
            roll < 0.88 -> vocabulary.unknownWord()
            minLength == 1 -> shortFuzzyToken()
            else -> null
        }
        return picked ?: corpus.liveToken(random, minLength) ?: vocabulary.unknownWord()
    }

    private fun shortFuzzyToken(): String {
        val token = corpus.liveToken(random, 1)
        if (token == null || random.nextDouble() < 0.4) return SHORT_FUZZY_WORDS.random(random)
        return token.take(random.nextInt(1, 3))
    }

    fun expression(): String {
        while (true) {
            val tree = if (large || random.nextDouble() < 0.3) anchoredTree(depth = 0) else freeTree(depth = 0)
            val lexemes = scatterNoise(buildList { emit(tree, this, requiredPrecedence = 1) })
            if (lexemes.size <= MAX_LEXEMES) return render(lexemes)
        }
    }

    fun brokenExpression(): String {
        if (random.nextDouble() < 0.6) return brokenProximity()
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

    private fun brokenProximity(): String {
        val first = queryWord(listOf(vocabulary.zipfWord()))
        val second = queryWord(listOf(vocabulary.zipfWord()))
        val third = queryWord(listOf(vocabulary.zipfWord()))
        val multiple = queryWord(listOf(vocabulary.zipfWord(), vocabulary.zipfWord()))
        val zero = zeroTokenWord()
        val near = "NEAR/${random.nextInt(1, MAX_NEAR_DISTANCE + 1)}"
        return listOf(
            "\"$first", "$first \"$second", "\"", "$first\"", "\"$first\" \"$second", "($first \"$second)",
            "$first NEAR/ $second", "$first NEAR/0 $second", "$first NEAR/05 $second", "$first NEAR/51 $second",
            "$first NEAR/3a $second", "$first NEAR/-1 $second", "$first NEAR/1.5 $second", "$first $near!!! $second",
            "$first NEAR/100 $second", "$first NEAR//3 $second", "$first $near/2 $second", "$multiple $near $first",
            "\"$first $second\" $near $third", "($first) $near $second", "$first $near ($second)",
            "$first $near NOT $second", "NOT $near $first", "$first $near AND $second", "$first $near OR $second",
            "$near $first", "$first $near", "( $first $near )", "( $near $first )", "$first $near \"$zero\"",
            "$first $near \"\"", "$first $near $zero", "$first $near $second $near $third",
            "$first $near $second NEAR/1", near, "$first $near $near $second", "NOT $first $near",
            "$first $near \"$second $third\"", "$first $near $multiple",
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

    private fun zeroTokenWord(): String = ZERO_TOKEN_WORDS.random(random)

    private fun termTokens(): List<String> {
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

    private fun phraseText(tokens: List<String>): String {
        val content = buildString {
            append(styled(tokens.first(), allowNear = true))
            for ((previous, current) in tokens.zipWithNext()) {
                val joinsWithoutMark = isHangul(previous) != isHangul(current) && random.nextDouble() < 0.3
                if (!joinsWithoutMark) append(PHRASE_SEPARATORS.sample(random))
                append(styled(current, allowNear = true))
            }
        }
        return quoted(PHRASE_EDGES.random(random) + content + PHRASE_EDGES.random(random))
    }

    private fun phraseTokens(document: List<String>): List<String> {
        val roll = random.nextDouble()
        if (document.size < 2 || roll < 0.12) return vocabulary.collocation()
        val length = listOf(2, 2, 2, 3, 3, 4).random(random)
        val start = random.nextInt(0, maxOf(0, document.size - length) + 1)
        val slice = document.subList(start, minOf(start + length, document.size))
        return when {
            roll < 0.2 -> slice.reversed()
            roll < 0.27 && start + 2 < document.size -> listOf(document[start], document[start + 2])
            roll < 0.33 -> slice.toMutableList().also { it[random.nextInt(it.size)] = vocabulary.zipfWord() }
            roll < 0.36 -> listOf(slice[0], slice[0])
            roll < 0.42 -> slice.take(1)
            else -> slice
        }
    }

    private fun nearPair(document: List<String>): NearPair {
        if (document.size < 2 || random.nextDouble() < 0.1) {
            return NearPair(vocabulary.zipfWord(), vocabulary.zipfWord(), random.nextInt(1, 7))
        }
        if (random.nextDouble() < 0.15) {
            val repeatedTokens = document.distinct().filter { token -> document.count { it == token } > 1 }
            if (repeatedTokens.isNotEmpty()) {
                val token = repeatedTokens.random(random)
                val positions = document.indices.filter { document[it] == token }
                val closestGap = positions.zipWithNext().minOf { (earlier, later) -> later - earlier }
                return NearPair(token, token, nearDistance(closestGap, listOf(-1, 0, 0, 1)))
            }
        }
        val firstPosition = random.nextInt(document.size)
        val secondPosition = neighborPosition(firstPosition, listOf(1, 1, 2, 2, 3, 4, 6, 9).random(random), document.size)
        val maxDistance = nearDistance(abs(secondPosition - firstPosition), listOf(-1, 0, 0, 0, 1, 3, 12))
        return NearPair(document[firstPosition], document[secondPosition], maxDistance)
    }

    private fun nearDistance(gap: Int, adjustments: List<Int>): Int =
        (gap + adjustments.random(random)).coerceIn(1, MAX_NEAR_DISTANCE)

    private fun proximityOperand(token: String): Lexeme {
        if (random.nextDouble() < 0.2) {
            val content = PHRASE_EDGES.random(random) + styled(token, allowNear = true) + PHRASE_EDGES.random(random)
            return Lexeme(LexemeKind.PHRASE, quoted(content))
        }
        return Lexeme(LexemeKind.WORD, queryWord(listOf(token)))
    }

    private fun nearNode(pair: NearPair): QueryTree {
        val swapped = random.nextDouble() < 0.5
        val left = if (swapped) pair.right else pair.left
        val right = if (swapped) pair.left else pair.right
        return QueryTree.Near(proximityOperand(left), pair.maxDistance, proximityOperand(right))
    }

    private fun lookalikeWord(): String {
        val distance = random.nextInt(1, 10)
        return listOf("NEAR", "near/$distance", "Near/$distance", "!NEAR/$distance", "nEAR/$distance", "NEAR-$distance", "NEAR_$distance")
            .random(random)
    }

    private fun atom(): QueryTree {
        val roll = random.nextDouble()
        return when {
            roll < 0.45 -> QueryTree.Term(queryWord(termTokens()))
            roll < 0.72 -> QueryTree.Phrase(phraseText(phraseTokens(corpus.randomDocument(random))))
            roll < 0.94 -> nearNode(nearPair(corpus.randomDocument(random)))
            else -> QueryTree.Term(lookalikeWord())
        }
    }

    private fun companion(document: List<String>): QueryTree {
        val roll = random.nextDouble()
        return when {
            roll < 0.35 -> QueryTree.Term(queryWord(listOf(document.random(random))))
            roll < 0.6 -> QueryTree.Phrase(phraseText(phraseTokens(document)))
            roll < 0.85 -> nearNode(nearPair(document))
            else -> QueryTree.Not(atom())
        }
    }

    private fun anchorAtom(occurrence: Occurrence): QueryTree {
        val document = occurrence.document
        val position = occurrence.position
        val roll = random.nextDouble()
        if (roll < 0.45 || document.size < 2) return QueryTree.Term(queryWord(listOf(document[position])))
        if (roll < 0.75) {
            val length = listOf(2, 2, 3).random(random)
            val start = maxOf(0, minOf(position - random.nextInt(length), document.size - length))
            return QueryTree.Phrase(phraseText(document.subList(start, minOf(start + length, document.size))))
        }
        val otherPosition = neighborPosition(position, listOf(1, 1, 2, 3, 5).random(random), document.size)
        val maxDistance = nearDistance(abs(otherPosition - position), listOf(-1, 0, 0, 1, 2))
        return nearNode(NearPair(document[position], document[otherPosition], maxDistance))
    }

    private fun freeTree(depth: Int): QueryTree {
        val roll = random.nextDouble()
        return when {
            depth >= 3 || roll < 0.4 -> atom()
            roll < 0.55 -> QueryTree.Not(freeTree(depth + 1))
            roll < 0.8 -> QueryTree.And(List(listOf(2, 2, 3).random(random)) { freeTree(depth + 1) })
            else -> QueryTree.Or(List(listOf(2, 2, 3).random(random)) { freeTree(depth + 1) })
        }
    }

    private fun anchoredTree(depth: Int): QueryTree {
        if (depth == 0 && random.nextDouble() < 0.15) {
            return QueryTree.Or(listOf(anchoredTree(depth + 1), anchoredTree(depth + 1)))
        }
        val occurrence = corpus.rareOccurrence(random, if (large) LARGE_ANCHOR_LIMIT else null)
            ?: return QueryTree.Term(queryWord(listOf(vocabulary.unknownWord())))
        val operands = mutableListOf(anchorAtom(occurrence))
        repeat(listOf(0, 1, 1, 2).random(random)) {
            operands += if (random.nextDouble() < 0.6) companion(occurrence.document) else freeTree(depth + 1)
        }
        operands.shuffle(random)
        val tree = operands.singleOrNull() ?: QueryTree.And(operands)
        return if (random.nextDouble() < 0.05) QueryTree.Not(QueryTree.Not(tree)) else tree
    }

    private fun emit(tree: QueryTree, lexemes: MutableList<Lexeme>, requiredPrecedence: Int) {
        val grouped = tree.precedence < requiredPrecedence || random.nextDouble() < 0.07
        if (grouped) lexemes += Lexeme(LexemeKind.OPEN, "(")
        when (tree) {
            is QueryTree.Term -> lexemes += Lexeme(LexemeKind.WORD, tree.word)
            is QueryTree.Phrase -> lexemes += Lexeme(LexemeKind.PHRASE, tree.text)
            is QueryTree.Near -> {
                lexemes += tree.left
                lexemes += Lexeme(LexemeKind.NEAR, "NEAR/${tree.maxDistance}")
                lexemes += tree.right
            }
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
            val noise = if (random.nextDouble() < 0.3) ZERO_TOKEN_PHRASES.random(random) else zeroTokenWord()
            scattered.add(random.nextInt(0, scattered.size + 1), Lexeme(LexemeKind.NOISE, noise))
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
        val besidePhrase = previous == LexemeKind.PHRASE || current == LexemeKind.PHRASE
        if (besidePhrase && random.nextDouble() < 0.3) return ""
        if (!large && current == LexemeKind.NOISE && previous in MERGEABLE_BEFORE_NOISE && random.nextDouble() < 0.2) return ""
        if (!large && previous == LexemeKind.NOISE && current == LexemeKind.WORD && random.nextDouble() < 0.2) return ""
        return if (random.nextDouble() < 0.05) "  " else " "
    }
}

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
            _lines += if (large) longAddLine(freshId()) else addLine(freshId(), writer.documentTokens())
        }
    }

    fun run(snippetCount: Int) {
        val otherCommandRate = if (large) LARGE_OTHER_COMMAND_RATE else OTHER_COMMAND_RATE
        repeat(snippetCount) {
            while (random.nextDouble() < otherCommandRate) _lines += otherCommand()
            _lines += snippetLine()
        }
    }

    private fun snippetLine(): String {
        val brokenRate = if (large) LARGE_BROKEN_SNIPPET_RATE else BROKEN_SNIPPET_RATE
        if (random.nextDouble() < brokenRate) return writer.brokenSnippet(knownId())
        val roll = random.nextDouble()
        if (roll < 0.06 || corpus.liveIds.isEmpty()) {
            val missingId = if (corpus.deletedIds.isNotEmpty() && random.nextDouble() < 0.6) corpus.deletedIds.random(random) else freshId()
            val donorId = corpus.liveIds.randomOrNull(random)
            val words = if (donorId == null) emptyList() else writer.snippetWords(corpus.tokensOf(donorId), corpus.tokenCountsOf(donorId)).take(2)
            return spaced("SNIPPET", "$missingId", *words.toTypedArray())
        }
        val id = corpus.liveIds.random(random)
        val words = writer.snippetWords(corpus.tokensOf(id), corpus.tokenCountsOf(id))
        return spaced("SNIPPET", "$id", *words.toTypedArray())
    }

    private fun suggestLine(): String {
        if (random.nextDouble() < BROKEN_SUGGEST_RATE) return writer.brokenSuggest()
        return spaced("SUGGEST", "${writer.suggestLimit()}", writer.suggestPrefix())
    }

    private fun fuzzyLine(): String {
        if (random.nextDouble() < BROKEN_FUZZY_RATE) return writer.brokenFuzzy()
        return spaced("FUZZY", "${writer.fuzzyLimit()}", writer.fuzzyWord())
    }

    private fun queryLine(): String {
        val expression = if (random.nextDouble() < 0.12) writer.brokenExpression() else writer.expression()
        if (expression.isEmpty()) return listOf("QUERY", "QUERY   ", "  QUERY").random(random)
        return spaced("QUERY", expression)
    }

    private fun searchLine(): String {
        if (random.nextDouble() < 0.07) return writer.brokenSearch()
        return spaced("SEARCH", "${writer.searchLimit()}", *writer.searchWords().toTypedArray())
    }

    private fun otherCommand(): String {
        if (large) return largeOtherCommand()
        val roll = random.nextDouble()
        return when {
            roll < 0.16 -> addNew()
            roll < 0.3 -> addReplace()
            roll < 0.42 -> deleteExisting()
            roll < 0.46 -> deleteMissing()
            roll < 0.52 -> spaced("STATS")
            roll < 0.54 -> spaced("STATS", listOf("now", "0", "x", "-v", "STATS", "1 2").random(random))
            roll < 0.63 -> badArgument()
            roll < 0.72 -> unknownCommand()
            roll < 0.74 -> listOf("", " ", "    ").random(random)
            roll < 0.8 -> queryLine()
            roll < 0.86 -> searchLine()
            roll < 0.92 -> fuzzyLine()
            else -> suggestLine()
        }
    }

    private fun largeOtherCommand(): String {
        val roll = random.nextDouble()
        return when {
            roll < 0.18 -> addNew()
            roll < 0.6 -> addReplace()
            roll < 0.8 -> deleteExisting()
            roll < 0.82 -> spaced("STATS")
            roll < 0.86 -> queryLine()
            roll < 0.9 -> searchLine()
            roll < 0.93 -> fuzzyLine()
            roll < 0.96 -> suggestLine()
            roll < 0.98 -> badArgument()
            else -> unknownCommand()
        }
    }

    private fun addNew(): String {
        val reusesDeletedId = corpus.deletedIds.isNotEmpty() && random.nextDouble() < 0.3
        val id = if (reusesDeletedId) corpus.takeDeletedId(random) else freshId()
        if (large) return longAddLine(id)
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
        if (large) return longAddLine(id)
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
            "SEARCH3 $word", "SEARCHES 3 $word", "SEARCH(3) $word", "fuzzy 3 $word", "Fuzzy 3 $word",
            "FUZZY\t3 $word", "FUZZY3 $word", "FUZZ 3 $word", "FUZZYY 3 $word", "ＦＵＺＺＹ 3 $word", "FUZZY\u00a03 $word",
            "FUZZY(3) $word", "suggest 3 $word", "snippet $id $word", "Suggest 3 $word", "SUGGEST\t3 $word",
            "SUGGEST3 $word", "SUGGESTS 3 $word", "SUGGESTION 3 $word", "ＳＵＧＧＥＳＴ 3 $word", "SUGGEST(3) $word",
            "SUGGEST\u00a03 $word", "SUGEST 3 $word", "Snippet $id $word", "SNIPPET\t$id $word", "SNIPPET$id $word",
            "SNIPPETS $id $word", "ＳＮＩＰＰＥＴ $id $word", "SNIPPET($id) $word", "SNIPET $id $word", "SNIPPET\u00a0$id $word",
        ).random(random)
    }

    private fun longAddLine(id: Int): String {
        val document = writer.longDocument()
        corpus.add(id, document.tokens)
        return spaced("ADD", "$id", document.body)
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
val vocabularyMeanLength = if (large) LONG_DOCUMENT_MEAN_TOKENS else NORMAL_MEAN_LENGTH
val vocabulary = Vocabulary(random, maxOf(30, (3.5 * sqrt(options.documents.toDouble() * vocabularyMeanLength)).toInt()))
val corpus = Corpus()
val writer = Writer(random, vocabulary, corpus, large, meanLength)
val commandScript = CommandScript(random, writer, corpus, vocabulary, large)
commandScript.load(options.documents)
commandScript.run(options.queries)
System.out.write(commandScript.lines.joinToString("\n", postfix = "\n").encodeToByteArray())
System.out.flush()
