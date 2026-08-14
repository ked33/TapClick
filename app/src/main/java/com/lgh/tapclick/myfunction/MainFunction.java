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
import android.graphics.Color;
import android.graphics.Path;
import android.graphics.PixelFormat;
import android.graphics.Rect;
import android.os.Build;
import android.os.Handler;
import android.os.Looper;
import android.text.TextUtils;
import android.util.DisplayMetrics;
import android.util.TypedValue;
import android.view.Gravity;
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
    private static final long QUICK_CAPTURE_PANEL_TIMEOUT_MILLIS = 250L;
    private static final long QUICK_CAPTURE_BUDGET_MILLIS = 180L;
    private static final int QUICK_CAPTURE_MAX_NODES = 400;
    private static final long QUICK_CAPTURE_MODULE_RETRY_MILLIS = 700L;
    private static final long QUICK_CAPTURE_WINDOW_FALLBACK_MILLIS = 1200L;
    private static final int QUICK_CAPTURE_FALLBACK_MAX_NODES = 1200;
    private static final long MANUAL_CAPTURE_BUDGET_MILLIS = 1500L;
    private static final int MANUAL_CAPTURE_MAX_NODES = 5000;

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
    private volatile AccessibilityServiceInfo serviceInfo;
    private volatile boolean contentChangeEventsEnabled;
    private volatile boolean runtimeLoggingEnabled;
    private MyBroadcastReceiver myBroadcastReceiver;
    private WindowManager.LayoutParams aParams, bParams, cParams;
    private ViewAddDataBinding addDataBinding;
    private ViewWidgetSelectBinding widgetSelectBinding;
    private volatile Widget activeWidgetSelection;
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
        executorServiceCapture = new ScheduledThreadPoolExecutor(2,
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
                if (generation != pageGeneration
                        || !onOffCoordinateSub
                        || !currentActivity.equals(coordinate.appActivity)
                        || closed.get()) {
                    return;
                }
                boolean actionAccepted = click(coordinate.xPosition, coordinate.yPosition);
                if (clickIndex == 0) {
                    coordinate.triggerCount += 1;
                    coordinate.lastTriggerTime = System.currentTimeMillis();
                    MyApplication.executeDatabase(() -> dataDao.updateCoordinate(coordinate));
                    addRuleLog("coordinate", coordinate.id, coordinate.appPackage,
                            coordinate.appActivity, "页面匹配",
                            actionAccepted ? "手势已提交" : "手势提交失败", coordinate);
                }
                scheduleCoordinateClick(coordinate, generation, clickIndex + 1,
                        coordinate.clickInterval <= 0 ? 10 : coordinate.clickInterval);
            }
        }, Math.max(0, delayMillis));
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
        final Widget widgetSelect = new Widget();
        activeWidgetSelection = widgetSelect;
        final Coordinate coordinateSelect = new Coordinate();
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
                                addDataBinding.pkgName.setText(coordinateSelect.appPackage);
                                addDataBinding.actName.setText(coordinateSelect.appActivity);
                                addDataBinding.saveAim.setEnabled(pattern.matcher(coordinateSelect.appPackage).matches());
                                addDataBinding.xy.setText("X轴：" + String.format("%-4d", coordinateSelect.xPosition) + "    " + "Y轴：" + String.format("%-4d", coordinateSelect.yPosition));
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
                    requestManualLayoutCapture(widgetSelect);
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
            }
        });
        windowManager.addView(widgetSelectBinding.getRoot(), bParams);
        windowManager.addView(addDataBinding.getRoot(), aParams);
        windowManager.addView(viewClickPosition, cParams);

        if (initialSnapshot != null) {
            renderCapturedLayout(initialSnapshot, widgetSelect);
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
        AtomicBoolean panelShown = new AtomicBoolean(false);
        addCaptureLog("request", packageAtRequest, true, 0L,
                "activity=" + valueOrUnknown(activityAtRequest));
        Runnable fallback = () -> {
            if (closed.get() || captureGeneration != layoutCaptureGeneration.get()) {
                return;
            }
            if (panelShown.compareAndSet(false, true)) {
                quickCaptureInProgress.set(false);
                showQuickCapturePanel(null, packageAtRequest, "timeout", requestStartedNanos);
            }
        };
        mainHandler.postDelayed(fallback, QUICK_CAPTURE_PANEL_TIMEOUT_MILLIS);
        captureLayoutAsync(packageAtRequest, activityAtRequest, true, snapshot -> {
            quickCaptureInProgress.set(false);
            if (closed.get() || captureGeneration != layoutCaptureGeneration.get()) {
                mainHandler.removeCallbacks(fallback);
                return;
            }
            if (panelShown.compareAndSet(false, true)) {
                mainHandler.removeCallbacks(fallback);
                if (snapshot == null || !snapshot.hasSelectableContent()) {
                    showQuickCapturePanel(null, packageAtRequest, "empty", requestStartedNanos);
                } else {
                    showQuickCapturePanel(snapshot, packageAtRequest, "snapshot", requestStartedNanos);
                }
                return;
            }
            if (snapshot != null && snapshot.hasSelectableContent()) {
                renderQuickCapturedLayout(snapshot, captureGeneration);
            }
        });
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
        captureLayoutAsync(packageAtRequest, activityAtRequest, false, snapshot -> {
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
        try {
            executorServiceCapture.execute(() -> {
                AccessibilityLayoutSnapshot capturedSnapshot = null;
                CaptureTrace trace = new CaptureTrace();
                try {
                    capturedSnapshot = captureCurrentLayout(
                            packageAtRequest, activityAtRequest, quick, trace);
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
                addCaptureLog("complete", packageAtRequest, quick,
                        trace.elapsedMillis, trace.toDetails());
                AccessibilityLayoutSnapshot snapshot = capturedSnapshot;
                mainHandler.post(() -> callback.onCaptured(snapshot));
            });
        } catch (RejectedExecutionException ignored) {
            addCaptureLog("rejected", packageAtRequest, quick, 0L, "executor=shutdown");
            mainHandler.post(() -> callback.onCaptured(null));
        }
    }

    private AccessibilityLayoutSnapshot captureCurrentLayout(String packageAtRequest,
                                                               String activityAtRequest,
                                                               boolean quick,
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

            if (requiresCaptureEnrichment(capturedNodes) && trace.moduleEnabled) {
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

            if (requiresCaptureEnrichment(capturedNodes)) {
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

    private static int capturedNodeQuality(List<AccessibilityLayoutSnapshot.Node> nodes) {
        AccessibilityLayoutSnapshot snapshot = new AccessibilityLayoutSnapshot("", "", nodes);
        return (snapshot.hasSelectableContent() ? 100_000 : 0) + snapshot.getNodes().size();
    }

    private static boolean requiresCaptureEnrichment(
            List<AccessibilityLayoutSnapshot.Node> nodes) {
        AccessibilityLayoutSnapshot snapshot = new AccessibilityLayoutSnapshot("", "", nodes);
        return snapshot.getNodes().size() <= 1 && !snapshot.hasSelectableContent();
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
            try {
                Rect bounds = new Rect();
                node.getBoundsInScreen(bounds);
                if (bounds.width() > 0 && bounds.height() > 0) {
                    capturedNodes.add(new AccessibilityLayoutSnapshot.Node(
                            node.isClickable(),
                            node.getSourceNodeId(),
                            StrUtil.toStringOrEmpty(node.getViewIdResourceName()),
                            StrUtil.toStringOrEmpty(node.getContentDescription()),
                            StrUtil.toStringOrEmpty(node.getText()),
                            bounds.left,
                            bounds.top,
                            bounds.right,
                            bounds.bottom));
                }
            } catch (RuntimeException ignored) {
                trace.nodeReadFailures++;
                // A node can become stale while its page is being dismissed.
                // Keep the properties already copied from the remaining nodes.
            }
            try {
                int childCount = node.getChildCount();
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
                                           long captureGeneration) {
        if (!snapshot.hasSelectableContent()
                || closed.get()
                || captureGeneration != layoutCaptureGeneration.get()
                || addDataBinding == null
                || widgetSelectBinding == null
                || activeWidgetSelection == null) {
            return;
        }
        if (bParams == null || bParams.alpha != 0f
                || activeWidgetSelection.widgetRect != null) {
            return;
        }
        addCaptureLog("lateRender", snapshot.getAppPackage(), true, 0L,
                "nodes=" + snapshot.getNodes().size());
        renderCapturedLayout(snapshot, activeWidgetSelection);
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
        addCaptureLog("panelReady", packageName, true,
                elapsedMillis(requestStartedNanos),
                "source=" + source + " nodes=" + nodeCount + " created=" + created);
    }

    private void renderCapturedLayout(AccessibilityLayoutSnapshot snapshot, Widget widgetSelect) {
        ViewAddDataBinding currentAddBinding = addDataBinding;
        ViewWidgetSelectBinding currentWidgetBinding = widgetSelectBinding;
        if (currentAddBinding == null || currentWidgetBinding == null || bParams == null) {
            return;
        }

        widgetSelect.appPackage = snapshot.getAppPackage();
        widgetSelect.appActivity = snapshot.getAppActivity();
        currentAddBinding.pkgName.setText(widgetSelect.appPackage);
        currentAddBinding.actName.setText(widgetSelect.appActivity);
        currentAddBinding.saveWid.setEnabled(false);
        currentWidgetBinding.frame.removeAllViews();

        View.OnClickListener onClickListener = view -> view.requestFocus();
        View.OnFocusChangeListener onFocusChangeListener = new View.OnFocusChangeListener() {
            final Pattern packagePattern = Pattern.compile("[A-Za-z0-9_.]+");

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
                widgetSelect.widgetClickable = node.isClickable();
                widgetSelect.widgetRect = new Rect(
                        node.getLeft(),
                        node.getTop(),
                        node.getLeft() + node.getWidth(),
                        node.getTop() + node.getHeight());
                widgetSelect.widgetNodeId = node.getNodeId();
                widgetSelect.widgetViewId = node.getViewId();
                widgetSelect.widgetDescribe = node.getDescription();
                widgetSelect.widgetText = node.getText();
                currentAddBinding.pkgName.setText(widgetSelect.appPackage);
                currentAddBinding.actName.setText(widgetSelect.appActivity);
                currentAddBinding.saveWid.setEnabled(
                        packagePattern.matcher(widgetSelect.appPackage).matches());
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
        };

        int renderedNodeCount = 0;
        for (AccessibilityLayoutSnapshot.Node node : snapshot.getNodes()) {
            if (node.getWidth() <= 0 || node.getHeight() <= 0) {
                continue;
            }
            FrameLayout.LayoutParams params = new FrameLayout.LayoutParams(
                    node.getWidth(), node.getHeight());
            params.leftMargin = node.getLeft();
            params.topMargin = node.getTop();
            View view = new View(service);
            view.setBackgroundResource(R.drawable.node);
            view.setFocusableInTouchMode(true);
            view.setFocusable(true);
            view.setOnClickListener(onClickListener);
            view.setOnFocusChangeListener(onFocusChangeListener);
            view.setTag(R.string.nodeInfo, node);
            currentWidgetBinding.frame.addView(view, params);
            renderedNodeCount++;
        }
        currentAddBinding.switchWid.setEnabled(true);
        if (renderedNodeCount == 0) {
            currentAddBinding.switchWid.setText("显示布局");
            Toast.makeText(service, "当前页面没有可选择的控件", Toast.LENGTH_SHORT).show();
            return;
        }

        bParams.alpha = 0.5f;
        bParams.flags = WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN
                | WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS
                | WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE;
        windowManager.updateViewLayout(currentWidgetBinding.getRoot(), bParams);
        currentAddBinding.switchWid.setText("隐藏布局");
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
        widgetSelectBinding.frame.removeAllViews();
        addDataBinding.switchWid.setEnabled(true);
        addDataBinding.switchWid.setText("显示布局");
    }

    private static long elapsedMillis(long startedNanos) {
        return TimeUnit.NANOSECONDS.toMillis(Math.max(0L, System.nanoTime() - startedNanos));
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

    private static final class CaptureTrace {
        private final long startedNanos = System.nanoTime();
        private boolean moduleEnabled;
        private int windowCount;
        private int matchedWindowCount;
        private int visitedNodes;
        private int capturedNodes;
        private boolean selectable;
        private int rootChildCount = -1;
        private int moduleQueryNodes;
        private int moduleQueryFailures;
        private int nodeReadFailures;
        private int childReadFailures;
        private String source = "none";
        private String failure;
        private long elapsedMillis;
        private long rootLookupMillis;
        private long activeTraversalMillis;
        private long moduleTraversalMillis;
        private long windowLookupMillis;
        private long windowTraversalMillis;

        private String toDetails() {
            return "source=" + source
                    + " module=" + moduleEnabled
                    + " rootMs=" + rootLookupMillis
                    + " activeMs=" + activeTraversalMillis
                    + " moduleMs=" + moduleTraversalMillis
                    + " windowLookupMs=" + windowLookupMillis
                    + " windowMs=" + windowTraversalMillis
                    + " windows=" + windowCount
                    + " matchedWindows=" + matchedWindowCount
                    + " rootChildren=" + rootChildCount
                    + " visited=" + visitedNodes
                    + " nodes=" + capturedNodes
                    + " selectable=" + selectable
                    + " moduleNodes=" + moduleQueryNodes
                    + " moduleErrors=" + moduleQueryFailures
                    + " nodeErrors=" + nodeReadFailures
                    + " childErrors=" + childReadFailures
                    + (failure == null ? "" : " failure=" + failure);
        }
    }

    private interface LayoutCaptureCallback {
        void onCaptured(AccessibilityLayoutSnapshot snapshot);
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
        widgetSelectBinding = null;
        addDataBinding = null;
        viewClickPosition = null;
        activeWidgetSelection = null;
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
