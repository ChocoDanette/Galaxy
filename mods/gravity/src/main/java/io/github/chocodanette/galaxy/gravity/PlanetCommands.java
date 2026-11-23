package io.github.chocodanette.galaxy.gravity;

import java.util.List;

import com.mojang.brigadier.arguments.DoubleArgumentType;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.context.CommandContext;

import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.arguments.coordinates.BlockPosArgument;
import net.minecraft.commands.arguments.coordinates.Vec3Argument;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.RegisterCommandsEvent;

/**
 * Test commands until galaxy generation places planets on its own:
 * <pre>
 * /galaxy planet add &lt;center&gt; &lt;radius&gt; &lt;range&gt; [strength]     (round planet)
 * /galaxy planet cube &lt;corner&gt; &lt;opposite corner&gt; &lt;range&gt; [strength]   (cube of blocks)
 * /galaxy planet list
 * /galaxy planet remove &lt;number&gt;
 * /galaxy planet clear
 * /galaxy planet centers show|hide   (glowing marker at each gravity center)
 * /galaxy planet ranges show|hide    (light blue bubble showing how far gravity reaches)
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
                        .then(Commands.literal("cube")
                                .then(Commands.argument("from", BlockPosArgument.blockPos())
                                        .then(Commands.argument("to", BlockPosArgument.blockPos())
                                                .then(Commands.argument("range", DoubleArgumentType.doubleArg(1, 2048))
                                                        .executes(ctx -> addCube(ctx, 1.0))
                                                        .then(Commands.argument("strength", DoubleArgumentType.doubleArg(0, 10))
                                                                .executes(ctx -> addCube(ctx, DoubleArgumentType.getDouble(ctx, "strength"))))))))
                        .then(Commands.literal("list").executes(PlanetCommands::list))
                        .then(Commands.literal("remove")
                                .then(Commands.argument("number", IntegerArgumentType.integer(1))
                                        .executes(PlanetCommands::remove)))
                        .then(Commands.literal("clear").executes(PlanetCommands::clear))
                        .then(Commands.literal("centers")
                                .then(Commands.literal("show").executes(ctx -> showCenters(ctx, true)))
                                .then(Commands.literal("hide").executes(ctx -> showCenters(ctx, false))))
                        .then(Commands.literal("ranges")
                                .then(Commands.literal("show").executes(ctx -> showRanges(ctx, true)))
                                .then(Commands.literal("hide").executes(ctx -> showRanges(ctx, false))))));
    }

    private static int add(CommandContext<CommandSourceStack> ctx, double strength) {
        Vec3 center = Vec3Argument.getVec3(ctx, "center");
        double radius = DoubleArgumentType.getDouble(ctx, "radius");
        double range = DoubleArgumentType.getDouble(ctx, "range");
        if (range < radius) {
            ctx.getSource().sendFailure(Component.literal("Range must be at least the radius."));
            return 0;
        }
        return addField(ctx, new GravityField(center, radius, range, strength));
    }

    /** A cube planet from two opposite corner blocks, so its center is exactly right. */
    private static int addCube(CommandContext<CommandSourceStack> ctx, double strength)
            throws com.mojang.brigadier.exceptions.CommandSyntaxException {
        BlockPos from = BlockPosArgument.getLoadedBlockPos(ctx, "from");
        BlockPos to = BlockPosArgument.getLoadedBlockPos(ctx, "to");
        double sizeX = Math.abs(from.getX() - to.getX()) + 1;
        double sizeY = Math.abs(from.getY() - to.getY()) + 1;
        double sizeZ = Math.abs(from.getZ() - to.getZ()) + 1;
        Vec3 center = new Vec3(
                Math.min(from.getX(), to.getX()) + sizeX / 2.0,
                Math.min(from.getY(), to.getY()) + sizeY / 2.0,
                Math.min(from.getZ(), to.getZ()) + sizeZ / 2.0);
        double halfSize = Math.max(sizeX, Math.max(sizeY, sizeZ)) / 2.0;
        double range = DoubleArgumentType.getDouble(ctx, "range");
        if (range < halfSize) {
            ctx.getSource().sendFailure(Component.literal("Range must be at least half the cube's size."));
            return 0;
        }
        if (sizeX != sizeY || sizeY != sizeZ) {
            ctx.getSource().sendSystemMessage(Component.literal(String.format(
                    "Warning: that box is %.0f x %.0f x %.0f. Gravity treats it as a %.0f cube, so use equal sides.",
                    sizeX, sizeY, sizeZ, halfSize * 2)));
        }
        return addField(ctx, new GravityField(center, halfSize, range, strength, GravityField.Shape.CUBE));
    }

    private static int addField(CommandContext<CommandSourceStack> ctx, GravityField field) {
        ServerLevel level = ctx.getSource().getLevel();
        List<GravityField> existing = PlanetGravityData.get(level).fields();
        for (int i = 0; i < existing.size(); i++) {
            GravityField other = existing.get(i);
            if (other.center().distanceTo(field.center()) < other.radius() + field.radius()) {
                int number = i + 1;
                ctx.getSource().sendSystemMessage(Component.literal(
                        "Warning: this overlaps planet " + number + ". Remove one with /galaxy planet remove "
                                + number + " or start over with /galaxy planet clear."));
            }
        }
        PlanetGravityData.get(level).add(field);
        PlanetSync.sendToDimension(level);
        PlanetMarkers.refresh(level);
        Vec3 c = field.center();
        ctx.getSource().sendSuccess(() -> Component.literal(String.format(
                "Added a %s planet at %.1f %.1f %.1f (%s %.1f, range %.0f, strength %.2f)",
                field.shape().getSerializedName(), c.x, c.y, c.z,
                field.shape() == GravityField.Shape.CUBE ? "half size" : "radius",
                field.radius(), field.range(), field.strength())), true);
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
                    "%d: %s, center %.1f %.1f %.1f, radius %.1f, range %.0f, strength %.2f",
                    number, f.shape().getSerializedName(), f.center().x, f.center().y, f.center().z, f.radius(), f.range(), f.strength())), false);
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
        PlanetMarkers.refresh(level);
        ctx.getSource().sendSuccess(() -> Component.literal("Removed planet " + number), true);
        return 1;
    }

    private static int clear(CommandContext<CommandSourceStack> ctx) {
        ServerLevel level = ctx.getSource().getLevel();
        PlanetGravityData.get(level).clear();
        PlanetSync.sendToDimension(level);
        PlanetMarkers.refresh(level);
        ctx.getSource().sendSuccess(() -> Component.literal("Removed every planet in this dimension"), true);
        return 1;
    }

    private static int showCenters(CommandContext<CommandSourceStack> ctx, boolean show) {
        ServerLevel level = ctx.getSource().getLevel();
        PlanetGravityData.get(level).setShowCenters(show);
        PlanetMarkers.refresh(level);
        ctx.getSource().sendSuccess(() -> Component.literal(show
                ? "Showing planet centers (gold = sphere, diamond = cube)"
                : "Hiding planet centers"), true);
        return 1;
    }

    private static int showRanges(CommandContext<CommandSourceStack> ctx, boolean show) {
        ServerLevel level = ctx.getSource().getLevel();
        PlanetGravityData.get(level).setShowRanges(show);
        PlanetSync.sendToDimension(level);
        ctx.getSource().sendSuccess(() -> Component.literal(show
                ? "Showing each planet's gravity range as a blue bubble"
                : "Hiding gravity ranges"), true);
        return 1;
    }
}
