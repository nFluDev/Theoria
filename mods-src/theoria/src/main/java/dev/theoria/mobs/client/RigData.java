package dev.theoria.mobs.client;

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
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import net.minecraft.client.model.geom.PartPose;
import net.minecraft.client.model.geom.builders.CubeListBuilder;
import net.minecraft.client.model.geom.builders.LayerDefinition;
import net.minecraft.client.model.geom.builders.MeshDefinition;
import net.minecraft.client.model.geom.builders.PartDefinition;
import org.jspecify.annotations.Nullable;

/**
 * The compiled rigs ({@code theoria/mob_rigs.json}: generator/mobs.py ← data/mob_rigs.json, UVs packed). Geometry only; animation roles are read by
 * {@link RigModel}. Adding a rig = a JSON entry, no Java.
 */
public final class RigData {
    public static final String RESOURCE = "/theoria/mob_rigs.json";

    public record Box(float x, float y, float z, int w, int h, int d, int u, int v) {}

    /** {@code role} is "" for a static part; {@code params} holds the role's numbers (phase, side, idx, i, rate). */
    public record Part(String name, @Nullable String parent, float[] pivot, float[] rot, String role, Map<String, Double> params, List<Box> boxes) {
        public double param(String key, double fallback) {
            return params.getOrDefault(key, fallback);
        }
    }

    public record Rig(String name, int texW, int texH, List<Part> parts) {}

    private static Map<String, Rig> rigs;

    private RigData() {}

    public static synchronized Map<String, Rig> rigs() {
        if (rigs == null) rigs = load();
        return rigs;
    }

    public static Rig rig(String name) {
        Rig rig = rigs().get(name);
        if (rig == null) throw new IllegalStateException("Unknown mob rig '" + name + "' (data/mob_rigs.json)");
        return rig;
    }

    /** Model parts are baked from this: one cube list per part, children nested under their parent pivot. */
    public static LayerDefinition layerDefinition(Rig rig) {
        MeshDefinition mesh = new MeshDefinition();
        Map<String, PartDefinition> defs = new HashMap<>();
        for (Part part : rig.parts()) {
            PartDefinition parent = part.parent() == null ? mesh.getRoot() : defs.get(part.parent());
            CubeListBuilder cubes = CubeListBuilder.create();
            for (Box b : part.boxes()) cubes.texOffs(b.u(), b.v()).addBox(b.x(), b.y(), b.z(), b.w(), b.h(), b.d());
            float[] p = part.pivot(), r = part.rot();
            PartPose pose = r[0] == 0 && r[1] == 0 && r[2] == 0 ? PartPose.offset(p[0], p[1], p[2]) : PartPose.offsetAndRotation(p[0], p[1], p[2], r[0], r[1], r[2]);
            defs.put(part.name(), parent.addOrReplaceChild(part.name(), cubes, pose));
        }
        return LayerDefinition.create(mesh, rig.texW(), rig.texH());
    }

    private static Map<String, Rig> load() {
        try (InputStream in = RigData.class.getResourceAsStream(RESOURCE)) {
            if (in == null) throw new IllegalStateException("Missing jar resource " + RESOURCE + " (python3 -I generator/mobs.py)");
            JsonObject root = JsonParser.parseReader(new InputStreamReader(in, StandardCharsets.UTF_8)).getAsJsonObject();
            Map<String, Rig> out = new LinkedHashMap<>();
            for (var entry : root.entrySet()) {
                JsonObject rig = entry.getValue().getAsJsonObject();
                JsonArray tex = rig.getAsJsonArray("tex");
                List<Part> parts = new ArrayList<>();
                for (JsonElement pe : rig.getAsJsonArray("parts")) parts.add(part(pe.getAsJsonObject()));
                out.put(entry.getKey(), new Rig(entry.getKey(), tex.get(0).getAsInt(), tex.get(1).getAsInt(), List.copyOf(parts)));
            }
            return Map.copyOf(out);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    private static Part part(JsonObject o) {
        Map<String, Double> params = new HashMap<>();
        for (var e : o.entrySet()) {
            if (e.getValue().isJsonPrimitive() && e.getValue().getAsJsonPrimitive().isNumber()) params.put(e.getKey(), e.getValue().getAsDouble());
        }
        List<Box> boxes = new ArrayList<>();
        for (JsonElement be : o.getAsJsonArray("boxes")) {
            JsonObject b = be.getAsJsonObject();
            JsonArray a = b.getAsJsonArray("b"), uv = b.getAsJsonArray("uv");
            boxes.add(new Box(a.get(0).getAsFloat(), a.get(1).getAsFloat(), a.get(2).getAsFloat(), a.get(3).getAsInt(), a.get(4).getAsInt(), a.get(5).getAsInt(),
                    uv.get(0).getAsInt(), uv.get(1).getAsInt()));
        }
        return new Part(o.get("name").getAsString(), o.has("parent") ? o.get("parent").getAsString() : null, floats(o.getAsJsonArray("pivot")),
                o.has("rot") ? floats(o.getAsJsonArray("rot")) : new float[3], o.has("role") ? o.get("role").getAsString() : "", Map.copyOf(params), List.copyOf(boxes));
    }

    private static float[] floats(JsonArray a) {
        return new float[] {a.get(0).getAsFloat(), a.get(1).getAsFloat(), a.get(2).getAsFloat()};
    }
}
