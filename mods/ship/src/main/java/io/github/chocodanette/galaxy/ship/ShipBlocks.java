package io.github.chocodanette.galaxy.ship;

import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.CreativeModeTabs;
import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.material.MapColor;
import net.minecraft.world.level.material.PushReaction;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.event.BuildCreativeModeTabContentsEvent;
import net.neoforged.neoforge.registries.DeferredBlock;
import net.neoforged.neoforge.registries.DeferredItem;
import net.neoforged.neoforge.registries.DeferredRegister;

/**
 * The ship's own blocks. Both can't be broken in survival, so the crew can't lose the
 * only way to fly or the only way home.
 */
public final class ShipBlocks {
    private static final DeferredRegister.Blocks BLOCKS = DeferredRegister.createBlocks(GalaxyShip.MODID);
    private static final DeferredRegister.Items ITEMS = DeferredRegister.createItems(GalaxyShip.MODID);

    public static final DeferredBlock<ConsoleBlock> CONSOLE = BLOCKS.registerBlock("console", ConsoleBlock::new,
            props -> props.mapColor(MapColor.COLOR_LIGHT_BLUE)
                    .strength(-1.0F, 3_600_000.0F)
                    .noLootTable()
                    .lightLevel(state -> 7)
                    .sound(SoundType.METAL)
                    .pushReaction(PushReaction.IMMOVEABLE));

    public static final DeferredBlock<ReturnPadBlock> RETURN_PAD = BLOCKS.registerBlock("return_pad", ReturnPadBlock::new,
            props -> props.mapColor(MapColor.COLOR_CYAN)
                    .strength(-1.0F, 3_600_000.0F)
                    .noLootTable()
                    .noOcclusion()
                    .lightLevel(state -> 10)
                    .sound(SoundType.METAL)
                    .pushReaction(PushReaction.IMMOVEABLE));

    public static final DeferredItem<BlockItem> CONSOLE_ITEM = ITEMS.registerSimpleBlockItem(CONSOLE);
    public static final DeferredItem<BlockItem> RETURN_PAD_ITEM = ITEMS.registerSimpleBlockItem(RETURN_PAD);

    private ShipBlocks() {
    }

    static void register(IEventBus modEventBus) {
        BLOCKS.register(modEventBus);
        ITEMS.register(modEventBus);
        modEventBus.addListener(ShipBlocks::onCreativeTab);
    }

    private static void onCreativeTab(BuildCreativeModeTabContentsEvent event) {
        if (event.getTabKey() == CreativeModeTabs.FUNCTIONAL_BLOCKS) {
            event.accept(CONSOLE_ITEM.get());
            event.accept(RETURN_PAD_ITEM.get());
        }
    }
}
