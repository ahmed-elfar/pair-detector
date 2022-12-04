package tracker.core;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import tracker.utils.AppManager;

import java.util.concurrent.*;

public abstract class BlockingEventWithExecutorService<T> extends Thread implements EventListener <T> {

    protected final static Logger LOG = LoggerFactory.getLogger(BlockingEventWithExecutorService.class);

    protected final BlockingDeque<T> blockingDeque;
    protected final ExecutorService executorService;
    private volatile boolean stopped;

    protected BlockingEventWithExecutorService(String eventName, int poolSize) {
        super((System.getSecurityManager() != null) ? System.getSecurityManager().getThreadGroup() : Thread.currentThread().getThreadGroup(),
                eventName);
        ThreadFactory threadFactory = new CoreThreadFactory(eventName);
        executorService = Executors.newFixedThreadPool(poolSize, threadFactory);
        blockingDeque = new LinkedBlockingDeque<>();
    }

    @Override
    public void run(){
        pollEvents();
    }

    @Override
    public void onEvent(T t) {
        blockingDeque.offer(t);
    }

    @Override
    public void pollEvents() {
        for (;;) {
            try {
                T t = blockingDeque.take();
                process(t);
            } catch (InterruptedException e) {
                if(stopped) {
                    LOG.info("Stopped by App Manager.");
                    break;
                } else {
                    LOG.warn("Unexpected Interrupt for thread id/name: {} / {}", this.getId(), this.getName());
                }
            } catch (Throwable t) {
                LOG.error("Error during execution : ", t);
            }
        }
    }

    protected abstract void process (T t);

    @Override
    public void open() {
        AppManager.add(this);
        this.start();
    }

    @Override
    public void close() {
        stopped = true;
        blockingDeque.clear();
        executorService.shutdownNow();
        this.interrupt();
    }

    @Override
    public boolean isActive() {
        return !stopped;
    }
}
