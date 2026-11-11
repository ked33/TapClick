package com.lgh.tapclick.myclass;

import android.graphics.Rect;

import com.lgh.tapclick.mybean.AppDescribe;
import com.lgh.tapclick.mybean.Coordinate;
import com.lgh.tapclick.mybean.Widget;

/** Immutable, field-limited updates cannot overwrite runtime statistics. */
public final class RuleConfiguration {
    private RuleConfiguration() { }

    public static final class AppDescribeConfig {
        public final Long id;
        public final int coordinateRetrieveTime;
        public final boolean coordinateRetrieveAllTime;
        public final int widgetRetrieveTime;
        public final boolean widgetRetrieveAllTime;
        public final boolean coordinateOnOff;
        public final boolean widgetOnOff;

        public AppDescribeConfig(AppDescribe source) {
            id = source.id;
            coordinateRetrieveTime = source.coordinateRetrieveTime;
            coordinateRetrieveAllTime = source.coordinateRetrieveAllTime;
            widgetRetrieveTime = source.widgetRetrieveTime;
            widgetRetrieveAllTime = source.widgetRetrieveAllTime;
            coordinateOnOff = source.coordinateOnOff;
            widgetOnOff = source.widgetOnOff;
        }
    }

    public static final class CoordinateConfig {
        public final Long id;
        public final String appActivity;
        public final int xPosition;
        public final int yPosition;
        public final int clickDelay;
        public final int clickInterval;
        public final int clickNumber;
        public final String visualSignature;
        public final int maxTriggerCount;
        public final int initialMatchWindowMillis;
        public final Long preconditionRuleId;
        public final int preconditionRuleType;
        public final int actionCooldownMillis;
        public final String comment;

        public CoordinateConfig(Coordinate source) {
            id = source.id;
            appActivity = source.appActivity;
            xPosition = source.xPosition;
            yPosition = source.yPosition;
            clickDelay = source.clickDelay;
            clickInterval = source.clickInterval;
            clickNumber = source.clickNumber;
            visualSignature = source.visualSignature;
            maxTriggerCount = source.maxTriggerCount;
            initialMatchWindowMillis = source.initialMatchWindowMillis;
            preconditionRuleId = source.preconditionRuleId;
            preconditionRuleType = source.preconditionRuleType;
            actionCooldownMillis = source.actionCooldownMillis;
            comment = source.comment;
        }
    }

    public static final class WidgetConfig {
        public final Long id;
        public final String appActivity;
        public final int clickDelay;
        public final int debounceDelay;
        public final boolean noRepeat;
        public final boolean clickOnly;
        public final boolean widgetClickable;
        public final Rect widgetRect;
        public final Long widgetNodeId;
        public final String widgetViewId;
        public final String widgetDescribe;
        public final String widgetText;
        public final String widgetParentViewId;
        public final String widgetParentText;
        public final String widgetChildText;
        public final String widgetSiblingText;
        public final String widgetExcludeText;
        public final int maxTriggerCount;
        public final int initialMatchWindowMillis;
        public final Long preconditionRuleId;
        public final int preconditionRuleType;
        public final int actionCooldownMillis;
        public final String comment;
        public final int clickInterval;
        public final int clickNumber;
        public final int action;
        public final int condition;

        public WidgetConfig(Widget source) {
            id = source.id;
            appActivity = source.appActivity;
            clickDelay = source.clickDelay;
            debounceDelay = source.debounceDelay;
            noRepeat = source.noRepeat;
            clickOnly = source.clickOnly;
            widgetClickable = source.widgetClickable;
            widgetRect = source.widgetRect == null ? null : new Rect(source.widgetRect);
            widgetNodeId = source.widgetNodeId;
            widgetViewId = source.widgetViewId;
            widgetDescribe = source.widgetDescribe;
            widgetText = source.widgetText;
            widgetParentViewId = source.widgetParentViewId;
            widgetParentText = source.widgetParentText;
            widgetChildText = source.widgetChildText;
            widgetSiblingText = source.widgetSiblingText;
            widgetExcludeText = source.widgetExcludeText;
            maxTriggerCount = source.maxTriggerCount;
            initialMatchWindowMillis = source.initialMatchWindowMillis;
            preconditionRuleId = source.preconditionRuleId;
            preconditionRuleType = source.preconditionRuleType;
            actionCooldownMillis = source.actionCooldownMillis;
            comment = source.comment;
            clickInterval = source.clickInterval;
            clickNumber = source.clickNumber;
            action = source.action;
            condition = source.condition;
        }
    }
}
