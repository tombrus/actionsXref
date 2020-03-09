package com.tombrus.persistentqueues;

import java.util.concurrent.TimeUnit;

public abstract class PQueueActor<T extends Savable> extends Actor {
    private final PQueue<T> q;

    public PQueueActor(String name, PQueue<T> q) {
        super(name);
        this.q = q;
        setDaemon(true);
    }

    @Override
    public boolean canProceed() {
        return !q.isEmpty();
    }

    @Override
    public void step() throws InterruptedException {
        T polled = q.poll(500, TimeUnit.MILLISECONDS);
        if (polled != null) {
            try {
                step(polled);
            } catch (Throwable t) {
                q.put(polled);
                System.err.println("ERROR: put back in queue (" + polled + ") because of " + t.getMessage());
            }
        }
    }

    public abstract void step(T t);
}
