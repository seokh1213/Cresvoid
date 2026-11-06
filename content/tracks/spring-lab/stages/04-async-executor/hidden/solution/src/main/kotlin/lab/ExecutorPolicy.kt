package lab

import java.util.concurrent.ThreadPoolExecutor
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor

fun tunedExecutor(): ThreadPoolTaskExecutor = ThreadPoolTaskExecutor().apply {
    corePoolSize = 2
    maxPoolSize = 6
    queueCapacity = 5
    setRejectedExecutionHandler(ThreadPoolExecutor.AbortPolicy())
}
