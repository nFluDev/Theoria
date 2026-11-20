package dev.theoria.relics;

import dev.theoria.curios.WornCurios;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import net.minecraft.core.Holder;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.effect.MobEffect;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.Attribute;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.monster.Enemy;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.event.tick.PlayerTickEvent;

/**
 * What worn relics DO, server side (Faz 12). One rule for everything: ask the Curios door ({@link WornCurios}) which relics the player wears, then make the world match — attribute
 * modifiers and hidden mob effects are re-derived once a second (idempotent, so respawn/login/dimension change need no special case), timed effects (magnet, hunger, mending, reveal)
 * run on their own interval, and the two reactive kinds (immunity, surviving a deadly blow) live in {@link RelicDamage}. The same relic worn twice counts once.
 * Every action is a public static method taking the player and the table row, so the GameTests and {@code /theoria test relic} drive the real code, not a copy.
 */
public final class RelicEffects {
    /** Ticks between two derivations of attribute modifiers and mob effects. */
    public static final int REFRESH = 20;
    /** Duration of the hidden mob effect: night vision flickers below 200 ticks, so it is re-applied before it gets there. */
    public static final int EFFECT_TICKS = 260;
    private static final int EFFECT_RENEW_BELOW = 240;
    private static final EquipmentSlot[] MEND_SLOTS = {EquipmentSlot.MAINHAND, EquipmentSlot.OFFHAND, EquipmentSlot.HEAD, EquipmentSlot.CHEST, EquipmentSlot.LEGS, EquipmentSlot.FEET};

    /** The relics each player was last seen wearing (cleared on logout). The reactive hooks read this instead of walking the Curios inventory on every hit. */
    private static final Map<UUID, Set<String>> WORN = new ConcurrentHashMap<>();

    /** Registry lookups by id, cached (refresh() resolves them for every relic of every player once a second). */
    private static final Map<String, Holder<Attribute>> ATTRIBUTES = new ConcurrentHashMap<>();
    private static final Map<String, Holder<MobEffect>> EFFECTS = new ConcurrentHashMap<>();

    private RelicEffects() {}

    public static void init() {
        NeoForge.EVENT_BUS.addListener(PlayerTickEvent.Post.class, event -> {
            if (event.getEntity() instanceof ServerPlayer player) tick(player);
        });
        NeoForge.EVENT_BUS.addListener(PlayerEvent.PlayerLoggedOutEvent.class, event -> WORN.remove(event.getEntity().getUUID()));
    }

    // ------------------------------------------------------------------------------------------------ what is worn
    /** Ids of the relics the entity wears right now (reads the Curios inventory). */
    public static Set<String> worn(LivingEntity entity) {
        Set<String> ids = new LinkedHashSet<>();
        for (ItemStack stack : WornCurios.stacks(entity)) {
            if (stack.getItem() instanceof RelicItem relic) ids.add(relic.def().id());
        }
        return ids;
    }

    /** The relics the player wore at the last refresh. */
    public static Set<String> wornCached(ServerPlayer player) {
        return WORN.getOrDefault(player.getUUID(), Set.of());
    }

    /** The first worn relic stack of this relic id (for the item cooldown of the survive kind), or EMPTY. */
    public static ItemStack wornStack(LivingEntity entity, String id) {
        for (ItemStack stack : WornCurios.stacks(entity)) {
            if (stack.getItem() instanceof RelicItem relic && relic.def().id().equals(id)) return stack;
        }
        return ItemStack.EMPTY;
    }

    // ------------------------------------------------------------------------------------------------ the tick
    /** Runs every server tick for each player ({@code tickCount} decides what is due). */
    public static void tick(ServerPlayer player) {
        int t = player.tickCount;
        if (t % REFRESH == 0) refresh(player);
        for (String id : wornCached(player)) {
            RelicData.Def def = RelicData.get(id);
            if (def == null) continue;
            switch (def.kind()) {
                case MAGNET -> {
                    if (t % 2 == 0) pull(player, def);
                }
                case FOOD -> {
                    if (t % (int) def.num("interval") == 0) feed(player, def);
                }
                case REPAIR -> {
                    if (t % (int) def.num("interval") == 0) mend(player, def);
                }
                case REVEAL -> {
                    if (t % (int) def.num("interval") == 0) reveal(player, def);
                }
                case IMMUNE -> {
                    if (t % 10 == 0 && RelicDamage.isFire(def) && def.num("factor") >= 1.0 && player.isOnFire()) player.clearFire();
                }
                default -> {}
            }
        }
    }

    /** Re-derives attribute modifiers and mob effects from what is worn now. Idempotent. */
    public static void refresh(ServerPlayer player) {
        Set<String> now = worn(player);
        Set<String> before = WORN.put(player.getUUID(), now);
        for (RelicData.Def def : RelicData.all()) {
            boolean on = now.contains(def.id());
            switch (def.kind()) {
                case ATTRIBUTE -> applyModifiers(player, def, on);
                case EFFECT -> applyEffect(player, def, on, before != null && before.contains(def.id()));
                default -> {}
            }
        }
    }

    /** Drops the cached worn set (a probe that put a relic on and took it off again calls {@link #refresh} first, then this, to leave no trace). */
    public static void forget(ServerPlayer player) {
        WORN.remove(player.getUUID());
    }

    // ------------------------------------------------------------------------------------------------ attribute + effect
    private static void applyModifiers(ServerPlayer player, RelicData.Def def, boolean on) {
        for (int n = 0; n < def.mods().size(); n++) {
            RelicData.Mod mod = def.mods().get(n);
            Holder<Attribute> holder = ATTRIBUTES.computeIfAbsent(mod.attribute(), key -> BuiltInRegistries.ATTRIBUTE.get(Identifier.parse(key)).orElse(null)); // cached: this runs per relic per player every second
            AttributeInstance instance = holder == null ? null : player.getAttribute(holder);
            if (instance == null) continue;
            Identifier id = def.modifierId(n);
            if (on) {
                AttributeModifier have = instance.getModifier(id);
                if (have == null || have.amount() != mod.amount() || have.operation() != mod.op()) instance.addOrUpdateTransientModifier(new AttributeModifier(id, mod.amount(), mod.op()));
            } else {
                instance.removeModifier(id);
            }
        }
    }

    private static void applyEffect(ServerPlayer player, RelicData.Def def, boolean on, boolean wasOn) {
        Holder<MobEffect> effect = EFFECTS.computeIfAbsent(def.str("effect"), key -> BuiltInRegistries.MOB_EFFECT.get(Identifier.parse(key)).orElse(null));
        if (effect == null) return;
        int amp = (int) def.num("amp");
        MobEffectInstance have = player.getEffect(effect);
        if (on) {
            if (have == null || have.getDuration() < EFFECT_RENEW_BELOW || have.getAmplifier() < amp) player.addEffect(new MobEffectInstance(effect, EFFECT_TICKS, amp, true, false, true));
        } else if (wasOn && have != null && have.isAmbient() && !have.isVisible() && have.getDuration() <= EFFECT_TICKS) {
            player.removeEffect(effect); // only OUR kind of instance: ambient, hidden particles, our duration — a potion or a beacon is left alone
        }
    }

    // ------------------------------------------------------------------------------------------------ timed kinds
    /** Magnet: pulls item entities in {@code radius} toward the player. Sneaking switches it off; items the player threw are left alone. Returns how many were pulled. */
    public static int pull(ServerPlayer player, RelicData.Def def) {
        if (player.isShiftKeyDown() || player.isSpectator()) return 0;
        double radius = def.num("radius");
        double speed = def.num("pull");
        Vec3 target = player.position().add(0.0, player.getBbHeight() / 2.0, 0.0);
        int pulled = 0;
        for (ItemEntity item : player.level().getEntitiesOfClass(ItemEntity.class, player.getBoundingBox().inflate(radius), i -> i.isAlive() && i.getOwner() != player)) {
            Vec3 to = target.subtract(item.position());
            double distance = to.length();
            if (distance > radius || distance < 0.5) continue;
            item.setDeltaMovement(to.scale(Math.min(speed, distance) / distance));
            item.needsSync = true;
            pulled++;
        }
        return pulled;
    }

    /** Eternal Apple: refills hunger while it is not full. */
    public static boolean feed(ServerPlayer player, RelicData.Def def) {
        if (!player.getFoodData().needsFood()) return false;
        player.getFoodData().eat((int) def.num("food"), (float) def.num("saturation"));
        return true;
    }

    /** Mender's Belt: gives {@code amount} durability back to the most worn piece of held/armor gear. */
    public static boolean mend(ServerPlayer player, RelicData.Def def) {
        ItemStack worst = ItemStack.EMPTY;
        double worstShare = 0.0;
        for (EquipmentSlot slot : MEND_SLOTS) {
            ItemStack stack = player.getItemBySlot(slot);
            if (stack.isEmpty() || !stack.isDamaged()) continue;
            double share = stack.getDamageValue() / (double) stack.getMaxDamage();
            if (share > worstShare) {
                worstShare = share;
                worst = stack;
            }
        }
        if (worst.isEmpty()) return false;
        worst.setDamageValue(Math.max(0, worst.getDamageValue() - (int) def.num("amount")));
        return true;
    }

    /** Watcher's Circlet: hostile creatures within {@code radius} glow for {@code duration} ticks. Returns how many. */
    public static int reveal(ServerPlayer player, RelicData.Def def) {
        double radius = def.num("radius");
        int duration = (int) def.num("duration");
        int count = 0;
        for (LivingEntity e : player.level().getEntitiesOfClass(LivingEntity.class, new AABB(player.blockPosition()).inflate(radius), e -> e instanceof Enemy && e.isAlive() && e.distanceToSqr(player) <= radius * radius)) {
            e.addEffect(new MobEffectInstance(MobEffects.GLOWING, duration, 0, true, false, false));
            count++;
        }
        return count;
    }
}
