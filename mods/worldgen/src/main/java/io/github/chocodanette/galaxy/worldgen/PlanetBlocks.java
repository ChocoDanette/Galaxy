package io.github.chocodanette.galaxy.worldgen;

import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.CreativeModeTabs;
import net.minecraft.world.level.block.Block;
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

    public static final DeferredBlock<PlanetPlantBlock> PLANET_GRASS = BLOCKS.registerBlock("planet_grass",
            PlanetPlantBlock::new, PlanetBlocks::plant);
    public static final DeferredBlock<PlanetPlantBlock> STARBLOOM = BLOCKS.registerBlock("starbloom",
            PlanetPlantBlock::new, PlanetBlocks::plant);

    static {
        ITEMS.registerSimpleBlockItem(PLANET_TURF);
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

    static void register(IEventBus modEventBus) {
        BLOCKS.register(modEventBus);
        ITEMS.register(modEventBus);
        modEventBus.addListener(PlanetBlocks::onCreativeTab);
    }

    private static void onCreativeTab(BuildCreativeModeTabContentsEvent event) {
        if (event.getTabKey() == CreativeModeTabs.NATURAL_BLOCKS) {
            event.accept(PLANET_TURF.get());
            event.accept(FUNGAL_TURF.get());
            event.accept(PLANET_GRASS.get());
            event.accept(STARBLOOM.get());
        }
    }
}
