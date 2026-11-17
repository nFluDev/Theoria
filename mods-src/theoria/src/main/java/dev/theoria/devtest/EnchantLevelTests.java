package dev.theoria.devtest;

import com.google.gson.JsonElement;
import com.mojang.serialization.JsonOps;
import java.util.function.BiConsumer;
import java.util.function.Consumer;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Holder;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.Registries;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.RegistryOps;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.EntityTypes;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.monster.zombie.Zombie;
import net.minecraft.world.inventory.AnvilMenu;
import net.minecraft.world.inventory.ContainerLevelAccess;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.component.CustomData;
import net.minecraft.world.item.enchantment.Enchantment;
import net.minecraft.world.item.enchantment.EnchantmentHelper;
import net.minecraft.world.item.enchantment.Enchantments;
import net.minecraft.world.item.enchantment.ItemEnchantments;
import org.apache.commons.lang3.mutable.MutableFloat;

/**
 * Faz 7b, first job (ADR-004 "Faz 7b — ilk iş", ADR-006 Karar 6): turns the [kaynak] claims about {@code GetEnchantmentLevelEvent} into [log].
 * Uses {@link EnchantLevelProbe} (a stack marker, not our component) so a red result points at the platform. If any test here is red,
 * stop: ADR-004 "Geri dönüş" 1 / ADR-006 "Geri dönüş" 1 apply, and the catalyst work below this line must not start.
 */
final class EnchantLevelTests {
    private static final double EPS = 1.0e-3;

    private EnchantLevelTests() {}

    static void register(BiConsumer<String, Consumer<GameTestHelper>> add) {
        EnchantLevelProbe.init();
        add.accept("enchant_effective_level", EnchantLevelTests::effectiveLevel);
    }

    private static Holder<Enchantment> ench(ServerLevel level, ResourceKey<Enchantment> key) {
        return level.registryAccess().lookupOrThrow(Registries.ENCHANTMENT).getOrThrow(key);
    }

    private static ItemStack marked(ItemStack stack, int bonus, String... virtual) {
        CompoundTag tag = new CompoundTag();
        if (bonus != 0) tag.putInt(EnchantLevelProbe.BONUS, bonus);
        for (int i = 0; i + 1 < virtual.length; i += 2) tag.putInt(EnchantLevelProbe.VIRTUAL_PREFIX + virtual[i], Integer.parseInt(virtual[i + 1]));
        stack.set(DataComponents.CUSTOM_DATA, CustomData.of(tag));
        return stack;
    }

    private static float damage(ServerLevel level, ItemStack weapon, Zombie victim, DamageSource source) {
        return EnchantmentHelper.modifyDamage(level, weapon, victim, source, 0f);
    }

    private static void effectiveLevel(GameTestHelper h) {
        ServerLevel level = h.getLevel();
        Dev.Report r = new Dev.Report("enchant_effective_level");
        Holder<Enchantment> sharp = ench(level, Enchantments.SHARPNESS);
        Holder<Enchantment> fortune = ench(level, Enchantments.FORTUNE);
        Holder<Enchantment> smite = ench(level, Enchantments.SMITE);
        Holder<Enchantment> protection = ench(level, Enchantments.PROTECTION);
        Zombie zombie = h.spawnWithNoFreeWill(EntityTypes.ZOMBIE, new BlockPos(1, 1, 1));
        DamageSource source = level.damageSources().generic();

        // 1. Sharpness V + bonus 1 -> effective 6 (gameplay), raw 5 (tag), +3.5 damage (V alone: +3.0)
        ItemStack plain = new ItemStack(Items.DIAMOND_SWORD);
        plain.enchant(sharp, 5);
        ItemStack boosted = marked(new ItemStack(Items.DIAMOND_SWORD), 1);
        boosted.enchant(sharp, 5);
        int gameplay = boosted.getEnchantmentLevel(sharp);
        int tagLevel = EnchantmentHelper.getTagEnchantmentLevel(sharp, boosted);
        int helperLevel = EnchantmentHelper.getItemEnchantmentLevel(sharp, boosted);
        float plainDamage = damage(level, plain, zombie, source);
        float boostedDamage = damage(level, boosted, zombie, source);
        r.line("Sharpness V + bonus 1: gameplay %d (expect 6), tag %d (expect 5), EnchantmentHelper.getItemEnchantmentLevel %d; damage plain %.2f / boosted %.2f (expect 3.00 / 3.50)",
                gameplay, tagLevel, helperLevel, plainDamage, boostedDamage);

        // 2. Fortune III + 1 -> 4 (loot functions read the same path)
        ItemStack pick = marked(new ItemStack(Items.DIAMOND_PICKAXE), 1);
        pick.enchant(fortune, 3);
        int fortuneLevel = pick.getEnchantmentLevel(fortune);
        r.line("Fortune III + bonus 1: gameplay %d (expect 4), tag %d", fortuneLevel, EnchantmentHelper.getTagEnchantmentLevel(fortune, pick));

        // 3. bonus 300 -> 255 (Mutable.set clamps; ADR-004 Karar 5)
        ItemStack huge = marked(new ItemStack(Items.DIAMOND_SWORD), 300);
        huge.enchant(sharp, 5);
        int hugeLevel = huge.getEnchantmentLevel(sharp);
        r.line("Sharpness V + bonus 300: gameplay %d (expect 255)", hugeLevel);

        // 4. bonus is for real enchantments only: no vanilla level -> nothing (ADR-004 Karar 3)
        ItemStack bare = marked(new ItemStack(Items.DIAMOND_SWORD), 2);
        int bareLevel = bare.getEnchantmentLevel(sharp);
        r.line("no Sharpness + bonus 2: gameplay %d (expect 0)", bareLevel);

        // 5. virtual enchantment (not in the component): visible to single queries, to getAllEnchantments and to the real effect code
        ItemStack virtualSword = marked(new ItemStack(Items.DIAMOND_SWORD), 0, "smite", "7");
        var lookup = level.registryAccess().lookupOrThrow(Registries.ENCHANTMENT);
        int virtualSingle = virtualSword.getEnchantmentLevel(smite);
        ItemEnchantments all = virtualSword.getAllEnchantments(lookup);
        boolean inTag = EnchantmentHelper.getTagEnchantmentLevel(smite, virtualSword) > 0;
        MutableFloat direct = new MutableFloat(0f);
        smite.value().modifyDamage(level, 7, virtualSword, zombie, source, direct);
        float virtualDamage = damage(level, virtualSword, zombie, source);
        r.line("virtual Smite 7: single %d (expect 7), getAllEnchantments %d (expect 7), in tag %s (expect false); damage via helper %.2f, direct Enchantment#modifyDamage(7) %.2f",
                virtualSingle, all.getLevel(smite), inTag, virtualDamage, direct.floatValue());

        // 6. mixed stack, getAllEnchantments: real ones get the bonus, virtual ones are added
        ItemStack mixed = marked(new ItemStack(Items.DIAMOND_SWORD), 1, "smite", "3");
        mixed.enchant(sharp, 4);
        ItemEnchantments mixedAll = mixed.getAllEnchantments(lookup);
        r.line("mixed getAllEnchantments: sharpness %d (expect 5), smite %d (expect 3, the bonus does not touch virtual levels)", mixedAll.getLevel(sharp), mixedAll.getLevel(smite));

        // 7. equipment path: a virtual Protection on a worn helmet reaches the damage-protection computation
        Zombie wearer = h.spawnWithNoFreeWill(EntityTypes.ZOMBIE, new BlockPos(2, 1, 2));
        ItemStack helmetReal = new ItemStack(Items.DIAMOND_HELMET);
        helmetReal.enchant(protection, 4);
        ItemStack helmetVirtual = marked(new ItemStack(Items.DIAMOND_HELMET), 0, "protection", "4");
        wearer.setItemSlot(EquipmentSlot.HEAD, helmetReal);
        float realEpf = EnchantmentHelper.getDamageProtection(level, wearer, source);
        wearer.setItemSlot(EquipmentSlot.HEAD, helmetVirtual);
        float virtualEpf = EnchantmentHelper.getDamageProtection(level, wearer, source);
        int worn = EnchantmentHelper.getEnchantmentLevel(protection, wearer);
        r.line("worn helmet: protection IV real EPF %.1f, virtual EPF %.1f, EnchantmentHelper.getEnchantmentLevel %d (expect 4)", realEpf, virtualEpf, worn);

        // 8. save / load: the marker survives the item codec and the effective level follows
        RegistryOps<JsonElement> ops = RegistryOps.create(JsonOps.INSTANCE, level.registryAccess());
        JsonElement saved = ItemStack.CODEC.encodeStart(ops, boosted).getOrThrow();
        ItemStack loaded = ItemStack.CODEC.parse(ops, saved).getOrThrow();
        int loadedGameplay = loaded.getEnchantmentLevel(sharp);
        int loadedTag = EnchantmentHelper.getTagEnchantmentLevel(sharp, loaded);
        r.line("after codec round trip: gameplay %d (expect 6), tag %d (expect 5)", loadedGameplay, loadedTag);

        // 9. anvil: it reads the raw component (bonus never reaches crafting) and copies the rest; the result keeps the marker, so effective stays raw + bonus
        ServerPlayer player = FakePlayers.survival(h, "dev-anvil");
        player.getAbilities().instabuild = true; // no XP needed for the preview
        AnvilMenu anvil = new AnvilMenu(1, player.getInventory(), ContainerLevelAccess.create(level, h.absolutePos(new BlockPos(1, 1, 1))));
        ItemStack left = marked(new ItemStack(Items.DIAMOND_SWORD), 1);
        left.enchant(sharp, 4);
        ItemStack book = new ItemStack(Items.ENCHANTED_BOOK);
        EnchantmentHelper.updateEnchantments(book, m -> m.set(sharp, 4));
        anvil.getSlot(AnvilMenu.INPUT_SLOT).set(left);
        anvil.getSlot(AnvilMenu.ADDITIONAL_SLOT).set(book);
        anvil.slotsChanged(anvil.getSlot(AnvilMenu.INPUT_SLOT).container);
        ItemStack result = anvil.getSlot(AnvilMenu.RESULT_SLOT).getItem();
        int anvilTag = EnchantmentHelper.getTagEnchantmentLevel(sharp, result);
        int anvilGameplay = result.getEnchantmentLevel(sharp);
        r.line("anvil Sharpness IV + book IV (+bonus 1 on the sword): result empty=%s, raw %d (expect 5: vanilla merge), effective %d (expect 6)",
                result.isEmpty(), anvilTag, anvilGameplay);
        FakePlayers.remove(player);

        r.write();
        h.assertTrue(Math.abs(plainDamage - 3.0f) < EPS, "sharpness V alone should add 3.0 damage, got " + plainDamage);
        h.assertValueEqual(gameplay, 6, "Sharpness V + bonus 1 gameplay level");
        h.assertValueEqual(helperLevel, 6, "EnchantmentHelper.getItemEnchantmentLevel (deprecated alias) must also see the bonus");
        h.assertValueEqual(tagLevel, 5, "raw tag level must stay vanilla");
        h.assertTrue(Math.abs(boostedDamage - 3.5f) < EPS, "sharpness VI effect should add 3.5 damage, got " + boostedDamage);
        h.assertValueEqual(fortuneLevel, 4, "Fortune III + bonus 1");
        h.assertValueEqual(hugeLevel, 255, "bonus 300 clamps at 255");
        h.assertValueEqual(bareLevel, 0, "bonus needs a real enchantment");
        h.assertValueEqual(virtualSingle, 7, "virtual enchantment visible to single query");
        h.assertValueEqual(all.getLevel(smite), 7, "virtual enchantment visible to getAllEnchantments");
        h.assertTrue(!inTag, "virtual enchantment must not exist in the raw component");
        h.assertTrue(direct.floatValue() > 0f && Math.abs(virtualDamage - direct.floatValue()) < EPS,
                "real effect code should use the virtual level: helper " + virtualDamage + " vs direct " + direct.floatValue());
        h.assertValueEqual(mixedAll.getLevel(sharp), 5, "mixed: real enchantment gets the bonus");
        h.assertValueEqual(mixedAll.getLevel(smite), 3, "mixed: virtual level stays as given");
        h.assertTrue(realEpf > 0f && Math.abs(realEpf - virtualEpf) < EPS, "virtual protection must protect like a real one: " + realEpf + " vs " + virtualEpf);
        h.assertValueEqual(worn, 4, "EnchantmentHelper.getEnchantmentLevel over worn equipment");
        h.assertValueEqual(loadedGameplay, 6, "effective level after save/load");
        h.assertValueEqual(loadedTag, 5, "raw level after save/load");
        h.assertTrue(!result.isEmpty(), "anvil should produce a result for the preview");
        h.assertValueEqual(anvilTag, 5, "anvil merge reads the raw level (IV + IV = V)");
        h.assertValueEqual(anvilGameplay, 6, "anvil result keeps the marker, so the effective level is raw + bonus");
        h.succeed();
    }
}
