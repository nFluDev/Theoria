package dev.theoria.devtest.client;

import com.mojang.blaze3d.platform.Window;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import net.minecraft.client.Minecraft;
import net.minecraft.client.MouseHandler;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.client.input.MouseButtonInfo;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;
import net.neoforged.fml.ModList;

/**
 * Unattended "middle-click sort" scenario ({@code ./gradlew runDevClient -PdevClient=sort}, Phase 13). The user's report: middle-click sorting does nothing in Lootr chests and
 * probably vanilla chests. This drives the REAL input path — {@code MouseHandler#onMove/onButton} with the middle button, so NeoForge's screen-click hooks and whatever the sorting mod
 * hooks into see exactly what a mouse would send (no OS event is injected) — over a chest slot and over a player-inventory slot of an open container, for every storage in
 * {@link #TARGETS}, and checks what a program CAN see: the stacks end up merged and packed from the first slot. It needs the sorting mod (and Lootr) in {@code run-dev/mods}
 * (copies of the pack's jars); a target whose mod is not loaded is reported SKIP. Results: build/dev-out/client_sort.txt.
 */
final class SortScenario {
    private record Target(String name, String modId, String block, List<String> fill, boolean scatter) {}

    // offsets are relative to the player's feet at the start; each chest gets its own free spot so two chests never join into a double chest
    private static final List<Target> TARGETS = List.of(
            new Target("vanilla chest", "minecraft", "minecraft:chest", List.of("0 minecraft:stone 10", "11 minecraft:dirt 5", "19 minecraft:stone 30", "26 minecraft:apple 4", "7 minecraft:dirt 40"), false),
            new Target("lootr chest", "lootr", "lootr:chest{LootTable:\"minecraft:chests/simple_dungeon\"}", List.of(), false),
            new Target("sophisticated chest", "sophisticatedstorage", "sophisticatedstorage:chest", List.of(), true));
    private static final int[][] OFFSETS = {{2, 0, 0}, {0, 0, 2}, {-2, 0, 0}};
    private static final int MIDDLE = 2;

    private static int index = -1;     // target
    private static int phase;          // 0 build, 1 open, 2 wait screen, 3 click chest, 4 check chest, 5 click inventory, 6 check inventory, 7 close
    private static int phaseStart;
    private static boolean failed;
    private static boolean started;
    private static BlockPos origin;
    private static List<String> beforeChest = List.of();
    private static boolean sortedChest, sortedInventory, skip;

    private SortScenario() {}

    static void tick(Minecraft mc, int t) {
        if (mc.player == null || mc.level == null || mc.getSingleplayerServer() == null) return;
        if (t < 60) return;
        if (!started) {
            started = true;
            DevClient.REPORT.line("sorting mod loaded: %s · lootr: %s · sophisticatedstorage: %s", ModList.get().isLoaded("inventoryprofilesnext"), ModList.get().isLoaded("lootr"), ModList.get().isLoaded("sophisticatedstorage"));
            dumpIpnSettings();
            for (String key : new String[] {"gui.done", "jei.tooltip.recipe.any_fuel", "gui.waystones.waystone_selection.sort", "enchantment.minecraft.breach.desc", "text.cloth-config.apply", "key.jei.toggleOverlay.context", "theoria.stats.title"}) {
                DevClient.REPORT.line("lang[%s] %s = %s", mc.options.languageCode, key, net.minecraft.client.resources.language.I18n.get(key));
            }
            origin = mc.player.blockPosition();
            next(t);
            return;
        }
        if (index >= TARGETS.size()) return;
        Target target = TARGETS.get(index);
        int dt = t - phaseStart;
        switch (phase) {
            case 0 -> { // build the storage and some inventory items
                int[] o = OFFSETS[index];
                BlockPos pos = origin.offset(o[0], o[1], o[2]);
                send(mc, "setblock %d %d %d minecraft:air".formatted(pos.getX(), pos.getY() + 1, pos.getZ()));
                send(mc, "setblock %d %d %d %s".formatted(pos.getX(), pos.getY(), pos.getZ(), target.block()));
                for (String f : target.fill()) {
                    String[] p = f.split(" ");
                    send(mc, "item replace block %d %d %d container.%s with %s %s".formatted(pos.getX(), pos.getY(), pos.getZ(), p[0], p[1], p[2]));
                }
                send(mc, "clear @s");
                send(mc, "item replace entity @s inventory.3 with minecraft:cobblestone 20");
                send(mc, "item replace entity @s inventory.9 with minecraft:dirt 7");
                send(mc, "item replace entity @s inventory.17 with minecraft:cobblestone 30");
                send(mc, "item replace entity @s inventory.25 with minecraft:apple 3");
                step(t, 1);
            }
            case 1 -> {
                if (dt < 20) return;
                if (!ModList.get().isLoaded(target.modId())) {
                    DevClient.REPORT.line("%-20s SKIP (mod %s not loaded — copy its jar into run-dev/mods)", target.name(), target.modId());
                    skip = true;
                    phase = 7;
                    phaseStart = t;
                    return;
                }
                int[] o = OFFSETS[index];
                BlockPos pos = origin.offset(o[0], o[1], o[2]);
                BlockHitResult hit = new BlockHitResult(Vec3.atCenterOf(pos).add(0, 0.4, 0), Direction.UP, pos, false);
                mc.gameMode.useItemOn(mc.player, InteractionHand.MAIN_HAND, hit);
                step(t, 2);
            }
            case 2 -> {
                if (mc.gui.screen() instanceof AbstractContainerScreen<?> screen && dt > 10) {
                    beforeChest = chestSlots(screen);
                    DevClient.REPORT.line("%-20s opened %s with %d container slots; before: %s", target.name(), screen.getClass().getSimpleName(), containerSize(screen), beforeChest);
                    step(t, 3);
                } else if (dt > 100) {
                    fail(mc, target, "the storage screen never opened (screen = " + mc.gui.screen() + ")");
                }
            }
            case 3 -> {
                AbstractContainerScreen<?> screen = (AbstractContainerScreen<?>) mc.gui.screen();
                if (dt == 5 && target.scatter()) scatter(mc, screen);
                // wait one second so Lootr has filled its per-player inventory before the snapshot
                if (dt == 25) beforeChest = chestSlots(screen);
                if (dt == 26) moveCursor(mc, screen, 0);
                if (dt == 32) {
                    clickMiddle(mc);
                    step(t, 4);
                }
            }
            case 4 -> {
                if (dt < 120) return;
                AbstractContainerScreen<?> screen = (AbstractContainerScreen<?>) mc.gui.screen();
                List<String> after = chestSlots(screen);
                sortedChest = packed(screen, 0, containerSize(screen));
                DevClient.REPORT.line("%-20s middle-click over a CHEST slot → %s; after: %s", target.name(), sortedChest ? "SORTED" : "NOT sorted", after);
                if (!sortedChest) failed = true;
                step(t, 5);
            }
            case 5 -> {
                AbstractContainerScreen<?> screen = (AbstractContainerScreen<?>) mc.gui.screen();
                if (dt == 1) moveCursor(mc, screen, containerSize(screen)); // first slot of the player's main inventory
                if (dt == 7) clickMiddle(mc);
                if (dt < 127) return;
                int from = containerSize(screen);
                sortedInventory = packed(screen, from, from + 27);
                DevClient.REPORT.line("%-20s middle-click over a PLAYER INVENTORY slot → %s; inventory: %s", target.name(), sortedInventory ? "SORTED" : "NOT sorted", slotsText(screen, from, from + 27));
                if (!sortedInventory && !target.scatter()) failed = true;
                step(t, 7);
            }
            case 7 -> {
                if (mc.gui.screen() != null) mc.player.closeContainer();
                if (dt < 10) return;
                next(t);
            }
            default -> { }
        }
    }

    private static void step(int t, int to) {
        phase = to;
        phaseStart = t;
    }

    private static void next(int t) {
        index++;
        if (index >= TARGETS.size()) {
            DevClient.REPORT.line("RESULT %s", failed ? "FAIL" : "PASS");
            DevClient.REPORT.write();
            Minecraft.getInstance().stop();
            return;
        }
        phase = 0;
        phaseStart = t;
    }

    private static void fail(Minecraft mc, Target target, String why) {
        failed = true;
        DevClient.REPORT.line("%-20s FAIL %s", target.name(), why);
        phase = 7;
        phaseStart = Integer.MAX_VALUE / 2; // closes next tick, then moves on
        phaseStart = 0;
    }

    private static void send(Minecraft mc, String command) {
        mc.player.connection.sendCommand(command);
    }

    // ------------------------------------------------------------------------------------------------ input
    /** Puts the cursor over the centre of menu slot {@code slotIndex} (the hovered slot is updated while the screen is drawn, so the click comes a few ticks later). */
    private static void moveCursor(Minecraft mc, AbstractContainerScreen<?> screen, int slotIndex) {
        Slot slot = screen.getMenu().slots.get(slotIndex);
        Window w = mc.getWindow();
        double guiX = screen.getLeftPos() + slot.x + 8;
        double guiY = screen.getTopPos() + slot.y + 8;
        double x = guiX * w.getScreenWidth() / w.getGuiScaledWidth();
        double y = guiY * w.getScreenHeight() / w.getGuiScaledHeight();
        mc.mouseHandler.onMove(w.handle(), x, y, 0, 0);
    }

    /** A middle press + release through the real MouseHandler (what NeoForge's screen hooks and the sorting mod see for a real mouse). */
    private static void clickMiddle(Minecraft mc) {
        MouseHandler mouse = mc.mouseHandler;
        long handle = mc.getWindow().handle();
        mouse.onButton(handle, new MouseButtonInfo(MIDDLE, 0), 1);
        mouse.onButton(handle, new MouseButtonInfo(MIDDLE, 0), 0);
    }

    /** Sophisticated storage is not a vanilla container (no /item replace): scatter the player's items into it with ordinary pick-up clicks. */
    private static void scatter(Minecraft mc, AbstractContainerScreen<?> screen) {
        int[][] moves = {{30, 5}, {36, 14}, {44, 22}, {52, 1}}; // player slot (menu index) → storage slot
        for (int[] m : moves) {
            mc.gameMode.handleContainerInput(screen.getMenu().containerId, m[0], 0, net.minecraft.world.inventory.ContainerInput.PICKUP, mc.player);
            mc.gameMode.handleContainerInput(screen.getMenu().containerId, m[1], 0, net.minecraft.world.inventory.ContainerInput.PICKUP, mc.player);
        }
    }

    // ------------------------------------------------------------------------------------------------ reading slots
    /** Slots that belong to the opened storage (everything before the player's 36 slots). */
    private static int containerSize(AbstractContainerScreen<?> screen) {
        return screen.getMenu().slots.size() - 36;
    }

    private static List<String> chestSlots(AbstractContainerScreen<?> screen) {
        return slotsText(screen, 0, containerSize(screen));
    }

    private static List<String> slotsText(AbstractContainerScreen<?> screen, int from, int to) {
        List<String> out = new ArrayList<>();
        for (int i = from; i < to && i < screen.getMenu().slots.size(); i++) {
            ItemStack s = screen.getMenu().slots.get(i).getItem();
            if (!s.isEmpty()) out.add(i + ":" + s.getItem().toString().replace("minecraft:", "") + "x" + s.getCount());
        }
        return out;
    }

    /** Non-empty slots form a prefix of [from, to) (the first empty slot is after every item) and no item type has two partial stacks. */
    private static boolean packed(AbstractContainerScreen<?> screen, int from, int to) {
        boolean sawEmpty = false;
        Map<String, Integer> partial = new HashMap<>();
        int items = 0;
        for (int i = from; i < to && i < screen.getMenu().slots.size(); i++) {
            ItemStack s = screen.getMenu().slots.get(i).getItem();
            if (s.isEmpty()) {
                sawEmpty = true;
                continue;
            }
            items++;
            if (sawEmpty) return false;
            if (s.getCount() < s.getMaxStackSize()) partial.merge(s.getItem().toString(), 1, Integer::sum);
        }
        return items > 0 && partial.values().stream().allMatch(n -> n <= 1);
    }

    // ------------------------------------------------------------------------------------------------ IPN diagnosis
    /** What the sorting mod thinks its defaults are (reflection: Theoria never compiles against it). Informational. */
    private static void dumpIpnSettings() {
        if (!ModList.get().isLoaded("inventoryprofilesnext")) return;
        if (System.getenv("THEORIA_SORT_BIND") != null) bindMiddleViaApi();
        String[][] probes = {
                {"org.anti_ad.mc.ipnext.config.Hotkeys", "getSORT_INVENTORY"},
                {"org.anti_ad.mc.ipnext.config.Hotkeys", "getMOVE_ALL_ITEMS"},
                {"org.anti_ad.mc.ipnext.config.ModSettings", "getSORT_AT_CURSOR"},
                {"org.anti_ad.mc.ipnext.config.ModSettings", "getENABLE_PROFILES"},
                {"org.anti_ad.mc.ipnext.config.GuiSettings", "getENABLE_PROFILES_UI"},
                {"org.anti_ad.mc.ipnext.config.GuiSettings", "getENABLE_INVENTORY_SETTINGS_BUTTON"},
                {"org.anti_ad.mc.ipnext.config.GuiSettings", "getENABLE_INVENTORY_EDITOR_BUTTON"}};
        for (String[] p : probes) {
            try {
                Class<?> c = Class.forName(p[0], true, SortScenario.class.getClassLoader());
                Object instance = c.getField("INSTANCE").get(null);
                Object option = c.getMethod(p[1]).invoke(instance);
                StringBuilder detail = new StringBuilder(String.valueOf(option));
                for (String getter : new String[] {"getBooleanValue", "getValue", "getMainKeybind", "getKeyBind"}) {
                    try {
                        Method m = option.getClass().getMethod(getter);
                        detail.append(" | ").append(getter).append("=").append(m.invoke(option));
                    } catch (ReflectiveOperationException ignored) {
                        // not every option type has every getter
                    }
                }
                DevClient.REPORT.line("ipn %-48s %s", p[0].substring(p[0].lastIndexOf('.') + 1) + "." + p[1].substring(3), detail);
                if (p[1].equals("getSORT_INVENTORY") || p[1].equals("getMOVE_ALL_ITEMS")) {
                    Object keybind = option.getClass().getMethod("getMainKeybind").invoke(option);
                    for (Method m : keybind.getClass().getMethods()) {
                        if (m.getParameterCount() != 0 || m.getReturnType() == void.class || m.getDeclaringClass() == Object.class) continue;
                        try {
                            DevClient.REPORT.line("      %s.%s = %s", p[1].substring(3), m.getName(), m.invoke(keybind));
                        } catch (ReflectiveOperationException | RuntimeException ignored) {
                            // some getters need arguments of the running game
                        }
                    }
                }
            } catch (ReflectiveOperationException | RuntimeException e) {
                DevClient.REPORT.line("ipn %-48s unreadable: %s", p[1], e);
            }
        }
    }

    /** Experiment: bind the sort hotkey to the middle mouse button through IPN's own API (THEORIA_SORT_BIND=1), and print the JSON IPN would save for it (the format the pack ships). */
    private static void bindMiddleViaApi() {
        try {
            Class<?> c = Class.forName("org.anti_ad.mc.ipnext.config.Hotkeys", true, SortScenario.class.getClassLoader());
            Object option = c.getMethod("getSORT_INVENTORY").invoke(c.getField("INSTANCE").get(null));
            Object keybind = option.getClass().getMethod("getMainKeybind").invoke(option);
            keybind.getClass().getMethod("setKeyCodes", List.class).invoke(keybind, List.of(-99));
            DevClient.REPORT.line("ipn BOUND SORT_INVENTORY to MOUSE_BUTTON_3 (-99); keybind json = %s", keybind.getClass().getMethod("toJsonElement").invoke(keybind));
            DevClient.REPORT.line("ipn option json = %s", option.getClass().getMethod("toJsonElement").invoke(option));
            for (String[] t : new String[][] {{"org.anti_ad.mc.ipnext.config.ModSettings", "getENABLE_PROFILES"}, {"org.anti_ad.mc.ipnext.config.GuiSettings", "getENABLE_PROFILES_UI"}}) {
                Class<?> k = Class.forName(t[0], true, SortScenario.class.getClassLoader());
                Object toggle = k.getMethod(t[1]).invoke(k.getField("INSTANCE").get(null));
                toggle.getClass().getMethod("setValue", boolean.class).invoke(toggle, false);
                DevClient.REPORT.line("ipn %s set false; json = %s", t[1], toggle.getClass().getMethod("toJsonElement").invoke(toggle));
            }
        } catch (ReflectiveOperationException | RuntimeException e) {
            DevClient.REPORT.line("ipn bind failed: %s", e);
        }
    }
}
