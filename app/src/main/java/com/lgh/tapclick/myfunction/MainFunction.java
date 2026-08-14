package com.lgh.tapclick.myfunction;

import android.accessibilityservice.AccessibilityService;
import android.accessibilityservice.AccessibilityServiceInfo;
import android.accessibilityservice.GestureDescription;
import android.annotation.SuppressLint;
import android.app.AlertDialog;
import android.app.Dialog;
import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;
import android.content.res.Configuration;
import android.graphics.Bitmap;
import android.graphics.Color;
import android.graphics.ColorSpace;
import android.graphics.Path;
import android.graphics.PixelFormat;
import android.graphics.Rect;
import android.hardware.HardwareBuffer;
import android.os.Build;
import android.os.Handler;
import android.os.Looper;
import android.os.SystemClock;
import android.text.TextUtils;
import android.util.DisplayMetrics;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.Display;
import android.view.LayoutInflater;
import android.view.MotionEvent;
import android.view.View;
import android.view.WindowManager;
import android.view.accessibility.AccessibilityEvent;
import android.view.accessibility.AccessibilityNodeInfo;
import android.view.accessibility.AccessibilityWindowInfo;
import android.widget.Button;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.SeekBar;
import android.widget.TextView;
import android.widget.Toast;

import androidx.core.content.ContextCompat;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.lgh.tapclick.BuildConfig;
import com.lgh.tapclick.R;
import com.lgh.tapclick.databinding.ViewAddDataBinding;
import com.lgh.tapclick.databinding.ViewDbClickSettingBinding;
import com.lgh.tapclick.databinding.ViewDialogWarningBinding;
import com.lgh.tapclick.databinding.ViewWidgetSelectBinding;
import com.lgh.tapclick.myactivity.EditDataActivity;
import com.lgh.tapclick.myactivity.ListDataActivity;
import com.lgh.tapclick.myactivity.MainActivity;
import com.lgh.tapclick.mybean.AppDescribe;
import com.lgh.tapclick.mybean.Coordinate;
import com.lgh.tapclick.mybean.Widget;
import com.lgh.tapclick.myclass.AccessibilityLayoutSnapshot;
import com.lgh.tapclick.myclass.DataDao;
import com.lgh.tapclick.myclass.MyApplication;
import com.lgh.tapclick.myclass.PackageCatalog;
import com.lgh.tapclick.myclass.RuntimeAppDescribeMap;
import com.lgh.tapclick.myclass.VisualCoordinateSignature;

import java.text.SimpleDateFormat;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Date;
import java.util.HashMap;
import java.util.HashSet;
import java.util.IdentityHashMap;
import java.util.LinkedHashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.ScheduledThreadPoolExecutor;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.AtomicReference;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import cn.hutool.core.util.StrUtil;

/**
 * adb shell pm grant com.lgh.advertising.going android.permission.WRITE_SECURE_SETTINGS
 * adb shell settings put secure enabled_accessibility_services com.lgh.tapclick/com.lgh.tapclick.myfunction.MyAccessibilityService
 * adb shell settings put secure accessibility_enabled 1
 * <p>
 * Settings.Secure.putString(getContentResolver(), Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES, getPackageName() + "/" + MyAccessibilityService.class.getName());
 * Settings.Secure.putString(getContentResolver(), Settings.Secure.ACCESSIBILITY_ENABLED, "1");
 */

public class MainFunction {
    private static final long QUICK_CAPTURE_BUDGET_MILLIS = 180L;
    private static final int QUICK_CAPTURE_MAX_NODES = 400;
    private static final long QUICK_CAPTURE_MODULE_RETRY_MILLIS = 700L;
    private static final long QUICK_CAPTURE_WINDOW_FALLBACK_MILLIS = 1200L;
    private static final int QUICK_CAPTURE_FALLBACK_MAX_NODES = 1200;
    private static final int QUICK_CAPTURE_SPARSE_NODE_THRESHOLD = 8;
    private static final long QUICK_CAPTURE_REFRESH_FIRST_DELAY_MILLIS = 300L;
    private static final long QUICK_CAPTURE_REFRESH_SECOND_DELAY_MILLIS = 900L;
    private static final int QUICK_CAPTURE_MAX_REFRESH_ATTEMPTS = 2;
    private static final long QUICK_CAPTURE_MAX_LIVE_SNAPSHOT_AGE_MILLIS = 2400L;
    private static final int CAPTURE_NODE_DIAGNOSTIC_LIMIT = 6;
    private static final Pattern PACKAGE_NAME_PATTERN = Pattern.compile("[A-Za-z0-9_.]+");
    private static final long MANUAL_CAPTURE_BUDGET_MILLIS = 1500L;
    private static final int MANUAL_CAPTURE_MAX_NODES = 5000;
    private static final int VISUAL_COORDINATE_MAX_ATTEMPTS = 3;
    private static final long VISUAL_COORDINATE_RETRY_DELAY_MILLIS = 500L;
    private static final long VISUAL_COORDINATE_SCREENSHOT_INTERVAL_MILLIS = 500L;
    private static final long VISUAL_COORDINATE_MAX_SCREENSHOT_AGE_MILLIS = 1500L;

    private final AccessibilityService service;
    private final WindowManager windowManager;
    private final PackageManager packageManager;
    private final DataDao dataDao;
    private volatile Map<String, AppDescribe> appDescribeMap;
    private final Object appDescribeMapLock;
    private final ScheduledThreadPoolExecutor executorServiceMain;
    private final ScheduledThreadPoolExecutor executorServiceSub;
    private final ScheduledThreadPoolExecutor executorServiceCapture;
    private final Handler mainHandler;
    private final Set<Widget> alreadyClickSet;
    private final Set<Widget> debounceSet;
    private final LinkedList<String> logList;
    private final Gson debugGson;
    private final SimpleDateFormat simpleDateFormat;
    private final Object serviceInfoLock;
    private final Object pageTaskLock;
    private final Object coordinateScreenshotLock;
    private final Set<ScheduledFuture<?>> pageTaskFutures;
    private final AtomicLong layoutCaptureGeneration;
    private final AtomicBoolean quickCaptureInProgress;
    private volatile AppDescribe appDescribe;
    private volatile String currentPackage;
    private volatile String currentPackageSub;
    private volatile String currentActivity;
    private volatile String prePackage;
    private volatile String preActivity;
    private volatile boolean onOffWidget;
    private volatile boolean onOffWidgetSub;
    private volatile boolean onOffCoordinate;
    private volatile boolean onOffCoordinateSub;
    private volatile boolean needChangeActivity;
    private volatile ScheduledFuture<?> futureWidget;
    private volatile ScheduledFuture<?> futureCoordinate;
    private volatile Map<String, List<Coordinate>> coordinateSetMap;
    private volatile Map<String, List<Widget>> widgetSetMap;
    private volatile List<Coordinate> coordinateSet;
    private volatile List<Widget> widgetSet;
    private volatile ScheduledFuture<?> pendingWidgetScan;
    private volatile long pageGeneration;
    private volatile long packageGeneration;
    private long lastCoordinateScreenshotRequestUptimeMillis;
    private volatile AccessibilityServiceInfo serviceInfo;
    private volatile boolean contentChangeEventsEnabled;
    private volatile boolean runtimeLoggingEnabled;
    private MyBroadcastReceiver myBroadcastReceiver;
    private WindowManager.LayoutParams aParams, bParams, cParams;
    private ViewAddDataBinding addDataBinding;
    private ViewWidgetSelectBinding widgetSelectBinding;
    private volatile Widget activeWidgetSelection;
    private volatile Coordinate activeCoordinateSelection;
    private volatile boolean captureLayoutHiddenByUser;
    private volatile boolean frozenCoordinateSelected;
    private final AtomicReference<FrozenScreenCapture> pendingFrozenCapture = new AtomicReference<>();
    private FrozenScreenCapture activeFrozenCapture;
    private ImageView frozenCaptureView;
    private View frozenCoordinateMarker;
    private ImageView viewClickPosition;
    private volatile Set<String> pkgSuggestNotOnList;
    private View ignoreView;
    private WindowManager.LayoutParams dbClickLp;
    private View dbClickView;
    private final AtomicBoolean closed;
    private boolean receiverRegistered;

    public MainFunction(AccessibilityService accessibilityService) {
        service = accessibilityService;
        windowManager = accessibilityService.getSystemService(WindowManager.class);
        packageManager = accessibilityService.getPackageManager();
        executorServiceMain = new ScheduledThreadPoolExecutor(1,
                runnable -> new Thread(runnable, "TapClick-WidgetScanner"));
        executorServiceSub = new ScheduledThreadPoolExecutor(1,
                runnable -> new Thread(runnable, "TapClick-Actions"));
        AtomicInteger captureThreadNumber = new AtomicInteger();
        // Keep one worker available for the screenshot callback while two
        // accessibility IPC captures may be blocked in vendor code.
        executorServiceCapture = new ScheduledThreadPoolExecutor(3,
                runnable -> new Thread(runnable,
                        "TapClick-Capture-" + captureThreadNumber.incrementAndGet()));
        executorServiceMain.setRemoveOnCancelPolicy(true);
        executorServiceSub.setRemoveOnCancelPolicy(true);
        executorServiceCapture.setRemoveOnCancelPolicy(true);
        executorServiceCapture.setKeepAliveTime(30, TimeUnit.SECONDS);
        executorServiceCapture.allowCoreThreadTimeOut(true);
        mainHandler = new Handler(Looper.getMainLooper());
        simpleDateFormat = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.ROOT);
        debugGson = BuildConfig.DEBUG ? new GsonBuilder().setPrettyPrinting().create() : null;
        appDescribe = new AppDescribe();
        alreadyClickSet = Collections.newSetFromMap(new ConcurrentHashMap<>());
        appDescribeMap = Collections.emptyMap();
        debounceSet = Collections.newSetFromMap(new ConcurrentHashMap<>());
        logList = new LinkedList<>();
        serviceInfoLock = new Object();
        appDescribeMapLock = new Object();
        pageTaskLock = new Object();
        coordinateScreenshotLock = new Object();
        pageTaskFutures = new HashSet<>();
        layoutCaptureGeneration = new AtomicLong();
        quickCaptureInProgress = new AtomicBoolean(false);
        closed = new AtomicBoolean(false);
        dataDao = MyApplication.dataDao;
        currentPackage = StrUtil.EMPTY;
        currentPackageSub = StrUtil.EMPTY;
        currentActivity = StrUtil.EMPTY;
        prePackage = StrUtil.EMPTY;
        preActivity = StrUtil.EMPTY;
        coordinateSetMap = Collections.emptyMap();
        widgetSetMap = Collections.emptyMap();
        pkgSuggestNotOnList = Collections.emptySet();
        runtimeLoggingEnabled = MyUtils.getRuntimeLoggingEnabled();
    }

    public void onServiceConnected() {
        serviceInfo = service.getServiceInfo();
        contentChangeEventsEnabled = serviceInfo != null
                && (serviceInfo.eventTypes & AccessibilityEvent.TYPE_WINDOW_CONTENT_CHANGED) != 0;
        setContentChangeEventsEnabled(false);
        if (!receiverRegistered) {
            IntentFilter intentFilter = new IntentFilter();
            intentFilter.addAction(Intent.ACTION_SCREEN_OFF);
            myBroadcastReceiver = new MyBroadcastReceiver();
            ContextCompat.registerReceiver(service, myBroadcastReceiver, intentFilter,
                    ContextCompat.RECEIVER_NOT_EXPORTED);
            receiverRegistered = true;
        }
        executorServiceSub.execute(this::getRunningData);
        executorServiceCapture.execute(this::preloadPackageCatalog);
        keepAliveByNotification(MyUtils.getKeepAliveByNotification());
        keepAliveByFloatingWindow(MyUtils.getKeepAliveByFloatingWindow());
        showDbClickFloating(MyUtils.getDbClickEnable());

        /*executorService.schedule(new Runnable() {
            @Override
            public void run() {
                NotificationManager notificationManager = service.getSystemService(NotificationManager.class);
                Notification.Builder builder = new Notification.Builder(service)
                        .setAutoCancel(true)
                        .setSmallIcon(R.drawable.app)
                        .setContentTitle(service.getText(R.string.app_name))
                        .setContentText("占用内存" + Debug.getPss() + "kb");
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                    builder.setChannelId(service.getPackageName());
                    NotificationChannel channel = new NotificationChannel(service.getPackageName(), service.getString(R.string.app_name), NotificationManager.IMPORTANCE_HIGH);
                    notificationManager.createNotificationChannel(channel);
                }
                notificationManager.notify(service.getPackageName(), 0x01, builder.build());
                executorService.schedule(this, 5000, TimeUnit.MILLISECONDS);
            }
        }, 0, TimeUnit.MILLISECONDS);*/
    }

    @SuppressLint("SwitchIntDef")
    public void onAccessibilityEvent(AccessibilityEvent event) {
        if (closed.get()) {
            return;
        }
        switch (event.getEventType()) {
            case AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED: {
                String eventPackage = event.getPackageName() == null
                        ? null : event.getPackageName().toString();
                AppDescribe cachedDescribe = eventPackage == null
                        ? null : appDescribeMap.get(eventPackage);
                if (TextUtils.equals(eventPackage, currentPackage)
                        && !hasRunnableRules(cachedDescribe)) {
                    if (!currentPackageSub.isEmpty() || contentChangeEventsEnabled
                            || onOffWidget || onOffCoordinate) {
                        appDescribe = new AppDescribe();
                        deactivateCurrentPage(false);
                    }
                    rememberCaptureActivity(event.getClassName());
                    break;
                }
                AccessibilityNodeInfo root = service.getRootInActiveWindow();
                if (root == null) {
                    break;
                }
                String packageName = root.getPackageName() != null ? root.getPackageName().toString() : null;
                String activityName = event.getClassName() != null ? event.getClassName().toString() : null;
                if (packageName == null) {
                    break;
                }
                AppDescribe nextDescribe = appDescribeMap.get(packageName);
                boolean packageChanged = !TextUtils.equals(packageName, currentPackage);
                boolean rulesChanged = nextDescribe == null
                        ? hasRunnableRules(appDescribe)
                        : appDescribe != nextDescribe;
                if (packageChanged) {
                    addStateLog("打开应用：", packageName);
                    currentPackage = packageName;
                }
                if (packageChanged || rulesChanged) {
                    appDescribe = nextDescribe == null ? new AppDescribe() : nextDescribe;
                    if (!packageChanged) {
                        currentPackageSub = StrUtil.EMPTY;
                    }
                }
                if (!packageName.equals(currentPackageSub)) {
                    deactivateCurrentPage(false);
                    currentPackageSub = packageName;
                    widgetSetMap = appDescribe.widgetSetMap;
                    coordinateSetMap = appDescribe.coordinateSetMap;
                    onOffWidget = appDescribe.widgetOnOff && !widgetSetMap.isEmpty();
                    onOffCoordinate = appDescribe.coordinateOnOff && !coordinateSetMap.isEmpty();
                    long activePackageGeneration = packageGeneration;

                    if (onOffWidget && !appDescribe.widgetRetrieveAllTime) {
                        futureWidget = executorServiceSub.schedule(new Runnable() {
                            @Override
                            public void run() {
                                if (activePackageGeneration != packageGeneration || closed.get()) {
                                    return;
                                }
                                onOffWidget = false;
                                onOffWidgetSub = false;
                                setContentChangeEventsEnabled(false);
                            }
                        }, appDescribe.widgetRetrieveTime, TimeUnit.MILLISECONDS);
                    }

                    if (onOffCoordinate && !appDescribe.coordinateRetrieveAllTime) {
                        futureCoordinate = executorServiceSub.schedule(new Runnable() {
                            @Override
                            public void run() {
                                if (activePackageGeneration != packageGeneration || closed.get()) {
                                    return;
                                }
                                onOffCoordinate = false;
                                onOffCoordinateSub = false;
                            }
                        }, appDescribe.coordinateRetrieveTime, TimeUnit.MILLISECONDS);
                    }
                }

                if (!hasRunnableRules(appDescribe)) {
                    rememberCaptureActivity(activityName);
                    break;
                }
                if (activityName == null) {
                    break;
                }
                if (!TextUtils.equals(event.getPackageName(), currentPackage)) {
                    break;
                }
                if ((!activityName.equals(currentActivity)
                        && !activityName.startsWith("android.view.")
                        && !activityName.startsWith("android.widget."))
                        || (activityName.equals("android.widget.FrameLayout")
                        && needChangeActivity)) {
                    addStateLog("进入页面：", activityName);
                    setContentChangeEventsEnabled(false);
                    needChangeActivity = false;
                    currentActivity = activityName;
                    long generation = ++pageGeneration;
                    cancelAllPageTasks();
                    alreadyClickSet.clear();
                    debounceSet.clear();
                    List<Coordinate> coordinates = coordinateSetMap != null ? coordinateSetMap.get(activityName) : null;
                    List<Widget> widgets = widgetSetMap != null ? widgetSetMap.get(activityName) : null;
                    coordinateSet = coordinates == null ? null : Collections.unmodifiableList(new ArrayList<>(coordinates));
                    widgetSet = widgets == null ? null : Collections.unmodifiableList(new ArrayList<>(widgets));
                    onOffCoordinateSub = onOffCoordinate && coordinateSet != null;
                    onOffWidgetSub = onOffWidget && widgetSet != null;

                    if (onOffWidgetSub) {
                        setContentChangeEventsEnabled(true);
                    }

                    if (onOffCoordinateSub) {
                        for (Coordinate coordinate : coordinateSet) {
                            scheduleCoordinateClick(coordinate, generation, 0, coordinate.clickDelay);
                        }
                    }
                }
                if (!onOffWidgetSub) {
                    break;
                }
                List<AccessibilityWindowInfo> windowInfoList = service.getWindows();
                if (windowInfoList.isEmpty()) {
                    break;
                }
                List<AccessibilityNodeInfo> nodeInfoList = new ArrayList<>();
                for (AccessibilityWindowInfo windowInfo : windowInfoList) {
                    AccessibilityNodeInfo nodeInfo = windowInfo.getRoot();
                    if (nodeInfo != null && TextUtils.equals(nodeInfo.getPackageName(), packageName)) {
                        nodeInfoList.add(nodeInfo);
                    }
                }
                if (nodeInfoList.isEmpty()) {
                    break;
                }
                scheduleWidgetScan(nodeInfoList, widgetSet, pageGeneration, 0);
                break;
            }
            case AccessibilityEvent.TYPE_WINDOW_CONTENT_CHANGED: {
                if (!TextUtils.equals(event.getPackageName(), currentPackageSub)) {
                    break;
                }
                AccessibilityNodeInfo source = event.getSource();
                if (source == null) {
                    break;
                }
                if (!onOffWidgetSub) {
                    break;
                }
                scheduleWidgetScan(Collections.singletonList(source), widgetSet, pageGeneration, 0);
                break;
            }
        }
    }

    public void onConfigurationChanged(Configuration newConfig) {
        layoutCaptureGeneration.incrementAndGet();
        quickCaptureInProgress.set(false);
        frozenCoordinateSelected = false;
        clearFrozenScreenCapture();
        if (addDataBinding != null && viewClickPosition != null && widgetSelectBinding != null) {
            addDataBinding.switchWid.setEnabled(true);
            addDataBinding.saveWid.setEnabled(false);
            DisplayMetrics metrics = new DisplayMetrics();
            windowManager.getDefaultDisplay().getRealMetrics(metrics);
            aParams.x = (metrics.widthPixels - aParams.width) / 2;
            aParams.y = metrics.heightPixels - addDataBinding.getRoot().getHeight();
            bParams.width = metrics.widthPixels;
            bParams.height = metrics.heightPixels;
            cParams.x = (metrics.widthPixels - cParams.width) / 2;
            cParams.y = (metrics.heightPixels - cParams.height) / 2;
            windowManager.updateViewLayout(addDataBinding.getRoot(), aParams);
            windowManager.updateViewLayout(widgetSelectBinding.getRoot(), bParams);
            windowManager.updateViewLayout(viewClickPosition, cParams);
            if (bParams.alpha != 0) {
                TextView text = new TextView(service);
                text.setTextSize(TypedValue.COMPLEX_UNIT_SP, 30);
                text.setGravity(Gravity.CENTER);
                text.setTextColor(0xffff0000);
                text.setText("请重新刷新布局");
                widgetSelectBinding.frame.removeAllViews();
                widgetSelectBinding.frame.addView(text, new FrameLayout.LayoutParams(FrameLayout.LayoutParams.MATCH_PARENT, FrameLayout.LayoutParams.MATCH_PARENT, Gravity.CENTER));
            } else {
                addDataBinding.switchWid.setText("显示布局");
            }
        }
        if (dbClickView != null) {
            Rect rect = MyUtils.getDbClickPosition();
            dbClickLp.x = rect.left;
            dbClickLp.y = rect.top;
            dbClickLp.width = rect.width();
            dbClickLp.height = rect.height();
            windowManager.updateViewLayout(dbClickView, dbClickLp);
        }
    }

    public boolean onUnbind(Intent intent) {
        close();
        return true;
    }

    public void refreshAppDescribe(String packageName) {
        if (TextUtils.isEmpty(packageName) || closed.get()) {
            return;
        }
        executorServiceSub.execute(new Runnable() {
            @Override
            public void run() {
                AppDescribe describe = dataDao.getAppDescribeByPackage(packageName);
                Map<String, AppDescribe> snapshot = copyAppDescribeMap();
                if (describe == null || (!describe.coordinateOnOff && !describe.widgetOnOff)) {
                    snapshot.remove(packageName);
                } else {
                    List<Coordinate> coordinates = describe.coordinateOnOff
                            ? dataDao.getCoordinatesByPackage(packageName)
                            : Collections.emptyList();
                    List<Widget> widgets = describe.widgetOnOff
                            ? dataDao.getWidgetsByPackage(packageName)
                            : Collections.emptyList();
                    Map<String, AppDescribe> refreshed = AppDescribe.buildRuntimeSnapshot(
                            Collections.singletonList(describe), coordinates, widgets);
                    if (refreshed.containsKey(packageName)) {
                        snapshot.put(packageName, refreshed.get(packageName));
                    } else {
                        snapshot.remove(packageName);
                    }
                }
                replaceAppDescribeMap(snapshot);
                if (TextUtils.equals(packageName, currentPackage)) {
                    appDescribe = snapshot.containsKey(packageName)
                            ? snapshot.get(packageName) : new AppDescribe();
                    deactivateCurrentPage(false);
                }
            }
        });
    }

    public void removeAppDescribes(List<String> packageNames) {
        if (packageNames == null || packageNames.isEmpty() || closed.get()) {
            return;
        }
        executorServiceSub.execute(new Runnable() {
            @Override
            public void run() {
                Map<String, AppDescribe> snapshot = copyAppDescribeMap();
                for (String packageName : packageNames) {
                    snapshot.remove(packageName);
                }
                replaceAppDescribeMap(snapshot);
                if (packageNames.contains(currentPackage)) {
                    appDescribe = new AppDescribe();
                    deactivateCurrentPage(false);
                }
            }
        });
    }

    public void refreshAllData() {
        if (!closed.get()) {
            executorServiceSub.execute(this::getRunningData);
        }
    }

    /**
     * 查找并点击View
     */
    private void findAndClickView(List<AccessibilityNodeInfo> nodeInfoList, List<Widget> widgets, long generation) {
        ArrayDeque<AccessibilityNodeInfo> list = new ArrayDeque<>();
        for (AccessibilityNodeInfo nodeInfo : nodeInfoList) {
            if (nodeInfo != null) {
                list.offer(nodeInfo);
            }
        }
        while (!list.isEmpty() && onOffWidgetSub && generation == pageGeneration && !closed.get()) {
            AccessibilityNodeInfo nodeInfo = list.poll();
            clickByWidget(nodeInfo, widgets, generation);
            for (int n = 0; n < nodeInfo.getChildCount(); n++) {
                AccessibilityNodeInfo child = nodeInfo.getChild(n);
                if (child != null) {
                    list.offer(child);
                }
            }
        }
    }

    private void clickByWidget(AccessibilityNodeInfo nodeInfo, List<Widget> widgets, long generation) {
        Rect rect = null;
        Long nodeId = null;
        String viewId = null;
        String describe = null;
        String text = null;
        boolean nodePropertiesLoaded = false;
        for (Widget e : widgets) {
            boolean alreadyClicked = alreadyClickSet.contains(e);
            boolean debouncing = debounceSet.contains(e);
            if (!WidgetScanPolicy.shouldEvaluate(e, alreadyClicked, debouncing)) {
                continue;
            }
            if (!nodePropertiesLoaded) {
                rect = new Rect();
                nodeInfo.getBoundsInScreen(rect);
                nodeId = nodeInfo.getSourceNodeId();
                viewId = StrUtil.emptyToNull(nodeInfo.getViewIdResourceName());
                describe = StrUtil.emptyToNull(nodeInfo.getContentDescription());
                text = StrUtil.emptyToNull(nodeInfo.getText());
                nodePropertiesLoaded = true;
            }
            String triggerReason;
            if (e.condition == Widget.CONDITION_OR) {
                if (rect.equals(e.widgetRect)) {
                    triggerReason = "Bonus 匹配";
                } else if (nodeId != null && nodeId.equals(e.widgetNodeId)) {
                    triggerReason = "NodeId 匹配";
                } else if (viewId != null && !e.widgetViewId.isEmpty() && viewId.equals(e.widgetViewId)) {
                    triggerReason = "ViewId 匹配";
                } else if (!e.widgetDescribe.isEmpty() && e.matchesDescribe(describe)) {
                    triggerReason = "Describe 匹配";
                } else if (!e.widgetText.isEmpty() && e.matchesText(text)) {
                    triggerReason = "Text 匹配";
                } else {
                    continue;
                }
            } else if (e.condition == Widget.CONDITION_AND) {
                StringBuilder strBuildTrigger = new StringBuilder();
                if (e.widgetRect != null) {
                    if (rect.equals(e.widgetRect)) {
                        strBuildTrigger.append(", Bonus");
                    } else {
                        continue;
                    }
                }
                if (e.widgetNodeId != null) {
                    if (nodeId != null && nodeId.equals(e.widgetNodeId)) {
                        strBuildTrigger.append(", NodeId");
                    } else {
                        continue;
                    }
                }
                if (!e.widgetViewId.isEmpty()) {
                    if (viewId != null && viewId.equals(e.widgetViewId)) {
                        strBuildTrigger.append(", ViewId");
                    } else {
                        continue;
                    }
                }
                if (!e.widgetDescribe.isEmpty()) {
                    if (e.matchesDescribe(describe)) {
                        strBuildTrigger.append(", Describe");
                    } else {
                        continue;
                    }
                }
                if (!e.widgetText.isEmpty()) {
                    if (e.matchesText(text)) {
                        strBuildTrigger.append(", Text");
                    } else {
                        continue;
                    }
                }
                if (e.widgetRect == null
                        && e.widgetNodeId == null
                        && e.widgetViewId.isEmpty()
                        && e.widgetDescribe.isEmpty()
                        && e.widgetText.isEmpty()) {
                    continue;
                }
                triggerReason = strBuildTrigger.append(" 匹配").substring(2);
            } else {
                continue;
            }
            e.triggerReason = triggerReason;
            if (e.action == Widget.ACTION_CLICK) {
                if (e.noRepeat && !alreadyClickSet.add(e)) {
                    continue;
                }
                if (!debounceSet.add(e)) {
                    continue;
                }
                schedulePageTask(executorServiceSub, new Runnable() {
                    @Override
                    public void run() {
                        debounceSet.remove(e);
                    }
                }, (long) e.clickDelay + e.debounceDelay);

                scheduleWidgetClick(nodeInfo, rect, e, widgets, generation, 0, e.clickDelay);
            } else if (e.action == Widget.ACTION_BACK) {
                if (!alreadyClickSet.add(e)) {
                    continue;
                }
                if (generation == pageGeneration
                        && onOffWidgetSub
                        && currentActivity.equals(e.appActivity)
                        && nodeInfo.refresh()) {
                    boolean actionAccepted = service.performGlobalAction(
                            AccessibilityService.GLOBAL_ACTION_BACK);
                    e.triggerCount += 1;
                    e.lastTriggerTime = System.currentTimeMillis();
                    MyApplication.executeDatabase(() -> dataDao.updateWidget(e));
                    addRuleLog("widget", e.id, e.appPackage, e.appActivity, e.triggerReason,
                            actionAccepted ? "返回已执行" : "返回执行失败", e);
                    if (!hasPendingWidgetRules(widgets)) {
                        setContentChangeEventsEnabled(false);
                    }
                }
            }
            break;
        }
    }

    private void scheduleWidgetScan(List<AccessibilityNodeInfo> nodeInfoList, List<Widget> widgets, long generation, long delayMillis) {
        if (widgets == null || widgets.isEmpty() || nodeInfoList == null || nodeInfoList.isEmpty() || closed.get()) {
            return;
        }
        cancelPageFuture(pendingWidgetScan);
        pendingWidgetScan = schedulePageTask(executorServiceMain, new Runnable() {
            @Override
            public void run() {
                if (generation == pageGeneration && onOffWidgetSub && !closed.get()) {
                    findAndClickView(nodeInfoList, widgets, generation);
                }
            }
        }, delayMillis);
    }

    private void scheduleCoordinateClick(Coordinate coordinate, long generation, int clickIndex, long delayMillis) {
        if (clickIndex >= coordinate.clickNumber || closed.get()) {
            return;
        }
        schedulePageTask(executorServiceSub, new Runnable() {
            @Override
            public void run() {
                if (!isCoordinateExecutionValid(coordinate, generation)) {
                    return;
                }
                if (TextUtils.isEmpty(coordinate.visualSignature)) {
                    performCoordinateClick(coordinate, generation, clickIndex, false);
                    return;
                }
                if (Build.VERSION.SDK_INT < Build.VERSION_CODES.R) {
                    addCoordinateVisualLog(coordinate, clickIndex, 0, -1,
                            "skipped", "reason=unsupportedSdk sdk=" + Build.VERSION.SDK_INT);
                    addRuleLog("coordinate", coordinate.id, coordinate.appPackage,
                            coordinate.appActivity, "视觉校验",
                            "系统不支持截图，已跳过", coordinateLogDetails(coordinate));
                    return;
                }
                if (!VisualCoordinateSignature.isValid(coordinate.visualSignature)) {
                    addCoordinateVisualLog(coordinate, clickIndex, 0, -1,
                            "skipped", "reason=invalidSignature");
                    addRuleLog("coordinate", coordinate.id, coordinate.appPackage,
                            coordinate.appActivity, "视觉校验",
                            "视觉签名无效，已跳过", coordinateLogDetails(coordinate));
                    return;
                }
                requestCoordinateVisualCheck(coordinate, generation, clickIndex, 1);
            }
        }, Math.max(0, delayMillis));
    }

    private boolean isCoordinateExecutionValid(Coordinate coordinate, long generation) {
        return coordinate != null
                && generation == pageGeneration
                && onOffCoordinateSub
                && TextUtils.equals(currentPackage, coordinate.appPackage)
                && TextUtils.equals(currentActivity, coordinate.appActivity)
                && !closed.get();
    }

    private void performCoordinateClick(Coordinate coordinate, long generation,
                                        int clickIndex, boolean visualVerified) {
        if (!isCoordinateExecutionValid(coordinate, generation)) {
            return;
        }
        boolean actionAccepted = click(coordinate.xPosition, coordinate.yPosition);
        if (clickIndex == 0) {
            coordinate.triggerCount += 1;
            coordinate.lastTriggerTime = System.currentTimeMillis();
            MyApplication.executeDatabase(() -> dataDao.updateCoordinate(coordinate));
            addRuleLog("coordinate", coordinate.id, coordinate.appPackage,
                    coordinate.appActivity, visualVerified ? "视觉校验通过" : "页面匹配",
                    actionAccepted ? "手势已提交" : "手势提交失败",
                    coordinateLogDetails(coordinate));
        }
        scheduleCoordinateClick(coordinate, generation, clickIndex + 1,
                coordinate.clickInterval <= 0 ? 10 : coordinate.clickInterval);
    }

    @SuppressLint("NewApi")
    private void requestCoordinateVisualCheck(Coordinate coordinate, long generation,
                                              int clickIndex, int attempt) {
        if (!isCoordinateExecutionValid(coordinate, generation)) {
            return;
        }
        long throttleDelay = acquireCoordinateScreenshotDelay();
        if (throttleDelay > 0) {
            schedulePageTask(executorServiceSub,
                    () -> requestCoordinateVisualCheck(
                            coordinate, generation, clickIndex, attempt),
                    throttleDelay);
            return;
        }
        long requestStartedUptimeMillis = SystemClock.uptimeMillis();
        try {
            service.takeScreenshot(Display.DEFAULT_DISPLAY, executorServiceCapture,
                    new AccessibilityService.TakeScreenshotCallback() {
                        @Override
                        public void onSuccess(
                                AccessibilityService.ScreenshotResult screenshotResult) {
                            Bitmap bitmap = copyScreenshotBitmap(screenshotResult);
                            int score = -1;
                            if (bitmap != null) {
                                try {
                                    if (!isCoordinateExecutionValid(coordinate, generation)) {
                                        return;
                                    }
                                    long screenshotTimestamp = screenshotResult.getTimestamp();
                                    long screenshotAge = SystemClock.uptimeMillis()
                                            - screenshotTimestamp;
                                    if (screenshotTimestamp + 100L
                                            < requestStartedUptimeMillis
                                            || screenshotAge < 0L
                                            || screenshotAge
                                            > VISUAL_COORDINATE_MAX_SCREENSHOT_AGE_MILLIS) {
                                        handleCoordinateVisualResult(coordinate, generation,
                                                clickIndex, attempt, -1,
                                                "staleScreenshot ageMs=" + screenshotAge);
                                        return;
                                    }
                                    DisplayMetrics metrics = new DisplayMetrics();
                                    windowManager.getDefaultDisplay().getRealMetrics(metrics);
                                    VisualRegionSample sample = sampleVisualCoordinateRegion(
                                            bitmap, coordinate.xPosition, coordinate.yPosition,
                                            metrics.widthPixels, metrics.heightPixels);
                                    if (sample != null) {
                                        score = VisualCoordinateSignature.matchScore(
                                                coordinate.visualSignature, sample.pixels,
                                                sample.width, sample.height);
                                    }
                                } catch (RuntimeException | OutOfMemoryError ignored) {
                                    score = -1;
                                } finally {
                                    bitmap.recycle();
                                }
                            }
                            if (score < 0) {
                                handleCoordinateVisualResult(coordinate, generation,
                                        clickIndex, attempt, score, "captureDecodeFailed");
                            } else {
                                handleCoordinateVisualResult(coordinate, generation,
                                        clickIndex, attempt, score, null);
                            }
                        }

                        @Override
                        public void onFailure(int errorCode) {
                            handleCoordinateVisualResult(coordinate, generation,
                                    clickIndex, attempt, -1,
                                    "screenshotError error=" + errorCode);
                        }
                    });
        } catch (RuntimeException exception) {
            handleCoordinateVisualResult(coordinate, generation, clickIndex, attempt, -1,
                    "screenshotException error=" + exception.getClass().getSimpleName());
        }
    }

    private long acquireCoordinateScreenshotDelay() {
        synchronized (coordinateScreenshotLock) {
            long now = SystemClock.uptimeMillis();
            if (lastCoordinateScreenshotRequestUptimeMillis > 0) {
                long elapsed = now - lastCoordinateScreenshotRequestUptimeMillis;
                if (elapsed < VISUAL_COORDINATE_SCREENSHOT_INTERVAL_MILLIS) {
                    return VISUAL_COORDINATE_SCREENSHOT_INTERVAL_MILLIS - elapsed;
                }
            }
            lastCoordinateScreenshotRequestUptimeMillis = now;
            return 0L;
        }
    }

    private void handleCoordinateVisualResult(Coordinate coordinate, long generation,
                                              int clickIndex, int attempt, int score,
                                              String failureReason) {
        schedulePageTask(executorServiceSub, () -> {
            if (!isCoordinateExecutionValid(coordinate, generation)) {
                return;
            }
            boolean matched = failureReason == null
                    && score >= VisualCoordinateSignature.DEFAULT_MATCH_THRESHOLD;
            if (matched) {
                addCoordinateVisualLog(coordinate, clickIndex, attempt, score,
                        "matched", null);
                performCoordinateClick(coordinate, generation, clickIndex, true);
                return;
            }
            if (attempt < VISUAL_COORDINATE_MAX_ATTEMPTS) {
                addCoordinateVisualLog(coordinate, clickIndex, attempt, score,
                        "retry", failureReason == null ? null : "reason=" + failureReason);
                schedulePageTask(executorServiceSub,
                        () -> requestCoordinateVisualCheck(
                                coordinate, generation, clickIndex, attempt + 1),
                        VISUAL_COORDINATE_RETRY_DELAY_MILLIS);
                return;
            }
            addCoordinateVisualLog(coordinate, clickIndex, attempt, score,
                    "skipped", failureReason == null ? null : "reason=" + failureReason);
            String result = failureReason == null
                    ? "第" + (clickIndex + 1) + "次点击前未匹配，已跳过"
                    : "第" + (clickIndex + 1) + "次点击前截图失败，已跳过";
            addRuleLog("coordinate", coordinate.id, coordinate.appPackage,
                    coordinate.appActivity, "视觉校验", result,
                    coordinateLogDetails(coordinate));
        }, 0L);
    }

    private void addCoordinateVisualLog(Coordinate coordinate, int clickIndex,
                                        int attempt, int score, String result,
                                        String details) {
        if (!runtimeLoggingEnabled) {
            return;
        }
        StringBuilder builder = new StringBuilder("坐标视觉校验 ruleId=")
                .append(coordinate.id == null ? "未分配" : coordinate.id)
                .append(" click=").append(clickIndex + 1)
                .append(" attempt=").append(attempt)
                .append(" score=").append(score < 0 ? "不可用" : score)
                .append(" threshold=")
                .append(VisualCoordinateSignature.DEFAULT_MATCH_THRESHOLD)
                .append(" result=").append(result);
        if (details != null && !details.isEmpty()) {
            builder.append(' ').append(details);
        }
        addLog(builder.toString());
    }

    private static Map<String, Object> coordinateLogDetails(Coordinate coordinate) {
        Map<String, Object> details = new LinkedHashMap<>();
        details.put("xPosition", coordinate.xPosition);
        details.put("yPosition", coordinate.yPosition);
        details.put("clickDelay", coordinate.clickDelay);
        details.put("clickInterval", coordinate.clickInterval);
        details.put("clickNumber", coordinate.clickNumber);
        details.put("comment", coordinate.comment);
        details.put("visualVerification", !TextUtils.isEmpty(coordinate.visualSignature));
        details.put("visualSignatureLength",
                coordinate.visualSignature == null ? 0 : coordinate.visualSignature.length());
        return details;
    }

    private void scheduleWidgetClick(AccessibilityNodeInfo nodeInfo, Rect rect, Widget widget,
                                     List<Widget> widgets, long generation, int clickIndex, long delayMillis) {
        if (clickIndex >= widget.clickNumber || closed.get()) {
            return;
        }
        schedulePageTask(executorServiceSub, new Runnable() {
            @Override
            public void run() {
                if (generation != pageGeneration
                        || !onOffWidgetSub
                        || !currentActivity.equals(widget.appActivity)
                        || !nodeInfo.refresh()
                        || closed.get()) {
                    return;
                }
                int centerX = rect.centerX();
                int centerY = rect.centerY();
                boolean actionAccepted;
                String actionResult;
                if (widget.clickOnly) {
                    actionAccepted = click(centerX, centerY);
                    actionResult = actionAccepted ? "手势已提交" : "手势提交失败";
                } else if (nodeInfo.performAction(AccessibilityNodeInfo.ACTION_CLICK)) {
                    actionAccepted = true;
                    actionResult = "节点点击已执行";
                } else {
                    actionAccepted = click(centerX, centerY);
                    actionResult = actionAccepted ? "备用手势已提交" : "备用手势提交失败";
                }
                if (clickIndex == 0) {
                    widget.triggerCount += 1;
                    widget.lastTriggerTime = System.currentTimeMillis();
                    MyApplication.executeDatabase(() -> dataDao.updateWidget(widget));
                    addRuleLog("widget", widget.id, widget.appPackage, widget.appActivity,
                            widget.triggerReason, actionResult, widget);
                    if (!hasPendingWidgetRules(widgets)) {
                        setContentChangeEventsEnabled(false);
                    }
                }
                scheduleWidgetClick(nodeInfo, rect, widget, widgets, generation, clickIndex + 1,
                        widget.clickInterval <= 0 ? 10 : widget.clickInterval);
            }
        }, Math.max(0, delayMillis));
    }

    /**
     * 模拟
     * 点击
     */
    private boolean click(int x, int y) {
        Path path = new Path();
        path.moveTo(x, y);
        GestureDescription.Builder builder = new GestureDescription.Builder().addStroke(new GestureDescription.StrokeDescription(path, 0, 50));
        return service.dispatchGesture(builder.build(), null, null);
    }

    /**
     * 开启无障碍服务时调用
     * 获取运行时需要的数据
     */
    private void getRunningData() {
        List<AppDescribe> appDescribeList = dataDao.getEnabledAppDescribes();
        List<Coordinate> coordinates = dataDao.getEnabledCoordinates();
        List<Widget> widgets = dataDao.getEnabledWidgets();
        Map<String, AppDescribe> snapshot = AppDescribe.buildRuntimeSnapshot(
                appDescribeList, coordinates, widgets);
        replaceAppDescribeMap(snapshot);
        if (!currentPackage.isEmpty()) {
            appDescribe = snapshot.containsKey(currentPackage)
                    ? snapshot.get(currentPackage) : new AppDescribe();
            deactivateCurrentPage(false);
        }
    }

    private Map<String, AppDescribe> copyAppDescribeMap() {
        synchronized (appDescribeMapLock) {
            return new HashMap<>(appDescribeMap);
        }
    }

    private void replaceAppDescribeMap(Map<String, AppDescribe> snapshot) {
        synchronized (appDescribeMapLock) {
            appDescribeMap = RuntimeAppDescribeMap.immutableSnapshot(snapshot);
        }
    }

    private void putAppDescribeInRuntimeMap(String packageName, AppDescribe describe) {
        if (TextUtils.isEmpty(packageName) || describe == null) {
            return;
        }
        synchronized (appDescribeMapLock) {
            appDescribeMap = RuntimeAppDescribeMap.withEntry(
                    appDescribeMap, packageName, describe);
        }
    }

    /**
     * 创建规则时调用
     */
    public void showAddDataWindow(boolean capture) {
        if (capture) {
            requestQuickLayoutCapture();
            return;
        }
        showAddDataWindow((AccessibilityLayoutSnapshot) null);
    }

    @SuppressLint("ClickableViewAccessibility")
    private void showAddDataWindow(AccessibilityLayoutSnapshot initialSnapshot) {
        if (closed.get()) {
            return;
        }
        if (viewClickPosition != null || addDataBinding != null || widgetSelectBinding != null) {
            return;
        }
        captureLayoutHiddenByUser = false;
        frozenCoordinateSelected = false;
        final Widget widgetSelect = new Widget();
        activeWidgetSelection = widgetSelect;
        final Coordinate coordinateSelect = new Coordinate();
        activeCoordinateSelection = coordinateSelect;
        final LayoutInflater inflater = LayoutInflater.from(service);

        addDataBinding = ViewAddDataBinding.inflate(inflater);
        widgetSelectBinding = ViewWidgetSelectBinding.inflate(inflater);

        viewClickPosition = new ImageView(service);
        viewClickPosition.setImageResource(R.drawable.p);

        DisplayMetrics metrics = new DisplayMetrics();
        windowManager.getDefaultDisplay().getRealMetrics(metrics);
        int width = Math.min(metrics.heightPixels, metrics.widthPixels);

        aParams = new WindowManager.LayoutParams();
        aParams.type = WindowManager.LayoutParams.TYPE_ACCESSIBILITY_OVERLAY;
        aParams.format = PixelFormat.TRANSPARENT;
        aParams.gravity = Gravity.START | Gravity.TOP;
        aParams.flags = WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN
                | WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE
                | WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS;
        aParams.width = width;
        aParams.height = WindowManager.LayoutParams.WRAP_CONTENT;
        aParams.x = (metrics.widthPixels - aParams.width) / 2;
        aParams.y = metrics.heightPixels / 5 * 3;
        aParams.alpha = 0.9f;

        bParams = new WindowManager.LayoutParams();
        bParams.type = WindowManager.LayoutParams.TYPE_ACCESSIBILITY_OVERLAY;
        bParams.format = PixelFormat.TRANSPARENT;
        bParams.gravity = Gravity.START | Gravity.TOP;
        bParams.width = metrics.widthPixels;
        bParams.height = metrics.heightPixels;
        bParams.flags = WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN
                | WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS
                | WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE
                | WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE;
        bParams.alpha = 0f;

        cParams = new WindowManager.LayoutParams();
        cParams.type = WindowManager.LayoutParams.TYPE_ACCESSIBILITY_OVERLAY;
        cParams.format = PixelFormat.TRANSPARENT;
        cParams.flags = WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN
                | WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS
                | WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE
                | WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE;
        cParams.gravity = Gravity.START | Gravity.TOP;
        cParams.width = cParams.height = width / 4;
        cParams.x = (metrics.widthPixels - cParams.width) / 2;
        cParams.y = (metrics.heightPixels - cParams.height) / 2;
        cParams.alpha = 0f;

        addDataBinding.getRoot().setOnTouchListener(new View.OnTouchListener() {
            int startRowX = 0, startRowY = 0, startLpX = 0, startLpY = 0;
            int preRowX = 0, preRowY = 0;
            long preEventTime = 0;
            boolean openPageFlag = false;
            final Pattern pattern = Pattern.compile("[A-Za-z0-9_.]+");

            @Override
            public boolean onTouch(View v, MotionEvent event) {
                v.post(new Runnable() {
                    final int action = event.getAction();
                    final int rowX = Math.round(event.getRawX());
                    final int rowY = Math.round(event.getRawY());

                    @Override
                    public void run() {
                        if (addDataBinding == null) {
                            return;
                        }
                        switch (action) {
                            case MotionEvent.ACTION_DOWN:
                                startRowX = rowX;
                                startRowY = rowY;
                                startLpX = aParams.x;
                                startLpY = aParams.y;
                                break;
                            case MotionEvent.ACTION_MOVE:
                                aParams.x = startLpX + (rowX - startRowX);
                                aParams.y = startLpY + (rowY - startRowY);
                                windowManager.updateViewLayout(v, aParams);
                                break;
                            case MotionEvent.ACTION_UP:
                                DisplayMetrics metrics = new DisplayMetrics();
                                windowManager.getDefaultDisplay().getRealMetrics(metrics);
                                aParams.x = Math.max(aParams.x, 0);
                                aParams.x = Math.min(aParams.x, metrics.widthPixels - addDataBinding.getRoot().getWidth());
                                aParams.y = Math.max(aParams.y, 0);
                                aParams.y = Math.min(aParams.y, metrics.heightPixels - addDataBinding.getRoot().getHeight());
                                windowManager.updateViewLayout(v, aParams);
                                break;
                        }
                    }
                });
                // 双击打开规则管理页面
                if (event.getAction() == MotionEvent.ACTION_DOWN) {
                    if (Math.abs(event.getEventTime() - preEventTime) < 500) {
                        if (!openPageFlag && Math.abs(event.getRawX() - preRowX) < 100 && Math.abs(event.getRawY() - preRowY) < 100) {
                            Matcher matcher = pattern.matcher(addDataBinding.pkgName.getText().toString());
                            if (matcher.find()) {
                                if (appDescribeMap.containsKey(matcher.group())) {
                                    Intent intent = new Intent(service, EditDataActivity.class);
                                    intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TOP);
                                    intent.putExtra("packageName", matcher.group());
                                    service.startActivity(intent);
                                } else {
                                    Intent intent = new Intent(service, ListDataActivity.class);
                                    intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TOP);
                                    service.startActivity(intent);
                                }
                                if (bParams.alpha != 0) {
                                    addDataBinding.switchWid.callOnClick();
                                }
                            }
                            openPageFlag = true;
                        }
                    } else {
                        openPageFlag = false;
                    }
                    preRowX = Math.round(event.getRawX());
                    preRowY = Math.round(event.getRawY());
                    preEventTime = event.getEventTime();
                }
                return true;
            }
        });
        viewClickPosition.setOnTouchListener(new View.OnTouchListener() {
            int startRowX = 0, startRowY = 0, startLpX = 0, startLpY = 0;
            final int width = cParams.width / 2;
            final int height = cParams.height / 2;
            final Pattern pattern = Pattern.compile("[A-Za-z0-9_.]+");

            @Override
            public boolean onTouch(View v, MotionEvent event) {
                v.post(new Runnable() {
                    final int action = event.getAction();
                    final int rowX = Math.round(event.getRawX());
                    final int rowY = Math.round(event.getRawY());

                    @Override
                    public void run() {
                        if (viewClickPosition == null) {
                            return;
                        }
                        switch (action) {
                            case MotionEvent.ACTION_DOWN:
                                cParams.alpha = 0.9f;
                                windowManager.updateViewLayout(v, cParams);
                                startRowX = rowX;
                                startRowY = rowY;
                                startLpX = cParams.x;
                                startLpY = cParams.y;
                                break;
                            case MotionEvent.ACTION_MOVE:
                                cParams.x = startLpX + (rowX - startRowX);
                                cParams.y = startLpY + (rowY - startRowY);
                                windowManager.updateViewLayout(v, cParams);
                                coordinateSelect.appPackage = currentPackage;
                                coordinateSelect.appActivity = currentActivity;
                                coordinateSelect.xPosition = cParams.x + width;
                                coordinateSelect.yPosition = cParams.y + height;
                                coordinateSelect.visualSignature = null;
                                frozenCoordinateSelected = false;
                                addDataBinding.pkgName.setText(coordinateSelect.appPackage);
                                addDataBinding.actName.setText(coordinateSelect.appActivity);
                                addDataBinding.saveAim.setEnabled(pattern.matcher(coordinateSelect.appPackage).matches());
                                addDataBinding.xy.setText("X轴：" + String.format("%-4d", coordinateSelect.xPosition) + "    " + "Y轴：" + String.format("%-4d", coordinateSelect.yPosition));
                                addDataBinding.widget.setText("已手动移动坐标，点击前视觉校验未启用");
                                break;
                            case MotionEvent.ACTION_UP:
                                cParams.alpha = 0.6f;
                                windowManager.updateViewLayout(v, cParams);
                                break;
                        }
                    }
                });
                return true;
            }
        });
        addDataBinding.switchWid.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                if (bParams.alpha == 0) {
                    if (!showFrozenCaptureLayout()) {
                        requestManualLayoutCapture(widgetSelect);
                    }
                } else {
                    hideCapturedLayout();
                }
            }
        });
        addDataBinding.switchAim.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                Button button = (Button) v;
                if (cParams.alpha == 0) {
                    coordinateSelect.appPackage = currentPackage;
                    coordinateSelect.appActivity = currentActivity;
                    cParams.alpha = 0.6f;
                    cParams.flags = WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN
                            | WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS
                            | WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE;
                    windowManager.updateViewLayout(viewClickPosition, cParams);
                    addDataBinding.pkgName.setText(coordinateSelect.appPackage);
                    addDataBinding.actName.setText(coordinateSelect.appActivity);
                    button.setText("隐藏准星");
                } else {
                    cParams.alpha = 0f;
                    cParams.flags = WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN
                            | WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS
                            | WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE
                            | WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE;
                    windowManager.updateViewLayout(viewClickPosition, cParams);
                    addDataBinding.saveAim.setEnabled(false);
                    button.setText("显示准星");
                }
            }
        });
        addDataBinding.saveWid.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                prePackage = currentPackage;
                preActivity = currentActivity;
                Runnable runnable = new Runnable() {
                    AppDescribe appDescribeTemp;

                    @Override
                    public void run() {
                        Map<String, Boolean> propsMap = new LinkedHashMap<>();
                        if (Objects.nonNull(widgetSelect.widgetRect)) {
                            propsMap.put("Bonus", true);
                        }
                        if (Objects.nonNull(widgetSelect.widgetNodeId)) {
                            propsMap.put("NodeId", false);
                        }
                        if (StrUtil.isNotBlank(widgetSelect.widgetViewId)) {
                            propsMap.put("ViewId", true);
                        }
                        if (StrUtil.isNotBlank(widgetSelect.widgetDescribe)) {
                            propsMap.put("Describe", true);
                        }
                        if (StrUtil.isNotBlank(widgetSelect.widgetText)) {
                            propsMap.put("Text", true);
                        }
                        int index = 0;
                        String[] keys = new String[propsMap.size()];
                        boolean[] values = new boolean[propsMap.size()];
                        for (Map.Entry<String, Boolean> entry : propsMap.entrySet()) {
                            keys[index] = entry.getKey();
                            values[index] = entry.getValue();
                            index++;
                        }
                        AlertDialog.Builder alertDialogBuilder = new AlertDialog.Builder(service);
                        alertDialogBuilder.setTitle("请选择需要保存的属性");
                        alertDialogBuilder.setMultiChoiceItems(keys, values, new DialogInterface.OnMultiChoiceClickListener() {
                            @Override
                            public void onClick(DialogInterface dialog, int which, boolean isChecked) {
                                propsMap.put(keys[which], isChecked);
                                values[which] = isChecked;
                            }
                        });
                        alertDialogBuilder.setNegativeButton("取消", null);
                        alertDialogBuilder.setPositiveButton("确定", new DialogInterface.OnClickListener() {
                            @Override
                            public void onClick(DialogInterface dialog, int which) {
                                appDescribeTemp = appDescribeMap.get(widgetSelect.appPackage);
                                if (appDescribeTemp == null) {
                                    appDescribeTemp = new AppDescribe();
                                    appDescribeTemp.appPackage = widgetSelect.appPackage;
                                    try {
                                        PackageInfo packageInfo = packageManager.getPackageInfo(widgetSelect.appPackage, PackageManager.GET_META_DATA);
                                        appDescribeTemp.appName = packageManager.getApplicationLabel(packageInfo.applicationInfo).toString();
                                    } catch (PackageManager.NameNotFoundException e) {
                                        // e.printStackTrace();
                                    }
                                    appDescribeTemp.id = dataDao.insertAppDescribe(appDescribeTemp);
                                    putAppDescribeInRuntimeMap(appDescribeTemp.appPackage, appDescribeTemp);
                                }
                                Widget temWidget = new Widget(widgetSelect);
                                temWidget.createTime = System.currentTimeMillis();
                                temWidget.widgetRect = Boolean.TRUE.equals(propsMap.get("Bonus")) ? temWidget.widgetRect : null;
                                temWidget.widgetNodeId = Boolean.TRUE.equals(propsMap.get("NodeId")) ? temWidget.widgetNodeId : null;
                                temWidget.widgetViewId = Boolean.TRUE.equals(propsMap.get("ViewId")) ? temWidget.widgetViewId : "";
                                temWidget.widgetDescribe = Boolean.TRUE.equals(propsMap.get("Describe"))
                                        ? Pattern.quote(temWidget.widgetDescribe) : "";
                                temWidget.widgetText = Boolean.TRUE.equals(propsMap.get("Text"))
                                        ? Pattern.quote(temWidget.widgetText) : "";
                                temWidget.validatePatterns();
                                dataDao.insertWidget(temWidget);
                                addDataBinding.saveWid.setEnabled(false);
                                addDataBinding.pkgName.setText(widgetSelect.appPackage + " (以下控件数据已保存)");
                                appDescribeTemp.getWidgetFromDatabase(dataDao);
                                if (!appDescribeTemp.widgetOnOff) {
                                    showWarningDialog(new Runnable() {
                                        @Override
                                        public void run() {
                                            appDescribeTemp.widgetOnOff = true;
                                            dataDao.updateAppDescribe(appDescribeTemp);
                                        }
                                    }, service.getString(R.string.widgetOffWarning));
                                }
                            }
                        });
                        alertDialogBuilder.setOnDismissListener(new DialogInterface.OnDismissListener() {
                            @Override
                            public void onDismiss(DialogInterface dialog) {
                                currentPackage = prePackage;
                                currentActivity = preActivity;
                            }
                        });
                        Dialog dialog = alertDialogBuilder.create();
                        dialog.getWindow().setType(WindowManager.LayoutParams.TYPE_ACCESSIBILITY_OVERLAY);
                        dialog.show();
                    }
                };
                if (pkgSuggestNotOnList.contains(widgetSelect.appPackage)) {
                    showWarningDialog(runnable, service.getString(R.string.addWarning));
                } else {
                    runnable.run();
                }
            }
        });
        addDataBinding.saveAim.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                prePackage = currentPackage;
                preActivity = currentActivity;
                Runnable runnable = new Runnable() {
                    AppDescribe appDescribeTemp;

                    @Override
                    public void run() {
                        appDescribeTemp = appDescribeMap.get(coordinateSelect.appPackage);
                        if (appDescribeTemp == null) {
                            appDescribeTemp = new AppDescribe();
                            appDescribeTemp.appPackage = coordinateSelect.appPackage;
                            try {
                                PackageInfo packageInfo = packageManager.getPackageInfo(coordinateSelect.appPackage, PackageManager.GET_META_DATA);
                                appDescribeTemp.appName = packageManager.getApplicationLabel(packageInfo.applicationInfo).toString();
                            } catch (PackageManager.NameNotFoundException e) {
                                // e.printStackTrace();
                            }
                            appDescribeTemp.id = dataDao.insertAppDescribe(appDescribeTemp);
                            putAppDescribeInRuntimeMap(appDescribeTemp.appPackage, appDescribeTemp);
                        }
                        Coordinate temCoordinate = new Coordinate(coordinateSelect);
                        temCoordinate.createTime = System.currentTimeMillis();
                        dataDao.insertCoordinate(temCoordinate);
                        addDataBinding.saveAim.setEnabled(false);
                        addDataBinding.pkgName.setText(coordinateSelect.appPackage + " (以下坐标数据已保存)");
                        appDescribeTemp.getCoordinateFromDatabase(dataDao);
                        if (!appDescribeTemp.coordinateOnOff) {
                            showWarningDialog(new Runnable() {
                                @Override
                                public void run() {
                                    appDescribeTemp.coordinateOnOff = true;
                                    dataDao.updateAppDescribe(appDescribeTemp);
                                }
                            }, service.getString(R.string.coordinateOffWarning));
                        }
                    }
                };
                if (pkgSuggestNotOnList.contains(coordinateSelect.appPackage)) {
                    showWarningDialog(runnable, service.getString(R.string.addWarning));
                } else {
                    runnable.run();
                }
            }
        });
        addDataBinding.quit.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                layoutCaptureGeneration.incrementAndGet();
                windowManager.removeViewImmediate(widgetSelectBinding.getRoot());
                windowManager.removeViewImmediate(addDataBinding.getRoot());
                windowManager.removeViewImmediate(viewClickPosition);
                widgetSelectBinding = null;
                addDataBinding = null;
                viewClickPosition = null;
                activeWidgetSelection = null;
                activeCoordinateSelection = null;
                captureLayoutHiddenByUser = false;
                frozenCoordinateSelected = false;
                clearFrozenScreenCapture();
            }
        });
        windowManager.addView(widgetSelectBinding.getRoot(), bParams);
        windowManager.addView(addDataBinding.getRoot(), aParams);
        windowManager.addView(viewClickPosition, cParams);

        if (initialSnapshot != null) {
            renderCapturedLayout(initialSnapshot, widgetSelect);
        } else {
            showFrozenCaptureLayout();
        }
    }

    private void preloadPackageCatalog() {
        try {
            Set<String> packages = PackageCatalog.getSuggestedRestrictedPackages(service);
            if (!closed.get()) {
                pkgSuggestNotOnList = packages;
            }
        } catch (RuntimeException ignored) {
            // The warning is advisory. Rule capture must remain available if a
            // vendor PackageManager rejects one of the catalogue queries.
        }
    }

    @SuppressLint("NewApi")
    private void requestFrozenScreenCapture(long captureGeneration,
                                            String packageAtRequest,
                                            String activityAtRequest,
                                            long requestStartedNanos) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.R) {
            addCaptureLog("screenCapture", packageAtRequest, true,
                    elapsedMillis(requestStartedNanos), "outcome=unsupported sdk=" + Build.VERSION.SDK_INT);
            return;
        }
        long requestStartedUptimeMillis = SystemClock.uptimeMillis();
        try {
            service.takeScreenshot(Display.DEFAULT_DISPLAY, executorServiceCapture,
                    new AccessibilityService.TakeScreenshotCallback() {
                        @Override
                        public void onSuccess(AccessibilityService.ScreenshotResult screenshotResult) {
                            Bitmap bitmap = copyScreenshotBitmap(screenshotResult);
                            if (bitmap == null) {
                                addCaptureLog("screenCapture", packageAtRequest, true,
                                        elapsedMillis(requestStartedNanos), "outcome=decodeFailed");
                                return;
                            }
                            long capturedAfterMillis = Math.max(0L,
                                    screenshotResult.getTimestamp()
                                            - requestStartedUptimeMillis);
                            if (capturedAfterMillis
                                    > QUICK_CAPTURE_MAX_LIVE_SNAPSHOT_AGE_MILLIS) {
                                bitmap.recycle();
                                addCaptureLog("screenCapture", packageAtRequest, true,
                                        elapsedMillis(requestStartedNanos),
                                        "outcome=stale captureAgeMs=" + capturedAfterMillis);
                                return;
                            }
                            FrozenScreenCapture capture = new FrozenScreenCapture(
                                    bitmap, packageAtRequest, activityAtRequest,
                                    captureGeneration, requestStartedNanos,
                                    capturedAfterMillis);
                            if (!registerPendingFrozenScreenCapture(capture)) {
                                return;
                            }
                            boolean posted = mainHandler.post(() -> {
                                if (pendingFrozenCapture.compareAndSet(capture, null)) {
                                    installFrozenScreenCapture(capture);
                                }
                            });
                            if (!posted && pendingFrozenCapture.compareAndSet(capture, null)) {
                                capture.recycle();
                            }
                        }

                        @Override
                        public void onFailure(int errorCode) {
                            addCaptureLog("screenCapture", packageAtRequest, true,
                                    elapsedMillis(requestStartedNanos),
                                    "outcome=failed error=" + errorCode);
                        }
                    });
        } catch (RuntimeException exception) {
            addCaptureLog("screenCapture", packageAtRequest, true,
                    elapsedMillis(requestStartedNanos),
                    "outcome=exception error=" + exception.getClass().getSimpleName());
        }
    }

    private boolean registerPendingFrozenScreenCapture(FrozenScreenCapture capture) {
        while (true) {
            if (closed.get() || capture.generation != layoutCaptureGeneration.get()) {
                capture.recycle();
                return false;
            }
            FrozenScreenCapture previousPending = pendingFrozenCapture.get();
            if (previousPending != null
                    && previousPending.generation > capture.generation) {
                capture.recycle();
                return false;
            }
            if (!pendingFrozenCapture.compareAndSet(previousPending, capture)) {
                continue;
            }
            if (previousPending != null && previousPending != capture) {
                previousPending.recycle();
            }
            return true;
        }
    }

    @SuppressLint("NewApi")
    private static Bitmap copyScreenshotBitmap(
            AccessibilityService.ScreenshotResult screenshotResult) {
        HardwareBuffer hardwareBuffer = screenshotResult == null
                ? null : screenshotResult.getHardwareBuffer();
        if (hardwareBuffer == null) {
            return null;
        }
        try {
            ColorSpace colorSpace = screenshotResult.getColorSpace();
            if (colorSpace == null) {
                colorSpace = ColorSpace.get(ColorSpace.Named.SRGB);
            }
            Bitmap hardwareBitmap = Bitmap.wrapHardwareBuffer(hardwareBuffer, colorSpace);
            return hardwareBitmap == null
                    ? null : hardwareBitmap.copy(Bitmap.Config.ARGB_8888, false);
        } catch (RuntimeException | OutOfMemoryError ignored) {
            return null;
        } finally {
            hardwareBuffer.close();
        }
    }

    private void installFrozenScreenCapture(FrozenScreenCapture capture) {
        if (capture == null) {
            return;
        }
        if (closed.get() || capture.generation != layoutCaptureGeneration.get()) {
            capture.recycle();
            return;
        }
        FrozenScreenCapture previousCapture = activeFrozenCapture;
        activeFrozenCapture = capture;
        if (previousCapture != null && previousCapture != capture) {
            previousCapture.recycle();
        }
        addCaptureLog("screenCapture", capture.appPackage, true,
                elapsedMillis(capture.requestStartedNanos),
                "outcome=ready size=" + capture.bitmap.getWidth()
                        + "x" + capture.bitmap.getHeight()
                        + " captureAgeMs=" + capture.capturedAfterMillis);
        if (!captureLayoutHiddenByUser
                && addDataBinding != null
                && widgetSelectBinding != null
                && activeCoordinateSelection != null) {
            showFrozenCaptureLayout();
        }
    }

    private boolean showFrozenCaptureLayout() {
        FrozenScreenCapture capture = activeFrozenCapture;
        ViewAddDataBinding currentAddBinding = addDataBinding;
        ViewWidgetSelectBinding currentWidgetBinding = widgetSelectBinding;
        Coordinate coordinateSelect = activeCoordinateSelection;
        if (capture == null
                || capture.bitmap.isRecycled()
                || currentAddBinding == null
                || currentWidgetBinding == null
                || coordinateSelect == null
                || bParams == null) {
            return false;
        }
        if (frozenCaptureView == null || frozenCaptureView.getParent() != currentWidgetBinding.frame) {
            ImageView screenshotView = new ImageView(service);
            screenshotView.setScaleType(ImageView.ScaleType.FIT_XY);
            screenshotView.setImageBitmap(capture.bitmap);
            screenshotView.setContentDescription("冻结画面坐标选择");
            screenshotView.setClickable(true);
            screenshotView.setOnTouchListener((view, event) -> {
                if (event.getAction() == MotionEvent.ACTION_UP) {
                    view.performClick();
                    selectFrozenCoordinate(activeFrozenCapture,
                            Math.round(event.getRawX()), Math.round(event.getRawY()));
                }
                return true;
            });
            currentWidgetBinding.frame.addView(screenshotView, 0,
                    new FrameLayout.LayoutParams(
                            FrameLayout.LayoutParams.MATCH_PARENT,
                            FrameLayout.LayoutParams.MATCH_PARENT));
            frozenCaptureView = screenshotView;
        } else {
            frozenCaptureView.setImageBitmap(capture.bitmap);
        }
        bParams.alpha = 1f;
        bParams.flags = WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN
                | WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS
                | WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE;
        windowManager.updateViewLayout(currentWidgetBinding.getRoot(), bParams);
        currentAddBinding.switchWid.setEnabled(true);
        currentAddBinding.switchWid.setText("隐藏布局");
        Widget selectedWidget = activeWidgetSelection;
        if (selectedWidget != null && selectedWidget.widgetRect != null) {
            currentAddBinding.saveWid.setEnabled(PACKAGE_NAME_PATTERN.matcher(
                    selectedWidget.appPackage == null ? "" : selectedWidget.appPackage).matches());
        } else if (!frozenCoordinateSelected) {
            currentAddBinding.widget.setText("未暴露可点击控件时，可直接点击冻结画面选择坐标");
        }
        captureLayoutHiddenByUser = false;
        addCaptureLog("frozenRender", capture.appPackage, true,
                elapsedMillis(capture.requestStartedNanos),
                "size=" + capture.bitmap.getWidth() + "x" + capture.bitmap.getHeight());
        return true;
    }

    private void selectFrozenCoordinate(FrozenScreenCapture capture, int rawX, int rawY) {
        ViewAddDataBinding currentAddBinding = addDataBinding;
        ViewWidgetSelectBinding currentWidgetBinding = widgetSelectBinding;
        Coordinate coordinateSelect = activeCoordinateSelection;
        if (capture == null
                || capture != activeFrozenCapture
                || currentAddBinding == null
                || currentWidgetBinding == null
                || coordinateSelect == null
                || bParams == null) {
            return;
        }
        int maximumX = Math.max(0, bParams.width - 1);
        int maximumY = Math.max(0, bParams.height - 1);
        int selectedX = Math.max(0, Math.min(rawX, maximumX));
        int selectedY = Math.max(0, Math.min(rawY, maximumY));
        coordinateSelect.appPackage = capture.appPackage;
        coordinateSelect.appActivity = capture.appActivity;
        coordinateSelect.xPosition = selectedX;
        coordinateSelect.yPosition = selectedY;
        VisualRegionSample visualSample = sampleVisualCoordinateRegion(
                capture.bitmap, selectedX, selectedY, bParams.width, bParams.height);
        coordinateSelect.visualSignature = visualSample == null
                ? null : VisualCoordinateSignature.create(
                        visualSample.pixels, visualSample.width, visualSample.height);
        Widget widgetSelect = activeWidgetSelection;
        if (widgetSelect != null) {
            widgetSelect.widgetClickable = false;
            widgetSelect.widgetRect = null;
            widgetSelect.widgetNodeId = null;
            widgetSelect.widgetViewId = "";
            widgetSelect.widgetDescribe = "";
            widgetSelect.widgetText = "";
        }
        currentAddBinding.pkgName.setText(coordinateSelect.appPackage);
        currentAddBinding.actName.setText(coordinateSelect.appActivity);
        currentAddBinding.xy.setText("X轴：" + String.format("%-4d", selectedX)
                + "    Y轴：" + String.format("%-4d", selectedY));
        boolean visualVerificationEnabled =
                VisualCoordinateSignature.isValid(coordinateSelect.visualSignature);
        currentAddBinding.saveAim.setEnabled(visualVerificationEnabled
                && PACKAGE_NAME_PATTERN.matcher(coordinateSelect.appPackage).matches());
        currentAddBinding.saveWid.setEnabled(false);
        currentAddBinding.widget.setText(visualVerificationEnabled
                ? "已从冻结画面选择坐标，并启用点击前视觉校验"
                : "目标区域特征不足，无法安全校验，请重新选择坐标");
        frozenCoordinateSelected = true;
        showFrozenCoordinateMarker(currentWidgetBinding, selectedX, selectedY);
        addCaptureLog("frozenSelect", capture.appPackage, true,
                elapsedMillis(capture.requestStartedNanos),
                "x=" + selectedX + " y=" + selectedY
                        + " visual=" + visualVerificationEnabled
                        + (visualSample == null ? "" : " region="
                                + visualSample.width + "x" + visualSample.height));
    }

    private static VisualRegionSample sampleVisualCoordinateRegion(
            Bitmap bitmap, int screenX, int screenY, int screenWidth, int screenHeight) {
        if (bitmap == null || bitmap.isRecycled()) {
            return null;
        }
        int bitmapWidth = bitmap.getWidth();
        int bitmapHeight = bitmap.getHeight();
        int bitmapX = mapScreenCoordinate(screenX, screenWidth, bitmapWidth);
        int bitmapY = mapScreenCoordinate(screenY, screenHeight, bitmapHeight);
        VisualCoordinateSignature.Region region = VisualCoordinateSignature.calculateRegion(
                bitmapWidth, bitmapHeight, bitmapX, bitmapY);
        if (region == null) {
            return null;
        }
        try {
            int[] pixels = new int[region.getWidth() * region.getHeight()];
            bitmap.getPixels(pixels, 0, region.getWidth(),
                    region.getLeft(), region.getTop(), region.getWidth(), region.getHeight());
            return new VisualRegionSample(pixels, region.getWidth(), region.getHeight());
        } catch (RuntimeException | OutOfMemoryError ignored) {
            return null;
        }
    }

    private static int mapScreenCoordinate(int coordinate, int screenSize, int bitmapSize) {
        if (bitmapSize <= 1) {
            return 0;
        }
        if (screenSize <= 1) {
            return Math.max(0, Math.min(coordinate, bitmapSize - 1));
        }
        int clampedCoordinate = Math.max(0, Math.min(coordinate, screenSize - 1));
        return (int) ((long) clampedCoordinate * (bitmapSize - 1) / (screenSize - 1));
    }

    private void showFrozenCoordinateMarker(ViewWidgetSelectBinding binding, int x, int y) {
        if (binding == null || bParams == null) {
            return;
        }
        if (frozenCoordinateMarker != null && frozenCoordinateMarker.getParent() == binding.frame) {
            binding.frame.removeView(frozenCoordinateMarker);
        }
        int markerSize = Math.max(16,
                Math.round(20 * service.getResources().getDisplayMetrics().density));
        FrameLayout.LayoutParams markerParams = new FrameLayout.LayoutParams(markerSize, markerSize);
        markerParams.leftMargin = Math.max(0,
                Math.min(x - markerSize / 2, Math.max(0, bParams.width - markerSize)));
        markerParams.topMargin = Math.max(0,
                Math.min(y - markerSize / 2, Math.max(0, bParams.height - markerSize)));
        View marker = new View(service);
        marker.setBackgroundResource(R.drawable.node_focus);
        marker.setClickable(false);
        marker.setFocusable(false);
        binding.frame.addView(marker, markerParams);
        frozenCoordinateMarker = marker;
    }

    private void clearFrozenScreenCapture() {
        if (frozenCaptureView != null) {
            frozenCaptureView.setImageDrawable(null);
            if (frozenCaptureView.getParent() instanceof FrameLayout) {
                ((FrameLayout) frozenCaptureView.getParent()).removeView(frozenCaptureView);
            }
            frozenCaptureView = null;
        }
        if (frozenCoordinateMarker != null) {
            if (frozenCoordinateMarker.getParent() instanceof FrameLayout) {
                ((FrameLayout) frozenCoordinateMarker.getParent()).removeView(frozenCoordinateMarker);
            }
            frozenCoordinateMarker = null;
        }
        FrozenScreenCapture capture = activeFrozenCapture;
        activeFrozenCapture = null;
        if (capture != null) {
            capture.recycle();
        }
        FrozenScreenCapture pendingCapture = pendingFrozenCapture.getAndSet(null);
        if (pendingCapture != null) {
            pendingCapture.recycle();
        }
    }

    private boolean isQuickSnapshotTimely(long requestStartedNanos,
                                          long captureCompletedNanos) {
        return elapsedMillis(requestStartedNanos, captureCompletedNanos)
                <= QUICK_CAPTURE_MAX_LIVE_SNAPSHOT_AGE_MILLIS;
    }

    private void logSkippedQuickSnapshot(String capturePhase, String packageName,
                                         long requestStartedNanos,
                                         long captureCompletedNanos,
                                         AccessibilityLayoutSnapshot snapshot) {
        addCaptureLog("snapshotSkipped", packageName, true,
                elapsedMillis(requestStartedNanos, captureCompletedNanos),
                "capture=" + capturePhase
                        + " reason=stale"
                        + " nodes=" + snapshot.getNodes().size()
                        + " interactive=" + snapshot.getInteractiveNodeCount());
    }

    private void requestQuickLayoutCapture() {
        if (closed.get()
                || viewClickPosition != null
                || addDataBinding != null
                || widgetSelectBinding != null
                || !quickCaptureInProgress.compareAndSet(false, true)) {
            return;
        }
        long captureGeneration = layoutCaptureGeneration.incrementAndGet();
        String packageAtRequest = currentPackage;
        String activityAtRequest = currentActivity;
        long requestStartedNanos = System.nanoTime();
        AtomicReference<AccessibilityLayoutSnapshot> bestSnapshot = new AtomicReference<>();
        clearFrozenScreenCapture();
        addCaptureLog("request", packageAtRequest, true, 0L,
                "activity=" + valueOrUnknown(activityAtRequest));
        requestFrozenScreenCapture(captureGeneration, packageAtRequest,
                activityAtRequest, requestStartedNanos);
        showQuickCapturePanel(null, packageAtRequest, "immediate", requestStartedNanos);
        quickCaptureInProgress.set(false);
        captureLayoutAsync(packageAtRequest, activityAtRequest, true,
                false, "initial", (snapshot, captureCompletedNanos) -> {
            if (closed.get() || captureGeneration != layoutCaptureGeneration.get()) {
                return;
            }
            boolean snapshotTimely = isQuickSnapshotTimely(
                    requestStartedNanos, captureCompletedNanos);
            if (snapshot != null && snapshot.hasSelectableContent() && snapshotTimely) {
                AccessibilityLayoutSnapshot previousSnapshot = bestSnapshot.get();
                if (previousSnapshot == null
                        || capturedNodeQuality(snapshot)
                        >= capturedNodeQuality(previousSnapshot)) {
                    bestSnapshot.set(snapshot);
                    renderQuickCapturedLayout(snapshot, captureGeneration, false,
                            "lateRender", requestStartedNanos);
                }
            } else if (snapshot != null && snapshot.hasSelectableContent()) {
                logSkippedQuickSnapshot("initial", packageAtRequest,
                        requestStartedNanos, captureCompletedNanos, snapshot);
            }
        });
        for (int attempt = 1; attempt <= QUICK_CAPTURE_MAX_REFRESH_ATTEMPTS; attempt++) {
            scheduleQuickLayoutRefresh(packageAtRequest, activityAtRequest,
                    captureGeneration, requestStartedNanos, bestSnapshot, attempt);
        }
    }

    private void scheduleQuickLayoutRefresh(String packageAtRequest,
                                             String activityAtRequest,
                                             long captureGeneration,
                                             long requestStartedNanos,
                                             AtomicReference<AccessibilityLayoutSnapshot> bestSnapshot,
                                             int attempt) {
        if (attempt > QUICK_CAPTURE_MAX_REFRESH_ATTEMPTS
                || closed.get()
                || captureGeneration != layoutCaptureGeneration.get()) {
            return;
        }
        long delayMillis = attempt == 1
                ? QUICK_CAPTURE_REFRESH_FIRST_DELAY_MILLIS
                : QUICK_CAPTURE_REFRESH_SECOND_DELAY_MILLIS;
        try {
            executorServiceCapture.schedule(() -> {
                if (closed.get()
                        || captureGeneration != layoutCaptureGeneration.get()
                        || frozenCoordinateSelected
                        || !shouldRefreshQuickSnapshot(bestSnapshot.get(), attempt)) {
                    return;
                }
                addCaptureLog("refreshRequest", packageAtRequest, true,
                        elapsedMillis(requestStartedNanos), "attempt=" + attempt);
                captureLayoutAsync(packageAtRequest, activityAtRequest, true,
                        true, "refresh" + attempt,
                        (refreshedSnapshot, captureCompletedNanos) -> {
                    if (closed.get() || captureGeneration != layoutCaptureGeneration.get()) {
                        return;
                    }
                    AccessibilityLayoutSnapshot previousSnapshot = bestSnapshot.get();
                    if (refreshedSnapshot != null
                            && refreshedSnapshot.hasSelectableContent()
                            && isQuickSnapshotTimely(
                            requestStartedNanos, captureCompletedNanos)
                            && (previousSnapshot == null
                            || capturedNodeQuality(refreshedSnapshot)
                            >= capturedNodeQuality(previousSnapshot))) {
                        bestSnapshot.set(refreshedSnapshot);
                        if (viewClickPosition != null
                                && addDataBinding != null
                                && widgetSelectBinding != null
                                && activeWidgetSelection != null
                                && activeWidgetSelection.widgetRect == null
                                && !frozenCoordinateSelected
                                && !captureLayoutHiddenByUser) {
                            renderQuickCapturedLayout(refreshedSnapshot, captureGeneration,
                                    true, "refreshRender", requestStartedNanos);
                        }
                    } else if (refreshedSnapshot != null
                            && refreshedSnapshot.hasSelectableContent()
                            && !isQuickSnapshotTimely(
                            requestStartedNanos, captureCompletedNanos)) {
                        logSkippedQuickSnapshot("refresh" + attempt, packageAtRequest,
                                requestStartedNanos, captureCompletedNanos,
                                refreshedSnapshot);
                    }
                });
            }, delayMillis, TimeUnit.MILLISECONDS);
        } catch (RejectedExecutionException ignored) {
            addCaptureLog("refreshRejected", packageAtRequest, true,
                    elapsedMillis(requestStartedNanos), "attempt=" + attempt);
        }
    }

    private static boolean shouldRefreshQuickSnapshot(
            AccessibilityLayoutSnapshot snapshot, int attempt) {
        return snapshot == null
                || !snapshot.hasInteractiveContent()
                || snapshot.getVisibleNodeCount() == 0
                || (attempt == 1
                && snapshot.getNodes().size() <= QUICK_CAPTURE_SPARSE_NODE_THRESHOLD);
    }

    private void requestManualLayoutCapture(Widget widgetSelect) {
        ViewAddDataBinding requestedAddBinding = addDataBinding;
        ViewWidgetSelectBinding requestedWidgetBinding = widgetSelectBinding;
        if (requestedAddBinding == null || requestedWidgetBinding == null) {
            return;
        }
        requestedAddBinding.switchWid.setEnabled(false);
        requestedAddBinding.switchWid.setText("正在捕获...");
        long captureGeneration = layoutCaptureGeneration.incrementAndGet();
        String packageAtRequest = currentPackage;
        String activityAtRequest = currentActivity;
        addCaptureLog("request", packageAtRequest, false, 0L,
                "activity=" + valueOrUnknown(activityAtRequest));
        captureLayoutAsync(packageAtRequest, activityAtRequest, false,
                (snapshot, captureCompletedNanos) -> {
            if (closed.get()
                    || captureGeneration != layoutCaptureGeneration.get()
                    || requestedAddBinding != addDataBinding
                    || requestedWidgetBinding != widgetSelectBinding) {
                return;
            }
            requestedAddBinding.switchWid.setEnabled(true);
            if (snapshot == null || !snapshot.hasSelectableContent()) {
                requestedAddBinding.switchWid.setText("显示布局");
                Toast.makeText(service, "未捕获到当前页面控件，请重试", Toast.LENGTH_SHORT).show();
                return;
            }
            renderCapturedLayout(snapshot, widgetSelect);
        });
    }

    private void captureLayoutAsync(String packageAtRequest, String activityAtRequest,
                                    boolean quick, LayoutCaptureCallback callback) {
        captureLayoutAsync(packageAtRequest, activityAtRequest, quick,
                true, quick ? "quick" : "manual", callback);
    }

    private void captureLayoutAsync(String packageAtRequest, String activityAtRequest,
                                    boolean quick, boolean enrichSparse,
                                    String capturePhase, LayoutCaptureCallback callback) {
        try {
            executorServiceCapture.execute(() -> {
                AccessibilityLayoutSnapshot capturedSnapshot = null;
                CaptureTrace trace = new CaptureTrace();
                trace.capturePhase = capturePhase;
                trace.enrichmentEnabled = enrichSparse;
                try {
                    capturedSnapshot = captureCurrentLayout(
                            packageAtRequest, activityAtRequest, quick, enrichSparse, trace);
                } catch (RuntimeException exception) {
                    // A window can disappear between the double tap and the
                    // accessibility IPC. Report a failed capture on the UI thread.
                    trace.failure = exception.getClass().getSimpleName();
                }
                trace.elapsedMillis = elapsedMillis(trace.startedNanos);
                trace.capturedNodes = capturedSnapshot == null
                        ? 0 : capturedSnapshot.getNodes().size();
                trace.selectable = capturedSnapshot != null
                        && capturedSnapshot.hasSelectableContent();
                trace.recordSnapshot(capturedSnapshot);
                AccessibilityLayoutSnapshot snapshot = capturedSnapshot;
                long captureCompletedNanos = System.nanoTime();
                addCaptureLog("complete", packageAtRequest, quick,
                        trace.elapsedMillis, trace.toDetails());
                mainHandler.post(() -> callback.onCaptured(snapshot, captureCompletedNanos));
            });
        } catch (RejectedExecutionException ignored) {
            addCaptureLog("rejected", packageAtRequest, quick, 0L, "executor=shutdown");
            mainHandler.post(() -> callback.onCaptured(null, System.nanoTime()));
        }
    }

    private AccessibilityLayoutSnapshot captureCurrentLayout(String packageAtRequest,
                                                               String activityAtRequest,
                                                               boolean quick,
                                                               boolean enrichSparse,
                                                               CaptureTrace trace) {
        if (closed.get()) {
            trace.failure = "closed";
            return null;
        }
        trace.moduleEnabled = MyUtils.isModuleValid();
        AccessibilityNodeInfo activeRoot;
        long rootLookupStartedNanos = System.nanoTime();
        try {
            activeRoot = service.getRootInActiveWindow();
        } catch (RuntimeException exception) {
            trace.rootLookupMillis = elapsedMillis(rootLookupStartedNanos);
            trace.failure = "activeRoot:" + exception.getClass().getSimpleName();
            return null;
        }
        trace.rootLookupMillis = elapsedMillis(rootLookupStartedNanos);
        if (activeRoot == null || activeRoot.getPackageName() == null) {
            trace.failure = "activeRootUnavailable";
            return null;
        }
        String capturedPackage = activeRoot.getPackageName().toString();
        if (StrUtil.isNotBlank(packageAtRequest)
                && !TextUtils.equals(packageAtRequest, capturedPackage)) {
            trace.failure = "packageChanged:" + capturedPackage;
            return null;
        }

        String capturedActivity = StrUtil.isNotBlank(activityAtRequest)
                ? activityAtRequest : currentActivity;
        List<AccessibilityLayoutSnapshot.Node> capturedNodes = new ArrayList<>();
        if (quick) {
            trace.source = "activeRoot";
            long activeTraversalStartedNanos = System.nanoTime();
            appendCapturedNodes(activeRoot, capturedNodes,
                    System.nanoTime() + TimeUnit.MILLISECONDS.toNanos(QUICK_CAPTURE_BUDGET_MILLIS),
                    QUICK_CAPTURE_MAX_NODES, trace, false);
            capturedNodes = normalizeCapturedNodes(capturedNodes);
            trace.activeTraversalMillis = elapsedMillis(activeTraversalStartedNanos);

            if (enrichSparse
                    && requiresCaptureEnrichment(capturedNodes)
                    && trace.moduleEnabled) {
                List<AccessibilityLayoutSnapshot.Node> moduleNodes = new ArrayList<>();
                long moduleTraversalStartedNanos = System.nanoTime();
                appendCapturedNodes(activeRoot, moduleNodes,
                        System.nanoTime() + TimeUnit.MILLISECONDS.toNanos(
                                QUICK_CAPTURE_MODULE_RETRY_MILLIS),
                        QUICK_CAPTURE_FALLBACK_MAX_NODES, trace, true);
                moduleNodes = normalizeCapturedNodes(moduleNodes);
                trace.moduleTraversalMillis = elapsedMillis(moduleTraversalStartedNanos);
                if (capturedNodeQuality(moduleNodes) >= capturedNodeQuality(capturedNodes)) {
                    capturedNodes = moduleNodes;
                    trace.source = "activeRootModuleRetry";
                }
            }

            if (enrichSparse && requiresCaptureEnrichment(capturedNodes)) {
                List<AccessibilityLayoutSnapshot.Node> windowNodes = captureMatchingWindows(
                        capturedPackage,
                        QUICK_CAPTURE_WINDOW_FALLBACK_MILLIS,
                        QUICK_CAPTURE_FALLBACK_MAX_NODES,
                        trace,
                        trace.moduleEnabled);
                if (capturedNodeQuality(windowNodes) >= capturedNodeQuality(capturedNodes)) {
                    capturedNodes = windowNodes;
                    trace.source = "quickWindowsFallback";
                }
            }
        } else {
            capturedNodes = captureMatchingWindows(
                    capturedPackage,
                    MANUAL_CAPTURE_BUDGET_MILLIS,
                    MANUAL_CAPTURE_MAX_NODES,
                    trace,
                    trace.moduleEnabled);
            if (!capturedNodes.isEmpty()) {
                trace.source = "windows";
            }
        }
        if (capturedNodes.isEmpty()) {
            trace.source = "activeRootFallback";
            long activeTraversalStartedNanos = System.nanoTime();
            appendCapturedNodes(activeRoot, capturedNodes,
                    System.nanoTime() + TimeUnit.MILLISECONDS.toNanos(
                            quick ? QUICK_CAPTURE_WINDOW_FALLBACK_MILLIS
                                    : MANUAL_CAPTURE_BUDGET_MILLIS),
                    quick ? QUICK_CAPTURE_FALLBACK_MAX_NODES : MANUAL_CAPTURE_MAX_NODES,
                    trace,
                    trace.moduleEnabled);
            capturedNodes = normalizeCapturedNodes(capturedNodes);
            trace.activeTraversalMillis += elapsedMillis(activeTraversalStartedNanos);
        }
        if (capturedNodes.isEmpty()) {
            if (trace.failure == null) {
                trace.failure = "noNodes";
            }
            return null;
        }

        return new AccessibilityLayoutSnapshot(
                capturedPackage, capturedActivity, capturedNodes);
    }

    private List<AccessibilityLayoutSnapshot.Node> captureMatchingWindows(
            String capturedPackage, long budgetMillis, int maxNodes,
            CaptureTrace trace, boolean includeModuleQuery) {
        List<AccessibilityWindowInfo> windows = new ArrayList<>();
        long windowLookupStartedNanos = System.nanoTime();
        try {
            List<AccessibilityWindowInfo> serviceWindows = service.getWindows();
            if (serviceWindows != null) {
                windows.addAll(serviceWindows);
            }
        } catch (RuntimeException exception) {
            trace.failure = "windows:" + exception.getClass().getSimpleName();
        }
        trace.windowLookupMillis += elapsedMillis(windowLookupStartedNanos);
        trace.windowCount = Math.max(trace.windowCount, windows.size());

        List<AccessibilityLayoutSnapshot.Node> capturedNodes = new ArrayList<>();
        long deadlineNanos = System.nanoTime() + TimeUnit.MILLISECONDS.toNanos(budgetMillis);
        long windowTraversalStartedNanos = System.nanoTime();
        Collections.reverse(windows);
        for (AccessibilityWindowInfo window : windows) {
            if (System.nanoTime() >= deadlineNanos || capturedNodes.size() >= maxNodes) {
                break;
            }
            try {
                AccessibilityNodeInfo windowRoot = window.getRoot();
                if (windowRoot == null
                        || !TextUtils.equals(windowRoot.getPackageName(), capturedPackage)) {
                    continue;
                }
                trace.matchedWindowCount++;
                appendCapturedNodes(windowRoot, capturedNodes, deadlineNanos,
                        maxNodes, trace, includeModuleQuery);
            } catch (RuntimeException exception) {
                trace.failure = "windowRoot:" + exception.getClass().getSimpleName();
            }
        }
        trace.windowTraversalMillis += elapsedMillis(windowTraversalStartedNanos);
        return normalizeCapturedNodes(capturedNodes);
    }

    private static List<AccessibilityLayoutSnapshot.Node> normalizeCapturedNodes(
            List<AccessibilityLayoutSnapshot.Node> nodes) {
        return new ArrayList<>(new AccessibilityLayoutSnapshot("", "", nodes).getNodes());
    }

    private static long capturedNodeQuality(List<AccessibilityLayoutSnapshot.Node> nodes) {
        AccessibilityLayoutSnapshot snapshot = new AccessibilityLayoutSnapshot("", "", nodes);
        return snapshot.getInteractiveNodeCount() * 1_000_000L
                + snapshot.getIdentifiedNodeCount() * 10_000L
                + snapshot.getVisibleNodeCount() * 100L
                + snapshot.getNodes().size();
    }

    private static long capturedNodeQuality(AccessibilityLayoutSnapshot snapshot) {
        return snapshot == null ? 0L : capturedNodeQuality(snapshot.getNodes());
    }

    private static boolean requiresCaptureEnrichment(
            List<AccessibilityLayoutSnapshot.Node> nodes) {
        AccessibilityLayoutSnapshot snapshot = new AccessibilityLayoutSnapshot("", "", nodes);
        return snapshot.isEmpty()
                || !snapshot.hasInteractiveContent()
                || snapshot.getNodes().size() <= QUICK_CAPTURE_SPARSE_NODE_THRESHOLD;
    }

    private void appendCapturedNodes(AccessibilityNodeInfo root,
                                     List<AccessibilityLayoutSnapshot.Node> capturedNodes,
                                     long deadlineNanos,
                                     int maxNodes,
                                     CaptureTrace trace,
                                     boolean includeModuleQuery) {
        if (root == null || capturedNodes.size() >= maxNodes || System.nanoTime() >= deadlineNanos) {
            return;
        }
        ArrayDeque<AccessibilityNodeInfo> pendingNodes = new ArrayDeque<>();
        Set<AccessibilityNodeInfo> visitedNodes = Collections.newSetFromMap(new IdentityHashMap<>());
        pendingNodes.offer(root);
        if (includeModuleQuery) {
            try {
                for (AccessibilityNodeInfo node : root.findAccessibilityNodeInfosByText(null)) {
                    if (System.nanoTime() >= deadlineNanos || pendingNodes.size() >= maxNodes * 2) {
                        break;
                    }
                    if (node != null) {
                        pendingNodes.offer(node);
                        trace.moduleQueryNodes++;
                    }
                }
            } catch (RuntimeException ignored) {
                trace.moduleQueryFailures++;
                // Fall back to the regular child traversal below.
            }
        }

        int visitedCount = 0;
        int maxVisitedNodes = Math.max(maxNodes * 2, maxNodes);
        while (!pendingNodes.isEmpty()
                && capturedNodes.size() < maxNodes
                && visitedCount < maxVisitedNodes
                && System.nanoTime() < deadlineNanos) {
            AccessibilityNodeInfo node = pendingNodes.poll();
            if (node == null || !visitedNodes.add(node)) {
                continue;
            }
            visitedCount++;
            trace.visitedNodes++;
            AccessibilityLayoutSnapshot.Node capturedNode = null;
            try {
                Rect bounds = new Rect();
                node.getBoundsInScreen(bounds);
                if (bounds.width() > 0 && bounds.height() > 0) {
                    capturedNode = new AccessibilityLayoutSnapshot.Node(
                            node.isClickable(),
                            node.getSourceNodeId(),
                            StrUtil.toStringOrEmpty(node.getViewIdResourceName()),
                            StrUtil.toStringOrEmpty(node.getContentDescription()),
                            StrUtil.toStringOrEmpty(node.getText()),
                            bounds.left,
                            bounds.top,
                            bounds.right,
                            bounds.bottom,
                            node.isVisibleToUser(),
                            node.isEnabled(),
                            node.isFocusable(),
                            node.getActions(),
                            node.getChildCount(),
                            node.getWindowId(),
                            StrUtil.toStringOrEmpty(node.getClassName()),
                            StrUtil.toStringOrEmpty(node.getPackageName()));
                    capturedNodes.add(capturedNode);
                }
            } catch (RuntimeException ignored) {
                trace.nodeReadFailures++;
                // A node can become stale while its page is being dismissed.
                // Keep the properties already copied from the remaining nodes.
            }
            try {
                int childCount = capturedNode == null
                        ? node.getChildCount() : capturedNode.getChildCount();
                if (trace.rootChildCount < 0) {
                    trace.rootChildCount = childCount;
                }
                for (int childIndex = 0; childIndex < childCount; childIndex++) {
                    if (System.nanoTime() >= deadlineNanos || pendingNodes.size() >= maxNodes * 2) {
                        break;
                    }
                    AccessibilityNodeInfo child = node.getChild(childIndex);
                    if (child != null) {
                        pendingNodes.offer(child);
                    }
                }
            } catch (RuntimeException ignored) {
                trace.childReadFailures++;
                // The copied properties remain valid even if child lookup fails.
            }
        }
    }

    private void renderQuickCapturedLayout(AccessibilityLayoutSnapshot snapshot,
                                           long captureGeneration,
                                           boolean allowVisibleRefresh,
                                           String phase,
                                           long requestStartedNanos) {
        if (!snapshot.hasSelectableContent()
                || closed.get()
                || captureGeneration != layoutCaptureGeneration.get()
                || addDataBinding == null
                || widgetSelectBinding == null
                || activeWidgetSelection == null
                || captureLayoutHiddenByUser
                || frozenCoordinateSelected) {
            return;
        }
        if (bParams == null
                || (!allowVisibleRefresh
                && bParams.alpha != 0f
                && activeFrozenCapture == null)
                || activeWidgetSelection.widgetRect != null) {
            return;
        }
        RenderResult result = renderCapturedLayout(snapshot, activeWidgetSelection);
        addCaptureLog(phase, snapshot.getAppPackage(), true, 0L,
                "nodes=" + snapshot.getNodes().size()
                        + " rendered=" + result.renderedNodeCount
                        + " interactive=" + result.interactiveNodeCount
                        + " widgetTargets=" + result.widgetTargetCount
                        + " clipped=" + result.clippedNodeCount
                        + " outside=" + result.outsideNodeCount
                        + " frozen=" + result.frozenBackground
                        + " requestAgeMs=" + elapsedMillis(requestStartedNanos)
                        + " overlay=" + bParams.width + "x" + bParams.height);
    }

    private void showQuickCapturePanel(AccessibilityLayoutSnapshot snapshot,
                                       String packageName, String source,
                                       long requestStartedNanos) {
        int nodeCount = snapshot == null ? 0 : snapshot.getNodes().size();
        addCaptureLog("panelStart", packageName, true,
                elapsedMillis(requestStartedNanos),
                "source=" + source + " nodes=" + nodeCount);
        showAddDataWindow(snapshot);
        boolean created = addDataBinding != null
                && widgetSelectBinding != null
                && viewClickPosition != null;
        int renderedNodeCount = widgetSelectBinding == null
                ? 0 : widgetSelectBinding.frame.getChildCount();
        int interactiveNodeCount = snapshot == null
                ? 0 : snapshot.getInteractiveNodeCount();
        addCaptureLog("panelReady", packageName, true,
                elapsedMillis(requestStartedNanos),
                "source=" + source + " nodes=" + nodeCount
                        + " rendered=" + renderedNodeCount
                        + " interactive=" + interactiveNodeCount
                        + " frozen=" + (activeFrozenCapture != null)
                        + " created=" + created);
    }

    private RenderResult renderCapturedLayout(AccessibilityLayoutSnapshot snapshot,
                                              Widget widgetSelect) {
        ViewAddDataBinding currentAddBinding = addDataBinding;
        ViewWidgetSelectBinding currentWidgetBinding = widgetSelectBinding;
        if (currentAddBinding == null || currentWidgetBinding == null || bParams == null) {
            return RenderResult.empty();
        }

        widgetSelect.appPackage = snapshot.getAppPackage();
        widgetSelect.appActivity = snapshot.getAppActivity();
        currentAddBinding.pkgName.setText(widgetSelect.appPackage);
        currentAddBinding.actName.setText(widgetSelect.appActivity);
        currentAddBinding.saveWid.setEnabled(false);
        if (frozenCaptureView != null) {
            frozenCaptureView.setImageDrawable(null);
        }
        currentWidgetBinding.frame.removeAllViews();
        frozenCaptureView = null;
        frozenCoordinateMarker = null;

        int overlayWidth = bParams.width;
        int overlayHeight = bParams.height;
        boolean frozenBackground = showFrozenCaptureLayout();

        View.OnClickListener onClickListener = view -> {
            AccessibilityLayoutSnapshot.Node node =
                    (AccessibilityLayoutSnapshot.Node) view.getTag(R.string.nodeInfo);
            applyCapturedNodeSelection(node, view, widgetSelect,
                    currentAddBinding, currentWidgetBinding);
            view.requestFocus();
        };
        View.OnFocusChangeListener onFocusChangeListener = new View.OnFocusChangeListener() {
            @Override
            public void onFocusChange(View view, boolean hasFocus) {
                if (currentAddBinding != addDataBinding
                        || currentWidgetBinding != widgetSelectBinding) {
                    return;
                }
                if (!hasFocus) {
                    view.setBackgroundResource(R.drawable.node);
                    return;
                }
                AccessibilityLayoutSnapshot.Node node =
                        (AccessibilityLayoutSnapshot.Node) view.getTag(R.string.nodeInfo);
                applyCapturedNodeSelection(node, view, widgetSelect,
                        currentAddBinding, currentWidgetBinding);
            }
        };

        int renderedNodeCount = 0;
        int interactiveNodeCount = 0;
        int widgetTargetCount = 0;
        int clippedNodeCount = 0;
        int outsideNodeCount = 0;
        for (AccessibilityLayoutSnapshot.Node node : snapshot.getNodes()) {
            if (node.getWidth() <= 0 || node.getHeight() <= 0) {
                continue;
            }
            Rect nodeBounds = new Rect(
                    node.getLeft(), node.getTop(),
                    node.getLeft() + node.getWidth(),
                    node.getTop() + node.getHeight());
            Rect visibleBounds = new Rect(nodeBounds);
            if (overlayWidth > 0 && overlayHeight > 0) {
                if (!visibleBounds.intersect(0, 0, overlayWidth, overlayHeight)) {
                    outsideNodeCount++;
                    continue;
                }
                if (!visibleBounds.equals(nodeBounds)) {
                    clippedNodeCount++;
                }
            }
            FrameLayout.LayoutParams params = new FrameLayout.LayoutParams(
                    visibleBounds.width(), visibleBounds.height());
            params.leftMargin = visibleBounds.left;
            params.topMargin = visibleBounds.top;
            View view = new View(service);
            view.setBackgroundResource(R.drawable.node);
            boolean widgetTarget = node.isUsefulWidgetTarget(overlayWidth, overlayHeight);
            if (widgetTarget) {
                view.setFocusableInTouchMode(true);
                view.setFocusable(true);
                view.setOnClickListener(onClickListener);
                view.setOnFocusChangeListener(onFocusChangeListener);
                widgetTargetCount++;
            } else {
                view.setClickable(false);
                view.setFocusable(false);
            }
            view.setTag(R.string.nodeInfo, node);
            currentWidgetBinding.frame.addView(view, params);
            renderedNodeCount++;
            if (node.isInteractive()) {
                interactiveNodeCount++;
            }
        }
        currentAddBinding.switchWid.setEnabled(true);
        if (renderedNodeCount == 0) {
            if (frozenBackground) {
                currentAddBinding.switchWid.setText("隐藏布局");
                currentAddBinding.widget.setText(
                        "当前页面没有可选控件，请点击冻结画面选择坐标");
            } else {
                currentAddBinding.switchWid.setText("显示布局");
                Toast.makeText(service, "当前页面没有可选择的控件", Toast.LENGTH_SHORT).show();
            }
            return new RenderResult(0, interactiveNodeCount, widgetTargetCount,
                    clippedNodeCount, outsideNodeCount, frozenBackground);
        }

        bParams.alpha = frozenBackground ? 1f : 0.5f;
        bParams.flags = WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN
                | WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS
                | WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE;
        windowManager.updateViewLayout(currentWidgetBinding.getRoot(), bParams);
        currentAddBinding.switchWid.setText("隐藏布局");
        captureLayoutHiddenByUser = false;
        return new RenderResult(renderedNodeCount, interactiveNodeCount,
                widgetTargetCount, clippedNodeCount, outsideNodeCount,
                frozenBackground);
    }

    private void applyCapturedNodeSelection(AccessibilityLayoutSnapshot.Node node,
                                             View view,
                                             Widget widgetSelect,
                                             ViewAddDataBinding currentAddBinding,
                                             ViewWidgetSelectBinding currentWidgetBinding) {
        if (node == null
                || currentAddBinding != addDataBinding
                || currentWidgetBinding != widgetSelectBinding) {
            return;
        }
        widgetSelect.widgetClickable = node.isClickable() || node.hasClickAction();
        widgetSelect.widgetRect = new Rect(
                node.getLeft(),
                node.getTop(),
                node.getLeft() + node.getWidth(),
                node.getTop() + node.getHeight());
        widgetSelect.widgetNodeId = node.getNodeId();
        widgetSelect.widgetViewId = node.getViewId();
        widgetSelect.widgetDescribe = node.getDescription();
        widgetSelect.widgetText = node.getText();
        frozenCoordinateSelected = false;
        currentAddBinding.saveAim.setEnabled(false);
        currentAddBinding.xy.setText("");
        if (frozenCoordinateMarker != null
                && frozenCoordinateMarker.getParent() == currentWidgetBinding.frame) {
            currentWidgetBinding.frame.removeView(frozenCoordinateMarker);
            frozenCoordinateMarker = null;
        }
        currentAddBinding.pkgName.setText(widgetSelect.appPackage);
        currentAddBinding.actName.setText(widgetSelect.appActivity);
        currentAddBinding.saveWid.setEnabled(
                PACKAGE_NAME_PATTERN.matcher(widgetSelect.appPackage).matches());
        String clickable = "clickable:" + widgetSelect.widgetClickable;
        String nodeId = "nodeId:" + widgetSelect.widgetNodeId;
        String viewId = widgetSelect.widgetViewId.isEmpty()
                ? "" : widgetSelect.widgetViewId.contains(":id/")
                ? "viewId:" + widgetSelect.widgetViewId.substring(
                widgetSelect.widgetViewId.indexOf(":id/") + 4) : "";
        String desc = widgetSelect.widgetDescribe.isEmpty()
                ? "" : "describe:" + widgetSelect.widgetDescribe;
        String text = widgetSelect.widgetText.isEmpty()
                ? "" : "text:" + widgetSelect.widgetText;
        currentAddBinding.widget.setText(clickable + " " + nodeId
                + (viewId.isEmpty() ? "" : " " + viewId)
                + (desc.isEmpty() ? "" : " " + desc)
                + (text.isEmpty() ? "" : " " + text));
        view.setBackgroundResource(R.drawable.node_focus);
    }

    private static final class RenderResult {
        private final int renderedNodeCount;
        private final int interactiveNodeCount;
        private final int widgetTargetCount;
        private final int clippedNodeCount;
        private final int outsideNodeCount;
        private final boolean frozenBackground;

        private RenderResult(int renderedNodeCount, int interactiveNodeCount,
                             int widgetTargetCount, int clippedNodeCount,
                             int outsideNodeCount, boolean frozenBackground) {
            this.renderedNodeCount = renderedNodeCount;
            this.interactiveNodeCount = interactiveNodeCount;
            this.widgetTargetCount = widgetTargetCount;
            this.clippedNodeCount = clippedNodeCount;
            this.outsideNodeCount = outsideNodeCount;
            this.frozenBackground = frozenBackground;
        }

        private static RenderResult empty() {
            return new RenderResult(0, 0, 0, 0, 0, false);
        }
    }

    private void hideCapturedLayout() {
        if (addDataBinding == null || widgetSelectBinding == null || bParams == null) {
            return;
        }
        bParams.alpha = 0f;
        bParams.flags = WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN
                | WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS
                | WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE
                | WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE;
        windowManager.updateViewLayout(widgetSelectBinding.getRoot(), bParams);
        addDataBinding.saveWid.setEnabled(false);
        // Preserve the frozen frame, captured node outlines and coordinate marker
        // while hidden. A transient ad may already be gone when the user shows
        // the layout again, so rebuilding from the live page would lose the
        // exact state that the rule picker was opened for.
        addDataBinding.switchWid.setEnabled(true);
        addDataBinding.switchWid.setText("显示布局");
        captureLayoutHiddenByUser = true;
    }

    private static long elapsedMillis(long startedNanos) {
        return TimeUnit.NANOSECONDS.toMillis(Math.max(0L, System.nanoTime() - startedNanos));
    }

    private static long elapsedMillis(long startedNanos, long completedNanos) {
        return TimeUnit.NANOSECONDS.toMillis(
                Math.max(0L, completedNanos - startedNanos));
    }

    private void addCaptureLog(String phase, String packageName, boolean quick,
                               long elapsedMillis, String details) {
        if (!runtimeLoggingEnabled) {
            return;
        }
        addLog(RuntimeLogFormatter.formatCaptureSummary(
                phase, packageName, quick, elapsedMillis, details));
    }

    private static String valueOrUnknown(String value) {
        return value == null || value.isEmpty() ? "未知" : value;
    }

    private void rememberCaptureActivity(CharSequence activityNameValue) {
        String activityName = activityNameValue == null ? "" : activityNameValue.toString();
        if (activityName.isEmpty()
                || activityName.startsWith("android.view.")
                || activityName.startsWith("android.widget.")) {
            return;
        }
        currentActivity = activityName;
    }

    private static final class CaptureTrace {
        private final long startedNanos = System.nanoTime();
        private String capturePhase = "unknown";
        private boolean enrichmentEnabled;
        private boolean moduleEnabled;
        private int windowCount;
        private int matchedWindowCount;
        private int visitedNodes;
        private int capturedNodes;
        private boolean selectable;
        private int interactiveNodes;
        private int identifiedNodes;
        private int visibleNodes;
        private int rootChildCount = -1;
        private int moduleQueryNodes;
        private int moduleQueryFailures;
        private int nodeReadFailures;
        private int childReadFailures;
        private String source = "none";
        private String outcome = "none";
        private String failure;
        private long elapsedMillis;
        private long rootLookupMillis;
        private long activeTraversalMillis;
        private long moduleTraversalMillis;
        private long windowLookupMillis;
        private long windowTraversalMillis;
        private final List<String> nodeSamples = new ArrayList<>();

        private void recordSnapshot(AccessibilityLayoutSnapshot snapshot) {
            if (snapshot == null) {
                return;
            }
            interactiveNodes = snapshot.getInteractiveNodeCount();
            identifiedNodes = snapshot.getIdentifiedNodeCount();
            visibleNodes = snapshot.getVisibleNodeCount();
            outcome = snapshot.hasInteractiveContent()
                    ? "interactive"
                    : snapshot.hasSelectableContent() ? "coordinateOnly" : "placeholder";
            int sampleCount = Math.min(CAPTURE_NODE_DIAGNOSTIC_LIMIT,
                    snapshot.getNodes().size());
            for (int index = 0; index < sampleCount; index++) {
                nodeSamples.add(snapshot.getNodes().get(index).toDebugSummary(index));
            }
        }

        private String toDetails() {
            StringBuilder details = new StringBuilder("capture=").append(capturePhase)
                    .append(" enrich=").append(enrichmentEnabled)
                    .append(" source=").append(source)
                    .append(" module=").append(moduleEnabled)
                    .append(" rootMs=").append(rootLookupMillis)
                    .append(" activeMs=").append(activeTraversalMillis)
                    .append(" moduleMs=").append(moduleTraversalMillis)
                    .append(" windowLookupMs=").append(windowLookupMillis)
                    .append(" windowMs=").append(windowTraversalMillis)
                    .append(" windows=").append(windowCount)
                    .append(" matchedWindows=").append(matchedWindowCount)
                    .append(" rootChildren=").append(rootChildCount)
                    .append(" visited=").append(visitedNodes)
                    .append(" nodes=").append(capturedNodes)
                    .append(" selectable=").append(selectable)
                    .append(" outcome=").append(outcome)
                    .append(" interactive=").append(interactiveNodes)
                    .append(" identified=").append(identifiedNodes)
                    .append(" visible=").append(visibleNodes)
                    .append(" moduleNodes=").append(moduleQueryNodes)
                    .append(" moduleErrors=").append(moduleQueryFailures)
                    .append(" nodeErrors=").append(nodeReadFailures)
                    .append(" childErrors=").append(childReadFailures);
            if (!nodeSamples.isEmpty()) {
                details.append(" samples=");
                for (int index = 0; index < nodeSamples.size(); index++) {
                    if (index > 0) {
                        details.append(";");
                    }
                    details.append("[").append(nodeSamples.get(index)).append("]");
                }
            }
            if (failure != null) {
                details.append(" failure=").append(failure);
            }
            return details.toString();
        }
    }

    private static final class VisualRegionSample {
        private final int[] pixels;
        private final int width;
        private final int height;

        private VisualRegionSample(int[] pixels, int width, int height) {
            this.pixels = pixels;
            this.width = width;
            this.height = height;
        }
    }

    private static final class FrozenScreenCapture {
        private final Bitmap bitmap;
        private final String appPackage;
        private final String appActivity;
        private final long generation;
        private final long requestStartedNanos;
        private final long capturedAfterMillis;

        private FrozenScreenCapture(Bitmap bitmap, String appPackage,
                                    String appActivity, long generation,
                                    long requestStartedNanos,
                                    long capturedAfterMillis) {
            this.bitmap = bitmap;
            this.appPackage = appPackage == null ? "" : appPackage;
            this.appActivity = appActivity == null ? "" : appActivity;
            this.generation = generation;
            this.requestStartedNanos = requestStartedNanos;
            this.capturedAfterMillis = capturedAfterMillis;
        }

        private void recycle() {
            if (!bitmap.isRecycled()) {
                bitmap.recycle();
            }
        }
    }

    private interface LayoutCaptureCallback {
        void onCaptured(AccessibilityLayoutSnapshot snapshot, long captureCompletedNanos);
    }

    private void showWarningDialog(Runnable onSureRun, String message) {
        ViewDialogWarningBinding binding = ViewDialogWarningBinding.inflate(LayoutInflater.from(service));
        binding.message.setText(message);
        AlertDialog.Builder alertDialogBuilder = new AlertDialog.Builder(service);
        alertDialogBuilder.setView(binding.getRoot());
        alertDialogBuilder.setNegativeButton("取消", null);
        alertDialogBuilder.setPositiveButton("确定", new DialogInterface.OnClickListener() {
            @Override
            public void onClick(DialogInterface dialog, int which) {
                onSureRun.run();
            }
        });
        alertDialogBuilder.setOnDismissListener(new DialogInterface.OnDismissListener() {
            @Override
            public void onDismiss(DialogInterface dialog) {
                currentPackage = prePackage;
                currentActivity = preActivity;
            }
        });
        AlertDialog dialog = alertDialogBuilder.create();
        dialog.getWindow().setType(WindowManager.LayoutParams.TYPE_ACCESSIBILITY_OVERLAY);
        dialog.show();
    }

    public void keepAliveByNotification(boolean enable) {
        if (closed.get()) {
            return;
        }
        if (enable) {
            NotificationManager notificationManager = service.getSystemService(NotificationManager.class);
            Intent intent = new Intent(service, MainActivity.class);
            Notification.Builder builder = new Notification.Builder(service);
            builder.setOngoing(true);
            builder.setAutoCancel(false);
            builder.setSmallIcon(R.drawable.app);
            builder.setContentTitle(service.getText(R.string.appName));
            builder.setContentText("运行中");
            builder.setContentIntent(PendingIntent.getActivity(service, 0x01, intent, PendingIntent.FLAG_IMMUTABLE | PendingIntent.FLAG_UPDATE_CURRENT));
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                String channelId = service.getPackageName() + ".foreground";
                builder.setChannelId(channelId);
                NotificationChannel channel = new NotificationChannel(
                        channelId, service.getString(R.string.appName) + "运行状态", NotificationManager.IMPORTANCE_LOW);
                notificationManager.createNotificationChannel(channel);
            }
            service.startForeground(0x01, builder.build());
        } else {
            service.stopForeground(true);
        }
    }

    public void keepAliveByFloatingWindow(boolean enable) {
        if (closed.get()) {
            return;
        }
        if (enable && ignoreView == null) {
            WindowManager.LayoutParams lp = new WindowManager.LayoutParams();
            lp.flags = WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN
                    | WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS
                    | WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE
                    | WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE;
            lp.type = WindowManager.LayoutParams.TYPE_ACCESSIBILITY_OVERLAY;
            lp.gravity = Gravity.START | Gravity.TOP;
            lp.format = PixelFormat.TRANSPARENT;
            lp.alpha = 0;
            lp.width = 0;
            lp.height = 0;
            lp.x = 0;
            lp.y = 0;
            ignoreView = new View(service);
            ignoreView.setBackgroundColor(Color.TRANSPARENT);
            windowManager.addView(ignoreView, lp);
        } else if (ignoreView != null) {
            windowManager.removeView(ignoreView);
            ignoreView = null;
        }
    }

    public void showDbClickFloating(boolean enable) {
        if (closed.get()) {
            return;
        }
        if (enable && dbClickView == null) {
            dbClickLp = new WindowManager.LayoutParams();
            dbClickLp.flags = WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN
                    | WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS
                    | WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL
                    | WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE;
            dbClickLp.type = WindowManager.LayoutParams.TYPE_ACCESSIBILITY_OVERLAY;
            dbClickLp.gravity = Gravity.START | Gravity.TOP;
            dbClickLp.format = PixelFormat.TRANSPARENT;
            dbClickLp.alpha = 0.5f;
            Rect rect = MyUtils.getDbClickPosition();
            dbClickLp.x = rect.left;
            dbClickLp.y = rect.top;
            dbClickLp.width = rect.width();
            dbClickLp.height = rect.height();

            dbClickView = new View(service);
            dbClickView.setBackgroundColor(Color.TRANSPARENT);
            dbClickView.setOnClickListener(new View.OnClickListener() {
                private long previousTime = 0;

                @Override
                public void onClick(View v) {
                    long currentTime = System.currentTimeMillis();
                    long interval = currentTime - previousTime;
                    previousTime = currentTime;
                    if (interval <= 1000) {
                        addCaptureLog("doubleTap", currentPackage, true, interval,
                                "received=true");
                        showAddDataWindow(true);
                    }
                }
            });
            windowManager.addView(dbClickView, dbClickLp);
        } else if (dbClickView != null) {
            windowManager.removeView(dbClickView);
            dbClickView = null;
        }
    }

    public void showDbClickSetting() {
        if (closed.get()) {
            return;
        }
        if (dbClickView == null || dbClickLp == null) {
            return;
        }
        ViewDbClickSettingBinding dbClickSettingBinding = ViewDbClickSettingBinding.inflate(LayoutInflater.from(service));
        AlertDialog alertDialog = new AlertDialog.Builder(service).setView(dbClickSettingBinding.getRoot()).create();
        alertDialog.getWindow().setType(WindowManager.LayoutParams.TYPE_ACCESSIBILITY_OVERLAY);
        alertDialog.getWindow().clearFlags(WindowManager.LayoutParams.FLAG_DIM_BEHIND);
        alertDialog.setOnDismissListener(new DialogInterface.OnDismissListener() {
            @Override
            public void onDismiss(DialogInterface dialog) {
                dbClickView.setBackgroundColor(Color.TRANSPARENT);
                windowManager.updateViewLayout(dbClickView, dbClickLp);
            }
        });
        alertDialog.show();
        DisplayMetrics displayMetrics = new DisplayMetrics();
        windowManager.getDefaultDisplay().getRealMetrics(displayMetrics);
        WindowManager.LayoutParams lp = alertDialog.getWindow().getAttributes();
        lp.width = Math.min(displayMetrics.widthPixels, displayMetrics.heightPixels) / 5 * 4;
        lp.height = Math.max(displayMetrics.widthPixels, displayMetrics.heightPixels) / 3;
        alertDialog.onWindowAttributesChanged(lp);

        dbClickSettingBinding.seekBarW.setMax(displayMetrics.widthPixels / 2);
        dbClickSettingBinding.seekBarH.setMax(displayMetrics.heightPixels / 4);
        dbClickSettingBinding.seekBarX.setMax(displayMetrics.widthPixels);
        dbClickSettingBinding.seekBarY.setMax(displayMetrics.heightPixels);
        dbClickSettingBinding.seekBarW.setProgress(dbClickLp.width);
        dbClickSettingBinding.seekBarH.setProgress(dbClickLp.height);
        dbClickSettingBinding.seekBarX.setProgress(dbClickLp.x);
        dbClickSettingBinding.seekBarY.setProgress(dbClickLp.y);
        SeekBar.OnSeekBarChangeListener onSeekBarChangeListener = new SeekBar.OnSeekBarChangeListener() {
            @Override
            public void onProgressChanged(SeekBar seekBar, int i, boolean b) {
                if (seekBar == dbClickSettingBinding.seekBarW) {
                    dbClickLp.width = i;
                }
                if (seekBar == dbClickSettingBinding.seekBarH) {
                    dbClickLp.height = i;
                }
                if (seekBar == dbClickSettingBinding.seekBarX) {
                    dbClickLp.x = i;
                }
                if (seekBar == dbClickSettingBinding.seekBarY) {
                    dbClickLp.y = i;
                }
                windowManager.updateViewLayout(dbClickView, dbClickLp);
            }

            @Override
            public void onStartTrackingTouch(SeekBar seekBar) {
            }

            @Override
            public void onStopTrackingTouch(SeekBar seekBar) {
                Rect rect = new Rect();
                rect.left = dbClickLp.x;
                rect.top = dbClickLp.y;
                rect.right = dbClickLp.x + dbClickLp.width;
                rect.bottom = dbClickLp.y + dbClickLp.height;
                MyUtils.setDbClickPosition(rect);
            }
        };
        dbClickSettingBinding.seekBarW.setOnSeekBarChangeListener(onSeekBarChangeListener);
        dbClickSettingBinding.seekBarH.setOnSeekBarChangeListener(onSeekBarChangeListener);
        dbClickSettingBinding.seekBarX.setOnSeekBarChangeListener(onSeekBarChangeListener);
        dbClickSettingBinding.seekBarY.setOnSeekBarChangeListener(onSeekBarChangeListener);
        dbClickView.setBackgroundColor(Color.RED);
        windowManager.updateViewLayout(dbClickView, dbClickLp);
    }

    private static boolean hasRunnableRules(AppDescribe describe) {
        if (describe == null) {
            return false;
        }
        boolean hasCoordinates = describe.coordinateOnOff
                && describe.coordinateSetMap != null
                && !describe.coordinateSetMap.isEmpty();
        boolean hasWidgets = describe.widgetOnOff
                && describe.widgetSetMap != null
                && !describe.widgetSetMap.isEmpty();
        return hasCoordinates || hasWidgets;
    }

    private boolean hasPendingWidgetRules(List<Widget> widgets) {
        for (Widget widget : widgets) {
            if (widget.action == Widget.ACTION_CLICK && !widget.noRepeat) {
                return true;
            }
            if (!alreadyClickSet.contains(widget)) {
                return true;
            }
        }
        return false;
    }

    private void setContentChangeEventsEnabled(boolean enabled) {
        synchronized (serviceInfoLock) {
            if (serviceInfo == null) {
                return;
            }
            int eventTypes = enabled
                    ? serviceInfo.eventTypes | AccessibilityEvent.TYPE_WINDOW_CONTENT_CHANGED
                    : serviceInfo.eventTypes & ~AccessibilityEvent.TYPE_WINDOW_CONTENT_CHANGED;
            if (eventTypes == serviceInfo.eventTypes) {
                contentChangeEventsEnabled = enabled;
                return;
            }
            serviceInfo.eventTypes = eventTypes;
            service.setServiceInfo(serviceInfo);
            contentChangeEventsEnabled = enabled;
        }
    }

    private ScheduledFuture<?> schedulePageTask(ScheduledThreadPoolExecutor executor,
                                                Runnable runnable, long delayMillis) {
        AtomicReference<ScheduledFuture<?>> futureReference = new AtomicReference<>();
        try {
            synchronized (pageTaskLock) {
                if (closed.get()) {
                    return null;
                }
                ScheduledFuture<?> future = executor.schedule(new Runnable() {
                    @Override
                    public void run() {
                        try {
                            runnable.run();
                        } finally {
                            ScheduledFuture<?> completed = futureReference.get();
                            if (completed != null) {
                                synchronized (pageTaskLock) {
                                    pageTaskFutures.remove(completed);
                                }
                            }
                        }
                    }
                }, Math.max(0, delayMillis), TimeUnit.MILLISECONDS);
                futureReference.set(future);
                pageTaskFutures.add(future);
                if (future.isDone()) {
                    pageTaskFutures.remove(future);
                }
                return future;
            }
        } catch (RejectedExecutionException ignored) {
            return null;
        }
    }

    private void cancelPageFuture(ScheduledFuture<?> future) {
        if (future == null) {
            return;
        }
        synchronized (pageTaskLock) {
            pageTaskFutures.remove(future);
            future.cancel(false);
        }
    }

    private void cancelAllPageTasks() {
        synchronized (pageTaskLock) {
            for (ScheduledFuture<?> future : pageTaskFutures) {
                future.cancel(false);
            }
            pageTaskFutures.clear();
            pendingWidgetScan = null;
        }
    }

    private void deactivateCurrentPage(boolean clearPackage) {
        packageGeneration++;
        pageGeneration++;
        cancelAllPageTasks();
        cancelFuture(futureWidget);
        cancelFuture(futureCoordinate);
        futureWidget = null;
        futureCoordinate = null;
        setContentChangeEventsEnabled(false);
        alreadyClickSet.clear();
        debounceSet.clear();
        onOffWidget = false;
        onOffWidgetSub = false;
        onOffCoordinate = false;
        onOffCoordinateSub = false;
        coordinateSetMap = Collections.emptyMap();
        widgetSetMap = Collections.emptyMap();
        coordinateSet = null;
        widgetSet = null;
        currentPackageSub = StrUtil.EMPTY;
        currentActivity = StrUtil.EMPTY;
        needChangeActivity = true;
        if (clearPackage) {
            currentPackage = StrUtil.EMPTY;
            appDescribe = new AppDescribe();
        }
    }

    public synchronized void setRuntimeLoggingEnabled(boolean enabled) {
        runtimeLoggingEnabled = enabled;
        if (!enabled) {
            logList.clear();
        }
    }

    private void addStateLog(String action, String value) {
        if (!runtimeLoggingEnabled) {
            return;
        }
        addLog(action + value);
    }

    private void addRuleLog(String ruleType, Long ruleId, String appPackage, String activity,
                            String reason, String result, Object fullRule) {
        if (!runtimeLoggingEnabled) {
            return;
        }
        String summary = RuntimeLogFormatter.formatRuleSummary(
                ruleType, ruleId, appPackage, activity, reason, result);
        if (BuildConfig.DEBUG && debugGson != null) {
            summary = RuntimeLogFormatter.appendDebugDetails(
                    summary, true, debugGson.toJson(fullRule));
        }
        addLog(summary);
    }

    private synchronized void addLog(String log) {
        if (closed.get() || !runtimeLoggingEnabled) {
            return;
        }
        logList.add(simpleDateFormat.format(new Date()) + " " + log);
        if (logList.size() > 1000) {
            logList.poll();
        }
    }

    public synchronized String getLog() {
        if (!runtimeLoggingEnabled) {
            return "运行日志已关闭，可在应用设置中开启";
        }
        StringBuilder stringBuilder = new StringBuilder();
        for (String s : logList) {
            stringBuilder.append(s).append("\n");
        }
        return stringBuilder.toString();
    }

    public void close() {
        if (!closed.compareAndSet(false, true)) {
            return;
        }
        pageGeneration++;
        layoutCaptureGeneration.incrementAndGet();
        quickCaptureInProgress.set(false);
        cancelAllPageTasks();
        cancelFuture(futureWidget);
        cancelFuture(futureCoordinate);
        setContentChangeEventsEnabled(false);
        executorServiceMain.shutdownNow();
        executorServiceSub.shutdownNow();
        executorServiceCapture.shutdownNow();
        mainHandler.removeCallbacksAndMessages(null);
        if (receiverRegistered && myBroadcastReceiver != null) {
            try {
                service.unregisterReceiver(myBroadcastReceiver);
            } catch (IllegalArgumentException ignored) {
            }
            receiverRegistered = false;
        }
        service.stopForeground(true);
        removeViewSafely(widgetSelectBinding == null ? null : widgetSelectBinding.getRoot());
        removeViewSafely(addDataBinding == null ? null : addDataBinding.getRoot());
        removeViewSafely(viewClickPosition);
        removeViewSafely(ignoreView);
        removeViewSafely(dbClickView);
        clearFrozenScreenCapture();
        widgetSelectBinding = null;
        addDataBinding = null;
        viewClickPosition = null;
        activeWidgetSelection = null;
        activeCoordinateSelection = null;
        frozenCoordinateSelected = false;
        ignoreView = null;
        dbClickView = null;
        alreadyClickSet.clear();
        debounceSet.clear();
        synchronized (this) {
            logList.clear();
        }
        replaceAppDescribeMap(Collections.emptyMap());
    }

    private void removeViewSafely(View view) {
        if (view == null || !view.isAttachedToWindow()) {
            return;
        }
        try {
            windowManager.removeViewImmediate(view);
        } catch (IllegalArgumentException ignored) {
        }
    }

    private static void cancelFuture(ScheduledFuture<?> future) {
        if (future != null) {
            future.cancel(false);
        }
    }

    public class MyBroadcastReceiver extends BroadcastReceiver {
        @Override
        public void onReceive(Context context, Intent intent) {
            if (TextUtils.equals(intent.getAction(), Intent.ACTION_SCREEN_OFF)) {
                deactivateCurrentPage(true);
            }
        }
    }
}
