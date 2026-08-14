package com.lgh.tapclick.myclass;

import com.lgh.tapclick.mybean.AppDescribe;
import com.lgh.tapclick.mybean.Coordinate;
import com.lgh.tapclick.mybean.MyAppConfig;
import com.lgh.tapclick.mybean.Regulation;
import com.lgh.tapclick.mybean.Widget;

import java.util.List;

final class SerializedDataDao extends DataDao {
    private final DataDao delegate;

    SerializedDataDao(DataDao delegate) {
        this.delegate = delegate;
    }

    @Override
    public List<AppDescribe> getAllAppDescribes() {
        return MyApplication.callDatabase(delegate::getAllAppDescribes);
    }

    @Override
    public List<AppDescribe> getEnabledAppDescribes() {
        return MyApplication.callDatabase(delegate::getEnabledAppDescribes);
    }

    @Override
    public AppDescribe getAppDescribeByPackage(String appPackage) {
        return MyApplication.callDatabase(() -> delegate.getAppDescribeByPackage(appPackage));
    }

    @Override
    public List<Coordinate> getCoordinatesByPackage(String appPackage) {
        return MyApplication.callDatabase(() -> delegate.getCoordinatesByPackage(appPackage));
    }

    @Override
    public List<Coordinate> getAllCoordinates() {
        return MyApplication.callDatabase(delegate::getAllCoordinates);
    }

    @Override
    public List<Coordinate> getEnabledCoordinates() {
        return MyApplication.callDatabase(delegate::getEnabledCoordinates);
    }

    @Override
    public List<Widget> getWidgetsByPackage(String appPackage) {
        return MyApplication.callDatabase(() -> delegate.getWidgetsByPackage(appPackage));
    }

    @Override
    public List<Widget> getAllWidgets() {
        return MyApplication.callDatabase(delegate::getAllWidgets);
    }

    @Override
    public List<Widget> getEnabledWidgets() {
        return MyApplication.callDatabase(delegate::getEnabledWidgets);
    }

    @Override
    public MyAppConfig getMyAppConfig() {
        return MyApplication.callDatabase(delegate::getMyAppConfig);
    }

    @Override
    public Long insertAppDescribe(AppDescribe appDescribe) {
        return MyApplication.callDatabase(() -> delegate.insertAppDescribe(appDescribe));
    }

    @Override
    public Long insertCoordinate(Coordinate coordinate) {
        return MyApplication.callDatabase(() -> delegate.insertCoordinate(coordinate));
    }

    @Override
    public Long insertWidget(Widget widget) {
        return MyApplication.callDatabase(() -> delegate.insertWidget(widget));
    }

    @Override
    public Long insertMyAppConfig(MyAppConfig myAppConfig) {
        return MyApplication.callDatabase(() -> delegate.insertMyAppConfig(myAppConfig));
    }

    @Override
    public void deleteCoordinate(Coordinate coordinate) {
        MyApplication.callDatabase(() -> {
            delegate.deleteCoordinate(coordinate);
            return null;
        });
    }

    @Override
    public void deleteWidget(Widget widget) {
        MyApplication.callDatabase(() -> {
            delegate.deleteWidget(widget);
            return null;
        });
    }

    @Override
    public void deleteAppDescribes(List<AppDescribe> appDescribes) {
        MyApplication.callDatabase(() -> {
            delegate.deleteAppDescribes(appDescribes);
            return null;
        });
    }

    @Override
    public void deleteCoordinates(List<Coordinate> coordinates) {
        MyApplication.callDatabase(() -> {
            delegate.deleteCoordinates(coordinates);
            return null;
        });
    }

    @Override
    public void deleteWidgets(List<Widget> widgets) {
        MyApplication.callDatabase(() -> {
            delegate.deleteWidgets(widgets);
            return null;
        });
    }

    @Override
    public void deleteAppDescribeByPackage(String appPackage) {
        MyApplication.callDatabase(() -> {
            delegate.deleteAppDescribeByPackage(appPackage);
            return null;
        });
    }

    @Override
    public void deleteCoordinatesByPackage(String appPackage) {
        MyApplication.callDatabase(() -> {
            delegate.deleteCoordinatesByPackage(appPackage);
            return null;
        });
    }

    @Override
    public void deleteWidgetsByPackage(String appPackage) {
        MyApplication.callDatabase(() -> {
            delegate.deleteWidgetsByPackage(appPackage);
            return null;
        });
    }

    @Override
    public void updateAppDescribe(AppDescribe appDescribe) {
        MyApplication.callDatabase(() -> {
            delegate.updateAppDescribe(appDescribe);
            return null;
        });
    }

    @Override
    public void updateAppDescribes(List<AppDescribe> appDescribes) {
        MyApplication.callDatabase(() -> {
            delegate.updateAppDescribes(appDescribes);
            return null;
        });
    }

    @Override
    public void updateCoordinate(Coordinate coordinate) {
        MyApplication.callDatabase(() -> {
            delegate.updateCoordinate(coordinate);
            return null;
        });
    }

    @Override
    public void updateCoordinates(List<Coordinate> coordinates) {
        MyApplication.callDatabase(() -> {
            delegate.updateCoordinates(coordinates);
            return null;
        });
    }

    @Override
    public void updateWidget(Widget widget) {
        MyApplication.callDatabase(() -> {
            delegate.updateWidget(widget);
            return null;
        });
    }

    @Override
    public void updateWidgets(List<Widget> widgets) {
        MyApplication.callDatabase(() -> {
            delegate.updateWidgets(widgets);
            return null;
        });
    }

    @Override
    public void updateMyAppConfig(MyAppConfig myAppConfig) {
        MyApplication.callDatabase(() -> {
            delegate.updateMyAppConfig(myAppConfig);
            return null;
        });
    }

    @Override
    public void insertAppDescribes(List<AppDescribe> appDescribes) {
        MyApplication.callDatabase(() -> {
            delegate.insertAppDescribes(appDescribes);
            return null;
        });
    }

    @Override
    public void insertCoordinates(List<Coordinate> coordinates) {
        MyApplication.callDatabase(() -> {
            delegate.insertCoordinates(coordinates);
            return null;
        });
    }

    @Override
    public void insertWidgets(List<Widget> widgets) {
        MyApplication.callDatabase(() -> {
            delegate.insertWidgets(widgets);
            return null;
        });
    }

    @Override
    public void replaceRegulations(List<Regulation> regulations) {
        MyApplication.callDatabase(() -> {
            delegate.replaceRegulations(regulations);
            return null;
        });
    }

    @Override
    public AppDescribe importWidget(Widget source, String appName) {
        return MyApplication.callDatabase(() -> delegate.importWidget(source, appName));
    }

    @Override
    public AppDescribe importCoordinate(Coordinate source, String appName) {
        return MyApplication.callDatabase(() -> delegate.importCoordinate(source, appName));
    }

    @Override
    public void deleteRegulations(List<AppDescribe> appDescribes) {
        MyApplication.callDatabase(() -> {
            delegate.deleteRegulations(appDescribes);
            return null;
        });
    }
}
