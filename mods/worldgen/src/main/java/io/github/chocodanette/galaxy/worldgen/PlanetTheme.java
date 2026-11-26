package io.github.chocodanette.galaxy.worldgen;

import java.util.List;
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
    VERDANT("verdant", () -> Blocks.GRASS_BLOCK, () -> PlanetBlocks.PLANET_TURF.get(), () -> Blocks.DIRT,
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
            () -> Blocks.NETHERRACK, () -> Blocks.BLACKSTONE, () -> Blocks.MAGMA_BLOCK,
            0.032, new int[] {4, 1, 0, 2, 4, 0, 1, 8, 4, 3, 6}),
    MOSSY("mossy", () -> Blocks.MOSS_BLOCK, () -> Blocks.MOSS_BLOCK, () -> Blocks.ROOTED_DIRT,
            () -> Blocks.TUFF, () -> Blocks.DEEPSLATE, () -> Blocks.DEEPSLATE,
            0.018, new int[] {4, 3, 7, 1, 1, 3, 1}),
    CANYON("canyon", () -> Blocks.RED_SAND, () -> Blocks.TERRACOTTA, () -> Blocks.DYED_TERRACOTTA.orange(),
            () -> Blocks.STONE, () -> Blocks.DEEPSLATE, () -> Blocks.DYED_TERRACOTTA.red(),
            0.020, new int[] {3, 3, 3, 6, 2, 1, 1}),
    FUNGAL("fungal", () -> Blocks.MYCELIUM, () -> PlanetBlocks.FUNGAL_TURF.get(), () -> Blocks.DIRT,
            () -> Blocks.STONE, () -> Blocks.DEEPSLATE, () -> Blocks.DEEPSLATE,
            0.018, new int[] {5, 4, 4, 2, 3, 2, 1}),
    CRYSTAL("crystal", () -> Blocks.CALCITE, () -> Blocks.CALCITE, () -> Blocks.CALCITE,
            () -> Blocks.SMOOTH_BASALT, () -> Blocks.DEEPSLATE, () -> Blocks.AMETHYST_BLOCK,
            0.026, new int[] {2, 3, 2, 3, 3, 4, 4}),
    /**
     * Rare far-out planets that replace the End: end stone, chorus groves on top and an
     * obsidian heart. Never a galaxy favorite; {@link GalaxyLayout} places them on its own.
     */
    VOID("void", () -> Blocks.END_STONE, () -> Blocks.END_STONE, () -> Blocks.END_STONE,
            () -> Blocks.END_STONE, () -> Blocks.END_STONE_BRICKS, () -> Blocks.OBSIDIAN,
            0.010, new int[] {0, 0, 0, 0, 0, 2, 3}),
    /**
     * Water planets: a shallow ocean over every face, grassy islands where the hills rise out
     * of it, and a sandy seabed with clay. A prismarine heart deep inside.
     */
    OCEAN("ocean", () -> Blocks.GRASS_BLOCK, () -> PlanetBlocks.PLANET_TURF.get(), () -> Blocks.DIRT,
            () -> Blocks.STONE, () -> Blocks.DEEPSLATE, () -> Blocks.PRISMARINE,
            0.018, new int[] {5, 4, 6, 2, 2, 5, 1},
            () -> Blocks.WATER, () -> Blocks.SAND, () -> Blocks.CLAY),
    /**
     * Lava planets: a sea of lava with basalt islands, magma and obsidian under it, and the
     * Nether's ores in the rock, more of them than on Ember planets.
     */
    LAVA("lava", () -> Blocks.SMOOTH_BASALT, () -> Blocks.SMOOTH_BASALT, () -> Blocks.BASALT,
            () -> Blocks.NETHERRACK, () -> Blocks.BLACKSTONE, () -> Blocks.MAGMA_BLOCK,
            0.030, new int[] {2, 3, 1, 4, 2, 0, 2, 6, 6, 3, 3},
            () -> Blocks.LAVA, () -> Blocks.MAGMA_BLOCK, () -> Blocks.OBSIDIAN);

    private final String name;
    private final Supplier<Block> top;
    private final Supplier<Block> side;
    private final Supplier<Block> soil;
    private final Supplier<Block> stone;
    private final Supplier<Block> deep;
    private final Supplier<Block> core;
    private final double oreChance;
    private final int[] oreWeights;
    private final Supplier<Block> liquid;
    private final Supplier<Block> seabed;
    private final Supplier<Block> seabedPatch;

    PlanetTheme(String name, Supplier<Block> top, Supplier<Block> side, Supplier<Block> soil,
            Supplier<Block> stone, Supplier<Block> deep, Supplier<Block> core,
            double oreChance, int[] oreWeights) {
        this(name, top, side, soil, stone, deep, core, oreChance, oreWeights, null, null, null);
    }

    PlanetTheme(String name, Supplier<Block> top, Supplier<Block> side, Supplier<Block> soil,
            Supplier<Block> stone, Supplier<Block> deep, Supplier<Block> core,
            double oreChance, int[] oreWeights,
            Supplier<Block> liquid, Supplier<Block> seabed, Supplier<Block> seabedPatch) {
        this.liquid = liquid;
        this.seabed = seabed;
        this.seabedPatch = seabedPatch;
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

    /** The usual kinds of planets, picked by galaxies; rare kinds (Void) are placed apart. */
    static final List<PlanetTheme> COMMON = java.util.Arrays.stream(values()).filter(t -> !t.isRare()).toList();

    /** Rare kinds never show up as a galaxy favorite or a normal pick. */
    boolean isRare() {
        return this == VOID;
    }

    /** Whether planet grass and starblooms grow on this kind of planet. */
    boolean hasPlants() {
        return this == VERDANT || this == MOSSY || this == OCEAN;
    }

    /** Whether this kind of planet is covered by a sea (water or lava). */
    public boolean hasSea() {
        return liquid != null;
    }

    /** The sea's liquid block (water or lava); only for themes with a sea. */
    public Block liquid() {
        return liquid.get();
    }

    /** Whether the top block falls (sand, gravel), so caves and inner ground must avoid it. */
    boolean topFalls() {
        return this == DUNE || this == ROCKY || this == CANYON;
    }

    /** The kinds of trees that grow here (none on bare planets). */
    List<Trees.Style> treeStyles() {
        return switch (this) {
            case VERDANT -> List.of(Trees.Style.OAK, Trees.Style.OAK, Trees.Style.BIRCH);
            case MOSSY -> List.of(Trees.Style.AZALEA);
            case FROST -> List.of(Trees.Style.SPRUCE);
            case FUNGAL -> List.of(Trees.Style.RED_MUSHROOM, Trees.Style.BROWN_MUSHROOM);
            case EMBER -> List.of(Trees.Style.CRIMSON);
            case OCEAN -> List.of(Trees.Style.OAK, Trees.Style.BIRCH);
            default -> List.of();
        };
    }

    /** How many tries at a tree per block of surface; many tries miss, so it's a rough guide. */
    double treeDensity() {
        return switch (this) {
            case VERDANT -> 0.014;
            case MOSSY -> 0.012;
            case FROST -> 0.008;
            case FUNGAL -> 0.006;
            case EMBER -> 0.005;
            case OCEAN -> 0.010;
            default -> 0.0;
        };
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
        // Ores added later count as 0 on themes that don't list them.
        return ore.ordinal() < oreWeights.length ? oreWeights[ore.ordinal()] : 0;
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
            case GLOW -> Blocks.SHROOMLIGHT;
            case SEABED -> seabed != null ? seabed.get() : top.get();
            case SEABED_PATCH -> seabedPatch != null ? seabedPatch.get() : top.get();
            default -> material.ore().block(material.isDeepOre());
        };
    }
}
