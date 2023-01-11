package tracker.core;

import com.fasterxml.jackson.annotation.JsonIgnore;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import tracker.utils.AppManager;

import java.util.Queue;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicBoolean;

public abstract class ConcurrentEvent<T> implements EventListener<T> {

    private final static Logger LOG = LoggerFactory.getLogger(ConcurrentEvent.class);

    private final AtomicBoolean processing;
    private final Runnable task;
    protected final Queue<T> concurrentQueue;
    protected final ExecutorService executorService;
    protected long start;

    protected ConcurrentEvent(String eventName) {
        processing = new AtomicBoolean();
        ThreadFactory threadFactory = new CoreThreadFactory(eventName);
        executorService = Executors.newSingleThreadExecutor(threadFactory);
        concurrentQueue = new ConcurrentLinkedDeque<>();
        task = () -> pollEvents();
    }

    @Override
    public void onEvent(T t) {
        concurrentQueue.offer(t);
        if (processing.compareAndSet(false, true)) {
            try {
                executorService.submit(task);
            } catch (Exception e) {
                LOG.error("RejectedExecutionException for thread id/name: {} / {}, exception {}", Thread.currentThread().getId(), Thread.currentThread().getName(), e);
                processing.set(false);
                onEvent(t);
            }
        }
    }

    @Override
    public void pollEvents() {
        try {
            start = System.currentTimeMillis();
            while (!concurrentQueue.isEmpty()) {
                process(concurrentQueue.poll());
            }
        } catch (Throwable t){
            LOG.error("Error processing Block event: ", t);
        } finally {
            processing.set(false);
        }
        if(!concurrentQueue.isEmpty()) LOG.warn("Missed event(s) during processing, remaining events {}", concurrentQueue.size());
    }

    protected abstract void process(T t);

    @Override
    public void open() {
        AppManager.add(this);
    }

    @Override
    public void close() {
        concurrentQueue.clear();
        executorService.shutdownNow();
    }

    @Override
    @JsonIgnore
    public boolean isActive() {
        return !executorService.isShutdown();
    }
}
