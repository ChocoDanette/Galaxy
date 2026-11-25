package io.github.chocodanette.galaxy.ship;

import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.exceptions.CommandSyntaxException;

import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.RegisterCommandsEvent;

import io.github.chocodanette.galaxy.worldgen.GalaxyClock;
import io.github.chocodanette.galaxy.worldgen.GalaxyCycle;

/**
 * <pre>
 * /station                  go back to the station                    (operators, for testing)
 * /station info             which galaxy is below the station        (operators)
 *                           (/galaxy next replaces the galaxy)
 * /station rebuild          put the station and the galaxy model back (operators)
 * </pre>
 */
public final class StationCommands {
    private StationCommands() {
    }

    static void register() {
        NeoForge.EVENT_BUS.addListener(StationCommands::onRegisterCommands);
    }

    private static void onRegisterCommands(RegisterCommandsEvent event) {
        // Operators only: players get home through Homeward Blooms, dying, or the galaxy ending.
        event.getDispatcher().register(Commands.literal("station")
                .requires(Commands.hasPermission(Commands.LEVEL_GAMEMASTERS))
                .executes(StationCommands::back)
                .then(Commands.literal("info")
                        .requires(Commands.hasPermission(Commands.LEVEL_GAMEMASTERS))
                        .executes(StationCommands::info))
                .then(Commands.literal("rebuild")
                        .requires(Commands.hasPermission(Commands.LEVEL_GAMEMASTERS))
                        .executes(StationCommands::rebuild)));
    }

    private static int back(CommandContext<CommandSourceStack> ctx) throws CommandSyntaxException {
        var player = ctx.getSource().getPlayerOrException();
        StationCrew.toStation(player);
        player.sendOverlayMessage(Component.translatable("galaxy_ship.returned"));
        return 1;
    }

    private static int info(CommandContext<CommandSourceStack> ctx) {
        GalaxyCycle cycle = GalaxyCycle.get(ctx.getSource().getServer());
        ctx.getSource().sendSuccess(() -> Component.literal("The station floats above galaxy " + GalaxyCycle.number()
                + ", which collapses in " + GalaxyClock.duration(cycle.ticksLeft()) + "."), false);
        return 1;
    }

    private static int rebuild(CommandContext<CommandSourceStack> ctx) {
        ServerLevel station = StationLocation.level(ctx.getSource().getServer());
        if (station == null) {
            ctx.getSource().sendFailure(Component.literal("The station dimension isn't loaded."));
            return 0;
        }
        StationBuilder.build(station);
        GalaxyView.rebuild(ctx.getSource().getServer());
        ctx.getSource().sendSuccess(() -> Component.literal("The station has been rebuilt."), true);
        return 1;
    }
}
