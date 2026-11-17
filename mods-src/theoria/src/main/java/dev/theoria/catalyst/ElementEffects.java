package dev.theoria.catalyst;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import net.minecraft.core.Holder;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.tags.EntityTypeTags;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.monster.Enemy;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.enchantment.Enchantment;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.entity.living.LivingDamageEvent;
import net.neoforged.neoforge.event.entity.living.LivingIncomingDamageEvent;
import net.neoforged.neoforge.event.tick.ServerTickEvent;
import org.jspecify.annotations.Nullable;

/**
 * Type B catalysts: the eleven element/effect enchantments (ADR-006 Karar 5-6). Each is a virtual enchantment ({@code theoria:frost}…) whose level is
 * {@code P = 1 + 5·g + (L − 1)}; {@link #value} decodes it into the table cell. The effect itself is Java, in one place, because chain/AoE/pull/DoT/conditions cannot be written
 * as datapack {@code LevelBasedValue} effects (ADR-006 Karar 5 expected this; the datapack enchantments of these ids are carriers that make the registry lookup possible).
 * <ul>
 * <li>instant, added before armor like Sharpness: Buz, Ateş, Yıldırım (target), Işık (undead only), Gölge (from behind), Fırtına (attacker airborne);</li>
 * <li>side effects once the hit has landed: Buz slows, Ateş ignites, Yıldırım chains to 2, AoE (Cleave) hits neighbours, Gravite pulls the target in;</li>
 * <li>damage over time, not stacking per kind: Zehir 5 s, Solma 5 s, Kanama 6 s (the table value is the TOTAL over the duration).</li>
 * </ul>
 * The element damage fractions are drafts (ADR-006 "⚠ Element hasar fraksiyonları"); the numbers come from the table, never from here. Only melee hits with the weapon count.
 */
public final class ElementEffects {
    private static final int DOT_SECONDS_POISON = 5;
    private static final int DOT_SECONDS_WITHER = 5;
    private static final int DOT_SECONDS_BLEED = 6;
    private static final double AOE_RADIUS = 2.0;
    private static final double CHAIN_RADIUS = 4.0;
    private static final int CHAIN_TARGETS = 2;
    private static final int FROST_SECONDS = 3;
    private static final int FIRE_SECONDS = 4;
    private static final double BEHIND_DOT = -0.5; // umbra: victim looks away from the attacker by more than 60° off straight away
    private static final double PULL_PER_BLOCK = 0.22;

    private record Dot(ResourceKey<Level> dimension, @Nullable UUID attacker, double perSecond, int secondsLeft) {}

    private static final Map<String, Dot> DOTS = new HashMap<>(); // key: victim UUID + ":" + kind
    /**
     * True while this class deals its own secondary damage (chain, AoE, damage over time). That damage is credited to the attacker, and an indirect source
     * with the attacker as its direct entity reports the attacker's weapon — without this guard a chained hit would count as a fresh melee hit and chain again.
     * The server thread is the only writer.
     */
    private static boolean secondary;

    private ElementEffects() {}

    public static void init() {
        NeoForge.EVENT_BUS.addListener(ElementEffects::onIncoming);
        NeoForge.EVENT_BUS.addListener(ElementEffects::onDamaged);
        NeoForge.EVENT_BUS.addListener(ElementEffects::onServerTick);
    }

    // ------------------------------------------------------------------------------------------------ values
    /** The weapon's value of element {@code id} (damage per hit, blocks for Gravite) read through its enchantment level; 0 when it has none. */
    public static double value(ServerLevel level, ItemStack weapon, String id) {
        Catalysts catalysts = weapon.get(CatalystComponents.CATALYSTS.get());
        if (catalysts == null) return 0.0;
        CatalystData.Def def = CatalystData.table().defs().get(id);
        if (def == null || def.enchantment() == null) return 0.0;
        boolean present = false;
        for (Catalysts.Slot slot : catalysts.slots()) if (slot.id().equals(def.identifier())) present = true;
        if (!present) return 0.0;
        Holder<Enchantment> holder = level.registryAccess().lookup(Registries.ENCHANTMENT).flatMap(l -> l.get(def.enchantment())).orElse(null);
        if (holder == null) return 0.0;
        int p = weapon.getEnchantmentLevel(holder);
        if (p <= 0) return 0.0;
        double v = CatalystData.table().value(id, "weapon", CatalystRules.decodeTier(p), CatalystRules.decodeLevel(p));
        return Double.isNaN(v) ? 0.0 : v;
    }

    private static boolean carriesElement(ItemStack weapon) {
        Catalysts catalysts = weapon.get(CatalystComponents.CATALYSTS.get());
        if (catalysts == null) return false;
        for (Catalysts.Slot slot : catalysts.slots()) {
            CatalystData.Def def = slot.def();
            if (def != null && def.kind() == CatalystData.Kind.ENCHANT && !slot.id().getPath().equals("restoration")) return true;
        }
        return false;
    }

    /** The melee attacker and weapon of a damage source, or null (projectiles, indirect and non-weapon damage do not trigger elements). */
    private static @Nullable LivingEntity meleeAttacker(DamageSource source) {
        if (source.getWeaponItem() == null || source.getEntity() != source.getDirectEntity()) return null;
        return source.getEntity() instanceof LivingEntity attacker ? attacker : null;
    }

    // ------------------------------------------------------------------------------------------------ instant part (before armor)
    private static void onIncoming(LivingIncomingDamageEvent event) {
        if (secondary) return;
        DamageSource source = event.getSource();
        ItemStack weapon = source.getWeaponItem();
        if (weapon == null || !carriesElement(weapon)) return;
        LivingEntity attacker = meleeAttacker(source);
        if (attacker == null || !(attacker.level() instanceof ServerLevel level)) return;
        LivingEntity victim = event.getEntity();
        double extra = value(level, weapon, "frost") + value(level, weapon, "fire") + value(level, weapon, "lightning");
        double radiance = value(level, weapon, "radiance");
        if (radiance > 0 && victim.is(EntityTypeTags.UNDEAD)) extra += radiance;
        double umbra = value(level, weapon, "umbra");
        if (umbra > 0 && isBehind(attacker, victim)) extra += umbra;
        double tempest = value(level, weapon, "tempest");
        if (tempest > 0 && airborne(attacker)) extra += tempest;
        if (extra > 0) event.setAmount(event.getAmount() + (float) extra);
    }

    private static boolean isBehind(LivingEntity attacker, LivingEntity victim) {
        Vec3 toAttacker = attacker.position().subtract(victim.position()).multiply(1, 0, 1);
        if (toAttacker.lengthSqr() < 1.0e-6) return false;
        Vec3 look = victim.getLookAngle().multiply(1, 0, 1);
        if (look.lengthSqr() < 1.0e-6) return false;
        return look.normalize().dot(toAttacker.normalize()) < BEHIND_DOT;
    }

    private static boolean airborne(LivingEntity attacker) {
        return !attacker.onGround() && !attacker.isInLiquid() && !attacker.isPassenger() && !attacker.onClimbable();
    }

    // ------------------------------------------------------------------------------------------------ side effects (after the hit landed)
    private static void onDamaged(LivingDamageEvent.Post event) {
        if (secondary) return;
        DamageSource source = event.getSource();
        ItemStack weapon = source.getWeaponItem();
        if (weapon == null || event.getInflictedDamage() <= 0.0F || !carriesElement(weapon)) return;
        LivingEntity attacker = meleeAttacker(source);
        if (attacker == null || !(attacker.level() instanceof ServerLevel level)) return;
        LivingEntity victim = event.getEntity();
        if (!victim.isAlive()) return;
        if (value(level, weapon, "frost") > 0) victim.addEffect(new MobEffectInstance(MobEffects.SLOWNESS, FROST_SECONDS * 20, slownessAmplifier(level, weapon)));
        if (value(level, weapon, "fire") > 0) victim.igniteForSeconds(FIRE_SECONDS);
        startDot(level, victim, attacker, "poison", value(level, weapon, "poison"), DOT_SECONDS_POISON, MobEffects.POISON);
        startDot(level, victim, attacker, "wither", value(level, weapon, "wither"), DOT_SECONDS_WITHER, MobEffects.WITHER);
        startDot(level, victim, attacker, "bleed", value(level, weapon, "bleed"), DOT_SECONDS_BLEED, null);
        double chain = value(level, weapon, "lightning");
        if (chain > 0) splash(level, attacker, victim, CHAIN_RADIUS, CHAIN_TARGETS, chain);
        double cleave = value(level, weapon, "cleave");
        if (cleave > 0) splash(level, attacker, victim, AOE_RADIUS, Integer.MAX_VALUE, cleave);
        double pull = value(level, weapon, "gravity");
        if (pull > 0) pull(attacker, victim, pull);
    }

    /** Frost: slowness amplifier grows with the catalyst level (I-II light, V strong). */
    private static int slownessAmplifier(ServerLevel level, ItemStack weapon) {
        Holder<Enchantment> holder = level.registryAccess().lookup(Registries.ENCHANTMENT).flatMap(l -> l.get(CatalystData.table().defs().get("frost").enchantment())).orElse(null);
        if (holder == null) return 0;
        int levelOfCatalyst = CatalystRules.decodeLevel(Math.max(1, weapon.getEnchantmentLevel(holder)));
        return Math.max(0, (levelOfCatalyst - 1) * 3 / 4); // 0,0,1,2,3 for I-V (slowness I…IV; V ≈ −60 %)
    }

    private static void splash(ServerLevel level, LivingEntity attacker, LivingEntity victim, double radius, int max, double damage) {
        List<LivingEntity> near = new ArrayList<>(level.getEntitiesOfClass(LivingEntity.class, victim.getBoundingBox().inflate(radius),
                e -> e != victim && e != attacker && e.isAlive() && e instanceof Enemy));
        near.sort((a, b) -> Double.compare(a.distanceToSqr(victim), b.distanceToSqr(victim)));
        DamageSource indirect = level.damageSources().indirectMagic(attacker, attacker);
        secondary = true;
        try {
            for (int i = 0; i < Math.min(max, near.size()); i++) near.get(i).hurtServer(level, indirect, (float) damage);
        } finally {
            secondary = false;
        }
    }

    private static void pull(LivingEntity attacker, LivingEntity victim, double blocks) {
        Vec3 toward = attacker.position().subtract(victim.position()).multiply(1, 0, 1);
        if (toward.lengthSqr() < 1.0e-6) return;
        Vec3 impulse = toward.normalize().scale(PULL_PER_BLOCK * blocks);
        victim.push(impulse.x, 0.1, impulse.z);
    }

    // ------------------------------------------------------------------------------------------------ damage over time
    private static void startDot(ServerLevel level, LivingEntity victim, LivingEntity attacker, String kind, double total, int seconds, @Nullable Holder<net.minecraft.world.effect.MobEffect> visual) {
        if (total <= 0.0) return;
        // Does not stack: a new hit refreshes the duration and keeps the stronger of the two rates.
        String key = victim.getStringUUID() + ":" + kind;
        Dot old = DOTS.get(key);
        double rate = total / seconds;
        DOTS.put(key, new Dot(level.dimension(), attacker.getUUID(), old == null ? rate : Math.max(old.perSecond(), rate), seconds));
        if (visual != null) victim.addEffect(new MobEffectInstance(visual, seconds * 20, 0));
    }

    private static void onServerTick(ServerTickEvent.Post event) {
        MinecraftServer server = event.getServer();
        if (DOTS.isEmpty() || server.getTickCount() % 20 != 0) return;
        for (var entry : new ArrayList<>(DOTS.entrySet())) {
            String key = entry.getKey();
            Dot dot = entry.getValue();
            UUID victimId = UUID.fromString(key.substring(0, key.indexOf(':')));
            ServerLevel level = server.getLevel(dot.dimension());
            Entity entity = level == null ? null : level.getEntity(victimId);
            if (!(entity instanceof LivingEntity victim) || !victim.isAlive()) {
                DOTS.remove(key);
                continue;
            }
            Entity attacker = dot.attacker() == null ? null : level.getEntity(dot.attacker());
            secondary = true;
            try {
                victim.hurtServer(level, level.damageSources().indirectMagic(attacker == null ? victim : attacker, attacker), (float) dot.perSecond());
            } finally {
                secondary = false;
            }
            if (dot.secondsLeft() <= 1) DOTS.remove(key);
            else DOTS.put(key, new Dot(dot.dimension(), dot.attacker(), dot.perSecond(), dot.secondsLeft() - 1));
        }
    }

    /** Active damage-over-time effects, for the checks. */
    public static int activeDots() {
        return DOTS.size();
    }

    /** Drops every damage-over-time effect (tests start clean). */
    public static void clearDots() {
        DOTS.clear();
    }
}
