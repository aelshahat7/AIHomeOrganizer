package com.aelshahat.homeorganizer;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public final class HomePage {
    public final int index;
    public final String identity;
    public final boolean current;
    public final List<HomeShortcut> shortcuts;

    public HomePage(int index, String identity, boolean current, List<HomeShortcut> shortcuts) {
        this.index = index;
        this.identity = identity == null ? "" : identity;
        this.current = current;
        this.shortcuts = Collections.unmodifiableList(new ArrayList<>(shortcuts));
    }

    public String signature() {
        StringBuilder b = new StringBuilder(identity);
        for (HomeShortcut s : shortcuts) {
            if (!s.hotseat) {
                b.append('|').append(s.label).append('@').append(s.centerX).append(',').append(s.centerY);
            }
        }
        return b.toString();
    }
}
