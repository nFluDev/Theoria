package dev.theoria.core.check;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import net.minecraft.commands.CommandSourceStack;

/**
 * In-game self checks behind {@code /theoria test <area>}. They must be safe to run in a real survival world:
 * read state, or restore whatever they touch. Destructive scenarios belong in GameTests instead.
 */
public final class Checks {
    /** Returns a short detail line on success; throws (see {@link #require}) on failure. */
    @FunctionalInterface
    public interface Check {
        String run(CommandSourceStack source) throws Exception;
    }

    public record Result(String name, boolean ok, String detail) {}

    private static final Map<String, Map<String, Check>> AREAS = new LinkedHashMap<>();

    private Checks() {}

    public static void register(String area, String name, Check check) {
        if (AREAS.computeIfAbsent(area, a -> new LinkedHashMap<>()).put(name, check) != null) {
            throw new IllegalStateException("Duplicate check " + area + "/" + name);
        }
    }

    public static Set<String> areas() {
        return AREAS.keySet();
    }

    /** The check names of an area, in registration order (empty for an unknown area): {@code /theoria test <area> <name>} runs one of them. */
    public static Set<String> names(String area) {
        Map<String, Check> checks = AREAS.get(area);
        return checks == null ? Set.of() : checks.keySet();
    }

    /** Runs one area, or every area when {@code area} is null. Unknown area → empty list. */
    public static List<Result> run(String area, CommandSourceStack source) {
        return run(area, null, source);
    }

    /** Like {@link #run(String, CommandSourceStack)} but only the check called {@code name} of the area when {@code name} is not null (Phase 12: {@code /theoria test relic <id>}). */
    public static List<Result> run(String area, String name, CommandSourceStack source) {
        List<Result> results = new ArrayList<>();
        for (var entry : AREAS.entrySet()) {
            if (area != null && !area.equals(entry.getKey())) continue;
            entry.getValue().forEach((checkName, check) -> {
                if (name == null || name.equals(checkName)) results.add(runOne(entry.getKey() + "/" + checkName, check, source));
            });
        }
        return results;
    }

    public static void require(boolean condition, String message) {
        if (!condition) throw new IllegalStateException(message);
    }

    private static Result runOne(String name, Check check, CommandSourceStack source) {
        try {
            return new Result(name, true, check.run(source));
        } catch (Exception e) {
            String message = e.getMessage() == null ? e.getClass().getSimpleName() : e.getMessage();
            return new Result(name, false, message);
        }
    }
}
