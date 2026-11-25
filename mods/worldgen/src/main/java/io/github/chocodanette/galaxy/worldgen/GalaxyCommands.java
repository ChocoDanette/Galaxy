package io.github.chocodanette.galaxy.worldgen;

import java.util.List;
import java.util.Set;

import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.exceptions.CommandSyntaxException;

import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.RegisterCommandsEvent;

import io.github.chocodanette.galaxy.gravity.GravityField;

/**
 * Operator commands to visit the galaxy for testing. Players get there by jumping off the station
 * (Galaxy Station mod). The commands:
 * <pre>
 * /galaxy go            travel to the current galaxy, landing on its middle planet
 * /galaxy leave         go back to the overworld spawn
 * /galaxy info          what galaxy and planet you're near
 * /galaxy timer         how long until the galaxy collapses
 * /galaxy timer &lt;min&gt;   make it collapse in this many minutes (0 = right now)
 * /galaxy next          collapse it now: everyone inside goes home, a new galaxy forms
 * /galaxy find &lt;kind&gt;   jump to the nearest hollow planet, gateway planets, planet with
 *                       caves or planet with trees in the galaxy you're in, or onto
 *                       the nearest linked Launch Bloom leaf ("bloom") or Homeward
 *                       Bloom leaf ("homeward")
 * </pre>
 */
public final class GalaxyCommands {
    private GalaxyCommands() {
    }

    static void register() {
        NeoForge.EVENT_BUS.addListener(GalaxyCommands::onRegisterCommands);
    }

    private static void onRegisterCommands(RegisterCommandsEvent event) {
        event.getDispatcher().register(Commands.literal("galaxy")
                .requires(Commands.hasPermission(Commands.LEVEL_GAMEMASTERS))
                .then(Commands.literal("go").executes(GalaxyCommands::go))
                .then(Commands.literal("timer")
                        .executes(GalaxyCommands::timer)
                        .then(Commands.argument("minutes", IntegerArgumentType.integer(0, 6 * 60))
                                .executes(ctx -> setTimer(ctx, IntegerArgumentType.getInteger(ctx, "minutes")))))
                .then(Commands.literal("next").executes(GalaxyCommands::next))
                .then(Commands.literal("leave").executes(GalaxyCommands::leave))
                .then(Commands.literal("info").executes(GalaxyCommands::info))
                .then(Commands.literal("find")
                        .then(Commands.literal("hollow").executes(ctx -> find(ctx, "hollow")))
                        .then(Commands.literal("gateway").executes(ctx -> find(ctx, "gateway")))
                        .then(Commands.literal("caves").executes(ctx -> find(ctx, "caves")))
                        .then(Commands.literal("trees").executes(ctx -> find(ctx, "trees")))
                        .then(Commands.literal("bloom").executes(ctx -> findBloom(ctx, false)))
                        .then(Commands.literal("homeward").executes(ctx -> findBloom(ctx, true)))));
    }

    /** How many cells around the player /galaxy find looks through, along each axis (about 1000 blocks). */
    private static final int FIND_CELLS = 5;

    private static boolean matches(List<Planet> cell, String kind) {
        return switch (kind) {
            case "hollow" -> cell.size() == 1 && cell.get(0).shape() == GravityField.Shape.HOLLOW;
            case "gateway" -> cell.size() > 1;
            case "caves" -> cell.stream().anyMatch(Planet::caves);
            default -> cell.stream().anyMatch(p -> !p.trees().isEmpty());
        };
    }

    private static int find(CommandContext<CommandSourceStack> ctx, String kind) throws CommandSyntaxException {
        ServerPlayer player = ctx.getSource().getPlayerOrException();
        ServerLevel level = ctx.getSource().getLevel();
        if (!level.dimension().equals(PlanetGravityLink.GALAXY) || Galaxy.numberAt(player.getX()) == 0) {
            ctx.getSource().sendFailure(Component.literal("Go to a galaxy first, with /galaxy go."));
            return 0;
        }
        long seed = level.getSeed();
        int cellX = GalaxyLayout.cellOf(player.getX());
        int cellY = GalaxyLayout.cellOf(player.getY());
        int cellZ = GalaxyLayout.cellOf(player.getZ());
        Planet found = null;
        double best = Double.MAX_VALUE;
        for (int dx = -FIND_CELLS; dx <= FIND_CELLS; dx++) {
            for (int dy = -FIND_CELLS; dy <= FIND_CELLS; dy++) {
            for (int dz = -FIND_CELLS; dz <= FIND_CELLS; dz++) {
                List<Planet> cell = GalaxyLayout.planetsInCell(seed, cellX + dx, cellY + dy, cellZ + dz);
                if (cell.isEmpty() || !matches(cell, kind)) {
                    continue;
                }
                Planet planet = kind.equals("caves") || kind.equals("trees")
                        ? cell.stream().filter(p -> kind.equals("caves") ? p.caves() : !p.trees().isEmpty())
                                .findFirst().orElseThrow()
                        : cell.get(0);
                double distance = planet.center().distanceToSqr(player.position());
                if (distance < best && distance > 1.0) {
                    best = distance;
                    found = planet;
                }
            }
            }
        }
        if (found == null) {
            ctx.getSource().sendFailure(Component.literal("No " + kind + " planet near here. Try another galaxy."));
            return 0;
        }
        Vec3 spot = found.landingSpot();
        PlanetGravityLink.addPlanetsAround(level, spot, true);
        player.teleportTo(level, spot.x, spot.y, spot.z, Set.of(), player.getYRot(), 0.0F, true);
        Planet planet = found;
        ctx.getSource().sendSuccess(() -> Component.literal(String.format(
                "Landed on a %s %s planet (%s), center %.0f %.0f %.0f.", planet.theme().displayName(),
                planet.shape().getSerializedName(), kind, planet.center().x, planet.center().y, planet.center().z)),
                false);
        return 1;
    }

    /** Puts you on the nearest linked Launch Bloom leaf (or Homeward Bloom leaf), ready to crouch and jump. */
    private static int findBloom(CommandContext<CommandSourceStack> ctx, boolean home) throws CommandSyntaxException {
        ServerPlayer player = ctx.getSource().getPlayerOrException();
        ServerLevel level = ctx.getSource().getLevel();
        if (!level.dimension().equals(PlanetGravityLink.GALAXY) || Galaxy.numberAt(player.getX()) == 0) {
            ctx.getSource().sendFailure(Component.literal("Go to a galaxy first, with /galaxy go."));
            return 0;
        }
        long seed = level.getSeed();
        Blooms.Leaf found = null;
        double best = Double.MAX_VALUE;
        for (Planet planet : GalaxyLayout.planetsNear(seed, player.position(), home ? FIND_CELLS : 3)) {
            if (home && !Blooms.isHomeward(seed, planet)) {
                continue;
            }
            var bloom = Blooms.bloom(seed, planet);
            if (bloom.isEmpty()) {
                continue;
            }
            for (Blooms.Leaf leaf : bloom.get().leaves()) {
                double distance = leaf.standSpot().distanceToSqr(player.position());
                if ((home ? leaf.home() : leaf.linked()) && distance < best && distance > 4.0) {
                    best = distance;
                    found = leaf;
                }
            }
        }
        if (found == null) {
            ctx.getSource().sendFailure(Component.literal((home ? "No Homeward Bloom" : "No Launch Bloom")
                    + " near here. Try another galaxy."));
            return 0;
        }
        Vec3 spot = found.standSpot().add(found.up().getUnitVec3().scale(0.05));
        PlanetGravityLink.addPlanetsAround(level, spot, true);
        player.teleportTo(level, spot.x, spot.y, spot.z, Set.of(), player.getYRot(), 0.0F, true);
        Blooms.Leaf leaf = found;
        if (home) {
            ctx.getSource().sendSuccess(() -> Component.literal(String.format(
                    "On a Homeward Bloom leaf at %d %d %d. Crouch to see the way home, then jump.",
                    leaf.pad().getX(), leaf.pad().getY(), leaf.pad().getZ())), false);
            return 1;
        }
        ctx.getSource().sendSuccess(() -> Component.literal(String.format(
                "On a Launch Bloom leaf at %d %d %d, linked to a %s planet. Crouch to see the arc, then jump.",
                leaf.pad().getX(), leaf.pad().getY(), leaf.pad().getZ(), leaf.partner().theme().displayName())), false);
        return 1;
    }

    private static int go(CommandContext<CommandSourceStack> ctx) throws CommandSyntaxException {
        ServerPlayer player = ctx.getSource().getPlayerOrException();
        MinecraftServer server = ctx.getSource().getServer();
        ServerLevel galaxyLevel = server.getLevel(PlanetGravityLink.GALAXY);
        if (galaxyLevel == null) {
            ctx.getSource().sendFailure(Component.literal(
                    "The galaxy dimension isn't loaded. Is Galaxy Generation installed on the server?"));
            return 0;
        }
        Planet planet = GalaxyLayout.arrivalPlanet(galaxyLevel.getSeed());
        Vec3 spot = planet.landingSpot();
        // Gravity first, so it's already there when the player arrives.
        PlanetGravityLink.addPlanetsAround(galaxyLevel, spot, true);
        player.teleportTo(galaxyLevel, spot.x, spot.y, spot.z, Set.of(), player.getYRot(), 0.0F, true);
        GalaxyClock.markInCurrentGalaxy(player);
        Galaxy galaxy = Galaxy.current(galaxyLevel.getSeed());
        ctx.getSource().sendSuccess(() -> Component.literal(String.format(
                "Welcome to galaxy %d: %s planets are common here. You're on a %s planet.",
                galaxy.number(), describe(galaxy), planet.theme().displayName())), false);
        return 1;
    }

    private static int timer(CommandContext<CommandSourceStack> ctx) {
        GalaxyCycle cycle = GalaxyCycle.get(ctx.getSource().getServer());
        ctx.getSource().sendSuccess(() -> Component.literal(String.format(
                "Galaxy %d collapses in %s (it lasts %s in all). The clock only runs while someone is online.",
                GalaxyCycle.number(), GalaxyClock.duration(cycle.ticksLeft()), GalaxyClock.duration(cycle.life()))),
                false);
        return 1;
    }

    private static int setTimer(CommandContext<CommandSourceStack> ctx, int minutes) {
        GalaxyClock.setTimeLeft(ctx.getSource().getServer(), Math.max(1, minutes * 60L * 20L));
        ctx.getSource().sendSuccess(() -> Component.literal(minutes == 0
                ? "The galaxy collapses now."
                : "Galaxy " + GalaxyCycle.number() + " now collapses in " + minutes + " minutes."), true);
        return 1;
    }

    private static int next(CommandContext<CommandSourceStack> ctx) {
        GalaxyClock.collapse(ctx.getSource().getServer());
        return 1;
    }

    private static int leave(CommandContext<CommandSourceStack> ctx) throws CommandSyntaxException {
        ServerPlayer player = ctx.getSource().getPlayerOrException();
        MinecraftServer server = ctx.getSource().getServer();
        ServerLevel overworld = server.overworld();
        BlockPos spawn = server.getRespawnData().pos();
        int y = overworld.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, spawn.getX(), spawn.getZ());
        player.teleportTo(overworld, spawn.getX() + 0.5, y, spawn.getZ() + 0.5, Set.of(), player.getYRot(), 0.0F, true);
        return 1;
    }

    private static int info(CommandContext<CommandSourceStack> ctx) {
        ServerLevel level = ctx.getSource().getLevel();
        if (!level.dimension().equals(PlanetGravityLink.GALAXY)) {
            ctx.getSource().sendSuccess(() -> Component.literal(
                    "You're not in the galaxy. Use /galaxy go to travel there."), false);
            return 0;
        }
        Vec3 pos = ctx.getSource().getPosition();
        int number = Galaxy.numberAt(pos.x);
        if (number == 0) {
            ctx.getSource().sendSuccess(() -> Component.literal("Deep space: no galaxy here."), false);
            return 0;
        }
        Galaxy galaxy = Galaxy.current(level.getSeed());
        StringBuilder text = new StringBuilder(String.format(
                "Galaxy %d: %d blocks wide, %d tall, common planets: %s. Collapses in %s.", number,
                galaxy.radius() * 2, galaxy.thickness() * 2, describe(galaxy),
                GalaxyClock.duration(GalaxyCycle.get(ctx.getSource().getServer()).ticksLeft())));
        GalaxyLayout.planetsNear(level.getSeed(), pos, 1)
                .stream()
                .min(java.util.Comparator.comparingDouble(p -> p.gravityField().distanceToSurface(pos)))
                .ifPresent(planet -> text.append(String.format(
                        " Nearest planet: %s %s, %s %.1f, gravity %.2f%s, center %.1f %.1f %.1f.",
                        planet.theme().displayName(), planet.shape().getSerializedName(),
                        planet.shape().getSerializedName().equals("cube") ? "half size" : "radius",
                        planet.radius(), planet.strength(), planet.caves() ? ", has caves" : "",
                        planet.center().x, planet.center().y, planet.center().z)));
        ctx.getSource().sendSuccess(() -> Component.literal(text.toString()), false);
        return 1;
    }

    static String describe(Galaxy galaxy) {
        return String.join(", ", galaxy.favorites().stream().map(PlanetTheme::displayName).toList());
    }
}
