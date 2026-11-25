package io.github.chocodanette.galaxy.gravity.mixin;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.AABB;

import io.github.chocodanette.galaxy.gravity.GravityFrames;

/**
 * Picking up items reaches 1 block around you along the ground and half a block along your
 * own "up", on every face of a planet (vanilla measures "up" along world Y).
 */
@Mixin(Player.class)
public abstract class PlayerPickupMixin {
    @Redirect(method = "aiStep", at = @At(value = "INVOKE",
            target = "Lnet/minecraft/world/phys/AABB;inflate(DDD)Lnet/minecraft/world/phys/AABB;"))
    private AABB galaxy$pickupArea(AABB box, double x, double y, double z) {
        return GravityFrames.inflateAlongFrame((Player) (Object) this, box, x, y);
    }
}
