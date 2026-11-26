package io.github.chocodanette.galaxy.gravity.mixin.client;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import com.mojang.blaze3d.vertex.VertexConsumer;

import net.minecraft.client.renderer.block.BlockAndTintGetter;
import net.minecraft.client.renderer.block.FluidRenderer;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.material.FluidState;

import io.github.chocodanette.galaxy.gravity.GravityWorld;
import io.github.chocodanette.galaxy.gravity.PlanetFluids;
import io.github.chocodanette.galaxy.gravity.client.TurnedFluidView;

/**
 * Draws water and lava with their surface facing the planet's sky. For a liquid that falls
 * some other way than world down, vanilla draws it upright from a turned view of its
 * neighbors ({@link TurnedFluidView}), and every corner it outputs is turned back.
 *
 * Sections are built on several threads at once, so the turn in progress is per thread.
 */
@Mixin(FluidRenderer.class)
public abstract class FluidRendererMixin {
    @Unique
    private static final ThreadLocal<TurnedFluidView> GALAXY$TURN = new ThreadLocal<>();

    @Shadow
    public abstract void tesselate(BlockAndTintGetter level, BlockPos pos, FluidRenderer.Output output,
            BlockState blockState, FluidState fluidState);

    @Inject(method = "tesselate", at = @At("HEAD"), cancellable = true)
    private void galaxy$turnLiquid(BlockAndTintGetter level, BlockPos pos, FluidRenderer.Output output,
            BlockState blockState, FluidState fluidState, CallbackInfo ci) {
        if (GALAXY$TURN.get() != null) {
            return;
        }
        GravityWorld world = PlanetFluids.clientWorld();
        if (world == null) {
            return;
        }
        Direction down = PlanetFluids.downAt(world, pos);
        if (down == Direction.DOWN) {
            return;
        }
        ci.cancel();
        TurnedFluidView view = new TurnedFluidView(level, pos, down);
        GALAXY$TURN.set(view);
        try {
            tesselate(view, pos, output, blockState, fluidState);
        } finally {
            GALAXY$TURN.remove();
        }
    }

    @Inject(method = "vertex", at = @At("HEAD"), cancellable = true)
    private void galaxy$turnVertex(VertexConsumer builder, float x, float y, float z, int color, float u, float v,
            int lightCoords, CallbackInfo ci) {
        TurnedFluidView view = GALAXY$TURN.get();
        if (view == null) {
            return;
        }
        ci.cancel();
        float[] p = view.turnCorner(x, y, z);
        float[] n = view.normal();
        builder.addVertex(p[0], p[1], p[2], color, u, v, OverlayTexture.NO_OVERLAY, lightCoords, n[0], n[1], n[2]);
    }
}
