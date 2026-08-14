package com.lgh.tapclick.mybean;

import androidx.room.Entity;
import androidx.room.Ignore;
import androidx.room.Index;
import androidx.room.PrimaryKey;

import com.lgh.tapclick.myclass.DataDao;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Entity(indices = @Index(value = {"appPackage"}, unique = true))
public class AppDescribe {
    @PrimaryKey(autoGenerate = true)
    public Long id;
    public String appName;
    public String appPackage;
    public int coordinateRetrieveTime;
    public boolean coordinateRetrieveAllTime;
    public int widgetRetrieveTime;
    public boolean widgetRetrieveAllTime;
    public boolean coordinateOnOff;
    public boolean widgetOnOff;
    @Ignore
    public transient Map<String, List<Coordinate>> coordinateSetMap;
    @Ignore
    public transient Map<String, List<Widget>> widgetSetMap;
    @Ignore
    public transient List<Coordinate> coordinateList;
    @Ignore
    public transient List<Widget> widgetList;

    public AppDescribe() {
        this.appName = "";
        this.appPackage = "";
        this.coordinateRetrieveTime = 20000;
        this.coordinateRetrieveAllTime = true;
        this.widgetRetrieveTime = 20000;
        this.widgetRetrieveAllTime = true;
        this.coordinateOnOff = false;
        this.widgetOnOff = false;
        this.coordinateSetMap = new HashMap<>();
        this.widgetSetMap = new HashMap<>();
        this.coordinateList = new ArrayList<>();
        this.widgetList = new ArrayList<>();
    }

    public AppDescribe(AppDescribe appDescribe) {
        this();
        this.appName = appDescribe.appName;
        this.appPackage = appDescribe.appPackage;
        this.coordinateRetrieveTime = appDescribe.coordinateRetrieveTime;
        this.coordinateRetrieveAllTime = appDescribe.coordinateRetrieveAllTime;
        this.widgetRetrieveTime = appDescribe.widgetRetrieveTime;
        this.widgetRetrieveAllTime = appDescribe.widgetRetrieveAllTime;
        this.coordinateOnOff = appDescribe.coordinateOnOff;
        this.widgetOnOff = appDescribe.widgetOnOff;
    }

    public void copy(AppDescribe appDescribe) {
        this.appName = appDescribe.appName;
        this.appPackage = appDescribe.appPackage;
        this.coordinateRetrieveTime = appDescribe.coordinateRetrieveTime;
        this.coordinateRetrieveAllTime = appDescribe.coordinateRetrieveAllTime;
        this.widgetRetrieveTime = appDescribe.widgetRetrieveTime;
        this.widgetRetrieveAllTime = appDescribe.widgetRetrieveAllTime;
        this.coordinateOnOff = appDescribe.coordinateOnOff;
        this.widgetOnOff = appDescribe.widgetOnOff;
        this.coordinateSetMap = appDescribe.coordinateSetMap;
        this.widgetSetMap = appDescribe.widgetSetMap;
        this.coordinateList = appDescribe.coordinateList;
        this.widgetList = appDescribe.widgetList;
    }

    public void getOtherFieldsFromDatabase(DataDao dataDao) {
        getCoordinateFromDatabase(dataDao);
        getWidgetFromDatabase(dataDao);
    }

    public void getCoordinateFromDatabase(DataDao dataDao) {
        coordinateList = new ArrayList<>(dataDao.getCoordinatesByPackage(this.appPackage));
        coordinateSetMap = groupCoordinatesByActivity(coordinateList);
    }

    public void getWidgetFromDatabase(DataDao dataDao) {
        widgetList = new ArrayList<>(dataDao.getWidgetsByPackage(this.appPackage));
        for (Widget widget : widgetList) {
            widget.preparePatterns();
        }
        widgetSetMap = groupWidgetsByActivity(widgetList);
    }

    public static Map<String, List<Coordinate>> groupCoordinatesByActivity(List<Coordinate> coordinates) {
        Map<String, List<Coordinate>> result = new HashMap<>();
        for (Coordinate coordinate : coordinates) {
            result.computeIfAbsent(coordinate.appActivity, key -> new ArrayList<>()).add(coordinate);
        }
        return result;
    }

    public static Map<String, List<Widget>> groupWidgetsByActivity(List<Widget> widgets) {
        Map<String, List<Widget>> result = new HashMap<>();
        for (Widget widget : widgets) {
            result.computeIfAbsent(widget.appActivity, key -> new ArrayList<>()).add(widget);
        }
        return result;
    }

    @Override
    public String toString() {
        return "AppDescribe{" +
                "id=" + id +
                ", appName='" + appName + '\'' +
                ", appPackage='" + appPackage + '\'' +
                ", coordinateRetrieveTime=" + coordinateRetrieveTime +
                ", coordinateRetrieveAllTime=" + coordinateRetrieveAllTime +
                ", widgetRetrieveTime=" + widgetRetrieveTime +
                ", widgetRetrieveAllTime=" + widgetRetrieveAllTime +
                ", coordinateOnOff=" + coordinateOnOff +
                ", widgetOnOff=" + widgetOnOff +
                ", coordinateMap=" + coordinateSetMap +
                ", widgetSetMap=" + widgetSetMap +
                ", coordinateList=" + coordinateList +
                ", widgetList=" + widgetList +
                '}';
    }
}
