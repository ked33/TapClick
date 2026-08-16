package com.lgh.tapclick.mybean;

import androidx.room.Entity;
import androidx.room.ColumnInfo;
import androidx.room.Index;
import androidx.room.PrimaryKey;

@Entity(indices = @Index(value = {"appPackage"}))
public class Coordinate {
    @PrimaryKey(autoGenerate = true)
    public Long id;
    public String appPackage;
    public String appActivity;
    public long createTime;
    public int xPosition;
    public int yPosition;
    public int clickDelay;
    public int clickInterval;
    public int clickNumber;
    public String visualSignature;
    /** 0 means unlimited successful triggers. */
    @ColumnInfo(defaultValue = "0")
    public int maxTriggerCount;
    /** 0 means no Activity-start window limit. */
    @ColumnInfo(defaultValue = "0")
    public int initialMatchWindowMillis;
    /** Optional rule id that must have triggered earlier on this Activity. */
    public Long preconditionRuleId;
    /** 0 means no successful-action cooldown. */
    @ColumnInfo(defaultValue = "0")
    public int actionCooldownMillis;
    public String comment;
    public long lastTriggerTime;
    public int triggerCount;

    public Coordinate() {
        this.appPackage = "";
        this.appActivity = "";
        this.xPosition = 0;
        this.yPosition = 0;
        this.clickDelay = 1000;
        this.clickInterval = 1000;
        this.clickNumber = 1;
        this.visualSignature = null;
        this.maxTriggerCount = 0;
        this.initialMatchWindowMillis = 0;
        this.preconditionRuleId = null;
        this.actionCooldownMillis = 0;
        this.comment = "";
        this.lastTriggerTime = 0;
        this.triggerCount = 0;
        this.createTime = System.currentTimeMillis();
    }

    public Coordinate(Coordinate coordinate) {
        this.appPackage = coordinate.appPackage;
        this.appActivity = coordinate.appActivity;
        this.createTime = coordinate.createTime;
        this.xPosition = coordinate.xPosition;
        this.yPosition = coordinate.yPosition;
        this.clickDelay = coordinate.clickDelay;
        this.clickInterval = coordinate.clickInterval;
        this.clickNumber = coordinate.clickNumber;
        this.visualSignature = coordinate.visualSignature;
        this.maxTriggerCount = coordinate.maxTriggerCount;
        this.initialMatchWindowMillis = coordinate.initialMatchWindowMillis;
        this.preconditionRuleId = coordinate.preconditionRuleId;
        this.actionCooldownMillis = coordinate.actionCooldownMillis;
        this.comment = coordinate.comment;
        this.lastTriggerTime = coordinate.lastTriggerTime;
        this.triggerCount = coordinate.triggerCount;
    }

    @Override
    public String toString() {
        return "Coordinate{" +
                "id=" + id +
                ", appPackage='" + appPackage + '\'' +
                ", appActivity='" + appActivity + '\'' +
                ", createTime=" + createTime +
                ", xPosition=" + xPosition +
                ", yPosition=" + yPosition +
                ", clickDelay=" + clickDelay +
                ", clickInterval=" + clickInterval +
                ", clickNumber=" + clickNumber +
                ", visualVerification=" + (visualSignature != null && !visualSignature.isEmpty()) +
                ", visualSignatureLength=" + (visualSignature == null ? 0 : visualSignature.length()) +
                ", maxTriggerCount=" + maxTriggerCount +
                ", initialMatchWindowMillis=" + initialMatchWindowMillis +
                ", preconditionRuleId=" + preconditionRuleId +
                ", actionCooldownMillis=" + actionCooldownMillis +
                ", comment='" + comment + '\'' +
                ", lastTriggerTime=" + lastTriggerTime +
                ", triggerCount=" + triggerCount +
                '}';
    }
}
