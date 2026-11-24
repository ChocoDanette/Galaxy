package io.github.chocodanette.galaxy.ship;

import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;

/** Where the station is: its dimension and the fixed spots in it. */
public final class StationLocation {
    public static final ResourceKey<Level> STATION = ResourceKey.create(Registries.DIMENSION,
            Identifier.fromNamespaceAndPath(GalaxyShip.MODID, "station"));

    /** The block in the middle of the station's floor (you stand at this height). */
    public static final BlockPos ORIGIN = new BlockPos(0, 100, 0);

    /** Where players appear: in front of the hut, facing the walkway over the edge. */
    public static final Vec3 SPAWN = new Vec3(0.5, ORIGIN.getY(), 0.5);
    /** Facing south, toward the walkway. */
    public static final float SPAWN_YAW = 0.0F;

    /** Falling this far below the floor drops you into the galaxy. */
    public static final int DIVE_DEPTH = 24;

    /** Where the model of the galaxy floats, below the station. */
    public static final BlockPos VIEW_CENTER = ORIGIN.below(58);

    private StationLocation() {
    }

    public static ServerLevel level(MinecraftServer server) {
        return server.getLevel(STATION);
    }

    public static boolean isStation(Level level) {
        return level.dimension().equals(STATION);
    }
}
