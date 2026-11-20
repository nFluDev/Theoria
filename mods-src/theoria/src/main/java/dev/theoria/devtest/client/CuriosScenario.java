package dev.theoria.devtest.client;

import com.mojang.authlib.GameProfile;
import dev.theoria.core.client.NetClient;
import dev.theoria.cosmetics.CosmeticSlot;
import dev.theoria.cosmetics.Cosmetics;
import dev.theoria.cosmetics.CosmeticsModule;
import dev.theoria.cosmetics.ToggleCosmeticHiddenPayload;
import dev.theoria.cosmetics.client.CosmeticsClient;
import dev.theoria.cosmetics.client.CosmeticsConfig;
import dev.theoria.curios.CuriosMaxLayout;
import dev.theoria.curios.CuriosMaxMenu;
import dev.theoria.curios.OpenCuriosMaxPayload;
import dev.theoria.curios.client.CuriosMaxScreen;
import dev.theoria.wings.client.WingsScreen;
import java.util.List;
import java.util.UUID;
import java.util.function.Consumer;
import java.util.function.Function;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.gui.components.events.GuiEventListener;
import net.minecraft.client.player.RemotePlayer;
import net.minecraft.client.renderer.entity.state.AvatarRenderState;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.neoforged.neoforge.client.network.ClientPacketDistributor;
import top.theillusivec4.curios.api.CuriosApi;
import top.theillusivec4.curios.client.screen.CuriosScreen;
import top.theillusivec4.curios.common.network.client.CPacketOpenCurios;

/**
 * Unattended Curios Max scenario ({@code ./gradlew runDevClient -PdevClient=curios}, Phase 11): opens the screen both ways (our payload, and the exact packet Curios' key / inventory
 * button sends, which the server must redirect), puts armor into the cosmetic row with the screen's Ctrl + click, presses the hide dots, and asserts what a program CAN see — the
 * screen is Curios' own classic screen (subclassed), drew every frame, did not pause the game, kept its panel, dots and slots inside the window; the server booked the cosmetic without touching
 * armor; the one render-state modifier produced the look for the local player AND a second avatar, obeyed the hide dots and the two switches (both off = vanilla) and left elytra alone;
 * the Wings cell opens the Wings screen and comes back. How it LOOKS is the user's approval (CLAUDE.md: no screenshots).
 */
final class CuriosScenario {
    private record Step(String name, List<String> commands, Consumer<Minecraft> before, int hold, Function<Minecraft, String> verify) {}

    private static int stepIndex = -1;
    private static int stepStart;
    private static boolean failed;
    private static List<Step> steps;
    private static String note = "";
    private static long markFrames, markSelfCalls, markSelfChanged, markOtherChanged;
    private static boolean[] originalSwitches = {true, true};

    private CuriosScenario() {}

    static void tick(Minecraft mc, int t) {
        if (steps == null) {
            steps = buildSteps();
            stepStart = t;
        }
        if (t < 40) return; // let the world settle
        if (stepIndex < 0 || t - stepStart >= steps.get(stepIndex).hold()) {
            if (stepIndex >= 0) finish(mc, steps.get(stepIndex));
            stepIndex++;
            stepStart = t;
            if (stepIndex >= steps.size()) {
                DevClient.REPORT.line("RESULT %s", failed ? "FAIL" : "PASS");
                DevClient.REPORT.write();
                mc.stop();
                return;
            }
            Step step = steps.get(stepIndex);
            note = "";
            markFrames = CuriosMaxScreen.frames();
            markSelfCalls = CosmeticsClient.selfCalls;
            markSelfChanged = CosmeticsClient.selfChanged;
            markOtherChanged = CosmeticsClient.otherChanged;
            if (step.before() != null) step.before().accept(mc);
            step.commands().forEach(mc.player.connection::sendCommand);
        }
    }

    private static void finish(Minecraft mc, Step step) {
        String problem;
        try {
            problem = step.verify().apply(mc);
        } catch (RuntimeException e) {
            problem = "threw " + e;
        }
        if (problem != null) failed = true;
        DevClient.REPORT.line("%-18s %s  [frames +%d, avatar passes +%d (%d changed), others +%d changed] %s", step.name(), problem == null ? "ok  " : "FAIL " + problem,
                CuriosMaxScreen.frames() - markFrames, CosmeticsClient.selfCalls - markSelfCalls, CosmeticsClient.selfChanged - markSelfChanged, CosmeticsClient.otherChanged - markOtherChanged, note);
    }

    // ------------------------------------------------------------------------------------------------ helpers
    private static CuriosMaxScreen screen(Minecraft mc) {
        return mc.gui.screen() instanceof CuriosMaxScreen s ? s : null;
    }

    private static CuriosMaxMenu menu(Minecraft mc) {
        return mc.player.containerMenu instanceof CuriosMaxMenu m ? m : null;
    }

    private static ServerPlayer serverPlayer(Minecraft mc) {
        return mc.getSingleplayerServer().getPlayerList().getPlayer(mc.player.getUUID());
    }

    private static AvatarRenderState state(Minecraft mc, Player who) {
        return (AvatarRenderState) mc.getEntityRenderDispatcher().getRenderer(who).createRenderState(who, 1.0F);
    }

    private static Slot inventorySlotWith(CuriosMaxMenu menu, Item item) {
        for (Slot s : menu.slots) if (s.index >= CuriosMaxMenu.INVENTORY_START && s.index < CuriosMaxMenu.INVENTORY_END && s.getItem().is(item)) return s;
        return null;
    }

    /** The screen's Ctrl + click on the inventory slot holding {@code item} (what a player does with the mouse). */
    private static void ctrlClick(Minecraft mc, Item item) {
        if (!screen(mc).toggleCosmetic(inventorySlotWith(menu(mc), item))) throw new IllegalStateException("Ctrl+click on " + item + " was not handled");
    }

    private static void setSwitches(boolean own, boolean others) {
        CosmeticsConfig.SHOW_COSMETICS.set(own);
        CosmeticsConfig.SHOW_OTHERS.set(others);
    }

    private static String shown(Minecraft mc, Player who, ItemStack head, ItemStack chest, ItemStack legs, ItemStack feet, String what) {
        AvatarRenderState s = state(mc, who);
        note = what + ": head=" + s.headEquipment.getItem() + " chest=" + s.chestEquipment.getItem() + " legs=" + s.legsEquipment.getItem() + " feet=" + s.feetEquipment.getItem();
        if (!ItemStack.matches(s.headEquipment, head)) return what + ": head is " + s.headEquipment + ", expected " + head;
        if (!ItemStack.matches(s.chestEquipment, chest)) return what + ": chest is " + s.chestEquipment + ", expected " + chest;
        if (!ItemStack.matches(s.legsEquipment, legs)) return what + ": legs is " + s.legsEquipment + ", expected " + legs;
        if (!ItemStack.matches(s.feetEquipment, feet)) return what + ": feet is " + s.feetEquipment + ", expected " + feet;
        return null;
    }

    private static final ItemStack NONE = ItemStack.EMPTY;

    // ------------------------------------------------------------------------------------------------ verifications
    private static String curiosData(Minecraft mc) {
        var handler = CuriosApi.getCuriosInventoryOrNull(mc.player);
        if (handler == null) return "the local player has no Curios inventory";
        note = handler.getVisibleSlots() + " Curios slots in " + handler.getCurios().size() + " types on the client";
        return handler.getVisibleSlots() >= 30 ? null : "only " + handler.getVisibleSlots() + " Curios slots (Phase 11 DoD: >= 30)";
    }

    private static String maxOpen(Minecraft mc) {
        CuriosMaxScreen screen = screen(mc);
        if (screen == null) return "not on the Curios Max screen: " + mc.gui.screen();
        if (screen.getClass() != CuriosMaxScreen.class || !(screen instanceof CuriosScreen)) return "the screen is not Curios' classic screen subclassed: " + screen.getClass();
        CuriosMaxMenu menu = menu(mc);
        if (menu == null) return "the open container is not the Max menu: " + mc.player.containerMenu;
        if (CuriosMaxScreen.frames() - markFrames < 10) return "the screen drew only " + (CuriosMaxScreen.frames() - markFrames) + " frames";
        if (CosmeticsClient.selfCalls - markSelfCalls < 10) return "the inventory's 3D player reached the render-state modifier only " + (CosmeticsClient.selfCalls - markSelfCalls) + " times";
        if (mc.isPaused()) return "the Max screen paused the game (a paused single-player server processes no packets)";
        if (menu.curioSlotCount() < 30) return "only " + menu.curioSlotCount() + " Curios slots in the menu";
        int columns = menu.grid.size();
        int left = screen.getLeftPos();
        int panelLeft = left + CuriosMaxLayout.cellX(0, menu.panelWidth) - 7;
        if (panelLeft < 0 || left + CuriosMaxLayout.IMAGE_W > screen.width) return "the panel or the inventory is off screen (panel left " + panelLeft + ", inventory " + left + ".." + (left + CuriosMaxLayout.IMAGE_W) + " of " + screen.width + ")";
        int top = screen.getTopPos();
        if (top < 0 || top + CuriosMaxLayout.panelHeight(menu.grid.get(0)) > screen.height) return "the panel is taller than the window";
        for (Slot s : menu.slots) {
            int x = left + s.x;
            int y = top + s.y;
            if (x < 0 || y < 0 || x + 16 > screen.width || y + 16 > screen.height) return "slot " + s.index + " off screen at " + x + "," + y;
        }
        if (screen.hideDots().size() != 4) return "expected the 4 hide dots of the cosmetic row, found " + screen.hideDots().size();
        if (screen.wingsCell() == null) return "the Wings cell is missing";
        for (GuiEventListener child : screen.children()) {
            if (child instanceof AbstractWidget w && w.visible && (w.getX() < 0 || w.getY() < 0 || w.getX() + w.getWidth() > screen.width || w.getY() + w.getHeight() > screen.height)) return "widget off screen: " + w;
        }
        note = "gui " + screen.width + "x" + screen.height + ", " + menu.curioSlotCount() + " Curios slots in " + columns + " columns, " + menu.slots.size() + " menu slots, panel x " + panelLeft + ".., inventory x " + left;
        return null;
    }

    private static String placed(Minecraft mc, Item item, EquipmentSlot slot) {
        ServerPlayer server = serverPlayer(mc);
        boolean serverHas = CosmeticsModule.of(server).get(slot).is(item);
        boolean clientHas = CosmeticsModule.of(mc.player).get(slot).is(item);
        note = "server cosmetic " + slot.getName() + " " + CosmeticsModule.of(server).get(slot).getItem() + ", synced to the client: " + clientHas + ", worn there " + mc.player.getItemBySlot(slot).getItem();
        if (!serverHas) return "the server did not book the cosmetic";
        if (!clientHas) return "the attachment did not sync back to the client";
        return null;
    }

    private static String hideDot(Minecraft mc) {
        CuriosMaxScreen screen = screen(mc);
        var dots = screen.hideDots();
        note = "dots " + dots.stream().map(d -> d.hidden() ? "dim" : "lit").toList();
        for (int i = 0; i < 4; i++) {
            if (dots.get(i).hidden() != (i == 1)) return "dot " + i + " is " + (dots.get(i).hidden() ? "dim" : "lit") + " but only the chest was toggled";
        }
        if (!CosmeticsModule.of(serverPlayer(mc)).isHidden(EquipmentSlot.CHEST) || !CosmeticsModule.of(mc.player).isHidden(EquipmentSlot.CHEST)) return "the chest hide switch did not reach the server and come back";
        return null;
    }

    private static String wingsCell(Minecraft mc) {
        if (!(mc.gui.screen() instanceof WingsScreen)) return "the Wings cell did not open the Wings screen: " + mc.gui.screen();
        if (mc.isPaused()) return "the Wings screen paused the game";
        mc.gui.screen().onClose();
        return null;
    }

    private static String backFromWings(Minecraft mc) {
        if (screen(mc) == null || menu(mc) == null) return "did not return to Curios Max: " + mc.gui.screen() + " / " + mc.player.containerMenu;
        return null;
    }

    private static String other(Minecraft mc) {
        RemotePlayer remote = new RemotePlayer(mc.level, new GameProfile(UUID.randomUUID(), "dev-remote"));
        remote.setId(-424242); // never added to the level: it only needs an id for createRenderState (the renderer reads it)
        remote.setData(CosmeticsModule.COSMETICS, Cosmetics.NONE.with(EquipmentSlot.HEAD, new ItemStack(Items.DIAMOND_HELMET)).withHidden(EquipmentSlot.LEGS, true));
        remote.setItemSlot(EquipmentSlot.LEGS, new ItemStack(Items.IRON_LEGGINGS));
        remote.setItemSlot(EquipmentSlot.CHEST, new ItemStack(Items.IRON_CHESTPLATE));
        setSwitches(true, true);
        String p = shown(mc, remote, new ItemStack(Items.DIAMOND_HELMET), new ItemStack(Items.IRON_CHESTPLATE), NONE, NONE, "another player (cosmetic helmet, hidden legs, worn iron chest + legs)");
        if (p != null) return p;
        if (CosmeticsClient.otherChanged - markOtherChanged < 2) return "the modifier never ran for the second avatar";
        setSwitches(true, false);
        p = shown(mc, remote, NONE, new ItemStack(Items.IRON_CHESTPLATE), new ItemStack(Items.IRON_LEGGINGS), NONE, "'show others' off: the other player looks vanilla (hide switch ignored too)");
        setSwitches(true, true);
        return p;
    }

    private static String gold(Minecraft mc) {
        setSwitches(true, true);
        // worn: iron chest + diamond legs; cosmetic: diamond chest (from before) + gold legs → chest = cosmetic diamond, legs = cosmetic gold, nothing else invented
        return shown(mc, mc.player, NONE, new ItemStack(Items.DIAMOND_CHESTPLATE), new ItemStack(Items.GOLDEN_LEGGINGS), NONE, "gold-leggings cosmetic over worn diamond leggings");
    }

    private static String vanilla(Minecraft mc) {
        setSwitches(false, false);
        // both switches off: exactly the worn armor (iron chest, diamond legs), whatever the cosmetic slots and hide dots hold
        String p = shown(mc, mc.player, NONE, new ItemStack(Items.IRON_CHESTPLATE), new ItemStack(Items.DIAMOND_LEGGINGS), NONE, "both switches off = vanilla");
        setSwitches(true, true);
        return p;
    }

    private static String elytra(Minecraft mc) {
        setSwitches(true, true);
        // the chest cosmetic (diamond, and hidden) must not touch the worn elytra
        return shown(mc, mc.player, NONE, new ItemStack(Items.ELYTRA), NONE, NONE, "elytra worn, chest cosmetic set and hidden: elytra stays (legs hidden: nothing)");
    }

    private static List<Step> buildSteps() {
        return List.of(
                new Step("reset", List.of("gamemode survival", "clear @s", "item replace entity @s armor.chest with minecraft:air", "item replace entity @s armor.legs with minecraft:air"), mc -> {
                    originalSwitches = new boolean[] {CosmeticsConfig.SHOW_COSMETICS.get(), CosmeticsConfig.SHOW_OTHERS.get()};
                    setSwitches(true, true);
                    for (int i = 0; i < 4; i++) if (CosmeticsModule.of(mc.player).isHidden(Cosmetics.SLOTS.get(i))) NetClient.send(new ToggleCosmeticHiddenPayload(i));
                }, 14, mc -> mc.player.getInventory().isEmpty() && CosmeticsModule.of(mc.player).hidden() == 0 ? null : "dev world not reset: " + CosmeticsModule.of(mc.player)),
                new Step("curios_data", List.of(), null, 2, CuriosScenario::curiosData),
                new Step("give", List.of("give @s minecraft:diamond_chestplate", "give @s minecraft:golden_leggings", "give @s minecraft:elytra"), null, 12,
                        mc -> mc.player.getInventory().countItem(Items.DIAMOND_CHESTPLATE) == 1 ? null : "the items did not arrive"),
                new Step("open_payload", List.of(), mc -> NetClient.send(new OpenCuriosMaxPayload()), 24, CuriosScenario::maxOpen),
                new Step("close", List.of(), mc -> mc.player.closeContainer(), 8, mc -> mc.gui.screen() == null ? null : "the screen did not close: " + mc.gui.screen()),
                // exactly what Curios' key and the inventory button send: the SERVER must hand us the Max menu instead
                new Step("open_curios_key", List.of(), mc -> ClientPacketDistributor.sendToServer(new CPacketOpenCurios(ItemStack.EMPTY)), 24, CuriosScenario::maxOpen),
                new Step("ctrl_chest", List.of(), mc -> ctrlClick(mc, Items.DIAMOND_CHESTPLATE), 20, mc -> placed(mc, Items.DIAMOND_CHESTPLATE, EquipmentSlot.CHEST)),
                new Step("drawn_on_self", List.of(), null, 14, mc -> {
                    if (CosmeticsClient.selfChanged - markSelfChanged < 5) return "the modifier changed the local avatar only " + (CosmeticsClient.selfChanged - markSelfChanged) + " times while the inventory drew it";
                    String p = shown(mc, mc.player, NONE, new ItemStack(Items.DIAMOND_CHESTPLATE), NONE, NONE, "cosmetic chest over a bare body");
                    return p != null ? p : (mc.player.getItemBySlot(EquipmentSlot.CHEST).isEmpty() && mc.player.getArmorValue() == 0 ? null : "the cosmetic became real armor");
                }),
                new Step("wear_iron", List.of("item replace entity @s armor.chest with minecraft:iron_chestplate", "item replace entity @s armor.legs with minecraft:diamond_leggings"), null, 10,
                        mc -> mc.player.getItemBySlot(EquipmentSlot.CHEST).is(Items.IRON_CHESTPLATE) && mc.player.getItemBySlot(EquipmentSlot.LEGS).is(Items.DIAMOND_LEGGINGS) ? null : "iron chest / diamond legs not worn"),
                new Step("override", List.of(), null, 6, mc -> shown(mc, mc.player, NONE, new ItemStack(Items.DIAMOND_CHESTPLATE), new ItemStack(Items.DIAMOND_LEGGINGS), NONE, "cosmetic diamond chest overrides the worn iron one; legs stay the worn diamond")),
                new Step("ctrl_legs", List.of(), mc -> ctrlClick(mc, Items.GOLDEN_LEGGINGS), 20, mc -> {
                    String p = placed(mc, Items.GOLDEN_LEGGINGS, EquipmentSlot.LEGS);
                    return p != null ? p : gold(mc);
                }),
                new Step("hide_dot", List.of(), mc -> screen(mc).hideDots().get(1).press(), 20, mc -> {
                    String p = hideDot(mc);
                    if (p != null) return p;
                    return shown(mc, mc.player, NONE, NONE, new ItemStack(Items.GOLDEN_LEGGINGS), NONE, "chest hidden: neither the cosmetic diamond nor the worn iron is drawn");
                }),
                new Step("hide_legs", List.of(), mc -> screen(mc).hideDots().get(2).press(), 16, mc -> shown(mc, mc.player, NONE, NONE, NONE, NONE, "legs hidden too: cosmetic gold and worn diamond both gone")),
                new Step("legs_out", List.of(), mc -> {
                    CosmeticSlot legs = menu(mc).cosmeticSlot(EquipmentSlot.LEGS);
                    screen(mc).toggleCosmetic(legs);
                }, 20, mc -> {
                    if (!CosmeticsModule.of(serverPlayer(mc)).legs().isEmpty()) return "the cosmetic legs did not come out";
                    return shown(mc, mc.player, NONE, NONE, NONE, NONE, "hidden legs slot, EMPTY cosmetic, worn diamond leggings: still nothing drawn");
                }),
                new Step("unhide", List.of(), mc -> {
                    screen(mc).hideDots().get(1).press();
                    screen(mc).hideDots().get(2).press();
                }, 16, mc -> shown(mc, mc.player, NONE, new ItemStack(Items.DIAMOND_CHESTPLATE), new ItemStack(Items.DIAMOND_LEGGINGS), NONE, "both dots lit again: cosmetic chest diamond, worn legs diamond")),
                new Step("drawn_on_other", List.of(), null, 2, CuriosScenario::other),
                new Step("vanilla", List.of(), null, 2, CuriosScenario::vanilla),
                new Step("wear_elytra", List.of("item replace entity @s armor.chest with minecraft:elytra", "item replace entity @s armor.legs with minecraft:air"), mc -> {
                    screen(mc).hideDots().get(1).press(); // hide the chest again: the elytra must survive it
                }, 14, mc -> mc.player.getItemBySlot(EquipmentSlot.CHEST).is(Items.ELYTRA) && CosmeticsModule.of(mc.player).isHidden(EquipmentSlot.CHEST) ? null : "elytra not worn / chest not hidden"),
                new Step("elytra_rule", List.of(), null, 2, mc -> shown(mc, mc.player, NONE, new ItemStack(Items.ELYTRA), NONE, NONE, "elytra worn, chest cosmetic set and hidden: the elytra is drawn")),
                new Step("wings_cell", List.of(), mc -> screen(mc).wingsCell().press(), 14, CuriosScenario::wingsCell),
                new Step("back_from_wings", List.of(), null, 14, CuriosScenario::backFromWings),
                new Step("take_out", List.of(), mc -> {
                    screen(mc).toggleCosmetic(menu(mc).cosmeticSlot(EquipmentSlot.CHEST));
                    screen(mc).hideDots().get(1).press(); // un-hide
                }, 20, mc -> CosmeticsModule.of(serverPlayer(mc)).isEmpty() && CosmeticsModule.of(mc.player).isEmpty() && CosmeticsModule.of(mc.player).hidden() == 0 ? null
                        : "the cosmetic row did not come back to its start: " + CosmeticsModule.of(mc.player)),
                new Step("close_final", List.of(), mc -> mc.player.closeContainer(), 8, mc -> mc.gui.screen() == null ? null : "the screen did not close: " + mc.gui.screen()),
                // as a player would: the screen is closed first, the commands come a moment later (same tick they would race the close packet's inventory hand-back)
                new Step("cleanup", List.of("clear @s", "item replace entity @s armor.chest with minecraft:air", "item replace entity @s armor.legs with minecraft:air"), mc -> setSwitches(originalSwitches[0], originalSwitches[1]), 14,
                        mc -> mc.player.getInventory().isEmpty() && CosmeticsModule.of(mc.player).isEmpty() ? null : "dev world not clean: " + CosmeticsModule.of(mc.player)));
    }
}
