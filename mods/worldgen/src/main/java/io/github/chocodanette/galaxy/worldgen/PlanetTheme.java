package io.github.chocodanette.galaxy.worldgen;

import java.util.function.Supplier;

import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;

/**
 * What a planet is made of, from the outside in. Blocks are looked up lazily so the
 * generation rules can be unit tested without starting Minecraft.
 *
 * Blocks that fall (sand, gravel) only go on the top of a planet: elsewhere they would
 * drop off into space as soon as something next to them changed.
 */
public enum PlanetTheme {
    VERDANT("verdant", () -> Blocks.GRASS_BLOCK, () -> Blocks.GRASS_BLOCK, () -> Blocks.DIRT,
            () -> Blocks.STONE, () -> Blocks.DEEPSLATE, () -> Blocks.DEEPSLATE,
            0.020, new int[] {6, 5, 4, 2, 2, 2, 1}),
    DUNE("dune", () -> Blocks.SAND, () -> Blocks.SANDSTONE, () -> Blocks.SANDSTONE,
            () -> Blocks.STONE, () -> Blocks.DEEPSLATE, () -> Blocks.SMOOTH_SANDSTONE,
            0.018, new int[] {3, 3, 2, 5, 2, 1, 1}),
    FROST("frost", () -> Blocks.SNOW_BLOCK, () -> Blocks.SNOW_BLOCK, () -> Blocks.PACKED_ICE,
            () -> Blocks.STONE, () -> Blocks.DEEPSLATE, () -> Blocks.BLUE_ICE,
            0.016, new int[] {3, 5, 2, 1, 1, 4, 1}),
    ROCKY("rocky", () -> Blocks.GRAVEL, () -> Blocks.ANDESITE, () -> Blocks.TUFF,
            () -> Blocks.STONE, () -> Blocks.DEEPSLATE, () -> Blocks.DEEPSLATE,
            0.028, new int[] {4, 7, 6, 2, 3, 1, 1}),
    EMBER("ember", () -> Blocks.BLACKSTONE, () -> Blocks.BLACKSTONE, () -> Blocks.BASALT,
            () -> Blocks.BLACKSTONE, () -> Blocks.DEEPSLATE, () -> Blocks.MAGMA_BLOCK,
            0.022, new int[] {7, 2, 1, 4, 5, 0, 1}),
    MOSSY("mossy", () -> Blocks.MOSS_BLOCK, () -> Blocks.MOSS_BLOCK, () -> Blocks.ROOTED_DIRT,
            () -> Blocks.TUFF, () -> Blocks.DEEPSLATE, () -> Blocks.DEEPSLATE,
            0.018, new int[] {4, 3, 7, 1, 1, 3, 1}),
    CANYON("canyon", () -> Blocks.RED_SAND, () -> Blocks.TERRACOTTA, () -> Blocks.DYED_TERRACOTTA.orange(),
            () -> Blocks.STONE, () -> Blocks.DEEPSLATE, () -> Blocks.DYED_TERRACOTTA.red(),
            0.020, new int[] {3, 3, 3, 6, 2, 1, 1}),
    FUNGAL("fungal", () -> Blocks.MYCELIUM, () -> Blocks.MYCELIUM, () -> Blocks.DIRT,
            () -> Blocks.STONE, () -> Blocks.DEEPSLATE, () -> Blocks.DEEPSLATE,
            0.018, new int[] {5, 4, 4, 2, 3, 2, 1}),
    CRYSTAL("crystal", () -> Blocks.CALCITE, () -> Blocks.CALCITE, () -> Blocks.CALCITE,
            () -> Blocks.SMOOTH_BASALT, () -> Blocks.DEEPSLATE, () -> Blocks.AMETHYST_BLOCK,
            0.026, new int[] {2, 3, 2, 3, 3, 4, 4});

    private final String name;
    private final Supplier<Block> top;
    private final Supplier<Block> side;
    private final Supplier<Block> soil;
    private final Supplier<Block> stone;
    private final Supplier<Block> deep;
    private final Supplier<Block> core;
    private final double oreChance;
    private final int[] oreWeights;

    PlanetTheme(String name, Supplier<Block> top, Supplier<Block> side, Supplier<Block> soil,
            Supplier<Block> stone, Supplier<Block> deep, Supplier<Block> core,
            double oreChance, int[] oreWeights) {
        this.name = name;
        this.top = top;
        this.side = side;
        this.soil = soil;
        this.stone = stone;
        this.deep = deep;
        this.core = core;
        this.oreChance = oreChance;
        this.oreWeights = oreWeights;
    }

    public String displayName() {
        return name;
    }

    /** Share of rock blocks that start a small ore cluster. */
    double oreChance() {
        return oreChance;
    }

    /** How common each {@link Material.Ore} is on this kind of planet, in the enum's order. */
    int oreWeight(Material.Ore ore) {
        return oreWeights[ore.ordinal()];
    }

    /** The block for one material of this theme. */
    public Block block(Material material) {
        return switch (material) {
            case TOP -> top.get();
            case SIDE -> side.get();
            case SOIL -> soil.get();
            case STONE -> stone.get();
            case DEEP -> deep.get();
            case CORE -> core.get();
            default -> material.ore().block(material.isDeepOre());
        };
    }
}
