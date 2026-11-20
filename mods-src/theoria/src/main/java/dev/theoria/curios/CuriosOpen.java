package dev.theoria.curios;

import dev.theoria.core.TheoriaConfig;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.event.entity.player.PlayerContainerEvent;
import top.theillusivec4.curios.common.inventory.container.CuriosMenu;

/**
 * How the Max menu gets opened (ADR-007 K5). The Curios key and the Curios button of the inventory both end in {@code player.openMenu(new CuriosMenuProvider())} on the
 * server; {@link #onOpen} sees THAT menu (exactly Curios' class — ours is a subclass) open and replaces it with ours before the client has drawn a frame — no mixin, and whatever the
 * player carried on the cursor travels along (Curios puts it into {@code containerMenu} after opening, which by then is our menu). {@link OpenCuriosMaxPayload} opens ours directly — the inventory's Curios button sends it (the client swaps Curios' own button for ours), because the redirect costs a close-and-reopen that re-centres the mouse; the J key still goes through the redirect.
 */
public final class CuriosOpen {
    private CuriosOpen() {}

    /** Server: opens the Max menu for {@code player}, carrying the cursor stack over. */
    public static void open(ServerPlayer player) {
        open(player, ItemStack.EMPTY);
    }

    /** As above; a creative player's cursor stack comes from the client ({@code creativeCarried}), anyone else's is the server's own. */
    public static void open(ServerPlayer player, ItemStack creativeCarried) {
        if (player.containerMenu instanceof CuriosMaxMenu) return;
        ItemStack carried = player.isCreative() && !creativeCarried.isEmpty() ? creativeCarried : player.containerMenu.getCarried();
        player.containerMenu.setCarried(ItemStack.EMPTY);
        player.openMenu(CuriosMaxMenu.provider());
        if (!carried.isEmpty() && player.containerMenu instanceof CuriosMaxMenu menu) {
            menu.setCarried(carried);
            menu.broadcastChanges();
        }
    }

    static void onOpen(PlayerContainerEvent.Open event) {
        if (!(event.getEntity() instanceof ServerPlayer player) || event.getContainer().getClass() != CuriosMenu.class || !TheoriaConfig.curiosMax()) return;
        player.openMenu(CuriosMaxMenu.provider());
    }
}
