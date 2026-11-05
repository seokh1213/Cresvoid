import java.util.TreeMap
import kotlin.math.ln

enum class SearchMethod { BM25, VECTOR, RRF }
data class EvaluationQuery(val id: Int, val text: String, val vector: DoubleArray)
data class EvaluationRequest(val method: SearchMethod, val k: Int)
data class Metrics(val recall: Double, val reciprocalRank: Double, val ndcg: Double) {
    fun fields(): String = listOf(recall, reciprocalRank, ndcg).joinToString("\t", transform = ::fixed)
    operator fun minus(other: Metrics): Metrics =
        Metrics(recall - other.recall, reciprocalRank - other.reciprocalRank, ndcg - other.ndcg)
}
data class QueryMetrics(val id: Int, val metrics: Metrics) {
    fun row(): String = "$id\t${metrics.fields()}"
}

class Evaluator {
    private val queries = TreeMap<Int, EvaluationQuery>()
    private val judgments = HashMap<Int, MutableMap<Int, Int>>()

    fun put(query: EvaluationQuery) { queries[query.id] = query }
    fun query(id: Int): EvaluationQuery = queries[id] ?: fail("NOT_FOUND")
    fun allQueries(): List<EvaluationQuery> = queries.values.toList()

    fun judge(qid: Int, document: Int, grade: Int) {
        query(qid)
        judgments.getOrPut(qid) { mutableMapOf() }[document] = grade
    }

    fun evaluate(query: EvaluationQuery, ranking: List<SearchHit>, k: Int): QueryMetrics {
        val grades = judgments[query.id].orEmpty()
        val relevant = grades.filterValues { it > 0 }
        val documents = ranking.map { it.chunk.id.document }.distinct()
        val top = documents.take(k)
        val recall = if (relevant.isEmpty()) 0.0 else top.count { it in relevant }.toDouble() / relevant.size
        val first = documents.indexOfFirst { it in relevant }
        val rr = if (first < 0) 0.0 else 1.0 / (first + 1)
        val ideal = relevant.entries.sortedWith(compareByDescending<Map.Entry<Int, Int>> { it.value }
            .thenBy { it.key }).take(k).map { it.value }
        val idealGain = discountedGain(ideal)
        val actualGain = discountedGain(top.map { grades[it] ?: 0 })
        val ndcg = if (idealGain == 0.0) 0.0 else actualGain / idealGain
        return QueryMetrics(query.id, Metrics(recall, rr, ndcg))
    }
}

fun discountedGain(grades: List<Int>): Double {
    var sum = 0.0
    for ((index, grade) in grades.withIndex()) {
        val gain = (1 shl grade) - 1
        sum += gain / (ln(index + 2.0) / ln(2.0))
    }
    return sum
}

fun average(results: List<QueryMetrics>): Metrics {
    var recall = 0.0
    var rr = 0.0
    var ndcg = 0.0
    for (result in results) {
        recall += result.metrics.recall
        rr += result.metrics.reciprocalRank
        ndcg += result.metrics.ndcg
    }
    if (results.isEmpty()) return Metrics(0.0, 0.0, 0.0)
    return Metrics(recall / results.size, rr / results.size, ndcg / results.size)
}
