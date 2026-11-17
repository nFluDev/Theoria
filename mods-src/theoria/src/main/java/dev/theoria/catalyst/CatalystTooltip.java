package dev.theoria.catalyst;

import dev.theoria.catalyst.CatalystHosts.Host;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.ChatFormatting;
import net.minecraft.core.Holder;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.enchantment.Enchantment;

/**
 * All player-facing text of the catalyst system that is computed (tooltip lines, the Catalyst Forge preview) — pure functions over stacks, shared by the
 * client (screen, {@code ItemTooltipEvent}) and the GameTests, so what the player reads is what is tested. Wording lives in lang files
 * ({@code theoria.catalyst.*}); numbers come from {@link CatalystData}.
 */
public final class CatalystTooltip {
    private CatalystTooltip() {}

    // ------------------------------------------------------------------------------------------------ names and values
    public static Component tierName(int idx) {
        return Component.translatable("theoria.catalyst.tier." + idx);
    }

    public static Component name(CatalystData.Def def) {
        return Component.translatable(def.nameKey());
    }

    /** "Güç IV": the catalyst's short name — the item name without the word "catalyst" is not available, so the full item name is used. */
    public static Component slotName(CatalystData.Def def, int level) {
        return name(def).copy().append(" ").append(CatalystText.roman(level));
    }

    /** The amount a filled attribute slot gives, as the player reads it (percent for fractions/multipliers). */
    public static Component amount(CatalystAttributes.Entry entry) {
        boolean percent = entry.operation() == net.minecraft.world.entity.ai.attributes.AttributeModifier.Operation.ADD_MULTIPLIED_BASE
                || entry.def().id().equals("crit_chance") || entry.def().id().equals("life_steal") || entry.def().id().equals("steadfast");
        String text = percent ? CatalystText.number(entry.amount() * 100) + "%" : CatalystText.number(entry.amount());
        return Component.literal((entry.amount() >= 0 ? "+" : "−") + text.replaceFirst("^-", ""));
    }

    /** One filled slot as a line: name, level and — when a number says it — the value on this host. */
    public static Component slotLine(Host host, Catalysts.Slot slot) {
        CatalystData.Def def = slot.def();
        if (def == null) return Component.literal(slot.id().toString());
        net.minecraft.network.chat.MutableComponent line = slotName(def, slot.level()).copy();
        CatalystData.Table table = CatalystData.table();
        switch (def.kind()) {
            case ATTRIBUTE -> {
                List<CatalystAttributes.Entry> entries = CatalystAttributes.entries(host, new Catalysts(List.of(slot), 0));
                if (!entries.isEmpty()) line.append(Component.literal(" (")).append(amount(entries.get(0))).append(Component.literal(")"));
            }
            case ENCHANT, RANGED -> {
                String target = host.matching(def).isEmpty() ? "weapon" : host.matching(def).get(0);
                int tier = def.graded() ? slot.grade() : host.idx();
                double value = table.value(def.id(), target, tier, slot.level());
                if (!Double.isNaN(value)) line.append(Component.literal(" (" + CatalystText.number(value) + ")"));
            }
            case ERUDITION -> line.append(Component.literal(" (+" + Erudition.percent(slot.level()) + "% XP)"));
            default -> { }
        }
        if (def.graded()) line.append(Component.literal(" · ")).append(tierName(slot.grade()));
        return line;
    }

    // ------------------------------------------------------------------------------------------------ item tooltips
    /** Lines appended to a stack's tooltip: catalyst items, raw materials, and hosts (slots, Netherite counter, innate traits). */
    public static List<Component> forStack(ItemStack stack) {
        List<Component> out = new ArrayList<>();
        Item item = stack.getItem();
        if (item instanceof CatalystItem catalyst) {
            catalystItem(out, catalyst.def(), CatalystItem.levelOf(stack), CatalystItem.gradeOf(stack));
        } else if (item == CatalystItems.DUST.get() || item == CatalystItems.ESSENCE.get() || item == CatalystItems.CORE.get()) {
            out.add(Component.translatable("theoria.catalyst.tooltip.raw_material").withStyle(ChatFormatting.GRAY));
        }
        Host host = CatalystHosts.of(item);
        if (host != null) {
            Catalysts catalysts = Catalysts.of(stack);
            if (!catalysts.slots().isEmpty() || catalysts.netherite() > 0) {
                out.add(Component.translatable("theoria.catalyst.tooltip.slots", catalysts.slots().size(), CatalystRules.slotCount(host)).withStyle(ChatFormatting.GOLD));
                for (Catalysts.Slot slot : catalysts.slots()) out.add(Component.literal("  ").append(slotLine(host, slot)).withStyle(ChatFormatting.AQUA));
                if (catalysts.netherite() > 0) out.add(Component.literal("  ").append(Component.translatable("theoria.catalyst.tooltip.netherite", catalysts.netherite())).withStyle(ChatFormatting.GOLD));
            }
        }
        for (EffectiveLevels.Trait trait : EffectiveLevels.traitsOf(item)) {
            out.add(Component.translatable("theoria.catalyst.tooltip.innate", traitName(trait)).withStyle(ChatFormatting.DARK_PURPLE));
        }
        return out;
    }

    /** An innate trait's name and level; for every trait {@code level()} is the plain level (the P encoding happens only inside {@link EffectiveLevels}). */
    private static Component traitName(EffectiveLevels.Trait trait) {
        String key = "enchantment." + trait.key().identifier().getNamespace() + "." + trait.key().identifier().getPath();
        return Component.translatable(key).append(" ").append(CatalystText.roman(trait.level()));
    }

    private static void catalystItem(List<Component> out, CatalystData.Def def, int level, int grade) {
        CatalystData.Table table = CatalystData.table();
        if (def.kind() != CatalystData.Kind.ATTRIBUTE) {
            out.add(Component.translatable("theoria.catalyst.desc." + def.id()).withStyle(ChatFormatting.GRAY));
        }
        List<Component> hosts = new ArrayList<>();
        for (String target : orderedTargets(def)) hosts.add(Component.translatable("theoria.catalyst.host." + target));
        out.add(Component.translatable("theoria.catalyst.tooltip.fits", join(hosts)).withStyle(ChatFormatting.BLUE));
        if (def.minIdx() > 0) out.add(Component.translatable("theoria.catalyst.tooltip.min_tier", tierName(def.minIdx())).withStyle(ChatFormatting.BLUE));
        if (def.graded()) out.add(Component.translatable("theoria.catalyst.tooltip.grade", tierName(grade)).withStyle(ChatFormatting.YELLOW));
        CatalystData.Ladder cost = table.ladder().get(Math.min(level, table.ladder().size()) - 1);
        out.add(Component.translatable("theoria.catalyst.tooltip.forge_cost", costText(def, cost)).withStyle(ChatFormatting.DARK_GRAY));
        out.add(Component.translatable("theoria.catalyst.tooltip.applied_at").withStyle(ChatFormatting.DARK_GRAY));
    }

    private static List<String> orderedTargets(CatalystData.Def def) {
        List<String> out = new ArrayList<>();
        for (String t : List.of("weapon", "tool", "ranged", "head", "chest", "legs", "feet")) if (def.targets().contains(t)) out.add(t);
        return out;
    }

    private static Component join(List<Component> parts) {
        net.minecraft.network.chat.MutableComponent out = Component.empty();
        for (int i = 0; i < parts.size(); i++) {
            if (i > 0) out.append(Component.literal(", "));
            out.append(parts.get(i));
        }
        return out;
    }

    /** "10 toz, 3 × Alev Tozu, 2 öz": the forge cost of one level; zero counts are left out. */
    public static Component costText(CatalystData.Def def, CatalystData.Ladder cost) {
        List<Component> parts = new ArrayList<>();
        if (cost.dust() > 0) parts.add(Component.translatable("theoria.catalyst.cost.dust", cost.dust()));
        if (cost.signature() > 0) {
            Item signature = net.minecraft.core.registries.BuiltInRegistries.ITEM.getValue(net.minecraft.resources.Identifier.parse(def.signature()));
            parts.add(Component.translatable("theoria.catalyst.cost.signature", cost.signature(), signature.getName(new ItemStack(signature))));
        }
        if (cost.essence() > 0) parts.add(Component.translatable("theoria.catalyst.cost.essence", cost.essence()));
        if (cost.core() > 0) parts.add(Component.translatable("theoria.catalyst.cost.core", cost.core()));
        return join(parts);
    }

    /** Replaces a Netherite-boosted enchantment line {@code Sharpness V} by {@code Sharpness VI (+1)}; other lines stay. Called with the stack's tooltip list. */
    public static void boostEnchantLines(ItemStack stack, List<Component> lines) {
        Catalysts catalysts = Catalysts.of(stack);
        if (catalysts.netherite() <= 0) return;
        for (var entry : stack.getTagEnchantments().entrySet()) {
            Holder<Enchantment> holder = entry.getKey();
            int raw = entry.getIntValue();
            int bonus = CatalystRules.netheriteBonus(holder, raw, catalysts.netherite());
            if (bonus <= 0) continue;
            Component original = Enchantment.getFullname(holder, raw);
            int at = lines.indexOf(original);
            if (at >= 0) lines.set(at, Enchantment.getFullname(holder, raw + bonus).copy().append(Component.literal(" (+" + bonus + ")").withStyle(ChatFormatting.GOLD)));
        }
    }

    // ------------------------------------------------------------------------------------------------ forge preview
    /** Preview of what "Uygula"/"Üret" would do, for the forge screen: coloured lines; a problem is the only line. */
    public static List<Component> preview(ItemStack target, List<ItemStack> inputs) {
        List<Component> out = new ArrayList<>();
        if (target.isEmpty()) return forgePreview(inputs);
        CatalystRules.Plan plan = CatalystRules.plan(target, inputs);
        if (!plan.ok()) {
            boolean hint = plan.problem() == CatalystRules.Problem.NO_INPUT || plan.problem() == CatalystRules.Problem.NO_TARGET;
            out.add(Component.translatable(plan.problem().langKey(), plan.needed()).withStyle(hint ? ChatFormatting.GRAY : ChatFormatting.RED));
            return out;
        }
        Host host = CatalystHosts.of(target);
        for (CatalystRules.Change change : plan.changes()) {
            Catalysts.Slot now = plan.after().slots().get(change.slotIndex());
            Component line = host == null ? slotName(change.def(), change.newLevel()) : slotLine(host, now);
            if (change.oldLevel() == 0) {
                out.add(Component.translatable("theoria.catalyst.line.add", line).withStyle(ChatFormatting.GREEN));
            } else {
                out.add(Component.translatable("theoria.catalyst.line.upgrade", slotName(change.def(), change.oldLevel()), line).withStyle(ChatFormatting.GREEN));
            }
        }
        for (CatalystRules.EnchantGain gain : plan.gains()) {
            out.add(Component.translatable("theoria.catalyst.line.netherite", Enchantment.getFullname(gain.enchantment(), gain.before()),
                    Enchantment.getFullname(gain.enchantment(), gain.after())).withStyle(ChatFormatting.GOLD));
        }
        return out;
    }

    private static List<Component> forgePreview(List<ItemStack> inputs) {
        List<Component> out = new ArrayList<>();
        CatalystForging.Plan plan = CatalystForging.plan(inputs);
        if (plan.ok()) {
            Component name = CatalystItems.stack(plan.def(), plan.level(), plan.grade()).getHoverName();
            out.add(Component.translatable("theoria.catalyst.line.make", name).withStyle(ChatFormatting.GREEN));
            int next = plan.level() + 1;
            if (next <= CatalystData.table().levels()) {
                out.add(Component.translatable("theoria.catalyst.line.next", CatalystText.roman(next) + ": ").append(costText(plan.def(), CatalystData.table().ladder().get(next - 1))).withStyle(ChatFormatting.GRAY));
            }
            return out;
        }
        boolean hint = plan.problem() == CatalystForging.Problem.NO_INPUT;
        out.add(Component.translatable(plan.problem().langKey()).withStyle(hint ? ChatFormatting.GRAY : ChatFormatting.RED));
        if (plan.def() != null) { // the signature picked a catalyst: show what each level costs
            for (CatalystData.Ladder step : CatalystData.table().ladder()) {
                out.add(Component.translatable("theoria.catalyst.line.cost", CatalystText.roman(step.level()), costText(plan.def(), step)).withStyle(ChatFormatting.DARK_GRAY));
            }
        }
        return out;
    }

    /** For checks: the plain string of a component list. */
    public static List<String> plain(List<Component> lines) {
        List<String> out = new ArrayList<>();
        for (Component c : lines) out.add(c.getString());
        return out;
    }
}
