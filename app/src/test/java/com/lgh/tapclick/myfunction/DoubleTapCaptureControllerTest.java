package com.lgh.tapclick.myfunction;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

public class DoubleTapCaptureControllerTest {
    @Test
    public void singleTapIsReplayedOnlyAfterConfirmationAndWindowRemoval() {
        FakeHost host = new FakeHost();
        tap(host, 900, 40);
        host.advance(299);
        assertTrue(host.overlayVisible);
        assertEquals(0, host.dispatchCount);
        host.advance(1);
        assertFalse(host.overlayVisible);
        assertEquals(0, host.dispatchCount);
        host.advance(32);
        assertEquals(1, host.dispatchCount);
        assertEquals(900f, host.lastX, 0f);
        assertEquals(40f, host.lastY, 0f);
        host.completion.run();
        assertTrue(host.overlayVisible);
        host.advance(2000);
        assertEquals(1, host.restoreCount);
    }

    @Test
    public void doubleTapCapturesOnceOnSecondReleaseWithoutClickingUnderlyingApp() {
        FakeHost host = new FakeHost();
        tap(host, 900, 40);
        host.advance(100);
        host.controller.onDown(910, 45, host.now);
        host.advance(20);
        assertEquals(0, host.captureCount);
        host.controller.onUp(910, 45, host.now);
        host.advance(2000);
        assertEquals(1, host.captureCount);
        assertEquals(0, host.dispatchCount);
    }

    @Test
    public void firstTapAfterDoubleTapIsAnIndependentSingleTap() {
        FakeHost host = new FakeHost();
        tap(host, 900, 40);
        host.advance(100);
        tap(host, 900, 40);
        host.advance(100);
        tap(host, 900, 40);
        host.advance(332);
        assertEquals(1, host.captureCount);
        assertEquals(1, host.dispatchCount);
    }

    @Test
    public void distantTapsAreNotDoubleTapAndReplayWaitsForFingerUp() {
        FakeHost host = new FakeHost();
        tap(host, 900, 40);
        host.advance(100);
        host.controller.onDown(700, 40, host.now);
        host.advance(200);
        assertTrue(host.overlayVisible);
        assertEquals(0, host.dispatchCount);
        host.controller.onUp(700, 40, host.now);
        host.advance(32);
        assertEquals(1, host.dispatchCount);
        host.completion.run();
        host.advance(300);
        assertEquals(2, host.dispatchCount);
        assertEquals(0, host.captureCount);
    }

    @Test
    public void dragLongPressAndMultiplePointersDoNotBecomeSingleClicks() {
        FakeHost host = new FakeHost();
        host.controller.onDown(900, 40, host.now);
        host.controller.onMove(850, 40);
        host.controller.onMove(900, 40);
        host.controller.onUp(900, 40, host.now + 40);
        host.advance(1000);
        host.controller.onDown(900, 40, host.now);
        host.advance(600);
        host.controller.onUp(900, 40, host.now);
        host.controller.onDown(900, 40, host.now);
        host.controller.cancelPendingTaps();
        host.controller.onUp(900, 40, host.now + 40);
        host.advance(1000);
        assertEquals(0, host.dispatchCount);
        assertEquals(0, host.captureCount);
    }

    @Test
    public void movedSecondContactStillReplaysFirstTap() {
        FakeHost host = new FakeHost();
        tap(host, 900, 40);
        host.advance(100);
        host.controller.onDown(900, 40, host.now);
        host.controller.onUp(850, 40, host.now + 40);
        host.advance(332);
        assertEquals(0, host.captureCount);
        assertEquals(1, host.dispatchCount);
        assertEquals(900f, host.lastX, 0f);
    }

    @Test
    public void changedPageDiscardsTapBeforeHidingOrDispatching() {
        FakeHost host = new FakeHost();
        tap(host, 900, 40);
        host.target = "other page";
        host.advance(1000);
        assertEquals(0, host.dispatchCount);
        assertTrue(host.overlayVisible);

        tap(host, 900, 40);
        host.advance(300);
        host.target = "third page";
        host.advance(32);
        assertEquals(0, host.dispatchCount);
        assertTrue(host.overlayVisible);
    }

    @Test
    public void rejectedCancelledAndTimedOutGesturesRestoreOverlay() {
        FakeHost rejected = new FakeHost();
        rejected.acceptGesture = false;
        tap(rejected, 900, 40);
        rejected.advance(332);
        assertTrue(rejected.overlayVisible);
        assertEquals(1, rejected.restoreCount);

        FakeHost cancelled = new FakeHost();
        tap(cancelled, 900, 40);
        cancelled.advance(332);
        cancelled.completion.run(); // Both Android completion and cancellation use this callback.
        assertTrue(cancelled.overlayVisible);

        FakeHost timedOut = new FakeHost();
        tap(timedOut, 900, 40);
        timedOut.advance(4332);
        assertTrue(timedOut.overlayVisible);
        assertEquals(1, timedOut.restoreCount);
    }

    @Test
    public void pendingTapCancellationAndCloseNeverInjectLater() {
        FakeHost cancelled = new FakeHost();
        tap(cancelled, 900, 40);
        cancelled.controller.cancelPendingTaps();
        cancelled.advance(2000);
        assertEquals(0, cancelled.dispatchCount);

        FakeHost removed = new FakeHost();
        tap(removed, 900, 40);
        removed.advance(300);
        removed.controller.close();
        removed.advance(2000);
        assertEquals(0, removed.dispatchCount);
        assertEquals(0, removed.restoreCount);
    }

    @Test
    public void cancellationDuringAcceptedGestureWaitsForCompletionBeforeRestoring() {
        FakeHost host = new FakeHost();
        tap(host, 900, 40);
        host.advance(332);
        host.controller.cancelPendingTaps();
        assertFalse(host.overlayVisible);
        host.completion.run();
        assertTrue(host.overlayVisible);
    }

    @Test
    public void downBeforeInjectionDefersReplayUntilTheRealFingerIsReleased() {
        FakeHost host = new FakeHost();
        tap(host, 900, 40);
        assertTrue(host.controller.isHandlingTouch());
        host.advance(300);
        host.controller.onDown(700, 40, host.now);
        host.advance(100);
        assertEquals(0, host.dispatchCount);
        host.controller.onUp(700, 40, host.now);
        host.advance(32);
        assertEquals(1, host.dispatchCount);
        assertTrue(host.controller.isHandlingTouch());
        host.completion.run();
        host.advance(300);
        host.completion.run();
        assertFalse(host.controller.isHandlingTouch());
    }

    @Test
    public void lateCallbackCannotRestoreClosedOverlayOrFinishAnotherReplay() {
        FakeHost host = new FakeHost();
        tap(host, 900, 40);
        host.advance(332);
        Runnable firstCompletion = host.completion;
        host.advance(4000);
        tap(host, 900, 40);
        host.advance(332);
        firstCompletion.run();
        assertFalse(host.overlayVisible);
        host.controller.close();
        host.completion.run();
        host.advance(2000);
        assertEquals(1, host.restoreCount);
    }

    private static void tap(FakeHost host, float x, float y) {
        host.controller.onDown(x, y, host.now);
        host.advance(40);
        host.controller.onUp(x, y, host.now);
    }

    private static final class FakeHost implements DoubleTapCaptureController.Host {
        final DoubleTapCaptureController controller =
                new DoubleTapCaptureController(this, 300, 500, 8, 100);
        final List<Task> tasks = new ArrayList<>();
        long now = 1000;
        String target = "page";
        boolean overlayVisible = true;
        boolean acceptGesture = true;
        int dispatchCount;
        int restoreCount;
        int captureCount;
        float lastX;
        float lastY;
        Runnable completion;

        @Override
        public Object currentTarget() {
            return target;
        }

        @Override
        public void postDelayed(Runnable action, long delayMillis) {
            tasks.add(new Task(now + delayMillis, action));
        }

        @Override
        public void removeCallbacks(Runnable action) {
            tasks.removeIf(task -> task.action == action);
        }

        @Override
        public boolean hideOverlay() {
            overlayVisible = false;
            return true;
        }

        @Override
        public void restoreOverlay() {
            restoreCount++;
            overlayVisible = true;
        }

        @Override
        public boolean dispatchTap(float x, float y, Runnable finished) {
            assertFalse(overlayVisible);
            dispatchCount++;
            lastX = x;
            lastY = y;
            completion = finished;
            return acceptGesture;
        }

        @Override
        public void capture() {
            captureCount++;
        }

        void advance(long duration) {
            long until = now + duration;
            while (true) {
                tasks.sort(Comparator.comparingLong(task -> task.when));
                if (tasks.isEmpty() || tasks.get(0).when > until) {
                    break;
                }
                Task task = tasks.remove(0);
                now = task.when;
                task.action.run();
            }
            now = until;
        }
    }

    private static final class Task {
        final long when;
        final Runnable action;

        Task(long when, Runnable action) {
            this.when = when;
            this.action = action;
        }
    }
}
