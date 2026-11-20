package dev.theoria.stats;

import dev.theoria.core.Attachments;
import dev.theoria.core.Net;
import dev.theoria.core.TheoriaRegistries;
import java.util.function.Supplier;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.Rarity;
import net.neoforged.bus.api.EventPriority;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.attachment.AttachmentType;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.event.entity.player.PlayerXpEvent;
import net.neoforged.neoforge.event.tick.PlayerTickEvent;
import net.neoforged.neoforge.registries.DeferredItem;

/**
 * Phase 10 entry point (common side): the {@code theoria:stats} attachment (lifetime XP + allocated points), the allocate request, the XP and tick hooks,
 * the Orb of Oblivion and {@code /theoria stats}. The screen, key and HUD level are client-only ({@code dev.theoria.stats.client}, wired from TheoriaClient).
 */
public final class StatsModule {
    /** {@code theoria:stats}: saved, synced to the owner, copied on death — levels are for life (D5). */
    public static Supplier<AttachmentType<PlayerStats>> STATS;
    /** The exhaustion level seen on the previous tick, NaN = not seen yet (Satiety). Neither saved nor synced. */
    public static Supplier<AttachmentType<float[]>> LAST_EXHAUSTION;
    public static DeferredItem<Item> OBLIVION_ORB;

    private StatsModule() {}

    public static void init(IEventBus modBus) {
        STATS = Attachments.playerState("stats", () -> PlayerStats.NEW, PlayerStats.CODEC, PlayerStats.STREAM_CODEC);
        LAST_EXHAUSTION = Attachments.register("stats_last_exhaustion", () -> new float[] {Float.NaN}, b -> b);
        OBLIVION_ORB = TheoriaRegistries.ITEMS.registerItem("oblivion_orb", OblivionOrbItem::new, () -> new Item.Properties().stacksTo(16).rarity(Rarity.EPIC));
        Net.serverbound(AllocatePointsPayload.TYPE, AllocatePointsPayload.STREAM_CODEC, StatsServer::onAllocateRequest);
        // The attribute modifiers live on the entity: a new entity (respawn, clone, dimension change, login) needs them derived again.
        NeoForge.EVENT_BUS.addListener((PlayerEvent.PlayerLoggedInEvent e) -> StatsEffects.apply(e.getEntity()));
        NeoForge.EVENT_BUS.addListener((PlayerEvent.PlayerRespawnEvent e) -> StatsEffects.apply(e.getEntity()));
        NeoForge.EVENT_BUS.addListener((PlayerEvent.Clone e) -> StatsEffects.apply(e.getEntity()));
        NeoForge.EVENT_BUS.addListener((PlayerEvent.PlayerChangedDimensionEvent e) -> StatsEffects.apply(e.getEntity()));
        NeoForge.EVENT_BUS.addListener(EventPriority.LOWEST, false, PlayerXpEvent.XpChange.class, StatsServer::onXpChange);
        NeoForge.EVENT_BUS.addListener(PlayerTickEvent.Post.class, StatsServer::onTick);
        StatsCommand.init();
        StatsChecks.init();
    }

    public static PlayerStats of(Player player) {
        return player.getData(STATS);
    }
}
