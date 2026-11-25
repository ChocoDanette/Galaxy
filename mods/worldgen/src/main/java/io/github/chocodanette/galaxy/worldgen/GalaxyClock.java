package io.github.chocodanette.galaxy.worldgen;

import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.function.Consumer;

import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerBossEvent;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.RandomSource;
import net.minecraft.world.BossEvent;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.event.server.ServerStartedEvent;
import net.neoforged.neoforge.event.server.ServerStoppedEvent;
import net.neoforged.neoforge.event.tick.PlayerTickEvent;
import net.neoforged.neoforge.event.tick.ServerTickEvent;

import io.github.chocodanette.galaxy.gravity.PlanetGravityData;
import io.github.chocodanette.galaxy.gravity.PlanetSync;

/**
 * Runs the galaxy's clock ({@link GalaxyCycle}) on the server: counts down while players are
 * online, shows the countdown bar to everyone, warns as the end gets near, makes the galaxy
 * rumble in its last minutes, and then collapses it.
 *
 * When a galaxy collapses, everyone in it is brought home with what they carry (to the
 * station when the Galaxy Station mod is there, else to the overworld spawn). Anything left
 * in the galaxy is lost. A new galaxy takes its place straight away. Players who were
 * offline in the old galaxy are brought home when they next log in.
 */
public final class GalaxyClock {
    /** Saved on each player: the number of the galaxy they were last in. */
    private static final String PLAYER_GALAXY = GalaxyWorldgen.MODID + ".galaxy";
    private static final long SECOND = 20;
    private static final long MINUTE = 60 * SECOND;
    /** Chat warnings, at these ticks left. */
    private static final long[] WARNINGS = {
            60 * MINUTE, 30 * MINUTE, 10 * MINUTE, 5 * MINUTE, MINUTE, 30 * SECOND};
    /** From here on the galaxy rumbles and sparks around everyone inside. */
    private static final long RUMBLE = 5 * MINUTE;
    /** From here on it goes dark in pulses. */
    private static final long BLACKOUT = MINUTE;

    private static ServerBossEvent bar;
    private static Consumer<ServerPlayer> recall = GalaxyClock::toOverworldSpawn;

    private GalaxyClock() {
    }

    static void register() {
        NeoForge.EVENT_BUS.addListener(GalaxyClock::onServerStarted);
        NeoForge.EVENT_BUS.addListener(GalaxyClock::onServerStopped);
        NeoForge.EVENT_BUS.addListener(GalaxyClock::onServerTick);
        NeoForge.EVENT_BUS.addListener(GalaxyClock::onPlayerTick);
        NeoForge.EVENT_BUS.addListener(GalaxyClock::onChangedDimension);
        NeoForge.EVENT_BUS.addListener(GalaxyClock::onLogout);
    }

    /** Where players go when their galaxy collapses. The Galaxy Station mod sends them to the station. */
    public static void setRecall(Consumer<ServerPlayer> whereTo) {
        recall = whereTo;
    }

    // ---------------------------------------------------------------- start and stop

    private static void onServerStarted(ServerStartedEvent event) {
        MinecraftServer server = event.getServer();
        GalaxyCycle cycle = GalaxyCycle.get(server);
        cycle.publish();
        RandomSource random = server.overworld().getRandom();
        if (cycle.layoutOutdated()) {
            if (GalaxyWipe.hasFiles(server, GalaxyCycle.slot())) {
                // Built by an older version of the mod: replace it with one built the new way.
                cycle.advance(random);
                resetGravity(server);
                GalaxyWorldgen.LOGGER.info("The galaxy was built by an older version; starting galaxy {}",
                        GalaxyCycle.number());
            } else {
                // A new world: nothing was built yet, so galaxy 1 can be built the new way.
                cycle.markLayoutCurrent();
            }
        }
        if (cycle.ticksLeft() < 0) {
            cycle.startClock(random);
        }
        GalaxyWipe.wipe(server, cycle);
        GalaxyCycle.fireNewGalaxy(server);
        GalaxyWorldgen.LOGGER.info("Galaxy {} (slot {}) collapses in {}", GalaxyCycle.number(), GalaxyCycle.slot(),
                duration(cycle.ticksLeft()));
    }

    private static void onServerStopped(ServerStoppedEvent event) {
        if (bar != null) {
            bar.removeAllPlayers();
            bar = null;
        }
        GalaxyCycle.forget();
    }

    // ---------------------------------------------------------------- the clock

    private static void onServerTick(ServerTickEvent.Post event) {
        MinecraftServer server = event.getServer();
        List<ServerPlayer> online = server.getPlayerList().getPlayers();
        if (online.isEmpty()) {
            return;
        }
        GalaxyCycle cycle = GalaxyCycle.get(server);
        if (cycle.ticksLeft() < 0) {
            return;
        }
        cycle.tick();
        long left = cycle.ticksLeft();
        if (left == 0) {
            collapse(server);
            return;
        }
        for (long warning : WARNINGS) {
            if (left == warning) {
                server.getPlayerList().broadcastSystemMessage(Component.translatable(
                        "galaxy_worldgen.clock.warning", GalaxyCycle.number(), duration(left)), false);
            }
        }
        if (left <= 10 * SECOND && left % SECOND == 0) {
            server.getPlayerList().broadcastSystemMessage(Component.translatable(
                    "galaxy_worldgen.clock.final", left / SECOND), true);
        }
        if (left % SECOND == 0) {
            updateBar(server, cycle);
        }
        ServerLevel galaxy = server.getLevel(PlanetGravityLink.GALAXY);
        if (galaxy != null && left <= RUMBLE) {
            rumble(galaxy, left);
        }
    }

    /** The countdown bar at the top of the screen, for everyone online. */
    private static void updateBar(MinecraftServer server, GalaxyCycle cycle) {
        if (bar == null) {
            bar = new ServerBossEvent(UUID.nameUUIDFromBytes("galaxy_worldgen:clock".getBytes()),
                    Component.empty(), BossEvent.BossBarColor.PURPLE, BossEvent.BossBarOverlay.NOTCHED_10);
        }
        long left = cycle.ticksLeft();
        bar.setName(Component.translatable("galaxy_worldgen.clock.bar", GalaxyCycle.number(), duration(left)));
        bar.setProgress((float) Math.clamp(left / (double) Math.max(1, cycle.life()), 0.0, 1.0));
        bar.setColor(left > 30 * MINUTE ? BossEvent.BossBarColor.PURPLE
                : left > 5 * MINUTE ? BossEvent.BossBarColor.YELLOW : BossEvent.BossBarColor.RED);
        Set<ServerPlayer> online = new HashSet<>(server.getPlayerList().getPlayers());
        for (ServerPlayer player : List.copyOf(bar.getPlayers())) {
            if (!online.contains(player)) {
                bar.removePlayer(player);
            }
        }
        for (ServerPlayer player : online) {
            bar.addPlayer(player);
        }
    }

    /** The galaxy coming apart: rumbling, sparks, and in the last minute, darkness. */
    private static void rumble(ServerLevel galaxy, long left) {
        long every = left <= BLACKOUT ? 5 * SECOND : 10 * SECOND;
        if (left % every != 0) {
            return;
        }
        for (ServerPlayer player : galaxy.players()) {
            Vec3 p = player.position();
            galaxy.playSound(null, p.x, p.y, p.z, SoundEvents.WARDEN_HEARTBEAT, SoundSource.AMBIENT,
                    left <= BLACKOUT ? 2.0F : 1.0F, 0.6F);
            galaxy.sendParticles(ParticleTypes.REVERSE_PORTAL, p.x, p.y + 1.0, p.z, 60, 6.0, 4.0, 6.0, 0.05);
            if (left <= BLACKOUT) {
                player.addEffect(new MobEffectInstance(MobEffects.DARKNESS, 60, 0, false, false));
            }
        }
    }

    // ---------------------------------------------------------------- collapse

    /** Ends the galaxy now: everyone inside goes home, and a new galaxy is born. */
    public static void collapse(MinecraftServer server) {
        int old = GalaxyCycle.number();
        ServerLevel galaxy = server.getLevel(PlanetGravityLink.GALAXY);
        if (galaxy != null) {
            for (ServerPlayer player : List.copyOf(galaxy.players())) {
                bringHome(player, Component.translatable("galaxy_worldgen.clock.recalled", old));
            }
        }
        GalaxyCycle cycle = GalaxyCycle.get(server);
        cycle.advance(server.overworld().getRandom());
        resetGravity(server);
        GalaxyCycle.fireNewGalaxy(server);
        Galaxy fresh = Galaxy.current(server.overworld().getSeed());
        server.getPlayerList().broadcastSystemMessage(Component.translatable("galaxy_worldgen.clock.collapsed",
                old, fresh.number(), GalaxyCommands.describe(fresh), duration(cycle.ticksLeft())), false);
        if (bar != null) {
            updateBar(server, cycle);
        }
        GalaxyWorldgen.LOGGER.info("Galaxy {} collapsed; galaxy {} (slot {}) lasts {}", old, fresh.number(),
                fresh.slot(), duration(cycle.ticksLeft()));
    }

    /** Sets the time left; for operators testing the clock. */
    public static void setTimeLeft(MinecraftServer server, long ticks) {
        GalaxyCycle cycle = GalaxyCycle.get(server);
        cycle.setTicksLeft(ticks);
        if (bar != null) {
            updateBar(server, cycle);
        }
    }

    /** The old galaxy's planets have no business pulling anything any more. */
    private static void resetGravity(MinecraftServer server) {
        ServerLevel galaxy = server.getLevel(PlanetGravityLink.GALAXY);
        if (galaxy != null) {
            PlanetGravityData.get(galaxy).reset();
            PlanetSync.sendToDimension(galaxy);
        }
    }

    private static void bringHome(ServerPlayer player, Component message) {
        player.removeEffect(MobEffects.DARKNESS);
        recall.accept(player);
        player.resetFallDistance();
        player.sendSystemMessage(message);
    }

    private static void toOverworldSpawn(ServerPlayer player) {
        MinecraftServer server = player.level().getServer();
        ServerLevel overworld = server.overworld();
        BlockPos spawn = server.getRespawnData().pos();
        int y = overworld.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, spawn.getX(), spawn.getZ());
        player.teleportTo(overworld, spawn.getX() + 0.5, y, spawn.getZ() + 0.5, Set.of(), player.getYRot(), 0.0F, true);
    }

    // ---------------------------------------------------------------- players

    /** Notes which galaxy a player is in, so an old one can be told apart after a collapse. */
    public static void markInCurrentGalaxy(ServerPlayer player) {
        player.getPersistentData().putInt(PLAYER_GALAXY, GalaxyCycle.number());
    }

    private static void onChangedDimension(PlayerEvent.PlayerChangedDimensionEvent event) {
        if (event.getTo().equals(PlanetGravityLink.GALAXY) && event.getEntity() instanceof ServerPlayer player) {
            markInCurrentGalaxy(player);
        }
    }

    /** Someone still in a galaxy that collapsed while they were offline goes home. */
    private static void onPlayerTick(PlayerTickEvent.Post event) {
        if (!(event.getEntity() instanceof ServerPlayer player) || player.tickCount % SECOND != 0
                || !player.level().dimension().equals(PlanetGravityLink.GALAXY)) {
            return;
        }
        int was = player.getPersistentData().getIntOr(PLAYER_GALAXY, 0);
        boolean inCurrent = Galaxy.numberAt(player.getX()) != 0;
        if (was == 0 && inCurrent) {
            markInCurrentGalaxy(player);
        } else if (was != GalaxyCycle.number() || !inCurrent) {
            bringHome(player, Component.translatable("galaxy_worldgen.clock.collapsed_away", Math.max(1, was)));
        }
    }

    private static void onLogout(PlayerEvent.PlayerLoggedOutEvent event) {
        if (bar != null && event.getEntity() instanceof ServerPlayer player) {
            bar.removePlayer(player);
        }
    }

    /** "3h 12m", "14m 05s", "42s". */
    public static String duration(long ticks) {
        long seconds = Math.max(0, ticks) / SECOND;
        long h = seconds / 3600;
        long m = seconds / 60 % 60;
        long s = seconds % 60;
        if (h > 0) {
            return h + "h " + m + "m";
        }
        if (m > 0) {
            return String.format("%dm %02ds", m, s);
        }
        return s + "s";
    }
}
