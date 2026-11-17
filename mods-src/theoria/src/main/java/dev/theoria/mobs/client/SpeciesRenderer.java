package dev.theoria.mobs.client;

import dev.theoria.Theoria;
import dev.theoria.mobs.Species;
import dev.theoria.mobs.TheoriaMob;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.entity.MobRenderer;
import net.minecraft.resources.Identifier;

/** The one renderer of every species: its own texture ({@code textures/entity/<id>.png}), the rig's model, scale from the scale attribute. */
public final class SpeciesRenderer extends MobRenderer<TheoriaMob, SpeciesRenderState, RigModel> {
    private final Identifier texture;

    public SpeciesRenderer(EntityRendererProvider.Context context, Species species) {
        super(context, new RigModel(context.bakeLayer(MobClient.layer(species.rig())), RigData.rig(species.rig())), species.width() * 0.6F);
        this.texture = Theoria.id("textures/entity/" + species.id() + ".png");
    }

    @Override
    public SpeciesRenderState createRenderState() {
        return new SpeciesRenderState();
    }

    @Override
    public void extractRenderState(TheoriaMob entity, SpeciesRenderState state, float partialTicks) {
        super.extractRenderState(entity, state, partialTicks);
        state.aggressive = entity.isAggressive();
        state.telegraph = entity.isTelegraphing();
        state.dormant = entity.isDormant();
    }

    @Override
    public Identifier getTextureLocation(SpeciesRenderState state) {
        return texture;
    }
}
