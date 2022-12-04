package tracker.core;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import tracker.utils.AppManager;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.ThreadFactory;

public class AsyncTaskManager implements IsProcess {


    private final static Logger LOG = LoggerFactory.getLogger(AsyncTaskManager.class);
    private static final int TASKS = 400;
    private final ExecutorService executorService;


    public final static AsyncTaskManager asyncTaskManager = new AsyncTaskManager();

    private AsyncTaskManager() {
        ThreadFactory threadFactory = new CoreThreadFactory("Shared-events-Pool");
        executorService = Executors.newFixedThreadPool(TASKS, threadFactory);
        open();
    }

    public void execute(Runnable task) {
        executorService.submit(task);
    }

    @Override
    public void open() {
        for (int i = 0; i < TASKS / 2; i++) {
            executorService.submit(() -> {});
        }
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
