package tracker.utils;

import io.reactivex.disposables.Disposable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import tracker.core.IsProcess;

import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class AppManager {

    protected static final Logger LOG = LoggerFactory.getLogger(AppManager.class);

    private static final long WATCH_INTERVAL = 60000;

    private static final ExecutorService EXECUTOR_SERVICE = Executors.newCachedThreadPool();
    private static final List<Disposable> disposables = new CopyOnWriteArrayList<>();
    private static final List<IsProcess> processList = new CopyOnWriteArrayList<>();

    public static void add(Disposable disposable) {
        EXECUTOR_SERVICE.submit(() -> {
            if(!disposables.contains(disposable)) disposables.add(disposable);
        });
    }

    public static void unTrack(Disposable disposable) {
        EXECUTOR_SERVICE.submit(() -> {
            processList.remove(disposable);
            if(!disposable.isDisposed()) disposable.dispose();
        });
    }

    public static void add(IsProcess process) {
        EXECUTOR_SERVICE.submit(() -> processList.add(process));
    }

    public static void start() {
        Thread watcher = new Thread( () -> {
            for (;;) {
                try {
                    Thread.sleep(WATCH_INTERVAL);
                } catch (InterruptedException e) {
                    e.printStackTrace();
                    return;
                }
                StringBuilder report = new StringBuilder(System.lineSeparator());
                boolean [] shouldReport = {false};
                int [] disposedCounter = {0};
                disposables.forEach(disposable -> {
                    if(disposable.isDisposed()) {
                        disposedCounter[0]++;
                        shouldReport[0] = true;
                        report.append(disposable)
                                .append(" : is Disposed.")
                                .append(System.lineSeparator());
                    }
                } );
                report.append("Disposed -> ")
                        .append(disposedCounter[0])
                        .append(System.lineSeparator());
                int [] inActiveCounter = {0};
                processList.forEach(process -> {
                    if(!process.isActive()){
                        inActiveCounter[0]++;
                        shouldReport[0] = true;
                        report.append(process);
                        report.append(" : is inactive.\n");
                    }
                } );
                report.append("Inactive Processes -> ")
                        .append(inActiveCounter[0])
                        .append(System.lineSeparator());
                if(shouldReport [0]) {
                    report.append("Total Active Listeners = ")
                            .append(disposables.size() - disposedCounter[0])
                            .append(System.lineSeparator())
                            .append("Total Active Processes = ")
                            .append(processList.size() - inActiveCounter[0])
                            .append(System.lineSeparator());
                    LOG.warn("AppManager Report: {}", report.toString());
                }
            }
        });
        watcher.setName("AppManager-Thread");
        watcher.start();
        Thread shutDownThread = new Thread( () -> {
            LOG.info("AppManager -> SHUTDOWN HOOK CALLED");
            watcher.interrupt();
            disposables.forEach(disposables -> {
                try {
                    if(!disposables.isDisposed()) disposables.dispose();
                } catch (Throwable t) {
                    LOG.warn("", t);
                }
            });
            processList.forEach(process -> {
                try{
                    process.close();
                }catch (Throwable t) {
                    LOG.warn("Safe Shutdown : ", t);
                }
            });
        });
        shutDownThread.setName("App-Shutdown-Thread.");
        Runtime.getRuntime().addShutdownHook(shutDownThread);
    }
}
