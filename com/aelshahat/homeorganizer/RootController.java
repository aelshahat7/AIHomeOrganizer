package com.aelshahat.homeorganizer;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.util.ArrayList;
import java.util.List;

/** Root bridge. Supports modern Magisk installations where su is under /debug_ramdisk. */
public final class RootController {
    private static final String[] SU_CANDIDATES = new String[] {
            "/debug_ramdisk/su",
            "/system_ext/bin/su",
            "/system/bin/su",
            "/system/xbin/su",
            "/product/bin/su",
            "/sbin/su"
    };

    public static final class Result {
        public final int exitCode;
        public final String output;
        public final String suPath;

        Result(int code, String text, String path) {
            exitCode = code;
            output = text;
            suPath = path;
        }

        public boolean ok() { return exitCode == 0; }
    }

    private Result run(String suPath, String command) {
        Process p = null;
        try {
            p = new ProcessBuilder(suPath, "-c", command)
                    .redirectErrorStream(true).start();
            StringBuilder out = new StringBuilder();
            try (BufferedReader r = new BufferedReader(new InputStreamReader(p.getInputStream()))) {
                String line;
                while ((line = r.readLine()) != null) out.append(line).append('\n');
            }
            int code = p.waitFor();
            return new Result(code, out.toString().trim(), suPath);
        } catch (Exception e) {
            return new Result(-1, e.getClass().getSimpleName() + ": " + e.getMessage(), suPath);
        } finally {
            if (p != null) p.destroy();
        }
    }

    /** Try known Android/Magisk locations instead of relying on the app's PATH. */
    public Result exec(String command) {
        List<String> errors = new ArrayList<>();
        for (String path : SU_CANDIDATES) {
            Result r = run(path, command);
            if (r.ok()) return r;
            errors.add(path + " => " + r.output);
            // A real su process that returned a non-zero status means the binary ran.
            // Do not hide a useful Magisk denial/policy result by trying another binary.
            if (r.exitCode >= 0) return r;
        }
        return new Result(-1, "No executable su path worked: " + String.join(" | ", errors), "");
    }

    /** Non-root diagnostic. It helps identify which su locations are present/executable. */
    public Result diagnoseSuPaths() {
        StringBuilder cmd = new StringBuilder("echo __SU_PATH_DIAGNOSTIC__; ");
        for (String path : SU_CANDIDATES) {
            cmd.append("if [ -e '").append(path).append("' ]; then ls -l '").append(path)
                    .append("'; else echo MISSING '").append(path).append("'; fi; ");
        }
        // This diagnostic itself is intentionally run without su by using the Android shell.
        Process p = null;
        try {
            p = new ProcessBuilder("/system/bin/sh", "-c", cmd.toString())
                    .redirectErrorStream(true).start();
            StringBuilder out = new StringBuilder();
            try (BufferedReader r = new BufferedReader(new InputStreamReader(p.getInputStream()))) {
                String line;
                while ((line = r.readLine()) != null) out.append(line).append('\n');
            }
            int code = p.waitFor();
            return new Result(code, out.toString().trim(), "/system/bin/sh");
        } catch (Exception e) {
            return new Result(-1, e.getClass().getSimpleName() + ": " + e.getMessage(), "/system/bin/sh");
        } finally {
            if (p != null) p.destroy();
        }
    }

    public Result test() {
        return exec("id; echo __ROOT_OK__");
    }

    /** Export the real Launcher3 workspace through its own protected provider, as root. */
    public Result exportLauncherLayout() {
        return exec("content call --uri content://com.android.launcher3.settings --method EXPORT_LAYOUT_XML");
    }
}
