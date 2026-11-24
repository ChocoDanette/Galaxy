package io.github.chocodanette.galaxy.gravity;

import java.util.Optional;

import net.minecraft.resources.Identifier;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.tick.EntityTickEvent;

/**
 * Pulls living entities toward the planet that governs their position.
 *
 * Inside a field (or anywhere in a dimension with a space pull), vanilla gravity is switched
 * off with an attribute modifier and replaced by a pull toward the planet. Each entity is handled on the side that owns its
 * movement: the server for mobs, the player's own client for players. That way both sides
 * agree in multiplayer without fighting over the entity's position.
 */
public final class GravityApplier {
    private static final Identifier NO_VANILLA_GRAVITY =
            Identifier.fromNamespaceAndPath(GalaxyGravity.MODID, "planet_gravity");
    private static final AttributeModifier CANCEL_VANILLA = new AttributeModifier(
            NO_VANILLA_GRAVITY, -1.0, AttributeModifier.Operation.ADD_MULTIPLIED_TOTAL);

    private GravityApplier() {
    }

    static void register() {
        NeoForge.EVENT_BUS.addListener(GravityApplier::onEntityTick);
    }

    private static void onEntityTick(EntityTickEvent.Pre event) {
        if (!(event.getEntity() instanceof LivingEntity entity) || !entity.isLocalInstanceAuthoritative()) {
            return;
        }
        AttributeInstance gravity = entity.getAttribute(Attributes.GRAVITY);
        if (gravity == null) {
            return;
        }

        GravityFrame anyFrame = GravityFrames.of(entity);
        double takeoff = anyFrame != null ? anyFrame.takeoffDistance() : Double.NaN;
        Optional<GravityMath.Pull> pull = GravityMath.pull(GravityFrames.worldFor(entity), entity.position(), takeoff,
                anyFrame != null ? anyFrame.holdCenter() : null);
        if (pull.isEmpty() || entity.isNoGravity()) {
            gravity.removeModifier(NO_VANILLA_GRAVITY);
            return;
        }

        if (!gravity.hasModifier(NO_VANILLA_GRAVITY)) {
            gravity.addTransientModifier(CANCEL_VANILLA);
        }
        if (SpaceFlight.isFreeFlying(entity)) {
            // Free flight in space: nothing pulls you (vanilla gravity stays off too).
            return;
        }
        GravityField field = pull.get().field();
        Vec3 down = pull.get().down();
        double scale = pull.get().scale();
        if (anyFrame != null && pull.get().inField()) {
            if (entity.onGround() || entity.isInLiquid() || entity.onClimbable()) {
                anyFrame.setTakeoffDistance(field.distanceToSurface(entity.position()));
                scale = field.strength() * GravityMath.pullFactor(field, entity.position(), anyFrame.takeoffDistance());
            }
        }
        if (anyFrame != null && anyFrame.gravityDown() != null && pull.get().inField()) {
            // Living things are pulled the way their frame update picked (into the ground).
            down = anyFrame.gravityDown();
        }
        GravityFrame frame = GravityFrames.tilted(entity);
        if (frame != null && entity.onGround()) {
            // Standing on a surface, pull straight into the block face you stand on. The true
            // direction can be slanted near a cube's edge or on a small sphere, and its sideways
            // part would make you slide slowly toward the middle of the face.
            down = GravityFrames.boxAxis(frame).getUnitVec3().scale(-1);
        }
        if (anyFrame != null && anyFrame.isLaunched()) {
            // Flying after a charged jump: half the pull, so you can leave the planet.
            scale *= Launch.PULL_SHARE;
        }
        entity.setDeltaMovement(entity.getDeltaMovement().add(down.scale(gravity.getBaseValue() * scale)));
    }
}
