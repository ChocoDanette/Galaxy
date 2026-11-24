package io.github.chocodanette.galaxy.ship;

import net.minecraft.network.chat.Component;
import net.minecraft.util.TriState;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.BucketItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.event.entity.player.PlayerInteractEvent;
import net.neoforged.neoforge.event.level.BlockEvent;
import net.neoforged.neoforge.event.level.ExplosionEvent;
import net.neoforged.neoforge.event.level.block.BreakBlockEvent;

/**
 * Nobody builds or breaks on the station: it's everyone's shared home, and it stays as it was
 * built. Chests, the crafting table and the furnace still work. Players in creative mode can
 * still edit it, for building and fixing the station itself.
 */
public final class StationRules {
    private StationRules() {
    }

    static void register() {
        NeoForge.EVENT_BUS.addListener(StationRules::onBreak);
        NeoForge.EVENT_BUS.addListener(StationRules::onBreakSpeed);
        NeoForge.EVENT_BUS.addListener(StationRules::onPlace);
        NeoForge.EVENT_BUS.addListener(StationRules::onUseOnBlock);
        NeoForge.EVENT_BUS.addListener(StationRules::onUseItem);
        NeoForge.EVENT_BUS.addListener(StationRules::onExplosion);
    }

    /** True when this player may not change blocks here. */
    static boolean restricted(Entity entity, Level level) {
        return StationLocation.isStation(level) && !(entity instanceof Player player && player.isCreative());
    }

    private static void refuse(Player player) {
        player.sendOverlayMessage(Component.translatable("galaxy_ship.no_building"));
    }

    private static void onBreak(BreakBlockEvent event) {
        if (event.getLevel() instanceof Level level && restricted(event.getPlayer(), level)) {
            event.setCanceled(true);
            refuse(event.getPlayer());
        }
    }

    /** No cracks appear either: mining just doesn't start. */
    private static void onBreakSpeed(PlayerEvent.BreakSpeed event) {
        if (restricted(event.getEntity(), event.getEntity().level())) {
            event.setNewSpeed(0.0F);
        }
    }

    private static void onPlace(BlockEvent.EntityPlaceEvent event) {
        Entity entity = event.getEntity();
        if (event.getLevel() instanceof Level level && StationLocation.isStation(level)
                && (entity == null || restricted(entity, level))) {
            event.setCanceled(true);
        }
    }

    /** Blocks and buckets can't be used on the station's blocks (chests still open). */
    private static void onUseOnBlock(PlayerInteractEvent.RightClickBlock event) {
        if (restricted(event.getEntity(), event.getLevel()) && changesBlocks(event.getItemStack())) {
            event.setUseItem(TriState.FALSE);
            refuse(event.getEntity());
        }
    }

    private static void onUseItem(PlayerInteractEvent.RightClickItem event) {
        if (restricted(event.getEntity(), event.getLevel()) && changesBlocks(event.getItemStack())) {
            event.setCanceled(true);
        }
    }

    private static boolean changesBlocks(ItemStack stack) {
        return stack.getItem() instanceof BlockItem || stack.getItem() instanceof BucketItem;
    }

    private static void onExplosion(ExplosionEvent.Detonate event) {
        if (StationLocation.isStation(event.getLevel())) {
            event.getAffectedBlocks().clear();
        }
    }
}
