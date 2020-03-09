package com.tombrus.persistentqueues;

public class TimerThread extends Thread {
    private final Runnable step;
    private final int      millis;

    public TimerThread(String name, int millis, Runnable step) {
        super(name);
        this.millis = millis;
        this.step = step;
        setDaemon(true);
        start();
    }

    @Override
    public void run() {
        //noinspection InfiniteLoopStatement
        while (true) {
            sleep_(millis);
            step.run();
        }
    }

    public static void sleep_(int millis) {
        try {
            sleep(millis);
        } catch (InterruptedException e) {
            e.printStackTrace();
        }
    }
}
