package dev.theoria.catalyst;

import dev.theoria.catalyst.CatalystHosts.Host;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.core.Holder;
import net.minecraft.core.component.DataComponents;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.enchantment.Enchantment;
import org.jspecify.annotations.Nullable;

/**
 * The rules of the Catalyst Forge (ADR-006 Karar 4, 7, 12) as pure functions over stacks — no menu, no player, no level: the menu, the screen's preview
 * and the GameTests all run exactly this. {@link #plan} decides, {@link #apply} commits atomically (it re-plans; nothing is trusted from a caller).
 * Every number comes from {@link CatalystData}.
 */
public final class CatalystRules {
    /** Why an application is refused. The lang key {@code theoria.catalyst.problem.<name>} explains it to the player. */
    public enum Problem {
        NO_TARGET, NOT_HOST, NO_INPUT, NOT_A_CATALYST, WRONG_CLASS, TIER_TOO_LOW, NO_SLOT, NOT_BETTER,
        NETHERITE_NO_ENCHANT, NETHERITE_NO_GAIN, NETHERITE_FULL, NETHERITE_INGOTS;

        public String langKey() {
            return "theoria.catalyst.problem." + name().toLowerCase(java.util.Locale.ROOT);
        }
    }

    /** One change a plan makes to the target, for the preview. {@code oldLevel} 0 = newly added. */
    public record Change(CatalystData.Def def, int oldLevel, int newLevel, int grade, int slotIndex) {}

    /** A Netherite gain on one real enchantment: effective level before and after the application. */
    public record EnchantGain(Holder<Enchantment> enchantment, int rawLevel, int before, int after) {}

    /**
     * Result of {@link #plan}. {@code problem == null} means the application can be committed; then {@code after} is the new component,
     * {@code consume[i]} how many items input {@code i} loses. {@code problemInput} is the input that caused the problem (-1 = the target itself).
     */
    public record Plan(@Nullable Problem problem, int problemInput, int needed, Catalysts after, List<Change> changes, List<EnchantGain> gains, int[] consume) {
        public boolean ok() {
            return problem == null;
        }
    }

    private CatalystRules() {}

    // ------------------------------------------------------------------------------------------------ slots
    /** Catalyst slots of a host: {@code min(max, base + ⌊idx/step⌋)} plus the Platinum bonus; tierless hosts have a fixed count. */
    public static int slotCount(Host host) {
        CatalystData.Table t = CatalystData.table();
        if (host.tierless()) return t.tierlessSlots();
        return Math.min(t.slotMax(), t.slotBase() + host.idx() / t.slotStep()) + (host.idx() >= t.platinumIdx() ? t.platinumBonus() : 0);
    }

    /** Why {@code def} cannot sit on {@code host}, or null when it can (class, minimum tier). Slot availability is a separate question. */
    public static @Nullable Problem fits(Host host, CatalystData.Def def) {
        if (!host.accepts(def)) return Problem.WRONG_CLASS;
        if (!host.tierless() && host.idx() < def.minIdx()) return Problem.TIER_TOO_LOW;
        return null;
    }

    // ------------------------------------------------------------------------------------------------ netherite
    /** Netherite's per-enchantment ceiling (ADR-006 Karar 7): the table, else a cautious default for enchantments of other mods. */
    public static int netheriteCap(Holder<Enchantment> enchantment) {
        CatalystData.Table t = CatalystData.table();
        Integer cap = t.enchantCaps().get(enchantment.getRegisteredName());
        if (cap != null) return cap;
        return enchantment.value().getMaxLevel() >= 2 ? t.netheriteDefaultCap() : 0;
    }

    /** Bonus levels Netherite adds to a REAL enchantment at {@code rawLevel}: {@code min(applications, cap, 255 − raw)}, and only when raw ≥ 1 (ADR-004 Karar 3). */
    public static int netheriteBonus(Holder<Enchantment> enchantment, int rawLevel, int applications) {
        if (applications <= 0 || rawLevel < 1) return 0;
        int ceiling = CatalystData.table().enchantLevelCap() - rawLevel;
        return Math.max(0, Math.min(applications, Math.min(netheriteCap(enchantment), ceiling)));
    }

    /** Ingots the next Netherite application costs, or -1 when the item is at its maximum. */
    public static int netheriteCost(int applications) {
        int[] cost = CatalystData.table().netheriteCost();
        return applications < 0 || applications >= cost.length || applications >= CatalystData.table().netheriteMax() ? -1 : cost[applications];
    }

    // ------------------------------------------------------------------------------------------------ plan
    public static Plan plan(ItemStack target, List<ItemStack> inputs) {
        Catalysts empty = Catalysts.EMPTY;
        int[] consume = new int[inputs.size()];
        if (target.isEmpty()) return failed(Problem.NO_TARGET, -1, 0, empty, consume);
        Host host = CatalystHosts.of(target);
        Catalysts current = Catalysts.of(target);
        if (host == null) return failed(Problem.NOT_HOST, -1, 0, current, consume);
        boolean any = inputs.stream().anyMatch(s -> !s.isEmpty());
        if (!any) return failed(Problem.NO_INPUT, -1, 0, current, consume);

        List<Change> changes = new ArrayList<>();
        List<EnchantGain> gains = new ArrayList<>();
        Catalysts working = current;
        int slotLimit = slotCount(host);
        for (int i = 0; i < inputs.size(); i++) {
            ItemStack input = inputs.get(i);
            if (input.isEmpty()) continue;
            if (input.is(Items.NETHERITE_INGOT)) {
                int cost = netheriteCost(working.netherite());
                if (cost < 0) return failed(Problem.NETHERITE_FULL, i, 0, working, consume);
                var raw = target.getTagEnchantments();
                if (raw.isEmpty()) return failed(Problem.NETHERITE_NO_ENCHANT, i, 0, working, consume);
                List<EnchantGain> step = new ArrayList<>();
                for (var entry : raw.entrySet()) {
                    Holder<Enchantment> ench = entry.getKey();
                    int level = entry.getIntValue();
                    int before = netheriteBonus(ench, level, working.netherite());
                    int after = netheriteBonus(ench, level, working.netherite() + 1);
                    if (after > before) step.add(new EnchantGain(ench, level, level + before, level + after));
                }
                if (step.isEmpty()) return failed(Problem.NETHERITE_NO_GAIN, i, 0, working, consume);
                if (input.getCount() < cost) return failed(Problem.NETHERITE_INGOTS, i, cost, working, consume);
                gains.addAll(step);
                working = working.withNetherite(working.netherite() + 1);
                consume[i] = cost;
                continue;
            }
            if (!(input.getItem() instanceof CatalystItem item)) return failed(Problem.NOT_A_CATALYST, i, 0, working, consume);
            CatalystData.Def def = item.def();
            int level = input.getOrDefault(CatalystComponents.LEVEL.get(), 1);
            int grade = def.graded() ? input.getOrDefault(CatalystComponents.GRADE.get(), 0) : 0;
            Problem misfit = fits(host, def);
            if (misfit != null) return failed(misfit, i, 0, working, consume);
            int existing = working.indexOf(def.identifier());
            if (existing >= 0) {
                Catalysts.Slot old = working.slots().get(existing);
                boolean better = level > old.level() || (level == old.level() && grade > old.grade());
                if (!better) return failed(Problem.NOT_BETTER, i, 0, working, consume);
                working = working.withSlot(existing, new Catalysts.Slot(def.identifier(), level, grade));
                changes.add(new Change(def, old.level(), level, grade, existing));
            } else {
                if (working.slots().size() >= slotLimit) return failed(Problem.NO_SLOT, i, slotLimit, working, consume);
                working = working.withSlot(working.slots().size(), new Catalysts.Slot(def.identifier(), level, grade));
                changes.add(new Change(def, 0, level, grade, working.slots().size() - 1));
            }
            consume[i] = 1;
        }
        return new Plan(null, -1, 0, working, List.copyOf(changes), List.copyOf(gains), consume);
    }

    private static Plan failed(Problem problem, int input, int needed, Catalysts after, int[] consume) {
        return new Plan(problem, input, needed, after, List.of(), List.of(), consume);
    }

    // ------------------------------------------------------------------------------------------------ commit
    /**
     * Commits an application: re-plans, then writes the component, keeps the glint flag, takes the materials. All or nothing — on any problem nothing changes.
     * @return the plan that was committed (check {@link Plan#ok()})
     */
    public static Plan apply(ItemStack target, List<ItemStack> inputs) {
        Plan plan = plan(target, inputs);
        if (!plan.ok()) return plan;
        write(target, plan.after());
        for (int i = 0; i < inputs.size(); i++) if (plan.consume()[i] > 0) inputs.get(i).shrink(plan.consume()[i]);
        return plan;
    }

    /** Takes a catalyst out of a slot without refund (ADR-006 Karar 12, Q45). */
    public static boolean remove(ItemStack target, int slotIndex) {
        Catalysts current = Catalysts.of(target);
        if (slotIndex < 0 || slotIndex >= current.slots().size()) return false;
        write(target, current.withoutSlot(slotIndex));
        return true;
    }

    /** Stores the component (or drops it when empty) and keeps the enchantment-glint flag in step: catalysts glow, innate traits and Netherite counters alone do not. */
    static void write(ItemStack target, Catalysts value) {
        if (value.isEmpty()) {
            target.remove(CatalystComponents.CATALYSTS.get());
        } else {
            target.set(CatalystComponents.CATALYSTS.get(), value);
        }
        if (value.slots().isEmpty()) target.remove(DataComponents.ENCHANTMENT_GLINT_OVERRIDE);
        else target.set(DataComponents.ENCHANTMENT_GLINT_OVERRIDE, true);
    }

    // ------------------------------------------------------------------------------------------------ helpers shared by listeners
    /** Level of a virtual enchantment encoded as P = 1 + 5·g + (L − 1) (ADR-006 Karar 6). */
    public static int encode(int tier, int level) {
        return 1 + 5 * tier + (level - 1);
    }

    public static int decodeLevel(int p) {
        return (p - 1) % 5 + 1;
    }

    public static int decodeTier(int p) {
        return (p - 1) / 5;
    }
}
