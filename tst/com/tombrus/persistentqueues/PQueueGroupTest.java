package com.tombrus.persistentqueues;

import static org.junit.jupiter.api.Assertions.*;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Collections;
import java.util.List;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class PQueueGroupTest {
    Path        dir;
    PQueueGroup g;
    private IntPQueue aaa;
    private IntPQueue bbb;

    @BeforeEach
    void setUp() throws IOException {
        dir = Files.createTempDirectory("PQueueGroupTest");
        g = new PQueueGroup(dir, 500);

        aaa = new IntPQueue("AAA", dir);
        bbb = new IntPQueue("BBB", dir);

        g.add(aaa);
        g.add(bbb);
    }

    @AfterEach
    void tearDown() throws IOException {
        if (g != null) {
            g.pause(true);
            Files.deleteIfExists(dir.resolve("AAA"));
            Files.deleteIfExists(dir.resolve("BBB"));
            Files.deleteIfExists(g.getDir());
        }
    }

    @Test
    void save() throws IOException, InterruptedException {
        g.carefullSave(true);
        assertTrue(Files.isDirectory(g.getDir()));
        assertEquals(0, Files.size(dir.resolve("AAA")));
        assertEquals(0, Files.size(dir.resolve("BBB")));
        TimerThread.sleep_(1000);
        assertEquals(0, Files.size(dir.resolve("AAA")));
        assertEquals(0, Files.size(dir.resolve("BBB")));
        aaa.put(new SavableInteger(88));
        aaa.put(new SavableInteger(44));
        aaa.put(new SavableInteger(22));
        g.carefullSave(true);
        assertEquals(9, Files.size(dir.resolve("AAA")));
        assertEquals(0, Files.size(dir.resolve("BBB")));
        bbb.put(new SavableInteger(333));
        bbb.put(new SavableInteger(666));
        bbb.put(new SavableInteger(999));
        g.carefullSave(true);
        assertEquals(9, Files.size(dir.resolve("AAA")));
        assertEquals(12, Files.size(dir.resolve("BBB")));
        aaa.put(new SavableInteger(88));
        bbb.put(new SavableInteger(333));
        TimerThread.sleep_(600);
        assertEquals(12, Files.size(dir.resolve("AAA")));
        assertEquals(16, Files.size(dir.resolve("BBB")));
    }

    static class SavableInteger implements Savable {
        int i;
        public static final Recreator<SavableInteger> RECREATOR = l -> new SavableInteger(Integer.parseUnsignedInt(l.get(0)));

        public SavableInteger(int i) {
            this.i = i;
        }

        @Override
        public List<?> save() {
            return Collections.singletonList(i);
        }
    }

    static class IntPQueue extends PQueue<SavableInteger> {
        public IntPQueue(String name, Path persDir) {
            super(SavableInteger.RECREATOR, name, persDir);
        }
    }
}