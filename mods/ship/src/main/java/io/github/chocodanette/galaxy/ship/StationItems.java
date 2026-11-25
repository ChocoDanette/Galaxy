package io.github.chocodanette.galaxy.ship;

import net.minecraft.world.item.CreativeModeTabs;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.Rarity;
import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.material.MapColor;
import net.minecraft.world.level.material.PushReaction;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.event.BuildCreativeModeTabContentsEvent;
import net.neoforged.neoforge.registries.DeferredBlock;
import net.neoforged.neoforge.registries.DeferredItem;
import net.neoforged.neoforge.registries.DeferredRegister;

/** The station's blocks and items: the trader and the space rock it sells. */
public final class StationItems {
    private static final DeferredRegister.Blocks BLOCKS = DeferredRegister.createBlocks(GalaxyShip.MODID);
    private static final DeferredRegister.Items ITEMS = DeferredRegister.createItems(GalaxyShip.MODID);

    /** The trading block on the common station (a trader character can take its place later). */
    public static final DeferredBlock<TraderBlock> TRADER = BLOCKS.registerBlock("trader",
            TraderBlock::new, props -> props.mapColor(MapColor.EMERALD).strength(-1.0F, 3_600_000.0F)
                    .noLootTable().lightLevel(state -> 7).sound(SoundType.WOOD).pushReaction(PushReaction.IMMOVEABLE));

    /** A chunk of floating rock: use it to found your crew's own station. */
    public static final DeferredItem<SpaceRockItem> SPACE_ROCK = ITEMS.registerItem("space_rock",
            SpaceRockItem::new, props -> props.stacksTo(1).rarity(Rarity.UNCOMMON));

    static {
        ITEMS.registerSimpleBlockItem(TRADER);
    }

    private StationItems() {
    }

    static void register(IEventBus modEventBus) {
        BLOCKS.register(modEventBus);
        ITEMS.register(modEventBus);
        modEventBus.addListener(StationItems::onCreativeTab);
    }

    private static void onCreativeTab(BuildCreativeModeTabContentsEvent event) {
        if (event.getTabKey() == CreativeModeTabs.FUNCTIONAL_BLOCKS) {
            event.accept(TRADER.get());
        }
        if (event.getTabKey() == CreativeModeTabs.TOOLS_AND_UTILITIES) {
            event.accept(SPACE_ROCK.get());
        }
    }

    /** True when this is a space rock. */
    static boolean isSpaceRock(Item item) {
        return item == SPACE_ROCK.get();
    }
}
