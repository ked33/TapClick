package com.lgh.tapclick.myclass;

import com.lgh.tapclick.mybean.Coordinate;
import com.lgh.tapclick.mybean.Widget;

import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

/** Typed rule identity and dependency validation shared by the editor and runtime. */
public final class RuleReference {
    public static final int LEGACY = 0;
    public static final int COORDINATE = 1;
    public static final int WIDGET = 2;
    public final int type;
    public final long id;

    public RuleReference(int type, long id) {
        this.type = type;
        this.id = id;
    }

    @Override
    public boolean equals(Object other) {
        return other instanceof RuleReference && type == ((RuleReference) other).type
                && id == ((RuleReference) other).id;
    }

    @Override
    public int hashCode() {
        return Objects.hash(type, id);
    }

    public static RuleReference resolve(int type, Long id, String activity,
                                        List<Coordinate> coordinates, List<Widget> widgets) {
        return new Index(coordinates, widgets).resolve(type, id, activity);
    }

    /** Null means valid. Unresolved legacy links remain blocked until explicitly repaired. */
    public static String validate(RuleReference owner, RuleReference selected, String activity,
                                  List<Coordinate> coordinates, List<Widget> widgets) {
        return new Index(coordinates, widgets).validate(owner, selected, activity);
    }

    /** Build once per app snapshot, rather than searching every rule for every edge. */
    public static final class Index {
        private final Map<RuleReference, String> activities = new HashMap<>();
        private final Map<RuleReference, RuleReference> edges = new HashMap<>();
        private final Set<RuleReference> invalidLinks = new HashSet<>();

        public Index(List<Coordinate> coordinates, List<Widget> widgets) {
            for (Coordinate rule : coordinates) {
                if (rule.id != null) { activities.put(new RuleReference(COORDINATE, rule.id), rule.appActivity); }
            }
            for (Widget rule : widgets) {
                if (rule.id != null) { activities.put(new RuleReference(WIDGET, rule.id), rule.appActivity); }
            }
            for (Coordinate rule : coordinates) {
                addEdge(COORDINATE, rule.id, rule.preconditionRuleType, rule.preconditionRuleId, rule.appActivity);
            }
            for (Widget rule : widgets) {
                addEdge(WIDGET, rule.id, rule.preconditionRuleType, rule.preconditionRuleId, rule.appActivity);
            }
        }

        private void addEdge(int type, Long id, int targetType, Long targetId, String activity) {
            if (id == null) { return; }
            RuleReference owner = new RuleReference(type, id);
            RuleReference target = resolve(targetType, targetId, activity);
            edges.put(owner, target);
            if (targetId != null && target == null) { invalidLinks.add(owner); }
        }

        public RuleReference resolve(int type, Long id, String activity) {
            if (id == null) { return null; }
            RuleReference coordinate = new RuleReference(COORDINATE, id);
            RuleReference widget = new RuleReference(WIDGET, id);
            boolean hasCoordinate = (type == LEGACY || type == COORDINATE)
                    && activities.containsKey(coordinate) && Objects.equals(activity, activities.get(coordinate));
            boolean hasWidget = (type == LEGACY || type == WIDGET)
                    && activities.containsKey(widget) && Objects.equals(activity, activities.get(widget));
            return hasCoordinate == hasWidget ? null : hasCoordinate ? coordinate : widget;
        }

        public String validate(RuleReference owner, RuleReference selected, String activity) {
            if (selected == null) { return null; }
            if (selected.equals(owner)) { return "不能选择当前规则作为前置规则"; }
            if (resolve(selected.type, selected.id, activity) == null) {
                return "前置规则不存在或不在同一页面";
            }
            Set<RuleReference> visited = new HashSet<>();
            visited.add(owner);
            RuleReference current = selected;
            while (current != null) {
                if (!visited.add(current)) { return "前置规则形成循环，无法执行"; }
                if (invalidLinks.contains(current)) { return "前置规则包含无效依赖，请先修复"; }
                current = edges.get(current);
            }
            return null;
        }
    }
}
