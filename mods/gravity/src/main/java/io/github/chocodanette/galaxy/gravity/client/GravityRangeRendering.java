package io.github.chocodanette.galaxy.gravity.client;

import java.util.List;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;

import net.minecraft.client.renderer.rendertype.RenderTypes;
import net.minecraft.util.Mth;
import net.minecraft.world.phys.Vec3;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.SubmitCustomGeometryEvent;

import io.github.chocodanette.galaxy.gravity.ClientPlanets;
import io.github.chocodanette.galaxy.gravity.GalaxyGravity;
import io.github.chocodanette.galaxy.gravity.GravityField;

/**
 * Draws a see-through light blue bubble around each planet, showing how far its gravity reaches.
 *
 * Every player draws the bubbles from their own copy of the planets, so everyone sees the
 * same thing. The server decides whether they are shown (/galaxy planet ranges show|hide).
 */
@EventBusSubscriber(modid = GalaxyGravity.MODID, value = Dist.CLIENT)
public final class GravityRangeRendering {
    /** Light blue, mostly transparent (alpha 0x26 of 0xFF). */
    private static final int COLOR = 0x2680D0FF;
    /** Bubbles further than this from the camera (beyond their own size) are skipped. */
    private static final double DRAW_DISTANCE = 256.0;
    /** Rings from pole to pole, and slices around; more is rounder but slower. */
    private static final int RINGS = 24;
    private static final int SLICES = 48;

    private static final float[] SIN_RING = new float[RINGS + 1];
    private static final float[] COS_RING = new float[RINGS + 1];
    private static final float[] SIN_SLICE = new float[SLICES + 1];
    private static final float[] COS_SLICE = new float[SLICES + 1];

    static {
        for (int i = 0; i <= RINGS; i++) {
            float angle = (float) Math.PI * i / RINGS;
            SIN_RING[i] = Mth.sin(angle);
            COS_RING[i] = Mth.cos(angle);
        }
        for (int j = 0; j <= SLICES; j++) {
            float angle = (float) (Math.PI * 2) * j / SLICES;
            SIN_SLICE[j] = Mth.sin(angle);
            COS_SLICE[j] = Mth.cos(angle);
        }
    }

    private GravityRangeRendering() {
    }

    @SubscribeEvent
    static void onSubmitGeometry(SubmitCustomGeometryEvent event) {
        if (!ClientPlanets.showRanges()) {
            return;
        }
        List<GravityField> fields = ClientPlanets.fields();
        if (fields.isEmpty()) {
            return;
        }
        Vec3 camera = event.getLevelRenderState().cameraRenderState.pos;
        PoseStack poseStack = event.getPoseStack();
        for (GravityField field : fields) {
            double range = field.range();
            Vec3 offset = field.center().subtract(camera);
            if (offset.length() - range > DRAW_DISTANCE) {
                continue;
            }
            poseStack.pushPose();
            poseStack.translate(offset.x, offset.y, offset.z);
            float r = (float) range;
            event.getSubmitNodeCollector().submitCustomGeometry(poseStack, RenderTypes.debugQuads(),
                    (pose, buffer) -> drawSphere(pose, buffer, r));
            poseStack.popPose();
        }
    }

    private static void drawSphere(PoseStack.Pose pose, VertexConsumer buffer, float r) {
        for (int i = 0; i < RINGS; i++) {
            for (int j = 0; j < SLICES; j++) {
                vertex(pose, buffer, r, i, j);
                vertex(pose, buffer, r, i + 1, j);
                vertex(pose, buffer, r, i + 1, j + 1);
                vertex(pose, buffer, r, i, j + 1);
            }
        }
    }

    private static void vertex(PoseStack.Pose pose, VertexConsumer buffer, float r, int ring, int slice) {
        float x = r * SIN_RING[ring] * COS_SLICE[slice];
        float y = r * COS_RING[ring];
        float z = r * SIN_RING[ring] * SIN_SLICE[slice];
        buffer.addVertex(pose, x, y, z).setColor(COLOR);
    }
}
