package io.github.chocodanette.galaxy.gravity;

import java.util.List;

import com.mojang.brigadier.arguments.DoubleArgumentType;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.context.CommandContext;

import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.arguments.coordinates.Vec3Argument;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.RegisterCommandsEvent;

/**
 * Test commands until galaxy generation places planets on its own:
 * <pre>
 * /galaxy planet add &lt;center&gt; &lt;radius&gt; &lt;range&gt; [strength]
 * /galaxy planet list
 * /galaxy planet remove &lt;number&gt;
 * /galaxy planet clear
 * </pre>
 */
public final class PlanetCommands {
    private PlanetCommands() {
    }

    static void register() {
        NeoForge.EVENT_BUS.addListener(PlanetCommands::onRegisterCommands);
    }

    private static void onRegisterCommands(RegisterCommandsEvent event) {
        event.getDispatcher().register(Commands.literal("galaxy")
                .requires(Commands.hasPermission(Commands.LEVEL_GAMEMASTERS))
                .then(Commands.literal("planet")
                        .then(Commands.literal("add")
                                .then(Commands.argument("center", Vec3Argument.vec3())
                                        .then(Commands.argument("radius", DoubleArgumentType.doubleArg(1, 512))
                                                .then(Commands.argument("range", DoubleArgumentType.doubleArg(1, 2048))
                                                        .executes(ctx -> add(ctx, 1.0))
                                                        .then(Commands.argument("strength", DoubleArgumentType.doubleArg(0, 10))
                                                                .executes(ctx -> add(ctx, DoubleArgumentType.getDouble(ctx, "strength"))))))))
                        .then(Commands.literal("list").executes(PlanetCommands::list))
                        .then(Commands.literal("remove")
                                .then(Commands.argument("number", IntegerArgumentType.integer(1))
                                        .executes(PlanetCommands::remove)))
                        .then(Commands.literal("clear").executes(PlanetCommands::clear))));
    }

    private static int add(CommandContext<CommandSourceStack> ctx, double strength) {
        Vec3 center = Vec3Argument.getVec3(ctx, "center");
        double radius = DoubleArgumentType.getDouble(ctx, "radius");
        double range = DoubleArgumentType.getDouble(ctx, "range");
        if (range < radius) {
            ctx.getSource().sendFailure(Component.literal("Range must be at least the radius."));
            return 0;
        }
        ServerLevel level = ctx.getSource().getLevel();
        PlanetGravityData.get(level).add(new GravityField(center, radius, range, strength));
        PlanetSync.sendToDimension(level);
        ctx.getSource().sendSuccess(() -> Component.literal(String.format(
                "Added a planet at %.1f %.1f %.1f (radius %.0f, range %.0f, strength %.2f)",
                center.x, center.y, center.z, radius, range, strength)), true);
        return 1;
    }

    private static int list(CommandContext<CommandSourceStack> ctx) {
        List<GravityField> fields = PlanetGravityData.get(ctx.getSource().getLevel()).fields();
        if (fields.isEmpty()) {
            ctx.getSource().sendSuccess(() -> Component.literal("No planets in this dimension."), false);
            return 0;
        }
        for (int i = 0; i < fields.size(); i++) {
            GravityField f = fields.get(i);
            int number = i + 1;
            ctx.getSource().sendSuccess(() -> Component.literal(String.format(
                    "%d: center %.1f %.1f %.1f, radius %.0f, range %.0f, strength %.2f",
                    number, f.center().x, f.center().y, f.center().z, f.radius(), f.range(), f.strength())), false);
        }
        return fields.size();
    }

    private static int remove(CommandContext<CommandSourceStack> ctx) {
        int number = IntegerArgumentType.getInteger(ctx, "number");
        ServerLevel level = ctx.getSource().getLevel();
        if (!PlanetGravityData.get(level).remove(number - 1)) {
            ctx.getSource().sendFailure(Component.literal("No planet number " + number + ". Use /galaxy planet list."));
            return 0;
        }
        PlanetSync.sendToDimension(level);
        ctx.getSource().sendSuccess(() -> Component.literal("Removed planet " + number), true);
        return 1;
    }

    private static int clear(CommandContext<CommandSourceStack> ctx) {
        ServerLevel level = ctx.getSource().getLevel();
        PlanetGravityData.get(level).clear();
        PlanetSync.sendToDimension(level);
        ctx.getSource().sendSuccess(() -> Component.literal("Removed every planet in this dimension"), true);
        return 1;
    }
}
