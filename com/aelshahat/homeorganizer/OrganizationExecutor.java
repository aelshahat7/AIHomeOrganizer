package com.aelshahat.homeorganizer;

import android.os.Handler;
import android.os.Looper;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;

/** Sequential, approval-gated executor for the crDroid Launcher3 drag/drop bridge. */
public final class OrganizationExecutor {
    public interface Callback { void onFinished(Report report); }
    public static final class Entry {
        public final String category, source, target, status, detail;
        Entry(String category, String source, String target, String status, String detail) {
            this.category=category; this.source=source; this.target=target; this.status=status; this.detail=detail;
        }
    }
    public static final class Report {
        public final List<Entry> entries = new ArrayList<>();
        public int success, skipped, failed, unverified;
    }

    private final HomeAccessibilityService service;
    private final Handler handler = new Handler(Looper.getMainLooper());
    private boolean running;

    public OrganizationExecutor(HomeAccessibilityService service) { this.service = service; }
    public boolean isRunning() { return running; }

    public void execute(final OrganizationPlan plan, final Callback callback) {
        final Report report = new Report();
        if (running) { report.entries.add(new Entry("", "", "", "FAILED", "Executor already running")); report.failed++; callback.onFinished(report); return; }
        if (service == null || plan == null || !plan.isApproved()) {
            report.entries.add(new Entry("", "", "", "SKIPPED", "Plan is missing or not approved")); report.skipped++; callback.onFinished(report); return;
        }
        running = true;
        service.appendDiagnostic("EXECUTION_START approved=true\n");
        List<Group> groups = buildGroups(plan);
        executeGroup(groups, 0, report, callback);
    }

    private static final class Group {
        final String category; final List<OrganizationPlan.Item> items;
        Group(String category, List<OrganizationPlan.Item> items) { this.category=category; this.items=items; }
    }

    private List<Group> buildGroups(OrganizationPlan plan) {
        List<Group> groups = new ArrayList<>();
        for (Map.Entry<String,List<OrganizationPlan.Item>> e : plan.grouped().entrySet()) {
            List<OrganizationPlan.Item> items = new ArrayList<>();
            for (OrganizationPlan.Item i : e.getValue()) if (!i.shortcut.hotseat && !"Needs Review".equalsIgnoreCase(i.category)) items.add(i);
            items.sort(Comparator.comparingDouble((OrganizationPlan.Item i) -> -i.confidence));
            groups.add(new Group(e.getKey(), items));
        }
        return groups;
    }

    private void executeGroup(List<Group> groups, int index, Report report, Callback callback) {
        if (index >= groups.size()) { finish(report, callback); return; }
        Group g = groups.get(index);
        if (g.items.size() < 2) {
            for (OrganizationPlan.Item i : g.items) { report.skipped++; report.entries.add(new Entry(g.category, i.shortcut.label, "", "SKIPPED", "Category has fewer than two items; no folder anchor available")); }
            executeGroup(groups, index + 1, report, callback); return;
        }
        OrganizationPlan.Item anchor = g.items.get(0);
        OrganizationPlan.Item seed = findSamePage(anchor, g.items);
        if (seed == null) {
            for (OrganizationPlan.Item i : g.items) { report.skipped++; report.entries.add(new Entry(g.category, i.shortcut.label, "", "SKIPPED", "No safe same-page pair available for folder creation")); }
            executeGroup(groups, index + 1, report, callback); return;
        }
        service.appendDiagnostic("OPERATION_START category=" + g.category + " source=" + seed.shortcut.label
                + " target=" + anchor.shortcut.label + " page=" + anchor.shortcut.pageIndex
                + " sourceCell=" + seed.shortcut.cellX + "," + seed.shortcut.cellY
                + " targetCell=" + anchor.shortcut.cellX + "," + anchor.shortcut.cellY
                + " sourceCenter=" + seed.shortcut.centerX + "," + seed.shortcut.centerY
                + " targetCenter=" + anchor.shortcut.centerX + "," + anchor.shortcut.centerY + "\n");
        service.createFolder(seed.shortcut, anchor.shortcut, (code, detail) -> {
            String status = "FOLDER_CREATED".equals(code) ? "SUCCESS" : (code.contains("VERIFICATION") ? "UNVERIFIED" : "FAILED");
            if ("SUCCESS".equals(status)) report.success++; else if ("UNVERIFIED".equals(status)) report.unverified++; else report.failed++;
            report.entries.add(new Entry(g.category, seed.shortcut.label, anchor.shortcut.label, status, detail));
            service.appendDiagnostic("OPERATION_RESULT category=" + g.category + " status=" + status + " detail=" + detail + "\n");
            if (!"SUCCESS".equals(status)) {
                for (OrganizationPlan.Item i : g.items) if (i != seed && i != anchor) { report.skipped++; report.entries.add(new Entry(g.category, i.shortcut.label, anchor.shortcut.label, "SKIPPED", "Folder state is not safely established")); }
                executeGroup(groups, index + 1, report, callback);
            } else {
                addRemainingSamePage(g, seed, anchor, 0, report, () -> executeGroup(groups, index + 1, report, callback));
            }
        });
    }

    private void addRemainingSamePage(Group g, OrganizationPlan.Item seed, OrganizationPlan.Item anchor, int pos, Report report, Runnable done) {
        if (pos >= g.items.size()) { done.run(); return; }
        OrganizationPlan.Item item = g.items.get(pos++);
        if (item == seed || item == anchor) { addRemainingSamePage(g, seed, anchor, pos, report, done); return; }
        if (item.shortcut.pageIndex != anchor.shortcut.pageIndex) {
            report.skipped++; report.entries.add(new Entry(g.category, item.shortcut.label, anchor.shortcut.label, "SKIPPED", "Different page; cross-page drag into an existing folder is intentionally not attempted"));
            addRemainingSamePage(g, seed, anchor, pos, report, done); return;
        }
        // The current Launcher3 Accessibility tree does not expose a stable public FolderInfo id.
        // Therefore this build only reuses a folder when the FolderIcon can be located and verified by the service.
        service.addShortcutToFolder(item.shortcut, anchor.shortcut.centerX, anchor.shortcut.centerY, result -> {
            String status = "FOLDER_ITEM_ADDED".equals(result) ? "SUCCESS" : ("FOLDER_ITEM_UNVERIFIED".equals(result) ? "UNVERIFIED" : "FAILED");
            if ("SUCCESS".equals(status)) report.success++; else if ("UNVERIFIED".equals(status)) report.unverified++; else report.failed++;
            report.entries.add(new Entry(g.category, item.shortcut.label, anchor.shortcut.label, status, "Reuse existing folder"));
            if (!"SUCCESS".equals(status)) {
                service.appendDiagnostic("FOLDER_REUSE_STOP category=" + g.category + " item=" + item.shortcut.label + " status=" + status + "\n");
                done.run();
            } else addRemainingSamePage(g, seed, anchor, pos, report, done);
        });
    }

    private OrganizationPlan.Item findSamePage(OrganizationPlan.Item anchor, List<OrganizationPlan.Item> items) {
        for (OrganizationPlan.Item i : items) if (i != anchor && i.shortcut.pageIndex == anchor.shortcut.pageIndex && !i.shortcut.hotseat) return i;
        return null;
    }

    private void finish(Report report, Callback callback) {
        running = false;
        service.appendDiagnostic("EXECUTION_COMPLETE success=" + report.success + " failed=" + report.failed
                + " skipped=" + report.skipped + " unverified=" + report.unverified + "\n");
        callback.onFinished(report);
    }
}
