import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.Job
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlin.coroutines.cancellation.CancellationException

data class SeedPrice(val product: String, val value: Int, val writtenAt: Long)

enum class LookupSource { FETCHED, FRESH, STALE }

data class LookupValue(val value: Int, val source: LookupSource)

class LookupFailure(val code: Int) : RuntimeException("조회가 $code 로 실패했다")

interface LookupServices {
    fun now(): Long

    suspend fun fetch(product: String): Int
}

class SingleFlightLookup(
    private val owner: CoroutineScope,
    private val ttl: Long,
    private val services: LookupServices,
    initialCache: List<SeedPrice>,
) {
    private val mutex = Mutex()
    private val cache = initialCache.associate { it.product to CacheEntry(it.value, it.writtenAt + ttl) }.toMutableMap()
    private val flights = HashMap<String, Flight>()

    suspend fun lookup(product: String): LookupValue {
        val choice = mutex.withLock {
            val cached = cache[product]
            if (cached != null) {
                val source = if (services.now() < cached.expiresAt) LookupSource.FRESH else LookupSource.STALE
                if (source == LookupSource.STALE && product !in flights) startFlight(product, refresh = true)
                return@withLock Choice.Immediate(LookupValue(cached.value, source))
            }
            val flight = flights[product] ?: startFlight(product, refresh = false)
            flight.waiters++
            Choice.Wait(flight)
        }
        return when (choice) {
            is Choice.Immediate -> choice.value
            is Choice.Wait -> waitFor(product, choice.flight)
        }
    }

    private fun startFlight(product: String, refresh: Boolean): Flight {
        val flight = Flight(refresh)
        flight.job = owner.launch(start = CoroutineStart.LAZY) { fetchAndPublish(product, flight) }
        flights[product] = flight
        flight.job.start()
        return flight
    }

    private suspend fun waitFor(product: String, flight: Flight): LookupValue {
        try {
            return when (val outcome = flight.result.await()) {
                is FetchOutcome.Success -> LookupValue(outcome.value, LookupSource.FETCHED)
                is FetchOutcome.Failed -> throw LookupFailure(outcome.code)
            }
        } finally {
            withContext(NonCancellable) {
                mutex.withLock {
                    flight.waiters--
                    if (flight.waiters == 0 && !flight.refresh && flights[product] === flight) {
                        flights.remove(product)
                        flight.job.cancel()
                    }
                }
            }
        }
    }

    private suspend fun fetchAndPublish(product: String, flight: Flight) {
        try {
            val value = services.fetch(product)
            mutex.withLock {
                if (flights[product] === flight) {
                    cache[product] = CacheEntry(value, services.now() + ttl)
                    flights.remove(product)
                    flight.result.complete(FetchOutcome.Success(value))
                }
            }
        } catch (cancelled: CancellationException) {
            withContext(NonCancellable) {
                mutex.withLock {
                    if (flights[product] === flight) flights.remove(product)
                    flight.result.cancel(cancelled)
                }
            }
            throw cancelled
        } catch (failure: LookupFailure) {
            mutex.withLock {
                if (flights[product] === flight) {
                    flights.remove(product)
                    flight.result.complete(FetchOutcome.Failed(failure.code))
                }
            }
        }
    }

    private data class CacheEntry(val value: Int, val expiresAt: Long)

    private class Flight(val refresh: Boolean) {
        val result = CompletableDeferred<FetchOutcome>()
        lateinit var job: Job
        var waiters = 0
    }

    private sealed interface Choice {
        data class Immediate(val value: LookupValue) : Choice
        data class Wait(val flight: Flight) : Choice
    }

    private sealed interface FetchOutcome {
        data class Success(val value: Int) : FetchOutcome
        data class Failed(val code: Int) : FetchOutcome
    }
}
