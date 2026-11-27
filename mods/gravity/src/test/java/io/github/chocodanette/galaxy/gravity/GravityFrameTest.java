package io.github.chocodanette.galaxy.gravity;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

import net.minecraft.world.phys.Vec3;

class GravityFrameTest {
    private static double angleDegrees(Vec3 a, Vec3 b) {
        return Math.toDegrees(Math.acos(Math.max(-1, Math.min(1, a.normalize().dot(b.normalize())))));
    }

    @Test
    void startsUpright() {
        GravityFrame frame = new GravityFrame();
        assertFalse(frame.isTilted());
        assertEquals(1.0, frame.up().y, 1.0E-6);
    }

    @Test
    void turnsGraduallyTowardTarget() {
        GravityFrame frame = new GravityFrame();
        Vec3 east = new Vec3(1, 0, 0);
        frame.tick(east);
        assertTrue(frame.isTilted());
        // It starts gently and speeds up, never past the top speed.
        assertEquals(GravityFrame.TURN_ACCELERATION_DEGREES, angleDegrees(new Vec3(0, 1, 0), frame.up()), 0.01);
        double last = 0;
        for (int i = 0; i < 40; i++) {
            Vec3 before = frame.up();
            frame.tick(east);
            double step = angleDegrees(before, frame.up());
            assertTrue(step <= GravityFrame.MAX_TURN_DEGREES_PER_TICK + 0.01);
            if (i < 3) {
                assertTrue(step > last, "should speed up at first");
            }
            last = step;
        }
        assertEquals(0.0, angleDegrees(east, frame.up()), 0.01);
    }

    @Test
    void localAndWorldRoundTrip() {
        GravityFrame frame = new GravityFrame();
        for (int i = 0; i < 30; i++) {
            frame.tick(new Vec3(1, 1, 0).normalize());
        }
        Vec3 world = new Vec3(0.3, -0.7, 2.0);
        Vec3 back = frame.toWorld(frame.toLocal(world));
        assertEquals(world.x, back.x, 1.0E-5);
        assertEquals(world.y, back.y, 1.0E-5);
        assertEquals(world.z, back.z, 1.0E-5);
        // Local up maps to the frame's up.
        Vec3 up = frame.toWorld(new Vec3(0, 1, 0));
        assertEquals(0.0, angleDegrees(up, frame.up()), 0.01);
    }

    @Test
    void returnsUprightAwayFromPlanets() {
        GravityFrame frame = new GravityFrame();
        for (int i = 0; i < 30; i++) {
            frame.tick(new Vec3(0, -1, 0));
        }
        for (int i = 0; i < 30; i++) {
            frame.tick(new Vec3(0, 1, 0));
        }
        assertFalse(frame.isTilted());
    }

    /** Where an entity looks, in world space, for a yaw in its own frame (level view). */
    private static Vec3 worldLook(GravityFrame frame, float yawDegrees) {
        double yaw = Math.toRadians(yawDegrees);
        return frame.toWorld(new Vec3(-Math.sin(yaw), 0, Math.cos(yaw)));
    }

    private static final Vec3 PLANET = new Vec3(0.5, 100.5, 0.5);

    /** The nearest point of a rounded cube's inner sharp cube (the surface is a fixed distance from it). */
    private static Vec3 corePoint(GravityField field, Vec3 pos) {
        double core = field.radius() - GravityField.roundedBevel(field.radius());
        Vec3 c = field.center();
        return new Vec3(
                Math.max(c.x - core, Math.min(c.x + core, pos.x)),
                Math.max(c.y - core, Math.min(c.y + core, pos.y)),
                Math.max(c.z - core, Math.min(c.z + core, pos.z)));
    }

    /** Walks along the local "forward" over a rounded cube, keeping to its surface. */
    private static Vec3 walk(GravityField field, GravityFrame frame, Vec3 pos, float yaw, int ticks) {
        for (int i = 0; i < ticks; i++) {
            Vec3 up = pos.subtract(corePoint(field, pos)).normalize();
            frame.tick(up);
            frame.settleTwist();
            pos = pos.add(worldLook(frame, yaw).scale(0.25));
            Vec3 newUp = pos.subtract(corePoint(field, pos)).normalize();
            pos = pos.subtract(newUp.scale(field.distanceToSurface(pos)));
        }
        return pos;
    }

    @Test
    void walkingStraightOverAnEdgeKeepsTheHeading() {
        GravityField field = new GravityField(PLANET, 12, 40, 1.0, GravityField.Shape.ROUNDED_CUBE);
        GravityFrame frame = new GravityFrame();
        // Start on the top face, heading east (yaw -90), off-center so the path isn't special.
        Vec3 pos = walk(field, frame, PLANET.add(-3, 12, 4), -90, 90);
        Vec3 up = frame.up();
        assertTrue(up.x > 0.99, "should be on the east face now: " + up);
        Vec3 look = worldLook(frame, -90);
        assertTrue(look.y < -0.99, "should be heading straight down the east face: " + look);
        assertEquals(4.0, pos.z - PLANET.z, 0.3, "drifted sideways");
    }

    @Test
    void comingBackUprightKeepsTheViewWhereItWas() {
        GravityFrame frame = new GravityFrame();
        // Over three edges in a loop (top, east, south, top): the frame comes back upright
        // turned around "up" by a quarter turn.
        Vec3[] ups = {new Vec3(1, 0, 0), new Vec3(0, 0, 1), new Vec3(0, 1, 0)};
        float yaw = 30;
        for (Vec3 target : ups) {
            for (int i = 0; i < 20; i++) {
                Vec3 before = worldLook(frame, yaw);
                frame.tick(target);
                Vec3 lookAfterTurn = worldLook(frame, yaw);
                yaw += frame.settleTwist();
                Vec3 after = worldLook(frame, yaw);
                assertEquals(0.0, angleDegrees(lookAfterTurn, after), 0.01, "settling moved the view");
                assertTrue(angleDegrees(before, after) <= GravityFrame.MAX_TURN_DEGREES_PER_TICK + 0.01,
                        "the view jumped");
            }
        }
        assertFalse(frame.isTilted());
        assertEquals(0.0, angleDegrees(new Vec3(0, 0, 1), frame.toWorld(new Vec3(0, 0, 1))), 0.01,
                "upright frame should carry no turn");
        assertTrue(Math.abs(Math.abs(yaw - 30) - 90) < 0.5, "the loop's quarter turn went into the yaw: " + yaw);
    }

    @Test
    void flippingOverRollsAroundTheWayYouFace() {
        GravityFrame frame = new GravityFrame();
        Vec3 facing = new Vec3(0, 0, 1);
        for (int i = 0; i < 40; i++) {
            frame.tick(new Vec3(0, -1, 0), facing);
        }
        assertTrue(frame.up().y < -0.999, "up was " + frame.up());
        // A roll keeps you facing the same way.
        Vec3 forward = frame.toWorld(new Vec3(0, 0, 1));
        assertTrue(forward.z > 0.999, "forward was " + forward);
    }
}
