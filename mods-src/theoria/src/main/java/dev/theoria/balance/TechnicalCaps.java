package dev.theoria.balance;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import dev.theoria.Theoria;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import net.minecraft.core.Holder;
import net.minecraft.world.entity.ai.attributes.Attribute;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.ai.attributes.RangedAttribute;

/**
 * Lifts vanilla's attribute ceilings (armor 30, toughness 20, max health 1024, attack damage 2048) — ADR-003 Karar 2.
 * These are <em>technical</em> ceilings far above anything the balance reaches, not balance values (those live in the
 * damage formula). The numbers come from {@code theoria/technical_caps.json} (tools/balance/tiers.py ← combat.csv) read
 * from the jar, not the datapack: the mod constructor runs before any datapack exists, the attribute objects are global,
 * and client and server must agree. Runs on both sides, once, from the mod constructor, before registries are used.
 */
public final class TechnicalCaps {
    private static final String RESOURCE = "/theoria/technical_caps.json";

    public record Caps(double armor, double armorToughness, double health, double attackDamage) {}

    private static Caps applied;

    private TechnicalCaps() {}

    public static Caps load() {
        try (InputStream in = TechnicalCaps.class.getResourceAsStream(RESOURCE)) {
            if (in == null) throw new IllegalStateException("Missing jar resource " + RESOURCE);
            JsonObject json = JsonParser.parseReader(new InputStreamReader(in, StandardCharsets.UTF_8)).getAsJsonObject();
            return new Caps(json.get("armor").getAsDouble(), json.get("armor_toughness").getAsDouble(),
                    json.get("health").getAsDouble(), json.get("attack_damage").getAsDouble());
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    /** Idempotent. Only ever raises a ceiling, never lowers one. */
    public static synchronized void apply() {
        if (applied != null) return;
        Caps caps = load();
        lift(Attributes.ARMOR, caps.armor());
        lift(Attributes.ARMOR_TOUGHNESS, caps.armorToughness());
        lift(Attributes.MAX_HEALTH, caps.health());
        lift(Attributes.ATTACK_DAMAGE, caps.attackDamage());
        applied = caps;
        Theoria.LOGGER.info("Attribute ceilings lifted: armor {}, toughness {}, max health {}, attack damage {}",
                caps.armor(), caps.armorToughness(), caps.health(), caps.attackDamage());
    }

    /** The caps applied at startup (for checks); loads them if {@link #apply()} has not run. */
    public static Caps caps() {
        return applied != null ? applied : load();
    }

    private static void lift(Holder<Attribute> attribute, double cap) {
        if (attribute.value() instanceof RangedAttribute ranged && ranged.maxValue < cap) {
            ranged.maxValue = cap; // public non-final through accesstransformer.cfg
        }
    }
}
