package dev.theoria.hud.client;

import dev.theoria.hud.HealthTracker;
import dev.theoria.hud.HudConfig;
import dev.theoria.hud.HudEffects;
import dev.theoria.hud.HudLayout;
import dev.theoria.hud.HudText;
import dev.theoria.hud.StatusModel;
import java.util.List;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.player.Player;

/**
 * The status HUD in one layer, right after the (cancelled) vanilla {@code player_health} slot: health, armor, absorption,
 * hunger + saturation, vehicle health and the centre numbers. Layout and model: docs/04 §1, {@link HudLayout}, {@link StatusModel}.
 * Vanilla's own effects are kept (D18): damage blink, regeneration wave, low-health jitter, hunger shake ({@link HudEffects}).
 *
 * <pre>
 *  row C  [absorption x5]  (only while absorbed)
 *  row A  [armor x5]      armor number        [vehicle x5]  (only riding a living vehicle with health)
 *  row B  [heart x5]      health number       [hunger  x5]
 * </pre>
 */
final class StatusLayer {
    /** Peak of the current absorption pool: the row starts full when granted and melts in proportion (LucidRem). */
    private static double absorptionPeak;
    private static HealthTracker tracker;
    /** Vanilla seeds its HUD jitter per tick (tickCount * 312871), not per frame: the shake does not flicker at high FPS. */
    private static final RandomSource RANDOM = RandomSource.create();

    private StatusLayer() {}

    /** Vanilla's own condition for the survival layers (ADR-002: layers added by events run unconditionally, so we repeat it). */
    static boolean survivalVisible(Minecraft mc) {
        return mc.gameMode != null && mc.gameMode.canHurtPlayer() && !mc.gui.hud.isHidden();
    }

    static void render(GuiGraphicsExtractor g, DeltaTracker delta) {
        Minecraft mc = Minecraft.getInstance();
        if (!survivalVisible(mc) || !(mc.getCameraEntity() instanceof Player player)) return;
        HudClient.statusDraws++;
        HudConfig cfg = HudConfig.get();
        HudConfig.Layout lo = cfg.layout();
        HudConfig.Effects fx = cfg.effects();
        HudLayout lay = HudLayout.of(cfg, g.guiWidth(), g.guiHeight());
        boolean hardcore = player.level().getLevelData().isHardcore();
        int icons = lo.icons();
        long tick = player.tickCount;
        RANDOM.setSeed(tick * 312871L);

        int[] left = new int[icons];
        int[] right = new int[icons];
        for (int i = 0; i < icons; i++) {
            left[i] = lay.leftIconX(cfg, i);
            right[i] = lay.rightIconX(cfg, i);
        }

        // --- health (row B, left)
        double current = player.getHealth();
        double maximum = player.getMaxHealth();
        double absorption = player.getAbsorptionAmount();
        int currentWhole = Mth.ceil(current);
        if (tracker == null) tracker = new HealthTracker(fx);
        tracker.update(currentWhole, player.damageCooldownTime > 0, tick);
        boolean blink = tracker.blinking(tick);
        int type = heartType(player);
        int effectTint = type == 1 ? cfg.tints().poison() : type == 2 ? cfg.tints().wither() : type == 3 ? cfg.tints().frozen() : 0;

        int[] hearts = StatusBars.flat(icons, lay.yB());
        if (HudEffects.isLowHealth(cfg, currentWhole + Mth.ceil(absorption), maximum)) {
            for (int i = 0; i < icons; i++) hearts[i] += RANDOM.nextInt(fx.lowHealthJitter());
            HudClient.lowHealthFrames++;
        }
        if (player.hasEffect(MobEffects.REGENERATION)) {
            int lifted = HudEffects.regenLifted(cfg, tick);
            if (lifted >= 0) hearts[lifted] -= fx.regenLift();
            HudClient.regenFrames++;
        }
        StatusBars.container(g, cfg, left, hearts, HudSprites.heartContainer(hardcore, blink));
        if (blink) {
            HudClient.blinkFrames++;
            // the part just lost stays lit at the old value while the hearts blink; the current fill is drawn over it
            if (tracker.display() > currentWhole) {
                StatusBars.layers(g, cfg, left, hearts, StatusModel.health(cfg, tracker.display()), cfg.healthPalette(),
                        HudSprites.heartFull(type, hardcore, true), HudSprites.HEART_NEUTRAL, cfg.tints().blink(), false);
            }
        }
        StatusBars.layers(g, cfg, left, hearts, StatusModel.health(cfg, current), cfg.healthPalette(), HudSprites.heartFull(type, hardcore, false),
                HudSprites.HEART_NEUTRAL, effectTint, false);

        // --- armor (row A, left): nothing at 0, like vanilla
        int armor = player.getArmorValue();
        List<String> armorText = List.of();
        if (armor > 0) {
            StatusBars.layered(g, cfg, left, StatusBars.flat(icons, lay.yA()), StatusModel.armor(cfg, armor), cfg.armorPalette(),
                    new StatusBars.Sprites(HudSprites.ARMOR_EMPTY, HudSprites.ARMOR_FULL, HudSprites.ARMOR_NEUTRAL), 0, false);
            armorText = HudText.armorCandidates(armor, player.getAttributeValue(Attributes.ARMOR_TOUGHNESS), HudNumbers.suffixes(mc));
        }

        // --- absorption (row C, left): its own row, never mixed into the hearts
        boolean absorbed = absorption > 0;
        if (absorbed) {
            absorptionPeak = Math.max(absorptionPeak, absorption);
            int quarters = StatusModel.single(absorption, absorptionPeak, lo.layerQuarters());
            StatusBars.single(g, cfg, left, StatusBars.flat(icons, lay.yA() - lo.rowHeight()), quarters, HudSprites.heartContainer(hardcore, false),
                    HudSprites.absorbingFull(hardcore), -1, false);
        } else {
            absorptionPeak = 0;
        }

        // --- hunger (row B, right): 5 icons x 4 points; vanilla drains from the left, so the rightmost icon fills first
        boolean hunger = player.hasEffect(MobEffects.HUNGER);
        var food = player.getFoodData();
        int[] foodYs = StatusBars.flat(icons, lay.yB());
        if (HudEffects.hungerShakes(tick, food.getFoodLevel(), food.getSaturationLevel())) {
            for (int i = 0; i < icons; i++) foodYs[i] += RANDOM.nextInt(fx.hungerShake()) - 1;
            HudClient.hungerShakeFrames++;
        }
        StatusBars.single(g, cfg, right, foodYs, StatusModel.single(food.getFoodLevel(), 20, lo.layerQuarters()),
                hunger ? HudSprites.FOOD_EMPTY_HUNGER : HudSprites.FOOD_EMPTY, hunger ? HudSprites.FOOD_FULL_HUNGER : HudSprites.FOOD_FULL, -1, true);
        int saturation = StatusModel.single(food.getSaturationLevel(), 20, lo.layerQuarters());
        if (saturation > 0) {
            StatusBars.single(g, cfg, right, foodYs, saturation, null, HudSprites.FOOD_NEUTRAL, cfg.tints().saturation(), true);
        }

        // --- vehicle health (row A, right): the same layered model, brown vanilla vehicle hearts at layer 0
        if (player.getVehicle() instanceof LivingEntity vehicle && vehicle.showVehicleHealth()) {
            StatusBars.layered(g, cfg, right, StatusBars.flat(icons, lay.yA()), StatusModel.health(cfg, vehicle.getHealth()), cfg.healthPalette(),
                    new StatusBars.Sprites(HudSprites.VEHICLE_CONTAINER, HudSprites.VEHICLE_FULL, HudSprites.VEHICLE_NEUTRAL), 0, true);
        }

        HudNumbers.draw(g, mc, cfg, lay, HudText.healthCandidates(current, maximum, absorption, HudNumbers.suffixes(mc)), armorText);

        // ADR-002: vanilla layers advance these counters; ours are cancelled, so hand over the space we used — the air bubbles,
        // the overlay message and the held-item name stack on top of the rows instead of drawing across them.
        int used = lo.bottom() + lo.reservedRows() * lo.rowHeight();
        mc.gui.hud.leftHeight = used + (absorbed ? lo.rowHeight() : 0);
        mc.gui.hud.rightHeight = used;
    }

    /** Vanilla {@code Hud.HeartType.forPlayer}: 0 normal, 1 poisoned, 2 withered, 3 frozen. */
    private static int heartType(Player player) {
        if (player.hasEffect(MobEffects.POISON)) return 1;
        if (player.hasEffect(MobEffects.WITHER)) return 2;
        return player.isFullyFrozen() ? 3 : 0;
    }
}
