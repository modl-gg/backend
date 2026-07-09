package gg.modl.backend.migration.config;

import java.util.concurrent.ThreadPoolExecutor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;

@Configuration
public class MigrationExecutorConfig {

    @Bean(autowireCandidate = false)
    public ThreadPoolTaskExecutor migrationTaskExecutor(
        @Value("${modl.migration.executor.core-pool-size:2}") int corePoolSize,
        @Value("${modl.migration.executor.max-pool-size:2}") int maxPoolSize,
        @Value("${modl.migration.executor.queue-capacity:4}") int queueCapacity,
        @Value("${modl.migration.executor.await-termination-seconds:30}") int awaitTerminationSeconds
    ) {
        ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
        executor.setCorePoolSize(corePoolSize);
        executor.setMaxPoolSize(maxPoolSize);
        executor.setQueueCapacity(queueCapacity);
        executor.setThreadNamePrefix("migration-");
        executor.setRejectedExecutionHandler(new ThreadPoolExecutor.AbortPolicy());
        executor.setWaitForTasksToCompleteOnShutdown(true);
        executor.setAwaitTerminationSeconds(awaitTerminationSeconds);
        return executor;
    }
}
