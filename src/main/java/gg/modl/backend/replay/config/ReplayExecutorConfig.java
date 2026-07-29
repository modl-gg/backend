package gg.modl.backend.replay.config;

import java.util.concurrent.ThreadPoolExecutor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;

@Configuration
public class ReplayExecutorConfig {

    public static final String TRAINING_SEGMENT_TASK_EXECUTOR = "trainingSegmentTaskExecutor";

    @Bean(name = TRAINING_SEGMENT_TASK_EXECUTOR, autowireCandidate = false)
    public ThreadPoolTaskExecutor trainingSegmentTaskExecutor(
        @Value("${modl.replay.training-segment-executor.core-pool-size:1}") int corePoolSize,
        @Value("${modl.replay.training-segment-executor.max-pool-size:2}") int maxPoolSize,
        @Value("${modl.replay.training-segment-executor.queue-capacity:50}") int queueCapacity,
        @Value("${modl.replay.training-segment-executor.await-termination-seconds:30}") int awaitTerminationSeconds
    ) {
        ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
        executor.setCorePoolSize(corePoolSize);
        executor.setMaxPoolSize(maxPoolSize);
        executor.setQueueCapacity(queueCapacity);
        executor.setThreadNamePrefix("training-segment-");
        executor.setRejectedExecutionHandler(new ThreadPoolExecutor.DiscardPolicy());
        executor.setWaitForTasksToCompleteOnShutdown(true);
        executor.setAwaitTerminationSeconds(awaitTerminationSeconds);
        return executor;
    }
}
