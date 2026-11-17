package dev.theoria.balance;

import com.mojang.serialization.Codec;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import dev.theoria.Theoria;
import dev.theoria.core.Attachments;
import java.util.function.Supplier;
import net.minecraft.core.Holder;
import net.minecraft.resources.Identifier;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.Attribute;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.neoforged.neoforge.attachment.AttachmentType;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.entity.EntityJoinLevelEvent;
import org.jspecify.annotations.Nullable;

/**
 * Mob tiers (docs/06 §4). A tiered mob carries a saved {@link MobTier} attachment; the tier picks three multipliers:
 * <ul>
 * <li>{@code mob_hp_mult} → max health and {@code mob_dmg_mult} → the mob's own armor: permanent attribute modifiers made here;</li>
 * <li>{@code mob_dmg_mult} / {@code mob_bypass_mult} → damage the mob causes: applied in {@link DamageHooks} (the damage
 * pipeline sees arrows, explosions and potions, which {@code attack_damage} never would — scaling both would count twice).</li>
 * </ul>
 * Who gets which tier (spawn zone, depth) is Phase 8/9; until then a tier is assigned by {@code /theoria tier} or by tests.
 * Tier 0 is vanilla: assigning 0 removes everything this class added.
 */
public final class MobScaling {
    /** The tier index; absent attachment = tier 0. */
    public record MobTier(int idx) {
        public static final MapCodec<MobTier> CODEC = RecordCodecBuilder.mapCodec(i -> i.group(
                Codec.INT.fieldOf("tier").forGetter(MobTier::idx)).apply(i, MobTier::new));
    }

    private static final Identifier HP_MODIFIER = Theoria.id("tier_hp");
    private static final Identifier ARMOR_MODIFIER = Theoria.id("tier_armor");

    /** Saved with the mob, not synced (the server alone decides combat). Registered from {@link #init()}. */
    private static Supplier<AttachmentType<MobTier>> tierAttachment;

    private MobScaling() {}

    public static void init() {
        tierAttachment = Attachments.register("mob_tier", () -> new MobTier(0), b -> b.serialize(MobTier.CODEC));
        NeoForge.EVENT_BUS.addListener(MobScaling::onJoin);
    }

    /** The mob's tier, 0 if it has none. Never creates the attachment. */
    public static int tierOf(LivingEntity mob) {
        MobTier tier = mob.getExistingDataOrNull(tierAttachment);
        return tier == null ? 0 : tier.idx();
    }

    /** True while the mob carries a tier attachment (tier 0 removes it, so an untiered mob saves nothing extra). */
    public static boolean isTiered(LivingEntity mob) {
        return mob.hasData(tierAttachment);
    }

    /** The mob's tier data, or null for an untiered mob or while the balance data is not loaded. */
    public static @Nullable Tier tierDataOf(LivingEntity mob) {
        MobTier tier = mob.getExistingDataOrNull(tierAttachment);
        return tier == null ? null : TierRegistry.tierOrNull(tier.idx());
    }

    /** Puts {@code mob} in tier {@code idx} (0 = back to vanilla). Health keeps its fraction. @throws IllegalArgumentException unknown tier */
    public static void assign(LivingEntity mob, int idx) {
        Tier tier = TierRegistry.tier(idx);
        if (idx == 0) {
            mob.removeData(tierAttachment);
        } else {
            mob.setData(tierAttachment, new MobTier(idx));
        }
        apply(mob, idx == 0 ? null : tier);
    }

    /** Idempotent: brings the attribute modifiers in line with {@code tier} (null = remove them). */
    private static void apply(LivingEntity mob, @Nullable Tier tier) {
        float oldMax = mob.getMaxHealth();
        scale(mob, Attributes.MAX_HEALTH, HP_MODIFIER, tier == null ? 1.0 : tier.mob().hpMult());
        scale(mob, Attributes.ARMOR, ARMOR_MODIFIER, tier == null ? 1.0 : tier.mob().dmgMult());
        float newMax = mob.getMaxHealth();
        if (oldMax > 0 && newMax != oldMax) {
            mob.setHealth(mob.getHealth() * newMax / oldMax);
        }
    }

    /** ADD_MULTIPLIED_TOTAL runs last, so the multiplier covers base value, gear and every other modifier. */
    private static void scale(LivingEntity mob, Holder<Attribute> attribute, Identifier id, double mult) {
        AttributeInstance instance = mob.getAttribute(attribute);
        if (instance == null) return;
        if (mult == 1.0) {
            instance.removeModifier(id);
        } else {
            instance.addOrReplacePermanentModifier(new AttributeModifier(id, mult - 1.0, AttributeModifier.Operation.ADD_MULTIPLIED_TOTAL));
        }
    }

    /** A saved mob comes back with its modifiers, but the numbers may have been rebalanced since: re-apply the current ones. */
    private static void onJoin(EntityJoinLevelEvent event) {
        if (event.getLevel().isClientSide() || !(event.getEntity() instanceof LivingEntity mob)) return;
        MobTier saved = mob.getExistingDataOrNull(tierAttachment);
        if (saved == null) return;
        Tier tier = TierRegistry.tierOrNull(saved.idx());
        if (tier == null) {
            Theoria.LOGGER.warn("{} has unknown mob tier {}; left as saved", mob, saved.idx());
            return;
        }
        apply(mob, tier);
    }
}
