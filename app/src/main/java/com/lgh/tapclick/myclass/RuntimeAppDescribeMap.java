package com.lgh.tapclick.myclass;

import com.lgh.tapclick.mybean.AppDescribe;

import java.util.Collections;
import java.util.HashMap;
import java.util.Map;

/**
 * Copy-on-write helpers for the runtime AppDescribe index.
 *
 * <p>Readers can safely retain a snapshot while a rule is added or removed;
 * writers replace the whole immutable map instead of mutating a published
 * snapshot.</p>
 */
public final class RuntimeAppDescribeMap {
    private RuntimeAppDescribeMap() {
    }

    public static Map<String, AppDescribe> immutableSnapshot(
            Map<String, AppDescribe> source) {
        return Collections.unmodifiableMap(new HashMap<>(source == null
                ? Collections.emptyMap() : source));
    }

    public static Map<String, AppDescribe> withEntry(
            Map<String, AppDescribe> source, String packageName, AppDescribe describe) {
        Map<String, AppDescribe> snapshot = new HashMap<>(source == null
                ? Collections.emptyMap() : source);
        if (packageName != null && !packageName.isEmpty() && describe != null) {
            snapshot.put(packageName, describe);
        }
        return Collections.unmodifiableMap(snapshot);
    }
}
