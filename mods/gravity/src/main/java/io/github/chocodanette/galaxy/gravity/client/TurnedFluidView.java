package io.github.chocodanette.galaxy.gravity.client;

import net.minecraft.client.renderer.block.BlockAndTintGetter;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.level.CardinalLighting;
import net.minecraft.world.level.ColorResolver;
import net.minecraft.world.level.LightLayer;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.lighting.LevelLightEngine;
import net.minecraft.world.level.material.FluidState;

import org.jspecify.annotations.Nullable;

import io.github.chocodanette.galaxy.gravity.PlanetFluids;

/**
 * The world around one liquid block, turned so that the block's planet "down" looks like world
 * down. Vanilla's liquid drawing code reads its neighbors through this and draws an upright
 * liquid; {@code FluidRendererMixin} then turns the drawn corners back into the world.
 */
public final class TurnedFluidView implements BlockAndTintGetter {
    private final BlockAndTintGetter level;
    private final BlockPos origin;
    private final Direction down;
    /** Middle of the block, in the section-relative space the corners are drawn in. */
    private final float cx;
    private final float cy;
    private final float cz;
    private final float[] normal;

    public TurnedFluidView(BlockAndTintGetter level, BlockPos origin, Direction down) {
        this.level = level;
        this.origin = origin.immutable();
        this.down = down;
        this.cx = (origin.getX() & 15) + 0.5F;
        this.cy = (origin.getY() & 15) + 0.5F;
        this.cz = (origin.getZ() & 15) + 0.5F;
        this.normal = PlanetFluids.turn(down, 0.0F, 1.0F, 0.0F);
    }

    /** A drawn corner of the upright liquid, turned around the block's middle into the world. */
    public float[] turnCorner(float x, float y, float z) {
        float[] p = PlanetFluids.turn(down, x - cx, y - cy, z - cz);
        p[0] += cx;
        p[1] += cy;
        p[2] += cz;
        return p;
    }

    /** The liquid's "up", for the drawn faces' normal. */
    public float[] normal() {
        return normal;
    }

    private BlockPos real(BlockPos pos) {
        return PlanetFluids.turnAround(down, origin, pos);
    }

    @Override
    public BlockState getBlockState(BlockPos pos) {
        return level.getBlockState(real(pos));
    }

    @Override
    public FluidState getFluidState(BlockPos pos) {
        return level.getFluidState(real(pos));
    }

    @Override
    public @Nullable BlockEntity getBlockEntity(BlockPos pos) {
        return level.getBlockEntity(real(pos));
    }

    @Override
    public int getBlockTint(BlockPos pos, ColorResolver color) {
        return level.getBlockTint(real(pos), color);
    }

    @Override
    public CardinalLighting cardinalLighting() {
        return level.cardinalLighting();
    }

    @Override
    public LevelLightEngine getLightEngine() {
        return level.getLightEngine();
    }

    @Override
    public int getBrightness(LightLayer layer, BlockPos pos) {
        return level.getBrightness(layer, real(pos));
    }

    @Override
    public int getRawBrightness(BlockPos pos, int darkening) {
        return level.getRawBrightness(real(pos), darkening);
    }

    @Override
    public int getHeight() {
        return level.getHeight();
    }

    @Override
    public int getMinY() {
        return level.getMinY();
    }
}
