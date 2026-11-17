package dev.theoria.devtest.client;

import com.mojang.blaze3d.platform.InputConstants;
import dev.theoria.catalyst.CatalystComponents;
import dev.theoria.catalyst.CatalystItem;
import dev.theoria.catalyst.CatalystMenu;
import dev.theoria.catalyst.CatalystTooltip;
import dev.theoria.catalyst.Catalysts;
import dev.theoria.catalyst.client.CatalystScreen;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;
import java.util.function.Function;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.client.input.MouseButtonInfo;
import net.minecraft.client.resources.language.I18n;
import net.minecraft.core.BlockPos;
import net.minecraft.core.component.DataComponents;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.EquipmentSlotGroup;
import net.minecraft.world.inventory.ContainerInput;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.core.Direction;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;

/**
 * Unattended Catalyst Forge scenario ({@code ./gradlew runDevClient -PdevClient=catalyst}): what a program can see of Phase 7b's client side.
 * A forge is placed and used with a real right-click; the screen opens and does NOT pause the game; items move in by shift-click through the real container
 * packets; the preview and the Apply button follow the pure rules; "Uygula" is clicked through the screen's hit-testing and answered by the real server (the synced
 * component is read back on the CLIENT, which is the network proof of {@code networkSynchronized}); "Üret" forges a catalyst; × takes one out; the tooltip comes through
 * the real {@code ItemTooltipEvent}; closing returns everything. How it LOOKS is the user's approval (CLAUDE.md: no screenshots).
 */
final class CatalystScenario {
    private record Step(String name, Consumer<Minecraft> action, int hold, Function<Minecraft, String> verify) {}

    private static List<Step> steps;
    private static int stepIndex = -1;
    private static int stepStart;
    private static boolean failed;
    private static BlockPos forge;
    private static int serverMark;
    private static int playerMark;
    private static String note = "";

    private CatalystScenario() {}

    static void tick(Minecraft mc, int t) {
        if (steps == null) {
            steps = buildSteps();
            stepStart = t;
        }
        if (t < 40) return;
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
            try {
                steps.get(stepIndex).action().accept(mc);
            } catch (RuntimeException e) {
                failed = true;
                DevClient.REPORT.line("%-28s FAIL action threw %s", steps.get(stepIndex).name(), e);
            }
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
        DevClient.REPORT.line("%-28s %s%s", step.name(), problem == null ? "ok" : "FAIL " + problem, note.isEmpty() ? "" : "  [" + note + "]");
        note = "";
    }

    // ------------------------------------------------------------ helpers
    private static void command(Minecraft mc, String text) {
        mc.player.connection.sendCommand(text);
    }

    private static CatalystScreen screen(Minecraft mc) {
        return mc.gui.screen() instanceof CatalystScreen s ? s : null;
    }

    private static void click(Minecraft mc, Button b) {
        Screen screen = mc.gui.screen();
        MouseButtonEvent event = new MouseButtonEvent(b.getX() + b.getWidth() / 2.0, b.getY() + b.getHeight() / 2.0, new MouseButtonInfo(InputConstants.MOUSE_BUTTON_LEFT, 0));
        screen.mouseClicked(event, false);
        screen.mouseReleased(event);
    }

    /** Shift-clicks the first slot (player inventory part) holding something that matches. */
    private static boolean shiftClick(Minecraft mc, java.util.function.Predicate<ItemStack> match, int fromSlot) {
        CatalystMenu menu = screen(mc).getMenu();
        for (int i = fromSlot; i < menu.slots.size(); i++) {
            if (match.test(menu.getSlot(i).getItem())) {
                mc.gameMode.handleContainerInput(menu.containerId, i, 0, ContainerInput.QUICK_MOVE, mc.player);
                return true;
            }
        }
        return false;
    }

    private static boolean isCatalyst(ItemStack s, String id) {
        return s.getItem() instanceof CatalystItem c && c.def().id().equals(id);
    }

    private static Button button(Minecraft mc, String text) {
        for (var child : screen(mc).children()) if (child instanceof Button b && b.getMessage().getString().equals(text)) return b;
        return null;
    }

    private static String need(boolean ok, String complaint) {
        return ok ? null : complaint;
    }

    // ------------------------------------------------------------ steps
    private static List<Step> buildSteps() {
        List<Step> s = new ArrayList<>();
        s.add(new Step("setup", mc -> {
            forge = mc.player.blockPosition().offset(0, 0, 2);
            command(mc, "clear @s");
            command(mc, "gamemode creative @s");
            command(mc, "setblock " + forge.getX() + " " + forge.getY() + " " + forge.getZ() + " theoria:catalyst_forge");
            command(mc, "give @s minecraft:diamond_sword");
            command(mc, "give @s theoria:catalyst/attack_speed[theoria:catalyst_level=4]");
            command(mc, "give @s theoria:catalyst/crit_chance[theoria:catalyst_level=3]");
            command(mc, "give @s theoria:catalyst/frost[theoria:catalyst_level=2]");
            command(mc, "give @s minecraft:blaze_powder 3");
            command(mc, "give @s theoria:catalyst_dust 12");
            command(mc, "give @s theoria:catalyst_essence 2");
        }, 25, mc -> {
            boolean placed = mc.level.getBlockState(forge).getBlock() == dev.theoria.catalyst.CatalystItems.FORGE.get();
            boolean items = mc.player.getInventory().contains(st -> st.is(Items.DIAMOND_SWORD)) && mc.player.getInventory().contains(st -> st.getItem() instanceof CatalystItem);
            return need(placed, "forge not placed") != null ? "forge block not placed at " + forge : need(items, "give commands did not reach the inventory");
        }));
        s.add(new Step("open (real right-click)", mc -> {
            serverMark = mc.getSingleplayerServer().getTickCount();
            playerMark = mc.player.tickCount;
            mc.gameMode.useItemOn(mc.player, InteractionHand.MAIN_HAND, new BlockHitResult(Vec3.atCenterOf(forge), Direction.UP, forge, false));
        }, 30, mc -> {
            CatalystScreen screen = screen(mc);
            if (screen == null) return "no CatalystScreen after use, screen is " + mc.gui.screen();
            if (screen.isPauseScreen()) return "the forge screen pauses the game (CLAUDE.md: it must not)";
            int server = mc.getSingleplayerServer().getTickCount() - serverMark, player = mc.player.tickCount - playerMark;
            note = "server ticks " + server + ", player ticks " + player;
            return need(server >= 25 && player >= 25, "the game stood still while the screen was open") ;
        }));
        s.add(new Step("shift-click target + inputs", mc -> {
            shiftClick(mc, st -> st.is(Items.DIAMOND_SWORD), CatalystMenu.PLAYER_START);
            for (int i = 0; i < 3; i++) shiftClick(mc, st -> st.getItem() instanceof CatalystItem, CatalystMenu.PLAYER_START);
        }, 20, mc -> {
            CatalystMenu menu = screen(mc).getMenu();
            long inputs = menu.inputs().stream().filter(st -> st.getItem() instanceof CatalystItem).count();
            note = "target " + menu.target() + ", " + inputs + " catalysts in the inputs";
            return need(menu.target().is(Items.DIAMOND_SWORD) && inputs == 3, "items did not land in the right slots");
        }));
        s.add(new Step("preview + Uygula state", mc -> { }, 10, mc -> {
            CatalystMenu menu = screen(mc).getMenu();
            List<String> lines = CatalystTooltip.plain(CatalystTooltip.preview(menu.target(), menu.inputs()));
            Button apply = button(mc, I18n.get("theoria.catalyst.apply"));
            note = lines.toString();
            if (apply == null) return "no Apply button (looked for '" + I18n.get("theoria.catalyst.apply") + "')";
            return need(lines.size() == 3 && apply.active, "preview should list 3 additions with Apply enabled; apply.active=" + apply.active);
        }));
        s.add(new Step("Uygula (click)", mc -> click(mc, button(mc, I18n.get("theoria.catalyst.apply"))), 20, mc -> {
            CatalystMenu menu = screen(mc).getMenu();
            Catalysts got = Catalysts.of(menu.target());
            boolean inputsEmpty = menu.inputs().stream().allMatch(ItemStack::isEmpty);
            note = "client reads " + got.slots().size() + " catalysts on the target; glint=" + menu.target().get(DataComponents.ENCHANTMENT_GLINT_OVERRIDE);
            return need(got.slots().size() == 3 && inputsEmpty && Boolean.TRUE.equals(menu.target().get(DataComponents.ENCHANTMENT_GLINT_OVERRIDE)), "the server's answer did not arrive in the client menu: " + got + ", inputs empty " + inputsEmpty);
        }));
        s.add(new Step("tooltip (ItemTooltipEvent)", mc -> { }, 5, mc -> {
            ItemStack target = screen(mc).getMenu().target();
            List<String> lines = target.getTooltipLines(Item.TooltipContext.of(mc.level), mc.player, TooltipFlag.NORMAL).stream().map(c -> c.getString()).toList();
            double speed = 0;
            for (var m : target.getAttributeModifiers().modifiers()) if (m.modifier().id().getPath().startsWith("catalyst/attack_speed")) speed = m.modifier().amount();
            note = "attack speed modifier " + speed + "; " + lines.stream().filter(l -> l.contains("Catalysts") || l.contains("Swiftness")).toList();
            return need(lines.stream().anyMatch(l -> l.contains("Catalysts 3/3")) && lines.stream().anyMatch(l -> l.contains("Swiftness Catalyst IV")) && speed > 0.3,
                    "tooltip lacks the catalyst lines or the live attribute: " + lines);
        }));
        s.add(new Step("× takes one out", mc -> click(mc, screen(mc).removeButton(1)), 20, mc -> {
            Catalysts got = Catalysts.of(screen(mc).getMenu().target());
            note = got.slots().toString();
            return need(got.slots().size() == 2, "expected 2 catalysts after taking one out, got " + got.slots().size());
        }));
        s.add(new Step("take the sword back", mc -> {
            CatalystMenu menu = screen(mc).getMenu();
            mc.gameMode.handleContainerInput(menu.containerId, CatalystMenu.TARGET, 0, ContainerInput.QUICK_MOVE, mc.player);
        }, 15, mc -> need(screen(mc).getMenu().target().isEmpty(), "the target slot should be empty again")));
        s.add(new Step("shift-click raw materials", mc -> {
            shiftClick(mc, st -> st.is(Items.BLAZE_POWDER), CatalystMenu.PLAYER_START);
            shiftClick(mc, st -> st.is(dev.theoria.catalyst.CatalystItems.DUST.get()), CatalystMenu.PLAYER_START);
            shiftClick(mc, st -> st.is(dev.theoria.catalyst.CatalystItems.ESSENCE.get()), CatalystMenu.PLAYER_START);
        }, 20, mc -> {
            CatalystMenu menu = screen(mc).getMenu();
            List<String> lines = CatalystTooltip.plain(CatalystTooltip.preview(menu.target(), menu.inputs()));
            Button forgeButton = button(mc, I18n.get("theoria.catalyst.forge"));
            note = lines.toString();
            return need(forgeButton != null && forgeButton.active && !lines.isEmpty() && lines.get(0).contains("Might"), "forge preview should offer a Might catalyst with 'Forge' enabled: " + lines + " button " + forgeButton);
        }));
        s.add(new Step("Üret (click)", mc -> click(mc, button(mc, I18n.get("theoria.catalyst.forge"))), 20, mc -> {
            ItemStack made = screen(mc).getMenu().target();
            note = made.toString();
            return need(isCatalyst(made, "power") && CatalystItem.levelOf(made) == 3, "expected a Might III catalyst in the target slot, got " + made);
        }));
        s.add(new Step("layout at GUI scales", mc -> { }, 5, mc -> {
            int original = mc.options.guiScale().get();
            StringBuilder report = new StringBuilder();
            String problem = null;
            try {
                int max = mc.getWindow().calculateScale(0, mc.isEnforceUnicode());
                for (int scale = 1; scale <= Math.min(3, max); scale++) {
                    mc.options.guiScale().set(scale);
                    mc.resizeGui();
                    CatalystScreen screen = screen(mc);
                    if (screen == null) return "screen closed while changing the GUI scale";
                    int[] b = screen.bounds();
                    int w = mc.getWindow().getGuiScaledWidth(), h = mc.getWindow().getGuiScaledHeight();
                    report.append("scale ").append(scale).append(": ").append(w).append("x").append(h).append(" panel ").append(b[2]).append("x").append(b[3]).append(" at ").append(b[0]).append(",").append(b[1]).append("; ");
                    if (b[0] < 0 || b[1] < 0 || b[0] + b[2] > w || b[1] + b[3] > h) problem = "the panel does not fit at GUI scale " + scale + " (" + w + "x" + h + ")";
                }
            } finally {
                mc.options.guiScale().set(original);
                mc.resizeGui();
            }
            note = report.toString();
            return problem;
        }));
        s.add(new Step("close returns everything", mc -> mc.player.closeContainer(), 25, mc -> {
            boolean back = mc.player.getInventory().contains(st -> isCatalyst(st, "power")) && mc.player.getInventory().contains(st -> st.is(Items.DIAMOND_SWORD));
            return need(mc.gui.screen() == null && back, "screen " + mc.gui.screen() + ", items back in the inventory: " + back);
        }));
        return s;
    }
}
