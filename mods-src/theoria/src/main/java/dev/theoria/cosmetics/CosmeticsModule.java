package dev.theoria.cosmetics;

import dev.theoria.core.Attachments;
import dev.theoria.core.Net;
import java.util.function.Supplier;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.player.Player;
import net.neoforged.neoforge.attachment.AttachmentType;

/**
 * Phase 11 entry point (common side, no Curios): the {@code theoria:cosmetics} attachment, the hide-switch request and the self-checks ({@code /theoria test cosmetics}). The slots are edited in the
 * Curios Max menu ({@code dev.theoria.curios}); the drawing is client-only ({@code dev.theoria.cosmetics.client}, wired from TheoriaClient).
 */
public final class CosmeticsModule {
    /** {@code theoria:cosmetics}: saved, synced to every client that sees the player (so other players' looks show), kept on death. */
    public static Supplier<AttachmentType<Cosmetics>> COSMETICS;

    private CosmeticsModule() {}

    public static void init() {
        COSMETICS = Attachments.playerState("cosmetics", () -> Cosmetics.NONE, Cosmetics.CODEC, Cosmetics.STREAM_CODEC);
        Net.serverbound(ToggleCosmeticHiddenPayload.TYPE, ToggleCosmeticHiddenPayload.STREAM_CODEC, (payload, player) -> onToggleRequest(player, payload.slot()));
        CosmeticsChecks.init();
    }

    /** Server: the hide dot's request. The index comes from a client: only 0..3 name a slot, anything else is ignored. */
    public static void onToggleRequest(Player player, int slotIndex) {
        if (slotIndex >= 0 && slotIndex < Cosmetics.SLOTS.size()) toggleHidden(player, Cosmetics.SLOTS.get(slotIndex));
    }

    /** Server: flips the hide switch of {@code slot} (a request from the cosmetic row's dot). */
    public static void toggleHidden(Player player, EquipmentSlot slot) {
        Cosmetics now = of(player);
        if (Cosmetics.indexOf(slot) < 0) return;
        player.setData(COSMETICS, now.withHidden(slot, !now.isHidden(slot)));
    }

    public static Cosmetics of(Player player) {
        return player.getData(COSMETICS);
    }

    /** Server (and the client's local prediction): stores {@code stack} as the cosmetic of {@code slot}; true if it was accepted. An empty stack always clears. */
    public static boolean set(Player player, net.minecraft.world.entity.EquipmentSlot slot, net.minecraft.world.item.ItemStack stack) {
        if (!stack.isEmpty() && !CosmeticRules.accepts(slot, stack)) return false;
        Cosmetics now = of(player);
        player.setData(COSMETICS, now.with(slot, stack));
        return true;
    }
}
