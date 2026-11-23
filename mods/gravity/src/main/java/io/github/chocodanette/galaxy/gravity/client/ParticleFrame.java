package io.github.chocodanette.galaxy.gravity.client;

import org.joml.Quaternionf;
import org.jspecify.annotations.Nullable;

/**
 * Added to every particle by ParticleMixin. A particle near a planet keeps its speed in its
 * own tilted frame, where "up" points away from the planet; this turns it into world movement.
 */
public interface ParticleFrame {
    /** Rotation from the particle's frame to the world, or null when it's upright. */
    @Nullable Quaternionf galaxy$frame();

    /** Moves the particle without collisions, by a step given in its own frame. */
    void galaxy$moveFree(double xa, double ya, double za);
}
