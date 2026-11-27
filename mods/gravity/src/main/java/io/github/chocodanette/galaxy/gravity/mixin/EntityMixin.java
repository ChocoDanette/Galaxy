package io.github.chocodanette.galaxy.gravity.mixin;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import java.util.List;

import net.minecraft.core.Direction;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityDimensions;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.MoverType;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

import org.jspecify.annotations.Nullable;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.item.FallingBlockEntity;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.level.block.state.BlockState;

import io.github.chocodanette.galaxy.gravity.GravityFrame;
import io.github.chocodanette.galaxy.gravity.GravityMath;
import io.github.chocodanette.galaxy.gravity.GravityFrames;

/**
 * Makes an entity's movement, aim, eyes, hitbox and "on ground" follow its gravity frame.
 * Everything here is skipped for upright entities, so vanilla behavior is unchanged off-planet.
 */
@Mixin(Entity.class)
public abstract class EntityMixin {
    @Shadow
    private EntityDimensions dimensions;

    @Shadow
    public double xo;
    @Shadow
    public double yo;
    @Shadow
    public double zo;

    /** On a planet you can walk up one full block without jumping, on any side. */
    @Unique
    private static final double GALAXY$PLANET_STEP = 1.0;

    @Unique
    private Vec3 galaxy$moveStart = Vec3.ZERO;

    @Unique
    private Vec3 galaxy$collided = Vec3.ZERO;

    /** Planet pull applied to a non-living entity this tick (items, arrows, falling blocks), or null. */
    @Unique
    private GravityMath.@Nullable Pull galaxy$itemPull;

    @Shadow
    protected static Vec3 getInputVector(Vec3 input, float speed, float yRot) {
        throw new AssertionError();
    }

    @Shadow
    public abstract float getYRot();

    @Shadow
    public abstract Vec3 getDeltaMovement();

    @Shadow
    public abstract void setDeltaMovement(Vec3 movement);

    @Shadow
    public abstract Vec3 position();

    @Shadow
    public abstract float getEyeHeight();

    @Shadow
    public abstract void setOnGround(boolean onGround);

    @Unique
    private Entity galaxy$self() {
        return (Entity) (Object) this;
    }

    /** Walking input (WASD) is relative to the entity's own frame. */
    @Inject(method = "moveRelative", at = @At("HEAD"), cancellable = true)
    private void galaxy$moveRelative(float speed, Vec3 input, CallbackInfo ci) {
        GravityFrame frame = GravityFrames.tilted(galaxy$self());
        if (frame == null) {
            return;
        }
        Vec3 local = getInputVector(input, speed, getYRot());
        setDeltaMovement(getDeltaMovement().add(frame.toWorld(local)));
        ci.cancel();
    }

    /** Aiming (block breaking, attacking, using items) follows the tilted view. */
    @Inject(method = "getViewVector", at = @At("RETURN"), cancellable = true)
    private void galaxy$viewVector(float partialTick, CallbackInfoReturnable<Vec3> cir) {
        GravityFrame frame = GravityFrames.tilted(galaxy$self());
        if (frame != null) {
            org.joml.Vector3f v = frame.rotation(partialTick).transform(new org.joml.Vector3f(
                    (float) cir.getReturnValue().x, (float) cir.getReturnValue().y, (float) cir.getReturnValue().z));
            cir.setReturnValue(new Vec3(v.x, v.y, v.z));
        }
    }

    /** Eyes sit along the local "up" from the feet. */
    @Inject(method = "getEyePosition()Lnet/minecraft/world/phys/Vec3;", at = @At("RETURN"), cancellable = true)
    private void galaxy$eyePosition(CallbackInfoReturnable<Vec3> cir) {
        GravityFrame frame = GravityFrames.tilted(galaxy$self());
        if (frame != null) {
            cir.setReturnValue(GravityFrames.safeEyes(galaxy$self(), position(), frame.up(),
                    GravityFrames.boxAxis(frame), getEyeHeight()));
        }
    }

    @Inject(method = "getEyePosition(F)Lnet/minecraft/world/phys/Vec3;", at = @At("RETURN"), cancellable = true)
    private void galaxy$eyePositionPartial(float partialTick, CallbackInfoReturnable<Vec3> cir) {
        GravityFrame frame = GravityFrames.tilted(galaxy$self());
        if (frame != null) {
            Vec3 feet = new Vec3(
                    Mth.lerp(partialTick, xo, position().x),
                    Mth.lerp(partialTick, yo, position().y),
                    Mth.lerp(partialTick, zo, position().z));
            cir.setReturnValue(GravityFrames.safeEyes(galaxy$self(), feet, frame.up(partialTick),
                    GravityFrames.boxAxis(frame), getEyeHeight()));
        }
    }

    /**
     * Vanilla checks for suffocation with a thin flat slab at the eyes, flat in world x and z.
     * On a planet's side that slab stands up along your "up" and can poke out above your head
     * into a low ceiling (crouching under a block on a wall hurt you). Lay it flat across the
     * hitbox's own axis instead.
     */
    @Inject(method = "isInWall", at = @At("HEAD"), cancellable = true)
    private void galaxy$inWallAlongFrame(CallbackInfoReturnable<Boolean> cir) {
        Entity self = galaxy$self();
        GravityFrame frame = GravityFrames.tilted(self);
        if (frame == null || dimensions == null || self.noPhysics) {
            return;
        }
        Direction.Axis axis = GravityFrames.boxAxis(frame).getAxis();
        double w = dimensions.width() * 0.8;
        AABB eyes = AABB.ofSize(self.getEyePosition(), axis == Direction.Axis.X ? 1.0E-6 : w,
                axis == Direction.Axis.Y ? 1.0E-6 : w, axis == Direction.Axis.Z ? 1.0E-6 : w);
        Level level = self.level();
        cir.setReturnValue(BlockPos.betweenClosedStream(eyes).anyMatch(pos -> {
            BlockState state = level.getBlockState(pos);
            return !state.isAir() && state.isSuffocating(level, pos)
                    && net.minecraft.world.phys.shapes.Shapes.joinIsNotEmpty(
                            state.getCollisionShape(level, pos).move(pos),
                            net.minecraft.world.phys.shapes.Shapes.create(eyes),
                            net.minecraft.world.phys.shapes.BooleanOp.AND);
        }));
    }

    /**
     * Hitboxes must stay axis-aligned, so the box is stood up along the world axis closest
     * to the entity's "up": a player on a planet's east side gets a box lying along +X.
     */
    @Inject(method = "makeBoundingBox(Lnet/minecraft/world/phys/Vec3;)Lnet/minecraft/world/phys/AABB;",
            at = @At("RETURN"), cancellable = true)
    private void galaxy$boundingBox(Vec3 pos, CallbackInfoReturnable<AABB> cir) {
        GravityFrame frame = GravityFrames.tilted(galaxy$self());
        if (frame == null || dimensions == null) {
            return;
        }
        Direction axis = GravityFrames.boxAxis(frame);
        if (axis != Direction.UP) {
            cir.setReturnValue(GravityFrames.orientedBox(axis, dimensions.width(), dimensions.height(), pos));
        }
    }

    /**
     * Step-up along the hitbox's own "up". Vanilla only steps along world up, which on a
     * planet's side or underside would mean sliding along the surface instead of climbing.
     */
    @Inject(method = "collide", at = @At("RETURN"), cancellable = true)
    private void galaxy$stepUpOnSide(Vec3 movement, CallbackInfoReturnable<Vec3> cir) {
        Entity self = galaxy$self();
        Vec3 result = cir.getReturnValue();
        galaxy$collided = result;
        GravityFrame frame = GravityFrames.tilted(self);
        if (frame == null || !(self instanceof LivingEntity living)) {
            return;
        }
        Direction axis = GravityFrames.boxAxis(frame);
        if (axis == Direction.UP) {
            return;
        }
        double step = Math.max(living.getAttributeValue(Attributes.STEP_HEIGHT), GALAXY$PLANET_STEP);
        Vec3 a = axis.getUnitVec3();
        double wantUp = movement.dot(a);
        double gotUp = result.dot(a);
        Vec3 wantFlat = movement.subtract(a.scale(wantUp));
        Vec3 gotFlat = result.subtract(a.scale(gotUp));
        if (wantFlat.lengthSqr() < 1.0E-7 || wantFlat.subtract(gotFlat).lengthSqr() < 1.0E-10) {
            return;
        }
        boolean landed = wantUp < 0 && gotUp > wantUp;
        if (!landed && !self.onGround()) {
            return;
        }
        Level level = self.level();
        Vec3 settle = a.scale(landed ? gotUp : 0.0);
        AABB start = self.getBoundingBox().move(settle);
        Vec3 up = Entity.collideBoundingBox(self, a.scale(step), start, level, List.of());
        AABB raised = start.move(up);
        Vec3 flat = Entity.collideBoundingBox(self, wantFlat, raised, level, List.of());
        if (flat.lengthSqr() <= gotFlat.lengthSqr() + 1.0E-7) {
            return;
        }
        Vec3 down = Entity.collideBoundingBox(self, a.scale(-up.dot(a)), raised.move(flat), level, List.of());
        Vec3 stepped = settle.add(up).add(flat).add(down);
        galaxy$collided = stepped;
        cir.setReturnValue(stepped);
    }

    /**
     * Items, arrows, thrown things and falling blocks fall toward the planet too. Players and
     * mobs are handled by GravityApplier instead. Both sides run this with the same synced
     * planets, so the client's guess matches the server.
     */
    @Inject(method = "applyGravity", at = @At("HEAD"), cancellable = true)
    private void galaxy$planetGravity(CallbackInfo ci) {
        Entity self = galaxy$self();
        galaxy$itemPull = null;
        if (self instanceof LivingEntity) {
            return;
        }
        double gravity = self.getGravity();
        if (gravity == 0.0) {
            return;
        }
        var world = GravityFrames.worldFor(self);
        if (world.fields().isEmpty()) {
            return;
        }
        var pull = GravityMath.pull(world, self.position(), Double.NaN);
        if (pull.isEmpty()) {
            return;
        }
        GravityMath.Pull itemPull = pull.get();
        if (self.onGround() && itemPull.inField()) {
            // Resting on a block (or always, in step gravity), pull straight into a block face. Near a cube's
            // edge the true pull is slanted, and its sideways part would slide the item off
            // the block and down the side (players get the same fix in GravityApplier).
            Vec3 d = itemPull.down();
            Vec3 face = Direction.getApproximateNearest(d.x, d.y, d.z).getUnitVec3();
            itemPull = new GravityMath.Pull(face, itemPull.scale(), itemPull.field(), true);
        }
        galaxy$itemPull = itemPull;
        setDeltaMovement(getDeltaMovement().add(itemPull.down().scale(gravity * itemPull.scale())));
        ci.cancel();
    }

    /**
     * A non-living thing pulled by a planet is "on the ground" when blocked along its pull, so
     * items rest and slow down on any side. A falling block that lands on a planet's side is
     * placed right there (vanilla only knows how to land on top of a block).
     */
    @Unique
    private void galaxy$landItem(Entity self, Vec3 delta) {
        GravityMath.Pull pull = galaxy$itemPull;
        if (pull == null || pull.down().y < -0.99) {
            return;
        }
        Vec3 down = pull.down();
        double wanted = delta.dot(down);
        double moved = position().subtract(galaxy$moveStart).dot(down);
        boolean blocked = wanted > 1.0E-5 && moved < wanted - 1.0E-5;
        setOnGround(blocked);
        if (!blocked) {
            return;
        }
        double into = getDeltaMovement().dot(down);
        if (into > 0) {
            setDeltaMovement(getDeltaMovement().subtract(down.scale(into)));
        }
        if (self instanceof ItemEntity) {
            // Vanilla ground friction only slows world x and z, so on a planet's side an item
            // would keep sliding along world y. Slow it along the face it lies on instead.
            BlockPos under = BlockPos.containing(position().add(down.scale(0.5)));
            float slip = self.level().getBlockState(under).getFriction(self.level(), under, self);
            Vec3 movement = getDeltaMovement();
            Vec3 along = movement.subtract(down.scale(movement.dot(down)));
            setDeltaMovement(movement.subtract(along.scale(1.0 - slip)));
        }
        if (self instanceof FallingBlockEntity falling && self.level() instanceof ServerLevel level && falling.isAlive()) {
            BlockPos pos = self.blockPosition();
            BlockState state = falling.getBlockState();
            if (level.getBlockState(pos).canBeReplaced() && level.setBlock(pos, state, 3)) {
                falling.discard();
            } else {
                falling.spawnAtLocation(level, state.getBlock());
                falling.discard();
            }
        }
    }

    @Inject(method = "move", at = @At("HEAD"))
    private void galaxy$moveStart(MoverType type, Vec3 delta, CallbackInfo ci) {
        galaxy$moveStart = position();
    }

    /** "On ground" means blocked along the local "down", so you can stand and jump on any side. */
    @Inject(method = "move", at = @At("TAIL"))
    private void galaxy$moveEnd(MoverType type, Vec3 delta, CallbackInfo ci) {
        GravityFrame frame = GravityFrames.tilted(galaxy$self());
        Entity self = galaxy$self();
        if (frame == null) {
            if (!(self instanceof LivingEntity)) {
                galaxy$landItem(self, delta);
            }
            return;
        }
        Vec3 down = frame.up().scale(-1);
        double wanted = delta.dot(down);
        double moved = position().subtract(galaxy$moveStart).dot(down);
        boolean blocked = wanted > 1.0E-5 && moved < wanted - 1.0E-5;
        setOnGround(blocked);

        // Vanilla sorts collisions into "walls" and "floor" by world axes. Redo it along the
        // hitbox's axis, so the planet under you is the floor and doesn't stop sprinting.
        Vec3 a = GravityFrames.boxAxis(frame).getUnitVec3();
        Vec3 flatWanted = delta.subtract(a.scale(delta.dot(a)));
        Vec3 flatMoved = galaxy$collided.subtract(a.scale(galaxy$collided.dot(a)));
        self.horizontalCollision = flatWanted.subtract(flatMoved).lengthSqr() > 1.0E-10;
        self.minorHorizontalCollision = self.horizontalCollision;
        self.verticalCollision = Math.abs(delta.dot(a) - galaxy$collided.dot(a)) > 1.0E-7;
        self.verticalCollisionBelow = blocked;
        if (blocked) {
            double into = getDeltaMovement().dot(down);
            if (into > 0) {
                setDeltaMovement(getDeltaMovement().subtract(down.scale(into)));
            }
        }
    }
}
