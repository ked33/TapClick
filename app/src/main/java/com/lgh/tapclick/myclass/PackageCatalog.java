package com.lgh.tapclick.myclass;

import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;
import android.content.pm.ResolveInfo;
import android.os.SystemClock;
import android.view.inputmethod.InputMethodInfo;
import android.view.inputmethod.InputMethodManager;

import java.util.Collections;
import java.util.HashSet;
import java.util.Set;

public final class PackageCatalog {
    private static final long CACHE_TTL_MILLIS = 10 * 60 * 1000L;
    private static final Object LOCK = new Object();
    private static Snapshot snapshot;

    private PackageCatalog() {
    }

    public static Set<String> getSuggestedRestrictedPackages(Context context) {
        long now = SystemClock.elapsedRealtime();
        synchronized (LOCK) {
            if (snapshot == null || now - snapshot.createdAtMillis >= CACHE_TTL_MILLIS) {
                snapshot = loadSnapshot(context.getApplicationContext(), now);
            }
            return snapshot.suggestedRestrictedPackages;
        }
    }

    private static Snapshot loadSnapshot(Context context, long now) {
        PackageManager packageManager = context.getPackageManager();
        Set<String> packages = new HashSet<>();
        for (PackageInfo packageInfo : packageManager
                .getInstalledPackages(PackageManager.MATCH_SYSTEM_ONLY)) {
            packages.add(packageInfo.packageName);
        }

        InputMethodManager inputMethodManager = context.getSystemService(InputMethodManager.class);
        if (inputMethodManager != null) {
            for (InputMethodInfo inputMethodInfo : inputMethodManager.getInputMethodList()) {
                packages.add(inputMethodInfo.getPackageName());
            }
        }

        for (ResolveInfo resolveInfo : packageManager.queryIntentActivities(
                new Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_HOME),
                PackageManager.MATCH_ALL)) {
            packages.add(resolveInfo.activityInfo.packageName);
        }
        return new Snapshot(now, Collections.unmodifiableSet(packages));
    }

    private static final class Snapshot {
        private final long createdAtMillis;
        private final Set<String> suggestedRestrictedPackages;

        private Snapshot(long createdAtMillis, Set<String> suggestedRestrictedPackages) {
            this.createdAtMillis = createdAtMillis;
            this.suggestedRestrictedPackages = suggestedRestrictedPackages;
        }
    }
}
