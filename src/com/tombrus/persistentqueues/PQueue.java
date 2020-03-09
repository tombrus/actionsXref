package com.tombrus.persistentqueues;

import static java.nio.file.StandardOpenOption.*;

import java.io.BufferedWriter;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.atomic.AtomicLong;
import java.util.stream.Collectors;

public class PQueue<T extends Savable> extends LinkedBlockingQueue<T> {
    private static final String RECREATE_DELIMITER = "#";

    private final Recreator<T> recreator;
    private final String       name;
    private final Path         persDir;

    public PQueue(Recreator<T> recreator, String name, Path persDir) {
        this(recreator, name, persDir, Integer.MAX_VALUE);
    }

    public PQueue(Recreator<T> recreator, String name, Path persDir, int capacity) {
        super(capacity);
        this.recreator = recreator;
        this.name = name;
        this.persDir = persDir;
        try {
            Files.createDirectories(persDir);
        } catch (IOException e) {
            throw new Error(e);
        }
    }

    long save() {
        try (BufferedWriter w = Files.newBufferedWriter(getMyFile(), WRITE, TRUNCATE_EXISTING, CREATE)) {
            AtomicLong numBytes = new AtomicLong();
            long count = stream()
                    .sorted()
                    .map(o -> o.save().stream()
                            .map(Object::toString)
                            .collect(Collectors.joining(RECREATE_DELIMITER, "", "\n")))
                    .peek(l -> numBytes.addAndGet(l.length() + 1))
                    .peek(line -> writeNoExc(w, line))
                    .count();
            System.err.printf("    WROTE  %20s: %9d bytes for %9d items\n", name, numBytes.get(), count);
            return numBytes.get();
        } catch (ClassCastException e) {
            // try unsorted save:
            try (BufferedWriter w = Files.newBufferedWriter(getMyFile(), WRITE, TRUNCATE_EXISTING, CREATE)) {
                AtomicLong numBytes = new AtomicLong();
                long count = stream()
                        .map(o -> o.save().stream()
                                .map(Object::toString)
                                .collect(Collectors.joining(RECREATE_DELIMITER, "", "\n")))
                        .peek(l -> numBytes.addAndGet(l.length() + 1))
                        .peek(line -> writeNoExc(w, line))
                        .count();
                System.err.printf("    WROTE  %20s: %9d bytes for %9d items\n", name, numBytes.get(), count);
                return numBytes.get();
            } catch (IOException ex) {
                ex.printStackTrace();
            }
        } catch (IOException e) {
            e.printStackTrace();
        }
        return 0L;
    }

    long restore() {
        Path myFile = getMyFile();
        if (Files.isRegularFile(myFile)) {
            try {
                AtomicLong numBytes = new AtomicLong();
                long count = Files.lines(myFile)
                        .peek(l -> numBytes.addAndGet(l.length() + 1))
                        .map(l -> Arrays.asList(l.split(RECREATE_DELIMITER)))
                        .map(recreator)
                        .peek(this::putNoExc)
                        .count();
                long bytes = numBytes.get();
                System.err.printf("    READ  %20s: %9d bytes in %9d items\n", name, bytes, count);
                return bytes;
            } catch (IOException e) {
                throw new Error(e);
            }
        }
        return 0;
    }

    private Path getMyFile() {
        return persDir.resolve(name);
    }

    private void writeNoExc(BufferedWriter w, String s) {
        try {
            w.write(s);
        } catch (IOException e) {
            throw new Error(e);
        }
    }

    public void putNoExc(T t) {
        try {
            put(t);
        } catch (InterruptedException e) {
            throw new Error(e);
        }
    }
}
