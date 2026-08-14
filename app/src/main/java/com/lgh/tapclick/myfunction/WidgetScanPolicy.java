package com.lgh.tapclick.myfunction;

import com.lgh.tapclick.mybean.Widget;

public final class WidgetScanPolicy {
    private WidgetScanPolicy() {
    }

    public static boolean shouldEvaluate(Widget widget, boolean alreadyClicked, boolean debouncing) {
        if (debouncing) {
            return false;
        }
        if (!alreadyClicked) {
            return true;
        }
        return widget.action == Widget.ACTION_CLICK && !widget.noRepeat;
    }
}
