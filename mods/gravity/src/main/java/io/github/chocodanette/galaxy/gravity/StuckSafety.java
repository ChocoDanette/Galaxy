package io.github.chocodanette.galaxy.gravity;

import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import net.minecraft.commands.Commands;
import net.minecraft.core.Direction;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.damagesource.DamageTypes;
import net.minecraft.world.entity.EntityDimensions;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.RegisterCommandsEvent;
import net.neoforged.neoforge.event.entity.living.LivingIncomingDamageEvent;

/**
 * Keeps players and mobs from getting stuck in planet rock, and from being hurt by it.
 *
 * <ul>
 * <li>When a hitbox overlaps blocks for a short moment (it can happen when gravity turns in
 * a tight spot), it slides to the nearest free spot, at most a block and a half away.</li>
 * <li>No suffocation damage while the body turns to a new side, or when only the leaning
 * eyes touched the rock. Really being buried (gravel falling on you) still hurts, after a
 * moment to get out.</li>
 * <li>{@code /galaxy unstuck}, for everyone: moves you straight up to the planet's surface
 * above you, once a minute.</li>
 * </ul>
 *
 * Only in dimensions with planets; elsewhere the vanilla rules apply.
 */
public final class StuckSafety {
    /** Ticks stuck in blocks before the nudge to free space (a quarter of a second). */
    private static final int NUDGE_AFTER_TICKS = 5;
    /** Farthest the nudge moves you, in blocks. */
    private static final double NUDGE_REACH = 1.5;
    /** No wall damage for this many ticks after the body swung to a new side (one second). */
    private static final int TURN_GRACE_TICKS = 20;
    /** Truly buried, wall damage starts after this many ticks (two seconds). */
    private static final int BURIED_GRACE_TICKS = 40;
    /** How far above you /galaxy unstuck looks for room, in blocks. */
    private static final int UNSTUCK_SEARCH = 64;
    /** Ticks between two uses of /galaxy unstuck (one minute). */
    private static final int UNSTUCK_COOLDOWN_TICKS = 60 * 20;

    private static final Map<UUID, Integer> LAST_UNSTUCK = new HashMap<>();

    private StuckSafety() {
    }

    static void register() {
        NeoForge.EVENT_BUS.addListener(StuckSafety::onDamage);
        NeoForge.EVENT_BUS.addListener(StuckSafety::onRegisterCommands);
    }

    /** Called each tick from {@link GravityFrames} for entities in a dimension with planets. */
    static void tick(LivingEntity entity, GravityFrame frame) {
        if (entity.noPhysics || entity.isSpectator() || entity.isPassenger() || entity.isSleeping()) {
            frame.updateStuck(false);
            return;
        }
        boolean stuck = !entity.level().noCollision(entity, entity.getBoundingBox().deflate(1.0E-4));
        int ticks = frame.updateStuck(stuck);
        if (!stuck || ticks < NUDGE_AFTER_TICKS || !entity.isLocalInstanceAuthoritative()) {
            return;
        }
        Vec3 up = GravityFrames.tilted(entity) != null ? frame.up() : new Vec3(0, 1, 0);
        nearestFreeSpot(entity, entity.position(), up, NUDGE_REACH).ifPresent(spot -> {
            entity.setPos(spot);
            entity.setDeltaMovement(Vec3.ZERO);
            entity.resetFallDistance();
            frame.updateStuck(false);
        });
    }

    /**
     * The closest spot (within {@code reach}) where the entity's hitbox fits. Tries straight
     * along its "up" first, then the other five block directions, then everything around.
     */
    static Optional<Vec3> nearestFreeSpot(LivingEntity entity, Vec3 from, Vec3 up, double reach) {
        Direction upAxis = Direction.getApproximateNearest(up.x, up.y, up.z);
        for (double d = 0.1; d <= reach + 1.0E-6; d += 0.1) {
            if (fits(entity, from.add(upAxis.getUnitVec3().scale(d)))) {
                return Optional.of(from.add(upAxis.getUnitVec3().scale(d)));
            }
            for (Direction dir : Direction.values()) {
                if (dir != upAxis && fits(entity, from.add(dir.getUnitVec3().scale(d)))) {
                    return Optional.of(from.add(dir.getUnitVec3().scale(d)));
                }
            }
        }
        Vec3 best = null;
        double bestDistance = Double.MAX_VALUE;
        int steps = (int) Math.ceil(reach / 0.25);
        for (int x = -steps; x <= steps; x++) {
            for (int y = -steps; y <= steps; y++) {
                for (int z = -steps; z <= steps; z++) {
                    Vec3 offset = new Vec3(x * 0.25, y * 0.25, z * 0.25);
                    double distance = offset.lengthSqr();
                    if (distance >= bestDistance || distance > reach * reach) {
                        continue;
                    }
                    if (fits(entity, from.add(offset))) {
                        best = from.add(offset);
                        bestDistance = distance;
                    }
                }
            }
        }
        return Optional.ofNullable(best);
    }

    /** True if the entity's hitbox, as it stands now, fits with its feet at {@code pos}. */
    private static boolean fits(LivingEntity entity, Vec3 pos) {
        AABB box = entity.getBoundingBox().move(pos.subtract(entity.position()));
        return entity.level().noCollision(entity, box.deflate(1.0E-7));
    }

    private static void onDamage(LivingIncomingDamageEvent event) {
        if (!event.getSource().is(DamageTypes.IN_WALL)) {
            return;
        }
        LivingEntity entity = event.getEntity();
        GravityFrame frame = GravityFrames.of(entity);
        if (frame == null || GravityFrames.worldFor(entity).fields().isEmpty()) {
            return;
        }
        boolean bodyFree = entity.level().noCollision(entity, entity.getBoundingBox().deflate(1.0E-4));
        if (bodyFree || frame.ticksSinceBoxTurn() < TURN_GRACE_TICKS || frame.stuckTicks() < BURIED_GRACE_TICKS) {
            event.setCanceled(true);
        }
    }

    private static void onRegisterCommands(RegisterCommandsEvent event) {
        event.getDispatcher().register(Commands.literal("galaxy")
                .then(Commands.literal("unstuck").executes(ctx -> unstuck(ctx.getSource().getPlayerOrException()))));
    }

    /** Moves the player straight up (along the planet's pull) to the first spot above the surface with room. */
    private static int unstuck(ServerPlayer player) {
        GravityWorld world = GravityFrames.worldFor(player);
        GravityFrame frame = GravityFrames.of(player);
        Optional<GravityMath.Pull> pull = GravityMath.pull(world, player.position(), Double.NaN,
                frame != null ? frame.holdCenter() : null);
        if (pull.isEmpty() || !pull.get().inField()) {
            player.sendSystemMessage(Component.literal("You can only use this on a planet."));
            return 0;
        }
        int now = player.level().getServer().getTickCount();
        Integer last = LAST_UNSTUCK.get(player.getUUID());
        if (last != null && now - last < UNSTUCK_COOLDOWN_TICKS && !player.isCreative()) {
            int seconds = (UNSTUCK_COOLDOWN_TICKS - (now - last) + 19) / 20;
            player.sendSystemMessage(Component.literal("Wait " + seconds + " s before using this again."));
            return 0;
        }
        Vec3 up = pull.get().down().scale(-1);
        Direction axis = frame != null && frame.isTilted()
                ? GravityFrames.boxAxis(frame)
                : Direction.getApproximateNearest(up.x, up.y, up.z);
        EntityDimensions size = player.getDimensions(player.getPose());
        double start = Math.max(0.0, -pull.get().field().distanceToSurface(player.position()));
        for (double d = start; d <= start + UNSTUCK_SEARCH; d += 0.5) {
            Vec3 spot = player.position().add(up.scale(d));
            AABB box = GravityFrames.orientedBox(axis, size.width(), size.height() + 1.0, spot);
            if (player.level().noCollision(player, box.deflate(1.0E-7))) {
                LAST_UNSTUCK.put(player.getUUID(), now);
                player.setDeltaMovement(Vec3.ZERO);
                player.resetFallDistance();
                player.teleportTo(spot.x, spot.y, spot.z);
                player.sendSystemMessage(Component.literal("Moved you to free space."));
                return 1;
            }
        }
        player.sendSystemMessage(Component.literal("No free space found above you."));
        return 0;
    }
}
