package io.github.chocodanette.galaxy.gravity;

import java.util.List;
import java.util.Optional;

import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerLevel;
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
 * Inside a field, vanilla gravity is switched off with an attribute modifier and replaced
 * by a pull toward the planet's center. Each entity is handled on the side that owns its
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

        Optional<GravityField> field = GravityMath.governingField(fieldsFor(entity), entity.position());
        if (field.isEmpty() || entity.isNoGravity()) {
            gravity.removeModifier(NO_VANILLA_GRAVITY);
            return;
        }

        if (!gravity.hasModifier(NO_VANILLA_GRAVITY)) {
            gravity.addTransientModifier(CANCEL_VANILLA);
        }
        Vec3 down = GravityMath.downDirection(field.get(), entity.position(), GravityMath.VANILLA_DOWN);
        double takeoff = Double.NaN;
        GravityFrame anyFrame = GravityFrames.of(entity);
        if (anyFrame != null) {
            if (entity.onGround() || entity.isInLiquid() || entity.onClimbable()) {
                anyFrame.setTakeoffDistance(field.get().distanceToSurface(entity.position()));
            }
            takeoff = anyFrame.takeoffDistance();
        }
        GravityFrame frame = GravityFrames.tilted(entity);
        if (frame != null && entity.onGround()) {
            // Standing on a surface, pull straight into the block face you stand on. The true
            // direction can be slanted near a cube's edge or on a small sphere, and its sideways
            // part would make you slide slowly toward the middle of the face.
            down = GravityFrames.boxAxis(frame).getUnitVec3().scale(-1);
        }
        double pull = gravity.getBaseValue() * field.get().strength()
                * GravityMath.pullFactor(field.get(), entity.position(), takeoff);
        entity.setDeltaMovement(entity.getDeltaMovement().add(down.scale(pull)));
    }

    private static List<GravityField> fieldsFor(LivingEntity entity) {
        if (entity.level() instanceof ServerLevel level) {
            return PlanetGravityData.get(level).fields();
        }
        return ClientPlanets.fields();
    }
}
