package tracker.core;

import tracker.utils.AppManager;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.ThreadFactory;

public abstract class AsyncEvent<T> implements EventListener<T> {

    protected final ExecutorService executorService;
    //private final Runnable task;

    protected AsyncEvent(String eventName, int poolSize) {
        ThreadFactory threadFactory = new CoreThreadFactory(eventName);
        executorService = Executors.newFixedThreadPool(poolSize, threadFactory);
        //task = () -> pollEvents();
    }

//    @Override
//    public void onEvent(T t) {
//        executorService.submit(task);
//    }


    @Override
    public void open() {
        AppManager.add(this);
    }

    @Override
    public void close() {
        executorService.shutdownNow();
    }

    @Override
    public boolean isActive() {
        return !executorService.isShutdown();
    }
}
