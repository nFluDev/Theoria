package dev.theoria.mobs;

import com.mojang.serialization.Codec;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import dev.theoria.Theoria;
import dev.theoria.core.Attachments;
import java.util.function.Supplier;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.player.Player;
import net.neoforged.neoforge.attachment.AttachmentType;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;

/**
 * Heart Crystals (boss reward, docs/05 §4.2 "Kalp Kristali"): each one adds a permanent max-health bonus, at most {@code heart.max} of them
 * (docs/06 §3: ≈ +44 health from reward items). The count is a death-surviving player attachment; the attribute modifier is re-derived from it
 * on login, respawn and clone, so rebalancing the per-crystal value reaches existing players.
 */
public final class HeartCrystals {
    public record Count(int value) {
        public static final Count NONE = new Count(0);
        public static final MapCodec<Count> CODEC = RecordCodecBuilder.mapCodec(i -> i.group(Codec.INT.fieldOf("count").forGetter(Count::value)).apply(i, Count::new));
        public static final StreamCodec<net.minecraft.network.RegistryFriendlyByteBuf, Count> STREAM_CODEC = ByteBufCodecs.VAR_INT.map(Count::new, Count::value).cast();
    }

    private static final Identifier MODIFIER = Theoria.id("heart_crystals");
    private static Supplier<AttachmentType<Count>> count;

    private HeartCrystals() {}

    static void init() {
        count = Attachments.playerState("heart_crystals", () -> Count.NONE, Count.CODEC, Count.STREAM_CODEC);
        NeoForge.EVENT_BUS.addListener((PlayerEvent.PlayerLoggedInEvent e) -> apply(e.getEntity()));
        NeoForge.EVENT_BUS.addListener((PlayerEvent.PlayerRespawnEvent e) -> apply(e.getEntity()));
        NeoForge.EVENT_BUS.addListener((PlayerEvent.Clone e) -> apply(e.getEntity()));
    }

    public static int count(Player player) {
        return player.getData(count).value();
    }

    /** Total max-health bonus the crystals give this player. */
    public static int bonus(Player player) {
        return count(player) * MobData.balance().heartHp();
    }

    /** Takes one crystal into the player's total. False when the cap is reached. */
    public static boolean consume(ServerPlayer player) {
        int now = count(player);
        if (now >= MobData.balance().heartMax()) return false;
        player.setData(count, new Count(now + 1));
        apply(player);
        return true;
    }

    public static void set(Player player, int value) {
        player.setData(count, new Count(Math.max(0, value)));
        apply(player);
    }

    static void apply(Player player) {
        AttributeInstance health = player.getAttribute(Attributes.MAX_HEALTH);
        if (health == null) return;
        int bonus = bonus(player);
        float old = player.getMaxHealth();
        if (bonus == 0) {
            health.removeModifier(MODIFIER);
        } else {
            health.addOrReplacePermanentModifier(new AttributeModifier(MODIFIER, bonus, AttributeModifier.Operation.ADD_VALUE));
        }
        float now = player.getMaxHealth();
        if (now > old) player.setHealth(Math.min(now, player.getHealth() + (now - old)));   // a fresh crystal also fills what it added (not a heal: no heal scaling)
        else if (player.getHealth() > now) player.setHealth(now);
    }
}
