package com.lgh.tapclick.mybean;

import android.graphics.Rect;

import androidx.room.Entity;
import androidx.room.ColumnInfo;
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
    /** Optional immediate-parent view id constraint. */
    public String widgetParentViewId;
    /** Optional immediate-parent text/content-description regex. */
    public String widgetParentText;
    /** Optional direct-child text/content-description regex. */
    public String widgetChildText;
    /** Optional sibling text/content-description regex. */
    public String widgetSiblingText;
    /** Optional exclusion text/content-description regex for the local neighbourhood. */
    public String widgetExcludeText;
    /** 0 means unlimited successful triggers. */
    @ColumnInfo(defaultValue = "0")
    public int maxTriggerCount;
    /** 0 means no Activity-start window limit. */
    @ColumnInfo(defaultValue = "0")
    public int initialMatchWindowMillis;
    /** Optional rule id that must have triggered earlier on this Activity. */
    public Long preconditionRuleId;
    /** 0 means no successful-action cooldown. */
    @ColumnInfo(defaultValue = "0")
    public int actionCooldownMillis;
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
    @Ignore
    private transient Pattern widgetParentTextPattern;
    @Ignore
    private transient Pattern widgetChildTextPattern;
    @Ignore
    private transient Pattern widgetSiblingTextPattern;
    @Ignore
    private transient Pattern widgetExcludeTextPattern;

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
        this.widgetParentViewId = "";
        this.widgetParentText = "";
        this.widgetChildText = "";
        this.widgetSiblingText = "";
        this.widgetExcludeText = "";
        this.maxTriggerCount = 0;
        this.initialMatchWindowMillis = 0;
        this.preconditionRuleId = null;
        this.actionCooldownMillis = 0;
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
        this.widgetParentViewId = widget.widgetParentViewId;
        this.widgetParentText = widget.widgetParentText;
        this.widgetChildText = widget.widgetChildText;
        this.widgetSiblingText = widget.widgetSiblingText;
        this.widgetExcludeText = widget.widgetExcludeText;
        this.maxTriggerCount = widget.maxTriggerCount;
        this.initialMatchWindowMillis = widget.initialMatchWindowMillis;
        this.preconditionRuleId = widget.preconditionRuleId;
        this.actionCooldownMillis = widget.actionCooldownMillis;
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
        widgetParentTextPattern = compilePattern(widgetParentText);
        widgetChildTextPattern = compilePattern(widgetChildText);
        widgetSiblingTextPattern = compilePattern(widgetSiblingText);
        widgetExcludeTextPattern = compilePattern(widgetExcludeText);
    }

    public boolean matchesDescribe(String value) {
        return value != null && widgetDescribePattern != null && widgetDescribePattern.matcher(value).matches();
    }

    public boolean matchesText(String value) {
        return value != null && widgetTextPattern != null && widgetTextPattern.matcher(value).matches();
    }

    public boolean matchesParentText(String value) {
        return value != null && widgetParentTextPattern != null
                && widgetParentTextPattern.matcher(value).matches();
    }

    public boolean matchesChildText(String value) {
        return value != null && widgetChildTextPattern != null
                && widgetChildTextPattern.matcher(value).matches();
    }

    public boolean matchesSiblingText(String value) {
        return value != null && widgetSiblingTextPattern != null
                && widgetSiblingTextPattern.matcher(value).matches();
    }

    public boolean matchesExcludeText(String value) {
        return value != null && widgetExcludeTextPattern != null
                && widgetExcludeTextPattern.matcher(value).matches();
    }

    public boolean hasParentConstraint() {
        return !isBlank(widgetParentViewId) || !isBlank(widgetParentText);
    }

    public boolean hasChildConstraint() {
        return !isBlank(widgetChildText);
    }

    public boolean hasSiblingConstraint() {
        return !isBlank(widgetSiblingText);
    }

    public boolean hasExcludeConstraint() {
        return !isBlank(widgetExcludeText);
    }

    public boolean hasStructuralConstraint() {
        return hasParentConstraint() || hasChildConstraint()
                || hasSiblingConstraint() || hasExcludeConstraint();
    }

    public boolean hasReachedMaxTriggerCount() {
        return maxTriggerCount > 0 && triggerCount >= maxTriggerCount;
    }

    public void validatePatterns() {
        validatePattern(widgetDescribe, "控件描述");
        validatePattern(widgetText, "控件文本");
        validatePattern(widgetParentText, "父节点文本");
        validatePattern(widgetChildText, "子节点文本");
        validatePattern(widgetSiblingText, "兄弟节点文本");
        validatePattern(widgetExcludeText, "排除文本");
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
                ", widgetParentViewId='" + widgetParentViewId + '\'' +
                ", widgetParentText='" + widgetParentText + '\'' +
                ", widgetChildText='" + widgetChildText + '\'' +
                ", widgetSiblingText='" + widgetSiblingText + '\'' +
                ", widgetExcludeText='" + widgetExcludeText + '\'' +
                ", maxTriggerCount=" + maxTriggerCount +
                ", initialMatchWindowMillis=" + initialMatchWindowMillis +
                ", preconditionRuleId=" + preconditionRuleId +
                ", actionCooldownMillis=" + actionCooldownMillis +
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

    private static boolean isBlank(String value) {
        return value == null || value.trim().isEmpty();
    }
}
