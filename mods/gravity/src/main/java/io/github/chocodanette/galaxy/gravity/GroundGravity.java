package io.github.chocodanette.galaxy.gravity;

import java.util.Optional;

import net.minecraft.world.phys.Vec3;

/**
 * Works out "up" from the ground around an entity's feet: every exposed face of a solid block
 * near the feet votes with its direction, nearer faces count more. On flat ground all votes
 * agree, so "up" stays still; at an edge the top and the side both vote, so "up" leans over
 * the edge and you walk around it smoothly. Inside caves, hollow planets and tunnels it just
 * follows whatever you stand on.
 *
 * Pure math over a "is this block solid" question, so it can be unit tested without a world.
 */
public final class GroundGravity {
    /** How far around the feet faces are looked at, in blocks. */
    public static final int REACH = 2;
    /** Faces farther than this from the feet don't count. */
    private static final double MAX_DISTANCE = 2.6;
    /** Faces turned sideways to your current up count this much (walls you walk past). */
    private static final double SIDEWAYS_WEIGHT = 0.3;
    /** Below this total vote there's no real ground nearby. */
    private static final double MIN_VOTE = 0.35;

    private GroundGravity() {
    }

    /** Answers whether the block at a position is solid ground. */
    @FunctionalInterface
    public interface Solidity {
        boolean isSolid(int x, int y, int z);
    }

    private static final int[][] SIDES = {{1, 0, 0}, {-1, 0, 0}, {0, 1, 0}, {0, -1, 0}, {0, 0, 1}, {0, 0, -1}};

    /**
     * The ground's "up" at these feet, or empty when there is no ground close enough.
     *
     * @param feet      the entity's feet position
     * @param currentUp the entity's current up (unit vector), to tell floors from walls
     * @param prior     where the planet would put "up" (unit vector), to settle ties
     */
    public static Optional<Vec3> upAt(Solidity world, Vec3 feet, Vec3 currentUp, Vec3 prior) {
        int fx = (int) Math.floor(feet.x);
        int fy = (int) Math.floor(feet.y);
        int fz = (int) Math.floor(feet.z);
        int size = REACH * 2 + 3;
        boolean[] solid = new boolean[size * size * size];
        for (int x = 0; x < size; x++) {
            for (int y = 0; y < size; y++) {
                for (int z = 0; z < size; z++) {
                    solid[(x * size + y) * size + z] =
                            world.isSolid(fx + x - REACH - 1, fy + y - REACH - 1, fz + z - REACH - 1);
                }
            }
        }
        double vx = 0;
        double vy = 0;
        double vz = 0;
        double total = 0;
        for (int x = 1; x < size - 1; x++) {
            for (int y = 1; y < size - 1; y++) {
                for (int z = 1; z < size - 1; z++) {
                    if (!solid[(x * size + y) * size + z]) {
                        continue;
                    }
                    double bx = fx + x - REACH - 1;
                    double by = fy + y - REACH - 1;
                    double bz = fz + z - REACH - 1;
                    for (int[] s : SIDES) {
                        if (solid[((x + s[0]) * size + y + s[1]) * size + z + s[2]]) {
                            continue;
                        }
                        // The middle of this open face, and how far it is from the feet.
                        double cx = bx + 0.5 + s[0] * 0.5;
                        double cy = by + 0.5 + s[1] * 0.5;
                        double cz = bz + 0.5 + s[2] * 0.5;
                        double dx = feet.x - cx;
                        double dy = feet.y - cy;
                        double dz = feet.z - cz;
                        // Only faces turned toward the feet.
                        if (dx * s[0] + dy * s[1] + dz * s[2] < -0.05) {
                            continue;
                        }
                        double distance = Math.sqrt(dx * dx + dy * dy + dz * dz);
                        if (distance >= MAX_DISTANCE) {
                            continue;
                        }
                        double near = 1.0 - distance / MAX_DISTANCE;
                        double facing = s[0] * currentUp.x + s[1] * currentUp.y + s[2] * currentUp.z;
                        double weight = near * near * (SIDEWAYS_WEIGHT + (1.0 - SIDEWAYS_WEIGHT) * Math.max(0.0, facing));
                        vx += s[0] * weight;
                        vy += s[1] * weight;
                        vz += s[2] * weight;
                        total += weight;
                    }
                }
            }
        }
        if (total < MIN_VOTE) {
            return Optional.empty();
        }
        // A little of the planet's own up, so opposite walls that cancel out still agree.
        Vec3 up = new Vec3(vx, vy, vz).add(prior.scale(0.05 * total));
        double length = up.length();
        if (length < 1.0E-6) {
            return Optional.empty();
        }
        return Optional.of(up.scale(1.0 / length));
    }
}
