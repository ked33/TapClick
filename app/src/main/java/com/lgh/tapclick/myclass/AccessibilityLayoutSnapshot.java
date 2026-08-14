package com.lgh.tapclick.myclass;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

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
        Map<String, Node> uniqueNodes = new LinkedHashMap<>();
        for (Node node : nodes == null ? Collections.<Node>emptyList() : nodes) {
            if (node == null) {
                continue;
            }
            String nodeKey = node.identityKey();
            Node previous = uniqueNodes.get(nodeKey);
            if (previous == null || node.getSelectionQuality() > previous.getSelectionQuality()) {
                uniqueNodes.put(nodeKey, node);
            }
        }
        List<Node> sortedNodes = new ArrayList<>(uniqueNodes.values());
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
        if (nodes.isEmpty()) {
            return false;
        }
        if (nodes.size() == 1) {
            Node node = nodes.get(0);
            return node.isInteractive()
                    || !node.getDescription().isEmpty()
                    || !node.getText().isEmpty();
        }
        for (Node node : nodes) {
            if (node.hasSelectableMetadata()) {
                return true;
            }
        }
        // Keep multiple positive-bounds nodes available for coordinate rules.
        // They may be custom-drawn containers with no accessibility metadata,
        // but a user can still select their screen rectangle explicitly.
        return nodes.size() > 1;
    }

    /**
     * Returns whether at least one node exposes an interaction affordance.
     * This is intentionally stricter than {@link #hasSelectableContent()}:
     * text-only nodes can still be useful for a coordinate rule, but should
     * trigger an enrichment/retry before we conclude that an ad button exists.
     */
    public boolean hasInteractiveContent() {
        for (Node node : nodes) {
            if (node.isInteractive()) {
                return true;
            }
        }
        return false;
    }

    public int getInteractiveNodeCount() {
        int count = 0;
        for (Node node : nodes) {
            if (node.isInteractive()) {
                count++;
            }
        }
        return count;
    }

    public int getVisibleNodeCount() {
        int count = 0;
        for (Node node : nodes) {
            if (node.isVisibleToUser()) {
                count++;
            }
        }
        return count;
    }

    public int getIdentifiedNodeCount() {
        int count = 0;
        for (Node node : nodes) {
            if (node.hasSelectableMetadata()) {
                count++;
            }
        }
        return count;
    }

    private static String valueOrEmpty(String value) {
        return value == null ? "" : value;
    }

    public static final class Node {
        private static final int ACTION_CLICK_MASK = 1 << 4;
        private static final int ACTION_LONG_CLICK_MASK = 1 << 5;
        private final boolean clickable;
        private final boolean visibleToUser;
        private final boolean enabled;
        private final boolean focusable;
        private final int actionMask;
        private final int childCount;
        private final int windowId;
        private final Long nodeId;
        private final String viewId;
        private final String description;
        private final String text;
        private final String className;
        private final String nodePackage;
        private final int left;
        private final int top;
        private final int right;
        private final int bottom;

        public Node(boolean clickable, Long nodeId, String viewId, String description,
                    String text, int left, int top, int right, int bottom) {
            this(clickable, nodeId, viewId, description, text, left, top, right, bottom,
                    true, true, false, 0, -1, -1, "", "");
        }

        public Node(boolean clickable, Long nodeId, String viewId, String description,
                    String text, int left, int top, int right, int bottom,
                    boolean visibleToUser, boolean enabled, boolean focusable,
                    int actionMask, int childCount, int windowId,
                    String className, String nodePackage) {
            this.clickable = clickable;
            this.visibleToUser = visibleToUser;
            this.enabled = enabled;
            this.focusable = focusable;
            this.actionMask = actionMask;
            this.childCount = childCount;
            this.windowId = windowId;
            this.nodeId = nodeId;
            this.viewId = valueOrEmpty(viewId);
            this.description = valueOrEmpty(description);
            this.text = valueOrEmpty(text);
            this.className = valueOrEmpty(className);
            this.nodePackage = valueOrEmpty(nodePackage);
            this.left = left;
            this.top = top;
            this.right = right;
            this.bottom = bottom;
        }

        public boolean isClickable() {
            return clickable;
        }

        public boolean isVisibleToUser() {
            return visibleToUser;
        }

        public boolean isEnabled() {
            return enabled;
        }

        public boolean isFocusable() {
            return focusable;
        }

        public int getActionMask() {
            return actionMask;
        }

        public int getChildCount() {
            return childCount;
        }

        public int getWindowId() {
            return windowId;
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

        public String getClassName() {
            return className;
        }

        public String getNodePackage() {
            return nodePackage;
        }

        public boolean hasClickAction() {
            return (actionMask & ACTION_CLICK_MASK) != 0;
        }

        public boolean isInteractive() {
            return enabled && (clickable || hasClickAction()
                    || (actionMask & ACTION_LONG_CLICK_MASK) != 0);
        }

        public boolean hasSelectableMetadata() {
            return isInteractive()
                    || !viewId.isEmpty()
                    || !description.isEmpty()
                    || !text.isEmpty();
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

        /**
         * A bounded diagnostic representation. It deliberately reports text
         * and description lengths instead of their contents to avoid leaking
         * ad/user data into the runtime log.
         */
        public String toDebugSummary(int index) {
            return "#" + index
                    + " bounds=" + left + "," + top + "," + right + "," + bottom
                    + " visible=" + visibleToUser
                    + " enabled=" + enabled
                    + " clickable=" + clickable
                    + " focusable=" + focusable
                    + " actions=0x" + Integer.toHexString(actionMask)
                    + " childCount=" + childCount
                    + " windowId=" + windowId
                    + " class=" + valueOrUnknown(className)
                    + " package=" + valueOrUnknown(nodePackage)
                    + " viewId=" + valueOrUnknown(viewId)
                    + " textLen=" + text.length()
                    + " descLen=" + description.length();
        }

        private long getSelectionQuality() {
            return (isInteractive() ? 1_000_000L : 0L)
                    + (visibleToUser ? 10_000L : 0L)
                    + (enabled ? 1_000L : 0L)
                    + (hasSelectableMetadata() ? 100L : 0L)
                    + getArea();
        }

        private String identityKey() {
            if (nodeId != null) {
                return "node:" + nodeId + "|window:" + windowId;
            }
            return "bounds:" + left + ':' + top + ':' + right + ':' + bottom
                    + "|window:" + windowId
                    + "|viewId:" + viewId
                    + "|class:" + className;
        }

        private static String valueOrUnknown(String value) {
            return value == null || value.isEmpty() ? "?" : value;
        }
    }
}
