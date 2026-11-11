package com.lgh.tapclick.myactivity;

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
import android.widget.TextView;
import android.widget.ScrollView;
import android.view.ViewGroup;
import androidx.recyclerview.widget.RecyclerView;
import androidx.recyclerview.widget.LinearLayoutManager;

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
import com.lgh.tapclick.myclass.RuleConfiguration;
import com.lgh.tapclick.myclass.RuleReference;
import com.lgh.tapclick.myclass.ExportFileManager;
import com.lgh.tapclick.myclass.MyApplication;
import com.lgh.tapclick.myclass.PackageCatalog;
import com.lgh.tapclick.myclass.VisualCoordinateSignature;
import com.lgh.tapclick.myfunction.MyUtils;

import java.io.File;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.Comparator;
import java.util.Collections;

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
    private final Map<Long, RuleConfiguration.CoordinateConfig> dirtyCoordinates = new LinkedHashMap<>();
    private final Map<Long, RuleConfiguration.WidgetConfig> dirtyWidgets = new LinkedHashMap<>();
    private RuleConfiguration.AppDescribeConfig pendingAppConfiguration;
    private final RulesAdapter rulesAdapter = new RulesAdapter();
    private AlertDialog ruleEditorDialog;
    private boolean baseExpanded;
    private boolean packageCatalogReady;

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
            if (isFinishing() || isDestroyed() || result == null) { return; }
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

        pkgSuggestNotOnList = Collections.emptySet();
        MyApplication.executeIo(() -> {
            Set<String> packages = PackageCatalog.getSuggestedRestrictedPackages(context);
            MyApplication.postToMain(() -> {
                if (!isFinishing() && !isDestroyed()) {
                    pkgSuggestNotOnList = packages;
                    packageCatalogReady = true;
                    if (baseSettingBinding != null) {
                        baseSettingBinding.coordinateSwitch.setEnabled(true);
                        baseSettingBinding.widgetSwitch.setEnabled(true);
                    }
                }
            });
        });
        editDataBinding.rulesList.setLayoutManager(new LinearLayoutManager(this));
        editDataBinding.rulesList.setAdapter(rulesAdapter);
        editDataBinding.rulesList.setItemAnimator(null);
        editDataBinding.tvBaseSetting.setOnClickListener(view -> {
            baseExpanded = !baseExpanded;
            if (baseSettingBinding != null) {
                baseSettingBinding.getRoot().setVisibility(baseExpanded ? View.VISIBLE : View.GONE);
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

        baseSettingBinding.coordinateSwitch.setEnabled(packageCatalogReady);
        baseSettingBinding.widgetSwitch.setEnabled(packageCatalogReady);
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
                Integer coordinateDuration = coordinateTime.equals("∞") ? appDescribe.coordinateRetrieveTime
                        : parseNonNegativeInt(coordinateTime);
                Integer widgetDuration = widgetTime.equals("∞") ? appDescribe.widgetRetrieveTime
                        : parseNonNegativeInt(widgetTime);
                if (coordinateDuration == null || widgetDuration == null) {
                    editDataBinding.baseSettingModify.setText("检索时间必须为有效的非负整数");
                    return;
                }
                appDescribe.coordinateOnOff = baseSettingBinding.coordinateSwitch.isChecked();
                appDescribe.coordinateRetrieveTime = coordinateDuration;
                appDescribe.coordinateRetrieveAllTime = baseSettingBinding.coordinateRetrieveAllTime.isChecked();
                appDescribe.widgetOnOff = baseSettingBinding.widgetSwitch.isChecked();
                appDescribe.widgetRetrieveTime = widgetDuration;
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
                            switchCompat.setChecked(true);
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
        baseSettingBinding.getRoot().setVisibility(baseExpanded ? View.VISIBLE : View.GONE);
        editDataBinding.baseSettingLayout.addView(baseSettingBinding.getRoot());

        long recentThreshold = System.currentTimeMillis() - 5 * 60 * 1000L;
        Comparator<Coordinate> coordinateOrder = Comparator
                .comparingLong((Coordinate rule) -> rule.lastTriggerTime > recentThreshold ? rule.lastTriggerTime : rule.createTime).reversed();
        Comparator<Widget> widgetOrder = Comparator
                .comparingLong((Widget rule) -> rule.lastTriggerTime > recentThreshold ? rule.lastTriggerTime : rule.createTime).reversed();
        appDescribe.coordinateList.sort(coordinateOrder);
        appDescribe.widgetList.sort(widgetOrder);
        rulesAdapter.notifyDataSetChanged();
    }

    private View createCoordinateEditor(Coordinate coordinate) {
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
        bindPrecondition(coordinateBinding.coordinatePreconditionRuleId, coordinate);
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
                                dirtyCoordinates.remove(coordinate.id);
                                MyApplication.executeDatabase(() -> dataDao.deleteCoordinate(coordinate));
                                appDescribe.coordinateList.remove(coordinate);
                                rulesAdapter.notifyDataSetChanged();
                                if (appDescribe.coordinateList.isEmpty()) {
                                    baseSettingBinding.coordinateSwitch.setChecked(false);
                                    appDescribe.coordinateOnOff = false;
                                    if (appDescribe.widgetList.isEmpty()) {
                                        baseSettingBinding.widgetSwitch.setChecked(false);
                                        appDescribe.widgetOnOff = false;
                                    }
                                    queueAppDescribeSave();
                                }
                                if (ruleEditorDialog != null) { ruleEditorDialog.dismiss(); }
                            }
                        }).create().show();
            }
        });
        return coordinateBinding.getRoot();
    }

    private View createWidgetEditor(Widget widget) {
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
        bindPrecondition(widgetBinding.widgetPreconditionRuleId, widget);
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
                Widget draft = new Widget(widget);
                try {
                    draft.widgetRect = gson.fromJson(StrUtil.trimToEmpty(widgetBinding.widgetRect.getText()), Rect.class);
                } catch (JsonSyntaxException jsonSyntaxException) {
                    widgetBinding.widgetModify.setText("Bonus格式错误");
                    return;
                }
                try {
                    draft.widgetNodeId = Long.valueOf(StrUtil.trimToEmpty(widgetBinding.widgetNodeId.getText()));
                } catch (NumberFormatException numberFormatException) {
                    draft.widgetNodeId = null;
                }
                draft.widgetViewId = StrUtil.toStringOrEmpty(widgetBinding.widgetViewId.getText());
                draft.widgetDescribe = StrUtil.toStringOrEmpty(widgetBinding.widgetDescribe.getText());
                draft.widgetText = StrUtil.toStringOrEmpty(widgetBinding.widgetText.getText());
                draft.widgetParentViewId = StrUtil.toStringOrEmpty(
                        widgetBinding.widgetParentViewId.getText());
                draft.widgetParentText = StrUtil.toStringOrEmpty(
                        widgetBinding.widgetParentText.getText());
                draft.widgetChildText = StrUtil.toStringOrEmpty(
                        widgetBinding.widgetChildText.getText());
                draft.widgetSiblingText = StrUtil.toStringOrEmpty(
                        widgetBinding.widgetSiblingText.getText());
                draft.widgetExcludeText = StrUtil.toStringOrEmpty(
                        widgetBinding.widgetExcludeText.getText());
                try {
                    draft.validatePatterns();
                } catch (IllegalArgumentException e) {
                    widgetBinding.widgetModify.setText(e.getMessage());
                    return;
                }
                draft.comment = StrUtil.trimToEmpty(widgetBinding.widgetComment.getText());
                draft.clickNumber = number;
                draft.clickInterval = interval;
                draft.clickDelay = delay;
                draft.debounceDelay = debounce;
                draft.maxTriggerCount = maxTriggers;
                draft.initialMatchWindowMillis = matchWindow;
                draft.actionCooldownMillis = cooldown;
                draft.noRepeat = widgetBinding.widgetNoRepeat.isChecked();
                draft.clickOnly = widgetBinding.widgetClickOnly.isChecked();
                widget.widgetRect = draft.widgetRect;
                widget.widgetNodeId = draft.widgetNodeId;
                widget.widgetViewId = draft.widgetViewId;
                widget.widgetDescribe = draft.widgetDescribe;
                widget.widgetText = draft.widgetText;
                widget.widgetParentViewId = draft.widgetParentViewId;
                widget.widgetParentText = draft.widgetParentText;
                widget.widgetChildText = draft.widgetChildText;
                widget.widgetSiblingText = draft.widgetSiblingText;
                widget.widgetExcludeText = draft.widgetExcludeText;
                widget.comment = draft.comment;
                widget.clickNumber = draft.clickNumber;
                widget.clickInterval = draft.clickInterval;
                widget.clickDelay = draft.clickDelay;
                widget.debounceDelay = draft.debounceDelay;
                widget.maxTriggerCount = draft.maxTriggerCount;
                widget.initialMatchWindowMillis = draft.initialMatchWindowMillis;
                widget.actionCooldownMillis = draft.actionCooldownMillis;
                widget.noRepeat = draft.noRepeat;
                widget.clickOnly = draft.clickOnly;
                widget.preparePatterns();
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
                                dirtyWidgets.remove(widget.id);
                                MyApplication.executeDatabase(() -> dataDao.deleteWidget(widget));
                                appDescribe.widgetList.remove(widget);
                                rulesAdapter.notifyDataSetChanged();
                                if (appDescribe.widgetList.isEmpty()) {
                                    baseSettingBinding.widgetSwitch.setChecked(false);
                                    appDescribe.widgetOnOff = false;
                                    if (appDescribe.coordinateList.isEmpty()) {
                                        baseSettingBinding.coordinateSwitch.setChecked(false);
                                        appDescribe.coordinateOnOff = false;
                                    }
                                    queueAppDescribeSave();
                                }
                                if (ruleEditorDialog != null) { ruleEditorDialog.dismiss(); }
                            }
                        }).create().show();
            }
        });
        return widgetBinding.getRoot();
    }

    @Override
    protected void onPause() {
        super.onPause();
        if (ruleEditorDialog != null) { ruleEditorDialog.dismiss(); }
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
        pendingAppConfiguration = new RuleConfiguration.AppDescribeConfig(appDescribe);
        schedulePendingChanges();
    }

    private final class RulesAdapter extends RecyclerView.Adapter<RuleHolder> {
        @Override
        public RuleHolder onCreateViewHolder(ViewGroup parent, int viewType) {
            TextView summary = new TextView(parent.getContext());
            int padding = (int) (16 * getResources().getDisplayMetrics().density);
            summary.setPadding(padding, padding, padding, padding);
            summary.setTextSize(15);
            summary.setTextColor(Color.BLACK);
            summary.setBackgroundResource(com.lgh.tapclick.R.drawable.item_bg_ripple);
            summary.setLayoutParams(new RecyclerView.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
            return new RuleHolder(summary);
        }

        @Override
        public void onBindViewHolder(RuleHolder holder, int position) {
            Object rule = ruleAt(position);
            holder.summary.setText(ruleLabel(rule) + "\n" + ruleActivity(rule) + "\n点击编辑");
            holder.summary.setOnClickListener(view -> {
                int index = holder.getBindingAdapterPosition();
                if (index == RecyclerView.NO_POSITION) { return; }
                Object selected = ruleAt(index);
                ScrollView scroll = new ScrollView(EditDataActivity.this);
                scroll.setFillViewport(true);
                scroll.addView(selected instanceof Coordinate
                        ? createCoordinateEditor((Coordinate) selected) : createWidgetEditor((Widget) selected));
                ruleEditorDialog = new AlertDialog.Builder(EditDataActivity.this)
                        .setTitle(ruleLabel(selected)).setView(scroll).setPositiveButton("完成", null).create();
                ruleEditorDialog.setOnDismissListener(dialog -> {
                    flushPendingChanges(true);
                    notifyDataSetChanged();
                    ruleEditorDialog = null;
                });
                ruleEditorDialog.show();
                ruleEditorDialog.getWindow().setLayout(ViewGroup.LayoutParams.MATCH_PARENT,
                        (int) (metrics.heightPixels * 0.85f));
            });
        }

        private Object ruleAt(int position) {
            int coordinates = appDescribe.coordinateList.size();
            return position < coordinates ? appDescribe.coordinateList.get(position)
                    : appDescribe.widgetList.get(position - coordinates);
        }

        @Override
        public int getItemCount() {
            return appDescribe == null ? 0 : appDescribe.coordinateList.size() + appDescribe.widgetList.size();
        }
    }

    private static final class RuleHolder extends RecyclerView.ViewHolder {
        final TextView summary;
        RuleHolder(TextView summary) { super(summary); this.summary = summary; }
    }

    private static String ruleActivity(Object rule) {
        return rule instanceof Coordinate ? ((Coordinate) rule).appActivity : ((Widget) rule).appActivity;
    }

    private static RuleReference ruleKey(Object rule) {
        return rule instanceof Coordinate
                ? new RuleReference(RuleReference.COORDINATE, ((Coordinate) rule).id)
                : new RuleReference(RuleReference.WIDGET, ((Widget) rule).id);
    }

    private static String ruleLabel(Object rule) {
        if (rule instanceof Coordinate) {
            Coordinate coordinate = (Coordinate) rule;
            return "坐标 #" + coordinate.id + " · " + coordinate.xPosition + ", " + coordinate.yPosition
                    + (TextUtils.isEmpty(coordinate.comment) ? "" : " · " + coordinate.comment);
        }
        Widget widget = (Widget) rule;
        String detail = !TextUtils.isEmpty(widget.comment) ? widget.comment
                : !TextUtils.isEmpty(widget.widgetText) ? widget.widgetText : widget.widgetViewId;
        return "控件 #" + widget.id + (TextUtils.isEmpty(detail) ? "" : " · " + detail);
    }

    private void bindPrecondition(TextView field, Object owner) {
        Long id = owner instanceof Coordinate ? ((Coordinate) owner).preconditionRuleId
                : ((Widget) owner).preconditionRuleId;
        int type = owner instanceof Coordinate ? ((Coordinate) owner).preconditionRuleType
                : ((Widget) owner).preconditionRuleType;
        RuleReference resolved = RuleReference.resolve(type, id, ruleActivity(owner),
                appDescribe.coordinateList, appDescribe.widgetList);
        field.setFocusable(false);
        field.setClickable(true);
        field.setText(id == null ? "无（点击选择）" : resolved == null ? "无效或有歧义，请重新选择"
                : (resolved.type == RuleReference.COORDINATE ? "坐标 #" : "控件 #") + resolved.id);
        field.setOnClickListener(view -> {
            List<Object> candidates = new ArrayList<>();
            candidates.add(null);
            for (Coordinate rule : appDescribe.coordinateList) {
                if (rule != owner && TextUtils.equals(rule.appActivity, ruleActivity(owner))) { candidates.add(rule); }
            }
            for (Widget rule : appDescribe.widgetList) {
                if (rule != owner && TextUtils.equals(rule.appActivity, ruleActivity(owner))) { candidates.add(rule); }
            }
            String[] labels = new String[candidates.size()];
            for (int i = 0; i < labels.length; i++) {
                labels[i] = candidates.get(i) == null ? "无前置规则" : ruleLabel(candidates.get(i));
            }
            new AlertDialog.Builder(this).setTitle("选择同一页面的前置规则")
                    .setItems(labels, (dialog, which) -> {
                        Object candidate = candidates.get(which);
                        RuleReference selected = candidate == null ? null : ruleKey(candidate);
                        String error = RuleReference.validate(ruleKey(owner), selected, ruleActivity(owner),
                                appDescribe.coordinateList, appDescribe.widgetList);
                        if (error != null) {
                            Toast.makeText(this, error, Toast.LENGTH_LONG).show();
                            return;
                        }
                        if (owner instanceof Coordinate) {
                            Coordinate rule = (Coordinate) owner;
                            rule.preconditionRuleId = selected == null ? null : selected.id;
                            rule.preconditionRuleType = selected == null ? 0 : selected.type;
                            queueCoordinateSave(rule);
                        } else {
                            Widget rule = (Widget) owner;
                            rule.preconditionRuleId = selected == null ? null : selected.id;
                            rule.preconditionRuleType = selected == null ? 0 : selected.type;
                            queueWidgetSave(rule);
                        }
                        bindPrecondition(field, owner);
                    }).show();
        });
    }

    private void queueCoordinateSave(Coordinate coordinate) {
        dirtyCoordinates.put(coordinate.id, new RuleConfiguration.CoordinateConfig(coordinate));
        schedulePendingChanges();
    }

    private void queueWidgetSave(Widget widget) {
        dirtyWidgets.put(widget.id, new RuleConfiguration.WidgetConfig(widget));
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
        RuleConfiguration.AppDescribeConfig appDescribeSnapshot = pendingAppConfiguration;
        List<RuleConfiguration.CoordinateConfig> coordinates = new ArrayList<>(dirtyCoordinates.values());
        List<RuleConfiguration.WidgetConfig> widgets = new ArrayList<>(dirtyWidgets.values());
        pendingAppConfiguration = null;
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
                    dataDao.updateAppConfiguration(appDescribeSnapshot);
                }
                if (!coordinates.isEmpty()) {
                    dataDao.updateCoordinateConfigurations(coordinates);
                }
                if (!widgets.isEmpty()) {
                    dataDao.updateWidgetConfigurations(widgets);
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
