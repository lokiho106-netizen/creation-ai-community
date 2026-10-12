package com.creationai.community.automation;

import android.graphics.Rect;
import android.view.accessibility.AccessibilityNodeInfo;

import java.util.ArrayDeque;
import java.util.Collections;
import java.util.List;
import java.util.Queue;

public final class NodeFinder {
    public AccessibilityNodeInfo byViewId(AccessibilityNodeInfo root, String id) {
        if (root == null || id == null || id.isEmpty()) return null;
        List<AccessibilityNodeInfo> matches = root.findAccessibilityNodeInfosByViewId(id);
        return firstVisible(matches);
    }

    public AccessibilityNodeInfo byText(AccessibilityNodeInfo root, String text) {
        if (root == null || text == null || text.isEmpty()) return null;
        List<AccessibilityNodeInfo> matches = root.findAccessibilityNodeInfosByText(text);
        return firstVisible(matches);
    }

    public AccessibilityNodeInfo byDescription(AccessibilityNodeInfo root, String description) {
        if (root == null || description == null || description.isEmpty()) return null;
        Queue<AccessibilityNodeInfo> queue = new ArrayDeque<>();
        queue.add(root);
        while (!queue.isEmpty()) {
            AccessibilityNodeInfo node = queue.remove();
            CharSequence value = node.getContentDescription();
            if (node.isVisibleToUser() && value != null && value.toString().contains(description)) {
                return node;
            }
            for (int i = 0; i < node.getChildCount(); i++) {
                AccessibilityNodeInfo child = node.getChild(i);
                if (child != null) queue.add(child);
            }
        }
        return null;
    }

    public AccessibilityNodeInfo clickableAncestor(AccessibilityNodeInfo node) {
        AccessibilityNodeInfo current = node;
        for (int depth = 0; current != null && depth < 6; depth++) {
            if (current.isClickable() && current.isEnabled() && current.isVisibleToUser()) return current;
            current = current.getParent();
        }
        return null;
    }

    public boolean hasUsableBounds(AccessibilityNodeInfo node) {
        if (node == null || !node.isVisibleToUser()) return false;
        Rect rect = new Rect();
        node.getBoundsInScreen(rect);
        return rect.width() > 2 && rect.height() > 2;
    }

    private AccessibilityNodeInfo firstVisible(List<AccessibilityNodeInfo> nodes) {
        for (AccessibilityNodeInfo node : nodes == null ? Collections.<AccessibilityNodeInfo>emptyList() : nodes) {
            if (node != null && node.isVisibleToUser()) return node;
        }
        return null;
    }
}
