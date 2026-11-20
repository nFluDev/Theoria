package dev.theoria.stats;

import dev.theoria.Theoria;
import java.util.HashMap;
import java.util.Map;
import net.minecraft.ChatFormatting;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.food.FoodData;
import net.neoforged.neoforge.event.entity.player.PlayerXpEvent;
import net.neoforged.neoforge.event.tick.PlayerTickEvent;

/**
 * The server half of the Status screen (docs/04 §5 "Sunucu doğrular, istemci sadece ister"): lifetime XP, level-up feedback, point allocation, the
 * Orb-of-Oblivion reset, and Satiety's hunger-drain reduction. Every mutation goes through {@code setData}, which syncs the attachment to the owner.
 */
public final class StatsServer {
    public enum Result { OK, NOTHING, UNKNOWN_STAT, TOO_MANY }

    private StatsServer() {}

    // ------------------------------------------------------------------------------------------------ XP
    /**
     * Every point of vanilla XP the player RECEIVES also counts towards the lifetime total. Enchanting and the anvil take vanilla LEVELS
     * ({@code giveExperienceLevels(-n)}), which fire no {@code XpChange}, and even a negative change is ignored here: spending never lowers the level.
     * LOWEST + not-cancelled: the amount other mods (and Bilgelik) have settled on is what the player really got.
     */
    public static void onXpChange(PlayerXpEvent.XpChange event) {
        if (!(event.getEntity() instanceof ServerPlayer player) || event.getAmount() <= 0) return;
        addXp(player, event.getAmount());
    }

    /** Adds lifetime XP and, if a level (or more) was gained, tells the player. */
    public static void addXp(ServerPlayer player, long amount) {
        PlayerStats before = StatsModule.of(player);
        PlayerStats after = before.addXp(amount);
        if (after.xp() == before.xp()) return;
        player.setData(StatsModule.STATS, after);
        announceLevelUp(player, before.level(), after.level());
    }

    /** Sets the lifetime XP outright (commands, tests): no level-up feedback, derived effects re-applied (a lower level can leave points over-spent). */
    public static void setXp(ServerPlayer player, long xp) {
        player.setData(StatsModule.STATS, StatsModule.of(player).withXp(xp));
        StatsEffects.apply(player);
    }

    private static void announceLevelUp(ServerPlayer player, int from, int to) {
        if (to <= from) return;
        int gained = (to - from) * StatsData.leveling().pointsPerLevel();
        player.sendSystemMessage(Component.translatable("theoria.stats.levelup", to, gained).withStyle(ChatFormatting.GOLD), true);
        player.level().playSound(null, player, SoundEvents.PLAYER_LEVELUP, SoundSource.PLAYERS, 0.7F, 1.0F);
        if (player.level() instanceof ServerLevel level) {
            level.sendParticles(ParticleTypes.ENCHANT, player.getX(), player.getY() + 1.0, player.getZ(), 40, 0.5, 0.9, 0.5, 0.6);
        }
    }

    // ------------------------------------------------------------------------------------------------ points
    /**
     * Spends points as asked. All or nothing: an unknown stat, a non-positive amount or more points than the player has rejects the whole request, so a
     * hostile or stale client cannot half-apply it. A request over {@link PlayerStats#MAX_ENTRIES} never gets here (the codec stops it).
     */
    public static Result allocate(ServerPlayer player, Map<String, Integer> request) {
        PlayerStats stats = StatsModule.of(player);
        long total = 0;
        Map<String, Integer> add = new HashMap<>();
        for (Map.Entry<String, Integer> e : request.entrySet()) {
            if (e.getKey() == null || StatsData.stat(e.getKey()) == null) return Result.UNKNOWN_STAT;
            Integer n = e.getValue();
            if (n == null || n < 0) return Result.UNKNOWN_STAT;
            if (n == 0) continue;
            total += n;
            add.put(e.getKey(), n);
        }
        if (total == 0) return Result.NOTHING;
        if (total > stats.available()) return Result.TOO_MANY;
        player.setData(StatsModule.STATS, stats.allocate(add));
        StatsEffects.apply(player);
        return Result.OK;
    }

    /** Handles the screen's request and answers with a short action-bar line. */
    public static void onAllocateRequest(AllocatePointsPayload payload, ServerPlayer player) {
        Result result = allocate(player, payload.add());
        switch (result) {
            case OK -> player.sendSystemMessage(Component.translatable("theoria.stats.allocated", payload.add().values().stream().mapToInt(Integer::intValue).sum()).withStyle(ChatFormatting.GREEN), true);
            case TOO_MANY -> player.sendSystemMessage(Component.translatable("theoria.stats.rejected").withStyle(ChatFormatting.RED), true);
            case UNKNOWN_STAT -> {
                Theoria.LOGGER.warn("{} sent a stat request with an unknown stat or a negative amount — ignored", player.getGameProfile().name());
                player.sendSystemMessage(Component.translatable("theoria.stats.rejected").withStyle(ChatFormatting.RED), true);
            }
            case NOTHING -> { }
        }
    }

    /** Orb of Oblivion: every allocated point comes back, the level stays. Returns the points returned (0 = nothing to reset). */
    public static int reset(ServerPlayer player) {
        PlayerStats stats = StatsModule.of(player);
        int spent = stats.spent();
        if (spent == 0) return 0;
        player.setData(StatsModule.STATS, stats.cleared());
        StatsEffects.apply(player);
        return spent;
    }

    // ------------------------------------------------------------------------------------------------ Satiety
    /**
     * Hunger drain: whatever exhaustion the player's body gained since the last tick is partly given back ({@code exhaustion} effect). Vanilla has no event
     * for exhaustion, so we read the tick-to-tick difference: a rise is new exhaustion, a fall is vanilla spending it ({@code FoodData#tick}) and is left alone.
     */
    public static void onTick(PlayerTickEvent.Post event) {
        if (!(event.getEntity() instanceof ServerPlayer player)) return;
        FoodData food = player.getFoodData();
        float[] last = player.getData(StatsModule.LAST_EXHAUSTION);
        float now = food.exhaustionLevel;
        if (!Float.isNaN(last[0]) && now > last[0]) {
            double reduction = StatsEffects.exhaustionReduction(player);
            if (reduction > 0.0) {
                now = (float) (now - (now - last[0]) * reduction);
                food.exhaustionLevel = now;
            }
        }
        last[0] = now;
    }
}
