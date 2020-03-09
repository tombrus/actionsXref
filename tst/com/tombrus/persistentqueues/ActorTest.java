package com.tombrus.persistentqueues;

import static com.tombrus.persistentqueues.TimerThread.*;
import static org.hamcrest.MatcherAssert.*;
import static org.hamcrest.Matchers.*;
import static org.junit.jupiter.api.Assertions.*;

import java.util.stream.IntStream;

import org.junit.jupiter.api.Test;

class ActorTest {
    private static final int MEASURING_SLOT_MS = 500;
    private static final int STEP_INTERVAL_MS  = 100;

    @Test
    void syncTest() {
        TestActor a = new TestActor("testactor");
        sleep_(MEASURING_SLOT_MS);

        assertEquals(0, a.getSteps());
        assertFalse(a.isAlive());

        IntStream.range(0, 4).forEach(i -> {
            System.err.println("\nUNPAUSED");
            {
                int s0 = a.getSteps();
                a.unpause(true);
                sleep_(MEASURING_SLOT_MS);
                int d = a.getSteps() - s0;
                assertTrue(Math.abs(d) - MEASURING_SLOT_MS / STEP_INTERVAL_MS <= 1);
                assertThat((double) (d), is(closeTo((double) MEASURING_SLOT_MS / STEP_INTERVAL_MS, 1d)));
            }
            {
                int s0 = a.getSteps();
                a.pause(true);
                int d = a.getSteps() - s0;
                assertTrue(Math.abs(d) <= 1, "s0=" + s0 + " d=" + d);
            }
            System.err.println("\nPAUSED");
            {
                int s0 = a.getSteps();
                sleep_(MEASURING_SLOT_MS);
                int d = a.getSteps() - s0;
                assertEquals(0, d);
            }
        });
    }

    @Test
    void asyncTest() {
        TestActor a = new TestActor("testactor");
        sleep_(MEASURING_SLOT_MS);

        assertEquals(0, a.getSteps());
        assertFalse(a.isAlive());

        IntStream.range(0, 4).forEach(i -> {
            System.err.println("\nUNPAUSED");
            {
                int s0 = a.getSteps();
                assertTrue(a.isPaused());
                a.unpause(false);
                assertTrue(a.isPaused());
                sleep_(MEASURING_SLOT_MS);
                int d = a.getSteps() - s0;
                assertTrue(Math.abs(d) - MEASURING_SLOT_MS / STEP_INTERVAL_MS <= 1);
                assertThat((double) (d), is(closeTo((double) MEASURING_SLOT_MS / STEP_INTERVAL_MS, 1d)));
            }
            {
                long t0 = System.currentTimeMillis();
                while (a.isPaused()) {
                    sleep_(1);
                }
                int d = (int) (System.currentTimeMillis() - t0);
                assertTrue(d < STEP_INTERVAL_MS);
                assertFalse(a.isPaused());
            }
            {
                int s0 = a.getSteps();
                a.pause(false);
                assertFalse(a.isPaused());
                int d = a.getSteps() - s0;
                assertTrue(Math.abs(d) <= 1, "s0=" + s0 + " d=" + d);
            }
            System.err.println("\nPAUSED");
            {
                long t0=System.currentTimeMillis();
                while (!a.isPaused()) {
                    sleep_(1);
                }
                int d= (int) (System.currentTimeMillis()-t0);
                assertTrue(d< STEP_INTERVAL_MS);
                assertTrue(a.isPaused());
            }
            {
                int s0 = a.getSteps();
                sleep_(MEASURING_SLOT_MS);
                int d = a.getSteps() - s0;
                assertTrue(a.isPaused());
                assertEquals(0, d);
            }
        });
    }

    static class TestActor extends Actor {
        public TestActor(String name) {
            super(name);
        }

        @Override
        public void step() {
            System.err.print('-');
            if (isPauseRequested()) {
                System.err.print('?');
            }
            System.err.flush();
            sleep_(STEP_INTERVAL_MS);
            System.err.print('_');
            if (isPauseRequested()) {
                System.err.print('$');
            }
            System.err.flush();
        }
    }
}