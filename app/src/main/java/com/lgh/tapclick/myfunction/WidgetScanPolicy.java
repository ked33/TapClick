package com.lgh.tapclick.myfunction;

import com.lgh.tapclick.mybean.Widget;

public final class WidgetScanPolicy {
    private WidgetScanPolicy() {
    }

    public static boolean shouldEvaluate(Widget widget, boolean alreadyClicked, boolean debouncing) {
        if (widget == null || widget.hasReachedMaxTriggerCount()) {
            return false;
        }
        if (debouncing) {
            return false;
        }
        if (!alreadyClicked) {
            return true;
        }
        return widget.action == Widget.ACTION_CLICK && !widget.noRepeat;
    }

    public static boolean isCooldownActive(Widget widget, long nowMillis) {
        return widget != null
                && widget.actionCooldownMillis > 0
                && widget.lastTriggerTime > 0L
                && nowMillis - widget.lastTriggerTime >= 0L
                && nowMillis - widget.lastTriggerTime < widget.actionCooldownMillis;
    }

    /** -1 waits for a state change; 0 is runnable; positive values are a timed wake-up. */
    public static long nextEvaluationDelay(Widget widget, boolean alreadyClicked,
                                           boolean debouncing, boolean failed,
                                           boolean preconditionSatisfied, long nowMillis,
                                           long activityElapsedMillis) {
        if (failed || !widget.preconditionValid || widget.hasReachedMaxTriggerCount()
                || (alreadyClicked && (widget.noRepeat || widget.action == Widget.ACTION_BACK))
                || (widget.initialMatchWindowMillis > 0
                && activityElapsedMillis > widget.initialMatchWindowMillis)) {
            return -1;
        }
        if (isCooldownActive(widget, nowMillis)) {
            long delay = widget.actionCooldownMillis - (nowMillis - widget.lastTriggerTime);
            if (widget.initialMatchWindowMillis > 0
                    && activityElapsedMillis + delay > widget.initialMatchWindowMillis) {
                return -1;
            }
            return delay;
        }
        return debouncing || !preconditionSatisfied ? -1 : 0;
    }
}
