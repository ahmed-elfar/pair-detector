package tracker.core;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import tracker.utils.AppManager;

import java.util.concurrent.*;

public abstract class BlockingEvent<T> extends Thread implements EventListener <T> {

    protected final static Logger LOG = LoggerFactory.getLogger(BlockingEvent.class);

    protected final BlockingQueue<T> blockingQueue;
    private volatile boolean stopped;

    protected BlockingEvent(String eventName) {
        super((System.getSecurityManager() != null) ? System.getSecurityManager().getThreadGroup() : Thread.currentThread().getThreadGroup(),
                eventName);
        blockingQueue = new LinkedBlockingQueue<>(4);
    }

    @Override
    public void run(){
        pollEvents();
    }

    @Override
    public void onEvent(T t) {
        blockingQueue.offer(t);
    }

    @Override
    public void pollEvents() {
        for (;;) {
            try {
                T t = blockingQueue.take();
                process(t);
            } catch (InterruptedException e) {
                if(stopped) {
                    LOG.info("Stopped by App Manager.");
                    break;
                } else {
                    LOG.warn("Unexpected Interrupt for thread id/name: {} / {}", this.getId(), this.getName());
                }
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
        this.interrupt();
        blockingQueue.clear();
    }

    @Override
    public boolean isActive() {
        return !stopped;
    }
}
