package io.github.chocodanette.galaxy.gravity.client;

import com.mojang.blaze3d.vertex.PoseStack;

import net.minecraft.client.renderer.entity.LivingEntityRenderer;
import net.minecraft.resources.Identifier;
import net.minecraft.util.context.ContextKey;
import net.minecraft.world.entity.LivingEntity;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.EventPriority;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.RenderLivingEvent;
import net.neoforged.neoforge.client.renderstate.RegisterRenderStateModifiersEvent;

import io.github.chocodanette.galaxy.gravity.GalaxyGravity;
import io.github.chocodanette.galaxy.gravity.GravityFrame;
import io.github.chocodanette.galaxy.gravity.GravityFrames;

/** Draws tilted players and mobs turned around their feet, so they stand on a planet's side. */
@EventBusSubscriber(modid = GalaxyGravity.MODID, value = Dist.CLIENT)
public final class TiltedEntityRendering {
    private static final ContextKey<GravityFrame> FRAME =
            new ContextKey<>(Identifier.fromNamespaceAndPath(GalaxyGravity.MODID, "frame"));

    private TiltedEntityRendering() {
    }

    @SubscribeEvent
    @SuppressWarnings({"unchecked", "rawtypes"})
    static void onRegisterModifiers(RegisterRenderStateModifiersEvent event) {
        event.registerEntityModifier((Class) LivingEntityRenderer.class,
                (entity, state) -> ((net.minecraft.client.renderer.entity.state.LivingEntityRenderState) state)
                        .setRenderData(FRAME, GravityFrames.tilted((LivingEntity) entity)));
    }

    @SubscribeEvent(priority = EventPriority.LOWEST)
    static void onRenderPre(RenderLivingEvent.Pre<?, ?, ?> event) {
        GravityFrame frame = event.getRenderState().getRenderData(FRAME);
        PoseStack pose = event.getPoseStack();
        pose.pushPose();
        if (frame != null) {
            pose.mulPose(new org.joml.Matrix4f().rotation(frame.rotation(event.getPartialTick())));
        }
    }

    @SubscribeEvent
    static void onRenderPost(RenderLivingEvent.Post<?, ?, ?> event) {
        event.getPoseStack().popPose();
    }
}
