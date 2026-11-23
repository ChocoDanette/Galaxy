package io.github.chocodanette.galaxy.worldgen;

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

/**
 * Operator commands to visit the galaxy for testing. Players travel with the ship's console
 * (Galaxy Ship mod) instead:
 * <pre>
 * /galaxy go [number]   travel to a galaxy (1 if no number), landing on its middle planet
 * /galaxy leave         go back to the overworld spawn
 * /galaxy info          what galaxy and planet you're near
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
                .then(Commands.literal("go")
                        .executes(ctx -> go(ctx, 1))
                        .then(Commands.argument("number", IntegerArgumentType.integer(1, Galaxy.MAX_NUMBER))
                                .executes(ctx -> go(ctx, IntegerArgumentType.getInteger(ctx, "number")))))
                .then(Commands.literal("leave").executes(GalaxyCommands::leave))
                .then(Commands.literal("info").executes(GalaxyCommands::info)));
    }

    private static int go(CommandContext<CommandSourceStack> ctx, int number) throws CommandSyntaxException {
        ServerPlayer player = ctx.getSource().getPlayerOrException();
        MinecraftServer server = ctx.getSource().getServer();
        ServerLevel galaxyLevel = server.getLevel(PlanetGravityLink.GALAXY);
        if (galaxyLevel == null) {
            ctx.getSource().sendFailure(Component.literal(
                    "The galaxy dimension isn't loaded. Is Galaxy Generation installed on the server?"));
            return 0;
        }
        Planet planet = GalaxyLayout.arrivalPlanet(galaxyLevel.getSeed(), number);
        Vec3 spot = planet.landingSpot();
        // Gravity first, so it's already there when the player arrives.
        PlanetGravityLink.addPlanetsAround(galaxyLevel, spot, true);
        player.teleportTo(galaxyLevel, spot.x, spot.y, spot.z, Set.of(), player.getYRot(), 0.0F, true);
        Galaxy galaxy = Galaxy.of(galaxyLevel.getSeed(), number);
        ctx.getSource().sendSuccess(() -> Component.literal(String.format(
                "Welcome to galaxy %d: %s planets are common here. You're on a %s planet.",
                number, describe(galaxy), planet.theme().displayName())), false);
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
        Galaxy galaxy = Galaxy.of(level.getSeed(), number);
        StringBuilder text = new StringBuilder(String.format(
                "Galaxy %d: %d blocks wide, common planets: %s.", number, galaxy.radius() * 2, describe(galaxy)));
        GalaxyLayout.planetInCell(level.getSeed(), GalaxyLayout.cellOf(pos.x), GalaxyLayout.cellOf(pos.z))
                .ifPresent(planet -> text.append(String.format(
                        " Nearest planet: %s %s, %s %.0f, gravity %.2f, center %.1f %.1f %.1f.",
                        planet.theme().displayName(), planet.shape().getSerializedName(),
                        planet.shape().getSerializedName().equals("cube") ? "half size" : "radius",
                        planet.radius(), planet.strength(),
                        planet.center().x, planet.center().y, planet.center().z)));
        ctx.getSource().sendSuccess(() -> Component.literal(text.toString()), false);
        return 1;
    }

    private static String describe(Galaxy galaxy) {
        return String.join(", ", galaxy.favorites().stream().map(PlanetTheme::displayName).toList());
    }
}
