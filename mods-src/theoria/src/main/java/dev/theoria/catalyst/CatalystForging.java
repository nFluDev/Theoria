package dev.theoria.catalyst;

import dev.theoria.gear.GearData;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.Identifier;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import org.jspecify.annotations.Nullable;

/**
 * Making a catalyst from raw materials, in the Catalyst Forge (the target slot empty). This replaces the "shapeless vanilla recipe" of ADR-006 Karar 11:
 * level III-V cost 15-39 items, which no 3×3 grid can hold (one item per ingredient slot), so the ladder is read from the stack COUNTS of the forge's five input
 * slots instead. The ladder, signature materials and forge item ids are data ({@link CatalystData}); the rule is: exactly one signature material in the
 * inputs picks the catalyst, the highest level whose four counts (dust, signature, essence, core) are all met is made, nothing else is consumed.
 * A graded catalyst (Menzil Gücü) also needs one ingot of the tier it should carry; the highest tier present wins.
 */
public final class CatalystForging {
    public enum Problem {
        NO_INPUT, NO_SIGNATURE, AMBIGUOUS_SIGNATURE, INSUFFICIENT, NO_GRADE_INGOT, FOREIGN_ITEM;

        public String langKey() {
            return "theoria.catalyst.forge.problem." + name().toLowerCase(java.util.Locale.ROOT);
        }
    }

    /** {@code consume[i]} = items taken from input {@code i}. On a problem {@code def} may still be set (for the cost preview). */
    public record Plan(@Nullable Problem problem, CatalystData.@Nullable Def def, int level, int grade, int[] consume, CatalystData.@Nullable Ladder missing) {
        public boolean ok() {
            return problem == null;
        }

        public ItemStack result() {
            if (!ok() || def == null) return ItemStack.EMPTY;
            return CatalystItems.stack(def, level, grade);
        }
    }

    private static volatile @Nullable Map<Item, Integer> ingotTiers;

    private CatalystForging() {}

    /** The catalyst whose signature material is {@code item}, or null. Netherite's signature is the ingot itself and is not forgeable. */
    public static CatalystData.@Nullable Def bySignature(Item item) {
        Identifier id = BuiltInRegistries.ITEM.getKey(item);
        for (CatalystData.Def def : CatalystData.table().defs().values()) {
            if (def.kind() != CatalystData.Kind.NETHERITE && def.signature().equals(id.toString())) return def;
        }
        return null;
    }

    public static Item forgeItem(String kind) {
        return BuiltInRegistries.ITEM.getValue(Identifier.parse(CatalystData.table().forge().get(kind)));
    }

    /** Material tier an ingot/gem stands for (the gear table's {@code ingredient}), or -1. */
    public static int ingotTier(Item item) {
        Map<Item, Integer> map = ingotTiers;
        if (map == null) {
            Map<Item, Integer> built = new HashMap<>();
            for (GearData.Material m : GearData.table().materials()) {
                if (m.ingredient().isEmpty()) continue;
                Item ingredient = BuiltInRegistries.ITEM.getValue(Identifier.parse(m.ingredient()));
                built.merge(ingredient, m.idx(), Math::max);
            }
            ingotTiers = map = built;
        }
        return map.getOrDefault(item, -1);
    }

    public static Plan plan(List<ItemStack> inputs) {
        int[] consume = new int[inputs.size()];
        Item dustItem = forgeItem("dust");
        Item essenceItem = forgeItem("essence");
        Item coreItem = forgeItem("core");
        int dust = 0;
        int essence = 0;
        int core = 0;
        Map<Item, Integer> signatures = new LinkedHashMap<>();
        int bestIngot = -1;
        boolean any = false;
        for (ItemStack stack : inputs) {
            if (stack.isEmpty()) continue;
            any = true;
            Item item = stack.getItem();
            if (item == dustItem) dust += stack.getCount();
            else if (item == essenceItem) essence += stack.getCount();
            else if (item == coreItem) core += stack.getCount();
            else if (bySignature(item) != null) signatures.merge(item, stack.getCount(), Integer::sum);
            else if (ingotTier(item) >= 0) bestIngot = Math.max(bestIngot, ingotTier(item));
            else return new Plan(Problem.FOREIGN_ITEM, null, 0, 0, consume, null);
        }
        if (!any) return new Plan(Problem.NO_INPUT, null, 0, 0, consume, null);
        // A signature material that is ALSO a gear ingot never exists (checked in tests), so ingots only matter to the graded catalyst.
        if (signatures.isEmpty()) return new Plan(Problem.NO_SIGNATURE, null, 0, 0, consume, null);
        if (signatures.size() > 1) return new Plan(Problem.AMBIGUOUS_SIGNATURE, null, 0, 0, consume, null);
        Map.Entry<Item, Integer> signature = signatures.entrySet().iterator().next();
        CatalystData.Def def = bySignature(signature.getKey());
        CatalystData.Ladder reached = null;
        for (CatalystData.Ladder step : CatalystData.table().ladder()) {
            if (dust >= step.dust() && signature.getValue() >= step.signature() && essence >= step.essence() && core >= step.core()) reached = step;
        }
        if (reached == null) return new Plan(Problem.INSUFFICIENT, def, 0, 0, consume, CatalystData.table().ladder().get(0));
        if (def.graded() && bestIngot < 0) return new Plan(Problem.NO_GRADE_INGOT, def, reached.level(), 0, consume, null);
        int grade = def.graded() ? bestIngot : 0;
        int dustLeft = reached.dust();
        int signatureLeft = reached.signature();
        int essenceLeft = reached.essence();
        int coreLeft = reached.core();
        boolean ingotTaken = !def.graded();
        for (int i = 0; i < inputs.size(); i++) {
            ItemStack stack = inputs.get(i);
            if (stack.isEmpty()) continue;
            Item item = stack.getItem();
            int take = 0;
            if (item == dustItem) {
                take = Math.min(stack.getCount(), dustLeft);
                dustLeft -= take;
            } else if (item == essenceItem) {
                take = Math.min(stack.getCount(), essenceLeft);
                essenceLeft -= take;
            } else if (item == coreItem) {
                take = Math.min(stack.getCount(), coreLeft);
                coreLeft -= take;
            } else if (item == signature.getKey()) {
                take = Math.min(stack.getCount(), signatureLeft);
                signatureLeft -= take;
            } else if (!ingotTaken && ingotTier(item) == bestIngot) {
                take = 1;
                ingotTaken = true;
            }
            consume[i] = take;
        }
        return new Plan(null, def, reached.level(), grade, consume, null);
    }

    /** Commits a forge plan: takes the materials and returns the catalyst (the caller puts it in the target slot). Re-plans; empty stack on any problem. */
    public static ItemStack forge(List<ItemStack> inputs) {
        Plan plan = plan(inputs);
        if (!plan.ok()) return ItemStack.EMPTY;
        for (int i = 0; i < inputs.size(); i++) if (plan.consume()[i] > 0) inputs.get(i).shrink(plan.consume()[i]);
        return plan.result();
    }
}
