package dev.theoria.mobs;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.jspecify.annotations.Nullable;

/**
 * The species table ({@code theoria/mobs.json}: generator/mobs.py ← data/mobs.csv + mob_drops.csv) and the mob balance constants
 * ({@code theoria/mob_balance.json}: tools/balance/mobs.py). Jar resources, not a datapack: entity types are registered before any datapack
 * exists (same reason as gear.json, ADR-003 Karar 2). Adding a species = a CSV row.
 */
public final class MobData {
    public static final String SPECIES_RESOURCE = "/theoria/mobs.json";
    public static final String BALANCE_RESOURCE = "/theoria/mob_balance.json";

    /** Zone-tier constants (docs/06 §12). */
    public record Zone(int distStep, int depthFrom, int depthStep, int heightFrom, int heightStep, int nether, int end, int cap, int band) {}

    public record Balance(Zone zone, double xpGrowth, Map<String, Integer> xpBase, int heartHp, int heartMax, Map<String, Integer> weapons) {}

    private static List<Species> species;
    private static Map<String, Species> byId;
    private static Balance balance;

    private MobData() {}

    public static synchronized List<Species> species() {
        if (species == null) loadSpecies();
        return species;
    }

    public static @Nullable Species byId(String id) {
        species();
        return byId.get(id);
    }

    public static synchronized Balance balance() {
        if (balance == null) loadBalance();
        return balance;
    }

    private static JsonObject read(String resource, String generator) {
        try (InputStream in = MobData.class.getResourceAsStream(resource)) {
            if (in == null) throw new IllegalStateException("Missing jar resource " + resource + " (python3 -I " + generator + ")");
            return JsonParser.parseReader(new InputStreamReader(in, StandardCharsets.UTF_8)).getAsJsonObject();
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    private static void loadSpecies() {
        List<Species> list = new ArrayList<>();
        Map<String, Species> map = new LinkedHashMap<>();
        for (JsonElement e : read(SPECIES_RESOURCE, "generator/mobs.py").getAsJsonArray("species")) {
            Species sp = parse(e.getAsJsonObject());
            list.add(sp);
            map.put(sp.id(), sp);
        }
        species = List.copyOf(list);
        byId = Map.copyOf(map);
    }

    private static Species parse(JsonObject o) {
        List<String> biomes = new ArrayList<>();
        o.getAsJsonArray("biomes").forEach(b -> biomes.add(b.getAsString()));
        Map<String, String> params = new LinkedHashMap<>();
        o.getAsJsonObject("params").entrySet().forEach(en -> params.put(en.getKey(), en.getValue().getAsString()));
        List<Species.Drop> drops = new ArrayList<>();
        for (JsonElement d : o.getAsJsonArray("drops")) {
            JsonObject j = d.getAsJsonObject();
            drops.add(new Species.Drop(j.get("item").getAsString(), j.get("chance").getAsDouble(), j.get("min").getAsInt(), j.get("max").getAsInt(), j.get("looting").getAsBoolean()));
        }
        return new Species(o.get("id").getAsString(), o.get("family").getAsString(), Archetype.byName(o.get("archetype").getAsString()), o.get("rig").getAsString(),
                o.get("tier").getAsInt(), o.get("cls").getAsString(), o.get("boss").getAsBoolean(),
                o.get("hp").getAsDouble(), o.get("dmg").getAsDouble(), o.get("armor").getAsDouble(), o.get("speed").getAsDouble(), o.get("follow").getAsDouble(),
                o.get("kb_res").getAsDouble(), o.get("scale").getAsDouble(), o.get("width").getAsFloat(), o.get("height").getAsFloat(),
                rgb(o.get("color").getAsString()), rgb(o.get("accent").getAsString()), List.copyOf(biomes), o.get("min_y").getAsInt(), o.get("max_y").getAsInt(),
                o.get("weight").getAsInt(), o.get("group_min").getAsInt(), o.get("group_max").getAsInt(), Map.copyOf(params), o.get("xp").getAsInt(),
                List.copyOf(drops), o.get("en").getAsString(), o.get("tr").getAsString());
    }

    private static int rgb(String hex) {
        return Integer.parseInt(hex.substring(1), 16);
    }

    private static void loadBalance() {
        JsonObject root = read(BALANCE_RESOURCE, "tools/balance/mobs.py");
        JsonObject z = root.getAsJsonObject("zone");
        Zone zone = new Zone(z.get("dist_step").getAsInt(), z.get("depth_from").getAsInt(), z.get("depth_step").getAsInt(), z.get("height_from").getAsInt(),
                z.get("height_step").getAsInt(), z.get("nether").getAsInt(), z.get("end").getAsInt(), z.get("cap").getAsInt(), z.get("band").getAsInt());
        JsonObject xp = root.getAsJsonObject("xp");
        Map<String, Integer> base = new LinkedHashMap<>();
        xp.getAsJsonObject("base").entrySet().forEach(en -> base.put(en.getKey(), en.getValue().getAsInt()));
        JsonObject heart = root.getAsJsonObject("heart");
        Map<String, Integer> weapons = new LinkedHashMap<>();
        root.getAsJsonObject("weapons").entrySet().forEach(en -> weapons.put(en.getKey(), en.getValue().getAsInt()));
        balance = new Balance(zone, xp.get("growth").getAsDouble(), Map.copyOf(base), heart.get("hp").getAsInt(), heart.get("max").getAsInt(), Map.copyOf(weapons));
    }
}
