package io.github.chocodanette.galaxy.ship;

import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import com.mojang.brigadier.suggestion.SuggestionProvider;

import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.SharedSuggestionProvider;
import net.minecraft.commands.arguments.EntityArgument;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.RegisterCommandsEvent;

import io.github.chocodanette.galaxy.ship.crew.Crew;
import io.github.chocodanette.galaxy.ship.crew.CrewData;
import io.github.chocodanette.galaxy.ship.crew.CrewRole;

/**
 * Crew commands (anyone):
 * <pre>
 * /crew                          your crew, its members and their roles
 * /crew create &lt;name&gt;            start a crew (you're its captain)
 * /crew invite &lt;player&gt;          invite someone (captains)
 * /crew accept                   join the crew that invited you
 * /crew leave                    leave your crew
 * /crew kick &lt;name&gt;              remove someone from your crew (captains)
 * /crew role &lt;name&gt; &lt;role&gt;       make someone captain, crew or guest (captains)
 * /crew rename &lt;name&gt;            rename your crew (captains)
 * </pre>
 * Station commands (operators):
 * <pre>
 * /stations list                 every crew and its station
 * /stations visit &lt;crew&gt;          go to a crew's station
 * /stations size &lt;crew&gt; &lt;radius&gt;  change how big a crew's building area is
 * /stations remove &lt;crew&gt;         delete a crew and empty its station
 * </pre>
 */
public final class CrewCommands {
    /** Open invites: who was invited, to which crew. Forgotten when the server restarts. */
    private static final Map<UUID, Integer> INVITES = new ConcurrentHashMap<>();
    private static final int MAX_NAME = 32;

    private CrewCommands() {
    }

    static void register() {
        NeoForge.EVENT_BUS.addListener(CrewCommands::onRegisterCommands);
    }

    private static final SuggestionProvider<CommandSourceStack> MEMBERS = (ctx, builder) -> {
        ServerPlayer player = ctx.getSource().getPlayer();
        if (player == null) {
            return builder.buildFuture();
        }
        return SharedSuggestionProvider.suggest(CrewData.get(ctx.getSource().getServer()).crewOf(player.getUUID())
                .map(c -> c.members().stream().map(m -> m.name).toList()).orElse(java.util.List.of()), builder);
    };

    private static final SuggestionProvider<CommandSourceStack> CREWS = (ctx, builder) ->
            SharedSuggestionProvider.suggest(CrewData.get(ctx.getSource().getServer()).crews().stream()
                    .map(c -> c.name.contains(" ") ? "\"" + c.name + "\"" : c.name), builder);

    private static void onRegisterCommands(RegisterCommandsEvent event) {
        event.getDispatcher().register(Commands.literal("crew")
                .executes(CrewCommands::info)
                .then(Commands.literal("create")
                        .then(Commands.argument("name", StringArgumentType.greedyString())
                                .executes(ctx -> create(ctx, StringArgumentType.getString(ctx, "name")))))
                .then(Commands.literal("invite")
                        .then(Commands.argument("player", EntityArgument.player())
                                .executes(ctx -> invite(ctx, EntityArgument.getPlayer(ctx, "player")))))
                .then(Commands.literal("accept").executes(CrewCommands::accept))
                .then(Commands.literal("leave").executes(ctx -> leave(ctx, false))
                        .then(Commands.literal("confirm").executes(ctx -> leave(ctx, true))))
                .then(Commands.literal("kick")
                        .then(Commands.argument("name", StringArgumentType.word()).suggests(MEMBERS)
                                .executes(ctx -> kick(ctx, StringArgumentType.getString(ctx, "name")))))
                .then(Commands.literal("role")
                        .then(Commands.argument("name", StringArgumentType.word()).suggests(MEMBERS)
                                .then(Commands.argument("role", StringArgumentType.word())
                                        .suggests((ctx, b) -> SharedSuggestionProvider.suggest(
                                                new String[] {"captain", "crew", "guest"}, b))
                                        .executes(ctx -> role(ctx, StringArgumentType.getString(ctx, "name"),
                                                StringArgumentType.getString(ctx, "role"))))))
                .then(Commands.literal("rename")
                        .then(Commands.argument("name", StringArgumentType.greedyString())
                                .executes(ctx -> rename(ctx, StringArgumentType.getString(ctx, "name"))))));

        event.getDispatcher().register(Commands.literal("stations")
                .requires(Commands.hasPermission(Commands.LEVEL_GAMEMASTERS))
                .then(Commands.literal("list").executes(CrewCommands::list))
                .then(Commands.literal("visit")
                        .then(Commands.argument("crew", StringArgumentType.string()).suggests(CREWS)
                                .executes(ctx -> visit(ctx, StringArgumentType.getString(ctx, "crew")))))
                .then(Commands.literal("size")
                        .then(Commands.argument("crew", StringArgumentType.string()).suggests(CREWS)
                                .then(Commands.argument("radius", IntegerArgumentType.integer(4, StationPlots.SPACING / 2 - 50))
                                        .executes(ctx -> size(ctx, StringArgumentType.getString(ctx, "crew"),
                                                IntegerArgumentType.getInteger(ctx, "radius"))))))
                .then(Commands.literal("remove")
                        .then(Commands.argument("crew", StringArgumentType.string()).suggests(CREWS)
                                .executes(ctx -> remove(ctx, StringArgumentType.getString(ctx, "crew"))))));
    }

    // ---- /crew ----

    private static int info(CommandContext<CommandSourceStack> ctx) throws CommandSyntaxException {
        ServerPlayer player = ctx.getSource().getPlayerOrException();
        Optional<Crew> crew = CrewData.get(ctx.getSource().getServer()).crewOf(player.getUUID());
        if (crew.isEmpty()) {
            reply(ctx, Component.translatable("galaxy_ship.crew.none"));
            return 0;
        }
        Crew c = crew.get();
        String members = c.members().stream().map(m -> m.name + " (" + m.role.getSerializedName() + ")")
                .collect(Collectors.joining(", "));
        reply(ctx, Component.translatable("galaxy_ship.crew.info", c.name, members));
        reply(ctx, Component.translatable(c.hasStation() ? "galaxy_ship.crew.has_station" : "galaxy_ship.crew.no_station"));
        return 1;
    }

    private static int create(CommandContext<CommandSourceStack> ctx, String name) throws CommandSyntaxException {
        ServerPlayer player = ctx.getSource().getPlayerOrException();
        CrewData data = CrewData.get(ctx.getSource().getServer());
        if (data.crewOf(player.getUUID()).isPresent()) {
            return fail(ctx, "galaxy_ship.crew.already_in");
        }
        Optional<String> bad = checkName(data, name.trim());
        if (bad.isPresent()) {
            return fail(ctx, bad.get());
        }
        Crew crew = data.create(player.getUUID(), player.getGameProfile().name(), name.trim());
        reply(ctx, Component.translatable("galaxy_ship.crew.created", crew.name));
        return 1;
    }

    private static int invite(CommandContext<CommandSourceStack> ctx, ServerPlayer target) throws CommandSyntaxException {
        ServerPlayer player = ctx.getSource().getPlayerOrException();
        CrewData data = CrewData.get(ctx.getSource().getServer());
        Optional<Crew> crew = captainsCrew(ctx, player);
        if (crew.isEmpty()) {
            return 0;
        }
        if (data.crewOf(target.getUUID()).isPresent()) {
            return fail(ctx, "galaxy_ship.crew.target_in_crew", target.getGameProfile().name());
        }
        INVITES.put(target.getUUID(), crew.get().id);
        target.sendSystemMessage(Component.translatable("galaxy_ship.crew.invited_you",
                player.getGameProfile().name(), crew.get().name), false);
        reply(ctx, Component.translatable("galaxy_ship.crew.invite_sent", target.getGameProfile().name()));
        return 1;
    }

    private static int accept(CommandContext<CommandSourceStack> ctx) throws CommandSyntaxException {
        ServerPlayer player = ctx.getSource().getPlayerOrException();
        CrewData data = CrewData.get(ctx.getSource().getServer());
        Integer id = INVITES.remove(player.getUUID());
        Optional<Crew> crew = id == null ? Optional.empty()
                : data.crews().stream().filter(c -> c.id == id).findFirst();
        if (crew.isEmpty()) {
            return fail(ctx, "galaxy_ship.crew.no_invite");
        }
        if (data.crewOf(player.getUUID()).isPresent()) {
            return fail(ctx, "galaxy_ship.crew.already_in");
        }
        data.join(crew.get(), player.getUUID(), player.getGameProfile().name(), CrewRole.CREW);
        StationCrew.makeStationHome(player);
        tellCrew(ctx.getSource().getServer(), crew.get(), Component.translatable("galaxy_ship.crew.joined",
                player.getGameProfile().name(), crew.get().name));
        return 1;
    }

    private static int leave(CommandContext<CommandSourceStack> ctx, boolean confirmed) throws CommandSyntaxException {
        ServerPlayer player = ctx.getSource().getPlayerOrException();
        CrewData data = CrewData.get(ctx.getSource().getServer());
        Optional<Crew> found = data.crewOf(player.getUUID());
        if (found.isEmpty()) {
            return fail(ctx, "galaxy_ship.crew.none");
        }
        Crew crew = found.get();
        boolean captain = crew.role(player.getUUID()).map(CrewRole::isCaptain).orElse(false);
        if (captain && crew.captains() == 1 && crew.size() > 1) {
            return fail(ctx, "galaxy_ship.crew.last_captain");
        }
        if (crew.size() == 1 && crew.hasStation() && !confirmed) {
            return fail(ctx, "galaxy_ship.crew.leave_confirm");
        }
        String name = crew.name;
        data.leave(crew, player.getUUID());
        sendAway(player, crew);
        reply(ctx, Component.translatable("galaxy_ship.crew.left", name));
        tellCrew(ctx.getSource().getServer(), crew, Component.translatable("galaxy_ship.crew.member_left",
                player.getGameProfile().name()));
        return 1;
    }

    private static int kick(CommandContext<CommandSourceStack> ctx, String name) throws CommandSyntaxException {
        ServerPlayer player = ctx.getSource().getPlayerOrException();
        Optional<Crew> crew = captainsCrew(ctx, player);
        if (crew.isEmpty()) {
            return 0;
        }
        Optional<Crew.Member> member = crew.get().memberNamed(name);
        if (member.isEmpty()) {
            return fail(ctx, "galaxy_ship.crew.not_member", name);
        }
        if (member.get().id.equals(player.getUUID())) {
            return fail(ctx, "galaxy_ship.crew.kick_self");
        }
        MinecraftServer server = ctx.getSource().getServer();
        CrewData.get(server).leave(crew.get(), member.get().id);
        ServerPlayer kicked = server.getPlayerList().getPlayer(member.get().id);
        if (kicked != null) {
            sendAway(kicked, crew.get());
            kicked.sendSystemMessage(Component.translatable("galaxy_ship.crew.kicked_you", crew.get().name), false);
        }
        tellCrew(server, crew.get(), Component.translatable("galaxy_ship.crew.member_kicked", member.get().name));
        return 1;
    }

    private static int role(CommandContext<CommandSourceStack> ctx, String name, String roleName) throws CommandSyntaxException {
        ServerPlayer player = ctx.getSource().getPlayerOrException();
        Optional<Crew> crew = captainsCrew(ctx, player);
        if (crew.isEmpty()) {
            return 0;
        }
        CrewRole role;
        try {
            role = CrewRole.valueOf(roleName.toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException e) {
            return fail(ctx, "galaxy_ship.crew.bad_role", roleName);
        }
        Optional<Crew.Member> member = crew.get().memberNamed(name);
        if (member.isEmpty()) {
            return fail(ctx, "galaxy_ship.crew.not_member", name);
        }
        if (member.get().role.isCaptain() && !role.isCaptain() && crew.get().captains() == 1) {
            return fail(ctx, "galaxy_ship.crew.last_captain");
        }
        member.get().role = role;
        CrewData.get(ctx.getSource().getServer()).changed();
        tellCrew(ctx.getSource().getServer(), crew.get(), Component.translatable("galaxy_ship.crew.role_set",
                member.get().name, role.getSerializedName()));
        return 1;
    }

    private static int rename(CommandContext<CommandSourceStack> ctx, String name) throws CommandSyntaxException {
        ServerPlayer player = ctx.getSource().getPlayerOrException();
        Optional<Crew> crew = captainsCrew(ctx, player);
        if (crew.isEmpty()) {
            return 0;
        }
        CrewData data = CrewData.get(ctx.getSource().getServer());
        Optional<String> bad = checkName(data, name.trim());
        if (bad.isPresent()) {
            return fail(ctx, bad.get());
        }
        crew.get().name = name.trim();
        data.changed();
        tellCrew(ctx.getSource().getServer(), crew.get(), Component.translatable("galaxy_ship.crew.renamed", crew.get().name));
        return 1;
    }

    // ---- /stations (operators) ----

    private static int list(CommandContext<CommandSourceStack> ctx) {
        CrewData data = CrewData.get(ctx.getSource().getServer());
        if (data.crews().isEmpty()) {
            reply(ctx, Component.literal("There are no crews yet."));
            return 0;
        }
        for (Crew crew : data.crews()) {
            String where = crew.hasStation()
                    ? "station " + crew.plot + " at x=" + StationPlots.center(crew.plot).getX() + ", area radius " + crew.buildRadius
                    : "no station";
            reply(ctx, Component.literal(crew.name + ": " + crew.size() + " member(s), " + where));
        }
        return data.crews().size();
    }

    private static Optional<Crew> crewArg(CommandContext<CommandSourceStack> ctx, String name) {
        Optional<Crew> crew = CrewData.get(ctx.getSource().getServer()).crewNamed(name);
        if (crew.isEmpty()) {
            ctx.getSource().sendFailure(Component.literal("No crew is called " + name + "."));
        }
        return crew;
    }

    private static int visit(CommandContext<CommandSourceStack> ctx, String name) throws CommandSyntaxException {
        ServerPlayer player = ctx.getSource().getPlayerOrException();
        Optional<Crew> crew = crewArg(ctx, name);
        if (crew.isEmpty() || !crew.get().hasStation()) {
            return 0;
        }
        var spot = StationPlots.spawn(crew.get().plot);
        ServerLevel station = StationLocation.level(ctx.getSource().getServer());
        player.teleportTo(station, spot.x, spot.y, spot.z, java.util.Set.of(), 0.0F, 0.0F, true);
        return 1;
    }

    private static int size(CommandContext<CommandSourceStack> ctx, String name, int radius) {
        Optional<Crew> crew = crewArg(ctx, name);
        if (crew.isEmpty()) {
            return 0;
        }
        crew.get().buildRadius = radius;
        CrewData.get(ctx.getSource().getServer()).changed();
        ctx.getSource().sendSuccess(() -> Component.literal(crew.get().name + "'s building area is now "
                + (radius * 2 + 1) + " by " + (radius * 2 + 1) + " blocks."), true);
        return 1;
    }

    private static int remove(CommandContext<CommandSourceStack> ctx, String name) {
        MinecraftServer server = ctx.getSource().getServer();
        Optional<Crew> crew = crewArg(ctx, name);
        if (crew.isEmpty()) {
            return 0;
        }
        ServerLevel station = StationLocation.level(server);
        if (crew.get().hasStation() && station != null) {
            // Anyone standing there goes back to the common station first.
            for (ServerPlayer p : station.players()) {
                if (StationPlots.plotAt(p.blockPosition()) == crew.get().plot) {
                    StationCrew.toCommonStation(p);
                }
            }
            StationPlots.clear(station, crew.get());
        }
        CrewData.get(server).disband(crew.get());
        ctx.getSource().sendSuccess(() -> Component.literal("Removed the crew " + crew.get().name
                + (crew.get().hasStation() ? " and emptied its station." : ".")), true);
        return 1;
    }

    // ---- helpers ----

    /** The player's crew if they're one of its captains; otherwise says why not. */
    private static Optional<Crew> captainsCrew(CommandContext<CommandSourceStack> ctx, ServerPlayer player) {
        Optional<Crew> crew = CrewData.get(ctx.getSource().getServer()).crewOf(player.getUUID());
        if (crew.isEmpty()) {
            fail(ctx, "galaxy_ship.crew.none");
            return Optional.empty();
        }
        if (!crew.get().role(player.getUUID()).map(CrewRole::isCaptain).orElse(false)) {
            fail(ctx, "galaxy_ship.crew.captains_only");
            return Optional.empty();
        }
        return crew;
    }

    /** The translation key of what's wrong with this crew name, if anything. */
    private static Optional<String> checkName(CrewData data, String name) {
        if (name.isEmpty() || name.length() > MAX_NAME) {
            return Optional.of("galaxy_ship.crew.bad_name");
        }
        if (data.crewNamed(name).isPresent()) {
            return Optional.of("galaxy_ship.crew.name_taken");
        }
        return Optional.empty();
    }

    /**
     * After leaving a crew: if the player is on that crew's station, they go to the common
     * station, and their respawn point moves off it too.
     */
    private static void sendAway(ServerPlayer player, Crew crew) {
        if (crew.hasStation() && StationLocation.isStation(player.level())
                && StationPlots.plotAt(player.blockPosition()) == crew.plot) {
            StationCrew.toCommonStation(player);
        }
        var respawn = player.getRespawnConfig();
        if (respawn == null || respawn.respawnData().dimension().equals(StationLocation.STATION)
                && StationPlots.plotAt(respawn.respawnData().pos()) == crew.plot) {
            StationCrew.makeStationHome(player);
        }
    }

    private static void tellCrew(MinecraftServer server, Crew crew, Component message) {
        for (Crew.Member member : crew.members()) {
            ServerPlayer online = server.getPlayerList().getPlayer(member.id);
            if (online != null) {
                online.sendSystemMessage(message, false);
            }
        }
    }

    private static void reply(CommandContext<CommandSourceStack> ctx, Component message) {
        ctx.getSource().sendSuccess(() -> message, false);
    }

    private static int fail(CommandContext<CommandSourceStack> ctx, String key, Object... args) {
        ctx.getSource().sendFailure(Component.translatable(key, args));
        return 0;
    }
}
