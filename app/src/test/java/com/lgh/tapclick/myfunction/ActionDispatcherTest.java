package com.lgh.tapclick.myfunction;

import static org.junit.Assert.*;

import org.junit.Test;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.function.Consumer;

public class ActionDispatcherTest {
    @Test
    public void secondActionWaitsForCompletionAndCancelledActionRetries() {
        Clock clock = new Clock();
        ActionDispatcher dispatcher = new ActionDispatcher(clock);
        List<Consumer<Boolean>> calls = new ArrayList<>();
        List<Boolean> results = new ArrayList<>();
        dispatcher.submit(calls::add, () -> true, () -> true, false, results::add);
        dispatcher.submit(calls::add, () -> true, () -> true, false, results::add);
        assertEquals(1, calls.size());
        calls.get(0).accept(false);
        assertTrue(results.isEmpty());
        clock.advance(100);
        assertEquals(2, calls.size());
        calls.get(1).accept(true);
        clock.advance(0);
        assertEquals(3, calls.size());
        calls.get(2).accept(true);
        assertEquals(java.util.Arrays.asList(true, true), results);
    }

    @Test
    public void retriesAreBoundedAndDuplicateCallbacksIgnored() {
        Clock clock = new Clock();
        ActionDispatcher dispatcher = new ActionDispatcher(clock);
        List<Consumer<Boolean>> calls = new ArrayList<>();
        List<Boolean> results = new ArrayList<>();
        dispatcher.submit(calls::add, () -> true, () -> true, false, results::add);
        for (int attempt = 0; attempt < 3; attempt++) {
            calls.get(attempt).accept(false);
            calls.get(attempt).accept(true);
            clock.advance(100);
        }
        assertEquals(3, calls.size());
        assertEquals(java.util.Collections.singletonList(false), results);
    }

    @Test
    public void manualReplayBypassesPausedAutomaticQueueButNeverInterruptsActiveGesture() {
        Clock clock = new Clock();
        ActionDispatcher dispatcher = new ActionDispatcher(clock);
        List<String> order = new ArrayList<>();
        List<Consumer<Boolean>> calls = new ArrayList<>();
        dispatcher.submit(done -> { order.add("active"); calls.add(done); },
                () -> true, () -> true, false, success -> { });
        dispatcher.submit(done -> { order.add("paused"); calls.add(done); },
                () -> true, () -> false, false, success -> { });
        dispatcher.submit(done -> { order.add("manual"); calls.add(done); },
                () -> true, () -> true, true, success -> { });
        assertEquals(java.util.Collections.singletonList("active"), order);
        calls.get(0).accept(true);
        clock.advance(0);
        assertEquals(java.util.Arrays.asList("active", "manual"), order);
    }

    @Test
    public void changedPageDiscardsPendingActionAndCloseRejectsLateCallback() {
        Clock clock = new Clock();
        ActionDispatcher dispatcher = new ActionDispatcher(clock);
        boolean[] pageValid = {true};
        List<Consumer<Boolean>> calls = new ArrayList<>();
        List<Boolean> results = new ArrayList<>();
        dispatcher.submit(calls::add, () -> true, () -> true, false, results::add);
        dispatcher.submit(calls::add, () -> pageValid[0], () -> true, false, results::add);
        pageValid[0] = false;
        calls.get(0).accept(true);
        clock.advance(0);
        assertEquals(1, calls.size());
        assertEquals(java.util.Arrays.asList(true, false), results);
        dispatcher.submit(calls::add, () -> true, () -> true, false, results::add);
        dispatcher.close();
        calls.get(1).accept(true);
        clock.advance(2000);
        assertEquals(java.util.Arrays.asList(true, false, false), results);
    }

    @Test
    public void missingCallbackTimesOutWithoutBlindlyRepeatingPotentiallySuccessfulTap() {
        Clock clock = new Clock();
        ActionDispatcher dispatcher = new ActionDispatcher(clock);
        List<Consumer<Boolean>> calls = new ArrayList<>();
        List<Boolean> results = new ArrayList<>();
        dispatcher.submit(calls::add, () -> true, () -> true, false, results::add);
        clock.advance(1500);
        assertEquals(1, calls.size());
        assertEquals(java.util.Collections.singletonList(false), results);
        calls.get(0).accept(true);
        assertEquals(1, results.size());
    }

    @Test
    public void pausedActionHasFiniteDeadline() {
        Clock clock = new Clock();
        ActionDispatcher dispatcher = new ActionDispatcher(clock);
        List<Boolean> results = new ArrayList<>();
        dispatcher.submit(done -> fail("Must not dispatch during a real touch"),
                () -> true, () -> false, false, results::add);
        clock.advance(1500);
        assertEquals(java.util.Collections.singletonList(false), results);
    }

    private static final class Clock implements ActionDispatcher.Host {
        long now;
        final List<Task> tasks = new ArrayList<>();
        public long now() { return now; }
        public void post(Runnable action, long delay) { tasks.add(new Task(now + delay, action)); }
        public void remove(Runnable action) { tasks.removeIf(task -> task.action == action); }
        void advance(long millis) {
            long until = now + millis;
            while (true) {
                tasks.sort(Comparator.comparingLong(task -> task.time));
                if (tasks.isEmpty() || tasks.get(0).time > until) { break; }
                Task task = tasks.remove(0);
                now = task.time;
                task.action.run();
            }
            now = until;
        }
    }

    private static final class Task {
        final long time;
        final Runnable action;
        Task(long time, Runnable action) { this.time = time; this.action = action; }
    }
}
