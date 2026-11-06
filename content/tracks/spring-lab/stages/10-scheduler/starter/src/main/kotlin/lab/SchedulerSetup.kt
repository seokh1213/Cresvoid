package lab

import org.springframework.context.annotation.AnnotationConfigApplicationContext
import org.springframework.context.annotation.Configuration
import org.springframework.scheduling.annotation.EnableScheduling
import org.springframework.scheduling.concurrent.ThreadPoolTaskScheduler
import java.util.function.Supplier

@Configuration(proxyBeanMethods = false)
@EnableScheduling
class SchedulingConfig

fun openRun(mode: String, probe: ScheduledProbe): AnnotationConfigApplicationContext {
    val context = AnnotationConfigApplicationContext()
    context.register(SchedulingConfig::class.java)
    context.registerBean(ScheduledProbe::class.java, Supplier { probe })
    if (mode == "comparison") {
        context.registerBean("taskScheduler", ThreadPoolTaskScheduler::class.java, Supplier {
            ThreadPoolTaskScheduler().apply {
                poolSize = 1
                setThreadNamePrefix("comparison-")
            }
        })
    }
    context.refresh()
    return context
}
