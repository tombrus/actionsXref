package com.tombrus.persistentqueues;

import java.util.concurrent.locks.ReentrantLock;

public class ReentrantLockPlus extends ReentrantLock {
    public interface RunnableExc {
        void run() throws InterruptedException;
    }

    public void lockAndRun(RunnableExc r) {
        lock();
        try {
            r.run();
        } catch (InterruptedException e) {
            handleInterrptedException(e);
        } finally {
            unlock();
        }
    }

    private void handleInterrptedException(InterruptedException e) {
        e.printStackTrace();
    }
}
