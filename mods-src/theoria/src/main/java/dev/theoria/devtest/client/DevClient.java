package dev.theoria.devtest.client;

import com.mojang.blaze3d.platform.MacosUtil;
import com.mojang.blaze3d.systems.RenderSystem;
import dev.theoria.Theoria;
import dev.theoria.core.client.NetClient;
import dev.theoria.devtest.Dev;
import dev.theoria.devtest.DevProbe;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.AccessibilityOnboardingScreen;
import net.minecraft.client.gui.screens.DeathScreen;
import net.minecraft.client.gui.screens.LevelLoadingScreen;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.TitleScreen;
import net.minecraft.client.resources.language.I18n;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.LevelSettings;
import net.minecraft.world.level.WorldDataConfiguration;
import net.minecraft.world.level.levelgen.WorldOptions;
import net.minecraft.world.level.levelgen.presets.WorldPresets;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.client.event.ClientChatReceivedEvent;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.client.event.RenderFrameEvent;
import net.neoforged.fml.ModList;
import net.neoforged.neoforge.common.NeoForge;
import org.lwjgl.sdl.SDLHints;

/**
 * Unattended client scenarios ({@code ./gradlew runDevClient}): opens a fixed-seed creative world, runs the
 * scenario selected by -Dtheoria.dev.client and quits the game by itself.
 * "smoke": payload → synced attachment → reply, /theoria commands, lang. "fps": {@link FpsBench}. "hud": {@link HudScenario} (Phase 3), "wings": {@link WingsScenario} (Phase 4), "catalyst": {@link CatalystScenario} (Phase 7b), "move": {@link MoveBench} (movement frame times / desync).
 */
public final class DevClient {
    static final String WORLD = "theoria-dev";
    static final long SEED = 260309L;
    private static final int PING = 1234;

    static final Dev.Report REPORT = new Dev.Report(
            (Dev.CLIENT_MODE.equals("fps") ? "fps" : Dev.CLIENT_MODE.equals("move") ? "move" : Dev.CLIENT_MODE.equals("hud") ? "client_hud" : Dev.CLIENT_MODE.equals("wings") ? "client_wings" : Dev.CLIENT_MODE.equals("catalyst") ? "client_catalyst" : "client_smoke") + System.getProperty("theoria.dev.label", ""));
    private static boolean worldRequested;
    private static int ticks = -1;
    private static int pong = -1;
    private static boolean smokePass;
    private static final List<String> chat = new ArrayList<>();
    // Phase 5 (full pack): the scenarios must not start before the world is on screen and the renderer sustains a frame rate.
    private static long frames;
    private static boolean ready;
    private static int readyTicks;
    private static int readyAt;
    private static long frameMark;
    private static int markTick;
    private static int serverMark;
    private static int stableSeconds;

    private DevClient() {}

    /** Chat lines received so far (the scenarios read command answers from here). */
    static List<String> chat() {
        return chat;
    }

    public static void init(IEventBus modBus) {
        NetClient.handle(DevProbe.Pong.TYPE, payload -> pong = payload.value());
        NeoForge.EVENT_BUS.addListener(DevClient::onTick);
        NeoForge.EVENT_BUS.addListener((RenderFrameEvent.Pre event) -> { frames++; MoveBench.framePre(); });
        NeoForge.EVENT_BUS.addListener((RenderFrameEvent.Post event) -> MoveBench.framePost(Minecraft.getInstance()));
        NeoForge.EVENT_BUS.addListener((ClientChatReceivedEvent event) -> chat.add(event.getMessage().getString()));
    }

    private static void onTick(ClientTickEvent.Post event) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null || mc.player == null) {
            Screen s = mc.gui.screen();
            if (!worldRequested && mc.gui.overlay() == null && (s instanceof TitleScreen || s instanceof AccessibilityOnboardingScreen)) {
                worldRequested = true;
                mc.options.pauseOnLostFocus = false;
                openOrCreateWorld(mc);
            }
            return;
        }
        if (mc.gui.screen() instanceof DeathScreen) {
            mc.player.respawn();
            mc.gui.setScreen(null);
        }
        ticks++;
        if (ticks == 0) {
            var info = RenderSystem.getDevice().getDeviceInfo();
            REPORT.line("backend=%s driver=%s device=%s window=%dx%d fullscreen=%s",
                    info.backendName(), info.driverInfo(), info.name(), mc.getWindow().getWidth(), mc.getWindow().getHeight(),
                    mc.getWindow().isWindowFullscreen());
        }
        if (Dev.CLIENT_MODE.equals("fps") || Dev.CLIENT_MODE.equals("move") || Dev.CLIENT_MODE.equals("hud") || Dev.CLIENT_MODE.equals("wings") || Dev.CLIENT_MODE.equals("catalyst")) {
            if (!renderReady(mc)) return;
            int t = ticks - readyAt; // scenario time starts when the world is on screen and frames flow
            if (Dev.CLIENT_MODE.equals("fps")) FpsBench.tick(mc, t);
            else if (Dev.CLIENT_MODE.equals("move")) MoveBench.tick(mc, t);
            else if (Dev.CLIENT_MODE.equals("hud")) HudScenario.tick(mc, t);
            else if (Dev.CLIENT_MODE.equals("catalyst")) CatalystScenario.tick(mc, t);
            else WingsScenario.tick(mc, t);
        } else {
            smokeTick(mc);
        }
    }

    /**
     * True once the loading screen is gone and, for two consecutive seconds, the renderer drew at least 15 frames AND the integrated
     * server ran at least 18 ticks. With the full pack the first tens of seconds stall: Terralith chunk generation, and Vitrail only
     * switches the world to the shader pack on the first real frame after its pipelines compile (a ~2 s hitch that also starves the
     * server — seen in Phase 5), so with Vitrail loaded a scenario additionally waits 15 s. Gives up after 3 minutes and says so.
     */
    private static boolean renderReady(Minecraft mc) {
        if (ready) return true;
        readyTicks++;
        int serverTicks = mc.getSingleplayerServer() == null ? 0 : mc.getSingleplayerServer().getTickCount();
        if (mc.gui.screen() instanceof LevelLoadingScreen) {
            stableSeconds = 0;
            markTick = readyTicks;
            frameMark = frames;
            serverMark = serverTicks;
        } else if (readyTicks - markTick >= 20) {
            long drawn = frames - frameMark;
            int ran = serverTicks - serverMark;
            frameMark = frames;
            serverMark = serverTicks;
            markTick = readyTicks;
            stableSeconds = drawn >= 15 && ran >= 18 ? stableSeconds + 1 : 0;
            int minTicks = ModList.get().isLoaded("vitrail") ? 300 : 0;
            if (stableSeconds >= 2 && readyTicks >= minTicks) {
                ready = true;
                readyAt = ticks;
                REPORT.line("render ready after %d ticks (%.1f s since the world appeared; last second: %d frames, %d server ticks)",
                        readyTicks, readyTicks / 20.0, drawn, ran);
            }
        }
        if (!ready && readyTicks > 3600) {
            ready = true;
            readyAt = ticks;
            REPORT.line("WARN render warm-up timed out after 3 minutes — measuring anyway");
        }
        return ready;
    }

    private static void openOrCreateWorld(Minecraft mc) {
        if (mc.getLevelSource().levelExists(WORLD)) {
            REPORT.line("opening existing world %s", WORLD);
            mc.createWorldOpenFlows().openWorld(WORLD, () -> mc.gui.setScreen(new TitleScreen()));
        } else {
            REPORT.line("creating world %s seed=%d (creative, cheats)", WORLD, SEED);
            LevelSettings settings = new LevelSettings(WORLD, GameType.CREATIVE, LevelSettings.DifficultySettings.DEFAULT, true, WorldDataConfiguration.DEFAULT);
            mc.createWorldOpenFlows().createFreshLevel(WORLD, settings, new WorldOptions(SEED, true, false),
                    WorldPresets::createNormalWorldDimensions, new TitleScreen());
        }
    }

    /**
     * Phase 6: every item and block Theoria registers must have a baked model that is not the missing-model fallback, and a translated name
     * (a missing texture/model JSON shows up here; the log only warns). Equipment assets are checked by tools/check_gear.py (no client accessor).
     */
    private static boolean gearAssets(Minecraft mc) {
        var models = mc.getModelManager();
        var missing = models.getMissingModels();
        int items = 0, blocks = 0, bad = 0;
        for (var id : net.minecraft.core.registries.BuiltInRegistries.ITEM.keySet()) {
            if (!id.getNamespace().equals(Theoria.MODID)) continue;
            items++;
            var item = net.minecraft.core.registries.BuiltInRegistries.ITEM.getValue(id);
            if (models.getItemModel(id) == missing.item()) { REPORT.line("GEAR missing item model: %s", id); bad++; }
            String name = new net.minecraft.world.item.ItemStack(item).getHoverName().getString();
            if (name.startsWith("item.") || name.startsWith("block.")) { REPORT.line("GEAR untranslated name: %s -> %s", id, name); bad++; }
        }
        for (var id : net.minecraft.core.registries.BuiltInRegistries.BLOCK.keySet()) {
            if (!id.getNamespace().equals(Theoria.MODID)) continue;
            blocks++;
            var state = net.minecraft.core.registries.BuiltInRegistries.BLOCK.getValue(id).defaultBlockState();
            if (models.getBlockStateModelSet().get(state) == missing.block()) { REPORT.line("GEAR missing block model: %s", id); bad++; }
        }
        // D6: netherite gear is unobtainable — and not offered in creative either (tabs are built from BuildCreativeModeTabContentsEvent)
        net.minecraft.world.item.CreativeModeTabs.tryRebuildTabContents(mc.player.connection.enabledFeatures(), true, mc.level.registryAccess());
        var removed = dev.theoria.gear.GearApi.removedItemIds();
        int offered = 0, total = 0;
        for (var tab : net.minecraft.core.registries.BuiltInRegistries.CREATIVE_MODE_TAB) {
            for (var stack : tab.getSearchTabDisplayItems()) {
                total++;
                if (removed.contains(net.minecraft.core.registries.BuiltInRegistries.ITEM.getKey(stack.getItem()).toString())) offered++;
            }
        }
        if (offered > 0) { REPORT.line("GEAR %d netherite gear stacks are still offered by creative tabs", offered); bad++; }
        if (total < 500) { REPORT.line("GEAR creative tabs look unbuilt (%d stacks)", total); bad++; }
        REPORT.line("gear assets: %d items and %d blocks have baked models and translated names; creative offers %d stacks, %d of them netherite gear; %d problems", items, blocks, total, offered, bad);
        return items > 0 && bad == 0;
    }

    private static void smokeTick(Minecraft mc) {
        switch (ticks) {
            case 40 -> {
                NetClient.send(new DevProbe.Ping(PING));
                mc.player.connection.sendCommand("theoria version");
                mc.player.connection.sendCommand("theoria test core");
                mc.player.connection.sendCommand("theoria test balance");
                // ADR-003: the client must accept the lifted ceilings too (its own attribute objects, synced values)
                mc.player.connection.sendCommand("attribute @s minecraft:armor base set 300");
                mc.player.connection.sendCommand("attribute @s minecraft:max_health base set 214");
            }
            case 100 -> {
                int synced = mc.player.getData(DevProbe.KEPT).value();
                String versionLine = I18n.get("commands.theoria.version", Theoria.version(), "", "").trim();
                boolean langOk = !versionLine.startsWith("commands.theoria");
                boolean versionShown = chat.stream().anyMatch(line -> line.contains(Theoria.version()) && !line.contains("commands.theoria"));
                long okLines = chat.stream()
                        .filter(line -> java.util.stream.IntStream.rangeClosed(1, 99).anyMatch(n -> line.equals(I18n.get("commands.theoria.test.ok", n)))).count();
                boolean testOk = okLines >= 2; // /theoria test core and /theoria test balance
                boolean noFailure = chat.stream().noneMatch(line -> line.startsWith("✘"));
                int armor = mc.player.getArmorValue();
                float maxHealth = mc.player.getMaxHealth();
                boolean attributesOk = armor == 300 && maxHealth == 214f;
                chat.forEach(line -> REPORT.line("chat> %s", line));
                REPORT.line("payload: sent Ping(%d), Pong=%d; synced attachment on client=%d", PING, pong, synced);
                REPORT.line("lang (%s): version key translated=%s; /theoria version shown=%s; /theoria test core + balance ok lines=%d, failures=%s",
                        mc.getLanguageManager().getSelected(), langOk, versionShown, okLines, !noFailure);
                REPORT.line("client attributes after /attribute: getArmorValue=%d (expect 300), getMaxHealth=%.0f (expect 214)", armor, (double) maxHealth);
                // Phase 5 / ADR-005 decision 1: on macOS the option reaches SDL as a hint. false -> "0" (Ctrl+click stays a left click).
                // The pack's options.txt sets ctrlClickEmulatesRightClick:false and quitShortcuts:false; only a dev run with the
                // pack's options (or a fresh vanilla one) proves the chain option -> hint. A user's own setting is reported, not judged.
                boolean emulate = mc.options.ctrlClickEmulatesRightClick().get();
                String hint = MacosUtil.IS_MACOS ? SDLHints.SDL_GetHint("SDL_MAC_CTRL_CLICK_EMULATE_RIGHT_CLICK") : "n/a (not macOS)";
                boolean hintOk = !MacosUtil.IS_MACOS || (emulate ? "1".equals(hint) : "0".equals(hint));
                REPORT.line("macOS input: option ctrlClickEmulatesRightClick=%s -> SDL hint SDL_MAC_CTRL_CLICK_EMULATE_RIGHT_CLICK=%s (%s); quitShortcuts=%s",
                        emulate, hint, hintOk ? "consistent" : "MISMATCH", mc.options.quitShortcuts().get());
                boolean gearOk = gearAssets(mc);
                smokePass = pong == PING && synced == PING && langOk && versionShown && testOk && noFailure && attributesOk && hintOk && gearOk;
                mc.player.connection.sendCommand("attribute @s minecraft:armor base reset");
                mc.player.connection.sendCommand("attribute @s minecraft:max_health base reset");
            }
            case 110 -> {
                REPORT.line("RESULT %s", smokePass ? "PASS" : "FAIL");
                REPORT.write();
                mc.stop();
            }
            default -> {}
        }
    }
}
