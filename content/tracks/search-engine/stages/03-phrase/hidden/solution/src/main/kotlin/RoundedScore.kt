import java.math.BigDecimal
import java.math.RoundingMode

@JvmInline
value class RoundedScore private constructor(private val value: BigDecimal) : Comparable<RoundedScore> {

    override fun compareTo(other: RoundedScore): Int = value.compareTo(other.value)

    override fun toString(): String = value.toPlainString()

    companion object {
        private const val DECIMAL_PLACES = 4

        // valueOf 와 "%.4f" 는 Double.toString 의 짧은 표기를 반올림해 결과가 달라질 수 있어 정확한 값을 옮기는 생성자를 쓴다
        fun of(score: Double): RoundedScore =
            RoundedScore(BigDecimal(score).setScale(DECIMAL_PLACES, RoundingMode.HALF_UP))
    }
}
