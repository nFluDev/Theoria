package dev.theoria.balance;

import dev.theoria.Theoria;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import net.minecraft.resources.FileToIdConverter;
import net.minecraft.resources.Identifier;
import net.minecraft.server.packs.resources.ResourceManager;
import net.minecraft.server.packs.resources.SimpleJsonResourceReloadListener;
import net.minecraft.server.packs.resources.SimplePreparableReloadListener;
import net.minecraft.util.profiling.ProfilerFiller;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.AddServerReloadListenersEvent;
import org.jspecify.annotations.Nullable;

/**
 * Server-side balance data, loaded from the datapack on every (re)load (docs/04 §3):
 * {@code data/theoria/balance/combat/default.json} and {@code data/theoria/balance/tier/<id>.json}.
 * A datapack may override either; a broken or incomplete set fails the reload loudly instead of silently running
 * with wrong numbers. The snapshot is replaced atomically, so reading never sees a half-loaded state.
 */
public final class TierRegistry extends SimplePreparableReloadListener<TierRegistry.Snapshot> {
    public static final Identifier ID = Theoria.id("balance");
    private static final FileToIdConverter COMBAT_FILES = FileToIdConverter.json("balance/combat");
    private static final FileToIdConverter TIER_FILES = FileToIdConverter.json("balance/tier");
    private static final Identifier COMBAT_DEFAULT = Theoria.id("default");

    /** Immutable result of one load; {@code tiers} is ordered by idx and gap-free (tiers.get(i).idx() == i). */
    public record Snapshot(CombatParams combat, List<Tier> tiers) {}

    private static volatile @Nullable Snapshot current;

    private TierRegistry() {}

    public static void init() {
        NeoForge.EVENT_BUS.addListener((AddServerReloadListenersEvent event) -> event.addListener(ID, new TierRegistry()));
    }

    public static boolean loaded() {
        return current != null;
    }

    /** The loaded data, or null before the first load (gameplay hooks then leave vanilla behaviour alone). */
    public static @Nullable Snapshot snapshot() {
        return current;
    }

    public static CombatParams combat() {
        return loadedSnapshot().combat();
    }

    public static int tierCount() {
        return loadedSnapshot().tiers().size();
    }

    /** @throws IllegalArgumentException if {@code idx} is not a tier */
    public static Tier tier(int idx) {
        List<Tier> tiers = loadedSnapshot().tiers();
        if (idx < 0 || idx >= tiers.size()) {
            throw new IllegalArgumentException("Unknown tier " + idx + " (valid: 0.." + (tiers.size() - 1) + ")");
        }
        return tiers.get(idx);
    }

    public static @Nullable Tier tierOrNull(int idx) {
        Snapshot snapshot = current;
        return snapshot == null || idx < 0 || idx >= snapshot.tiers().size() ? null : snapshot.tiers().get(idx);
    }

    private static Snapshot loadedSnapshot() {
        Snapshot snapshot = current;
        if (snapshot == null) throw new IllegalStateException("Theoria balance data is not loaded yet");
        return snapshot;
    }

    @Override
    protected Snapshot prepare(ResourceManager manager, ProfilerFiller profiler) {
        Map<Identifier, CombatParams> combat = new HashMap<>();
        SimpleJsonResourceReloadListener.scanDirectory(manager, COMBAT_FILES, makeConditionalOps(), CombatParams.CODEC, combat);
        Map<Identifier, Tier> found = new HashMap<>();
        SimpleJsonResourceReloadListener.scanDirectory(manager, TIER_FILES, makeConditionalOps(), Tier.CODEC, found);

        CombatParams params = combat.get(COMBAT_DEFAULT);
        if (params == null) throw new IllegalStateException("Missing or invalid " + COMBAT_FILES.idToFile(COMBAT_DEFAULT));
        if (found.isEmpty()) throw new IllegalStateException("No valid tier files under data/" + Theoria.MODID + "/" + TIER_FILES.prefix());
        List<Tier> tiers = new ArrayList<>(found.values());
        tiers.sort((a, b) -> Integer.compare(a.idx(), b.idx()));
        for (int i = 0; i < tiers.size(); i++) {
            if (tiers.get(i).idx() != i) {
                throw new IllegalStateException("Tier idx must run 0.." + (tiers.size() - 1) + " without gaps or duplicates; "
                        + "found '" + tiers.get(i).id() + "' with idx " + tiers.get(i).idx() + " at position " + i);
            }
        }
        return new Snapshot(params, List.copyOf(tiers));
    }

    @Override
    protected void apply(Snapshot snapshot, ResourceManager manager, ProfilerFiller profiler) {
        current = snapshot;
        Theoria.LOGGER.info("Theoria balance data loaded: {} tiers, armor k1={} k2={} cap={}",
                snapshot.tiers().size(), snapshot.combat().armorK1(), snapshot.combat().armorK2(), snapshot.combat().armorCap());
    }
}
