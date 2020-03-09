package com.tombrus.persistentqueues;

import static com.tombrus.persistentqueues.TimerThread.*;

import java.util.concurrent.locks.Condition;

public abstract class Actor extends Thread {
    private final ReentrantLockPlus lock             = new ReentrantLockPlus();
    private final Condition         requestCondition = lock.newCondition();
    private final Condition         acceptCondition  = lock.newCondition();
    private       boolean           pauseRequested;
    private       boolean           unpauseRequested;
    private       boolean           paused           = true;
    private       int               steps;

    public Actor(String name) {
        super(name);
        setDaemon(true);
    }

    public abstract void step() throws InterruptedException;

    public boolean canProceed() {
        return true;
    }

    @Override
    public void run() {
        try {
            lock.lockAndRun(() -> switchPaused(false));
            //noinspection InfiniteLoopStatement
            while (true) {
                if (canProceed()) {
                    step();
                    steps++;
                } else {
                    sleep_(500);
                }

                lock.lockAndRun(() -> {
                    if (pauseRequested) {
                        switchPaused(true);
                        while (!unpauseRequested) {
                            requestCondition.await();
                        }
                        switchPaused(false);
                    }
                });
            }
        } catch (InterruptedException e) {
            e.printStackTrace();
        }
    }

    private void switchPaused(boolean newPaused) {
        pauseRequested = false;
        unpauseRequested = false;
        paused = newPaused;
        acceptCondition.signalAll();
    }

    public boolean isPauseRequested() {
        return pauseRequested;
    }

    public boolean isUnpauseRequested() {
        return unpauseRequested;
    }

    public boolean isPaused() {
        return paused;
    }

    public int getSteps() {
        return steps;
    }

    void pause(boolean sync) {
        if (!paused && !pauseRequested) {
            lock.lockAndRun(() -> {
                if (!isAlive()) {
                    start();
                }
                pauseRequested = true;
                requestCondition.signalAll();
                if (sync) {
                    while (!paused) {
                        acceptCondition.await();
                    }
                }
            });
        }
    }

    void unpause(boolean sync) {
        if (paused && !unpauseRequested) {
            lock.lockAndRun(() -> {
                if (!isAlive()) {
                    start();
                }
                unpauseRequested = true;
                requestCondition.signalAll();
                if (sync) {
                    while (paused) {
                        acceptCondition.await();
                    }
                }
            });
        }
    }
}

