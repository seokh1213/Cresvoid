import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope

suspend fun loadProductDetail(services: DetailServices, limits: DetailLimits): DetailResult =
    try {
        coroutineScope {
            val price = async {
                beforeDeadline(limits.deadline) { callWithin(limits.priceTimeout) { services.price() } }
                    .requireValue(onError = FailureReason.PRICE_ERROR, onTimeout = FailureReason.PRICE_TIMEOUT)
            }
            val stock = async {
                beforeDeadline(limits.deadline) { callWithin(limits.stockTimeout) { services.stock() } }
                    .requireValue(onError = FailureReason.STOCK_ERROR, onTimeout = FailureReason.STOCK_TIMEOUT)
            }
            val review = async {
                beforeDeadline(limits.deadline) { callWithin(limits.reviewTimeout) { services.review() } }
                    .orDefault()
            }
            // 쿠폰도 시각 0 부터 마감 시각을 재야 가격을 기다린 시간이 마감 시각에 들어간다.
            val coupon = async {
                beforeDeadline(limits.deadline) {
                    val listPrice = price.await()
                    callWithin(limits.couponTimeout) { services.coupon(listPrice) }
                }.orDefault()
            }
            DetailResult.Loaded(
                price = price.await(),
                stock = stock.await(),
                review = review.await(),
                coupon = coupon.await(),
            )
        }
    } catch (failure: RequiredCallFailure) {
        DetailResult.Failed(failure.reason)
    }
