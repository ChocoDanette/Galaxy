package io.github.chocodanette.galaxy.ship;

import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.level.Level;

/**
 * Bought from the trader on the common station. Use it (right-click) to found your crew's own
 * station: it becomes the rock your station is built on, and you're taken there.
 */
public class SpaceRockItem extends Item {
    public SpaceRockItem(Properties properties) {
        super(properties);
    }

    @Override
    public InteractionResult use(Level level, Player player, InteractionHand hand) {
        if (player instanceof ServerPlayer serverPlayer && StationPlots.found(serverPlayer)) {
            if (!player.isCreative()) {
                player.getItemInHand(hand).shrink(1);
            }
            return InteractionResult.SUCCESS;
        }
        return level.isClientSide() ? InteractionResult.SUCCESS : InteractionResult.FAIL;
    }
}
