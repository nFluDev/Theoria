package dev.theoria.hud.client;

import dev.theoria.Theoria;
import net.minecraft.resources.Identifier;

/**
 * Every sprite id the HUD draws. Literal {@code vanilla("…")} / {@code own("…")} calls ONLY — tools/check_hud.py scans this file
 * and verifies each id exists (a typo compiles, runs, and draws the purple-black "missing texture" square — visible only on
 * screen), that our own sprites are neutral (tint multiplies), and that none is a {@code _half} sprite (half and quarter fills
 * are clipped from the full sprite at draw time; vanilla's armor_half has an OPAQUE empty half that blacks out the layer under it).
 */
final class HudSprites {
    /** Hearts by vanilla {@code Hud.HeartType} order: normal, poisoned, withered, frozen; [type][hardcore][blinking]. */
    private static final Identifier[][][] HEART_FULL = {
        {{vanilla("hud/heart/full"), vanilla("hud/heart/full_blinking")}, {vanilla("hud/heart/hardcore_full"), vanilla("hud/heart/hardcore_full_blinking")}},
        {{vanilla("hud/heart/poisoned_full"), vanilla("hud/heart/poisoned_full_blinking")},
            {vanilla("hud/heart/poisoned_hardcore_full"), vanilla("hud/heart/poisoned_hardcore_full_blinking")}},
        {{vanilla("hud/heart/withered_full"), vanilla("hud/heart/withered_full_blinking")},
            {vanilla("hud/heart/withered_hardcore_full"), vanilla("hud/heart/withered_hardcore_full_blinking")}},
        {{vanilla("hud/heart/frozen_full"), vanilla("hud/heart/frozen_full_blinking")},
            {vanilla("hud/heart/frozen_hardcore_full"), vanilla("hud/heart/frozen_hardcore_full_blinking")}},
    };
    /** [hardcore][blinking] */
    private static final Identifier[][] HEART_CONTAINER = {
        {vanilla("hud/heart/container"), vanilla("hud/heart/container_blinking")},
        {vanilla("hud/heart/container_hardcore"), vanilla("hud/heart/container_hardcore_blinking")},
    };
    private static final Identifier[] ABSORBING_FULL = {vanilla("hud/heart/absorbing_full"), vanilla("hud/heart/absorbing_hardcore_full")};

    static final Identifier VEHICLE_CONTAINER = vanilla("hud/heart/vehicle_container");
    static final Identifier VEHICLE_FULL = vanilla("hud/heart/vehicle_full");

    static final Identifier ARMOR_EMPTY = vanilla("hud/armor_empty");
    static final Identifier ARMOR_FULL = vanilla("hud/armor_full");

    static final Identifier FOOD_EMPTY = vanilla("hud/food_empty");
    static final Identifier FOOD_FULL = vanilla("hud/food_full");
    static final Identifier FOOD_EMPTY_HUNGER = vanilla("hud/food_empty_hunger");
    static final Identifier FOOD_FULL_HUNGER = vanilla("hud/food_full_hunger");

    /** generator/hud.py — tintable neutral bases for layers above 0 (and the saturation sheen). */
    static final Identifier HEART_NEUTRAL = own("hud/heart_neutral");
    static final Identifier VEHICLE_NEUTRAL = own("hud/vehicle_heart_neutral");
    static final Identifier ARMOR_NEUTRAL = own("hud/armor_neutral");
    static final Identifier FOOD_NEUTRAL = own("hud/food_neutral");

    private HudSprites() {}

    static Identifier heartFull(int type, boolean hardcore, boolean blinking) {
        return HEART_FULL[type][hardcore ? 1 : 0][blinking ? 1 : 0];
    }

    static Identifier heartContainer(boolean hardcore, boolean blinking) {
        return HEART_CONTAINER[hardcore ? 1 : 0][blinking ? 1 : 0];
    }

    static Identifier absorbingFull(boolean hardcore) {
        return ABSORBING_FULL[hardcore ? 1 : 0];
    }

    private static Identifier vanilla(String path) {
        return Identifier.withDefaultNamespace(path);
    }

    private static Identifier own(String path) {
        return Identifier.fromNamespaceAndPath(Theoria.MODID, path);
    }
}
