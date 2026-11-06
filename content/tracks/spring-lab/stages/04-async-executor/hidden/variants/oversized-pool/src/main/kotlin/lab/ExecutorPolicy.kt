package lab

import java.util.concurrent.ThreadPoolExecutor
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor

fun tunedExecutor(): ThreadPoolTaskExecutor = ThreadPoolTaskExecutor().apply {
    corePoolSize = 16
    maxPoolSize = 16
    queueCapacity = 8
    setRejectedExecutionHandler(ThreadPoolExecutor.AbortPolicy())
}
