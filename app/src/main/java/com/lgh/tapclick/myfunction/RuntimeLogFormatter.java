package com.lgh.tapclick.myfunction;

public final class RuntimeLogFormatter {
    private RuntimeLogFormatter() {
    }

    public static String formatRuleSummary(String ruleType, Long ruleId, String appPackage,
                                           String activity, String reason, String result) {
        return "规则 type=" + valueOrUnknown(ruleType)
                + " id=" + (ruleId == null ? "未分配" : ruleId)
                + " app=" + valueOrUnknown(appPackage)
                + " page=" + valueOrUnknown(activity)
                + " reason=" + valueOrUnknown(reason)
                + " result=" + valueOrUnknown(result);
    }

    public static String appendDebugDetails(String summary, boolean debug, String fullRuleJson) {
        if (!debug || fullRuleJson == null || fullRuleJson.isEmpty()) {
            return summary;
        }
        return summary + " detail=" + fullRuleJson;
    }

    private static String valueOrUnknown(String value) {
        return value == null || value.isEmpty() ? "未知" : value;
    }
}
