package io.github.chocodanette.galaxy.gravity;

import net.minecraft.core.registries.Registries;
import net.minecraft.resources.Identifier;
import net.minecraft.tags.TagKey;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.dimension.DimensionType;

/**
 * Dimensions in space (listed in the dimension type tag {@code galaxy_gravity:space}): no sun or
 * moon in the sky, and every block face is lit the same, since there is no up or down there.
 */
public final class Space {
    public static final TagKey<DimensionType> SPACE = TagKey.create(Registries.DIMENSION_TYPE,
            Identifier.fromNamespaceAndPath(GalaxyGravity.MODID, "space"));

    private Space() {
    }

    public static boolean isSpace(Level level) {
        return level != null && level.dimensionTypeRegistration().is(SPACE);
    }
}
