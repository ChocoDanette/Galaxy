package io.github.chocodanette.galaxy.worldgen;

import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.CreativeModeTabs;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.RotatedPillarBlock;
import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.material.MapColor;
import net.minecraft.world.level.material.PushReaction;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.event.BuildCreativeModeTabContentsEvent;
import net.neoforged.neoforge.registries.DeferredBlock;
import net.neoforged.neoforge.registries.DeferredItem;
import net.neoforged.neoforge.registries.DeferredRegister;

/**
 * Blocks made for planets, where there is no single "up".
 * Turf looks the same on every side, and the plants can grow out of any side of a block.
 * They reuse vanilla textures, so no new art is needed yet.
 */
public final class PlanetBlocks {
    private static final DeferredRegister.Blocks BLOCKS = DeferredRegister.createBlocks(GalaxyWorldgen.MODID);
    private static final DeferredRegister.Items ITEMS = DeferredRegister.createItems(GalaxyWorldgen.MODID);

    /** Grass on every side, so a planet can be green all around. */
    public static final DeferredBlock<Block> PLANET_TURF = BLOCKS.registerSimpleBlock("planet_turf",
            props -> props.mapColor(MapColor.GRASS).strength(0.6F).sound(SoundType.GRASS));
    /** Mycelium on every side, for fungal planets. */
    public static final DeferredBlock<Block> FUNGAL_TURF = BLOCKS.registerSimpleBlock("fungal_turf",
            props -> props.mapColor(MapColor.COLOR_PURPLE).strength(0.6F).sound(SoundType.GRASS));

    /**
     * Marks where two gravity zones of a planet meet (see the Gravity mod's zones): down
     * changes when you cross it. Glows a little, so you can follow it underground too.
     */
    public static final DeferredBlock<Block> SEAM_STONE = BLOCKS.registerSimpleBlock("seam_stone",
            props -> props.mapColor(MapColor.COLOR_CYAN).strength(1.5F, 6.0F).requiresCorrectToolForDrops()
                    .lightLevel(state -> 6).sound(SoundType.CALCITE));

    public static final DeferredBlock<PlanetPlantBlock> PLANET_GRASS = BLOCKS.registerBlock("planet_grass",
            PlanetPlantBlock::new, PlanetBlocks::plant);
    public static final DeferredBlock<PlanetPlantBlock> STARBLOOM = BLOCKS.registerBlock("starbloom",
            PlanetPlantBlock::new, PlanetBlocks::plant);

    // Launch Bloom parts (see Blooms). They can't be broken in survival, so links stay usable.
    public static final DeferredBlock<RotatedPillarBlock> BLOOM_STEM = BLOCKS.registerBlock("bloom_stem",
            RotatedPillarBlock::new, props -> bloom(props, MapColor.COLOR_GREEN, 0));
    public static final DeferredBlock<Block> BLOOM_LEAF = BLOCKS.registerSimpleBlock("bloom_leaf",
            props -> bloom(props, MapColor.PLANT, 0));
    public static final DeferredBlock<BloomPadBlock> BLOOM_PAD = BLOCKS.registerBlock("bloom_pad",
            BloomPadBlock::new, props -> bloom(props, MapColor.COLOR_LIGHT_GREEN, 10));
    public static final DeferredBlock<Block> BLOOM_PETAL = BLOCKS.registerSimpleBlock("bloom_petal",
            props -> bloom(props, MapColor.COLOR_PINK, 0));
    public static final DeferredBlock<Block> BLOOM_CORE = BLOCKS.registerSimpleBlock("bloom_core",
            props -> bloom(props, MapColor.COLOR_YELLOW, 14));
    // Homeward Bloom parts: same stem and leaves, blue flower, violet pad that throws you home.
    public static final DeferredBlock<BloomPadBlock> HOMEWARD_PAD = BLOCKS.registerBlock("homeward_pad",
            BloomPadBlock::new, props -> bloom(props, MapColor.COLOR_PURPLE, 10));
    public static final DeferredBlock<Block> HOMEWARD_PETAL = BLOCKS.registerSimpleBlock("homeward_petal",
            props -> bloom(props, MapColor.COLOR_LIGHT_BLUE, 0));
    public static final DeferredBlock<Block> HOMEWARD_CORE = BLOCKS.registerSimpleBlock("homeward_core",
            props -> bloom(props, MapColor.COLOR_CYAN, 14));

    static {
        ITEMS.registerSimpleBlockItem(BLOOM_STEM);
        ITEMS.registerSimpleBlockItem(BLOOM_LEAF);
        ITEMS.registerSimpleBlockItem(BLOOM_PAD);
        ITEMS.registerSimpleBlockItem(BLOOM_PETAL);
        ITEMS.registerSimpleBlockItem(BLOOM_CORE);
        ITEMS.registerSimpleBlockItem(HOMEWARD_PAD);
        ITEMS.registerSimpleBlockItem(HOMEWARD_PETAL);
        ITEMS.registerSimpleBlockItem(HOMEWARD_CORE);
        ITEMS.registerSimpleBlockItem(PLANET_TURF);
        ITEMS.registerSimpleBlockItem(SEAM_STONE);
        ITEMS.registerSimpleBlockItem(FUNGAL_TURF);
        ITEMS.registerSimpleBlockItem(PLANET_GRASS);
        ITEMS.registerSimpleBlockItem(STARBLOOM);
    }

    private PlanetBlocks() {
    }

    private static Block.Properties plant(Block.Properties props) {
        return props.mapColor(MapColor.PLANT).replaceable().noCollision().instabreak()
                .sound(SoundType.GRASS).pushReaction(PushReaction.POPPED);
    }

    private static Block.Properties bloom(Block.Properties props, MapColor color, int light) {
        return props.mapColor(color).strength(-1.0F, 3_600_000.0F).noLootTable()
                .lightLevel(state -> light).sound(SoundType.MOSS).pushReaction(PushReaction.IMMOVEABLE);
    }

    /** The block a Launch Bloom part is made of. */
    public static Block bloomBlock(Blooms.Part part) {
        return switch (part) {
            case STEM -> BLOOM_STEM.get();
            case LEAF -> BLOOM_LEAF.get();
            case PAD -> BLOOM_PAD.get();
            case PETAL -> BLOOM_PETAL.get();
            case CORE -> BLOOM_CORE.get();
            case HOME_PAD -> HOMEWARD_PAD.get();
            case HOME_PETAL -> HOMEWARD_PETAL.get();
            case HOME_CORE -> HOMEWARD_CORE.get();
        };
    }

    /** True for a pad you can jump from: a Launch Bloom pad or a Homeward Bloom pad. */
    public static boolean isLaunchPad(net.minecraft.world.level.block.state.BlockState state) {
        return state.is(BLOOM_PAD.get()) || state.is(HOMEWARD_PAD.get());
    }

    static void register(IEventBus modEventBus) {
        BLOCKS.register(modEventBus);
        ITEMS.register(modEventBus);
        modEventBus.addListener(PlanetBlocks::onCreativeTab);
    }

    private static void onCreativeTab(BuildCreativeModeTabContentsEvent event) {
        if (event.getTabKey() == CreativeModeTabs.NATURAL_BLOCKS) {
            event.accept(PLANET_TURF.get());
            event.accept(FUNGAL_TURF.get());
            event.accept(SEAM_STONE.get());
            event.accept(PLANET_GRASS.get());
            event.accept(STARBLOOM.get());
            event.accept(BLOOM_STEM.get());
            event.accept(BLOOM_LEAF.get());
            event.accept(BLOOM_PAD.get());
            event.accept(BLOOM_PETAL.get());
            event.accept(BLOOM_CORE.get());
            event.accept(HOMEWARD_PAD.get());
            event.accept(HOMEWARD_PETAL.get());
            event.accept(HOMEWARD_CORE.get());
        }
    }
}
