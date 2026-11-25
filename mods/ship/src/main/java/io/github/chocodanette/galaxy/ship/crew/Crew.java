package io.github.chocodanette.galaxy.ship.crew;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;

import net.minecraft.core.UUIDUtil;

/**
 * A crew: one or more players who share one station. A player playing alone is a crew of one.
 * Members are kept by account id (so renaming your Minecraft account changes nothing), with
 * their last known name for commands and messages.
 */
public final class Crew {
    /** One member: who they are and what they may do. */
    public static final class Member {
        public static final Codec<Member> CODEC = RecordCodecBuilder.create(i -> i.group(
                UUIDUtil.CODEC.fieldOf("id").forGetter(m -> m.id),
                Codec.STRING.fieldOf("name").forGetter(m -> m.name),
                CrewRole.CODEC.fieldOf("role").forGetter(m -> m.role)
        ).apply(i, Member::new));

        public final UUID id;
        public String name;
        public CrewRole role;

        public Member(UUID id, String name, CrewRole role) {
            this.id = id;
            this.name = name;
            this.role = role;
        }
    }

    /** Plot number when the crew has no station yet. */
    public static final int NO_PLOT = 0;

    public static final Codec<Crew> CODEC = RecordCodecBuilder.create(i -> i.group(
            Codec.INT.fieldOf("id").forGetter(c -> c.id),
            Codec.STRING.fieldOf("name").forGetter(c -> c.name),
            Member.CODEC.listOf().fieldOf("members").forGetter(c -> List.copyOf(c.members.values())),
            Codec.INT.optionalFieldOf("plot", NO_PLOT).forGetter(c -> c.plot),
            Codec.INT.optionalFieldOf("build_radius", 0).forGetter(c -> c.buildRadius)
    ).apply(i, Crew::new));

    public final int id;
    public String name;
    private final Map<UUID, Member> members = new LinkedHashMap<>();
    /** Which plot the crew's station is on (1, 2, 3...), or {@link #NO_PLOT}. */
    public int plot;
    /** How far from the middle of the station the crew may build, sideways. */
    public int buildRadius;

    public Crew(int id, String name, List<Member> members, int plot, int buildRadius) {
        this.id = id;
        this.name = name;
        for (Member member : members) {
            this.members.put(member.id, member);
        }
        this.plot = plot;
        this.buildRadius = buildRadius;
    }

    public boolean hasStation() {
        return plot != NO_PLOT;
    }

    public Optional<Member> member(UUID player) {
        return Optional.ofNullable(members.get(player));
    }

    /** The player's role here, or empty if they aren't in this crew. */
    public Optional<CrewRole> role(UUID player) {
        return member(player).map(m -> m.role);
    }

    public List<Member> members() {
        return new ArrayList<>(members.values());
    }

    public Optional<Member> memberNamed(String name) {
        return members.values().stream().filter(m -> m.name.equalsIgnoreCase(name)).findFirst();
    }

    void add(UUID player, String name, CrewRole role) {
        members.put(player, new Member(player, name, role));
    }

    void remove(UUID player) {
        members.remove(player);
    }

    public int size() {
        return members.size();
    }

    public long captains() {
        return members.values().stream().filter(m -> m.role.isCaptain()).count();
    }
}
