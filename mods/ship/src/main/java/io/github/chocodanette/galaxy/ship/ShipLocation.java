package io.github.chocodanette.galaxy.ship;

import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;

/** Where the ship is: its dimension and the fixed spots inside it. */
public final class ShipLocation {
    public static final ResourceKey<Level> SHIP = ResourceKey.create(Registries.DIMENSION,
            Identifier.fromNamespaceAndPath(GalaxyShip.MODID, "ship"));

    /** The block in the middle of the ship's floor. Everything is built around it. */
    public static final BlockPos ORIGIN = new BlockPos(0, 100, 0);

    /** Where players appear on board: a few steps behind the console, facing it. */
    public static final Vec3 SPAWN = new Vec3(0.5, ORIGIN.getY(), 3.5);
    /** Facing north (toward the ship's nose and the console). */
    public static final float SPAWN_YAW = 180.0F;

    /** The console block, near the nose. */
    public static final BlockPos CONSOLE = ORIGIN.offset(0, 0, -8);

    private ShipLocation() {
    }

    public static ServerLevel level(MinecraftServer server) {
        return server.getLevel(SHIP);
    }

    public static boolean isShip(Level level) {
        return level.dimension().equals(SHIP);
    }

    /** True for positions inside the ship's outer box, used to keep effects off the ship. */
    public static boolean nearHull(double x, double y, double z) {
        return Math.abs(x - ORIGIN.getX()) < 11 && y > ORIGIN.getY() - 4 && y < ORIGIN.getY() + 10
                && Math.abs(z - ORIGIN.getZ()) < 16;
    }
}
