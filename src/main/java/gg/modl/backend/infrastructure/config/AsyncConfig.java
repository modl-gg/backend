package gg.modl.backend.infrastructure.config;

import java.util.concurrent.Executors;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.task.AsyncTaskExecutor;
import org.springframework.core.task.support.TaskExecutorAdapter;
import org.springframework.scheduling.annotation.EnableAsync;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;

@Configuration
@EnableAsync
@EnableScheduling
@Slf4j
public class AsyncConfig {

    @Bean
    public AsyncTaskExecutor applicationTaskExecutor() {
        return new TaskExecutorAdapter(Executors.newVirtualThreadPerTaskExecutor());
    }

    @Bean(autowireCandidate = false)
    public ThreadPoolTaskExecutor emailTaskExecutor(
        @Value("${modl.email.executor.core-pool-size:2}") int corePoolSize,
        @Value("${modl.email.executor.max-pool-size:8}") int maxPoolSize,
        @Value("${modl.email.executor.queue-capacity:500}") int queueCapacity,
        @Value("${modl.email.executor.await-termination-seconds:20}") int awaitTerminationSeconds
    ) {
        ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
        executor.setCorePoolSize(corePoolSize);
        executor.setMaxPoolSize(maxPoolSize);
        executor.setQueueCapacity(queueCapacity);
        executor.setThreadNamePrefix("email-");
        executor.setRejectedExecutionHandler((task, rejectingExecutor) ->
            log.warn("Email executor saturated; dropping queued email send"));
        executor.setWaitForTasksToCompleteOnShutdown(true);
        executor.setAwaitTerminationSeconds(awaitTerminationSeconds);
        return executor;
    }
}
