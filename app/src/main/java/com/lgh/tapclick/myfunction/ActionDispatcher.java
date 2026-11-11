package com.lgh.tapclick.myfunction;

import java.util.ArrayDeque;
import java.util.function.BooleanSupplier;
import java.util.function.Consumer;

/** Main-thread action queue: never overlap gestures and never commit a cancelled action. */
final class ActionDispatcher {
    interface Host {
        long now();
        void post(Runnable action, long delay);
        void remove(Runnable action);
    }

    interface Action {
        void start(Completion completed);
    }

    interface Completion extends Consumer<Boolean> {
        boolean isActive();
    }

    private static final int MAX_PENDING = 32;
    private static final long TIMEOUT_MILLIS = 1500;
    private final Host host;
    private final ArrayDeque<Request> pending = new ArrayDeque<>();
    private volatile Request active;
    private volatile long attempt;
    private volatile boolean closed;
    private Runnable scheduled;

    ActionDispatcher(Host host) {
        this.host = host;
    }

    void submit(Action action, BooleanSupplier valid, BooleanSupplier ready,
                boolean manual, Consumer<Boolean> completed) {
        if (closed || pending.size() >= MAX_PENDING) {
            completed.accept(false);
            return;
        }
        Request request = new Request(action, valid, ready, completed, manual ? 0 : 2,
                host.now() + TIMEOUT_MILLIS);
        if (manual) {
            pending.addFirst(request);
        } else {
            pending.addLast(request);
        }
        pump();
    }

    void close() {
        closed = true;
        attempt++;
        if (scheduled != null) {
            host.remove(scheduled);
        }
        if (active != null) {
            Request request = active;
            active = null;
            request.completed.accept(false);
        }
        while (!pending.isEmpty()) {
            pending.removeFirst().completed.accept(false);
        }
    }

    private void pump() {
        if (closed || active != null) {
            return;
        }
        if (scheduled != null) {
            host.remove(scheduled);
            scheduled = null;
        }
        int count = pending.size();
        while (count-- > 0 && !pending.isEmpty()) {
            Request request = pending.removeFirst();
            if (!request.valid.getAsBoolean() || host.now() >= request.deadline) {
                request.completed.accept(false);
                continue;
            }
            if (!request.ready.getAsBoolean()) {
                pending.addLast(request);
                continue;
            }
            active = request;
            long token = ++attempt;
            scheduled = () -> finish(request, token, false, false);
            host.post(scheduled, TIMEOUT_MILLIS);
            try {
                request.action.start(new Completion() {
                    public boolean isActive() {
                        return !closed && active == request && token == attempt;
                    }
                    public void accept(Boolean success) {
                        finish(request, token, success, true);
                    }
                });
            } catch (RuntimeException exception) {
                finish(request, token, false, true);
            }
            return;
        }
        if (!pending.isEmpty()) {
            scheduled = this::pump;
            host.post(scheduled, 50);
        }
    }

    private void finish(Request request, long token, boolean success, boolean allowRetry) {
        if (closed || active != request || token != attempt) {
            return;
        }
        host.remove(scheduled);
        scheduled = null;
        active = null;
        attempt++; // Ignore duplicate/late callbacks from the finished attempt.
        if (!success && allowRetry && request.retries-- > 0 && request.valid.getAsBoolean()) {
            pending.addFirst(request);
            scheduled = this::pump;
            host.post(scheduled, 100);
        } else {
            request.completed.accept(success);
            scheduled = this::pump;
            host.post(scheduled, 0);
        }
    }

    private static final class Request {
        final Action action;
        final BooleanSupplier valid;
        final BooleanSupplier ready;
        final Consumer<Boolean> completed;
        final long deadline;
        int retries;

        Request(Action action, BooleanSupplier valid, BooleanSupplier ready,
                Consumer<Boolean> completed, int retries, long deadline) {
            this.action = action;
            this.valid = valid;
            this.ready = ready;
            this.completed = completed;
            this.retries = retries;
            this.deadline = deadline;
        }
    }
}
