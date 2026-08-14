package com.lgh.tapclick.myclass;

import androidx.room.Dao;
import androidx.room.Delete;
import androidx.room.Insert;
import androidx.room.OnConflictStrategy;
import androidx.room.Query;
import androidx.room.Transaction;
import androidx.room.Update;

import com.lgh.tapclick.mybean.AppDescribe;
import com.lgh.tapclick.mybean.Coordinate;
import com.lgh.tapclick.mybean.MyAppConfig;
import com.lgh.tapclick.mybean.Regulation;
import com.lgh.tapclick.mybean.Widget;

import java.util.HashSet;
import java.util.List;
import java.util.Set;

@Dao
public abstract class DataDao {

    @Query("SELECT * FROM AppDescribe")
    public abstract List<AppDescribe> getAllAppDescribes();

    @Query("SELECT * FROM AppDescribe WHERE appPackage = :appPackage")
    public abstract AppDescribe getAppDescribeByPackage(String appPackage);

    @Query("SELECT * FROM Coordinate WHERE appPackage = :appPackage")
    public abstract List<Coordinate> getCoordinatesByPackage(String appPackage);

    @Query("SELECT * FROM Widget WHERE appPackage = :appPackage")
    public abstract List<Widget> getWidgetsByPackage(String appPackage);

    @Query("SELECT * FROM MyAppConfig WHERE id = 0")
    public abstract MyAppConfig getMyAppConfig();

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    public abstract Long insertAppDescribe(AppDescribe appDescribe);

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    public abstract Long insertCoordinate(Coordinate coordinate);

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    public abstract Long insertWidget(Widget widget);

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    public abstract Long insertMyAppConfig(MyAppConfig myAppConfig);

    @Delete
    public abstract void deleteCoordinate(Coordinate coordinate);

    @Delete
    public abstract void deleteWidget(Widget widget);

    @Delete
    public abstract void deleteAppDescribes(List<AppDescribe> appDescribes);

    @Delete
    public abstract void deleteCoordinates(List<Coordinate> coordinates);

    @Delete
    public abstract void deleteWidgets(List<Widget> widgets);

    @Query("DELETE FROM AppDescribe WHERE appPackage = :appPackage")
    public abstract void deleteAppDescribeByPackage(String appPackage);

    @Query("DELETE FROM Coordinate WHERE appPackage = :appPackage")
    public abstract void deleteCoordinatesByPackage(String appPackage);

    @Query("DELETE FROM Widget WHERE appPackage = :appPackage")
    public abstract void deleteWidgetsByPackage(String appPackage);

    @Update
    public abstract void updateAppDescribe(AppDescribe appDescribe);

    @Update
    public abstract void updateCoordinate(Coordinate coordinate);

    @Update
    public abstract void updateWidget(Widget widget);

    @Update
    public abstract void updateMyAppConfig(MyAppConfig myAppConfig);

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    public abstract void insertAppDescribes(List<AppDescribe> appDescribes);

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    public abstract void insertCoordinates(List<Coordinate> coordinates);

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    public abstract void insertWidgets(List<Widget> widgets);

    @Transaction
    public void replaceRegulations(List<Regulation> regulations) {
        long importTime = System.currentTimeMillis();
        Set<String> importedPackages = new HashSet<>();
        for (Regulation source : regulations) {
            Regulation regulation = new Regulation(source, importTime);
            String appPackage = regulation.appDescribe.appPackage;
            if (appPackage == null || appPackage.trim().isEmpty()) {
                throw new IllegalArgumentException("导入规则缺少应用包名");
            }
            if (!importedPackages.add(appPackage)) {
                throw new IllegalArgumentException("导入数据包含重复应用：" + appPackage);
            }
            deleteWidgetsByPackage(appPackage);
            deleteCoordinatesByPackage(appPackage);
            deleteAppDescribeByPackage(appPackage);
            regulation.appDescribe.id = insertAppDescribe(regulation.appDescribe);
            insertCoordinates(regulation.coordinateList);
            insertWidgets(regulation.widgetList);
        }
    }

    @Transaction
    public AppDescribe importWidget(Widget source, String appName) {
        Widget widget = new Widget(source);
        widget.id = null;
        widget.createTime = System.currentTimeMillis();
        widget.lastTriggerTime = 0;
        widget.triggerCount = 0;
        widget.triggerReason = "";
        widget.validatePatterns();
        AppDescribe appDescribe = getOrCreateAppDescribe(widget.appPackage, appName);
        appDescribe.widgetOnOff = true;
        updateAppDescribe(appDescribe);
        insertWidget(widget);
        return appDescribe;
    }

    @Transaction
    public AppDescribe importCoordinate(Coordinate source, String appName) {
        Coordinate coordinate = new Coordinate(source);
        coordinate.id = null;
        coordinate.createTime = System.currentTimeMillis();
        coordinate.lastTriggerTime = 0;
        coordinate.triggerCount = 0;
        AppDescribe appDescribe = getOrCreateAppDescribe(coordinate.appPackage, appName);
        appDescribe.coordinateOnOff = true;
        updateAppDescribe(appDescribe);
        insertCoordinate(coordinate);
        return appDescribe;
    }

    @Transaction
    public void deleteRegulations(List<AppDescribe> appDescribes) {
        for (AppDescribe appDescribe : appDescribes) {
            deleteWidgetsByPackage(appDescribe.appPackage);
            deleteCoordinatesByPackage(appDescribe.appPackage);
            deleteAppDescribeByPackage(appDescribe.appPackage);
        }
    }

    private AppDescribe getOrCreateAppDescribe(String appPackage, String appName) {
        if (appPackage == null || appPackage.trim().isEmpty()) {
            throw new IllegalArgumentException("导入规则缺少应用包名");
        }
        AppDescribe appDescribe = getAppDescribeByPackage(appPackage);
        if (appDescribe == null) {
            appDescribe = new AppDescribe();
            appDescribe.appPackage = appPackage;
            appDescribe.appName = appName == null ? "" : appName;
            appDescribe.id = insertAppDescribe(appDescribe);
        }
        return appDescribe;
    }
}
