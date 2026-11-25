package io.github.chocodanette.galaxy.ship.crew;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;

import net.minecraft.resources.Identifier;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.level.saveddata.SavedData;
import net.minecraft.world.level.saveddata.SavedDataType;

import io.github.chocodanette.galaxy.ship.GalaxyShip;

/**
 * Every crew and their stations, saved with the world (in the overworld's data, like the
 * common station's own data). Only the server reads and changes it. A player is in at most
 * one crew at a time.
 */
public final class CrewData extends SavedData {
    public static final Codec<CrewData> CODEC = RecordCodecBuilder.create(i -> i.group(
            Crew.CODEC.listOf().optionalFieldOf("crews", List.of()).forGetter(d -> List.copyOf(d.crews.values())),
            Codec.INT.optionalFieldOf("next_crew", 1).forGetter(d -> d.nextCrew),
            Codec.INT.optionalFieldOf("next_plot", 1).forGetter(d -> d.nextPlot)
    ).apply(i, CrewData::new));

    public static final SavedDataType<CrewData> TYPE = new SavedDataType<>(
            Identifier.fromNamespaceAndPath(GalaxyShip.MODID, "crews"), CrewData::new, CODEC);

    private final Map<Integer, Crew> crews = new LinkedHashMap<>();
    /** Which crew each player is in (worked out from the crews, not saved separately). */
    private final Map<UUID, Integer> crewOf = new HashMap<>();
    private int nextCrew;
    /** Plots are handed out in order and never reused, so an old station is never built over. */
    private int nextPlot;

    public CrewData() {
        this(List.of(), 1, 1);
    }

    private CrewData(List<Crew> crews, int nextCrew, int nextPlot) {
        for (Crew crew : crews) {
            this.crews.put(crew.id, crew);
            for (Crew.Member member : crew.members()) {
                crewOf.put(member.id, crew.id);
            }
        }
        this.nextCrew = nextCrew;
        this.nextPlot = nextPlot;
    }

    public static CrewData get(MinecraftServer server) {
        return server.overworld().getDataStorage().computeIfAbsent(TYPE);
    }

    public Optional<Crew> crewOf(UUID player) {
        Integer id = crewOf.get(player);
        return id == null ? Optional.empty() : Optional.ofNullable(crews.get(id));
    }

    public Optional<Crew> crewNamed(String name) {
        return crews.values().stream().filter(c -> c.name.equalsIgnoreCase(name)).findFirst();
    }

    /** The crew whose station is on this plot. */
    public Optional<Crew> crewOnPlot(int plot) {
        if (plot == Crew.NO_PLOT) {
            return Optional.empty();
        }
        return crews.values().stream().filter(c -> c.plot == plot).findFirst();
    }

    public List<Crew> crews() {
        return new ArrayList<>(crews.values());
    }

    public long stationCount() {
        return crews.values().stream().filter(Crew::hasStation).count();
    }

    /** Starts a new crew with this player as its only member and captain. */
    public Crew create(UUID captain, String captainName, String name) {
        Crew crew = new Crew(nextCrew++, name, List.of(), Crew.NO_PLOT, 0);
        crew.add(captain, captainName, CrewRole.CAPTAIN);
        crews.put(crew.id, crew);
        crewOf.put(captain, crew.id);
        setDirty();
        return crew;
    }

    public void join(Crew crew, UUID player, String name, CrewRole role) {
        crew.add(player, name, role);
        crewOf.put(player, crew.id);
        setDirty();
    }

    public void leave(Crew crew, UUID player) {
        crew.remove(player);
        crewOf.remove(player);
        if (crew.size() == 0) {
            crews.remove(crew.id);
        }
        setDirty();
    }

    /** Removes a crew entirely; its members are left without a crew. */
    public void disband(Crew crew) {
        for (Crew.Member member : crew.members()) {
            crewOf.remove(member.id);
        }
        crews.remove(crew.id);
        setDirty();
    }

    /** Gives the crew the next free plot. */
    public int claimPlot(Crew crew, int buildRadius) {
        crew.plot = nextPlot++;
        crew.buildRadius = buildRadius;
        setDirty();
        return crew.plot;
    }

    /** Call after changing a crew's fields directly (name, roles, build radius). */
    public void changed() {
        setDirty();
    }
}
