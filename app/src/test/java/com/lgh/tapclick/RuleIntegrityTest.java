package com.lgh.tapclick;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertThrows;
import static org.junit.Assert.assertTrue;

import com.lgh.tapclick.mybean.AppDescribe;
import com.lgh.tapclick.mybean.Coordinate;
import com.lgh.tapclick.mybean.Regulation;
import com.lgh.tapclick.mybean.Widget;
import com.lgh.tapclick.myfunction.RuntimeLogFormatter;
import com.lgh.tapclick.myfunction.WidgetScanPolicy;

import org.junit.Test;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Map;

public class RuleIntegrityTest {
    @Test
    public void coordinatesOnSameActivityAreAllRetained() {
        Coordinate first = new Coordinate();
        first.appActivity = "example.Activity";
        first.xPosition = 10;
        Coordinate second = new Coordinate();
        second.appActivity = "example.Activity";
        second.xPosition = 20;

        Map<String, List<Coordinate>> grouped = AppDescribe.groupCoordinatesByActivity(Arrays.asList(first, second));

        assertEquals(2, grouped.get("example.Activity").size());
    }

    @Test
    public void textWidgetsOnSameActivityAreAllRetained() {
        Widget first = new Widget();
        first.appActivity = "example.Activity";
        first.widgetText = "跳过";
        Widget second = new Widget();
        second.appActivity = "example.Activity";
        second.widgetText = "关闭";

        Map<String, List<Widget>> grouped = AppDescribe.groupWidgetsByActivity(Arrays.asList(first, second));

        assertEquals(2, grouped.get("example.Activity").size());
    }

    @Test
    public void importCopyClearsIdsAndDoesNotDuplicateWidgets() {
        Regulation source = new Regulation();
        source.appDescribe = new AppDescribe();
        source.appDescribe.id = 100L;
        source.appDescribe.appPackage = "example.package";
        Widget widget = new Widget();
        widget.id = 200L;
        widget.appPackage = "example.package";
        widget.appActivity = "example.Activity";
        widget.widgetText = "跳过";
        widget.triggerCount = 8;
        source.widgetList.add(widget);

        Regulation imported = new Regulation(source, 1234L);

        assertNull(imported.appDescribe.id);
        assertEquals(1, imported.widgetList.size());
        assertNull(imported.widgetList.get(0).id);
        assertEquals(0, imported.widgetList.get(0).triggerCount);
        assertEquals(1234L, imported.widgetList.get(0).createTime);
    }

    @Test
    public void invalidRegexIsRejectedBeforePersistence() {
        Widget widget = new Widget();
        widget.widgetText = "[invalid";

        assertThrows(IllegalArgumentException.class, widget::validatePatterns);
    }

    @Test
    public void runtimeSnapshotKeepsOnlyEnabledAppsAndRuleTypes() {
        AppDescribe coordinateApp = new AppDescribe();
        coordinateApp.appPackage = "enabled.coordinate";
        coordinateApp.coordinateOnOff = true;

        AppDescribe disabledApp = new AppDescribe();
        disabledApp.appPackage = "disabled.app";

        Coordinate enabledCoordinate = new Coordinate();
        enabledCoordinate.appPackage = coordinateApp.appPackage;
        enabledCoordinate.appActivity = "example.Activity";
        Coordinate disabledCoordinate = new Coordinate();
        disabledCoordinate.appPackage = disabledApp.appPackage;
        disabledCoordinate.appActivity = "example.Activity";
        Widget disabledTypeWidget = new Widget();
        disabledTypeWidget.appPackage = coordinateApp.appPackage;
        disabledTypeWidget.appActivity = "example.Activity";
        disabledTypeWidget.widgetText = "跳过";

        Map<String, AppDescribe> snapshot = AppDescribe.buildRuntimeSnapshot(
                Arrays.asList(coordinateApp, disabledApp),
                Arrays.asList(enabledCoordinate, disabledCoordinate),
                Arrays.asList(disabledTypeWidget));

        assertEquals(1, snapshot.size());
        assertNotNull(snapshot.get(coordinateApp.appPackage));
        assertEquals(1, snapshot.get(coordinateApp.appPackage).coordinateList.size());
        assertTrue(snapshot.get(coordinateApp.appPackage).widgetList.isEmpty());
        assertFalse(snapshot.containsKey(disabledApp.appPackage));
    }

    @Test
    public void runtimeSnapshotPreparesWidgetPatternsAndRetainsSamePageRules() {
        AppDescribe appDescribe = new AppDescribe();
        appDescribe.appPackage = "enabled.widget";
        appDescribe.widgetOnOff = true;
        Widget first = new Widget();
        first.appPackage = appDescribe.appPackage;
        first.appActivity = "example.Activity";
        first.widgetText = "跳.*";
        Widget second = new Widget();
        second.appPackage = appDescribe.appPackage;
        second.appActivity = "example.Activity";
        second.widgetText = "关闭";

        Map<String, AppDescribe> snapshot = AppDescribe.buildRuntimeSnapshot(
                Arrays.asList(appDescribe), Collections.emptyList(), Arrays.asList(first, second));

        List<Widget> widgets = snapshot.get(appDescribe.appPackage)
                .widgetSetMap.get("example.Activity");
        assertEquals(2, widgets.size());
        assertTrue(widgets.get(0).matchesText("跳过广告"));
    }

    @Test
    public void scanPolicySkipsCompletedAndDebouncingRulesButKeepsRepeatableRules() {
        Widget noRepeat = new Widget();
        noRepeat.noRepeat = true;
        Widget repeatable = new Widget();
        repeatable.noRepeat = false;
        Widget back = new Widget();
        back.action = Widget.ACTION_BACK;

        assertFalse(WidgetScanPolicy.shouldEvaluate(noRepeat, true, false));
        assertTrue(WidgetScanPolicy.shouldEvaluate(repeatable, true, false));
        assertFalse(WidgetScanPolicy.shouldEvaluate(back, true, false));
        assertFalse(WidgetScanPolicy.shouldEvaluate(repeatable, false, true));
    }

    @Test
    public void releaseRuntimeLogRemainsACompactSummary() {
        String summary = RuntimeLogFormatter.formatRuleSummary(
                "widget", 12L, "example.app", "example.Activity", "Text 匹配", "点击已执行");
        String releaseLog = RuntimeLogFormatter.appendDebugDetails(
                summary, false, "{\"widgetText\":\"跳过\"}");

        assertEquals(summary, releaseLog);
        assertTrue(releaseLog.contains("id=12"));
        assertTrue(releaseLog.contains("app=example.app"));
        assertTrue(releaseLog.contains("page=example.Activity"));
        assertTrue(releaseLog.contains("reason=Text 匹配"));
        assertTrue(releaseLog.contains("result=点击已执行"));
        assertFalse(releaseLog.contains("widgetText"));
    }
}
