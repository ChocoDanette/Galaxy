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
 * Operator commands for testing the ship. Players fly it with the console instead.
 * <pre>
 * /ship board            teleport to the ship
 * /ship info             where the ship is parked and how much fuel it has
 * /ship fuel &lt;amount&gt;     set the fuel
 * /ship galaxy &lt;number&gt;  park the ship at a galaxy without using fuel
 * /ship rebuild          put the ship's blocks back as new (keeps fuel and galaxy)
 * </pre>
 */
public final class ShipCommands {
    private ShipCommands() {
    }

    static void register() {
        NeoForge.EVENT_BUS.addListener(ShipCommands::onRegisterCommands);
    }

    private static void onRegisterCommands(RegisterCommandsEvent event) {
        event.getDispatcher().register(Commands.literal("ship")
                .requires(Commands.hasPermission(Commands.LEVEL_GAMEMASTERS))
                .then(Commands.literal("board").executes(ShipCommands::board))
                .then(Commands.literal("info").executes(ShipCommands::info))
                .then(Commands.literal("fuel")
                        .then(Commands.argument("amount", IntegerArgumentType.integer(0, ShipData.MAX_FUEL))
                                .executes(ctx -> fuel(ctx, IntegerArgumentType.getInteger(ctx, "amount")))))
                .then(Commands.literal("galaxy")
                        .then(Commands.argument("number", IntegerArgumentType.integer(1, Galaxy.MAX_NUMBER))
                                .executes(ctx -> galaxy(ctx, IntegerArgumentType.getInteger(ctx, "number")))))
                .then(Commands.literal("rebuild").executes(ShipCommands::rebuild)));
    }

    private static int board(CommandContext<CommandSourceStack> ctx) throws CommandSyntaxException {
        ShipCrew.beamAboard(ctx.getSource().getPlayerOrException());
        return 1;
    }

    private static int info(CommandContext<CommandSourceStack> ctx) {
        ShipData data = ShipData.get(ctx.getSource().getServer());
        ctx.getSource().sendSuccess(() -> Component.literal(String.format(
                "The ship is parked at galaxy %d with %d/%d fuel (a jump costs %d).",
                data.galaxy(), data.fuel(), ShipData.MAX_FUEL, ShipData.JUMP_COST)), false);
        return 1;
    }

    private static int fuel(CommandContext<CommandSourceStack> ctx, int amount) {
        ShipData.get(ctx.getSource().getServer()).setFuel(amount);
        ShipNetwork.sendStateToShip(ctx.getSource().getServer(), false);
        ctx.getSource().sendSuccess(() -> Component.literal("Ship fuel set to " + amount + "."), true);
        return 1;
    }

    private static int galaxy(CommandContext<CommandSourceStack> ctx, int number) {
        ShipData.get(ctx.getSource().getServer()).setGalaxy(number);
        ShipNetwork.sendStateToShip(ctx.getSource().getServer(), false);
        ctx.getSource().sendSuccess(() -> Component.literal("The ship is now parked at galaxy " + number + "."), true);
        return 1;
    }

    private static int rebuild(CommandContext<CommandSourceStack> ctx) {
        ServerLevel ship = ShipLocation.level(ctx.getSource().getServer());
        if (ship == null) {
            ctx.getSource().sendFailure(Component.literal("The ship dimension isn't loaded."));
            return 0;
        }
        ShipBuilder.build(ship);
        ctx.getSource().sendSuccess(() -> Component.literal("The ship has been rebuilt."), true);
        return 1;
    }
}
