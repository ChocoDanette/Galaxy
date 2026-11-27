package io.github.chocodanette.galaxy.worldgen.farming;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;

/**
 * Block offsets measured on a face instead of in world axes. Vanilla farming looks at
 * "the 3x3 blocks around me" and "the block above me"; on a planet's side those words
 * mean the two axes along the face and the face's own up.
 */
public final class FaceGrid {
    private FaceGrid() {
    }

    /** The first axis lying along the face. */
    public static Direction first(Direction up) {
        return up.getAxis() == Direction.Axis.X ? Direction.SOUTH : Direction.EAST;
    }

    /** The second axis lying along the face. */
    public static Direction second(Direction up) {
        return up.getAxis() == Direction.Axis.Y ? Direction.SOUTH : Direction.UP;
    }

    /** {@code a} and {@code b} steps along the face, then {@code height} steps along its up. */
    public static BlockPos offset(BlockPos pos, Direction up, int a, int b, int height) {
        return pos.relative(first(up), a).relative(second(up), b).relative(up, height);
    }
}
