package io.github.chocodanette.galaxy.gravity.mixin;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;

import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.common.NeoForgeMod;
import net.neoforged.neoforge.fluids.FluidType;

import io.github.chocodanette.galaxy.gravity.GravityFrame;
import io.github.chocodanette.galaxy.gravity.GravityFrames;

/**
 * Swimming up (jump) and down (sneak) in water or lava go along the planet's up, not world up.
 * These replace NeoForge's default versions, which are the same with world up.
 */
@Mixin(LivingEntity.class)
public abstract class SwimMixin {
    /** How hard one tick of swimming pushes, as in vanilla. */
    @Unique
    private static final double GALAXY$SWIM_PUSH = 0.04F;

    public void jumpInFluid(FluidType type) {
        galaxy$swim(type, 1.0);
    }

    public void sinkInFluid(FluidType type) {
        galaxy$swim(type, -1.0);
    }

    @Unique
    private void galaxy$swim(FluidType type, double sign) {
        LivingEntity self = (LivingEntity) (Object) this;
        double multiplier = type == NeoForgeMod.WATER_TYPE.value()
                ? self.getAttributeValue(NeoForgeMod.SWIM_SPEED)
                : 1.0;
        GravityFrame frame = GravityFrames.tilted(self);
        Vec3 up = frame != null ? frame.up() : new Vec3(0.0, 1.0, 0.0);
        self.setDeltaMovement(self.getDeltaMovement().add(up.scale(sign * GALAXY$SWIM_PUSH * multiplier)));
    }
}
