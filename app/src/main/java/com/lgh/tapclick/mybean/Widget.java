package com.lgh.tapclick.mybean;

import android.graphics.Rect;

import androidx.room.Entity;
import androidx.room.Ignore;
import androidx.room.Index;
import androidx.room.PrimaryKey;

import java.util.regex.Pattern;
import java.util.regex.PatternSyntaxException;

@Entity(indices = @Index(value = {"appPackage"}))
public class Widget {
    public static final int ACTION_CLICK = 0;
    public static final int ACTION_BACK = 1;
    public static final int CONDITION_OR = 0;
    public static final int CONDITION_AND = 1;

    @PrimaryKey(autoGenerate = true)
    public Long id;
    public String appPackage;
    public String appActivity;
    public long createTime;
    public int clickDelay;
    public int debounceDelay;
    public boolean noRepeat;
    public boolean clickOnly;
    public boolean widgetClickable;
    public Rect widgetRect;
    public Long widgetNodeId;
    public String widgetViewId;
    public String widgetDescribe;
    public String widgetText;
    public String comment;
    public String triggerReason;
    public long lastTriggerTime;
    public int triggerCount;
    public int clickInterval;
    public int clickNumber;
    public int action;
    public int condition;
    @Ignore
    private transient Pattern widgetDescribePattern;
    @Ignore
    private transient Pattern widgetTextPattern;

    public Widget() {
        this.appPackage = "";
        this.appActivity = "";
        this.clickNumber = 1;
        this.clickInterval = 0;
        this.clickDelay = 0;
        this.debounceDelay = 0;
        this.noRepeat = false;
        this.clickOnly = true;
        this.widgetClickable = false;
        this.widgetRect = null;
        this.widgetNodeId = null;
        this.widgetViewId = "";
        this.widgetDescribe = "";
        this.widgetText = "";
        this.comment = "";
        this.triggerReason = "";
        this.lastTriggerTime = 0;
        this.triggerCount = 0;
        this.action = ACTION_CLICK;
        this.condition = CONDITION_OR;
        this.createTime = System.currentTimeMillis();
    }

    public Widget(Widget widget) {
        this.appPackage = widget.appPackage;
        this.appActivity = widget.appActivity;
        this.createTime = widget.createTime;
        this.clickNumber = widget.clickNumber;
        this.clickInterval = widget.clickInterval;
        this.clickDelay = widget.clickDelay;
        this.debounceDelay = widget.debounceDelay;
        this.noRepeat = widget.noRepeat;
        this.clickOnly = widget.clickOnly;
        this.widgetClickable = widget.widgetClickable;
        this.widgetRect = widget.widgetRect;
        this.widgetNodeId = widget.widgetNodeId;
        this.widgetViewId = widget.widgetViewId;
        this.widgetDescribe = widget.widgetDescribe;
        this.widgetText = widget.widgetText;
        this.comment = widget.comment;
        this.lastTriggerTime = widget.lastTriggerTime;
        this.triggerCount = widget.triggerCount;
        this.action = widget.action;
        this.condition = widget.condition;
        this.triggerReason = widget.triggerReason;
        preparePatterns();
    }

    public void preparePatterns() {
        widgetDescribePattern = compilePattern(widgetDescribe);
        widgetTextPattern = compilePattern(widgetText);
    }

    public boolean matchesDescribe(String value) {
        return value != null && widgetDescribePattern != null && widgetDescribePattern.matcher(value).matches();
    }

    public boolean matchesText(String value) {
        return value != null && widgetTextPattern != null && widgetTextPattern.matcher(value).matches();
    }

    public void validatePatterns() {
        validatePattern(widgetDescribe, "控件描述");
        validatePattern(widgetText, "控件文本");
        preparePatterns();
    }

    private static Pattern compilePattern(String expression) {
        if (expression == null || expression.isEmpty()) {
            return null;
        }
        try {
            return Pattern.compile(expression);
        } catch (PatternSyntaxException ignored) {
            return null;
        }
    }

    private static void validatePattern(String expression, String fieldName) {
        if (expression == null || expression.isEmpty()) {
            return;
        }
        try {
            Pattern.compile(expression);
        } catch (PatternSyntaxException e) {
            throw new IllegalArgumentException(fieldName + "正则表达式无效：" + e.getDescription(), e);
        }
    }

    @Override
    public String toString() {
        return "Widget{" +
                "id=" + id +
                ", appPackage='" + appPackage + '\'' +
                ", appActivity='" + appActivity + '\'' +
                ", createTime=" + createTime +
                ", clickDelay=" + clickDelay +
                ", debounceDelay=" + debounceDelay +
                ", noRepeat=" + noRepeat +
                ", clickOnly=" + clickOnly +
                ", widgetClickable=" + widgetClickable +
                ", widgetRect=" + widgetRect +
                ", widgetNodeId=" + widgetNodeId +
                ", widgetViewId='" + widgetViewId + '\'' +
                ", widgetDescribe='" + widgetDescribe + '\'' +
                ", widgetText='" + widgetText + '\'' +
                ", comment='" + comment + '\'' +
                ", triggerReason='" + triggerReason + '\'' +
                ", lastTriggerTime=" + lastTriggerTime +
                ", triggerCount=" + triggerCount +
                ", clickInterval=" + clickInterval +
                ", clickNumber=" + clickNumber +
                ", action=" + action +
                ", condition=" + condition +
                '}';
    }
}
