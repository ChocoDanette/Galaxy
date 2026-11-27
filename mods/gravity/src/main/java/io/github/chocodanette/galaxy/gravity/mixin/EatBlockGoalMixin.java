package io.github.chocodanette.galaxy.gravity.mixin;

import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.Identifier;
import net.minecraft.tags.TagKey;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.ai.goal.EatBlockGoal;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;

import io.github.chocodanette.galaxy.gravity.GalaxyGravity;
import io.github.chocodanette.galaxy.gravity.GravityFrame;
import io.github.chocodanette.galaxy.gravity.GravityFrames;

/**
 * Sheep grazing on every face of a planet. Vanilla looks for grass at world "below" the
 * sheep's block, and only plain grass blocks count. Here "below" follows the sheep's own
 * feet, and any block in the {@code galaxy_gravity:grazable} tag counts (planet grass that
 * faces sideways, planet turf). It still turns into dirt once eaten, like vanilla.
 */
@Mixin(EatBlockGoal.class)
public abstract class EatBlockGoalMixin {
    private static final TagKey<Block> GRAZABLE =
            TagKey.create(Registries.BLOCK, Identifier.fromNamespaceAndPath(GalaxyGravity.MODID, "grazable"));

    @Shadow
    @Final
    private Mob mob;

    @Redirect(method = {"canUse", "tick"}, at = @At(value = "INVOKE",
            target = "Lnet/minecraft/world/entity/Mob;blockPosition()Lnet/minecraft/core/BlockPos;"))
    private BlockPos galaxy$feet(Mob mob) {
        return GravityFrames.feetBlock(mob);
    }

    @Redirect(method = {"canUse", "tick"}, at = @At(value = "INVOKE",
            target = "Lnet/minecraft/core/BlockPos;below()Lnet/minecraft/core/BlockPos;"))
    private BlockPos galaxy$below(BlockPos pos) {
        GravityFrame frame = GravityFrames.tilted(mob);
        Direction down = frame == null ? Direction.DOWN : GravityFrames.boxAxis(frame).getOpposite();
        return pos.relative(down);
    }

    @Redirect(method = {"canUse", "tick"}, at = @At(value = "INVOKE",
            target = "Lnet/minecraft/world/level/block/state/BlockState;is(Ljava/lang/Object;)Z"))
    private boolean galaxy$isGrass(BlockState state, Object grass) {
        return state.is((Block) grass) || state.is(GRAZABLE);
    }
}
