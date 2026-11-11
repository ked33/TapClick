package com.lgh.tapclick;

import static org.junit.Assert.*;

import com.lgh.tapclick.mybean.Coordinate;
import com.lgh.tapclick.mybean.Widget;
import com.lgh.tapclick.myclass.RuleConfiguration;
import com.lgh.tapclick.myclass.RuleReference;
import com.lgh.tapclick.myfunction.WidgetScanPolicy;

import org.junit.Test;
import java.util.Arrays;
import java.util.Collections;

public class RuleReferenceTest {
    @Test
    public void scanSleepsForCooldownAndStopsForExpiredOrExhaustedRules() {
        Widget rule = widget(1);
        rule.actionCooldownMillis = 500;
        rule.lastTriggerTime = 1000;
        assertEquals(300, WidgetScanPolicy.nextEvaluationDelay(rule, false, false, false, true, 1200, 100));
        assertEquals(0, WidgetScanPolicy.nextEvaluationDelay(rule, false, false, false, true, 1500, 400));
        rule.initialMatchWindowMillis = 200;
        assertEquals(-1, WidgetScanPolicy.nextEvaluationDelay(rule, false, false, false, true, 1200, 100));
        rule.initialMatchWindowMillis = 0;
        rule.maxTriggerCount = 1;
        rule.triggerCount = 1;
        assertEquals(-1, WidgetScanPolicy.nextEvaluationDelay(rule, false, false, false, true, 2000, 100));
    }

    @Test
    public void failedOrInFlightRulesDoNotKeepScanning() {
        Widget rule = widget(1);
        assertEquals(-1, WidgetScanPolicy.nextEvaluationDelay(rule, false, false, true, true, 1000, 100));
        assertEquals(-1, WidgetScanPolicy.nextEvaluationDelay(rule, false, true, false, true, 1000, 100));
        assertEquals(-1, WidgetScanPolicy.nextEvaluationDelay(rule, false, false, false, false, 1000, 100));
        assertEquals(0, WidgetScanPolicy.nextEvaluationDelay(rule, false, false, false, true, 1000, 100));
    }

    @Test
    public void sameNumericIdInDifferentTablesCannotSatisfyTheOtherRule() {
        Coordinate coordinate = coordinate(1);
        Widget widget = widget(1);
        assertNotEquals(new RuleReference(1, 1), new RuleReference(2, 1));
        assertNull(RuleReference.resolve(0, 1L, "page",
                Collections.singletonList(coordinate), Collections.singletonList(widget)));
        assertEquals(new RuleReference(2, 1), RuleReference.resolve(2, 1L, "page",
                Collections.singletonList(coordinate), Collections.singletonList(widget)));
    }

    @Test
    public void unambiguousLegacyIdResolvesButMissingAndDifferentPageDoNot() {
        Coordinate coordinate = coordinate(4);
        assertEquals(new RuleReference(1, 4), RuleReference.resolve(0, 4L, "page",
                Collections.singletonList(coordinate), Collections.emptyList()));
        assertNull(RuleReference.resolve(1, 4L, "other",
                Collections.singletonList(coordinate), Collections.emptyList()));
        assertNull(RuleReference.resolve(1, 9L, "page",
                Collections.singletonList(coordinate), Collections.emptyList()));
    }

    @Test
    public void rejectsSelfReferencesAndCrossTypeCycles() {
        Coordinate coordinate = coordinate(1);
        Widget widget = widget(2);
        widget.preconditionRuleType = 1;
        widget.preconditionRuleId = 1L;
        RuleReference owner = new RuleReference(1, 1);
        assertNotNull(RuleReference.validate(owner, owner, "page",
                Collections.singletonList(coordinate), Collections.singletonList(widget)));
        assertNotNull(RuleReference.validate(owner, new RuleReference(2, 2), "page",
                Collections.singletonList(coordinate), Collections.singletonList(widget)));
        assertNull(RuleReference.validate(owner, null, "page",
                Collections.singletonList(coordinate), Collections.singletonList(widget)));
    }

    @Test
    public void acceptsAcyclicChain() {
        Coordinate first = coordinate(1);
        Coordinate second = coordinate(2);
        Widget widget = widget(3);
        second.preconditionRuleType = 2;
        second.preconditionRuleId = 3L;
        assertNull(RuleReference.validate(new RuleReference(1, 1), new RuleReference(1, 2), "page",
                Arrays.asList(first, second), Collections.singletonList(widget)));
    }

    @Test
    public void queuedConfigurationDoesNotChangeWhenTheEditorKeepsTyping() throws Exception {
        Coordinate coordinate = coordinate(1);
        coordinate.comment = "first";
        coordinate.xPosition = 12;
        RuleConfiguration.CoordinateConfig snapshot = new RuleConfiguration.CoordinateConfig(coordinate);
        coordinate.comment = "second";
        coordinate.xPosition = 24;
        assertEquals("first", snapshot.comment);
        assertEquals(12, snapshot.xPosition);
        assertThrows(NoSuchFieldException.class,
                () -> RuleConfiguration.CoordinateConfig.class.getField("triggerCount"));
        assertThrows(NoSuchFieldException.class,
                () -> RuleConfiguration.WidgetConfig.class.getField("lastTriggerTime"));
    }

    private static Coordinate coordinate(long id) {
        Coordinate rule = new Coordinate(); rule.id = id; rule.appActivity = "page"; return rule;
    }
    private static Widget widget(long id) {
        Widget rule = new Widget(); rule.id = id; rule.appActivity = "page"; return rule;
    }
}
