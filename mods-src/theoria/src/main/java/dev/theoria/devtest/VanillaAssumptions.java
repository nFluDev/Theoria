package dev.theoria.devtest;

import com.google.gson.JsonElement;
import com.google.gson.JsonParser;
import com.mojang.serialization.DataResult;
import com.mojang.serialization.JsonOps;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Holder;
import net.minecraft.core.registries.Registries;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.resources.RegistryOps;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.damagesource.CombatRules;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.EntityTypes;
import net.minecraft.world.entity.monster.zombie.Zombie;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.enchantment.Enchantment;
import net.minecraft.world.item.enchantment.EnchantmentHelper;
import net.minecraft.world.item.enchantment.Enchantments;
import net.minecraft.world.item.enchantment.ItemEnchantments;
import org.apache.commons.lang3.mutable.MutableFloat;

/**
 * Vanilla / NeoForge behaviour that ADR-003 and ADR-004 are built on (Phase 0 spikes S4 and S6, kept as regression
 * tests). If one of these fails after a NeoForge or Minecraft update, re-read the ADR before fixing the test.
 * Phase 2b: the armor hook is real now, so the old placeholder-formula tests (S6 A-E) became {@link BalanceTests};
 * what stays here is the vanilla fact the hook exists for. Phase 7b adds the effective-level tests.
 */
final class VanillaAssumptions {
    private VanillaAssumptions() {}

    // ------------------------------------------------------------ ADR-004: enchantment level cap (S4)
    static void enchantCap(GameTestHelper h) {
        ServerLevel level = h.getLevel();
        Dev.Report r = new Dev.Report("vanilla_enchant_cap");
        Holder<Enchantment> sharp = level.registryAccess().lookupOrThrow(Registries.ENCHANTMENT).getOrThrow(Enchantments.SHARPNESS);
        RegistryOps<JsonElement> ops = RegistryOps.create(JsonOps.INSTANCE, level.registryAccess());

        DataResult<ItemEnchantments> c255 = ItemEnchantments.CODEC.parse(ops, JsonParser.parseString("{\"minecraft:sharpness\":255}"));
        DataResult<ItemEnchantments> c300 = ItemEnchantments.CODEC.parse(ops, JsonParser.parseString("{\"minecraft:sharpness\":300}"));
        r.line("ItemEnchantments.CODEC level 255 -> %s", describe(c255));
        r.line("ItemEnchantments.CODEC level 300 -> %s", describe(c300));

        ItemEnchantments.Mutable mut = new ItemEnchantments.Mutable(ItemEnchantments.EMPTY);
        mut.set(sharp, 300);
        int mutStored = mut.toImmutable().getLevel(sharp);
        r.line("ItemEnchantments.Mutable.set(sharpness, 300) stores %d", mutStored);

        ItemStack sword = new ItemStack(Items.DIAMOND_SWORD);
        sword.enchant(sharp, 300);
        int onStack = EnchantmentHelper.getItemEnchantmentLevel(sharp, sword);
        r.line("ItemStack.enchant(sharpness, 300) -> stack level %d", onStack);

        JsonElement def = Enchantment.DIRECT_CODEC.encodeStart(ops, sharp.value()).getOrThrow();
        def.getAsJsonObject().addProperty("max_level", 300);
        DataResult<Enchantment> defParse = Enchantment.DIRECT_CODEC.parse(ops, def);
        r.line("datapack enchantment definition max_level=300 -> %s", describe(defParse));

        Zombie z = h.spawnWithNoFreeWill(EntityTypes.ZOMBIE, new BlockPos(1, 1, 1));
        DamageSource src = level.damageSources().mobAttack(z);
        float[] got = new float[5];
        int[] levels = {1, 5, 255, 300, 1000};
        for (int i = 0; i < levels.length; i++) {
            MutableFloat amount = new MutableFloat(0f);
            sharp.value().modifyDamage(level, levels[i], sword, z, src, amount);
            got[i] = amount.floatValue();
            r.line("Enchantment.modifyDamage(level=%d) direct call -> +%.2f damage (vanilla linear 1+0.5*(L-1) = %.2f)",
                    levels[i], got[i], 1f + 0.5f * (levels[i] - 1));
        }
        r.write();
        h.assertTrue(c255.isSuccess(), "codec should accept 255");
        h.assertTrue(c300.isError(), "codec should reject 300");
        h.assertValueEqual(mutStored, 255, "Mutable clamp");
        h.assertValueEqual(onStack, 255, "ItemStack.enchant clamp");
        h.assertTrue(defParse.isError(), "enchantment definition should reject max_level 300");
        h.assertTrue(Math.abs(got[2] - 128f) < 1e-3, "level-255 effect should compute although max_level is 5");
        h.assertTrue(Math.abs(got[3] - 150.5f) < 1e-3, "level-300 effect should compute linearly");
        h.succeed();
    }

    // ------------------------------------------------------------ ADR-003: vanilla's own armor step still caps at 80 %
    /**
     * Calls vanilla's {@code CombatRules} directly (no hook involved): even with the attribute ceiling lifted, vanilla
     * clamps the effective armor to 20 → at most 80 % off, whatever the armor or toughness. That is why the formula is a
     * {@code Reduction.ARMOR} modifier and not just a higher attribute cap. (30 armor vs a big hit is below that ceiling:
     * vanilla subtracts {@code damage / (2 + T/4)} from the armor first, so 100 damage leaves 6 effective armor = 24 % off.)
     */
    static void combatRulesCap(GameTestHelper h) {
        ServerLevel level = h.getLevel();
        Dev.Report r = new Dev.Report("vanilla_combat_rules_cap");
        Zombie attacker = h.spawnWithNoFreeWill(EntityTypes.ZOMBIE, new BlockPos(3, 1, 3));
        Zombie victim = h.spawnWithNoFreeWill(EntityTypes.ZOMBIE, new BlockPos(1, 1, 1));
        DamageSource source = level.damageSources().mobAttack(attacker);
        float armor30 = CombatRules.getDamageAfterAbsorb(victim, 100f, source, 30f, 0f);
        float armor300 = CombatRules.getDamageAfterAbsorb(victim, 100f, source, 300f, 0f);
        float armor300T20 = CombatRules.getDamageAfterAbsorb(victim, 100f, source, 300f, 20f);
        r.line("CombatRules.getDamageAfterAbsorb(100 dmg): armor 30 -> %.4f (big hit eats armor), armor 300 -> %.4f, armor 300 + toughness 20 -> %.4f (ceiling: 80%% off)",
                armor30, armor300, armor300T20);
        r.write();
        h.assertTrue(Math.abs(armor30 - 76f) < 1e-3, "vanilla: 30 armor vs 100 damage -> 24% off (S6 log: took 76)");
        h.assertTrue(Math.abs(armor300 - 20f) < 1e-3, "vanilla: armor above 20 effective adds nothing");
        h.assertTrue(Math.abs(armor300T20 - 20f) < 1e-3, "vanilla: toughness cannot lift the 80% ceiling either");
        h.succeed();
    }

    private static String describe(DataResult<?> r) {
        return r.error().map(e -> "ERROR: " + e.message()).orElse("OK");
    }
}
