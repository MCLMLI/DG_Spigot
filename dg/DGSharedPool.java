package dg;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.atomic.AtomicInteger;

public class DGSharedPool {
    // For Java21 and later, you can use VT here, so we won't mark this as final.
    public static ExecutorService executorService = Executors.newCachedThreadPool(new ThreadFactory() {
        final AtomicInteger threadNumber = new AtomicInteger(1);

        @Override
        public Thread newThread(Runnable r) {
            Thread thread = new Thread(r, "DGSharedPool-Thread-" + threadNumber.getAndIncrement());
            thread.setDaemon(true); // Set the thread as a daemon thread
            return thread;
        }
    });
}
