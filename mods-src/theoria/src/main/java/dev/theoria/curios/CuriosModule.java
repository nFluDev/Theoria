package dev.theoria.curios;

import dev.theoria.core.Net;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.entity.player.PlayerContainerEvent;

/**
 * Phase 11 entry point (common side) — the ONLY package that touches Curios classes (ADR-007 K1). Slot sizes are datapack JSON ({@code generator/curios.py}); this registers
 * the Max menu, the "open" request, the redirect from Curios' own menu and the self-checks ({@code /theoria test curios}). The screen is client-only
 * ({@code dev.theoria.curios.client}, wired from TheoriaClient).
 */
public final class CuriosModule {
    private CuriosModule() {}

    public static void init() {
        CuriosMaxMenu.register();
        Net.serverbound(OpenCuriosMaxPayload.TYPE, OpenCuriosMaxPayload.STREAM_CODEC, (payload, player) -> CuriosOpen.open(player, payload.carried()));
        NeoForge.EVENT_BUS.addListener(CuriosOpen::onOpen);
        CuriosChecks.init();
    }
}
