package com.aelshahat.homeorganizer;

import android.content.Context;
import android.content.pm.ApplicationInfo;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/** Offline multi-signal classifier. It adapts to whatever apps are installed on the device. */
public final class ClassificationEngine {
    private final List<String> categories = new ArrayList<>(Arrays.asList(
            "Communication", "Social", "Work", "Finance", "Shopping", "Media",
            "Games", "Utilities", "Travel", "Productivity", "Other", "Needs Review"));
    private final AppMetadataResolver resolver;

    public ClassificationEngine() { this.resolver = null; }
    public ClassificationEngine(Context context) { this.resolver = new AppMetadataResolver(context); }

    public List<String> getCategories() { return new ArrayList<>(categories); }

    public List<ClassificationResult> classify(List<HomeShortcut> shortcuts) {
        List<ClassificationResult> out = new ArrayList<>();
        Map<String, ClassificationResult> cache = new LinkedHashMap<>();
        for (HomeShortcut s : shortcuts) {
            String key = AppMetadataResolver.normalize(s.label);
            ClassificationResult r = cache.get(key);
            if (r == null) {
                r = classifyOne(s);
                cache.put(key, r);
            }
            out.add(r);
        }
        return out;
    }

    public ClassificationResult classifyOne(HomeShortcut s) {
        String label = AppMetadataResolver.normalize(s.label);
        String pkg = "";
        int systemCategory = ApplicationInfo.CATEGORY_UNDEFINED;
        AppMetadataResolver.Metadata meta = resolver == null ? null : resolver.resolve(s.label);
        if (meta != null) {
            pkg = AppMetadataResolver.normalize(meta.packageName);
            systemCategory = meta.applicationCategory;
        }

        int[] scores = new int[categories.size()];
        String[] reasons = new String[categories.size()];
        score(scores, reasons, "Communication", pkg, label, 50, "Package/label signal");
        score(scores, reasons, "Social", pkg, label, 50, "Package/label signal");
        score(scores, reasons, "Work", pkg, label, 50, "Package/label signal");
        score(scores, reasons, "Finance", pkg, label, 50, "Package/label signal");
        score(scores, reasons, "Shopping", pkg, label, 50, "Package/label signal");
        score(scores, reasons, "Media", pkg, label, 50, "Package/label signal");
        score(scores, reasons, "Games", pkg, label, 50, "Package/label signal");
        score(scores, reasons, "Travel", pkg, label, 50, "Package/label signal");
        score(scores, reasons, "Productivity", pkg, label, 50, "Package/label signal");
        score(scores, reasons, "Utilities", pkg, label, 50, "Package/label signal");

        keyword(scores, reasons, "Communication", label, 22, "Communication keyword");
        keyword(scores, reasons, "Social", label, 22, "Social keyword");
        keyword(scores, reasons, "Work", label, 20, "Work keyword");
        keyword(scores, reasons, "Finance", label, 22, "Finance keyword");
        keyword(scores, reasons, "Shopping", label, 20, "Shopping keyword");
        keyword(scores, reasons, "Media", label, 20, "Media keyword");
        keyword(scores, reasons, "Games", label, 24, "Game keyword");
        keyword(scores, reasons, "Travel", label, 20, "Travel keyword");
        keyword(scores, reasons, "Productivity", label, 18, "Productivity keyword");
        keyword(scores, reasons, "Utilities", label, 16, "Utility keyword");

        // Android's category is intentionally only a supporting signal.
        if (systemCategory == ApplicationInfo.CATEGORY_GAME)
            add(scores, reasons, "Games", 20, "Android application category");
        else if (systemCategory == ApplicationInfo.CATEGORY_AUDIO || systemCategory == ApplicationInfo.CATEGORY_IMAGE)
            add(scores, reasons, "Media", 15, "Android application category");

        int best = -1, second = -1;
        for (int i = 0; i < categories.size() - 2; i++) {
            if (best < 0 || scores[i] > scores[best]) { second = best; best = i; }
            else if (second < 0 || scores[i] > scores[second]) second = i;
        }

        int bestScore = best < 0 ? 0 : scores[best];
        int secondScore = second < 0 ? 0 : scores[second];
        String category = best < 0 ? "Needs Review" : categories.get(best);
        float confidence;
        if (bestScore >= 90 && bestScore - secondScore >= 15) confidence = .95f;
        else if (bestScore >= 70 && bestScore - secondScore >= 10) confidence = .85f;
        else if (bestScore >= 50 && bestScore - secondScore >= 8) confidence = .72f;
        else if (bestScore >= 35 && bestScore - secondScore >= 6) confidence = .60f;
        else { category = "Needs Review"; confidence = .35f; }

        String reason = reasons[best < 0 ? 0 : best];
        if (reason == null) reason = "No reliable local signals matched";
        if (meta != null) reason += " | resolved=" + meta.packageName;
        return new ClassificationResult(s, category, confidence, reason);
    }

    private void score(int[] scores, String[] reasons, String category, String pkg, String label, int points, String reason) {
        String p = pkg + " " + label;
        String c = category.toLowerCase(Locale.ROOT);
        boolean hit = false;
        switch (category) {
            case "Communication": hit = contains(p,"whatsapp","telegram","messenger","signal","dialer","phone","contacts","sms"); break;
            case "Social": hit = contains(p,"facebook","instagram","twitter","tiktok","snapchat","linkedin","reddit"); break;
            case "Work": hit = contains(p,"business","outlook","office","teams","slack","adsmanager","businesssuite"); break;
            case "Finance": hit = contains(p,"bank","finance","wallet","money","cash","instapay","paypal","fawry"); break;
            case "Shopping": hit = contains(p,"amazon","shopping","shop","noon","jumia","shein","ebay","talabat","delivery"); break;
            case "Media": hit = contains(p,"youtube","spotify","soundcloud","anghami","music","video","gallery","photos","netflix"); break;
            case "Games": hit = contains(p,"game","games","pubg","clash","minecraft","brawl","wildrift","candycrush","gossipharbor"); break;
            case "Travel": hit = contains(p,"maps","uber","careem","booking","travel","airbnb","trip"); break;
            case "Productivity": hit = contains(p,"drive","docs","sheets","excel","word","notion","calendar","task","todo","keep"); break;
            case "Utilities": hit = contains(p,"settings","calculator","clock","files","xplore","speedtest","router","authenticator","smart\u0020switch"); break;
        }
        if (hit) add(scores, reasons, category, points, reason);
    }

    private void keyword(int[] scores, String[] reasons, String category, String label, int points, String reason) {
        score(scores, reasons, category, "", label, points, reason);
    }

    private void add(int[] scores, String[] reasons, String category, int points, String reason) {
        int i = categories.indexOf(category);
        if (i >= 0) { scores[i] += points; if (reasons[i] == null) reasons[i] = reason; }
    }

    private boolean contains(String text, String... words) {
        for (String w : words) if (text.contains(w.toLowerCase(Locale.ROOT))) return true;
        return false;
    }
}
