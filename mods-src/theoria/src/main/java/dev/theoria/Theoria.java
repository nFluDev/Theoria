package dev.theoria;

import com.mojang.logging.LogUtils;
import dev.theoria.balance.Balance;
import dev.theoria.catalyst.CatalystModule;
import dev.theoria.core.Net;
import dev.theoria.core.TheoriaConfig;
import dev.theoria.core.TheoriaRegistries;
import dev.theoria.core.check.CoreChecks;
import dev.theoria.core.command.TheoriaCommand;
import dev.theoria.cosmetics.CosmeticsModule;
import dev.theoria.curios.CuriosModule;
import dev.theoria.gear.GearModule;
import dev.theoria.hud.HudModule;
import dev.theoria.mobs.MobModule;
import dev.theoria.relics.RelicModule;
import dev.theoria.stats.StatsModule;
import dev.theoria.wings.WingsModule;
import dev.theoria.worlds.WorldsModule;
import net.minecraft.resources.Identifier;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.ModContainer;
import net.neoforged.fml.common.Mod;
import org.slf4j.Logger;

/** Theoria main entry point. Everything is wired from this constructor, in this order. */
@Mod(Theoria.MODID)
public final class Theoria {
    public static final String MODID = "theoria";
    public static final Logger LOGGER = LogUtils.getLogger();
    private static String version = "?";

    public Theoria(IEventBus modBus, ModContainer container) {
        version = container.getModInfo().getVersion().toString();
        LOGGER.info("Theoria {} loading", version);
        // One place, fixed order: scattered static init crashed LucidRem at startup (docs/02 §7.9).
        TheoriaRegistries.init(modBus);
        TheoriaConfig.init(container);
        Net.init(modBus);
        TheoriaCommand.init();
        CoreChecks.init();
        Balance.init(); // Faz 2: attribute ceilings, tier data, damage/heal hooks, mob tiers, /theoria tier, balance checks
        HudModule.init(); // Faz 3: HUD model self-checks (/theoria test hud); drawing is client-only (TheoriaClient)
        WingsModule.init(); // Faz 4: wing choice attachment + select payload + re-wear after death (/theoria test wings); screen is client-only
        CatalystModule.init(modBus); // Faz 7: catalysts, the Catalyst Forge, virtual enchantments, combat hooks (/theoria test catalysts)
        GearModule.init(modBus); // Faz 6: materials, armor/tools/weapons, ores, alloys, vanilla re-tune, netherite gear removal (/theoria test gear)
        MobModule.init(modBus); // Faz 8: 12 archetypes, ~35 species + 3 bosses, spawn rules, zone tiers, XP scale, weapon normalization, Heart Crystals (/theoria test mobs)
        StatsModule.init(modBus); // Faz 10: lifetime XP + level (L2 = 55 raw XP, cap 9999), 8 soft-capped stats, 4 points/level, Orb of Oblivion, /theoria stats (/theoria test stats)
        CosmeticsModule.init(); // Faz 11: the cosmetic armor attachment + look rule (/theoria test cosmetics); the drawing is client-only
        CuriosModule.init(); // Faz 11: Curios Max menu, open request + redirect from Curios' own menu (/theoria test curios); slot sizes are datapack JSON
        RelicModule.init(); // Faz 12: ~25 relics (data → Curios trinkets), worn-relic effects, loot bound by modifiers (/theoria test relic [id])
        WorldsModule.init(); // Faz 9b: world-generation self-checks (/theoria test worlds); the worldgen itself is data
        if (Boolean.getBoolean("theoria.dev")) bootDevTests(modBus);
    }

    /**
     * Dev tests live in {@code dev.theoria.devtest}, which the RELEASE jar does not contain (build.gradle: {@code jar} excludes it unless {@code -PkeepDevTests}); the dev run configs use the
     * compiled classes directly. So nothing outside that package may name it at link time — it is reached by reflection, and only with {@code -Dtheoria.dev=true}.
     */
    private static void bootDevTests(IEventBus modBus) {
        try {
            Class.forName("dev.theoria.devtest.DevGameTests", true, Theoria.class.getClassLoader()).getMethod("init", IEventBus.class).invoke(null, modBus);
            LOGGER.warn("Theoria dev tests ENABLED (-Dtheoria.dev=true)");
        } catch (ClassNotFoundException e) {
            LOGGER.warn("-Dtheoria.dev=true is set but this build has no dev tests (release jar) — ignored");
        } catch (ReflectiveOperationException e) {
            throw new IllegalStateException("Theoria dev tests failed to start", e); // a broken dev harness must be loud, not a silent skip
        }
    }

    public static String version() {
        return version;
    }

    public static Identifier id(String path) {
        return Identifier.fromNamespaceAndPath(MODID, path);
    }
}
