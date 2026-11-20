package dev.theoria.wings.client;

import dev.theoria.Theoria;
import dev.theoria.wings.WingsLayout;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.renderer.entity.EntityRenderer;
import net.minecraft.client.renderer.entity.state.EntityRenderState;
import net.minecraft.client.renderer.entity.state.LivingEntityRenderState;
import net.minecraft.world.entity.LivingEntity;
import org.joml.Quaternionf;
import org.joml.Vector3f;

/**
 * Draws the local player in a screen rectangle at an angle WE choose (ADR-002 b). Vanilla's {@code renderEntityInInventoryFollowsAngle}
 * cannot be used: it turns the body 180° + 20·x and the head 20·x, so the head looks backwards once the body has turned. We build the
 * same render state ourselves — body at the turntable angle, head straight on the body — and hand it to the vanilla
 * {@code GuiGraphicsExtractor#entity}. The state comes from {@code createRenderState}, which also runs other mods' render-state
 * extensions (Fantastic Wings' wings, later cosmetics), so the preview shows what the world shows.
 */
public final class WingsPreview {
    /** Counted for the unattended scenario: previews that really reached the GUI renderer. */
    public static long draws;
    private static boolean failureLogged;

    private WingsPreview() {}

    /** {@code yaw} 0° = back to the camera. Returns false (and draws nothing) if the render state could not be built. */
    public static boolean draw(GuiGraphicsExtractor g, LivingEntity entity, WingsLayout.Rect box, float yaw, float pitch) {
        try {
            EntityRenderer<? super LivingEntity, ?> renderer = Minecraft.getInstance().getEntityRenderDispatcher().getRenderer(entity);
            EntityRenderState state = renderer.createRenderState(entity, 1.0F);
            state.shadowPieces.clear();
            state.outlineColor = 0;
            if (state instanceof LivingEntityRenderState living) {
                living.bodyRot = yaw;
                living.yRot = 0f; // head relative to the body (HumanoidModel: head.yRot = state.yRot)
                living.xRot = 0f;
                living.boundingBoxWidth = living.boundingBoxWidth / living.scale;
                living.boundingBoxHeight = living.boundingBoxHeight / living.scale;
                living.scale = 1.0F;
            }
            float tilt = (float) Math.toRadians(pitch);
            Quaternionf camera = new Quaternionf().rotateX(tilt);
            Quaternionf rotation = new Quaternionf().rotateZ((float) Math.PI); // GUI space is y-down
            rotation.mul(camera);
            Vector3f translation = new Vector3f(0.0F, state.boundingBoxHeight / 2.0F + 0.0625F, 0.0F);
            float size = Math.min(box.h() * 0.40F, box.w() * 0.55F);
            g.entity(state, size, translation, rotation, camera, box.x(), box.y(), box.right(), box.bottom());
            draws++;
            return true;
        } catch (RuntimeException e) {
            if (!failureLogged) {
                failureLogged = true;
                Theoria.LOGGER.error("Wings preview could not be drawn (this message is logged once)", e);
            }
            return false;
        }
    }
}
