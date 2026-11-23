package io.github.chocodanette.galaxy.gravity.mixin.client;

import java.util.List;

import org.joml.Quaternionf;
import org.joml.Vector3f;
import org.jspecify.annotations.Nullable;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.particle.Particle;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.phys.shapes.CollisionContext;

import io.github.chocodanette.galaxy.gravity.ClientPlanets;
import io.github.chocodanette.galaxy.gravity.GravityMath;
import io.github.chocodanette.galaxy.gravity.client.ParticleFrame;

/**
 * Particles fall, rise and bounce relative to the planet they're near, not world down.
 *
 * When a particle is made near a planet, it gets a fixed frame whose "up" points away from
 * the planet. Its speed (xd, yd, zd) is then read in that frame, so all of vanilla's particle
 * logic (gravity pulling yd down, smoke rising, landing on the ground) just works on any side.
 */
@Mixin(Particle.class)
public abstract class ParticleMixin implements ParticleFrame {
    @Unique
    private static final double GALAXY$MAX_STEP_SQR = 100.0 * 100.0;

    @Shadow
    @Final
    protected ClientLevel level;
    @Shadow
    protected double xd;
    @Shadow
    protected double zd;
    @Shadow
    protected boolean onGround;
    @Shadow
    protected boolean hasPhysics;
    @Shadow
    private boolean stoppedByCollision;
    @Shadow
    protected double y;

    @Unique
    private @Nullable Quaternionf galaxy$frame;

    @Shadow
    public abstract AABB getBoundingBox();

    @Shadow
    public abstract void setBoundingBox(AABB bb);

    @Shadow
    protected abstract void setLocationFromBoundingbox();

    @Inject(method = "<init>(Lnet/minecraft/client/multiplayer/ClientLevel;DDD)V", at = @At("TAIL"))
    private void galaxy$pickFrame(ClientLevel level, double x, double y, double z, CallbackInfo ci) {
        if (ClientPlanets.fields().isEmpty()) {
            return;
        }
        GravityMath.pull(ClientPlanets.world(), new Vec3(x, y, z), Double.NaN).ifPresent(pull -> {
            Vec3 up = pull.down().scale(-1);
            if (up.y < 0.999) {
                galaxy$frame = new Quaternionf().rotationTo(new Vector3f(0, 1, 0),
                        new Vector3f((float) up.x, (float) up.y, (float) up.z));
            }
        });
    }

    @Override
    public @Nullable Quaternionf galaxy$frame() {
        return galaxy$frame;
    }

    @Override
    public void galaxy$moveFree(double xa, double ya, double za) {
        Vector3f world = galaxy$frame.transform(new Vector3f((float) xa, (float) ya, (float) za));
        setBoundingBox(getBoundingBox().move(world.x, world.y, world.z));
        setLocationFromBoundingbox();
    }

    /** Same as vanilla's move, but the step and the collision results are in the particle's frame. */
    @Inject(method = "move", at = @At("HEAD"), cancellable = true)
    private void galaxy$tiltedMove(double xa, double ya, double za, CallbackInfo ci) {
        Quaternionf frame = galaxy$frame;
        if (frame == null) {
            return;
        }
        ci.cancel();
        if (stoppedByCollision) {
            return;
        }
        Vector3f w = frame.transform(new Vector3f((float) xa, (float) ya, (float) za));
        Vec3 wanted = new Vec3(w.x, w.y, w.z);
        Vec3 moved = wanted;
        if (hasPhysics && wanted.lengthSqr() > 0 && wanted.lengthSqr() < GALAXY$MAX_STEP_SQR) {
            moved = Entity.collideBoundingBox(CollisionContext.positionContext(y), wanted, getBoundingBox(), level, List.of());
        }
        if (moved.lengthSqr() > 0) {
            setBoundingBox(getBoundingBox().move(moved));
            setLocationFromBoundingbox();
        }
        Vector3f local = frame.transformInverse(new Vector3f((float) moved.x, (float) moved.y, (float) moved.z));
        boolean blockedUpDown = Math.abs(ya - local.y) > 1.0E-5;
        if (Math.abs(ya) >= 1.0E-5 && Math.abs(local.y) < 1.0E-5) {
            stoppedByCollision = true;
        }
        onGround = blockedUpDown && ya < 0.0;
        if (Math.abs(xa - local.x) > 1.0E-5) {
            xd = 0.0;
        }
        if (Math.abs(za - local.z) > 1.0E-5) {
            zd = 0.0;
        }
    }
}
