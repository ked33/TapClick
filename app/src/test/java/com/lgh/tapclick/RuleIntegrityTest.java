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
import com.lgh.tapclick.myclass.AccessibilityLayoutSnapshot;
import com.lgh.tapclick.myclass.RuntimeAppDescribeMap;
import com.lgh.tapclick.myclass.VisualCoordinateSignature;
import com.lgh.tapclick.myfunction.RuntimeLogFormatter;
import com.lgh.tapclick.myfunction.WidgetScanPolicy;

import org.junit.Test;

import java.util.ArrayList;
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
        Coordinate coordinate = new Coordinate();
        coordinate.id = 300L;
        coordinate.appPackage = "example.package";
        coordinate.appActivity = "example.Activity";
        coordinate.triggerCount = 5;
        coordinate.visualSignature = VisualCoordinateSignature.create(
                createVisualPattern(64, 64), 64, 64);
        source.coordinateList.add(coordinate);

        Regulation imported = new Regulation(source, 1234L);

        assertNull(imported.appDescribe.id);
        assertEquals(1, imported.widgetList.size());
        assertNull(imported.widgetList.get(0).id);
        assertEquals(0, imported.widgetList.get(0).triggerCount);
        assertEquals(1234L, imported.widgetList.get(0).createTime);
        assertEquals(1, imported.coordinateList.size());
        assertNull(imported.coordinateList.get(0).id);
        assertEquals(0, imported.coordinateList.get(0).triggerCount);
        assertEquals(1234L, imported.coordinateList.get(0).createTime);
        assertEquals(coordinate.visualSignature,
                imported.coordinateList.get(0).visualSignature);
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
    public void accessibilityLayoutSnapshotIsImmutableAndLargestNodesComeFirst() {
        List<AccessibilityLayoutSnapshot.Node> source = new ArrayList<>();
        source.add(new AccessibilityLayoutSnapshot.Node(
                false, 1L, "small", "", "", 0, 0, 10, 10));
        source.add(new AccessibilityLayoutSnapshot.Node(
                true, 2L, "large", "跳过", "跳过广告", 0, 0, 30, 20));
        source.add(new AccessibilityLayoutSnapshot.Node(
                true, 2L, "large", "跳过", "跳过广告", 0, 0, 30, 20));

        AccessibilityLayoutSnapshot snapshot = new AccessibilityLayoutSnapshot(
                "example.app", "example.AdActivity", source);
        source.clear();

        assertEquals("example.app", snapshot.getAppPackage());
        assertEquals("example.AdActivity", snapshot.getAppActivity());
        assertEquals(2, snapshot.getNodes().size());
        assertEquals(600L, snapshot.getNodes().get(0).getArea());
        assertEquals("large", snapshot.getNodes().get(0).getViewId());
        assertThrows(UnsupportedOperationException.class,
                () -> snapshot.getNodes().add(new AccessibilityLayoutSnapshot.Node(
                        false, 3L, "", "", "", 0, 0, 1, 1)));
    }

    @Test
    public void singleEmptyRootIsNotTreatedAsSelectableLayout() {
        AccessibilityLayoutSnapshot placeholder = new AccessibilityLayoutSnapshot(
                "example.app", "example.Activity", Collections.singletonList(
                new AccessibilityLayoutSnapshot.Node(
                        false, 1L, "android:id/content", "", "", 0, 0, 1080, 2400)));
        AccessibilityLayoutSnapshot button = new AccessibilityLayoutSnapshot(
                "example.app", "example.Activity", Collections.singletonList(
                new AccessibilityLayoutSnapshot.Node(
                        true, 2L, "skip", "跳过", "跳过广告", 900, 20, 1060, 100)));

        assertFalse(placeholder.hasSelectableContent());
        assertTrue(button.hasSelectableContent());
    }

    @Test
    public void interactiveAndDiagnosticNodeCountsAreSeparatedFromCoordinateContent() {
        AccessibilityLayoutSnapshot textOnly = new AccessibilityLayoutSnapshot(
                "example.app", "example.Activity", Collections.singletonList(
                new AccessibilityLayoutSnapshot.Node(
                        false, 3L, "ad_text", "", "广告", 0, 0, 120, 40,
                        true, true, false, 0, 0, 7,
                        "android.widget.TextView", "example.app")));
        AccessibilityLayoutSnapshot clickTarget = new AccessibilityLayoutSnapshot(
                "example.app", "example.Activity", Collections.singletonList(
                new AccessibilityLayoutSnapshot.Node(
                        false, 4L, "skip", "跳过", "", 900, 20, 1060, 100,
                        true, true, false, 0x10, 0, 7,
                        "android.widget.Button", "example.app")));

        assertTrue(textOnly.hasSelectableContent());
        assertFalse(textOnly.hasInteractiveContent());
        assertEquals(0, textOnly.getInteractiveNodeCount());
        assertEquals(1, textOnly.getIdentifiedNodeCount());
        assertEquals(1, textOnly.getVisibleNodeCount());
        assertFalse(textOnly.getNodes().get(0).toDebugSummary(0).contains("广告"));

        assertTrue(clickTarget.hasInteractiveContent());
        assertEquals(1, clickTarget.getInteractiveNodeCount());
        assertTrue(clickTarget.getNodes().get(0).hasClickAction());
        assertTrue(clickTarget.getNodes().get(0).toDebugSummary(0).contains("actions=0x10"));
    }

    @Test
    public void multipleUnidentifiedNodesRemainAvailableForCoordinateSelection() {
        List<AccessibilityLayoutSnapshot.Node> nodes = Arrays.asList(
                new AccessibilityLayoutSnapshot.Node(
                        false, 5L, "", "", "", 0, 0, 100, 100),
                new AccessibilityLayoutSnapshot.Node(
                        false, 6L, "", "", "", 100, 100, 200, 200));
        AccessibilityLayoutSnapshot snapshot = new AccessibilityLayoutSnapshot(
                "example.app", "example.Activity", nodes);

        assertTrue(snapshot.hasSelectableContent());
        assertFalse(snapshot.hasInteractiveContent());
    }

    @Test
    public void fullScreenStructuralContainersDoNotBlockFrozenCoordinateSelection() {
        AccessibilityLayoutSnapshot.Node structuralRoot =
                new AccessibilityLayoutSnapshot.Node(
                        false, 7L, "android:id/content", "", "",
                        0, 0, 1080, 2400,
                        true, true, false, 0x4c, 2, 3,
                        "android.widget.FrameLayout", "example.app");
        AccessibilityLayoutSnapshot.Node fullScreenInteractive =
                new AccessibilityLayoutSnapshot.Node(
                        true, 8L, "interactive", "", "",
                        0, 0, 1080, 2400,
                        true, true, false, 0x10, 0, 3,
                        "android.view.View", "example.app");
        AccessibilityLayoutSnapshot.Node smallCustomTarget =
                new AccessibilityLayoutSnapshot.Node(
                        false, 9L, "", "", "",
                        940, 20, 1060, 140,
                        true, true, false, 0, 0, 3,
                        "android.view.View", "example.app");

        assertFalse(structuralRoot.isUsefulWidgetTarget(1080, 2400));
        assertTrue(fullScreenInteractive.isUsefulWidgetTarget(1080, 2400));
        assertTrue(smallCustomTarget.isUsefulWidgetTarget(1080, 2400));
    }

    @Test
    public void duplicateNodeKeepsTheRicherAccessibilityMetadata() {
        AccessibilityLayoutSnapshot snapshot = new AccessibilityLayoutSnapshot(
                "example.app", "example.Activity", Arrays.asList(
                new AccessibilityLayoutSnapshot.Node(
                        false, 9L, "skip", "", "", 900, 20, 1060, 100,
                        false, true, false, 0, 0, 3,
                        "android.view.View", "example.app"),
                new AccessibilityLayoutSnapshot.Node(
                        false, 9L, "skip", "跳过", "跳过广告", 900, 20, 1060, 100,
                        true, true, false, 0x10, 0, 3,
                        "android.widget.Button", "example.app")));

        assertEquals(1, snapshot.getNodes().size());
        assertTrue(snapshot.hasInteractiveContent());
        assertEquals("跳过广告", snapshot.getNodes().get(0).getText());
        assertTrue(snapshot.getNodes().get(0).hasClickAction());
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

    @Test
    public void runtimeAppDescribeSnapshotCanAddNewAppWithoutMutatingPublishedMap() {
        Map<String, AppDescribe> published = RuntimeAppDescribeMap.immutableSnapshot(null);
        AppDescribe describe = new AppDescribe();
        describe.appPackage = "new.example.app";

        Map<String, AppDescribe> updated = RuntimeAppDescribeMap.withEntry(
                published, describe.appPackage, describe);

        assertTrue(published.isEmpty());
        assertEquals(describe, updated.get(describe.appPackage));
        assertThrows(UnsupportedOperationException.class,
                () -> updated.put("another.app", new AppDescribe()));
    }

    @Test
    public void captureRuntimeLogContainsTimingAndBoundedSummaryFields() {
        String summary = RuntimeLogFormatter.formatCaptureSummary(
                "complete", "example.app", true, 187,
                "source=activeRoot windows=0 visited=42 nodes=18");

        assertTrue(summary.contains("布局捕获"));
        assertTrue(summary.contains("phase=complete"));
        assertTrue(summary.contains("app=example.app"));
        assertTrue(summary.contains("mode=quick"));
        assertTrue(summary.contains("elapsedMs=187"));
        assertTrue(summary.contains("visited=42"));
        assertFalse(summary.contains("AccessibilityNodeInfo"));
    }

    @Test
    public void visualCoordinateSignatureMatchesStableAndModeratelyChangedRegions() {
        int width = 64;
        int height = 64;
        int[] original = createVisualPattern(width, height);
        String signature = VisualCoordinateSignature.create(original, width, height);

        assertNotNull(signature);
        assertTrue(VisualCoordinateSignature.isValid(signature));
        assertEquals(100, VisualCoordinateSignature.matchScore(
                signature, original, width, height));

        int brightnessScore = VisualCoordinateSignature.matchScore(
                signature, adjustBrightness(original, 24), width, height);
        int noiseScore = VisualCoordinateSignature.matchScore(
                signature, addDeterministicNoise(original), width, height);

        assertTrue(brightnessScore >= VisualCoordinateSignature.DEFAULT_MATCH_THRESHOLD);
        assertTrue(noiseScore >= VisualCoordinateSignature.DEFAULT_MATCH_THRESHOLD);
    }

    @Test
    public void visualCoordinateSignatureRejectsUnrelatedOrMalformedRegions() {
        int width = 64;
        int height = 64;
        int[] original = createVisualPattern(width, height);
        String signature = VisualCoordinateSignature.create(original, width, height);
        int[] unrelated = createCheckerboard(width, height);
        int[] flat = new int[width * height];
        Arrays.fill(flat, 0xff808080);

        assertNotNull(signature);
        assertTrue(VisualCoordinateSignature.matchScore(
                signature, unrelated, width, height)
                < VisualCoordinateSignature.DEFAULT_MATCH_THRESHOLD);
        assertNull(VisualCoordinateSignature.create(flat, width, height));
        assertFalse(VisualCoordinateSignature.isValid("v1:00"));
        assertFalse(VisualCoordinateSignature.isValid(signature + "00"));
        assertEquals(-1, VisualCoordinateSignature.matchScore(
                "unsupported:" + signature, original, width, height));
    }

    @Test
    public void visualCoordinateRegionStaysInsideScreenAtEdges() {
        VisualCoordinateSignature.Region topLeft =
                VisualCoordinateSignature.calculateRegion(1080, 2400, 0, 0);
        VisualCoordinateSignature.Region bottomRight =
                VisualCoordinateSignature.calculateRegion(1080, 2400, 1079, 2399);

        assertNotNull(topLeft);
        assertNotNull(bottomRight);
        assertEquals(0, topLeft.getLeft());
        assertEquals(0, topLeft.getTop());
        assertEquals(1080, bottomRight.getLeft() + bottomRight.getWidth());
        assertEquals(2400, bottomRight.getTop() + bottomRight.getHeight());
        assertTrue(topLeft.getWidth() >= 48);
        assertTrue(topLeft.getWidth() <= 112);
    }

    @Test
    public void coordinateCopyPreservesVisualVerificationWithoutExposingSignature() {
        Coordinate source = new Coordinate();
        source.visualSignature = VisualCoordinateSignature.create(
                createVisualPattern(64, 64), 64, 64);

        Coordinate copy = new Coordinate(source);

        assertEquals(source.visualSignature, copy.visualSignature);
        assertTrue(copy.toString().contains("visualVerification=true"));
        assertFalse(copy.toString().contains(source.visualSignature));
    }

    @Test
    public void regulationImportRejectsMalformedVisualCoordinateSignature() {
        Regulation source = new Regulation();
        source.appDescribe = new AppDescribe();
        source.appDescribe.appPackage = "example.package";
        Coordinate coordinate = new Coordinate();
        coordinate.appPackage = source.appDescribe.appPackage;
        coordinate.appActivity = "example.Activity";
        coordinate.visualSignature = "v1:00";
        source.coordinateList.add(coordinate);

        assertThrows(IllegalArgumentException.class,
                () -> new Regulation(source, 1234L));
    }

    private static int[] createVisualPattern(int width, int height) {
        int[] pixels = new int[width * height];
        for (int y = 0; y < height; y++) {
            for (int x = 0; x < width; x++) {
                int value = 24 + (x * 2 + y * 3 + ((x / 8 + y / 8) % 2) * 48) % 176;
                pixels[y * width + x] = grayscale(value);
            }
        }
        return pixels;
    }

    private static int[] createCheckerboard(int width, int height) {
        int[] pixels = new int[width * height];
        for (int y = 0; y < height; y++) {
            for (int x = 0; x < width; x++) {
                int value = ((x / 4 + y / 4) & 1) == 0 ? 18 : 238;
                pixels[y * width + x] = grayscale(value);
            }
        }
        return pixels;
    }

    private static int[] adjustBrightness(int[] source, int adjustment) {
        int[] result = source.clone();
        for (int i = 0; i < result.length; i++) {
            int value = Math.max(0, Math.min(255, (result[i] & 0xff) + adjustment));
            result[i] = grayscale(value);
        }
        return result;
    }

    private static int[] addDeterministicNoise(int[] source) {
        int[] result = source.clone();
        for (int i = 0; i < result.length; i++) {
            int noise = (i * 17 % 13) - 6;
            int value = Math.max(0, Math.min(255, (result[i] & 0xff) + noise));
            result[i] = grayscale(value);
        }
        return result;
    }

    private static int grayscale(int value) {
        return 0xff000000 | (value << 16) | (value << 8) | value;
    }
}
