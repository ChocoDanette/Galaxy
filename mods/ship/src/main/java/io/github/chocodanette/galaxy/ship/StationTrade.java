package io.github.chocodanette.galaxy.ship;

import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.Prediction;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

/**
 * Buying a space rock from the trader: it costs emeralds and coal (amounts in the server
 * settings, {@link StationConfig}). Everything happens on the server.
 */
public final class StationTrade {
    private StationTrade() {
    }

    static void buySpaceRock(ServerPlayer player, BlockPos trader) {
        int emeralds = StationConfig.PRICE_EMERALDS.getAsInt();
        int coal = StationConfig.PRICE_COAL.getAsInt();
        if (StationPlots.stationOf(player).isPresent()) {
            say(player, trader, false, Component.translatable("galaxy_ship.trader.already_have"));
            return;
        }
        if (hasRock(player.getInventory())) {
            say(player, trader, false, Component.translatable("galaxy_ship.trader.already_carrying"));
            return;
        }
        Inventory inventory = player.getInventory();
        int haveEmeralds = count(inventory, Items.EMERALD, null);
        int haveCoal = count(inventory, Items.COAL, Items.CHARCOAL);
        if (!player.isCreative() && (haveEmeralds < emeralds || haveCoal < coal)) {
            say(player, trader, false, Component.translatable("galaxy_ship.trader.price", emeralds, coal, haveEmeralds, haveCoal));
            return;
        }
        if (!player.isCreative()) {
            take(inventory, Items.EMERALD, null, emeralds);
            take(inventory, Items.COAL, Items.CHARCOAL, coal);
        }
        ItemStack rock = new ItemStack(StationItems.SPACE_ROCK.get());
        // Into the inventory, or dropped at their feet if it's full.
        inventory.placeItemBackInInventory(rock, Prediction.SERVER_ONLY);
        say(player, trader, true, Component.translatable("galaxy_ship.trader.sold"));
    }

    private static boolean hasRock(Inventory inventory) {
        return count(inventory, StationItems.SPACE_ROCK.get(), null) > 0;
    }

    private static void say(ServerPlayer player, BlockPos trader, boolean yes, Component message) {
        player.sendSystemMessage(message, false);
        player.level().playSound(null, trader, yes ? SoundEvents.VILLAGER_YES : SoundEvents.VILLAGER_NO,
                SoundSource.BLOCKS, 1.0F, 1.0F);
    }

    private static boolean matches(ItemStack stack, Item item, Item alsoItem) {
        return stack.is(item) || alsoItem != null && stack.is(alsoItem);
    }

    private static int count(Inventory inventory, Item item, Item alsoItem) {
        int total = 0;
        for (int i = 0; i < inventory.getContainerSize(); i++) {
            ItemStack stack = inventory.getItem(i);
            if (matches(stack, item, alsoItem)) {
                total += stack.getCount();
            }
        }
        return total;
    }

    private static void take(Inventory inventory, Item item, Item alsoItem, int amount) {
        for (int i = 0; i < inventory.getContainerSize() && amount > 0; i++) {
            ItemStack stack = inventory.getItem(i);
            if (matches(stack, item, alsoItem)) {
                int used = Math.min(amount, stack.getCount());
                stack.shrink(used);
                amount -= used;
            }
        }
        inventory.setChanged();
    }
}
