package dev.theoria.mobs.client;

import net.minecraft.client.renderer.entity.state.LivingEntityRenderState;

/** What the rig model needs beyond a living entity's walk cycle and look angles. */
public class SpeciesRenderState extends LivingEntityRenderState {
    public boolean aggressive;
    public boolean telegraph;
    public boolean dormant;
}
