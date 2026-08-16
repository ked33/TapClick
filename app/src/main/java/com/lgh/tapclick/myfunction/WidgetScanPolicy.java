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
}
