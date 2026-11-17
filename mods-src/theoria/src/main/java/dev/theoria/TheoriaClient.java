package dev.theoria;

import dev.theoria.catalyst.client.CatalystClient;
import dev.theoria.core.client.NetClient;
import dev.theoria.devtest.Dev;
import dev.theoria.devtest.client.DevClient;
import dev.theoria.hud.client.HudClient;
import dev.theoria.mobs.client.MobClient;
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
        container.registerExtensionPoint(IConfigScreenFactory.class, ConfigurationScreen::new);
        if (Dev.ENABLED && !Dev.CLIENT_MODE.isEmpty()) {
            DevClient.init(modBus);
        }
    }
}
