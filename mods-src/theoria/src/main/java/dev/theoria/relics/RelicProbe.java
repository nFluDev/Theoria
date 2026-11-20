package dev.theoria.relics;

import dev.theoria.core.check.Checks;
import dev.theoria.curios.WornCurios;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Holder;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.tags.TagKey;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.effect.MobEffect;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.EntityTypes;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.ai.attributes.Attribute;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.phys.Vec3;

/**
 * The per-relic test (Faz 12): puts the REAL relic item into the player's REAL Curios slot, lets the REAL effect code run, checks what that kind promises, takes the relic off and checks
 * the world went back. It restores everything it touches (the slot's previous stack, hunger, the held item, the health, effects, cooldowns, temporary entities), so it is safe in a survival
 * world — {@code /theoria test relic <id>} runs it on the command's player, and the {@code relic_<id>} GameTests run it on a survival mock player (plus edge cases in RelicTests).
 */
public final class RelicProbe {
    private RelicProbe() {}

    /** The part that needs no player: the item exists, it is in its Curios tag, the tooltip builds, every source is a real row. */
    public static String data(RelicData.Def def) {
        Item item = RelicItems.RELICS.containsKey(def.id()) ? RelicItems.RELICS.get(def.id()).get() : null;
        Checks.require(item != null, "no item registered for relic " + def.id());
        ItemStack stack = new ItemStack(item);
        TagKey<Item> slotTag = TagKey.create(Registries.ITEM, Identifier.fromNamespaceAndPath("curios", def.slot()));
        Checks.require(stack.is(slotTag), def.id() + " is not in #curios:" + def.slot() + " — Curios would refuse it in that slot");
        Checks.require(!RelicText.tooltip(def).isEmpty() && RelicText.tooltip(def).size() >= 4, "tooltip of " + def.id() + " is incomplete");
        for (RelicData.Source source : def.sources()) {
            Checks.require(RelicData.table().sources().stream().anyMatch(s -> s.id().equals(source.source())), def.id() + " names unknown source " + source.source());
        }
        return def.kind().name().toLowerCase() + " in #curios:" + def.slot() + ", " + def.sources().size() + " source(s), tooltip " + RelicText.tooltip(def).size() + " lines";
    }

    /** The whole test on {@code player} (data part included). Leaves no trace. */
    public static String run(ServerPlayer player, RelicData.Def def) {
        String dataPart = data(def);
        String slot = def.slot();
        Checks.require(WornCurios.slotCount(player, slot) > 0, "the player has no '" + slot + "' Curios slot");
        ItemStack relic = RelicItems.stack(def.id());
        Checks.require(WornCurios.accepts(player, slot, relic), "Curios refuses " + def.id() + " in its own '" + slot + "' slot");
        RelicEffects.refresh(player); // sync the cache with what is really worn before we add anything
        double immuneBefore = def.kind() == RelicData.Kind.IMMUNE ? RelicDamage.immuneFactor(player, sample(player.level(), def)) : 0.0;
        int index = WornCurios.equip(player, slot, relic);
        ItemStack displaced = ItemStack.EMPTY;
        if (index < 0) { // every slot of that type is taken: borrow the first one and give it back
            index = 0;
            displaced = WornCurios.swap(player, slot, 0, relic);
        }
        String live;
        MobEffectInstance keptEffect = def.kind() == RelicData.Kind.EFFECT ? existing(player, def) : null;
        try {
            RelicEffects.refresh(player);
            Checks.require(RelicEffects.worn(player).contains(def.id()), def.id() + " is in the slot but the effect code does not see it worn");
            live = verify(player, def, index);
        } finally {
            WornCurios.swap(player, slot, index, displaced);
            RelicEffects.refresh(player);
        }
        Checks.require(!RelicEffects.worn(player).contains(def.id()) || !displaced.isEmpty(), def.id() + " is still seen as worn after it was taken off");
        afterOff(player, def, keptEffect, immuneBefore);
        return dataPart + "; live: " + live + "; removed cleanly";
    }

    // ------------------------------------------------------------------------------------------------ per kind
    private static String verify(ServerPlayer player, RelicData.Def def, int index) {
        ServerLevel level = player.level();
        switch (def.kind()) {
            case EFFECT -> {
                Holder<MobEffect> effect = BuiltInRegistries.MOB_EFFECT.get(Identifier.parse(def.str("effect"))).orElseThrow();
                MobEffectInstance have = player.getEffect(effect);
                Checks.require(have != null && have.getAmplifier() == (int) def.num("amp"), "effect " + def.str("effect") + " not active at amplifier " + (int) def.num("amp"));
                Checks.require(have.isAmbient() && !have.isVisible(), "the relic's effect must be ambient and hide its particles");
                Checks.require(have.getDuration() >= 200, "effect duration " + have.getDuration() + " < 200 would flicker");
                return def.str("effect") + " active (" + have.getDuration() + " ticks, hidden)";
            }
            case ATTRIBUTE -> {
                for (int n = 0; n < def.mods().size(); n++) {
                    RelicData.Mod mod = def.mods().get(n);
                    AttributeInstance instance = attribute(player, mod.attribute());
                    var modifier = instance.getModifier(def.modifierId(n));
                    Checks.require(modifier != null && modifier.amount() == mod.amount() && modifier.operation() == mod.op(), "modifier " + n + " of " + def.id() + " missing or wrong on " + mod.attribute());
                }
                String stacking = "";
                RelicData.Mod first = def.mods().get(0);
                if (WornCurios.slotCount(player, def.slot()) >= 2) { // the same relic twice must not stack
                    double one = attribute(player, first.attribute()).getValue();
                    int second = WornCurios.equip(player, def.slot(), RelicItems.stack(def.id()));
                    if (second >= 0) {
                        try {
                            RelicEffects.refresh(player);
                            double two = attribute(player, first.attribute()).getValue();
                            Checks.require(Math.abs(one - two) < 1.0E-9, def.id() + " worn twice stacked: " + one + " → " + two);
                            stacking = ", two copies do not stack";
                        } finally {
                            WornCurios.swap(player, def.slot(), second, ItemStack.EMPTY);
                        }
                    }
                }
                return def.mods().size() + " modifier(s) on the player" + stacking + " (" + first.attribute() + " = " + attribute(player, first.attribute()).getValue() + ")";
            }
            case MAGNET -> {
                boolean wasSneaking = player.isShiftKeyDown();
                Vec3 at = nearby(level, player, def.num("radius") / 2.0);
                ItemEntity item = new ItemEntity(level, at.x, at.y, at.z, new ItemStack(Items.STICK));
                item.setDeltaMovement(Vec3.ZERO);
                level.addFreshEntity(item);
                try {
                    int pulled = RelicEffects.pull(player, def);
                    Checks.require(pulled >= 1, "the magnet pulled nothing within " + def.num("radius") + " blocks");
                    Vec3 toPlayer = player.position().subtract(item.position());
                    Checks.require(item.getDeltaMovement().dot(toPlayer) > 0.0, "the item was not moved toward the player");
                    item.setDeltaMovement(Vec3.ZERO);
                    player.setShiftKeyDown(true);
                    Checks.require(RelicEffects.pull(player, def) == 0, "sneaking must switch the magnet off");
                    player.setShiftKeyDown(false);
                    item.setThrower(player);
                    Checks.require(RelicEffects.pull(player, def) == 0, "items the wearer threw must be left alone");
                } finally {
                    player.setShiftKeyDown(wasSneaking);
                    item.discard();
                }
                return "pulls items within " + def.num("radius") + " blocks, not while sneaking, not your own drops";
            }
            case FOOD -> {
                int food = player.getFoodData().getFoodLevel();
                float saturation = player.getFoodData().getSaturationLevel();
                try {
                    player.getFoodData().setFoodLevel(10);
                    Checks.require(RelicEffects.feed(player, def) && player.getFoodData().getFoodLevel() == 10 + (int) def.num("food"), "hunger was not refilled by " + def.num("food"));
                    player.getFoodData().setFoodLevel(20);
                    Checks.require(!RelicEffects.feed(player, def), "a full stomach must not be fed");
                } finally {
                    player.getFoodData().setFoodLevel(food);
                    player.getFoodData().setSaturation(saturation);
                }
                return "+" + (int) def.num("food") + " hunger per " + (int) def.num("interval") + " ticks, only while hungry";
            }
            case REPAIR -> {
                ItemStack held = player.getItemBySlot(EquipmentSlot.MAINHAND);
                ItemStack worn = new ItemStack(Items.IRON_SWORD);
                worn.setDamageValue(10);
                player.setItemSlot(EquipmentSlot.MAINHAND, worn);
                try {
                    Checks.require(RelicEffects.mend(player, def) && worn.getDamageValue() == 10 - (int) def.num("amount"), "damage 10 → " + worn.getDamageValue() + ", expected " + (10 - (int) def.num("amount")));
                    ItemStack fresh = new ItemStack(Items.IRON_SWORD);
                    player.setItemSlot(EquipmentSlot.MAINHAND, fresh);
                    Checks.require(!RelicEffects.mend(player, def), "undamaged gear must be left alone");
                } finally {
                    player.setItemSlot(EquipmentSlot.MAINHAND, held);
                }
                return "mends " + (int) def.num("amount") + " durability per " + (int) def.num("interval") + " ticks";
            }
            case IMMUNE -> {
                DamageSource source = sample(level, def);
                double factor = RelicDamage.immuneFactor(player, source);
                Checks.require(factor >= def.num("factor") - 1.0E-9, "damage of " + def.str("damage") + " reduced by " + factor + ", expected " + def.num("factor"));
                Checks.require(RelicDamage.immuneFactor(player, level.damageSources().generic()) < 1.0 - 1.0E-9, "an unrelated damage type must not be removed completely");
                return def.str("damage") + " reduced by " + Math.round(factor * 100) + "%";
            }
            case REVEAL -> {
                Mob zombie = EntityTypes.ZOMBIE.create(level, EntitySpawnReason.COMMAND);
                Checks.require(zombie != null, "could not create a test zombie");
                zombie.setNoAi(true);
                Vec3 at = nearby(level, player, 2.0);
                zombie.setPos(at.x, at.y, at.z);
                level.addFreshEntity(zombie);
                try {
                    Checks.require(RelicEffects.reveal(player, def) >= 1 && zombie.hasEffect(MobEffects.GLOWING), "the zombie two blocks away does not glow");
                } finally {
                    zombie.discard();
                }
                return "hostile mobs within " + def.num("radius") + " blocks glow";
            }
            case SURVIVE -> {
                ItemStack stack = RelicEffects.wornStack(player, def.id());
                float health = player.getHealth();
                try {
                    float result = RelicDamage.survive(player, player.getHealth() + player.getAbsorptionAmount() + 1000.0F);
                    Checks.require(result == 0.0F, "a lethal hit was not turned into nothing");
                    Checks.require(player.getHealth() >= def.num("heal") * player.getMaxHealth() - 1.0E-3, "health not restored to " + def.num("heal") + " of the maximum");
                    Checks.require(player.getCooldowns().isOnCooldown(stack), "the cooldown did not start");
                    float again = RelicDamage.survive(player, 1000.0F);
                    Checks.require(again == 1000.0F, "a second lethal hit during the cooldown must go through");
                } finally {
                    player.getCooldowns().removeCooldown(player.getCooldowns().getCooldownGroup(stack));
                    player.setHealth(Math.min(health, player.getMaxHealth()));
                }
                return "survives one lethal hit, cooldown " + (int) def.num("cooldown") + " ticks";
            }
        }
        return "";
    }

    private static void afterOff(ServerPlayer player, RelicData.Def def, MobEffectInstance keptEffect, double immuneBefore) {
        switch (def.kind()) {
            case ATTRIBUTE -> {
                for (int n = 0; n < def.mods().size(); n++) {
                    AttributeInstance instance = attribute(player, def.mods().get(n).attribute());
                    Checks.require(instance.getModifier(def.modifierId(n)) == null, "modifier " + n + " of " + def.id() + " is still on the player after taking the relic off");
                }
            }
            case EFFECT -> {
                Holder<MobEffect> effect = BuiltInRegistries.MOB_EFFECT.get(Identifier.parse(def.str("effect"))).orElseThrow();
                if (keptEffect == null) Checks.require(!player.hasEffect(effect), def.str("effect") + " is still active after taking the relic off");
                else player.addEffect(new MobEffectInstance(keptEffect)); // the player had it already (a potion): give it back
            }
            case IMMUNE -> Checks.require(Math.abs(RelicDamage.immuneFactor(player, sample(player.level(), def)) - immuneBefore) < 1.0E-9, "the damage share is still removed after taking the relic off");
            default -> {}
        }
    }

    /** A point {@code distance} blocks from the player inside a LOADED chunk (a probe may stand next to a chunk edge; entities added to an unloaded chunk silently vanish). */
    private static Vec3 nearby(ServerLevel level, ServerPlayer player, double distance) {
        double[][] directions = {{1, 0}, {-1, 0}, {0, 1}, {0, -1}};
        for (double[] d : directions) {
            Vec3 at = new Vec3(player.getX() + d[0] * distance, player.getY(), player.getZ() + d[1] * distance);
            if (level.hasChunkAt(BlockPos.containing(at))) return at;
        }
        return player.position();
    }

    private static MobEffectInstance existing(ServerPlayer player, RelicData.Def def) {
        Holder<MobEffect> effect = BuiltInRegistries.MOB_EFFECT.get(Identifier.parse(def.str("effect"))).orElse(null);
        MobEffectInstance have = effect == null ? null : player.getEffect(effect);
        return have == null ? null : new MobEffectInstance(have);
    }

    private static AttributeInstance attribute(ServerPlayer player, String id) {
        Holder<Attribute> holder = BuiltInRegistries.ATTRIBUTE.get(Identifier.parse(id)).orElseThrow();
        AttributeInstance instance = player.getAttribute(holder);
        Checks.require(instance != null, "the player has no attribute " + id);
        return instance;
    }

    /** One representative damage source for the damage-type tag of an immune relic. */
    static DamageSource sample(ServerLevel level, RelicData.Def def) {
        return switch (def.str("damage")) {
            case "minecraft:is_fire" -> level.damageSources().lava();
            case "minecraft:is_explosion" -> level.damageSources().explosion(null, null);
            case "minecraft:is_freezing" -> level.damageSources().freeze();
            case "minecraft:is_lightning" -> level.damageSources().lightningBolt();
            default -> throw new IllegalStateException("no sample damage source for " + def.str("damage"));
        };
    }
}
