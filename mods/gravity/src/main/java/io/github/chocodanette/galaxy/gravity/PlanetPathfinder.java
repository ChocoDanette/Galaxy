package io.github.chocodanette.galaxy.gravity;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.PriorityQueue;
import java.util.Set;

import org.jspecify.annotations.Nullable;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;

/**
 * Finds walking paths for mobs standing on a planet's side or underside.
 *
 * Vanilla pathfinding only knows world "up", so on a planet's east face it looks for floors
 * below the mob along world Y and finds nothing. This search works the same way (walk to a
 * neighbor, step up one block, drop down a few) but along the mob's own "up" axis, so it
 * works on every face. It knows nothing about Minecraft blocks: {@link Cells} answers which
 * cells a mob can stand in, which keeps it easy to test.
 */
public final class PlanetPathfinder {
    /** How far a mob is willing to drop down in one go, in blocks (vanilla uses 3 too). */
    public static final int MAX_DROP = 3;

    /** What the search needs to know about the world. */
    public interface Cells {
        /** True if a mob can be in this cell (air, plants, water). */
        boolean passable(int x, int y, int z);

        /** True if a mob can stand on top of this cell (solid and not harmful). */
        boolean floor(int x, int y, int z);
    }

    /** A found path, from the start cell to the last cell, in world block positions. */
    public record Result(List<BlockPos> cells, BlockPos target, boolean reached) {
    }

    private PlanetPathfinder() {
    }

    /** The four ways to walk on a face whose "up" is {@code up}. */
    public static Direction[] flatDirections(Direction up) {
        List<Direction> out = new ArrayList<>(4);
        for (Direction d : Direction.values()) {
            if (d.getAxis() != up.getAxis()) {
                out.add(d);
            }
        }
        return out.toArray(new Direction[0]);
    }

    /** True if a mob {@code height} cells tall can stand in this cell, with "up" along {@code up}. */
    public static boolean standable(Cells cells, BlockPos pos, Direction up, int height) {
        return clear(cells, pos, up, height) && cells.floor(pos.getX() - up.getStepX(), pos.getY() - up.getStepY(),
                pos.getZ() - up.getStepZ());
    }

    private static boolean clear(Cells cells, BlockPos pos, Direction up, int height) {
        for (int i = 0; i < height; i++) {
            if (!cells.passable(pos.getX() + up.getStepX() * i, pos.getY() + up.getStepY() * i,
                    pos.getZ() + up.getStepZ() * i)) {
                return false;
            }
        }
        return true;
    }

    private record Step(BlockPos pos, double cost) {
    }

    /** The cells a mob can reach in one move from {@code from}. */
    static List<Step> neighbors(Cells cells, BlockPos from, Direction up, int height) {
        List<Step> out = new ArrayList<>(8);
        Direction[] flat = flatDirections(up);
        boolean[] walkable = new boolean[flat.length];
        for (int i = 0; i < flat.length; i++) {
            BlockPos next = from.relative(flat[i]);
            if (standable(cells, next, up, height)) {
                out.add(new Step(next, 1.0));
                walkable[i] = true;
                continue;
            }
            BlockPos stepUp = next.relative(up);
            if (clear(cells, from.relative(up, height), up, 1) && standable(cells, stepUp, up, height)) {
                out.add(new Step(stepUp, 1.5));
                continue;
            }
            if (!clear(cells, next, up, height)) {
                continue;
            }
            for (int drop = 1; drop <= MAX_DROP; drop++) {
                BlockPos below = next.relative(up, -drop);
                if (!cells.passable(below.getX(), below.getY(), below.getZ())) {
                    break;
                }
                if (standable(cells, below, up, height)) {
                    out.add(new Step(below, 1.0 + drop * 0.5));
                    break;
                }
            }
        }
        // Diagonals, only on level ground with both corners open (no cutting through walls).
        for (int i = 0; i < flat.length; i++) {
            for (int j = i + 1; j < flat.length; j++) {
                if (flat[i].getAxis() == flat[j].getAxis() || !walkable[i] || !walkable[j]) {
                    continue;
                }
                BlockPos diagonal = from.relative(flat[i]).relative(flat[j]);
                if (standable(cells, diagonal, up, height)) {
                    out.add(new Step(diagonal, Math.sqrt(2.0)));
                }
            }
        }
        return out;
    }

    private record Open(BlockPos pos, double f) {
    }

    /**
     * Searches from {@code start} toward the nearest of {@code targets}. Reaching within
     * {@code reachRange} blocks (counted along each axis, like vanilla) counts as arriving.
     * If no target can be reached, returns the path to the closest cell found, like vanilla.
     * Returns null when the start isn't somewhere a mob can stand.
     */
    public static @Nullable Result find(Cells cells, BlockPos start, Direction up, int height,
            Set<BlockPos> targets, int reachRange, int maxVisited) {
        if (targets.isEmpty() || !clear(cells, start, up, height)) {
            return null;
        }
        Map<BlockPos, Double> cost = new HashMap<>();
        Map<BlockPos, BlockPos> cameFrom = new HashMap<>();
        PriorityQueue<Open> open = new PriorityQueue<>((a, b) -> Double.compare(a.f(), b.f()));
        cost.put(start, 0.0);
        open.add(new Open(start, heuristic(start, targets)));
        BlockPos best = start;
        double bestH = heuristic(start, targets);
        BlockPos reachedTarget = null;
        int visited = 0;
        while (!open.isEmpty() && visited < maxVisited) {
            Open current = open.poll();
            BlockPos pos = current.pos();
            double g = cost.get(pos);
            if (current.f() > g + heuristic(pos, targets) + 1.0E-9) {
                continue; // stale entry
            }
            visited++;
            BlockPos hit = reachedBy(pos, targets, reachRange);
            if (hit != null) {
                best = pos;
                reachedTarget = hit;
                break;
            }
            double h = heuristic(pos, targets);
            if (h < bestH) {
                bestH = h;
                best = pos;
            }
            for (Step step : neighbors(cells, pos, up, height)) {
                double ng = g + step.cost();
                Double old = cost.get(step.pos());
                if (old == null || ng < old - 1.0E-9) {
                    cost.put(step.pos(), ng);
                    cameFrom.put(step.pos(), pos);
                    open.add(new Open(step.pos(), ng + heuristic(step.pos(), targets)));
                }
            }
        }
        List<BlockPos> path = new ArrayList<>();
        for (BlockPos at = best; at != null; at = cameFrom.get(at)) {
            path.add(at);
        }
        Collections.reverse(path);
        BlockPos target = reachedTarget != null ? reachedTarget : nearest(best, targets);
        return new Result(path, target, reachedTarget != null);
    }

    private static @Nullable BlockPos reachedBy(BlockPos pos, Set<BlockPos> targets, int reachRange) {
        for (BlockPos target : targets) {
            if (pos.distManhattan(target) <= reachRange) {
                return target;
            }
        }
        return null;
    }

    private static BlockPos nearest(BlockPos pos, Set<BlockPos> targets) {
        BlockPos best = null;
        double bestD = Double.MAX_VALUE;
        for (BlockPos target : targets) {
            double d = pos.distSqr(target);
            if (d < bestD) {
                bestD = d;
                best = target;
            }
        }
        return best;
    }

    private static double heuristic(BlockPos pos, Set<BlockPos> targets) {
        return Math.sqrt(pos.distSqr(nearest(pos, targets)));
    }
}
