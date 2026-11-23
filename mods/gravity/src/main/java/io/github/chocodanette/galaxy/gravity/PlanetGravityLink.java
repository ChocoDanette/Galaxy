package io.github.chocodanette.galaxy.gravity;

import java.util.List;
import java.util.Optional;

import gravity_changer.GravityComponent;
import gravity_changer.api.RotationParameters;

import net.minecraft.core.Direction;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.phys.Vec3;

/**
 * Hands our planets to Gravity Changer, which turns the entity's hitbox, movement and camera.
 *
 * Every tick, on both the server and the player's own client, Gravity Changer asks its
 * sources which way gravity should point. We answer with the planet face nearest to the
 * entity: Gravity Changer supports six directions, so "toward the center" is snapped to
 * the closest of up, down, north, south, east or west.
 */
public final class PlanetGravityLink {
    private static final Identifier PHASE = Identifier.fromNamespaceAndPath(GalaxyGravity.MODID, "planets");
    /** Same priority Gravity Changer gives its own gravity potions. */
    private static final double PRIORITY = 1.0;

    private PlanetGravityLink() {
    }

    static void register() {
        GravityComponent.GRAVITY_UPDATE_EVENT.register(PHASE, PlanetGravityLink::update);
    }

    private static void update(Entity entity, GravityComponent component) {
        Optional<GravityField> field = GravityMath.governingField(fieldsFor(entity), entity.position());
        if (field.isEmpty()) {
            return;
        }
        Vec3 down = GravityMath.downDirection(field.get(), entity.position(), GravityMath.VANILLA_DOWN);
        Direction direction = GravityMath.nearestDirection(down, component.getCurrGravityDirection());
        component.applyGravityDirectionEffect(direction, RotationParameters.getDefault(), PRIORITY);
        component.applyGravityStrengthEffect(field.get().strength());
    }

    private static List<GravityField> fieldsFor(Entity entity) {
        if (entity.level() instanceof ServerLevel level) {
            return PlanetGravityData.get(level).fields();
        }
        return ClientPlanets.fields();
    }
}
