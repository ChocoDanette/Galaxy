package io.github.chocodanette.galaxy.ship.crew;

import java.util.Locale;

import com.mojang.serialization.Codec;

import net.minecraft.util.StringRepresentable;

/**
 * What a crew member may do on their crew's station.
 * <ul>
 * <li>Captain: everything, plus inviting, kicking and changing roles.</li>
 * <li>Crew: build, break, and open chests.</li>
 * <li>Guest: walk around and use crafting tables and doors; no chests, no building.</li>
 * </ul>
 */
public enum CrewRole implements StringRepresentable {
    GUEST,
    CREW,
    CAPTAIN;

    public static final Codec<CrewRole> CODEC = StringRepresentable.fromEnum(CrewRole::values);

    @Override
    public String getSerializedName() {
        return name().toLowerCase(Locale.ROOT);
    }

    /** True when this role can build, break and open chests. */
    public boolean canBuild() {
        return this != GUEST;
    }

    public boolean isCaptain() {
        return this == CAPTAIN;
    }
}
