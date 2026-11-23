package io.github.chocodanette.galaxy.ship;

import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;
import net.neoforged.neoforge.network.PacketDistributor;

/**
 * The ship's console. Right-click it to open the flight screen (land on a planet, jump to
 * the next galaxy). Right-click it while holding coal to fill the fuel tank.
 *
 * The screen only shows what the server sends and asks the server to act; the server
 * checks everything again, so two players at the console can't cheat or clash.
 */
public final class ConsoleBlock extends Block {
    public ConsoleBlock(Properties properties) {
        super(properties);
    }

    /** How much fuel one of this item adds, or 0 when it isn't fuel. */
    public static int fuelValue(ItemStack stack) {
        if (stack.is(Items.COAL) || stack.is(Items.CHARCOAL)) {
            return 1;
        }
        if (stack.is(Items.COAL_BLOCK)) {
            return 9;
        }
        return 0;
    }

    @Override
    protected InteractionResult useItemOn(ItemStack stack, BlockState state, Level level, BlockPos pos,
            Player player, InteractionHand hand, BlockHitResult hitResult) {
        int value = fuelValue(stack);
        if (value == 0) {
            return InteractionResult.TRY_WITH_EMPTY_HAND;
        }
        if (level.isClientSide() || !(player instanceof ServerPlayer serverPlayer)) {
            return InteractionResult.SUCCESS;
        }
        ShipData data = ShipData.get(serverPlayer.level().getServer());
        int room = ShipData.MAX_FUEL - data.fuel();
        int used = Math.min(stack.getCount(), room / value);
        if (used <= 0) {
            serverPlayer.sendSystemMessage(Component.translatable("galaxy_ship.fuel.full"), true);
            return InteractionResult.SUCCESS;
        }
        data.setFuel(data.fuel() + used * value);
        if (!player.getAbilities().instabuild) {
            stack.shrink(used);
        }
        level.playSound(null, pos, SoundEvents.FIRECHARGE_USE, SoundSource.BLOCKS, 0.6F, 1.4F);
        serverPlayer.sendSystemMessage(Component.translatable("galaxy_ship.fuel.added",
                used * value, data.fuel(), ShipData.MAX_FUEL), true);
        ShipNetwork.sendStateToShip(serverPlayer.level().getServer(), false);
        return InteractionResult.SUCCESS;
    }

    @Override
    protected InteractionResult useWithoutItem(BlockState state, Level level, BlockPos pos, Player player,
            BlockHitResult hitResult) {
        if (!level.isClientSide() && player instanceof ServerPlayer serverPlayer) {
            PacketDistributor.sendToPlayer(serverPlayer,
                    ShipData.get(serverPlayer.level().getServer()).state(true, pos));
        }
        return InteractionResult.SUCCESS;
    }
}
