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
        frame.tick(up, facing(entity, frame));
        float yawChange = frame.settleTwist();
        if (yawChange != 0.0F && entity.isLocalInstanceAuthoritative()) {
            turnYaw(entity, yawChange);
        }
        afterTurn(entity, axisBefore);
        if (tilted(entity) != null || frame.isLaunched()) {
            // Vanilla measures falls along world "down", which is wrong on a planet's side.
            entity.resetFallDistance();
        }
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

    /** Ticks after leaving the ground during which "up" still follows the ground. */
    private static final int GROUND_GRACE_TICKS = 3;

    /**
     * Which way is up for this entity now. In {@link GravityMode#GROUND} mode, while standing
     * on something (or just after), it follows the ground around the feet; in the air, and
     * in {@link GravityMode#CENTER} mode, it is the planet's own pull.
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
        Vec3 planetUp = pull.map(GravityMath.Pull::down).orElse(GravityMath.VANILLA_DOWN).scale(-1);
        if (pull.isPresent() && pull.get().floating()) {
            // Floating round a gateway planet: turn smoothly with it, no steps.
            frame.setStepUp(null);
            return planetUp;
        }
        if (world.mode() == GravityMode.STEPS) {
            if (GravityMath.governingField(world.fields(), entity.position()).isEmpty()) {
                frame.setStepUp(null);
                return planetUp;
            }
            Direction step = GravityMath.stepUp(planetUp, frame.stepUp());
            frame.setStepUp(step);
            return step.getUnitVec3();
        }
        if (world.mode() != GravityMode.GROUND || offGround > GROUND_GRACE_TICKS
                || GravityMath.governingField(world.fields(), entity.position()).isEmpty()) {
            return planetUp;
        }
        Level level = entity.level();
        BlockPos.MutableBlockPos cursor = new BlockPos.MutableBlockPos();
        GroundGravity.Solidity solidity = (x, y, z) -> {
            cursor.set(x, y, z);
            BlockState state = level.getBlockState(cursor);
            return !state.isAir() && !state.getCollisionShape(level, cursor).isEmpty();
        };
        return GroundGravity.upAt(solidity, entity.position(), frame.up(), planetUp).orElse(planetUp);
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
