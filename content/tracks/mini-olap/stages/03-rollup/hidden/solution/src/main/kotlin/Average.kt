import kotlin.math.absoluteValue

fun averageHundredths(sum: Long, count: Long): Long {
    // 합의 절댓값은 행 10,000,000개 × 1,000,000,000 이하라 100 을 곱해도 Long 에 담긴다
    val scaled = sum.absoluteValue * 100
    val quotient = scaled / count
    val roundedAwayFromZero = if (scaled % count * 2 >= count) quotient + 1 else quotient
    return if (sum < 0) -roundedAwayFromZero else roundedAwayFromZero
}

fun renderHundredths(hundredths: Long): String {
    val sign = if (hundredths < 0) "-" else ""
    val magnitude = hundredths.absoluteValue
    return "$sign${magnitude / 100}.${(magnitude % 100).toString().padStart(2, '0')}"
}
