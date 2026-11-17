package dev.theoria.wings;

import dev.theoria.core.Attachments;
import dev.theoria.core.Net;
import java.util.function.Supplier;
import net.neoforged.neoforge.attachment.AttachmentType;
import net.neoforged.neoforge.common.NeoForge;

/**
 * Phase 4 entry point (common side): the persistent wing choice, the select request, re-wearing after death, the self-checks
 * ({@code /theoria test wings}). The screen and the ESC button are client-only ({@code dev.theoria.wings.client}, wired from TheoriaClient).
 * Fantastic Wings is touched only through its datapack registry and its {@code /wings} command (ADR-002 d) — never compiled against.
 */
public final class WingsModule {
    /** {@code theoria:wings} — the chosen wing id, "" = none. */
    public static Supplier<AttachmentType<WingChoice>> CHOICE;

    private WingsModule() {}

    public static void init() {
        CHOICE = Attachments.playerState("wings", () -> WingChoice.NONE, WingChoice.CODEC, WingChoice.STREAM_CODEC);
        Net.serverbound(SelectWingsPayload.TYPE, SelectWingsPayload.STREAM_CODEC, (payload, player) -> WingsServer.select(player, payload.wingId()));
        NeoForge.EVENT_BUS.addListener(WingsServer::onRespawn);
        WingsChecks.init();
    }
}
