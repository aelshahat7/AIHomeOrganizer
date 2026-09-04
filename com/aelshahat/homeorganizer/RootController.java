package com.aelshahat.homeorganizer;

import java.io.BufferedReader;
import java.io.InputStreamReader;

/** Minimal root bridge. All privileged work stays in a short-lived su subprocess. */
public final class RootController {
    public static final class Result {
        public final int exitCode;
        public final String output;
        Result(int code, String text) { exitCode = code; output = text; }
        public boolean ok() { return exitCode == 0; }
    }

    public Result exec(String command) {
        Process p = null;
        try {
            p = new ProcessBuilder("su", "-c", command)
                    .redirectErrorStream(true).start();
            StringBuilder out = new StringBuilder();
            try (BufferedReader r = new BufferedReader(new InputStreamReader(p.getInputStream()))) {
                String line;
                while ((line = r.readLine()) != null) out.append(line).append('\n');
            }
            int code = p.waitFor();
            return new Result(code, out.toString().trim());
        } catch (Exception e) {
            return new Result(-1, e.getClass().getSimpleName() + ": " + e.getMessage());
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
