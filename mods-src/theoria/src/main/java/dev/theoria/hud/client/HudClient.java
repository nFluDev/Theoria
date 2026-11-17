package dev.theoria.hud.client;

import dev.theoria.Theoria;
import java.util.Set;
import net.minecraft.client.Minecraft;
import net.minecraft.resources.Identifier;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.client.event.RegisterGuiLayersEvent;
import net.neoforged.neoforge.client.event.RenderGuiLayerEvent;
import net.neoforged.neoforge.client.gui.VanillaGuiLayers;
import net.neoforged.neoforge.common.NeoForge;

/**
 * Client wiring of the Phase 3 HUD (ADR-002 a). Our status layer is registered right above the vanilla
 * {@code player_health} slot; the vanilla health / armor / hunger / vehicle layers are cancelled; the
 * {@code experience_level} layer is replaced (level text inside the XP bar). No mixins, no GL — vanilla GUI render types only.
 */
public final class HudClient {
    public static final Identifier STATUS_LAYER = Theoria.id("status_bars");
    /** Layers the status layer draws for; cancelled rather than replaced so other mods that wrap them keep working (ADR-002). */
    private static final Set<Identifier> CANCELLED = Set.of(VanillaGuiLayers.PLAYER_HEALTH, VanillaGuiLayers.ARMOR_LEVEL, VanillaGuiLayers.FOOD_LEVEL);

    // Dev scenario evidence (devtest hud): the layers really ran, the vanilla ones really stayed quiet.
    static long statusDraws;
    static long levelDraws;
    static long cancelledLayers;
    static long blinkFrames;
    static long lowHealthFrames;
    static long regenFrames;
    static long hungerShakeFrames;

    private HudClient() {}

    public static void init(IEventBus modBus) {
        modBus.addListener(HudClient::registerLayers);
        NeoForge.EVENT_BUS.addListener(HudClient::onLayerPre);
    }

    private static void registerLayers(RegisterGuiLayersEvent event) {
        event.registerAbove(VanillaGuiLayers.PLAYER_HEALTH, STATUS_LAYER, StatusLayer::render);
        event.replaceLayer(VanillaGuiLayers.EXPERIENCE_LEVEL, LevelLayer::render);
    }

    private static void onLayerPre(RenderGuiLayerEvent.Pre event) {
        Identifier name = event.getName();
        // Vehicle hearts are ours only while the survival HUD shows; a creative rider keeps vanilla's (vanilla draws them regardless of mode).
        if (CANCELLED.contains(name) || name.equals(VanillaGuiLayers.VEHICLE_HEALTH) && StatusLayer.survivalVisible(Minecraft.getInstance())) {
            event.setCanceled(true);
            cancelledLayers++;
        }
    }

    /**
     * {statusDraws, levelDraws, cancelledLayers, blinkFrames, lowHealthFrames, regenFrames, hungerShakeFrames} since start —
     * read by the unattended client scenario.
     */
    public static long[] counters() {
        return new long[] {statusDraws, levelDraws, cancelledLayers, blinkFrames, lowHealthFrames, regenFrames, hungerShakeFrames};
    }
}
