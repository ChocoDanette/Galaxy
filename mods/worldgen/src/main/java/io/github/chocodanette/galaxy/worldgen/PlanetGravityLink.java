package io.github.chocodanette.galaxy.worldgen;

import java.util.HashSet;
import java.util.List;
import java.util.Set;

import net.minecraft.core.registries.Registries;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.tick.LevelTickEvent;

import io.github.chocodanette.galaxy.gravity.GravityField;
import io.github.chocodanette.galaxy.gravity.PlanetGravityData;
import io.github.chocodanette.galaxy.gravity.PlanetSync;

/**
 * Gives generated planets their gravity. The Gravity mod keeps a saved list of planets per
 * dimension and sends it to players; this adds each generated planet to that list before
 * anyone gets close to it.
 *
 * Only the server does this, about once a second, for the area around each player in the
 * galaxy. Players get the new planets through the Gravity mod's normal sync, so multiplayer
 * needs nothing extra.
 */
public final class PlanetGravityLink {
    public static final ResourceKey<Level> GALAXY = ResourceKey.create(Registries.DIMENSION,
            Identifier.fromNamespaceAndPath(GalaxyWorldgen.MODID, "galaxy"));

    /** How many cells around a player get their gravity ready (2 cells = about 190 blocks). */
    private static final int CELLS_AROUND_PLAYER = 2;
    private static final int TICKS_BETWEEN_CHECKS = 20;

    private PlanetGravityLink() {
    }

    static void register() {
        NeoForge.EVENT_BUS.addListener(PlanetGravityLink::onLevelTick);
    }

    private static void onLevelTick(LevelTickEvent.Post event) {
        if (!(event.getLevel() instanceof ServerLevel level) || !level.dimension().equals(GALAXY)) {
            return;
        }
        if (level.getGameTime() % TICKS_BETWEEN_CHECKS != 0 || level.players().isEmpty()) {
            return;
        }
        boolean added = false;
        for (ServerPlayer player : level.players()) {
            added |= addPlanetsAround(level, player.position(), false);
        }
        if (added) {
            PlanetSync.sendToDimension(level);
        }
    }

    /**
     * Makes sure every generated planet near a position has its gravity. Returns true when
     * any planet was added. With sync on, players in the galaxy are told straight away.
     */
    public static boolean addPlanetsAround(ServerLevel level, Vec3 pos, boolean sync) {
        PlanetGravityData data = PlanetGravityData.get(level);
        List<GravityField> existing = data.fields();
        if (existing.isEmpty() && data.showCenters()) {
            // Center markers are a testing aid; with hundreds of planets they'd just add clutter.
            // Turn them back on with /galaxy planet centers show.
            data.setShowCenters(false);
        }
        Set<Vec3> known = new HashSet<>();
        for (GravityField field : existing) {
            known.add(field.center());
        }

        long seed = level.getSeed();
        int cellX = GalaxyLayout.cellOf(pos.x);
        int cellZ = GalaxyLayout.cellOf(pos.z);
        boolean added = false;
        for (int dx = -CELLS_AROUND_PLAYER; dx <= CELLS_AROUND_PLAYER; dx++) {
            for (int dz = -CELLS_AROUND_PLAYER; dz <= CELLS_AROUND_PLAYER; dz++) {
                var planet = GalaxyLayout.planetInCell(seed, cellX + dx, cellZ + dz);
                if (planet.isPresent() && known.add(planet.get().center())) {
                    data.add(planet.get().gravityField());
                    added = true;
                }
            }
        }
        if (added && sync) {
            PlanetSync.sendToDimension(level);
        }
        return added;
    }
}
