package com.lgh.tapclick.myactivity;

import android.animation.LayoutTransition;
import android.annotation.SuppressLint;
import android.app.AlertDialog;
import android.content.ClipData;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;
import android.graphics.Color;
import android.graphics.Rect;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.text.Editable;
import android.text.TextUtils;
import android.text.TextWatcher;
import android.util.DisplayMetrics;
import android.view.LayoutInflater;
import android.view.MotionEvent;
import android.view.View;
import android.widget.Toast;

import androidx.activity.OnBackPressedCallback;
import androidx.appcompat.widget.SwitchCompat;
import androidx.core.content.FileProvider;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonSyntaxException;
import com.lgh.tapclick.BuildConfig;
import com.lgh.tapclick.databinding.ActivityEditDataBinding;
import com.lgh.tapclick.databinding.ViewBaseSettingBinding;
import com.lgh.tapclick.databinding.ViewCoordinateBinding;
import com.lgh.tapclick.databinding.ViewEditFileNameBinding;
import com.lgh.tapclick.databinding.ViewOnOffWarningBinding;
import com.lgh.tapclick.databinding.ViewWidgetBinding;
import com.lgh.tapclick.mybean.AppDescribe;
import com.lgh.tapclick.mybean.BasicContent;
import com.lgh.tapclick.mybean.Coordinate;
import com.lgh.tapclick.mybean.CoordinateShare;
import com.lgh.tapclick.mybean.MyAppConfig;
import com.lgh.tapclick.mybean.Widget;
import com.lgh.tapclick.mybean.WidgetShare;
import com.lgh.tapclick.myclass.DataDao;
import com.lgh.tapclick.myclass.ExportFileManager;
import com.lgh.tapclick.myclass.MyApplication;
import com.lgh.tapclick.myclass.PackageCatalog;
import com.lgh.tapclick.myclass.VisualCoordinateSignature;
import com.lgh.tapclick.myfunction.MyUtils;

import java.io.File;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.stream.Collectors;

import cn.hutool.core.util.StrUtil;
import cn.hutool.crypto.digest.DigestUtil;

public class EditDataActivity extends BaseActivity {
    private static final long SAVE_DEBOUNCE_MILLIS = 400;

    private AppDescribe appDescribe;
    private Context context;
    private LayoutInflater inflater;
    private DataDao dataDao;
    private MyAppConfig myAppConfig;
    private DisplayMetrics metrics;
    private SimpleDateFormat dateFormatModify;
    private SimpleDateFormat dateFormat;
    private ActivityEditDataBinding editDataBinding;
    private ViewBaseSettingBinding baseSettingBinding;
    private Set<String> pkgSuggestNotOnList;
    private String packageName;
    private Gson gson;
    private Handler saveHandler;
    private final Set<Coordinate> dirtyCoordinates = new HashSet<>();
    private final Set<Widget> dirtyWidgets = new HashSet<>();
    private boolean appDescribeDirty;
    private final Runnable persistChangesRunnable = new Runnable() {
        @Override
        public void run() {
            flushPendingChanges(false);
        }
    };

    @SuppressLint("ClickableViewAccessibility")
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        inflater = getLayoutInflater();
        editDataBinding = ActivityEditDataBinding.inflate(inflater);
        setContentView(editDataBinding.getRoot());
        getOnBackPressedDispatcher().addCallback(this, new OnBackPressedCallback(true) {
            @Override
            public void handleOnBackPressed() {
                Intent intent = getIntent();
                intent.putExtra("packageName", packageName);
                setResult(RESULT_OK, intent);
                finish();
            }
        });

        context = getApplicationContext();
        dataDao = MyApplication.dataDao;
        saveHandler = new Handler(Looper.getMainLooper());
        MyApplication.queryDatabase(dataDao::getMyAppConfig, result -> {
            myAppConfig = result;
            if (myAppConfig.autoHideOnTaskList) {
                MyUtils.setExcludeFromRecents(false);
            }
        });
        gson = new GsonBuilder().create();
        dateFormatModify = new SimpleDateFormat("HH:mm:ss", Locale.getDefault());
        dateFormat = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault());
        metrics = new DisplayMetrics();
        getWindowManager().getDefaultDisplay().getRealMetrics(metrics);

        pkgSuggestNotOnList = PackageCatalog.getSuggestedRestrictedPackages(context);

        LayoutTransition transition = new LayoutTransition();
        transition.enableTransitionType(LayoutTransition.CHANGING);
        editDataBinding.rootLayout.setLayoutTransition(transition);
        editDataBinding.baseSettingLayout.setLayoutTransition(transition);
        editDataBinding.coordinateLayout.setLayoutTransition(transition);
        editDataBinding.widgetLayout.setLayoutTransition(transition);

        editDataBinding.scrollView.setOnTouchListener(new View.OnTouchListener() {
            @Override
            public boolean onTouch(View v, MotionEvent event) {
                if (event.getAction() != MotionEvent.ACTION_MOVE) {
                    editDataBinding.getRoot().requestFocus();
                }
                return false;
            }
        });

    }

    @Override
    protected void onStart() {
        super.onStart();
        String extraPackage = getIntent().getStringExtra("packageName");
        if (!TextUtils.isEmpty(extraPackage)) {
            packageName = extraPackage;
        }
        if (!TextUtils.isEmpty(packageName)) {
            MyApplication.queryDatabase(() -> {
                AppDescribe result = dataDao.getAppDescribeByPackage(packageName);
                if (result != null) {
                    result.getOtherFieldsFromDatabase(dataDao);
                }
                return result;
            }, result -> {
                if (isFinishing() || isDestroyed()) {
                    return;
                }
                appDescribe = result;
                renderAppDescribe();
            });
        } else {
            finishAndRemoveTask();
        }
    }

    private void renderAppDescribe() {
        if (appDescribe == null) {
            finishAndRemoveTask();
            return;
        }

        if (baseSettingBinding != null) {
            editDataBinding.baseSettingLayout.removeView(baseSettingBinding.getRoot());
        }
        baseSettingBinding = ViewBaseSettingBinding.inflate(inflater);
        baseSettingBinding.appName.setText(StrUtil.blankToDefault(appDescribe.appName, "读取失败，无权限或未安装"));
        baseSettingBinding.appPackage.setText(appDescribe.appPackage);

        baseSettingBinding.coordinateSwitch.setChecked(appDescribe.coordinateOnOff);
        baseSettingBinding.coordinateSustainTime.setText(appDescribe.coordinateRetrieveAllTime ? "∞" : String.valueOf(appDescribe.coordinateRetrieveTime));
        baseSettingBinding.coordinateRetrieveAllTime.setChecked(appDescribe.coordinateRetrieveAllTime);

        baseSettingBinding.widgetSwitch.setChecked(appDescribe.widgetOnOff);
        baseSettingBinding.widgetSustainTime.setText(appDescribe.widgetRetrieveAllTime ? "∞" : String.valueOf(appDescribe.widgetRetrieveTime));
        baseSettingBinding.widgetRetrieveAllTime.setChecked(appDescribe.widgetRetrieveAllTime);

        Runnable baseSettingSaveRun = new Runnable() {
            @Override
            public void run() {
                String coordinateTime = StrUtil.trimToEmpty(baseSettingBinding.coordinateSustainTime.getText());
                String widgetTime = StrUtil.trimToEmpty(baseSettingBinding.widgetSustainTime.getText());
                editDataBinding.baseSettingModify.setTextColor(0xfff20000);
                if (coordinateTime.isEmpty()) {
                    editDataBinding.baseSettingModify.setText("坐标检索持续时间不能为空");
                    return;
                }
                if (widgetTime.isEmpty()) {
                    editDataBinding.baseSettingModify.setText("控件检索持续时间不能为空");
                    return;
                }
                appDescribe.coordinateOnOff = baseSettingBinding.coordinateSwitch.isChecked();
                appDescribe.coordinateRetrieveTime = coordinateTime.equals("∞") ? appDescribe.coordinateRetrieveTime : Integer.parseInt(coordinateTime);
                appDescribe.coordinateRetrieveAllTime = baseSettingBinding.coordinateRetrieveAllTime.isChecked();
                appDescribe.widgetOnOff = baseSettingBinding.widgetSwitch.isChecked();
                appDescribe.widgetRetrieveTime = widgetTime.equals("∞") ? appDescribe.widgetRetrieveTime : Integer.parseInt(widgetTime);
                appDescribe.widgetRetrieveAllTime = baseSettingBinding.widgetRetrieveAllTime.isChecked();
                queueAppDescribeSave();
                editDataBinding.baseSettingModify.setTextColor(0xff000000);
                editDataBinding.baseSettingModify.setText(dateFormatModify.format(new Date()) + " (已修改)");
            }
        };

        View.OnClickListener onOffClickListener = new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                SwitchCompat switchCompat = (SwitchCompat) v;
                if (switchCompat.isChecked() && pkgSuggestNotOnList.contains(appDescribe.appPackage)) {
                    switchCompat.setChecked(false);
                    View view = ViewOnOffWarningBinding.inflate(getLayoutInflater()).getRoot();
                    AlertDialog.Builder alertDialogBuilder = new AlertDialog.Builder(EditDataActivity.this);
                    alertDialogBuilder.setView(view);
                    alertDialogBuilder.setNegativeButton("取消", null);
                    alertDialogBuilder.setPositiveButton("确定", new DialogInterface.OnClickListener() {
                        @Override
                        public void onClick(DialogInterface dialog, int which) {
                            baseSettingSaveRun.run();
                        }
                    });
                    AlertDialog dialog = alertDialogBuilder.create();
                    dialog.show();
                } else {
                    baseSettingSaveRun.run();
                }
            }
        };
        baseSettingBinding.widgetSwitch.setOnClickListener(onOffClickListener);
        baseSettingBinding.coordinateSwitch.setOnClickListener(onOffClickListener);

        TextWatcher sustainTimeTextWatcher = new TextWatcher() {
            @Override
            public void beforeTextChanged(CharSequence s, int start, int count, int after) {

            }

            @Override
            public void onTextChanged(CharSequence s, int start, int before, int count) {

            }

            @Override
            public void afterTextChanged(Editable s) {
                baseSettingSaveRun.run();
            }
        };
        baseSettingBinding.widgetSustainTime.addTextChangedListener(sustainTimeTextWatcher);
        baseSettingBinding.coordinateSustainTime.addTextChangedListener(sustainTimeTextWatcher);

        View.OnClickListener allTimeClickListener = new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                baseSettingSaveRun.run();
                baseSettingBinding.coordinateSustainTime.setText(appDescribe.coordinateRetrieveAllTime ? "∞" : String.valueOf(appDescribe.coordinateRetrieveTime));
                baseSettingBinding.widgetSustainTime.setText(appDescribe.widgetRetrieveAllTime ? "∞" : String.valueOf(appDescribe.widgetRetrieveTime));
            }
        };
        baseSettingBinding.widgetRetrieveAllTime.setOnClickListener(allTimeClickListener);
        baseSettingBinding.coordinateRetrieveAllTime.setOnClickListener(allTimeClickListener);
        editDataBinding.baseSettingLayout.addView(baseSettingBinding.getRoot());

        List<Coordinate> latestTriggerCoordinateList = appDescribe.coordinateList.stream()
                .filter(e -> e.lastTriggerTime > System.currentTimeMillis() - 1000 * 60 * 5)
                .sorted((e1, e2) -> Long.compare(e2.lastTriggerTime, e1.lastTriggerTime))
                .collect(Collectors.toList());
        appDescribe.coordinateList.removeIf(e -> latestTriggerCoordinateList.stream().anyMatch(n -> n == e));
        appDescribe.coordinateList.sort((e1, e2) -> Long.compare(e2.createTime, e1.createTime));
        appDescribe.coordinateList.addAll(0, latestTriggerCoordinateList);
        if (appDescribe.coordinateList.isEmpty()) {
            editDataBinding.coordinateLayout.setVisibility(View.GONE);
        } else {
            editDataBinding.coordinateLayout.setVisibility(View.VISIBLE);
        }
        if (editDataBinding.coordinateLayout.getChildCount() > 2) {
            editDataBinding.coordinateLayout.removeViews(2, editDataBinding.coordinateLayout.getChildCount() - 2);
        }
        for (int n = 0; n < appDescribe.coordinateList.size(); n++) {
            Coordinate coordinate = appDescribe.coordinateList.get(n);
            ViewCoordinateBinding coordinateBinding = ViewCoordinateBinding.inflate(inflater);
            coordinateBinding.coordinateActivity.setText(coordinate.appActivity);
            coordinateBinding.coordinateXPosition.setText(String.valueOf(coordinate.xPosition));
            coordinateBinding.coordinateYPosition.setText(String.valueOf(coordinate.yPosition));
            coordinateBinding.coordinateClickDelay.setText(String.valueOf(coordinate.clickDelay));
            coordinateBinding.coordinateClickInterval.setText(String.valueOf(coordinate.clickInterval));
            coordinateBinding.coordinateClickNumber.setText(String.valueOf(coordinate.clickNumber));
            coordinateBinding.coordinateMaxTriggerCount.setText(
                    String.valueOf(coordinate.maxTriggerCount));
            coordinateBinding.coordinateInitialMatchWindow.setText(
                    String.valueOf(coordinate.initialMatchWindowMillis));
            coordinateBinding.coordinatePreconditionRuleId.setText(
                    coordinate.preconditionRuleId == null ? null
                            : String.valueOf(coordinate.preconditionRuleId));
            coordinateBinding.coordinateActionCooldown.setText(
                    String.valueOf(coordinate.actionCooldownMillis));
            coordinateBinding.coordinateTriggerCount.setText(String.valueOf(coordinate.triggerCount));
            coordinateBinding.coordinateComment.setText(coordinate.comment);
            updateCoordinateVisualStatus(coordinateBinding, coordinate);
            long day1 = (System.currentTimeMillis() - coordinate.createTime) / (1000 * 60 * 60 * 24);
            long day2 = (System.currentTimeMillis() - coordinate.lastTriggerTime) / (1000 * 60 * 60 * 24);
            coordinateBinding.coordinateCreateTime.setText(String.format("%s (%s天前)", dateFormat.format(new Date(coordinate.createTime)), day1));
            coordinateBinding.coordinateLastTriggerTime.setTextColor(day1 >= 60 && day2 >= 60 ? Color.RED : coordinateBinding.coordinateLastTriggerTime.getCurrentTextColor());
            if (coordinate.lastTriggerTime <= 0) {
                coordinateBinding.coordinateLastTriggerTime.setText("无触发记录");
            } else {
                coordinateBinding.coordinateLastTriggerTime.setText(String.format("%s (%s天前)", dateFormat.format(coordinate.lastTriggerTime), day2));
            }
            if (n < latestTriggerCoordinateList.size()) {
                coordinateBinding.coordinateModify.setTextColor(0xff00c507);
                if (n == 0) {
                    coordinateBinding.coordinateModify.setText("该坐标为最新触发坐标");
                } else {
                    coordinateBinding.coordinateModify.setText("该坐标最近5分钟内有被触发");
                }
            }
            Runnable coordinateSaveRun = new Runnable() {
                @Override
                public void run() {
                    String sX = StrUtil.trimToEmpty(coordinateBinding.coordinateXPosition.getText());
                    String sY = StrUtil.trimToEmpty(coordinateBinding.coordinateYPosition.getText());
                    String sDelay = StrUtil.trimToEmpty(coordinateBinding.coordinateClickDelay.getText());
                    String sInterval = StrUtil.trimToEmpty(coordinateBinding.coordinateClickInterval.getText());
                    String sNumber = StrUtil.trimToEmpty(coordinateBinding.coordinateClickNumber.getText());
                    String maxTriggerCount = StrUtil.trimToEmpty(
                            coordinateBinding.coordinateMaxTriggerCount.getText());
                    String initialMatchWindow = StrUtil.trimToEmpty(
                            coordinateBinding.coordinateInitialMatchWindow.getText());
                    String preconditionRuleId = StrUtil.trimToEmpty(
                            coordinateBinding.coordinatePreconditionRuleId.getText());
                    String actionCooldown = StrUtil.trimToEmpty(
                            coordinateBinding.coordinateActionCooldown.getText());
                    coordinateBinding.coordinateModify.setTextColor(0xfff20000);
                    Integer xPosition = parseNonNegativeInt(sX);
                    if (xPosition == null) {
                        coordinateBinding.coordinateModify.setText("X轴坐标不能为空");
                        return;
                    }
                    if (xPosition > metrics.widthPixels) {
                        coordinateBinding.coordinateModify.setText("X轴坐标超出屏幕寸");
                        return;
                    }
                    Integer yPosition = parseNonNegativeInt(sY);
                    if (yPosition == null) {
                        coordinateBinding.coordinateModify.setText("Y轴坐标不能为空");
                        return;
                    }
                    if (yPosition > metrics.heightPixels) {
                        coordinateBinding.coordinateModify.setText("Y轴坐标超出屏幕寸");
                        return;
                    }
                    Integer delay = parseNonNegativeInt(sDelay);
                    if (delay == null) {
                        coordinateBinding.coordinateModify.setText("延迟点击不能为空");
                        return;
                    }
                    Integer interval = parseNonNegativeInt(sInterval);
                    if (interval == null) {
                        coordinateBinding.coordinateModify.setText("点击间隔不能为空");
                        return;
                    }
                    Integer number = parseNonNegativeInt(sNumber);
                    if (number == null || number <= 0) {
                        coordinateBinding.coordinateModify.setText("点击次数不能为空");
                        return;
                    }
                    Integer maxTriggers = parseNonNegativeInt(maxTriggerCount);
                    Integer matchWindow = parseNonNegativeInt(initialMatchWindow);
                    Integer cooldown = parseNonNegativeInt(actionCooldown);
                    if (maxTriggers == null || matchWindow == null || cooldown == null) {
                        coordinateBinding.coordinateModify.setText("高级执行参数必须为非负整数");
                        return;
                    }
                    Long precondition;
                    try {
                        precondition = parseOptionalRuleId(preconditionRuleId);
                    } catch (IllegalArgumentException exception) {
                        coordinateBinding.coordinateModify.setText("前置规则ID格式错误");
                        return;
                    }
                    if ((coordinate.xPosition != xPosition || coordinate.yPosition != yPosition)
                            && !TextUtils.isEmpty(coordinate.visualSignature)) {
                        coordinate.visualSignature = null;
                    }
                    coordinate.xPosition = xPosition;
                    coordinate.yPosition = yPosition;
                    coordinate.clickDelay = delay;
                    coordinate.clickInterval = interval;
                    coordinate.clickNumber = number;
                    coordinate.maxTriggerCount = maxTriggers;
                    coordinate.initialMatchWindowMillis = matchWindow;
                    coordinate.preconditionRuleId = precondition;
                    coordinate.actionCooldownMillis = cooldown;
                    coordinate.comment = StrUtil.trimToEmpty(coordinateBinding.coordinateComment.getText());
                    updateCoordinateVisualStatus(coordinateBinding, coordinate);
                    queueCoordinateSave(coordinate);
                    coordinateBinding.coordinateModify.setTextColor(0xff000000);
                    coordinateBinding.coordinateModify.setText(dateFormatModify.format(new Date()) + " (已修改)");
                }
            };

            TextWatcher coordinateTextWatcher = new TextWatcher() {
                @Override
                public void beforeTextChanged(CharSequence s, int start, int count, int after) {

                }

                @Override
                public void onTextChanged(CharSequence s, int start, int before, int count) {

                }

                @Override
                public void afterTextChanged(Editable s) {
                    coordinateSaveRun.run();
                }
            };

            coordinateBinding.coordinateXPosition.addTextChangedListener(coordinateTextWatcher);
            coordinateBinding.coordinateYPosition.addTextChangedListener(coordinateTextWatcher);
            coordinateBinding.coordinateClickDelay.addTextChangedListener(coordinateTextWatcher);
            coordinateBinding.coordinateClickInterval.addTextChangedListener(coordinateTextWatcher);
            coordinateBinding.coordinateClickNumber.addTextChangedListener(coordinateTextWatcher);
            coordinateBinding.coordinateMaxTriggerCount.addTextChangedListener(coordinateTextWatcher);
            coordinateBinding.coordinateInitialMatchWindow.addTextChangedListener(coordinateTextWatcher);
            coordinateBinding.coordinatePreconditionRuleId.addTextChangedListener(coordinateTextWatcher);
            coordinateBinding.coordinateActionCooldown.addTextChangedListener(coordinateTextWatcher);
            coordinateBinding.coordinateComment.addTextChangedListener(coordinateTextWatcher);

            coordinateBinding.coordinateShare.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    CoordinateShare coordinateShare = new CoordinateShare();
                    coordinateShare.coordinate = coordinate;
                    coordinateShare.basicContent = new BasicContent();
                    coordinateShare.basicContent.fingerPrint = Build.FINGERPRINT;
                    coordinateShare.basicContent.displayMetrics = new DisplayMetrics();
                    getWindowManager().getDefaultDisplay().getRealMetrics(coordinateShare.basicContent.displayMetrics);
                    coordinateShare.basicContent.packageName = coordinate.appPackage;
                    try {
                        PackageInfo packageInfo = getPackageManager().getPackageInfo(coordinate.appPackage, PackageManager.GET_META_DATA);
                        coordinateShare.basicContent.versionCode = packageInfo.versionCode;
                        coordinateShare.basicContent.versionName = packageInfo.versionName;
                    } catch (PackageManager.NameNotFoundException ex) {
                        // ex.printStackTrace();
                    }
                    Gson gson = new GsonBuilder().setPrettyPrinting().create();
                    String strRule = '"' + CoordinateShare.class.getSimpleName() + '"' + ": " + gson.toJson(coordinateShare);
                    showEditShareFileNameDialog(strRule);
                }
            });

            coordinateBinding.coordinateDelete.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    new AlertDialog.Builder(EditDataActivity.this)
                            .setTitle("确定删除？")
                            .setNegativeButton("取消", null)
                            .setPositiveButton("确定", new DialogInterface.OnClickListener() {
                                @Override
                                public void onClick(DialogInterface dialog, int which) {
                                    dirtyCoordinates.remove(coordinate);
                                    MyApplication.executeDatabase(() -> dataDao.deleteCoordinate(coordinate));
                                    appDescribe.coordinateList.remove(coordinate);
                                    editDataBinding.coordinateLayout.removeView(coordinateBinding.getRoot());
                                    if (appDescribe.coordinateList.isEmpty()) {
                                        editDataBinding.coordinateLayout.setVisibility(View.GONE);
                                        baseSettingBinding.coordinateSwitch.setChecked(false);
                                        appDescribe.coordinateOnOff = false;
                                        if (appDescribe.widgetList.isEmpty()) {
                                            baseSettingBinding.widgetSwitch.setChecked(false);
                                            appDescribe.widgetOnOff = false;
                                        }
                                        queueAppDescribeSave();
                                    }
                                }
                            }).create().show();
                }
            });
            editDataBinding.coordinateLayout.addView(coordinateBinding.getRoot());
        }

        List<Widget> latestTriggerWidgetList = appDescribe.widgetList.stream()
                .filter(e -> e.lastTriggerTime > System.currentTimeMillis() - 1000 * 60 * 5)
                .sorted((e1, e2) -> Long.compare(e2.lastTriggerTime, e1.lastTriggerTime))
                .collect(Collectors.toList());
        appDescribe.widgetList.removeIf(e -> latestTriggerWidgetList.stream().anyMatch(n -> n == e));
        appDescribe.widgetList.sort((e1, e2) -> Long.compare(e2.createTime, e1.createTime));
        appDescribe.widgetList.addAll(0, latestTriggerWidgetList);
        if (appDescribe.widgetList.isEmpty()) {
            editDataBinding.widgetLayout.setVisibility(View.GONE);
        } else {
            editDataBinding.widgetLayout.setVisibility(View.VISIBLE);
        }
        if (editDataBinding.widgetLayout.getChildCount() > 2) {
            editDataBinding.widgetLayout.removeViews(2, editDataBinding.widgetLayout.getChildCount() - 2);
        }
        for (int n = 0; n < appDescribe.widgetList.size(); n++) {
            Widget widget = appDescribe.widgetList.get(n);
            ViewWidgetBinding widgetBinding = ViewWidgetBinding.inflate(inflater);
            widgetBinding.widgetActivity.setText(widget.appActivity);
            widgetBinding.widgetClickable.setText(String.valueOf(widget.widgetClickable));
            widgetBinding.widgetRect.setText(widget.widgetRect != null ? gson.toJson(widget.widgetRect) : null);
            widgetBinding.widgetNodeId.setText(widget.widgetNodeId != null ? String.valueOf(widget.widgetNodeId) : null);
            widgetBinding.widgetViewId.setText(widget.widgetViewId);
            widgetBinding.widgetDescribe.setText(widget.widgetDescribe);
            widgetBinding.widgetText.setText(widget.widgetText);
            widgetBinding.widgetParentViewId.setText(widget.widgetParentViewId);
            widgetBinding.widgetParentText.setText(widget.widgetParentText);
            widgetBinding.widgetChildText.setText(widget.widgetChildText);
            widgetBinding.widgetSiblingText.setText(widget.widgetSiblingText);
            widgetBinding.widgetExcludeText.setText(widget.widgetExcludeText);
            widgetBinding.widgetMaxTriggerCount.setText(String.valueOf(widget.maxTriggerCount));
            widgetBinding.widgetInitialMatchWindow.setText(
                    String.valueOf(widget.initialMatchWindowMillis));
            widgetBinding.widgetPreconditionRuleId.setText(widget.preconditionRuleId == null ? null
                    : String.valueOf(widget.preconditionRuleId));
            widgetBinding.widgetActionCooldown.setText(
                    String.valueOf(widget.actionCooldownMillis));
            widgetBinding.widgetClickDelay.setText(String.valueOf(widget.clickDelay));
            widgetBinding.widgetDebounceDelay.setText(String.valueOf(widget.debounceDelay));
            widgetBinding.widgetNoRepeat.setChecked(widget.noRepeat);
            widgetBinding.widgetClickOnly.setChecked(widget.clickOnly);
            widgetBinding.widgetComment.setText(widget.comment);
            widgetBinding.widgetClickNumber.setText(String.valueOf(widget.clickNumber));
            widgetBinding.widgetClickInterval.setText(String.valueOf(widget.clickInterval));
            widgetBinding.widgetTriggerCount.setText(String.valueOf(widget.triggerCount));
            widgetBinding.widgetActionClick.setChecked(widget.action == Widget.ACTION_CLICK);
            widgetBinding.widgetActionBack.setChecked(widget.action == Widget.ACTION_BACK);
            widgetBinding.widgetActionClick.setEnabled(widget.action != Widget.ACTION_CLICK);
            widgetBinding.widgetActionBack.setEnabled(widget.action != Widget.ACTION_BACK);
            widgetBinding.llClickProp.setVisibility(widget.action == Widget.ACTION_CLICK ? View.VISIBLE : View.GONE);
            widgetBinding.widgetConditionOr.setChecked(widget.condition == Widget.CONDITION_OR);
            widgetBinding.widgetConditionAnd.setChecked(widget.condition == Widget.CONDITION_AND);
            widgetBinding.widgetConditionOr.setEnabled(widget.condition != Widget.CONDITION_OR);
            widgetBinding.widgetConditionAnd.setEnabled(widget.condition != Widget.CONDITION_AND);
            widgetBinding.widgetTriggerReason.setText(StrUtil.blankToDefault(widget.triggerReason, "无触发记录"));
            long day1 = (System.currentTimeMillis() - widget.createTime) / (1000 * 60 * 60 * 24);
            long day2 = (System.currentTimeMillis() - widget.lastTriggerTime) / (1000 * 60 * 60 * 24);
            widgetBinding.widgetCreateTime.setText(String.format("%s (%s天前)", dateFormat.format(new Date(widget.createTime)), day1));
            widgetBinding.widgetLastTriggerTime.setTextColor(day1 >= 60 && day2 >= 60 ? Color.RED : widgetBinding.widgetLastTriggerTime.getCurrentTextColor());
            if (widget.lastTriggerTime <= 0) {
                widgetBinding.widgetLastTriggerTime.setText("无触发记录");
            } else {
                widgetBinding.widgetLastTriggerTime.setText(String.format("%s (%s天前)", dateFormat.format(widget.lastTriggerTime), day2));
            }
            if (n < latestTriggerWidgetList.size()) {
                widgetBinding.widgetModify.setTextColor(0xff00c507);
                if (n == 0) {
                    widgetBinding.widgetModify.setText("该控件为最新触发控件");
                } else {
                    widgetBinding.widgetModify.setText("该控件最近5分钟内有被触发");
                }
            }
            Runnable widgetSaveRun = new Runnable() {
                @Override
                public void run() {
                    String clickNumber = StrUtil.trimToEmpty(widgetBinding.widgetClickNumber.getText());
                    String clickInterval = StrUtil.trimToEmpty(widgetBinding.widgetClickInterval.getText());
                    String clickDelay = StrUtil.trimToEmpty(widgetBinding.widgetClickDelay.getText());
                    String debounceDelay = StrUtil.trimToEmpty(widgetBinding.widgetDebounceDelay.getText());
                    String maxTriggerCount = StrUtil.trimToEmpty(
                            widgetBinding.widgetMaxTriggerCount.getText());
                    String initialMatchWindow = StrUtil.trimToEmpty(
                            widgetBinding.widgetInitialMatchWindow.getText());
                    String preconditionRuleId = StrUtil.trimToEmpty(
                            widgetBinding.widgetPreconditionRuleId.getText());
                    String actionCooldown = StrUtil.trimToEmpty(
                            widgetBinding.widgetActionCooldown.getText());
                    widgetBinding.widgetModify.setTextColor(0xfff20000);
                    Integer number = parseNonNegativeInt(clickNumber);
                    if (number == null || number <= 0) {
                        widgetBinding.widgetModify.setText("点击次数不能为空");
                        return;
                    }
                    Integer interval = parseNonNegativeInt(clickInterval);
                    if (interval == null) {
                        widgetBinding.widgetModify.setText("点击间隔不能为空");
                        return;
                    }
                    Integer delay = parseNonNegativeInt(clickDelay);
                    if (delay == null) {
                        widgetBinding.widgetModify.setText("最小触发间隔不能为空");
                        return;
                    }
                    Integer debounce = parseNonNegativeInt(debounceDelay);
                    if (debounce == null) {
                        widgetBinding.widgetModify.setText("防抖延迟不能为空");
                        return;
                    }
                    Integer maxTriggers = parseNonNegativeInt(maxTriggerCount);
                    Integer matchWindow = parseNonNegativeInt(initialMatchWindow);
                    Integer cooldown = parseNonNegativeInt(actionCooldown);
                    if (maxTriggers == null || matchWindow == null || cooldown == null) {
                        widgetBinding.widgetModify.setText("高级执行参数必须为非负整数");
                        return;
                    }
                    Long precondition;
                    try {
                        precondition = parseOptionalRuleId(preconditionRuleId);
                    } catch (IllegalArgumentException exception) {
                        widgetBinding.widgetModify.setText("前置规则ID格式错误");
                        return;
                    }
                    try {
                        widget.widgetRect = gson.fromJson(StrUtil.trimToEmpty(widgetBinding.widgetRect.getText()), Rect.class);
                    } catch (JsonSyntaxException jsonSyntaxException) {
                        widgetBinding.widgetModify.setText("Bonus格式错误");
                        return;
                    }
                    try {
                        widget.widgetNodeId = Long.valueOf(StrUtil.trimToEmpty(widgetBinding.widgetNodeId.getText()));
                    } catch (NumberFormatException numberFormatException) {
                        widget.widgetNodeId = null;
                    }
                    widget.widgetViewId = StrUtil.toStringOrEmpty(widgetBinding.widgetViewId.getText());
                    widget.widgetDescribe = StrUtil.toStringOrEmpty(widgetBinding.widgetDescribe.getText());
                    widget.widgetText = StrUtil.toStringOrEmpty(widgetBinding.widgetText.getText());
                    widget.widgetParentViewId = StrUtil.toStringOrEmpty(
                            widgetBinding.widgetParentViewId.getText());
                    widget.widgetParentText = StrUtil.toStringOrEmpty(
                            widgetBinding.widgetParentText.getText());
                    widget.widgetChildText = StrUtil.toStringOrEmpty(
                            widgetBinding.widgetChildText.getText());
                    widget.widgetSiblingText = StrUtil.toStringOrEmpty(
                            widgetBinding.widgetSiblingText.getText());
                    widget.widgetExcludeText = StrUtil.toStringOrEmpty(
                            widgetBinding.widgetExcludeText.getText());
                    try {
                        widget.validatePatterns();
                    } catch (IllegalArgumentException e) {
                        widgetBinding.widgetModify.setText(e.getMessage());
                        return;
                    }
                    widget.comment = StrUtil.trimToEmpty(widgetBinding.widgetComment.getText());
                    widget.clickNumber = number;
                    widget.clickInterval = interval;
                    widget.clickDelay = delay;
                    widget.debounceDelay = debounce;
                    widget.maxTriggerCount = maxTriggers;
                    widget.initialMatchWindowMillis = matchWindow;
                    widget.preconditionRuleId = precondition;
                    widget.actionCooldownMillis = cooldown;
                    widget.noRepeat = widgetBinding.widgetNoRepeat.isChecked();
                    widget.clickOnly = widgetBinding.widgetClickOnly.isChecked();
                    queueWidgetSave(widget);
                    widgetBinding.widgetModify.setTextColor(0xff000000);
                    widgetBinding.widgetModify.setText(dateFormatModify.format(new Date()) + " (已修改)");
                }
            };

            TextWatcher widgetTextWatcher = new TextWatcher() {
                @Override
                public void beforeTextChanged(CharSequence s, int start, int count, int after) {

                }

                @Override
                public void onTextChanged(CharSequence s, int start, int before, int count) {

                }

                @Override
                public void afterTextChanged(Editable s) {
                    widgetSaveRun.run();
                }
            };

            widgetBinding.widgetRect.addTextChangedListener(widgetTextWatcher);
            widgetBinding.widgetNodeId.addTextChangedListener(widgetTextWatcher);
            widgetBinding.widgetViewId.addTextChangedListener(widgetTextWatcher);
            widgetBinding.widgetDescribe.addTextChangedListener(widgetTextWatcher);
            widgetBinding.widgetText.addTextChangedListener(widgetTextWatcher);
            widgetBinding.widgetParentViewId.addTextChangedListener(widgetTextWatcher);
            widgetBinding.widgetParentText.addTextChangedListener(widgetTextWatcher);
            widgetBinding.widgetChildText.addTextChangedListener(widgetTextWatcher);
            widgetBinding.widgetSiblingText.addTextChangedListener(widgetTextWatcher);
            widgetBinding.widgetExcludeText.addTextChangedListener(widgetTextWatcher);
            widgetBinding.widgetMaxTriggerCount.addTextChangedListener(widgetTextWatcher);
            widgetBinding.widgetInitialMatchWindow.addTextChangedListener(widgetTextWatcher);
            widgetBinding.widgetPreconditionRuleId.addTextChangedListener(widgetTextWatcher);
            widgetBinding.widgetActionCooldown.addTextChangedListener(widgetTextWatcher);
            widgetBinding.widgetClickNumber.addTextChangedListener(widgetTextWatcher);
            widgetBinding.widgetClickInterval.addTextChangedListener(widgetTextWatcher);
            widgetBinding.widgetClickDelay.addTextChangedListener(widgetTextWatcher);
            widgetBinding.widgetDebounceDelay.addTextChangedListener(widgetTextWatcher);
            widgetBinding.widgetComment.addTextChangedListener(widgetTextWatcher);

            View.OnClickListener widgetClickListener = new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    if (v == widgetBinding.widgetActionClick || v == widgetBinding.widgetActionBack) {
                        widget.action = Integer.parseInt((String) v.getTag());
                        widgetBinding.widgetActionClick.setChecked(widget.action == Widget.ACTION_CLICK);
                        widgetBinding.widgetActionBack.setChecked(widget.action == Widget.ACTION_BACK);
                        widgetBinding.widgetActionClick.setEnabled(widget.action != Widget.ACTION_CLICK);
                        widgetBinding.widgetActionBack.setEnabled(widget.action != Widget.ACTION_BACK);
                        widgetBinding.llClickProp.setVisibility(widget.action == Widget.ACTION_CLICK ? View.VISIBLE : View.GONE);
                    }
                    if (v == widgetBinding.widgetConditionOr || v == widgetBinding.widgetConditionAnd) {
                        widget.condition = Integer.parseInt((String) v.getTag());
                        widgetBinding.widgetConditionOr.setChecked(widget.condition == Widget.CONDITION_OR);
                        widgetBinding.widgetConditionAnd.setChecked(widget.condition == Widget.CONDITION_AND);
                        widgetBinding.widgetConditionOr.setEnabled(widget.condition != Widget.CONDITION_OR);
                        widgetBinding.widgetConditionAnd.setEnabled(widget.condition != Widget.CONDITION_AND);
                    }
                    widgetSaveRun.run();
                }
            };
            widgetBinding.widgetNoRepeat.setOnClickListener(widgetClickListener);
            widgetBinding.widgetClickOnly.setOnClickListener(widgetClickListener);
            widgetBinding.widgetActionClick.setOnClickListener(widgetClickListener);
            widgetBinding.widgetActionBack.setOnClickListener(widgetClickListener);
            widgetBinding.widgetConditionOr.setOnClickListener(widgetClickListener);
            widgetBinding.widgetConditionAnd.setOnClickListener(widgetClickListener);

            widgetBinding.widgetShare.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    WidgetShare widgetShare = new WidgetShare();
                    widgetShare.widget = widget;
                    widgetShare.basicContent = new BasicContent();
                    widgetShare.basicContent.fingerPrint = Build.FINGERPRINT;
                    widgetShare.basicContent.displayMetrics = new DisplayMetrics();
                    getWindowManager().getDefaultDisplay().getRealMetrics(widgetShare.basicContent.displayMetrics);
                    widgetShare.basicContent.packageName = widget.appPackage;
                    try {
                        PackageInfo packageInfo = getPackageManager().getPackageInfo(widget.appPackage, PackageManager.GET_META_DATA);
                        widgetShare.basicContent.versionCode = packageInfo.versionCode;
                        widgetShare.basicContent.versionName = packageInfo.versionName;
                    } catch (PackageManager.NameNotFoundException ex) {
                        // ex.printStackTrace();
                    }
                    Gson gson = new GsonBuilder().setPrettyPrinting().create();
                    String strRule = '"' + WidgetShare.class.getSimpleName() + '"' + ": " + gson.toJson(widgetShare);
                    showEditShareFileNameDialog(strRule);
                }
            });

            widgetBinding.widgetDelete.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    new AlertDialog.Builder(EditDataActivity.this)
                            .setTitle("确定删除？")
                            .setNegativeButton("取消", null)
                            .setPositiveButton("确定", new DialogInterface.OnClickListener() {
                                @Override
                                public void onClick(DialogInterface dialog, int which) {
                                    dirtyWidgets.remove(widget);
                                    MyApplication.executeDatabase(() -> dataDao.deleteWidget(widget));
                                    appDescribe.widgetList.remove(widget);
                                    editDataBinding.widgetLayout.removeView(widgetBinding.getRoot());
                                    if (appDescribe.widgetList.isEmpty()) {
                                        editDataBinding.widgetLayout.setVisibility(View.GONE);
                                        baseSettingBinding.widgetSwitch.setChecked(false);
                                        appDescribe.widgetOnOff = false;
                                        if (appDescribe.coordinateList.isEmpty()) {
                                            baseSettingBinding.coordinateSwitch.setChecked(false);
                                            appDescribe.coordinateOnOff = false;
                                        }
                                        queueAppDescribeSave();
                                    }
                                }
                            }).create().show();
                }
            });
            editDataBinding.widgetLayout.addView(widgetBinding.getRoot());
        }
    }

    @Override
    protected void onPause() {
        super.onPause();
        if (appDescribe != null) {
            flushPendingChanges(true);
        }
    }

    @Override
    protected void onDestroy() {
        if (saveHandler != null) {
            saveHandler.removeCallbacks(persistChangesRunnable);
        }
        super.onDestroy();
        if (myAppConfig != null && myAppConfig.autoHideOnTaskList) {
            MyUtils.setExcludeFromRecents(true);
        }
    }

    private static Integer parseNonNegativeInt(String value) {
        if (TextUtils.isEmpty(value)) {
            return null;
        }
        try {
            int parsed = Integer.parseInt(value);
            return parsed >= 0 ? parsed : null;
        } catch (NumberFormatException exception) {
            return null;
        }
    }

    private static Long parseOptionalRuleId(String value) {
        if (TextUtils.isEmpty(value)) {
            return null;
        }
        try {
            long parsed = Long.parseLong(value);
            if (parsed < 0L) {
                throw new NumberFormatException("negative");
            }
            return parsed;
        } catch (NumberFormatException exception) {
            throw new IllegalArgumentException("invalid rule id", exception);
        }
    }

    private static void updateCoordinateVisualStatus(ViewCoordinateBinding binding,
                                                     Coordinate coordinate) {
        if (TextUtils.isEmpty(coordinate.visualSignature)) {
            binding.coordinateVisualStatus.setText("未启用（需从冻结画面重新创建）");
            binding.coordinateVisualStatus.setTextColor(
                    binding.coordinateActivity.getCurrentTextColor());
        } else if (VisualCoordinateSignature.isValid(coordinate.visualSignature)) {
            binding.coordinateVisualStatus.setText("已启用（修改 X/Y 后自动移除）");
            binding.coordinateVisualStatus.setTextColor(0xff008a00);
        } else {
            binding.coordinateVisualStatus.setText("数据无效（运行时将安全跳过）");
            binding.coordinateVisualStatus.setTextColor(Color.RED);
        }
    }

    private void queueAppDescribeSave() {
        appDescribeDirty = true;
        schedulePendingChanges();
    }

    private void queueCoordinateSave(Coordinate coordinate) {
        dirtyCoordinates.add(coordinate);
        schedulePendingChanges();
    }

    private void queueWidgetSave(Widget widget) {
        dirtyWidgets.add(widget);
        schedulePendingChanges();
    }

    private void schedulePendingChanges() {
        saveHandler.removeCallbacks(persistChangesRunnable);
        saveHandler.postDelayed(persistChangesRunnable, SAVE_DEBOUNCE_MILLIS);
    }

    private void flushPendingChanges(boolean notifyService) {
        if (saveHandler == null) {
            return;
        }
        saveHandler.removeCallbacks(persistChangesRunnable);
        AppDescribe appDescribeSnapshot = appDescribeDirty ? appDescribe : null;
        List<Coordinate> coordinates = new ArrayList<>(dirtyCoordinates);
        List<Widget> widgets = new ArrayList<>(dirtyWidgets);
        appDescribeDirty = false;
        dirtyCoordinates.clear();
        dirtyWidgets.clear();
        String packageToRefresh = appDescribe == null ? null : appDescribe.appPackage;
        if (appDescribeSnapshot == null && coordinates.isEmpty() && widgets.isEmpty() && !notifyService) {
            return;
        }
        MyApplication.executeDatabase(new Runnable() {
            @Override
            public void run() {
                if (appDescribeSnapshot != null) {
                    dataDao.updateAppDescribe(appDescribeSnapshot);
                }
                if (!coordinates.isEmpty()) {
                    dataDao.updateCoordinates(coordinates);
                }
                if (!widgets.isEmpty()) {
                    dataDao.updateWidgets(widgets);
                }
                if (notifyService && !TextUtils.isEmpty(packageToRefresh)) {
                    MyUtils.requestUpdateAppDescribe(packageToRefresh);
                }
            }
        });
    }

    private void showEditShareFileNameDialog(String strRegulation) {
        ViewEditFileNameBinding binding = ViewEditFileNameBinding.inflate(inflater);
        binding.fileName.setHint(DigestUtil.md5Hex(strRegulation));
        new AlertDialog.Builder(EditDataActivity.this)
                .setView(binding.getRoot())
                .setCancelable(false)
                .setTitle("编辑文件名称")
                .setNegativeButton("取消", null)
                .setPositiveButton("确定", new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface dialog, int which) {
                        String fileName = StrUtil.trimToEmpty(binding.fileName.getText());
                        String requestedName = (fileName.isEmpty() ? String.valueOf(binding.fileName.getHint()) : fileName) + ".txt";
                        MyApplication.executeIo(new Runnable() {
                            @Override
                            public void run() {
                                try {
                                    File file = ExportFileManager.writeText(getApplicationContext(), requestedName, strRegulation);
                                    MyApplication.postToMain(() -> shareRuleFile(file, strRegulation));
                                } catch (Exception e) {
                                    MyApplication.postToMain(() -> Toast.makeText(context,
                                            "生成分享文件时发生错误", Toast.LENGTH_SHORT).show());
                                }
                            }
                        });
                    }
                }).create().show();
    }

    private void shareRuleFile(File file, String rule) {
        if (isFinishing() || isDestroyed()) {
            return;
        }
        Intent intent = new Intent(Intent.ACTION_SEND);
        Uri uri = FileProvider.getUriForFile(this, BuildConfig.APPLICATION_ID + ".fileprovider", file);
        intent.setDataAndType(uri, "text/plain");
        intent.putExtra(Intent.EXTRA_TEXT, rule);
        intent.putExtra(Intent.EXTRA_STREAM, uri);
        intent.setClipData(ClipData.newUri(getContentResolver(), "regulation", uri));
        intent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
        startActivity(Intent.createChooser(intent, "分享"));
    }
}
