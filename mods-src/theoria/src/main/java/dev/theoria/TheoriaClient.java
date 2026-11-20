package dev.theoria;

import dev.theoria.catalyst.client.CatalystClient;
import dev.theoria.core.client.NetClient;
import dev.theoria.cosmetics.client.CosmeticsClient;
import dev.theoria.curios.client.CuriosClient;
import dev.theoria.hud.client.HudClient;
import dev.theoria.mobs.client.MobClient;
import dev.theoria.stats.client.StatsClient;
import dev.theoria.wings.client.WingsClient;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.ModContainer;
import net.neoforged.fml.common.Mod;
import net.neoforged.neoforge.client.gui.ConfigurationScreen;
import net.neoforged.neoforge.client.gui.IConfigScreenFactory;

/** Client-only entry point (never loaded on a dedicated server). Runs after {@link Theoria}. */
@Mod(value = Theoria.MODID, dist = Dist.CLIENT)
public final class TheoriaClient {
    public TheoriaClient(IEventBus modBus, ModContainer container) {
        NetClient.init(modBus);
        HudClient.init(modBus);
        WingsClient.init();
        CatalystClient.init(modBus); // Faz 7: Catalyst Forge screen + tooltip lines
        MobClient.init(modBus); // Faz 8: rig model layers, species renderers, spawn-egg tooltip
        StatsClient.init(modBus); // Faz 10: K key, ESC ▸ Status button, Orb of Oblivion tooltip (the XP bar shows vanilla's level, HudClient's LevelLayer; the skill level is on the Status screen)
        CosmeticsClient.init(modBus, container); // Faz 11: the cosmetic look (one render-state modifier) + the three look switches (client config)
        CuriosClient.init(modBus); // Faz 11: Curios Max screen
        container.registerExtensionPoint(IConfigScreenFactory.class, ConfigurationScreen::new);
        if (Boolean.getBoolean("theoria.dev") && !System.getProperty("theoria.dev.client", "").isEmpty()) {
            try { // the release jar has no dev.theoria.devtest (see Theoria#bootDevTests): reflection, never a link-time reference
                Class.forName("dev.theoria.devtest.client.DevClient", true, TheoriaClient.class.getClassLoader()).getMethod("init", IEventBus.class).invoke(null, modBus);
            } catch (ClassNotFoundException e) {
                Theoria.LOGGER.warn("-Dtheoria.dev.client is set but this build has no dev scenarios (release jar) — ignored");
            } catch (ReflectiveOperationException e) {
                throw new IllegalStateException("Theoria dev scenario failed to start", e);
            }
        }
    }
}
