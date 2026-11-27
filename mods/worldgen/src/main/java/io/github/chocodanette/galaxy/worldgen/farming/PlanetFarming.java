package io.github.chocodanette.galaxy.worldgen.farming;

import java.util.Map;
import java.util.function.Supplier;

import org.jspecify.annotations.Nullable;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.tags.ItemTags;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.gameevent.GameEvent;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.entity.player.PlayerInteractEvent;

import io.github.chocodanette.galaxy.worldgen.PlanetBlocks;

/**
 * Farming on every side of a planet. Vanilla only tills the top of a block and only plants
 * seeds on farmland below them, so on a planet's side or underside nothing works. Here:
 * <ul>
 * <li>a hoe used on any open side of grass, dirt or planet turf tills that side, making
 * {@link PlanetFarmlandBlock} facing out of it (or vanilla farmland on a top side);</li>
 * <li>wheat seeds, carrots, potatoes and beetroot seeds used on that farmland's tilled
 * side plant a {@link PlanetCropBlock} growing out of it.</li>
 * </ul>
 * The server decides; the player's game only swings the arm, so it works the same in
 * multiplayer.
 */
public final class PlanetFarming {
    private PlanetFarming() {
    }

    public static void register() {
        NeoForge.EVENT_BUS.addListener(PlanetFarming::onUseBlock);
    }

    /** The planet crop each vanilla seed plants. */
    private static Map<Item, Supplier<? extends PlanetCropBlock>> crops() {
        return Map.of(
                Items.WHEAT_SEEDS, PlanetBlocks.PLANET_WHEAT,
                Items.CARROT, PlanetBlocks.PLANET_CARROTS,
                Items.POTATO, PlanetBlocks.PLANET_POTATOES,
                Items.BEETROOT_SEEDS, PlanetBlocks.PLANET_BEETROOTS);
    }

    /** True for blocks a hoe turns into farmland on any side. */
    static boolean isTillable(BlockState state) {
        return state.is(Blocks.GRASS_BLOCK) || state.is(Blocks.DIRT) || state.is(Blocks.COARSE_DIRT)
                || state.is(PlanetBlocks.PLANET_GRASS_BLOCK.get()) || state.is(PlanetBlocks.PLANET_TURF.get());
    }

    /**
     * What a hoe makes of this block when used on {@code side}, or null when vanilla should
     * handle it (the top side of vanilla grass and dirt) or nothing happens.
     */
    static @Nullable BlockState tilled(BlockState state, Direction side) {
        if (!isTillable(state)) {
            return null;
        }
        boolean ours = state.is(PlanetBlocks.PLANET_GRASS_BLOCK.get()) || state.is(PlanetBlocks.PLANET_TURF.get());
        if (side == Direction.UP && !ours) {
            return null;
        }
        if (state.is(Blocks.COARSE_DIRT)) {
            // Like vanilla, coarse dirt first becomes plain dirt.
            return Blocks.DIRT.defaultBlockState();
        }
        return PlanetFarmlandBlock.facing(side);
    }

    private static void onUseBlock(PlayerInteractEvent.RightClickBlock event) {
        Level level = event.getLevel();
        BlockPos pos = event.getPos();
        Direction side = event.getFace();
        ItemStack stack = event.getItemStack();
        Player player = event.getEntity();
        if (side == null || stack.isEmpty()) {
            return;
        }
        BlockState state = level.getBlockState(pos);
        if (stack.is(ItemTags.HOES)) {
            BlockState result = tilled(state, side);
            if (result == null || !level.getBlockState(pos.relative(side)).isAir()) {
                return;
            }
            if (!level.isClientSide()) {
                level.playSound(null, pos, SoundEvents.HOE_TILL.value(), SoundSource.BLOCKS, 1.0F, 1.0F);
                level.setBlock(pos, result, Block.UPDATE_ALL_IMMEDIATE);
                level.gameEvent(GameEvent.BLOCK_CHANGE, pos, GameEvent.Context.of(player, result));
                stack.hurtAndBreak(1, player, event.getHand());
            }
            event.setCanceled(true);
            event.setCancellationResult(InteractionResult.SUCCESS);
            return;
        }
        Supplier<? extends PlanetCropBlock> crop = crops().get(stack.getItem());
        if (crop == null || !(state.getBlock() instanceof PlanetFarmlandBlock)
                || state.getValue(PlanetFarmlandBlock.FACING) != side) {
            return;
        }
        BlockPos spot = pos.relative(side);
        BlockState planted = crop.get().planted(side);
        if (!level.getBlockState(spot).canBeReplaced() || !planted.canSurvive(level, spot)) {
            return;
        }
        if (!level.isClientSide()) {
            level.setBlock(spot, planted, Block.UPDATE_ALL_IMMEDIATE);
            SoundType sound = planted.getSoundType(level, spot, player);
            level.playSound(null, spot, sound.getPlaceSound(), SoundSource.BLOCKS,
                    (sound.getVolume() + 1.0F) / 2.0F, sound.getPitch() * 0.8F);
            level.gameEvent(GameEvent.BLOCK_PLACE, spot, GameEvent.Context.of(player, planted));
            stack.consume(1, player);
        }
        event.setCanceled(true);
        event.setCancellationResult(InteractionResult.SUCCESS);
    }
}
