package lab

import java.util.concurrent.ThreadPoolExecutor
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor

fun tunedExecutor(): ThreadPoolTaskExecutor = ThreadPoolTaskExecutor().apply {
    corePoolSize = 4
    maxPoolSize = 8
    queueCapacity = Int.MAX_VALUE
    setRejectedExecutionHandler(ThreadPoolExecutor.AbortPolicy())
}
