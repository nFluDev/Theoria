package dev.theoria.wings;

import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import net.minecraft.core.Registry;
import net.minecraft.core.RegistryAccess;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;

/**
 * The wing catalog is Fantastic Wings' own datapack registry {@code fantasticwings:wings} (ADR-002 d): read at run time from the
 * registry access of whichever side asks, so a datapack that adds a wing shows up with no change here. No compile dependency on
 * the mod — an absent mod simply means an empty catalog ("unavailable" in the screen, never a crash).
 * Pure (no client classes): the server validates ids with it, the screen lists them with it.
 */
public final class WingsCatalog {
    public static final ResourceKey<Registry<Object>> REGISTRY = ResourceKey.createRegistryKey(Identifier.parse("fantasticwings:wings"));
    /** Spelled out once; the lang files carry {@code theoria.wings.name.<namespace>.<path>} for the known wings. */
    private static final String NAME_KEY_PREFIX = "theoria.wings.name.";
    /** Longest id the payload carries — a namespaced id is far shorter, this only bounds hostile input. */
    public static final int MAX_ID_LENGTH = 64;

    private WingsCatalog() {}

    /** Every wing id the given registry access knows, ordered by path (stable card order). Empty when Fantastic Wings is absent. */
    public static List<Identifier> ids(RegistryAccess access) {
        return access.lookup(REGISTRY)
                .map(registry -> registry.keySet().stream().sorted(Comparator.comparing(Identifier::getPath)).toList())
                .orElse(List.of());
    }

    public static boolean contains(RegistryAccess access, Identifier id) {
        return access.lookup(REGISTRY).map(registry -> registry.containsKey(id)).orElse(false);
    }

    /** {@code null} for anything that is not a well-formed id (empty, too long, illegal characters). */
    public static Identifier parse(String raw) {
        if (raw == null || raw.isEmpty() || raw.length() > MAX_ID_LENGTH) return null;
        Identifier id = Identifier.tryParse(raw);
        return id == null || id.getPath().isEmpty() ? null : id; // "fantasticwings:" parses, but names nothing
    }

    /** Lang key of a wing's display name, e.g. {@code theoria.wings.name.fantasticwings.blue_butterfly}. */
    public static String nameKey(Identifier id) {
        return NAME_KEY_PREFIX + id.getNamespace() + "." + id.getPath().replace('/', '.');
    }

    /** Fallback for wings the lang files do not know: {@code blue_butterfly} → {@code Blue Butterfly}. */
    public static String prettyName(Identifier id) {
        StringBuilder out = new StringBuilder();
        for (String word : id.getPath().split("[_/]")) {
            if (word.isEmpty()) continue;
            if (!out.isEmpty()) out.append(' ');
            out.append(word.substring(0, 1).toUpperCase(Locale.ROOT)).append(word.substring(1));
        }
        return out.toString();
    }
}
