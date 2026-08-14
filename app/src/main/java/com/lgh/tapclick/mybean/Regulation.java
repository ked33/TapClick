package com.lgh.tapclick.mybean;

import com.lgh.tapclick.myclass.VisualCoordinateSignature;

import java.util.ArrayList;
import java.util.List;

public class Regulation {
    public AppDescribe appDescribe;
    public List<Coordinate> coordinateList;
    public List<Widget> widgetList;

    public Regulation() {
        coordinateList = new ArrayList<>();
        widgetList = new ArrayList<>();
    }

    public Regulation(Regulation source, long importTime) {
        this();
        if (source == null || source.appDescribe == null) {
            throw new IllegalArgumentException("导入规则缺少应用信息");
        }
        appDescribe = new AppDescribe(source.appDescribe);
        appDescribe.id = null;
        if (source.coordinateList == null || source.widgetList == null) {
            throw new IllegalArgumentException("导入规则列表不完整：" + appDescribe.appPackage);
        }
        for (Coordinate sourceCoordinate : source.coordinateList) {
            if (sourceCoordinate == null) {
                throw new IllegalArgumentException("导入数据包含空坐标规则：" + appDescribe.appPackage);
            }
            Coordinate coordinate = new Coordinate(sourceCoordinate);
            if (coordinate.visualSignature != null
                    && !coordinate.visualSignature.isEmpty()
                    && !VisualCoordinateSignature.isValid(coordinate.visualSignature)) {
                throw new IllegalArgumentException(
                        "导入数据包含无效坐标视觉校验：" + appDescribe.appPackage);
            }
            coordinate.id = null;
            coordinate.createTime = importTime;
            coordinate.lastTriggerTime = 0;
            coordinate.triggerCount = 0;
            coordinateList.add(coordinate);
        }
        for (Widget sourceWidget : source.widgetList) {
            if (sourceWidget == null) {
                throw new IllegalArgumentException("导入数据包含空控件规则：" + appDescribe.appPackage);
            }
            Widget widget = new Widget(sourceWidget);
            widget.id = null;
            widget.createTime = importTime;
            widget.lastTriggerTime = 0;
            widget.triggerCount = 0;
            widget.triggerReason = "";
            widget.validatePatterns();
            widgetList.add(widget);
        }
    }
}
