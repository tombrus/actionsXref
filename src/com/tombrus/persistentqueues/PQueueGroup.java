package com.tombrus.persistentqueues;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.List;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.stream.Stream;

public class PQueueGroup {
    private final List<PQueue<?>> queues = new ArrayList<>();
    private final List<Actor>     actors = new ArrayList<>();
    private final Path            dir;
    private       int             queueHashAtSave;
    private       long            lastRestoreMs;
    private       long            lastSaveMs;
    private       long            diskSpace;

    public PQueueGroup(Path dir, int saveIntervalSec) {
        this.dir = dir;
        if (30 < saveIntervalSec) {
            new TimerThread("saver", saveIntervalSec * 1000, () -> carefullSave(false));
        }
    }

    public <T extends Savable> PQueue<T> add(PQueue<T> q) {
        queues.add(q);
        return q;
    }

    public void add(Actor actor) {
        actors.add(actor);
    }

    public int getQueueHash() {
        return Arrays.hashCode(queues.stream().mapToInt(LinkedBlockingQueue::size).toArray());
    }

    public boolean isPaused() {
        return actors.stream().allMatch(Actor::isPaused);
    }

    public boolean isWaitingForPaused() {
        return actors.stream().anyMatch(Actor::isPauseRequested);
    }

    public boolean isWaitingForUnpaused() {
        return actors.stream().anyMatch(Actor::isUnpauseRequested);
    }

    public Path getDir() {
        return dir;
    }

    public Stream<?> getAll() {
        return queues.stream().flatMap(Collection::stream);
    }

    public void pause(boolean sync) {
        actors.forEach(a -> a.pause(sync));
    }

    public void unpause(boolean sync) {
        actors.forEach(a -> a.unpause(sync));
    }

    public long getLastRestoreMs() {
        return lastRestoreMs;
    }

    public long getLastSaveMs() {
        return lastSaveMs;
    }

    public long getDiskSpace() {
        return diskSpace;
    }

    public int size() {
        return queues.stream().mapToInt(LinkedBlockingQueue::size).sum();
    }

    public void carefullSave(boolean forced) {
        if (forced || getQueueHash() != queueHashAtSave) {
            long    t0        = System.currentTimeMillis();
            boolean wasPaused = isPaused();
            if (!wasPaused) {
                System.err.println("@@@ pause for save...");
                pause(true);
            }
            save();
            if (!wasPaused) {
                unpause(true);
                System.err.println("@@@ unpause after save....");
            }
            long t1 = System.currentTimeMillis();
            System.err.println("@@@ save with pause took " + (t1 - t0) + " ms");
        }
    }

    public void save() {
        System.err.println("@@@ save...");
        long t00 = System.currentTimeMillis();
        actualSave();
        long t01 = System.currentTimeMillis();
        lastSaveMs = t01 - t00;
        queueHashAtSave = getQueueHash();
        System.err.println("@@@ save done, took " + lastSaveMs + " ms");
    }

    protected void actualSave() {
        diskSpace = queues.stream().mapToLong(PQueue::save).sum();
    }

    public void restore() {
        long t0 = System.currentTimeMillis();
        System.err.println("@@@ restoreing...");
        diskSpace = queues.stream().mapToLong(PQueue::restore).sum();
        long t1 = System.currentTimeMillis();
        lastRestoreMs = t1 - t0;
        System.err.println("@@@ restore done, took " + lastRestoreMs + " ms");
        queueHashAtSave = getQueueHash();
    }
}
