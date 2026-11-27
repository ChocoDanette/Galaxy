package io.github.chocodanette.galaxy.gravity;

import java.util.function.Supplier;

import org.jspecify.annotations.Nullable;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.attachment.AttachmentType;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.tick.EntityTickEvent;
import net.neoforged.neoforge.registries.DeferredRegister;
import net.neoforged.neoforge.registries.NeoForgeRegistries;

/**
 * Gives every living entity a {@link GravityFrame} and turns it toward its planet each tick.
 *
 * The frame is not saved or sent over the network: both the server and each player work it
 * out from the same synced planet list, so they agree without extra traffic.
 */
public final class GravityFrames {
    private static final DeferredRegister<AttachmentType<?>> ATTACHMENTS =
            DeferredRegister.create(NeoForgeRegistries.Keys.ATTACHMENT_TYPES, GalaxyGravity.MODID);

    public static final Supplier<AttachmentType<GravityFrame>> FRAME =
            ATTACHMENTS.register("frame", () -> AttachmentType.builder(GravityFrame::new).build());

    private GravityFrames() {
    }

    static void register(IEventBus modEventBus) {
        ATTACHMENTS.register(modEventBus);
        NeoForge.EVENT_BUS.addListener(GravityFrames::onEntityTick);
    }

    /** The entity's frame when it is tilted, otherwise null (vanilla behavior applies). */
    public static @Nullable GravityFrame tilted(Entity entity) {
        if (!(entity instanceof LivingEntity)) {
            return null;
        }
        GravityFrame frame = entity.getExistingDataOrNull(FRAME);
        return frame != null && frame.isTilted() ? frame : null;
    }

    /** The entity's frame if it has one (tilted or not), otherwise null. */
    public static @Nullable GravityFrame of(Entity entity) {
        return entity instanceof LivingEntity ? entity.getExistingDataOrNull(FRAME) : null;
    }

    /** The world axis the hitbox stands along: the one closest to the frame's "up". */
    public static Direction boxAxis(GravityFrame frame) {
        Vec3 up = frame.up();
        return Direction.getApproximateNearest(up.x, up.y, up.z);
    }

    /**
     * A hitbox of the given size stood up along an axis, with the feet at {@code pos}.
     * For {@link Direction#UP} this is the vanilla box.
     */
    public static AABB orientedBox(Direction axis, double width, double height, Vec3 pos) {
        double w = width / 2.0;
        double minX = pos.x - w, maxX = pos.x + w;
        double minY = pos.y, maxY = pos.y + height;
        double minZ = pos.z - w, maxZ = pos.z + w;
        switch (axis) {
            case DOWN -> { minY = pos.y - height; maxY = pos.y; }
            case EAST -> { minX = pos.x; maxX = pos.x + height; minY = pos.y - w; maxY = pos.y + w; }
            case WEST -> { minX = pos.x - height; maxX = pos.x; minY = pos.y - w; maxY = pos.y + w; }
            case SOUTH -> { minZ = pos.z; maxZ = pos.z + height; minY = pos.y - w; maxY = pos.y + w; }
            case NORTH -> { minZ = pos.z - height; maxZ = pos.z; minY = pos.y - w; maxY = pos.y + w; }
            default -> { }
        }
        return new AABB(minX, minY, minZ, maxX, maxY, maxZ);
    }

    /**
     * Grows a box by {@code flat} along the ground and {@code up} along the entity's own "up",
     * like vanilla's {@code inflate(flat, up, flat)} for an upright entity. Vanilla code that
     * reaches around an entity (picking up items, melee reach) assumes world Y is up, which on
     * a planet's side would give a thin reach along one direction of the face.
     */
    public static AABB inflateAlongFrame(Entity entity, AABB box, double flat, double up) {
        GravityFrame frame = tilted(entity);
        if (frame == null) {
            return box.inflate(flat, up, flat);
        }
        Direction.Axis axis = boxAxis(frame).getAxis();
        return box.inflate(axis == Direction.Axis.X ? up : flat, axis == Direction.Axis.Y ? up : flat,
                axis == Direction.Axis.Z ? up : flat);
    }

    /**
     * The block an entity stands in: its feet cell along its own "up". On a planet's
     * underside (or west or north side) the feet sit exactly on the boundary of the block
     * they stand on, so vanilla's {@code blockPosition()} names the ground instead.
     */
    public static BlockPos feetBlock(Entity entity) {
        GravityFrame frame = tilted(entity);
        if (frame == null) {
            return entity.blockPosition();
        }
        return BlockPos.containing(entity.position().add(boxAxis(frame).getUnitVec3().scale(0.5)));
    }

    private static void onEntityTick(EntityTickEvent.Pre event) {
        if (!(event.getEntity() instanceof LivingEntity entity)) {
            return;
        }
        GravityWorld world = worldFor(entity);
        if (world.fields().isEmpty() && !entity.hasData(FRAME)) {
            return;
        }
        GravityFrame frame = entity.getData(FRAME);
        if (frame.isFree()) {
            // Free flight in space owns the frame (see SpaceFlight).
            entity.resetFallDistance();
            return;
        }
        updateLaunch(entity, world, frame);
        Vec3 up = upFor(entity, world, frame);
        frame.setGravityDown(up.scale(-1));
        Direction axisBefore = boxAxis(frame);
        org.joml.Quaternionf before = frame.currentRotation();
        frame.tick(up, facing(entity, frame));
        if (boxAxis(frame) != axisBefore && !hasRoomToTurn(entity, boxAxis(frame))) {
            // Turning now would push the hitbox into rock: wait until there's room. Gravity
            // waits too, or it would pull the body sideways into the wall it can't turn from.
            frame.undoTurn();
            frame.setGravityDown(frame.up().scale(-1));
        }
        frame.noteBoxTurn(boxAxis(frame) != axisBefore);
        if (!before.equals(frame.currentRotation(), 1.0E-6F) && KEEPS_LOOK.test(entity)) {
            keepLook(entity, before, frame.currentRotation());
        }
        float yawChange = frame.settleTwist();
        if (yawChange != 0.0F && entity.isLocalInstanceAuthoritative()) {
            turnYaw(entity, yawChange);
        }
        afterTurn(entity, axisBefore);
        if (tilted(entity) != null || frame.isLaunched()) {
            // Vanilla measures falls along world "down", which is wrong on a planet's side.
            entity.resetFallDistance();
        }
        if (!world.fields().isEmpty()) {
            StuckSafety.tick(entity, frame);
        }
    }

    /**
     * Where the eyes are, given the smooth "up". The view leans smoothly on a curve but the
     * hitbox stays straight along a block axis, so in a narrow tunnel the leaning eyes can end
     * up inside the rock beside the body: you'd take suffocation damage and see through
     * blocks. When that happens, the eyes go back to the middle of the hitbox instead, which
     * is always clear.
     */
    public static Vec3 safeEyes(Entity entity, Vec3 feet, Vec3 up, Direction axis, double eyeHeight) {
        Vec3 eyes = feet.add(up.scale(eyeHeight));
        if (!insideBlock(entity.level(), eyes)) {
            return eyes;
        }
        return feet.add(axis.getUnitVec3().scale(eyeHeight));
    }

    /** True if the point is inside a solid part of a block. */
    private static boolean insideBlock(Level level, Vec3 point) {
        BlockPos pos = BlockPos.containing(point);
        BlockState state = level.getBlockState(pos);
        if (state.isAir()) {
            return false;
        }
        var shape = state.getCollisionShape(level, pos);
        if (shape.isEmpty()) {
            return false;
        }
        Vec3 local = point.subtract(pos.getX(), pos.getY(), pos.getZ());
        for (AABB part : shape.toAabbs()) {
            if (part.inflate(0.05).contains(local)) {
                return true;
            }
        }
        return false;
    }

    /**
     * Charges the charged jump while crouching on a planet, and ends a charged jump's flight
     * on landing (or in water, on a ladder, or when flying).
     */
    private static void updateLaunch(LivingEntity entity, GravityWorld world, GravityFrame frame) {
        boolean onPlanet = entity.onGround()
                && GravityMath.governingField(world.fields(), entity.position()).isPresent();
        frame.updateCharge(onPlanet && entity.isCrouching());
        if (frame.isLaunched() && (entity.onGround() || entity.isInLiquid() || entity.onClimbable()
                || entity.isFallFlying()
                || entity instanceof net.minecraft.world.entity.player.Player player && player.getAbilities().flying)) {
            frame.setLaunched(false);
        }
    }

    /**
     * True for entities whose view should stay on the same spot while their frame turns (the
     * "steady camera" option for your own player; set by the client).
     */
    public static java.util.function.Predicate<LivingEntity> KEEPS_LOOK = entity -> false;

    /**
     * Changes yaw and pitch so the entity keeps looking at the same spot while its frame turns
     * from {@code before} to {@code after}: the world turns around the view instead of the
     * view sweeping with the body.
     */
    private static void keepLook(LivingEntity entity, org.joml.Quaternionf before, org.joml.Quaternionf after) {
        float yaw = entity.getYRot() * net.minecraft.util.Mth.DEG_TO_RAD;
        float pitch = entity.getXRot() * net.minecraft.util.Mth.DEG_TO_RAD;
        org.joml.Vector3f look = new org.joml.Vector3f(
                -net.minecraft.util.Mth.sin(yaw) * net.minecraft.util.Mth.cos(pitch),
                -net.minecraft.util.Mth.sin(pitch),
                net.minecraft.util.Mth.cos(yaw) * net.minecraft.util.Mth.cos(pitch));
        before.transform(look);
        after.transformInverse(look);
        float newYaw = (float) Math.toDegrees(Math.atan2(-look.x, look.z));
        float newPitch = (float) Math.toDegrees(Math.asin(Math.max(-1.0F, Math.min(1.0F, -look.y))));
        float yawChange = net.minecraft.util.Mth.wrapDegrees(newYaw - entity.getYRot());
        if (Math.abs(newPitch) > 89.5F) {
            // Looking straight up or down: yaw is meaningless there, keep it.
            yawChange = 0.0F;
        }
        entity.setYRot(entity.getYRot() + yawChange);
        entity.setYHeadRot(entity.getYHeadRot() + yawChange);
        entity.yBodyRot += yawChange;
        entity.setXRot(Math.max(-90.0F, Math.min(90.0F, newPitch)));
    }

    /**
     * True if a hitbox stood along this axis fits here, or one lift (at most a block along the
     * new "up") makes it fit.
     */
    private static boolean hasRoomToTurn(LivingEntity entity, Direction axis) {
        net.minecraft.world.entity.EntityDimensions size = entity.getDimensions(entity.getPose());
        Vec3 up = axis.getUnitVec3();
        for (int i = 0; i <= 10; i++) {
            AABB box = orientedBox(axis, size.width(), size.height(), entity.position().add(up.scale(i * 0.1)));
            if (entity.level().noCollision(entity, box.deflate(1.0E-7))) {
                return true;
            }
        }
        return false;
    }

    /**
     * Which way is up for this entity now. On a planet each block face is floor or not (see
     * {@link GravityField#blockUps}), and the entity stands on floor faces:
     *
     * <ol>
     * <li>While it touches a floor face below its feet, it keeps that floor. If it walks into
     * a wall at least as tall as itself whose face is floor too (inside a corner), it steps
     * onto the wall.</li>
     * <li>Just after it walks off an edge (not a jump), if a floor face touches its side (the
     * other face of the corner block), it goes on walking on that face instead of falling.</li>
     * <li>Otherwise it is in the air: it keeps its down while the block it is in allows it,
     * and takes the zone's down when it doesn't.</li>
     * </ol>
     *
     * Around floating gateway planets it turns smoothly with the planet instead.
     */
    private static Vec3 upFor(LivingEntity entity, GravityWorld world, GravityFrame frame) {
        int offGround = frame.updateGround(entity.onGround());
        if (inOpenSpace(entity, world)) {
            // Between planets: keep whatever way you're turned (see SpaceFlightClient).
            frame.setStepUp(null);
            return frame.up();
        }
        var pull = GravityMath.pull(world, entity.position(), Double.NaN, frame.holdCenter());
        pull.filter(GravityMath.Pull::inField).ifPresent(p -> frame.setHoldCenter(p.field().center()));
        if (pull.isEmpty()) {
            frame.setStepUp(null);
            return GravityMath.VANILLA_DOWN.scale(-1);
        }
        if (pull.get().floating() || !pull.get().inField()) {
            // Floating round a gateway planet, or the faint pull from space: turn smoothly.
            frame.setStepUp(null);
            return pull.get().down().scale(-1);
        }
        GravityField field = pull.get().field();
        Direction current = frame.stepUp() != null ? frame.stepUp() : field.zoneUp(entity.position());
        Direction zone = floorUp(entity, frame, field, current, offGround);
        if (zone == current.getOpposite()) {
            StuckSafety.middleCrossing(entity, frame, field, zone);
        }
        frame.setStepUp(zone);
        return zone.getUnitVec3();
    }

    /** Ticks after a turn onto another face before a wall can take you again. */
    private static final int WALL_STEP_COOLDOWN = 10;
    /** Ticks after leaving the ground during which a floor face at your side still catches you. */
    private static final int EDGE_CATCH_TICKS = 4;

    /** The rules of {@link #upFor} on a planet: which floor the entity stands on. */
    static Direction floorUp(LivingEntity entity, GravityFrame frame, GravityField field, Direction current,
            int offGround) {
        AABB box = entity.getBoundingBox();
        Level level = entity.level();
        if (touchesFloor(level, field, box, current, current, false)) {
            if (frame.ticksSinceBoxTurn() >= WALL_STEP_COOLDOWN) {
                Vec3 facing = facing(entity, frame);
                for (Direction wall : Direction.values()) {
                    if (wall.getAxis() != current.getAxis() && facing.dot(wall.getUnitVec3()) < -0.7
                            && touchesFloor(level, field, box, wall, current, true)) {
                        return wall;
                    }
                }
            }
            return current;
        }
        Vec3 motion = entity.getDeltaMovement();
        boolean jumping = motion.dot(current.getUnitVec3()) > 0.05;
        if (offGround <= EDGE_CATCH_TICKS && !jumping) {
            for (Direction side : Direction.values()) {
                if (side.getAxis() != current.getAxis() && touchesFloor(level, field, box, side, current, false)) {
                    return side;
                }
            }
        }
        // In the air: the block at your feet decides (a hair up from them, so standing on a
        // block's top counts as the block you stand in, not the one below).
        Vec3 feet = entity.position().add(current.getUnitVec3().scale(0.05));
        return field.blockUp(BlockPos.containing(feet), current);
    }

    /** How close a block face must be to the hitbox to count as touching it, in blocks. */
    private static final double TOUCH = 0.06;

    /**
     * True if a solid block touches the hitbox on its {@code -up} side, with that face being
     * floor for {@code up}. With {@code fullHeight}, the touching blocks must cover both the
     * lower and the upper part of the hitbox along {@code bodyUp} (a wall as tall as you).
     */
    static boolean touchesFloor(Level level, GravityField field, AABB box, Direction up, Direction bodyUp,
            boolean fullHeight) {
        if (!fullHeight) {
            return touchesFloorIn(level, field, slab(box, up), up);
        }
        Direction.Axis along = bodyUp.getAxis();
        double min = box.min(along);
        double max = box.max(along);
        double third = (max - min) / 3.0;
        return touchesFloorIn(level, field, slab(clip(box, along, min, min + third), up), up)
                && touchesFloorIn(level, field, slab(clip(box, along, max - third, max), up), up);
    }

    /** A thin box just outside the hitbox on its {@code -up} side, a bit narrower than it. */
    private static AABB slab(AABB box, Direction up) {
        double inset = 0.02;
        double[] min = {box.minX + inset, box.minY + inset, box.minZ + inset};
        double[] max = {box.maxX - inset, box.maxY - inset, box.maxZ - inset};
        int i = up.getAxis().ordinal();
        if (up.getAxisDirection() == Direction.AxisDirection.POSITIVE) {
            max[i] = box.min(up.getAxis());
            min[i] = max[i] - TOUCH;
        } else {
            min[i] = box.max(up.getAxis());
            max[i] = min[i] + TOUCH;
        }
        return new AABB(min[0], min[1], min[2], max[0], max[1], max[2]);
    }

    private static AABB clip(AABB box, Direction.Axis axis, double from, double to) {
        return switch (axis) {
            case X -> new AABB(from, box.minY, box.minZ, to, box.maxY, box.maxZ);
            case Y -> new AABB(box.minX, from, box.minZ, box.maxX, to, box.maxZ);
            case Z -> new AABB(box.minX, box.minY, from, box.maxX, box.maxY, to);
        };
    }

    private static boolean touchesFloorIn(Level level, GravityField field, AABB slab, Direction up) {
        BlockPos.MutableBlockPos cursor = new BlockPos.MutableBlockPos();
        for (int x = (int) Math.floor(slab.minX); x <= (int) Math.floor(slab.maxX); x++) {
            for (int y = (int) Math.floor(slab.minY); y <= (int) Math.floor(slab.maxY); y++) {
                for (int z = (int) Math.floor(slab.minZ); z <= (int) Math.floor(slab.maxZ); z++) {
                    cursor.set(x, y, z);
                    BlockState state = level.getBlockState(cursor);
                    var shape = state.getCollisionShape(level, cursor);
                    if (shape.isEmpty() || !field.blockUps(cursor).contains(up)) {
                        continue;
                    }
                    for (AABB part : shape.toAabbs()) {
                        if (part.move(cursor).intersects(slab)) {
                            return true;
                        }
                    }
                }
            }
        }
        return false;
    }

    /** The way the entity faces, flat in its own frame, in world space. */
    private static Vec3 facing(LivingEntity entity, GravityFrame frame) {
        double yaw = Math.toRadians(entity.getYRot());
        return frame.toWorld(new Vec3(-Math.sin(yaw), 0, Math.cos(yaw)));
    }

    /**
     * True when the entity floats in space between planets: in a space dimension that has
     * planets, outside every planet's range. There nothing turns its frame, so the view is free.
     */
    public static boolean inOpenSpace(Entity entity, GravityWorld world) {
        return !world.fields().isEmpty() && Space.isSpace(entity.level())
                && GravityMath.governingField(world.fields(), entity.position()).isEmpty();
    }

    /** Turns the entity's yaw (view, head and body) by some degrees, with no smoothing jump. */
    private static void turnYaw(LivingEntity entity, float degrees) {
        entity.setYRot(entity.getYRot() + degrees);
        entity.yRotO += degrees;
        entity.setYHeadRot(entity.getYHeadRot() + degrees);
        entity.yHeadRotO += degrees;
        entity.yBodyRot += degrees;
        entity.yBodyRotO += degrees;
    }

    /** Call after a frame turned: rebuilds the hitbox if it now stands along another axis. */
    public static void afterTurn(LivingEntity entity, Direction axisBefore) {
        GravityFrame frame = entity.getData(FRAME);
        Direction axisAfter = boxAxis(frame);
        if (axisAfter != axisBefore) {
            standUpNewBox(entity, axisAfter);
        }
    }

    /**
     * When the hitbox swings to a new axis it can end up partly inside the planet. Rebuild it
     * and, on the side that owns the movement, lift the entity along its new "up" until it is
     * free (at most one block).
     */
    private static void standUpNewBox(LivingEntity entity, Direction axis) {
        Vec3 start = entity.position();
        entity.setPos(start);
        if (!entity.isLocalInstanceAuthoritative() || entity.level().noCollision(entity, entity.getBoundingBox())) {
            return;
        }
        Vec3 up = axis.getUnitVec3();
        for (int i = 1; i <= 10; i++) {
            entity.setPos(start.add(up.scale(i * 0.1)));
            if (entity.level().noCollision(entity, entity.getBoundingBox())) {
                return;
            }
        }
        entity.setPos(start);
    }

    /** The gravity rules of the entity's dimension, from the server's list or the player's copy. */
    public static GravityWorld worldFor(Entity entity) {
        if (entity.level() instanceof ServerLevel level) {
            return PlanetGravityData.get(level).world();
        }
        return ClientPlanets.world();
    }
}
