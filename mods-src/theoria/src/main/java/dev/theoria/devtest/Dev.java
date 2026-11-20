package dev.theoria.devtest;

import dev.theoria.Theoria;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * Development-only test harness. The whole {@code devtest} package is inert unless the JVM is started with
 * {@code -Dtheoria.dev=true} (the Gradle run configs do; a packaged instance does not).
 * Output: lines are logged with a "[DEV name]" prefix and written to ${theoria.dev.out}/name.txt.
 */
public final class Dev {
    public static final boolean ENABLED = Boolean.getBoolean("theoria.dev");
    /** Client scenario: "smoke" (skeleton end-to-end), "fps" (benchmark) or "hud" (Phase 3 HUD) or "wings" (Phase 4) or "catalyst" (Phase 7b); empty = none. */
    public static final String CLIENT_MODE = System.getProperty("theoria.dev.client", "");

    private Dev() {}

    public static Path outDir() {
        return Path.of(System.getProperty("theoria.dev.out", "dev-out"));
    }

    /** Collects result lines for one test and writes them to a file. */
    public static final class Report {
        private final String name;
        private final List<String> lines = new ArrayList<>();

        public Report(String name) {
            this.name = name;
        }

        public Report line(String fmt, Object... args) {
            String s = args.length == 0 ? fmt : String.format(Locale.ROOT, fmt, args);
            lines.add(s);
            Theoria.LOGGER.info("[DEV {}] {}", name, s);
            return this;
        }

        public void write() {
            write(name + ".txt", lines);
        }

        public static void write(String file, List<String> content) {
            try {
                Path target = outDir().resolve(file);
                Files.createDirectories(target.getParent());   // `world/climate_<seed>.csv` etc.
                Files.write(target, content);
            } catch (IOException e) {
                Theoria.LOGGER.error("[DEV] cannot write {}", file, e);
            }
        }
    }
}
