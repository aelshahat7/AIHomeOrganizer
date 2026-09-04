package com.aelshahat.homeorganizer;

/** Root-only bridge for talking to Launcher3's protected Provider. */
public final class RootLauncherController {
    private final RootController root;
    public RootLauncherController(RootController root) { this.root = root; }

    public RootController.Result probe() {
        return root.exec("content call --uri content://com.android.launcher3.settings --method EXPORT_LAYOUT_XML");
    }

    public RootController.Result forceStopAndRestartLauncher() {
        return root.exec("am force-stop com.android.launcher3; monkey -p com.android.launcher3 1");
    }
}
