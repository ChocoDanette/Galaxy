package io.github.chocodanette.galaxy.gravity;

import java.util.function.Supplier;

import org.jspecify.annotations.Nullable;

import net.minecraft.core.Direction;
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
        Vec3 down = GravityMath.downDirection(world, entity.position());
        GravityFrame frame = entity.getData(FRAME);
        Direction axisBefore = boxAxis(frame);
        frame.tick(down.scale(-1));
        Direction axisAfter = boxAxis(frame);
        if (axisAfter != axisBefore) {
            standUpNewBox(entity, axisAfter);
        }
        if (tilted(entity) != null) {
            // Vanilla measures falls along world "down", which is wrong on a planet's side.
            entity.resetFallDistance();
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
