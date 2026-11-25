package io.github.chocodanette.galaxy.gravity;

import java.util.Set;

import org.jspecify.annotations.Nullable;

import net.minecraft.core.BlockPos;
import net.minecraft.world.level.pathfinder.Path;

/** Added to every mob navigation: finds paths along a planet's side (see PathNavigationMixin). */
public interface PlanetNavigator {
    /** A path toward the targets along the mob's planet face, or null. */
    @Nullable Path galaxy$planetPath(Set<BlockPos> targets, int reachRange);
}
