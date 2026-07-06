package gg.modl.backend.realtime.dispatch;

import gg.modl.backend.realtime.config.RealtimeProperties;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import jakarta.annotation.PreDestroy;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import org.springframework.stereotype.Component;

@Component
public class RealtimeDispatchExecutor {
    private static final String DROPPED_EVENTS_COUNTER = "modl.realtime.dispatch.dropped";

    private final MeterRegistry meterRegistry;
    private final ExecutorService[] workers;

    public RealtimeDispatchExecutor(RealtimeProperties properties, MeterRegistry meterRegistry) {
        this.meterRegistry = meterRegistry;
        this.workers = createWorkers(properties.getDispatchWorkers(), properties.getDispatchQueueCapacity());
    }

    public void execute(String serverId, Runnable task) {
        workers[Math.floorMod(serverId.hashCode(), workers.length)].execute(task);
    }

    private ExecutorService[] createWorkers(int workerCount, int queueCapacity) {
        ExecutorService[] created = new ExecutorService[workerCount];
        for (int index = 0; index < workerCount; index++) {
            created[index] = createWorker(index, queueCapacity);
        }
        return created;
    }

    private ExecutorService createWorker(int workerIndex, int queueCapacity) {
        Counter dropped = Counter.builder(DROPPED_EVENTS_COUNTER)
            .tag("worker", Integer.toString(workerIndex))
            .register(meterRegistry);
        return new ThreadPoolExecutor(
            1,
            1,
            0L,
            TimeUnit.MILLISECONDS,
            new ArrayBlockingQueue<>(queueCapacity),
            daemonThreadFactory(workerIndex),
            (rejected, executor) -> dropped.increment()
        );
    }

    private ThreadFactory daemonThreadFactory(int workerIndex) {
        return runnable -> {
            Thread thread = new Thread(runnable, "realtime-dispatch-" + workerIndex);
            thread.setDaemon(true);
            return thread;
        };
    }

    @PreDestroy
    public void shutdown() {
        for (ExecutorService worker : workers) {
            worker.shutdownNow();
        }
    }
}
