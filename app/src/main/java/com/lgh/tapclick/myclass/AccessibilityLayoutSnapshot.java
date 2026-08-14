package com.lgh.tapclick.myclass;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * An immutable copy of the accessibility properties needed by the rule picker.
 *
 * <p>The picker must not retain {@code AccessibilityNodeInfo} instances because
 * the underlying window can disappear before the user selects a node.</p>
 */
public final class AccessibilityLayoutSnapshot {
    private final String appPackage;
    private final String appActivity;
    private final List<Node> nodes;

    public AccessibilityLayoutSnapshot(String appPackage, String appActivity, List<Node> nodes) {
        this.appPackage = valueOrEmpty(appPackage);
        this.appActivity = valueOrEmpty(appActivity);
        List<Node> sortedNodes = new ArrayList<>();
        Set<String> nodeKeys = new HashSet<>();
        for (Node node : nodes == null ? Collections.<Node>emptyList() : nodes) {
            if (node != null && nodeKeys.add(node.deduplicationKey())) {
                sortedNodes.add(node);
            }
        }
        sortedNodes.sort(Comparator.comparingLong(Node::getArea).reversed());
        this.nodes = Collections.unmodifiableList(sortedNodes);
    }

    public String getAppPackage() {
        return appPackage;
    }

    public String getAppActivity() {
        return appActivity;
    }

    public List<Node> getNodes() {
        return nodes;
    }

    public boolean isEmpty() {
        return nodes.isEmpty();
    }

    /**
     * A single empty root rectangle is not a useful rule target. Some apps
     * expose that placeholder before their real accessibility window is ready.
     */
    public boolean hasSelectableContent() {
        if (nodes.size() > 1) {
            return true;
        }
        if (nodes.isEmpty()) {
            return false;
        }
        Node node = nodes.get(0);
        return node.isClickable()
                || !node.getDescription().isEmpty()
                || !node.getText().isEmpty();
    }

    private static String valueOrEmpty(String value) {
        return value == null ? "" : value;
    }

    public static final class Node {
        private final boolean clickable;
        private final Long nodeId;
        private final String viewId;
        private final String description;
        private final String text;
        private final int left;
        private final int top;
        private final int right;
        private final int bottom;

        public Node(boolean clickable, Long nodeId, String viewId, String description,
                    String text, int left, int top, int right, int bottom) {
            this.clickable = clickable;
            this.nodeId = nodeId;
            this.viewId = valueOrEmpty(viewId);
            this.description = valueOrEmpty(description);
            this.text = valueOrEmpty(text);
            this.left = left;
            this.top = top;
            this.right = right;
            this.bottom = bottom;
        }

        public boolean isClickable() {
            return clickable;
        }

        public Long getNodeId() {
            return nodeId;
        }

        public String getViewId() {
            return viewId;
        }

        public String getDescription() {
            return description;
        }

        public String getText() {
            return text;
        }

        public int getLeft() {
            return left;
        }

        public int getTop() {
            return top;
        }

        public int getWidth() {
            return Math.max(0, right - left);
        }

        public int getHeight() {
            return Math.max(0, bottom - top);
        }

        public long getArea() {
            return (long) getWidth() * getHeight();
        }

        private String deduplicationKey() {
            return "node:" + nodeId
                    + "|bounds:" + left + ':' + top + ':' + right + ':' + bottom
                    + "|clickable:" + clickable
                    + "|viewId:" + viewId
                    + "|description:" + description
                    + "|text:" + text;
        }
    }
}
