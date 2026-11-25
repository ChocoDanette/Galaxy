package io.github.chocodanette.galaxy.ship;

import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.exceptions.CommandSyntaxException;

import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.RegisterCommandsEvent;

import io.github.chocodanette.galaxy.worldgen.Galaxy;

/**
 * <pre>
 * /station                  go back to the station                    (operators, for testing)
 * /station info             which galaxy is below the station        (operators)
 * /station galaxy &lt;number&gt;  put another galaxy below the station       (operators)
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
                .then(Commands.literal("galaxy")
                        .requires(Commands.hasPermission(Commands.LEVEL_GAMEMASTERS))
                        .then(Commands.argument("number", IntegerArgumentType.integer(1, Galaxy.MAX_NUMBER))
                                .executes(ctx -> galaxy(ctx, IntegerArgumentType.getInteger(ctx, "number")))))
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
        StationData data = StationData.get(ctx.getSource().getServer());
        ctx.getSource().sendSuccess(() -> Component.literal(
                "The station floats above galaxy " + data.galaxy() + "."), false);
        return 1;
    }

    private static int galaxy(CommandContext<CommandSourceStack> ctx, int number) {
        StationData.get(ctx.getSource().getServer()).setGalaxy(number);
        GalaxyView.refresh(ctx.getSource().getServer());
        ctx.getSource().sendSuccess(() -> Component.literal(
                "The station now floats above galaxy " + number + "."), true);
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
