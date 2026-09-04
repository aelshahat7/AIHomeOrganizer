package com.aelshahat.homeorganizer;

import android.content.Context;
import android.content.pm.ApplicationInfo;
import android.content.pm.LauncherActivityInfo;
import android.content.pm.LauncherApps;
import android.os.Process;
import android.os.UserHandle;

import java.text.Normalizer;
import java.util.List;
import java.util.Locale;

/** Resolves a Home Screen label to the real launchable application metadata on this device. */
public final class AppMetadataResolver {
    public static final class Metadata {
        public final String packageName;
        public final String label;
        public final int applicationCategory;
        public final boolean exactLabel;
        public final boolean unique;

        Metadata(String packageName, String label, int applicationCategory,
                 boolean exactLabel, boolean unique) {
            this.packageName = packageName;
            this.label = label;
            this.applicationCategory = applicationCategory;
            this.exactLabel = exactLabel;
            this.unique = unique;
        }
    }

    private final Context context;
    private final LauncherApps launcherApps;

    public AppMetadataResolver(Context context) {
        this.context = context.getApplicationContext();
        this.launcherApps = (LauncherApps) this.context.getSystemService(Context.LAUNCHER_APPS_SERVICE);
    }

    public Metadata resolve(String homeLabel) {
        String wanted = normalize(homeLabel);
        if (wanted.isEmpty() || launcherApps == null) return null;

        List<LauncherActivityInfo> activities;
        try {
            // UserHandle.myUserHandle() is not a public API on the Android SDK we compile against.
            // Process.myUserHandle() is the public API and returns this process' user profile.
            UserHandle currentUser = Process.myUserHandle();
            activities = launcherApps.getActivityList(null, currentUser);
        } catch (Throwable ignored) {
            return null;
        }

        LauncherActivityInfo best = null;
        int bestScore = 0;
        int exactCount = 0;
        for (LauncherActivityInfo info : activities) {
            if (info == null || info.getApplicationInfo() == null) continue;
            String label = String.valueOf(info.getLabel());
            String normalized = normalize(label);
            if (normalized.equals(wanted)) exactCount++;
            int score = labelScore(wanted, normalized, info.getComponentName().getPackageName());
            if (score > bestScore) {
                bestScore = score;
                best = info;
            }
        }

        if (best == null || bestScore < 80) return null;
        ApplicationInfo ai = best.getApplicationInfo();
        return new Metadata(best.getComponentName().getPackageName(),
                String.valueOf(best.getLabel()), ai.category,
                normalize(String.valueOf(best.getLabel())).equals(wanted), exactCount == 1);
    }

    private int labelScore(String wanted, String label, String pkg) {
        if (wanted.equals(label)) return 100;
        if (label.contains(wanted) || wanted.contains(label)) return 88;
        String p = normalize(pkg);
        if (p.contains(wanted) && wanted.length() >= 4) return 82;
        return 0;
    }

    public static String normalize(String value) {
        if (value == null) return "";
        String s = Normalizer.normalize(value, Normalizer.Form.NFKC)
                .toLowerCase(Locale.ROOT)
                .replace("\u200b", "")
                .replace("\u200c", "")
                .replace("\u200d", "")
                .replace("\ufeff", "")
                .replaceAll("[\\p{M}]", "")
                .replaceAll("[^\\p{L}\\p{N}]+", " ")
                .trim();
        return s.replaceAll("\\s+", " ");
    }
}
