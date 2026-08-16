package com.lgh.tapclick.myclass;

import android.os.SystemClock;
import android.view.accessibility.AccessibilityNodeInfo;

import java.util.Collections;
import java.util.Map;
import java.util.WeakHashMap;

/**
 * Tracks when a node entered TapClick's asynchronous pipeline. Accessibility
 * nodes can become stale while a delayed action is waiting to run, so callers
 * must refresh before acting and should avoid matching old snapshots.
 */
public final class AccessibilityNodeFreshness {
    public static final long DEFAULT_MAX_AGE_MILLIS = 1500L;

    private static final Map<AccessibilityNodeInfo, Long> GENERATED_UPTIMES =
            Collections.synchronizedMap(new WeakHashMap<>());

    private AccessibilityNodeFreshness() {
    }

    public static AccessibilityNodeInfo mark(AccessibilityNodeInfo node) {
        if (node == null) {
            return null;
        }
        GENERATED_UPTIMES.put(node, SystemClock.uptimeMillis());
        return node;
    }

    public static boolean isFresh(AccessibilityNodeInfo node) {
        return isFresh(node, DEFAULT_MAX_AGE_MILLIS);
    }

    public static boolean isFresh(AccessibilityNodeInfo node, long maxAgeMillis) {
        if (node == null || maxAgeMillis < 0L) {
            return false;
        }
        Long generatedUptime = GENERATED_UPTIMES.get(node);
        if (generatedUptime == null) {
            return false;
        }
        long age = SystemClock.uptimeMillis() - generatedUptime;
        return age >= 0L && age <= maxAgeMillis;
    }

    public static boolean refresh(AccessibilityNodeInfo node) {
        if (node == null) {
            return false;
        }
        try {
            if (!node.refresh()) {
                return false;
            }
            mark(node);
            return true;
        } catch (RuntimeException ignored) {
            return false;
        }
    }
}
