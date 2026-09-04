package com.aelshahat.homeorganizer;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public final class OrganizationPlan {
    public static final class Item {
        public final HomeShortcut shortcut;
        public final String category;
        public final float confidence;
        public final String reason;
        public Item(HomeShortcut shortcut, String category, float confidence, String reason) {
            this.shortcut = shortcut; this.category = category; this.confidence = confidence; this.reason = reason;
        }
    }
    private final List<Item> items = new ArrayList<>();
    private boolean approved;
    public void add(Item item) { if (item != null) items.add(item); }
    public List<Item> getItems() { return Collections.unmodifiableList(items); }
    public Map<String,List<Item>> grouped() {
        Map<String,List<Item>> result = new LinkedHashMap<>();
        for (Item item : items) result.computeIfAbsent(item.category, k -> new ArrayList<>()).add(item);
        return result;
    }
    public List<Item> eligibleItems() {
        List<Item> result = new ArrayList<>();
        for (Item item : items) if (item.shortcut != null && !item.shortcut.hotseat && !"Needs Review".equalsIgnoreCase(item.category)) result.add(item);
        return result;
    }
    public void setApproved(boolean value) { approved = value; }
    public boolean isApproved() { return approved; }
    public boolean isEmpty() { return items.isEmpty(); }
}
