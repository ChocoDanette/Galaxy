package io.github.chocodanette.galaxy.gravity;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

import net.minecraft.world.phys.Vec3;

class HollowTunnelTest {
    private final GravityField hollow = new GravityField(Vec3.ZERO, 20, 40, 1, GravityField.Shape.HOLLOW);

    @Test
    void middleOfTheTunnelCounts() {
        assertTrue(hollow.inHollowTunnel(new Vec3(0.5, 17, 0)));
        assertTrue(hollow.inHollowTunnel(new Vec3(-1, -16, 1)));
    }

    @Test
    void shellInsideAndOtherShapesDoNot() {
        assertFalse(hollow.inHollowTunnel(new Vec3(10, 0, 0)));
        assertFalse(hollow.inHollowTunnel(new Vec3(0, 30, 0)));
        assertFalse(hollow.inHollowTunnel(new Vec3(0, 5, 0)));
        assertFalse(new GravityField(Vec3.ZERO, 20, 40, 1).inHollowTunnel(new Vec3(0, 17, 0)));
    }

    @Test
    void pullFlipsAcrossTheMiddleLine() {
        // This is the flip the tunnel guard in GravityFrames holds back.
        Vec3 left = hollow.pullPoint(new Vec3(-0.2, 17, 0)).subtract(new Vec3(-0.2, 17, 0));
        Vec3 right = hollow.pullPoint(new Vec3(0.2, 17, 0)).subtract(new Vec3(0.2, 17, 0));
        assertTrue(left.normalize().dot(right.normalize()) < -0.9);
    }
}
