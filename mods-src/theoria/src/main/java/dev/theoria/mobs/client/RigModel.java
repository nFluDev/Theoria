package dev.theoria.mobs.client;

import dev.theoria.mobs.client.RigData.Part;
import dev.theoria.mobs.client.RigData.Rig;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.client.model.EntityModel;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.util.Mth;

/**
 * One model class for every species: the parts and their roles come from the rig ({@link RigData}); the animation is generic per role
 * (look, leg/arm swing, spider legs, wing flap, tail sway, spin, serpent wave, jaw). A dormant sentinel keeps its rest pose.
 */
public final class RigModel extends EntityModel<SpeciesRenderState> {
    private record Animated(ModelPart part, Part def) {}

    private final List<Animated> animated = new ArrayList<>();

    public RigModel(ModelPart root, Rig rig) {
        super(root);
        for (Part def : rig.parts()) {
            if (def.role().isEmpty()) continue;
            animated.add(new Animated(find(root, rig, def), def));
        }
    }

    /** Walks down from the root along the part's parent chain. */
    private static ModelPart find(ModelPart root, Rig rig, Part def) {
        List<String> path = new ArrayList<>();
        Part cursor = def;
        while (cursor != null) {
            path.add(0, cursor.name());
            String parent = cursor.parent();
            cursor = parent == null ? null : rig.parts().stream().filter(p -> p.name().equals(parent)).findFirst().orElseThrow();
        }
        ModelPart part = root;
        for (String name : path) part = part.getChild(name);
        return part;
    }

    @Override
    public void setupAnim(SpeciesRenderState state) {
        super.setupAnim(state);
        if (state.dormant) return;
        float age = state.ageInTicks, pos = state.walkAnimationPos * 0.6662F, speed = state.walkAnimationSpeed;
        for (Animated a : animated) {
            ModelPart p = a.part();
            Part d = a.def();
            switch (d.role()) {
                case "head" -> {
                    p.yRot += Mth.clamp(state.yRot, -50.0F, 50.0F) * Mth.DEG_TO_RAD;
                    p.xRot += Mth.clamp(state.xRot, -40.0F, 40.0F) * Mth.DEG_TO_RAD;
                }
                case "leg" -> p.xRot += Mth.cos(pos + (d.param("phase", 0) == 1 ? Mth.PI : 0.0F)) * 1.4F * speed;
                case "arm" -> {
                    float swing = Mth.cos(pos + (d.param("phase", 0) == 1 ? Mth.PI : 0.0F)) * 1.0F * speed;
                    if (state.aggressive || state.telegraph) {
                        float raise = state.telegraph ? 2.3F + Mth.sin(age * 0.4F) * 0.15F : 1.4F + Mth.sin(age * 0.2F) * 0.08F;
                        p.xRot += -raise + swing * 0.2F;
                        p.zRot += (d.param("phase", 0) == 1 ? 0.1F : -0.1F) * (1.0F + Mth.sin(age * 0.3F));
                    } else {
                        p.xRot += swing;
                    }
                }
                case "spider" -> spiderLeg(p, d, pos, speed);
                case "wing" -> {
                    float flap = Mth.sin(age * (state.telegraph ? 0.9F : 0.55F)) * 0.75F + 0.2F;
                    p.zRot += -(float) d.param("side", 1) * flap;
                }
                case "tail" -> p.yRot += Mth.sin(age * 0.15F + (float) d.param("idx", 0)) * (0.2F + speed * 0.5F);
                case "spin" -> p.yRot += age * (float) d.param("rate", 0.1) + (float) d.param("phase", 0);
                case "seg" -> p.yRot += Mth.sin(age * 0.35F - (float) d.param("idx", 1) * 0.8F) * 0.3F * (0.35F + speed * 1.5F);
                case "jaw" -> p.xRot += state.telegraph ? 0.7F : (state.aggressive ? 0.25F + Mth.sin(age * 0.5F) * 0.2F : 0.0F);
                default -> {
                }
            }
        }
    }

    /** Vanilla spider walk cycle (SpiderModel): legs 0..3 front to back with different phases, mirrored per side. */
    private static void spiderLeg(ModelPart p, Part d, float pos, float speed) {
        int i = (int) d.param("i", 0);
        float side = (float) d.param("side", 1);
        float phase = switch (i) { case 0 -> 0.0F; case 1 -> Mth.PI; case 2 -> Mth.PI / 2; default -> Mth.PI * 3.0F / 2.0F; };
        float swing = -(Mth.cos(pos * 2.0F + phase) * 0.4F) * speed;
        float lift = Math.abs(Mth.sin(pos + phase) * 0.4F) * speed;
        p.yRot += swing * -side;
        p.zRot += lift * -side;
    }
}
