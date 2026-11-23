package io.github.chocodanette.galaxy.worldgen;

import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;

/**
 * One layer of a planet. Each {@link PlanetTheme} turns these into real blocks.
 * Ores come in a stone version and a deepslate version for the deeper layers.
 */
public enum Material {
    TOP, SIDE, SOIL, STONE, DEEP, CORE,
    /** A light block, like the little sun in the middle of a hollow planet. */
    GLOW,
    COAL(Ore.COAL, false), IRON(Ore.IRON, false), COPPER(Ore.COPPER, false), GOLD(Ore.GOLD, false),
    REDSTONE(Ore.REDSTONE, false), LAPIS(Ore.LAPIS, false), DIAMOND(Ore.DIAMOND, false),
    DEEP_COAL(Ore.COAL, true), DEEP_IRON(Ore.IRON, true), DEEP_COPPER(Ore.COPPER, true), DEEP_GOLD(Ore.GOLD, true),
    DEEP_REDSTONE(Ore.REDSTONE, true), DEEP_LAPIS(Ore.LAPIS, true), DEEP_DIAMOND(Ore.DIAMOND, true);

    private final Ore ore;
    private final boolean deepOre;

    Material() {
        this(null, false);
    }

    Material(Ore ore, boolean deepOre) {
        this.ore = ore;
        this.deepOre = deepOre;
    }

    /** The ore in this block, or null for plain rock and soil. */
    public Ore ore() {
        return ore;
    }

    public boolean isDeepOre() {
        return deepOre;
    }

    static Material ore(Ore ore, boolean deep) {
        return switch (ore) {
            case COAL -> deep ? DEEP_COAL : COAL;
            case IRON -> deep ? DEEP_IRON : IRON;
            case COPPER -> deep ? DEEP_COPPER : COPPER;
            case GOLD -> deep ? DEEP_GOLD : GOLD;
            case REDSTONE -> deep ? DEEP_REDSTONE : REDSTONE;
            case LAPIS -> deep ? DEEP_LAPIS : LAPIS;
            case DIAMOND -> deep ? DEEP_DIAMOND : DIAMOND;
        };
    }

    /** Ores a planet can hold. Some only appear deep inside, so you have to dig for them. */
    public enum Ore {
        COAL(0.0), IRON(0.0), COPPER(0.0), GOLD(0.35), REDSTONE(0.35), LAPIS(0.35), DIAMOND(0.65);

        /** How deep (0 = surface, 1 = center) a block must be before this ore can appear. */
        final double minDepth;

        Ore(double minDepth) {
            this.minDepth = minDepth;
        }

        Block block(boolean deep) {
            return switch (this) {
                case COAL -> deep ? Blocks.DEEPSLATE_COAL_ORE : Blocks.COAL_ORE;
                case IRON -> deep ? Blocks.DEEPSLATE_IRON_ORE : Blocks.IRON_ORE;
                case COPPER -> deep ? Blocks.DEEPSLATE_COPPER_ORE : Blocks.COPPER_ORE;
                case GOLD -> deep ? Blocks.DEEPSLATE_GOLD_ORE : Blocks.GOLD_ORE;
                case REDSTONE -> deep ? Blocks.DEEPSLATE_REDSTONE_ORE : Blocks.REDSTONE_ORE;
                case LAPIS -> deep ? Blocks.DEEPSLATE_LAPIS_ORE : Blocks.LAPIS_ORE;
                case DIAMOND -> deep ? Blocks.DEEPSLATE_DIAMOND_ORE : Blocks.DIAMOND_ORE;
            };
        }
    }
}
