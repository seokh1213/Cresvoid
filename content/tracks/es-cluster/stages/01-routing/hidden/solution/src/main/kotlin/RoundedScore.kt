import java.math.BigDecimal
import java.math.RoundingMode
import kotlin.math.abs
import kotlin.math.floor
import kotlin.math.roundToLong

@JvmInline
value class RoundedScore private constructor(private val tenThousandths: Long) : Comparable<RoundedScore> {

    override fun compareTo(other: RoundedScore): Int = tenThousandths.compareTo(other.tenThousandths)

    override fun toString(): String =
        "${tenThousandths / SCALE}.${(tenThousandths % SCALE).toString().padStart(DECIMAL_PLACES, '0')}"

    companion object {
        private const val DECIMAL_PLACES = 4
        private const val SCALE = 10_000L
        private const val TIE_MARGIN = 1e-6

        val ONE = RoundedScore(SCALE)

        fun of(score: Double): RoundedScore {
            val scaled = score * SCALE
            // 곱셈의 반올림 오차가 결과를 바꿀 수 있는 반올림 경계 근처만 double 의 정확한 이진값으로 다시 계산한다
            if (abs(scaled - floor(scaled) - 0.5) > TIE_MARGIN) return RoundedScore(scaled.roundToLong())
            return RoundedScore(BigDecimal(score).setScale(DECIMAL_PLACES, RoundingMode.HALF_UP).unscaledValue().toLong())
        }
    }
}
