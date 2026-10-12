import java.math.BigDecimal

class Extrapolation private constructor(private val extended: Long, private val sampled: Long) {
    fun extrapolate(change: Long): BigDecimal =
        roundedQuotient(BigDecimal.valueOf(change).multiply(BigDecimal.valueOf(extended)), BigDecimal.valueOf(sampled))

    companion object {
        // 평균 간격의 절반도 정수가 되도록 길이를 모두 2(n − 1) 배 해서 다룬다
        fun of(window: RangeWindow, firstTime: Long, lastTime: Long, sampleCount: Int): Extrapolation {
            val intervals = sampleCount - 1L
            val sampled = lastTime - firstTime
            val startExtension = scaledExtension(firstTime - window.start, sampled, intervals)
            val endExtension = scaledExtension(window.end - lastTime, sampled, intervals)
            val scaledSampled = sampled * 2 * intervals
            return Extrapolation(scaledSampled + startExtension + endExtension, scaledSampled)
        }

        // gap < 1.1 × sampled ÷ intervals 이면 gap 만큼, 아니면 평균 간격의 절반만큼 늘린다
        private fun scaledExtension(gap: Long, sampled: Long, intervals: Long): Long =
            if (gap * intervals * 10 < sampled * 11) gap * 2 * intervals else sampled
    }
}
