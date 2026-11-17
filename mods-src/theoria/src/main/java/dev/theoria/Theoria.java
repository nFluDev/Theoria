package dev.theoria;

import com.mojang.logging.LogUtils;
import dev.theoria.balance.Balance;
import dev.theoria.catalyst.CatalystModule;
import dev.theoria.core.Net;
import dev.theoria.core.TheoriaConfig;
import dev.theoria.core.TheoriaRegistries;
import dev.theoria.core.check.CoreChecks;
import dev.theoria.core.command.TheoriaCommand;
import dev.theoria.devtest.Dev;
import dev.theoria.devtest.DevGameTests;
import dev.theoria.gear.GearModule;
import dev.theoria.hud.HudModule;
import dev.theoria.mobs.MobModule;
import dev.theoria.wings.WingsModule;
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
        if (Dev.ENABLED) {
            LOGGER.warn("Theoria dev tests ENABLED (-Dtheoria.dev=true)");
            DevGameTests.init(modBus);
        }
    }

    public static String version() {
        return version;
    }

    public static Identifier id(String path) {
        return Identifier.fromNamespaceAndPath(MODID, path);
    }
}
