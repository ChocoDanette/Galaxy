package io.github.chocodanette.galaxy.gravity;

import java.util.Locale;

import net.minecraft.commands.CommandSourceStack;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Display;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.entity.EntityTypeTest;
import net.minecraft.world.phys.Vec3;

/**
 * Glowing markers at each planet's gravity center, for building and testing planets.
 *
 * A marker is a small glowing block display. Its glowing outline shows through blocks, so
 * you can see the center even when it's buried inside the planet. Markers are normal
 * entities, so every player on the server sees them.
 */
public final class PlanetMarkers {
    /** Tag on every marker, so they can be found and removed again. */
    public static final String TAG = "galaxy_planet_center";
    private static final double SIZE = 0.5;

    private PlanetMarkers() {
    }

    /** Removes this dimension's markers and, when markers are on, puts one at each center. */
    public static void refresh(ServerLevel level) {
        for (Display.BlockDisplay marker : level.getEntities(
                EntityTypeTest.forClass(Display.BlockDisplay.class), e -> e.entityTags().contains(TAG))) {
            marker.discard();
        }
        PlanetGravityData data = PlanetGravityData.get(level);
        if (!data.showCenters()) {
            return;
        }
        CommandSourceStack source = level.getServer().createCommandSourceStack()
                .withLevel(level)
                .withSuppressedOutput();
        for (GravityField field : data.fields()) {
            Vec3 c = field.center();
            float half = (float) (SIZE / 2);
            String command = String.format(Locale.ROOT,
                    "summon minecraft:block_display %.4f %.4f %.4f {Tags:[\"%s\"],Glowing:1b,"
                            + "glow_color_override:%d,brightness:{sky:15,block:15},"
                            + "block_state:\"minecraft:%s\","
                            + "transformation:{left_rotation:[0f,0f,0f,1f],right_rotation:[0f,0f,0f,1f],"
                            + "translation:[%sf,%sf,%sf],scale:[%sf,%sf,%sf]}}",
                    c.x, c.y, c.z, TAG,
                    field.shape() == GravityField.Shape.CUBE ? 0x55FFFF : 0xFFAA00,
                    field.shape() == GravityField.Shape.CUBE ? "diamond_block" : "gold_block",
                    -half, -half, -half, SIZE, SIZE, SIZE);
            level.getServer().getCommands().performPrefixedCommand(source, command);
        }
    }

    /** True for a marker entity, so other code can leave it alone. */
    public static boolean isMarker(Entity entity) {
        return entity.entityTags().contains(TAG);
    }
}
