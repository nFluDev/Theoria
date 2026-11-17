package dev.theoria.catalyst;

import com.google.gson.JsonElement;
import com.mojang.serialization.JsonOps;
import dev.theoria.core.check.Checks;
import io.netty.buffer.Unpooled;
import java.util.List;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.RegistryOps;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

/**
 * {@code /theoria test catalysts}: safe in a live world (reads tables and registries, builds throw-away stacks, changes nothing).
 * The per-feature scenarios that need entities, menus and loot live in the GameTests ({@code catalyst_*}); these are the cheap invariants.
 */
final class CatalystChecks {
    private CatalystChecks() {}

    static void init() {
        Checks.register("catalysts", "table", s -> {
            CatalystData.Table t = CatalystData.table();
            Checks.require(t.defs().size() == 32, "expected 32 catalysts, table has " + t.defs().size());
            Checks.require(t.defs().containsKey("netherite"), "Netherite is missing from the table");
            for (CatalystData.Def def : t.defs().values()) {
                if (def.kind() == CatalystData.Kind.NETHERITE) continue;
                Checks.require(CatalystItems.CATALYSTS.containsKey(def.id()) && CatalystItems.CATALYSTS.get(def.id()).isBound(), "catalyst item not registered: " + def.id());
            }
            Checks.require(CatalystItems.FORGE.isBound() && CatalystItems.MENU.isBound(), "forge block or menu not registered");
            return t.defs().size() + " catalysts, " + CatalystItems.CATALYSTS.size() + " items (Netherite is the vanilla ingot), forge + menu registered";
        });
        Checks.register("catalysts", "hosts", s -> {
            int armor = 0, weapons = 0, tools = 0, ranged = 0;
            boolean[] tiers = new boolean[14];
            for (var host : CatalystHosts.all().values()) {
                if (host.armor() != null) armor++;
                if (host.weapon()) weapons++;
                if (host.tool()) tools++;
                if (host.tierless()) ranged++;
                else tiers[host.idx()] = true;
            }
            for (int i = 0; i < tiers.length; i++) Checks.require(tiers[i], "no host item at tier " + i);
            Checks.require(ranged == CatalystData.table().tierlessItems().size(), "tierless hosts: " + ranged);
            return armor + " armor pieces, " + weapons + " weapons, " + tools + " tools, " + ranged + " tierless, all 14 tiers present";
        });
        Checks.register("catalysts", "slots", s -> {
            int previous = 0;
            StringBuilder sb = new StringBuilder();
            for (int idx = 0; idx < 14; idx++) {
                int slots = CatalystRules.slotCount(new CatalystHosts.Host(idx, false, true, false, null));
                Checks.require(slots >= previous && slots >= 1 && slots <= 5, "slot count at tier " + idx + " = " + slots);
                previous = slots;
                sb.append(slots);
            }
            Checks.require(previous == 5, "Platinum must have 5 slots, has " + previous);
            return "slots per tier T0..T13 = " + sb + ", tierless " + CatalystRules.slotCount(new CatalystHosts.Host(13, true, false, false, null));
        });
        Checks.register("catalysts", "values_monotone", s -> {
            int rows = 0;
            for (var perTarget : CatalystData.table().values().values()) {
                for (double[][] byIdx : perTarget.values()) {
                    for (double[] row : byIdx) {
                        if (row == null) continue;
                        rows++;
                        for (int i = 1; i < row.length; i++) Checks.require(row[i] > row[i - 1], "value row not increasing with level: " + java.util.Arrays.toString(row));
                    }
                }
            }
            return rows + " value rows, each strictly increasing I→V";
        });
        Checks.register("catalysts", "enchantments_registered", s -> {
            var lookup = s.getServer().registryAccess().lookupOrThrow(Registries.ENCHANTMENT);
            int checked = 0;
            for (CatalystData.Def def : CatalystData.table().defs().values()) {
                if (def.enchantment() == null) continue;
                Checks.require(lookup.get(def.enchantment()).isPresent(), "enchantment missing from the registry: " + def.enchantment().identifier());
                checked++;
            }
            for (String id : CatalystData.table().enchantCaps().keySet()) {
                Checks.require(lookup.get(net.minecraft.resources.ResourceKey.create(Registries.ENCHANTMENT, Identifier.parse(id))).isPresent(), "enchant_caps.csv names an unknown enchantment: " + id);
                checked++;
            }
            return checked + " enchantment ids resolve in the registry";
        });
        Checks.register("catalysts", "component_codec", s -> {
            Catalysts sample = new Catalysts(List.of(new Catalysts.Slot(Identifier.parse("theoria:attack_speed"), 4, 0), new Catalysts.Slot(Identifier.parse("theoria:ranged_power"), 2, 6)), 2);
            RegistryOps<JsonElement> ops = RegistryOps.create(JsonOps.INSTANCE, s.getServer().registryAccess());
            JsonElement json = Catalysts.CODEC.encodeStart(ops, sample).getOrThrow();
            Checks.require(Catalysts.CODEC.parse(ops, json).getOrThrow().equals(sample), "JSON round trip changed the component");
            RegistryFriendlyByteBuf buf = new RegistryFriendlyByteBuf(Unpooled.buffer(), s.getServer().registryAccess());
            Catalysts.STREAM_CODEC.encode(buf, sample);
            Checks.require(Catalysts.STREAM_CODEC.decode(buf).equals(sample), "network round trip changed the component");
            Checks.require(Catalysts.CODEC.parse(ops, ops.createMap(java.util.Map.of(ops.createString("slots"), ops.createList(java.util.stream.Stream.of(
                    ops.createMap(java.util.Map.of(ops.createString("id"), ops.createString("theoria:nope"), ops.createString("level"), ops.createInt(1))))))))
                    .isError(), "an unknown catalyst id must fail the codec");
            return "JSON " + json + " round trips; stream codec round trips; unknown id rejected";
        });
        Checks.register("catalysts", "plan", s -> {
            Item sword = BuiltInRegistries.ITEM.getValue(Identifier.parse("minecraft:iron_sword"));
            ItemStack stack = new ItemStack(sword);
            CatalystRules.Plan nothing = CatalystRules.plan(stack, List.of(ItemStack.EMPTY));
            Checks.require(nothing.problem() == CatalystRules.Problem.NO_INPUT, "empty input should say NO_INPUT, said " + nothing.problem());
            ItemStack haste = CatalystItems.stack(CatalystData.table().defs().get("attack_speed"), 3, 0);
            CatalystRules.Plan ok = CatalystRules.plan(stack, List.of(haste));
            Checks.require(ok.ok() && ok.after().slots().size() == 1, "an iron sword must take Swiftness: " + ok.problem());
            Checks.require(stack.get(CatalystComponents.CATALYSTS.get()) == null, "planning must not touch the stack");
            ItemStack netherite = new ItemStack(Items.NETHERITE_INGOT);
            Checks.require(CatalystRules.plan(stack, List.of(netherite)).problem() == CatalystRules.Problem.NETHERITE_NO_ENCHANT, "Netherite on an unenchanted sword must be refused");
            return "iron sword + Swiftness III plans OK; Netherite without enchantments refused; the stack is untouched";
        });
    }
}
