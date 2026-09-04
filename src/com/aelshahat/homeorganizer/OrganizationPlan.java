package com.aelshahat.homeorganizer;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

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
    public void add(Item item) { items.add(item); }
    public List<Item> getItems() { return Collections.unmodifiableList(items); }
    public void setApproved(boolean value) { approved = value; }
    public boolean isApproved() { return approved; }
    public boolean isEmpty() { return items.isEmpty(); }
}
