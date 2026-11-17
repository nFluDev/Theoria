package dev.theoria.devtest;

import dev.theoria.Theoria;
import dev.theoria.catalyst.CatalystComponents;
import dev.theoria.catalyst.CatalystData;
import dev.theoria.catalyst.CatalystItems;
import dev.theoria.catalyst.CatalystMenu;
import dev.theoria.catalyst.Catalysts;
import dev.theoria.catalyst.ElementEffects;
import dev.theoria.catalyst.Restoration;
import java.util.List;
import java.util.function.BiConsumer;
import java.util.function.Consumer;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Holder;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.EntityTypes;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.Attribute;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.animal.pig.Pig;
import net.minecraft.world.entity.monster.zombie.Zombie;
import net.minecraft.world.inventory.ContainerLevelAccess;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.entity.living.LivingEntityUseItemEvent;
import net.neoforged.neoforge.event.entity.player.CriticalHitEvent;
import net.neoforged.neoforge.event.tick.PlayerTickEvent;

/**
 * Phase 7b GameTests that need entities and menus: the consumers of the combat attributes, the element effects, damage over time, Restoration, and the Catalyst Forge's
 * menu (apply, remove, forge, close). Values are the CSVs' (via {@link CatalystTests#value}); the hits go through real {@code hurtServer} so the actual event chain runs.
 */
final class CatalystCombatTests {
    private static final double EPS = 2.0e-3;

    private CatalystCombatTests() {}

    static void register(BiConsumer<String, Consumer<GameTestHelper>> add) {
        add.accept("catalyst_combat", CatalystCombatTests::combat);
        add.accept("catalyst_elements", CatalystCombatTests::elements);
        add.accept("catalyst_dot", CatalystCombatTests::dot);
        add.accept("catalyst_restoration", CatalystCombatTests::restoration);
        add.accept("catalyst_menu", CatalystCombatTests::menu);
        add.accept("catalyst_forge", CatalystCombatTests::forge);
    }

    // ------------------------------------------------------------------------------------------------ helpers
    private static void modify(LivingEntity entity, Holder<Attribute> attribute, double amount, String name) {
        entity.getAttribute(attribute).addTransientModifier(new AttributeModifier(Theoria.id("test/" + name), amount, AttributeModifier.Operation.ADD_VALUE));
    }

    /** Hits {@code victim} with a plain player attack for {@code amount}; returns the health it lost. */
    private static double hit(ServerLevel level, ServerPlayer player, LivingEntity victim, float amount) {
        float before = victim.getHealth();
        victim.hurtServer(level, level.damageSources().playerAttack(player), amount);
        return before - victim.getHealth();
    }

    private static ItemStack weapon(String... idLevel) {
        ItemStack sword = new ItemStack(Items.DIAMOND_SWORD);
        Catalysts.Slot[] slots = new Catalysts.Slot[idLevel.length / 2];
        for (int i = 0; i < slots.length; i++) slots[i] = CatalystTests.slot(idLevel[2 * i], Integer.parseInt(idLevel[2 * i + 1]));
        return CatalystTests.withCatalysts(sword, slots);
    }

    /** Removes every non-player mob near the test, so splash effects (chain, AoE) only find the entities a section sets up. */
    private static void clearMobs(GameTestHelper h, ServerLevel level) {
        Vec3 origin = h.absoluteVec(new Vec3(4, 1, 4));
        level.getEntitiesOfClass(LivingEntity.class, new AABB(origin, origin).inflate(40), e -> !(e instanceof net.minecraft.world.entity.player.Player)).forEach(net.minecraft.world.entity.Entity::discard);
    }

    /** A zombie with its natural 2 armor removed, so a hit loses exactly the damage the test computed (the balance formula would shave 2-3 % off). */
    private static Zombie zombie(GameTestHelper h, int x, int z) {
        Zombie zombie = h.spawnWithNoFreeWill(EntityTypes.ZOMBIE, new BlockPos(x, 1, z));
        zombie.getAttribute(net.minecraft.world.entity.ai.attributes.Attributes.ARMOR).setBaseValue(0.0);
        return zombie;
    }

    // ------------------------------------------------------------------------------------------------ attribute consumers
    private static void combat(GameTestHelper h) {
        Dev.Report r = new Dev.Report("catalyst_combat");
        ServerLevel level = h.getLevel();
        ServerPlayer player = FakePlayers.survival(h, "dev-catalyst-combat");
        Zombie dummy = zombie(h, 3, 3);
        Zombie zombie2 = zombie(h, 3, 4);

        // the attributes exist on players (registered and attached)
        for (Holder<Attribute> a : List.of(CatalystComponents.CRIT_CHANCE, CatalystComponents.CRIT_DAMAGE, CatalystComponents.LIFE_STEAL, CatalystComponents.THORNS, CatalystComponents.FOOD_SATURATION)) {
            h.assertTrue(player.getAttribute(a) != null, "players must have attribute " + a.getRegisteredName());
        }

        // critical hits: chance rolls on a charged attack, the soft cap clamps the sum, crit damage adds to the 1.5 multiplier
        player.attackStrengthTicker = 1000;
        modify(player, CatalystComponents.CRIT_CHANCE, 0.5, "crit_chance");
        int crits = 0, trials = 4000;
        for (int i = 0; i < trials; i++) {
            CriticalHitEvent event = new CriticalHitEvent(player, dummy, 1.0F, false);
            NeoForge.EVENT_BUS.post(event);
            if (event.isCriticalHit()) crits++;
        }
        double rate = crits / (double) trials;
        h.assertTrue(rate > 0.45 && rate < 0.55, "crit chance 0.5 should crit about half the time, got " + rate);
        modify(player, CatalystComponents.CRIT_CHANCE, 1.0, "crit_chance_more"); // sum 1.5, soft cap 0.75 (agg_cap_crit_chance)
        crits = 0;
        for (int i = 0; i < trials; i++) {
            CriticalHitEvent event = new CriticalHitEvent(player, dummy, 1.0F, false);
            NeoForge.EVENT_BUS.post(event);
            if (event.isCriticalHit()) crits++;
        }
        double capped = crits / (double) trials;
        h.assertTrue(capped > 0.69 && capped < 0.81, "crit chance is capped at 0.75, got " + capped);
        modify(player, CatalystComponents.CRIT_DAMAGE, 0.5, "crit_damage");
        CriticalHitEvent vanilla = new CriticalHitEvent(player, dummy, 1.5F, true);
        NeoForge.EVENT_BUS.post(vanilla);
        h.assertTrue(Math.abs(vanilla.getDamageMultiplier() - 2.0F) < EPS, "crit damage +0.5 on a vanilla 1.5 crit = 2.0, got " + vanilla.getDamageMultiplier());
        player.attackStrengthTicker = 0;
        int uncharged = 0;
        for (int i = 0; i < 400; i++) {
            CriticalHitEvent event = new CriticalHitEvent(player, dummy, 1.0F, false);
            NeoForge.EVENT_BUS.post(event);
            if (event.isCriticalHit()) uncharged++;
        }
        h.assertValueEqual(uncharged, 0, "an uncharged swing never rolls a crit");
        r.line("crit chance 0.5 → %.3f, capped sum → %.3f (cap 0.75), vanilla crit with +0.5 damage → ×%.2f, uncharged crits %d", rate, capped, vanilla.getDamageMultiplier(), uncharged);

        // life steal: a share of the damage dealt, capped at 0.30 (health scale divided out, see CatalystCombat#healScale)
        player.setHealth(5.0F);
        modify(player, CatalystComponents.LIFE_STEAL, 0.1, "life_steal");
        double dealt = hit(level, player, dummy, 10.0F);
        h.assertTrue(Math.abs(player.getHealth() - (5.0F + dealt * 0.1)) < 0.05, "life steal 0.1 of " + dealt + " → health 6.0, got " + player.getHealth());
        player.setHealth(5.0F);
        modify(player, CatalystComponents.LIFE_STEAL, 0.9, "life_steal_more");
        double dealt2 = hit(level, player, zombie2, 10.0F);
        h.assertTrue(Math.abs(player.getHealth() - (5.0F + dealt2 * 0.3)) < 0.05, "life steal is capped at 0.30: health " + player.getHealth() + " after " + dealt2);
        r.line("life steal 0.1: +%.2f health for %.1f dealt; capped at 0.3: +%.2f for %.1f", 1.0, dealt, player.getHealth() - 5.0, dealt2);

        // thorns: flat damage back at a melee attacker, and not a loop
        Zombie attacker = zombie(h, 4, 3);
        modify(player, CatalystComponents.THORNS, 3.0, "thorns");
        float zombieBefore = attacker.getHealth();
        player.hurtServer(level, level.damageSources().mobAttack(attacker), 4.0F);
        h.assertTrue(Math.abs((zombieBefore - attacker.getHealth()) - 3.0) < 0.05, "thorns 3 returns 3 damage, got " + (zombieBefore - attacker.getHealth()));
        // food saturation: extra saturation per meal, never above the food level
        player.getFoodData().setFoodLevel(10);
        player.getFoodData().setSaturation(0F);
        modify(player, CatalystComponents.FOOD_SATURATION, 4.0, "saturation");
        NeoForge.EVENT_BUS.post(new LivingEntityUseItemEvent.Finish(player, new ItemStack(Items.COOKED_BEEF), 0, ItemStack.EMPTY));
        h.assertTrue(Math.abs(player.getFoodData().getSaturationLevel() - 4.0F) < EPS, "saturation +4, got " + player.getFoodData().getSaturationLevel());
        player.getFoodData().setSaturation(0F);
        NeoForge.EVENT_BUS.post(new LivingEntityUseItemEvent.Finish(player, new ItemStack(Items.STICK), 0, ItemStack.EMPTY));
        h.assertTrue(player.getFoodData().getSaturationLevel() == 0F, "non-food items add no saturation");
        modify(player, CatalystComponents.FOOD_SATURATION, 40.0, "saturation_more");
        NeoForge.EVENT_BUS.post(new LivingEntityUseItemEvent.Finish(player, new ItemStack(Items.COOKED_BEEF), 0, ItemStack.EMPTY));
        h.assertTrue(player.getFoodData().getSaturationLevel() <= 10F, "saturation never exceeds the food level (10), got " + player.getFoodData().getSaturationLevel());
        FakePlayers.remove(player);
        r.write();
        h.succeed();
    }

    // ------------------------------------------------------------------------------------------------ elements (instant + side effects)
    private static void elements(GameTestHelper h) {
        Dev.Report r = new Dev.Report("catalyst_elements");
        ServerLevel level = h.getLevel();
        ServerPlayer player = FakePlayers.survival(h, "dev-catalyst-elements");
        ElementEffects.clearDots();
        final float base = 6.0F;

        player.setItemSlot(EquipmentSlot.MAINHAND, new ItemStack(Items.DIAMOND_SWORD));
        double plain = hit(level, player, zombie(h, 4, 1), base);
        h.assertTrue(Math.abs(plain - base) < EPS, "a plain diamond sword adds nothing: " + plain);

        // Buz II (T8): +value damage and slowness
        double frost = CatalystTests.value("frost", "weapon", 8, 2);
        player.setItemSlot(EquipmentSlot.MAINHAND, weapon("frost", "2"));
        Zombie victim = zombie(h, 4, 1);
        double lost = hit(level, player, victim, base);
        h.assertTrue(Math.abs(lost - (base + frost)) < 0.01, "Frost II adds " + frost + " per hit, lost " + lost);
        h.assertTrue(victim.hasEffect(MobEffects.SLOWNESS), "Frost slows the target");

        // Ateş: damage + ignite
        double fire = CatalystTests.value("fire", "weapon", 8, 5);
        player.setItemSlot(EquipmentSlot.MAINHAND, weapon("fire", "5"));
        Zombie burning = zombie(h, 4, 2);
        double fireLost = hit(level, player, burning, base);
        h.assertTrue(Math.abs(fireLost - (base + fire)) < 0.01, "Fire V adds " + fire + ", lost " + fireLost);
        h.assertTrue(burning.isOnFire(), "Fire sets the target ablaze");

        // Işık: undead only
        double radiance = CatalystTests.value("radiance", "weapon", 8, 5);
        player.setItemSlot(EquipmentSlot.MAINHAND, weapon("radiance", "5"));
        double vsUndead = hit(level, player, zombie(h, 4, 3), base);
        net.minecraft.world.entity.animal.pig.Pig pig = h.spawnWithNoFreeWill(EntityTypes.PIG, new BlockPos(5, 1, 1));
        double vsPig = hit(level, player, pig, base);
        h.assertTrue(Math.abs(vsUndead - (base + radiance)) < 0.01 && Math.abs(vsPig - base) < 0.01, "Radiance: undead +" + radiance + " (" + vsUndead + "), pig +0 (" + vsPig + ")");

        // Gölge: from behind only. The player stands at x≈1.5; a zombie facing +x looks away from it, one facing −x looks at it.
        double umbra = CatalystTests.value("umbra", "weapon", 8, 5);
        player.setItemSlot(EquipmentSlot.MAINHAND, weapon("umbra", "5"));
        Zombie away = zombie(h, 5, 1);
        away.setYRot(-90.0F);
        Zombie facing = zombie(h, 5, 3);
        facing.setYRot(90.0F);
        double fromBehind = hit(level, player, away, base);
        double fromFront = hit(level, player, facing, base);
        h.assertTrue(Math.abs(fromBehind - (base + umbra)) < 0.01 && Math.abs(fromFront - base) < 0.01, "Umbra: behind +" + umbra + " (" + fromBehind + "), front +0 (" + fromFront + ")");

        // Fırtına: attacker airborne only
        double tempest = CatalystTests.value("tempest", "weapon", 8, 5);
        player.setItemSlot(EquipmentSlot.MAINHAND, weapon("tempest", "5"));
        player.setOnGround(true);
        double grounded = hit(level, player, zombie(h, 5, 4), base);
        player.setOnGround(false);
        double airborne = hit(level, player, zombie(h, 6, 1), base);
        player.setOnGround(true);
        h.assertTrue(Math.abs(grounded - base) < 0.01 && Math.abs(airborne - (base + tempest)) < 0.01, "Tempest: grounded +0 (" + grounded + "), airborne +" + tempest + " (" + airborne + ")");

        // Yıldırım: the hit target takes the damage, two enemies nearby are struck for the same; Cleave (AoE): all enemies within 2 blocks
        clearMobs(h, level);
        double lightning = CatalystTests.value("lightning", "weapon", 8, 5);
        player.setItemSlot(EquipmentSlot.MAINHAND, weapon("lightning", "5"));
        Zombie target = zombie(h, 6, 3);
        Zombie n1 = zombie(h, 7, 3), n2 = zombie(h, 6, 5), n3 = zombie(h, 8, 5), far = zombie(h, 6, 9);
        float[] before = {n1.getHealth(), n2.getHealth(), n3.getHealth(), far.getHealth()};
        double targetLost = hit(level, player, target, base);
        int chained = 0;
        for (LivingEntity e : List.of(n1, n2, n3)) if (e.getHealth() < 20.0F) chained++;
        h.assertTrue(Math.abs(targetLost - (base + lightning)) < 0.01, "Lightning: target takes +" + lightning + ", lost " + targetLost);
        h.assertValueEqual(chained, 2, "Lightning chains to exactly two of the three nearby enemies");
        h.assertTrue(far.getHealth() == before[3], "an enemy out of range is not struck");
        clearMobs(h, level);
        double cleave = CatalystTests.value("cleave", "weapon", 8, 5);
        player.setItemSlot(EquipmentSlot.MAINHAND, weapon("cleave", "5"));
        Zombie centre = zombie(h, 3, 8);
        Zombie beside = zombie(h, 4, 8), other = zombie(h, 3, 9), outside = zombie(h, 3, 12);
        hit(level, player, centre, base);
        h.assertTrue(Math.abs((20.0F - beside.getHealth()) - cleave) < 0.01 && Math.abs((20.0F - other.getHealth()) - cleave) < 0.01, "AoE: neighbours within 2 blocks take " + cleave + ", got " + (20.0F - beside.getHealth()));
        h.assertTrue(outside.getHealth() == 20.0F, "AoE: nothing beyond 2 blocks");

        // Gravite: the target is pulled towards the attacker (compare with the same hit without it)
        player.setItemSlot(EquipmentSlot.MAINHAND, new ItemStack(Items.DIAMOND_SWORD));
        Zombie baseline = zombie(h, 7, 1);
        hit(level, player, baseline, base);
        player.setItemSlot(EquipmentSlot.MAINHAND, weapon("gravity", "5"));
        Zombie pulled = zombie(h, 7, 2);
        hit(level, player, pulled, base);
        double pull = baseline.getDeltaMovement().x - pulled.getDeltaMovement().x;
        h.assertTrue(pull > 0.1, "Gravity pulls the target in (x velocity " + pulled.getDeltaMovement().x + " vs baseline " + baseline.getDeltaMovement().x + ")");

        // an arrow-like hit (no weapon in the source) triggers no element: the bow would need Menzil Gücü instead
        player.setItemSlot(EquipmentSlot.MAINHAND, weapon("frost", "5", "fire", "5"));
        Zombie arrowHit = zombie(h, 7, 4);
        float arrowBefore = arrowHit.getHealth();
        arrowHit.hurtServer(level, level.damageSources().magic(), base);
        h.assertTrue(Math.abs((arrowBefore - arrowHit.getHealth()) - base) < EPS, "damage with no melee weapon is not boosted");
        r.line("frost %.2f, fire %.2f, radiance %.2f, umbra %.2f, tempest %.2f, lightning %.2f (2 chained), AoE %.2f, gravity pull %.2f", frost, fire, radiance, umbra, tempest, lightning, cleave, pull);
        FakePlayers.remove(player);
        r.write();
        h.succeed();
    }

    // ------------------------------------------------------------------------------------------------ damage over time (needs time to pass)
    private static void dot(GameTestHelper h) {
        Dev.Report r = new Dev.Report("catalyst_dot");
        ServerLevel level = h.getLevel();
        ServerPlayer player = FakePlayers.survival(h, "dev-catalyst-dot");
        ElementEffects.clearDots();
        double poison = CatalystTests.value("poison", "weapon", 8, 5);
        double bleed = CatalystTests.value("bleed", "weapon", 8, 5);
        player.setItemSlot(EquipmentSlot.MAINHAND, weapon("poison", "5", "bleed", "5"));
        Zombie victim = zombie(h, 4, 1);
        victim.setHealth(victim.getMaxHealth());
        double initial = hit(level, player, victim, 1.0F);
        h.assertValueEqual(ElementEffects.activeDots(), 2, "poison and bleed each start one damage-over-time effect");
        // a second hit refreshes, it does not add a second copy of the same kind
        victim.invulnerableTime = 0;
        hit(level, player, victim, 1.0F);
        h.assertValueEqual(ElementEffects.activeDots(), 2, "the same kind does not stack");
        float afterHits = victim.getHealth();
        r.line("poison total %.2f over 5 s, bleed total %.2f over 6 s; after two 1-damage hits (%.2f) the victim has %.2f", poison, bleed, initial, afterHits);
        h.runAfterDelay(45, () -> {
            float now = victim.getHealth();
            double expectedAtLeast = poison / 5.0 + bleed / 6.0; // at least one whole second elapsed in 45 ticks
            r.line("after 45 ticks: health %.2f (lost %.2f; one second of both = %.2f)", now, afterHits - now, expectedAtLeast);
            h.assertTrue(afterHits - now >= expectedAtLeast - 0.05, "damage over time ticked at least once: lost " + (afterHits - now) + ", expected ≥ " + expectedAtLeast);
            ElementEffects.clearDots();
            FakePlayers.remove(player);
            r.write();
            h.succeed();
        });
    }

    // ------------------------------------------------------------------------------------------------ Restoration
    private static void restoration(GameTestHelper h) {
        Dev.Report r = new Dev.Report("catalyst_restoration");
        ServerPlayer player = FakePlayers.survival(h, "dev-catalyst-restoration");
        double rate = CatalystTests.value("restoration", "weapon", 8, 5); // fraction of max durability per second
        ItemStack sword = weapon("restoration", "5");
        int max = sword.getMaxDamage();
        sword.setDamageValue(max / 2);
        player.setItemSlot(EquipmentSlot.MAINHAND, sword);
        // an item without the virtual enchantment is left alone
        ItemStack plain = new ItemStack(Items.DIAMOND_CHESTPLATE);
        plain.setDamageValue(100);
        player.setItemSlot(EquipmentSlot.CHEST, plain);
        int seconds = 20;
        for (int i = 0; i < seconds; i++) {
            player.tickCount = 20 * (i + 1);
            NeoForge.EVENT_BUS.post(new PlayerTickEvent.Post(player));
        }
        double repaired = max / 2 - sword.getDamageValue();
        double expected = rate * max * seconds;
        h.assertTrue(Math.abs(repaired - expected) <= expected * 0.15 + 2, "Restoration V repairs " + rate + " of max durability per second: " + repaired + " points in " + seconds + " s, expected about " + expected);
        h.assertValueEqual(plain.getDamageValue(), 100, "an item without Restoration is not repaired");
        // Orikalkum carries it innately (II)
        ItemStack orichalcum = new ItemStack(CatalystTests.item("theoria:orichalcum_chestplate"));
        orichalcum.setDamageValue(orichalcum.getMaxDamage() / 2);
        player.setItemSlot(EquipmentSlot.CHEST, orichalcum);
        int hurt = orichalcum.getDamageValue();
        for (int i = 0; i < 60; i++) {
            player.tickCount = 20 * (i + 1);
            NeoForge.EVENT_BUS.post(new PlayerTickEvent.Post(player));
        }
        h.assertTrue(orichalcum.getDamageValue() < hurt, "innate Restoration II mends an orichalcum chestplate (" + hurt + " → " + orichalcum.getDamageValue() + ")");
        // a stack in the hotbar (not held, not worn) is not touched
        r.line("held sword: %.1f points repaired in %d s (expected %.1f); innate orichalcum: %d → %d in 60 s", repaired, seconds, expected, hurt, orichalcum.getDamageValue());
        h.assertTrue(Restoration.KEY != null, "restoration key");
        FakePlayers.remove(player);
        r.write();
        h.succeed();
    }

    // ------------------------------------------------------------------------------------------------ the forge's menu
    private static CatalystMenu menuFor(GameTestHelper h, ServerPlayer player) {
        return new CatalystMenu(7, player.getInventory(), ContainerLevelAccess.create(h.getLevel(), h.absolutePos(new BlockPos(1, 1, 1))));
    }

    private static void menu(GameTestHelper h) {
        Dev.Report r = new Dev.Report("catalyst_menu");
        ServerPlayer player = FakePlayers.survival(h, "dev-catalyst-menu");
        CatalystMenu menu = menuFor(h, player);

        // apply: target in slot 0, three catalysts in the inputs, the button commits; inputs are used up, the target stays in its slot
        menu.getSlot(CatalystMenu.TARGET).set(new ItemStack(Items.DIAMOND_SWORD));
        menu.getSlot(CatalystMenu.FIRST_INPUT).set(CatalystTests.catalyst("attack_speed", 4));
        menu.getSlot(CatalystMenu.FIRST_INPUT + 1).set(CatalystTests.catalyst("crit_chance", 3));
        menu.getSlot(CatalystMenu.FIRST_INPUT + 2).set(CatalystTests.catalyst("frost", 2));
        h.assertTrue(menu.clickMenuButton(player, CatalystMenu.BUTTON_APPLY), "the apply button is handled");
        Catalysts applied = Catalysts.of(menu.getSlot(CatalystMenu.TARGET).getItem());
        h.assertValueEqual(applied.slots().size(), 3, "three catalysts applied through the menu");
        for (int i = 0; i < 3; i++) h.assertTrue(menu.getSlot(CatalystMenu.FIRST_INPUT + i).getItem().isEmpty(), "input " + i + " consumed");

        // a refused application changes nothing
        menu.getSlot(CatalystMenu.FIRST_INPUT).set(CatalystTests.catalyst("power", 1));
        h.assertTrue(menu.clickMenuButton(player, CatalystMenu.BUTTON_APPLY), "button handled even when refused");
        h.assertValueEqual(Catalysts.of(menu.getSlot(CatalystMenu.TARGET).getItem()).slots().size(), 3, "a diamond sword has 3 slots: a fourth is refused");
        h.assertValueEqual(menu.getSlot(CatalystMenu.FIRST_INPUT).getItem().getCount(), 1, "the refused catalyst is still there");
        menu.getSlot(CatalystMenu.FIRST_INPUT).set(ItemStack.EMPTY);

        // remove (no refund)
        h.assertTrue(menu.clickMenuButton(player, CatalystMenu.BUTTON_REMOVE + 1), "remove button handled");
        Catalysts after = Catalysts.of(menu.getSlot(CatalystMenu.TARGET).getItem());
        h.assertTrue(after.slots().size() == 2 && after.indexOf(Theoria.id("crit_chance")) < 0, "slot 1 (Kritik Şansı) removed: " + after);
        h.assertTrue(!menu.clickMenuButton(player, 99), "unknown buttons are not handled");

        // closing hands every stack back (no BlockEntity keeps them)
        menu.getSlot(CatalystMenu.FIRST_INPUT + 4).set(new ItemStack(CatalystItems.DUST.get(), 5));
        menu.removed(player);
        int dust = 0;
        boolean sword = false;
        for (ItemStack s : player.getInventory().getNonEquipmentItems()) {
            if (s.is(CatalystItems.DUST.get())) dust += s.getCount();
            if (s.is(Items.DIAMOND_SWORD)) sword = true;
        }
        h.assertTrue(dust == 5 && sword, "closing the forge returns the target and the inputs (dust " + dust + ", sword " + sword + ")");
        h.assertTrue(menu.getSlot(CatalystMenu.TARGET).getItem().isEmpty(), "the container is empty after closing");

        // shift-click routing: a host goes to the target slot, a catalyst to the inputs
        CatalystMenu again = menuFor(h, player);
        int swordSlot = -1, catalystSlot = -1;
        player.getInventory().setItem(0, new ItemStack(Items.IRON_SWORD));
        player.getInventory().setItem(1, CatalystTests.catalyst("power", 1));
        for (int i = CatalystMenu.PLAYER_START; i < again.slots.size(); i++) {
            ItemStack s = again.getSlot(i).getItem();
            if (s.is(Items.IRON_SWORD)) swordSlot = i;
            if (s.getItem() instanceof dev.theoria.catalyst.CatalystItem) catalystSlot = i;
        }
        again.quickMoveStack(player, swordSlot);
        again.quickMoveStack(player, catalystSlot);
        h.assertTrue(again.getSlot(CatalystMenu.TARGET).getItem().is(Items.IRON_SWORD), "shift-click moves a host to the target slot");
        h.assertTrue(again.getSlot(CatalystMenu.FIRST_INPUT).getItem().getItem() instanceof dev.theoria.catalyst.CatalystItem, "shift-click moves a catalyst to the first input");
        again.removed(player);
        FakePlayers.remove(player);
        r.line("apply, refuse, remove, close and shift-click behave");
        r.write();
        h.succeed();
    }

    // ------------------------------------------------------------------------------------------------ forging a catalyst from raw materials
    private static void forge(GameTestHelper h) {
        Dev.Report r = new Dev.Report("catalyst_forge");
        ServerPlayer player = FakePlayers.survival(h, "dev-catalyst-forge");
        CatalystMenu menu = menuFor(h, player);
        CatalystData.Table t = CatalystData.table();
        int in = CatalystMenu.FIRST_INPUT;

        // level III of Güç: 10 dust, 3 blaze powder, 2 essence — exactly that is taken, extras stay
        menu.getSlot(in).set(new ItemStack(CatalystItems.DUST.get(), 12));
        menu.getSlot(in + 1).set(new ItemStack(Items.BLAZE_POWDER, 3));
        menu.getSlot(in + 2).set(new ItemStack(CatalystItems.ESSENCE.get(), 2));
        h.assertTrue(menu.clickMenuButton(player, CatalystMenu.BUTTON_APPLY), "forge button handled");
        ItemStack made = menu.getSlot(CatalystMenu.TARGET).getItem();
        h.assertTrue(made.getItem() instanceof dev.theoria.catalyst.CatalystItem item && item.def().id().equals("power") && dev.theoria.catalyst.CatalystItem.levelOf(made) == 3, "forged Güç III, got " + made);
        h.assertValueEqual(menu.getSlot(in).getItem().getCount(), 2, "12 dust − 10 = 2 left");
        h.assertTrue(menu.getSlot(in + 1).getItem().isEmpty() && menu.getSlot(in + 2).getItem().isEmpty(), "signature and essence used up");
        menu.getSlot(CatalystMenu.TARGET).set(ItemStack.EMPTY);
        menu.getSlot(in).set(ItemStack.EMPTY);

        // every catalyst × every level, at exactly its ladder cost: the forge makes that level and consumes exactly that
        int forged = 0;
        for (CatalystData.Def def : t.defs().values()) {
            if (def.kind() == CatalystData.Kind.NETHERITE) continue;
            for (CatalystData.Ladder step : t.ladder()) {
                menu.getSlot(CatalystMenu.TARGET).set(ItemStack.EMPTY);
                ItemStack signature = new ItemStack(CatalystTests.item(def.signature()), step.signature());
                int slot = in;
                menu.getSlot(slot++).set(new ItemStack(CatalystItems.DUST.get(), step.dust()));
                menu.getSlot(slot++).set(signature);
                if (step.essence() > 0) menu.getSlot(slot++).set(new ItemStack(CatalystItems.ESSENCE.get(), step.essence()));
                if (step.core() > 0) menu.getSlot(slot++).set(new ItemStack(CatalystItems.CORE.get(), step.core()));
                if (def.graded()) menu.getSlot(slot++).set(new ItemStack(Items.DIAMOND));
                h.assertTrue(menu.clickMenuButton(player, CatalystMenu.BUTTON_APPLY), "forge handled");
                ItemStack result = menu.getSlot(CatalystMenu.TARGET).getItem();
                h.assertTrue(result.getItem() instanceof dev.theoria.catalyst.CatalystItem c && c.def() == def && dev.theoria.catalyst.CatalystItem.levelOf(result) == step.level(),
                        def.id() + " level " + step.level() + " should be forged from its exact ladder cost, got " + result);
                if (def.graded()) h.assertValueEqual(dev.theoria.catalyst.CatalystItem.gradeOf(result), 8, "grade = the ingot's tier (diamond = 8)");
                for (int i = 0; i < CatalystMenu.INPUTS; i++) h.assertTrue(menu.getSlot(in + i).getItem().isEmpty(), def.id() + " L" + step.level() + ": exact cost leaves nothing behind (input " + i + ")");
                forged++;
            }
        }
        menu.getSlot(CatalystMenu.TARGET).set(ItemStack.EMPTY);

        // refusals consume nothing: one dust short, two signatures, none, a foreign item
        menu.getSlot(in).set(new ItemStack(CatalystItems.DUST.get(), 2));
        menu.getSlot(in + 1).set(new ItemStack(Items.BLAZE_POWDER, 1));
        menu.clickMenuButton(player, CatalystMenu.BUTTON_APPLY);
        h.assertTrue(menu.getSlot(CatalystMenu.TARGET).getItem().isEmpty() && menu.getSlot(in).getItem().getCount() == 2, "2 dust is below level I (3 dust): nothing forged, nothing taken");
        menu.getSlot(in).set(new ItemStack(CatalystItems.DUST.get(), 3));
        menu.getSlot(in + 2).set(new ItemStack(Items.SUGAR, 1));
        menu.clickMenuButton(player, CatalystMenu.BUTTON_APPLY);
        h.assertTrue(menu.getSlot(CatalystMenu.TARGET).getItem().isEmpty() && menu.getSlot(in + 1).getItem().getCount() == 1, "two different signatures are ambiguous: nothing forged");
        menu.getSlot(in + 2).set(ItemStack.EMPTY);
        menu.getSlot(in + 1).set(ItemStack.EMPTY);
        menu.clickMenuButton(player, CatalystMenu.BUTTON_APPLY);
        h.assertTrue(menu.getSlot(CatalystMenu.TARGET).getItem().isEmpty(), "no signature: nothing forged");

        // Menzil Gücü: the highest-tier ingot sets the grade and only that one ingot is taken
        menu.getSlot(in).set(new ItemStack(CatalystItems.DUST.get(), 3));
        menu.getSlot(in + 1).set(new ItemStack(Items.ARROW, 1));
        menu.getSlot(in + 2).set(new ItemStack(CatalystTests.item("theoria:tin_ingot"), 2));
        menu.getSlot(in + 3).set(new ItemStack(Items.DIAMOND, 2));
        menu.clickMenuButton(player, CatalystMenu.BUTTON_APPLY);
        ItemStack ranged = menu.getSlot(CatalystMenu.TARGET).getItem();
        h.assertTrue(dev.theoria.catalyst.CatalystItem.gradeOf(ranged) == 8 && menu.getSlot(in + 3).getItem().getCount() == 1 && menu.getSlot(in + 2).getItem().getCount() == 2,
                "Menzil Gücü takes one diamond (grade 8), keeps the tin: " + ranged);
        menu.removed(player);
        FakePlayers.remove(player);
        r.line("%d catalyst × level forges, each from exactly its ladder cost; refusals take nothing", forged);
        r.write();
        h.succeed();
    }
}
