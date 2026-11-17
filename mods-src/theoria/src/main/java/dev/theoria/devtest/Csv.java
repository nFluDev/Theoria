package dev.theoria.devtest;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/** Minimal CSV reader for the GameTests: the single-source files under {@code data/} (RFC-4180 quoting), found through {@code theoria.dev.data}. */
final class Csv {
    private Csv() {}

    static Path dataDir() {
        return Path.of(System.getProperty("theoria.dev.data", "data/balance")).toAbsolutePath().getParent();
    }

    static List<Map<String, String>> read(String relative) {
        try {
            List<String> lines = Files.readAllLines(dataDir().resolve(relative));
            String[] header = lines.get(0).split(",", -1);
            List<Map<String, String>> rows = new ArrayList<>();
            for (String line : lines.subList(1, lines.size())) {
                if (line.isBlank()) continue;
                List<String> cells = new ArrayList<>();
                StringBuilder cell = new StringBuilder();
                boolean quoted = false;
                for (char c : line.toCharArray()) {
                    if (c == '"') quoted = !quoted;
                    else if (c == ',' && !quoted) { cells.add(cell.toString()); cell.setLength(0); }
                    else cell.append(c);
                }
                cells.add(cell.toString());
                Map<String, String> row = new HashMap<>();
                for (int i = 0; i < header.length; i++) row.put(header[i], i < cells.size() ? cells.get(i) : "");
                rows.add(row);
            }
            return rows;
        } catch (IOException e) {
            throw new IllegalStateException("cannot read " + relative, e);
        }
    }

    /** key → value of a two-column-plus file such as catalyst_rules.csv. */
    static Map<String, String> keyValue(String relative) {
        Map<String, String> out = new HashMap<>();
        for (Map<String, String> row : read(relative)) out.put(row.get("key"), row.get("value"));
        return out;
    }
}
