package com.aelshahat.homeorganizer;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;

public final class ClassificationEngine {
    private final List<String> categories = new ArrayList<>(Arrays.asList(
            "Communication", "Social", "Work", "Finance", "Shopping", "Media",
            "Games", "Utilities", "Travel", "Productivity", "Other", "Needs Review"));

    public List<String> getCategories() { return new ArrayList<>(categories); }

    public List<ClassificationResult> classify(List<HomeShortcut> shortcuts) {
        List<ClassificationResult> out = new ArrayList<>();
        for (HomeShortcut s : shortcuts) out.add(classifyOne(s));
        return out;
    }

    public ClassificationResult classifyOne(HomeShortcut s) {
        String text = (s.label + " " + String.valueOf(s.packageName) + " " + String.valueOf(s.componentName))
                .toLowerCase(Locale.US);
        if (contains(text, "chrome", "browser", "firefox", "edge", "opera"))
            return new ClassificationResult(s, "Utilities", .90f, "Browser keyword");
        if (contains(text, "message", "dialer", "phone", "contacts", "telegram", "whatsapp"))
            return new ClassificationResult(s, "Communication", .92f, "Communication keyword");
        if (contains(text, "instagram", "facebook", "twitter", "tiktok", "snapchat"))
            return new ClassificationResult(s, "Social", .93f, "Social keyword");
        if (contains(text, "camera", "gallery", "photos", "youtube", "spotify", "music", "video"))
            return new ClassificationResult(s, "Media", .88f, "Media keyword");
        if (contains(text, "bank", "finance", "wallet", "money", "cash", "instapay"))
            return new ClassificationResult(s, "Finance", .88f, "Finance keyword");
        if (contains(text, "amazon", "shopping", "shop", "noon", "jumia", "shein"))
            return new ClassificationResult(s, "Shopping", .88f, "Shopping keyword");
        if (contains(text, "brawl", "game", "games", "pubg", "clash", "minecraft"))
            return new ClassificationResult(s, "Games", .94f, "Game keyword");
        if (contains(text, "calendar", "drive", "docs", "sheets", "office", "slack", "teams", "notion"))
            return new ClassificationResult(s, "Productivity", .82f, "Productivity keyword");
        if (contains(text, "maps", "uber", "careem", "booking", "travel", "airbnb"))
            return new ClassificationResult(s, "Travel", .86f, "Travel keyword");
        if (contains(text, "settings", "calculator", "clock", "files", "x-plore"))
            return new ClassificationResult(s, "Utilities", .84f, "Utility keyword");
        return new ClassificationResult(s, "Needs Review", .35f, "No reliable local rule matched");
    }

    private boolean contains(String text, String... words) {
        for (String w : words) if (text.contains(w.toLowerCase(Locale.US))) return true;
        return false;
    }
}
