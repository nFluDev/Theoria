package dev.theoria.devtest.client;

import dev.theoria.hud.client.HudClient;
import java.util.List;
import java.util.function.Function;
import net.minecraft.client.Minecraft;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.LivingEntity;

/**
 * Unattended HUD scenario ({@code ./gradlew runDevClient -PdevClient=hud}): steps the survival player through the DoD states
 * (20 / 21 / 250 HP, armor 0 / 310, XP level, absorption, poison, riding a horse, GUI scales 1-4, creative) and asserts what a
 * program CAN see: our layers ran every frame, the vanilla layers were cancelled, the Hud height counters were handed over,
 * nothing threw. How it LOOKS is the user's approval (CLAUDE.md: no screenshots) — see tools/hud_preview.py for the offline render.
 */
final class HudScenario {
    /** A state to enter ({@code commands}, {@code before} run on enter), hold for {@code hold} ticks, then verify ({@code null} = ok). */
    private record Step(String name, List<String> commands, Runnable before, int hold, Function<Minecraft, String> verify) {}

    private static long[] mark = new long[7];
    private static int stepIndex = -1;
    private static int stepStart;
    private static boolean failed;
    private static List<Step> steps;

    private HudScenario() {}

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
            mark = HudClient.counters();
            if (step.before() != null) step.before().run();
            step.commands().forEach(mc.player.connection::sendCommand);
        }
    }

    private static void finish(Minecraft mc, Step step) {
        long[] now = HudClient.counters();
        String problem = step.verify().apply(mc);
        if (problem != null) failed = true;
        DevClient.REPORT.line("%-16s %s  [draws +%d, level +%d, vanilla cancelled +%d, blink +%d, low +%d, regen +%d, hunger +%d] heights left=%d right=%d gui=%dx%d scale %d",
                step.name(), problem == null ? "ok  " : "FAIL " + problem, now[0] - mark[0], now[1] - mark[1], now[2] - mark[2],
                now[3] - mark[3], now[4] - mark[4], now[5] - mark[5], now[6] - mark[6], mc.gui.hud.leftHeight, mc.gui.hud.rightHeight, mc.getWindow().getGuiScaledWidth(), mc.getWindow().getGuiScaledHeight(),
                mc.getWindow().getGuiScale());
    }

    private static long drewStatus() {
        return HudClient.counters()[0] - mark[0];
    }

    private static long drewLevel() {
        return HudClient.counters()[1] - mark[1];
    }

    private static long cancelled() {
        return HudClient.counters()[2] - mark[2];
    }

    private static long blinked() {
        return HudClient.counters()[3] - mark[3];
    }

    private static long lowHealthFrames() {
        return HudClient.counters()[4] - mark[4];
    }

    private static long regenFrames() {
        return HudClient.counters()[5] - mark[5];
    }

    private static long hungerShakes() {
        return HudClient.counters()[6] - mark[6];
    }

    /** Common state check for every survival step: status layer drew, vanilla ones stayed cancelled, two rows reserved. */
    private static String survival(Minecraft mc, int leftHeight) {
        if (!mc.gameMode.canHurtPlayer()) return "not in survival";
        if (drewStatus() <= 0) return "status layer never drew";
        if (cancelled() < 3 * drewStatus()) return "vanilla health/armor/food layers were not cancelled every frame (" + cancelled() + " cancels for " + drewStatus() + " draws)";
        if (mc.gui.hud.rightHeight != 59) return "rightHeight " + mc.gui.hud.rightHeight + " ≠ 59";
        if (mc.gui.hud.leftHeight != leftHeight) return "leftHeight " + mc.gui.hud.leftHeight + " ≠ " + leftHeight;
        return null;
    }

    private static String and(String first, String second) {
        return first != null ? first : second;
    }

    /** The option is clamped to what fits the window (1280x720 → 3), so the expectation is whatever the window itself computes. */
    private static Step scale(int n) {
        return new Step("gui_scale_" + n, List.of(), () -> {
            Minecraft mc = Minecraft.getInstance();
            mc.options.guiScale().set(n);
            mc.resizeGui();
        }, 8, mc -> {
            int expected = mc.getWindow().calculateScale(mc.options.guiScale().get(), mc.isEnforceUnicode());
            return and(survival(mc, 59), mc.getWindow().getGuiScale() == expected && expected >= 1 ? null
                    : "gui scale " + mc.getWindow().getGuiScale() + " ≠ " + expected);
        });
    }

    private static List<Step> buildSteps() {
        // The scenario reuses the persistent dev world (run-dev): whatever was left in it — a /give typed into the window while a run was
        // open (08:44, a diamond chestplate = +8 armor made two later steps FAIL), a horse's leather picked up — must not leak into the
        // armor / health expectations. Gear and inventory are cleared first.
        List<String> reset = List.of("gamemode survival", "effect clear @s", "xp set @s 0 levels", "attribute @s minecraft:max_health base reset",
                "attribute @s minecraft:armor base reset", "clear @s", "item replace entity @s armor.head with minecraft:air",
                "item replace entity @s armor.chest with minecraft:air", "item replace entity @s armor.legs with minecraft:air",
                "item replace entity @s armor.feet with minecraft:air", "ride @s dismount", "kill @e[tag=theoria_hud]");
        return List.of(
                new Step("hp20_armor0", reset, null, 24, mc -> and(survival(mc, 59),
                        mc.player.getMaxHealth() == 20f && mc.player.getArmorValue() == 0 ? null
                                : "expected 20 max HP / 0 armor, got " + mc.player.getMaxHealth() + " / " + mc.player.getArmorValue())),
                // D18: vanilla's HUD effects. Each triggers the real state (damage, regeneration, ...) and counts the frames the effect drew.
                new Step("damage_blink", List.of("damage @s 3"), null, 24,
                        mc -> and(survival(mc, 59), blinked() > 0 ? null : "no blink frames after taking damage")),
                new Step("heal", List.of("effect give @s minecraft:instant_health 1 10"), null, 30, mc -> survival(mc, 59)),
                new Step("low_health", List.of("damage @s 17"), null, 24,
                        mc -> and(survival(mc, 59), lowHealthFrames() > 0 ? null : "no low-health jitter at " + mc.player.getHealth() + "/" + mc.player.getMaxHealth())),
                new Step("heal_again", List.of("effect give @s minecraft:instant_health 1 10"), null, 30, mc -> survival(mc, 59)),
                new Step("regen_wave", List.of("effect give @s minecraft:regeneration 8 1"), null, 24,
                        mc -> and(survival(mc, 59), regenFrames() > 0 ? null : "no regeneration wave frames")),
                new Step("hunger_shake", List.of("effect clear @s", "effect give @s minecraft:hunger 20 100"), null, 140,
                        mc -> and(survival(mc, 59), hungerShakes() > 0 ? null : "no hunger shake with saturation " + mc.player.getFoodData().getSaturationLevel())),
                new Step("food_restore", List.of("effect clear @s", "effect give @s minecraft:saturation 1 20"), null, 24, mc -> survival(mc, 59)),
                new Step("hp21", List.of("attribute @s minecraft:max_health base set 21", "effect give @s minecraft:instant_health 1 10"), null, 24,
                        mc -> and(survival(mc, 59), mc.player.getMaxHealth() == 21f && mc.player.getHealth() == 21f ? null
                                : "expected 21/21, got " + mc.player.getHealth() + "/" + mc.player.getMaxHealth())),
                new Step("hp250_armor310", List.of("attribute @s minecraft:max_health base set 250", "attribute @s minecraft:armor base set 310",
                        "effect give @s minecraft:instant_health 1 10"), null, 24,
                        mc -> and(survival(mc, 59), mc.player.getMaxHealth() == 250f && mc.player.getArmorValue() == 310 && mc.player.getHealth() == 250f
                                ? null : "expected 250/250 HP and 310 armor, got " + mc.player.getHealth() + "/" + mc.player.getMaxHealth() + ", " + mc.player.getArmorValue())),
                new Step("xp_level_12", List.of("xp set @s 12 levels"), null, 16,
                        mc -> and(survival(mc, 59), mc.player.experienceLevel == 12 && drewLevel() > 0 ? null : "level text did not draw (level " + mc.player.experienceLevel + ")")),
                new Step("absorption", List.of("effect give @s minecraft:absorption 60 4"), null, 16,
                        mc -> and(survival(mc, 69), mc.player.getAbsorptionAmount() > 0 ? null : "no absorption")),
                new Step("poison", List.of("effect clear @s", "effect give @s minecraft:poison 4 0"), null, 16,
                        mc -> and(survival(mc, 59), mc.player.hasEffect(MobEffects.POISON) ? null : "no poison")),
                // A saddled horse owns the contextual slot with its jump bar: the XP bar (and our level text inside it) must give way.
                new Step("ride_horse", List.of("effect clear @s", "summon minecraft:horse ~ ~ ~ {Tame:1b,Tags:[\"theoria_hud\"],equipment:{saddle:{id:\"minecraft:saddle\",count:1}}}",
                        "ride @s mount @e[type=minecraft:horse,tag=theoria_hud,limit=1,sort=nearest]"), null, 40,
                        mc -> and(survival(mc, 59), mc.player.getVehicle() instanceof LivingEntity v && v.showVehicleHealth() ? null : "not riding a horse")),
                new Step("jump_bar_owns", List.of(), null, 16,
                        mc -> and(survival(mc, 59), mc.player.jumpableVehicle() == null ? "horse is not jumpable (saddle missing?) — suppress path not exercised"
                                : drewLevel() == 0 ? null : "level text drew over the jump bar (+" + drewLevel() + ")")),
                new Step("dismount", List.of("ride @s dismount", "kill @e[tag=theoria_hud]"), null, 16, mc -> survival(mc, 59)),
                scale(1), scale(2), scale(3), scale(4),
                new Step("gui_scale_auto", List.of(), () -> {
                    Minecraft mc = Minecraft.getInstance();
                    mc.options.guiScale().set(0);
                    mc.resizeGui();
                }, 8, mc -> survival(mc, 59)),
                new Step("to_creative", List.of("gamemode creative", "attribute @s minecraft:max_health base reset", "attribute @s minecraft:armor base reset",
                        "effect clear @s"), null, 16, mc -> mc.gameMode.canHurtPlayer() ? "still in survival" : null),
                // Creative has no health / hunger / XP bar: our layers must stay silent (vanilla's survivalVisible condition, repeated by us).
                new Step("creative_quiet", List.of(), null, 16,
                        mc -> mc.gameMode.canHurtPlayer() ? "survival again" : drewStatus() == 0 && drewLevel() == 0 ? null
                                : "drew in creative: status +" + drewStatus() + ", level +" + drewLevel()));
    }
}
