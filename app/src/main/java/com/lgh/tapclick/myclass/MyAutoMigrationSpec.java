package com.lgh.tapclick.myclass;

import androidx.room.DeleteColumn;
import androidx.room.RenameColumn;
import androidx.room.migration.AutoMigrationSpec;
import androidx.annotation.NonNull;
import androidx.sqlite.db.SupportSQLiteDatabase;

public class MyAutoMigrationSpec {
    @DeleteColumn(tableName = "AppDescribe", columnName = "onOff")
    public static class From7To8 implements AutoMigrationSpec {
    }

    @RenameColumn(tableName = "Widget", fromColumnName = "widgetId", toColumnName = "widgetViewId")
    public static class From8To9 implements AutoMigrationSpec {
    }

    @DeleteColumn(tableName = "Widget", columnName = "toast")
    @DeleteColumn(tableName = "Coordinate", columnName = "toast")
    public static class From11To12 implements AutoMigrationSpec {
    }

    public static class From16To17 implements AutoMigrationSpec {
        @Override
        public void onPostMigrate(@NonNull SupportSQLiteDatabase db) {
            migrateReferences(db, "Coordinate");
            migrateReferences(db, "Widget");
        }

        private static void migrateReferences(SupportSQLiteDatabase db, String table) {
            String coordinate = "EXISTS (SELECT 1 FROM Coordinate AS target WHERE target.id = "
                    + table + ".preconditionRuleId AND target.appPackage = " + table
                    + ".appPackage AND target.appActivity = " + table + ".appActivity)";
            String widget = "EXISTS (SELECT 1 FROM Widget AS target WHERE target.id = "
                    + table + ".preconditionRuleId AND target.appPackage = " + table
                    + ".appPackage AND target.appActivity = " + table + ".appActivity)";
            // -1 retains the link but blocks ambiguous/missing legacy references.
            db.execSQL("UPDATE " + table + " SET preconditionRuleType = CASE WHEN "
                    + coordinate + " AND NOT " + widget + " THEN 1 WHEN " + widget
                    + " AND NOT " + coordinate + " THEN 2 ELSE -1 END WHERE preconditionRuleId IS NOT NULL");
        }
    }
}
