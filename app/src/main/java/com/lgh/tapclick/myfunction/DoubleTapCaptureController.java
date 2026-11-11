package com.lgh.tapclick.myfunction;

import java.util.ArrayDeque;
import java.util.Objects;

/**
 * Recognizes taps in the capture shortcut and replays confirmed single taps.
 * All calls, including Host callbacks, must run on the main thread.
 */
final class DoubleTapCaptureController {
    // Removing the input window precedes injection; never inject through our own window.
    private static final long WINDOW_REMOVAL_DELAY_MILLIS = 32L;
    private static final long REPLAY_TIMEOUT_MILLIS = 4000L;

    interface Host {
        Object currentTarget();

        void postDelayed(Runnable action, long delayMillis);

        void removeCallbacks(Runnable action);

        boolean hideOverlay();

        void restoreOverlay();

        boolean dispatchTap(float x, float y, Runnable finished);

        void capture();
    }

    private final Host host;
    private final long doubleTapTimeout;
    private final long longPressTimeout;
    private final float touchSlopSquared;
    private final float doubleTapSlopSquared;
    private final ArrayDeque<Tap> readyTaps = new ArrayDeque<>();
    private final Runnable confirmSingleTap = this::confirmPendingTap;
    private volatile Tap pendingTap;
    private volatile Tap replayTap;
    private Runnable dispatchTask;
    private Runnable timeoutTask;
    private boolean replayDispatched;
    private boolean closed;
    private volatile boolean touching;
    private boolean moved;
    private boolean secondTap;
    private float downX;
    private float downY;
    private long downTime;
    private Object downTarget;

    DoubleTapCaptureController(Host host, long doubleTapTimeout, long longPressTimeout,
                               float touchSlop, float doubleTapSlop) {
        this.host = host;
        this.doubleTapTimeout = doubleTapTimeout;
        this.longPressTimeout = longPressTimeout;
        touchSlopSquared = touchSlop * touchSlop;
        doubleTapSlopSquared = doubleTapSlop * doubleTapSlop;
    }

    void onDown(float x, float y, long eventTime) {
        if (closed) {
            return;
        }
        if (replayTap != null) {
            if (replayDispatched) {
                return;
            }
            // A DOWN already queued by InputDispatcher can arrive before removal.
            // Defer replay until that real contact ends instead of cancelling it.
            readyTaps.addFirst(replayTap);
            removeReplayCallbacks();
            replayTap = null;
            host.restoreOverlay();
        }
        touching = true;
        moved = false;
        downX = x;
        downY = y;
        downTime = eventTime;
        downTarget = host.currentTarget();
        secondTap = pendingTap != null
                && eventTime >= pendingTap.upTime
                && eventTime - pendingTap.upTime <= doubleTapTimeout
                && Objects.equals(downTarget, pendingTap.target)
                && distanceSquared(x, y, pendingTap.x, pendingTap.y) <= doubleTapSlopSquared;
        if (secondTap) {
            host.removeCallbacks(confirmSingleTap);
        } else {
            // Two nearby-in-time taps at distant positions are independent clicks.
            // Keep the first queued until the current finger has lifted.
            confirmPendingTap();
        }
    }

    void onMove(float x, float y) {
        if (touching && distanceSquared(x, y, downX, downY) > touchSlopSquared) {
            moved = true;
        }
    }

    void onUp(float x, float y, long eventTime) {
        if (closed || !touching) {
            return;
        }
        onMove(x, y);
        touching = false;
        boolean validTap = !moved && eventTime >= downTime
                && eventTime - downTime < longPressTimeout
                && Objects.equals(downTarget, host.currentTarget());
        if (validTap && secondTap) {
            cancelPendingTaps();
            host.capture();
            return;
        }
        if (secondTap) {
            // A moved/held second contact must not turn into a capture or lose the first tap.
            secondTap = false;
            confirmPendingTap();
        }
        if (validTap) {
            pendingTap = new Tap(x, y, eventTime, downTarget);
            host.postDelayed(confirmSingleTap, doubleTapTimeout);
        }
        startReplayIfReady();
    }

    void cancelPendingTaps() {
        touching = false;
        moved = false;
        secondTap = false;
        pendingTap = null;
        readyTaps.clear();
        host.removeCallbacks(confirmSingleTap);
        // An accepted gesture owns its completion callback. Do not restore a touchable
        // window in the middle of that gesture; completion/cancellation/timeout restores it.
        if (replayTap != null && !replayDispatched) {
            finishReplay(replayTap);
        }
    }

    void close() {
        closed = true;
        cancelPendingTaps();
        removeReplayCallbacks();
        replayTap = null;
        // The owner removes the window. Late callbacks must never recreate it.
    }

    boolean isHandlingTouch() {
        return touching || pendingTap != null || replayTap != null;
    }

    private void confirmPendingTap() {
        host.removeCallbacks(confirmSingleTap);
        if (pendingTap != null) {
            readyTaps.addLast(pendingTap);
            pendingTap = null;
        }
        startReplayIfReady();
    }

    private void startReplayIfReady() {
        if (closed || touching || replayTap != null) {
            return;
        }
        while (!readyTaps.isEmpty()) {
            Tap tap = readyTaps.removeFirst();
            if (!Objects.equals(tap.target, host.currentTarget())) {
                continue;
            }
            replayTap = tap;
            replayDispatched = false;
            // Never detach a window from inside its ACTION_UP dispatch.
            dispatchTask = () -> prepareReplay(tap);
            host.postDelayed(dispatchTask, 0);
            return;
        }
    }

    private void prepareReplay(Tap tap) {
        if (closed || replayTap != tap) {
            return;
        }
        if (!Objects.equals(tap.target, host.currentTarget()) || !host.hideOverlay()) {
            finishReplay(tap);
            return;
        }
        dispatchTask = () -> dispatchReplay(tap);
        host.postDelayed(dispatchTask, WINDOW_REMOVAL_DELAY_MILLIS);
    }

    private void dispatchReplay(Tap tap) {
        if (closed || replayTap != tap) {
            return;
        }
        if (!Objects.equals(tap.target, host.currentTarget())) {
            finishReplay(tap);
            return;
        }
        replayDispatched = true;
        timeoutTask = () -> finishReplay(tap);
        host.postDelayed(timeoutTask, REPLAY_TIMEOUT_MILLIS);
        if (!host.dispatchTap(tap.x, tap.y, () -> finishReplay(tap))) {
            finishReplay(tap);
        }
    }

    private void finishReplay(Tap tap) {
        if (closed || replayTap != tap) {
            return;
        }
        removeReplayCallbacks();
        replayTap = null;
        replayDispatched = false;
        host.restoreOverlay();
        startReplayIfReady();
    }

    private void removeReplayCallbacks() {
        if (dispatchTask != null) {
            host.removeCallbacks(dispatchTask);
            dispatchTask = null;
        }
        if (timeoutTask != null) {
            host.removeCallbacks(timeoutTask);
            timeoutTask = null;
        }
    }

    private static float distanceSquared(float x, float y, float otherX, float otherY) {
        float dx = x - otherX;
        float dy = y - otherY;
        return dx * dx + dy * dy;
    }

    private static final class Tap {
        final float x;
        final float y;
        final long upTime;
        final Object target;

        Tap(float x, float y, long upTime, Object target) {
            this.x = x;
            this.y = y;
            this.upTime = upTime;
            this.target = target;
        }
    }
}
