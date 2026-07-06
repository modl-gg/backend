package gg.modl.backend.storage.config;

import java.util.concurrent.ThreadPoolExecutor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;

@Configuration
public class StorageExecutorConfig {

    public static final String STORAGE_TASK_EXECUTOR = "storageTaskExecutor";

    @Bean(name = STORAGE_TASK_EXECUTOR, autowireCandidate = false)
    public ThreadPoolTaskExecutor storageTaskExecutor(
        @Value("${modl.storage.executor.core-pool-size:2}") int corePoolSize,
        @Value("${modl.storage.executor.max-pool-size:4}") int maxPoolSize,
        @Value("${modl.storage.executor.queue-capacity:100}") int queueCapacity,
        @Value("${modl.storage.executor.await-termination-seconds:20}") int awaitTerminationSeconds
    ) {
        ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
        executor.setCorePoolSize(corePoolSize);
        executor.setMaxPoolSize(maxPoolSize);
        executor.setQueueCapacity(queueCapacity);
        executor.setThreadNamePrefix("storage-");
        executor.setRejectedExecutionHandler(new ThreadPoolExecutor.DiscardPolicy());
        executor.setWaitForTasksToCompleteOnShutdown(true);
        executor.setAwaitTerminationSeconds(awaitTerminationSeconds);
        return executor;
    }
}
