package com.lgh.tapclick;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertThrows;

import com.lgh.tapclick.mybean.AppDescribe;
import com.lgh.tapclick.mybean.Coordinate;
import com.lgh.tapclick.mybean.Regulation;
import com.lgh.tapclick.mybean.Widget;

import org.junit.Test;

import java.util.Arrays;
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
}
