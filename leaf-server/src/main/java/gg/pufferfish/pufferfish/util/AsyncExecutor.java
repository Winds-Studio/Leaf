package gg.pufferfish.pufferfish.util;

import com.google.common.collect.Queues;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.util.Queue;
import java.util.concurrent.locks.Condition;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantLock;

public class AsyncExecutor implements Runnable {

    private final Logger LOGGER = LogManager.getLogger("Leaf");
    private final Queue<Runnable> jobs = Queues.newArrayDeque();
    private final Lock mutex = new ReentrantLock();
    private final Condition cond = mutex.newCondition();
    private final Thread thread;
    private volatile boolean killswitch = false;

    public AsyncExecutor(String threadName) {
        this.thread = new Thread(this, threadName);
    }

    public void start() {
        thread.start();
    }

    public void kill() {
        mutex.lock();
        try {
            killswitch = true;
            cond.signalAll();
        } finally {
            mutex.unlock();
        }
    }

    public void submit(Runnable runnable) {
        mutex.lock();
        try {
            jobs.offer(runnable);
            // Use signal() instead of signalAll() for better performance
            // since only one waiting thread needs to be woken up
            cond.signal();
        } finally {
            mutex.unlock();
        }
    }

    @Override
    public void run() {
        while (!killswitch) {
            try {
                Runnable runnable = takeRunnable();
                if (runnable != null) {
                    runnable.run();
                }
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            } catch (Exception e) {
                LOGGER.error("Failed to execute async job for thread {}", thread.getName(), e);
            }
        }
    }

    private Runnable takeRunnable() throws InterruptedException {
        mutex.lock();
        try {
            while (jobs.isEmpty() && !killswitch) {
                cond.await();
            }

            if (jobs.isEmpty()) return null; // We've set killswitch

            return jobs.remove();
        } finally {
            mutex.unlock();
        }
    }
}
