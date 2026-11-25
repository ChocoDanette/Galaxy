package io.github.chocodanette.galaxy.worldgen;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Stream;

import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.dimension.DimensionType;
import net.minecraft.world.level.storage.LevelResource;

/**
 * Deletes what's left of old galaxies: the saved chunks, mobs and items of their slots. It runs
 * once when the server starts, before anyone joins, while none of the galaxy is loaded, so no
 * file is in use. Only whole region files (512 by 512 blocks) inside an old slot's strip are
 * deleted; the current galaxy's strip is never touched.
 */
final class GalaxyWipe {
    /** Folders of the galaxy dimension that hold region files. */
    private static final List<String> FOLDERS = List.of("region", "entities", "poi");
    private static final Pattern REGION_FILE = Pattern.compile("r\\.(-?\\d+)\\.(-?\\d+)\\.mca");
    private static final int REGION_SIZE = 512;

    private GalaxyWipe() {
    }

    /** Deletes the files of every dirty slot (when it's safe) and marks those slots clean. */
    static void wipe(MinecraftServer server, GalaxyCycle cycle) {
        ServerLevel galaxy = server.getLevel(PlanetGravityLink.GALAXY);
        if (galaxy == null || cycle.dirtySlots().isEmpty()) {
            return;
        }
        if (galaxy.getChunkSource().getLoadedChunksCount() > 0) {
            GalaxyWorldgen.LOGGER.warn("Part of the galaxy is already loaded; old galaxies will be deleted next start");
            return;
        }
        Path folder = folder(server);
        for (int slot : cycle.dirtySlots()) {
            if (slot == GalaxyCycle.slot()) {
                continue;
            }
            int deleted = 0;
            try {
                for (String name : FOLDERS) {
                    deleted += deleteSlot(folder.resolve(name), slot);
                }
                cycle.markClean(slot);
                GalaxyWorldgen.LOGGER.info("Deleted {} files of the old galaxy in slot {}", deleted, slot);
            } catch (IOException e) {
                GalaxyWorldgen.LOGGER.warn("Couldn't delete the old galaxy in slot {}; will try again next start", slot, e);
            }
        }
    }

    /** Whether any chunk of this slot was ever saved. */
    static boolean hasFiles(MinecraftServer server, int slot) {
        Path region = folder(server).resolve("region");
        if (!Files.isDirectory(region)) {
            return false;
        }
        try (Stream<Path> files = Files.list(region)) {
            return files.anyMatch(file -> inSlot(file, slot));
        } catch (IOException e) {
            return false;
        }
    }

    private static int deleteSlot(Path dir, int slot) throws IOException {
        if (!Files.isDirectory(dir)) {
            return 0;
        }
        List<Path> doomed;
        try (Stream<Path> files = Files.list(dir)) {
            doomed = files.filter(file -> inSlot(file, slot)).toList();
        }
        for (Path file : doomed) {
            Files.deleteIfExists(file);
        }
        return doomed.size();
    }

    /** Whether a region file lies entirely inside a slot's strip of the dimension. */
    static boolean inSlot(Path file, int slot) {
        Matcher m = REGION_FILE.matcher(file.getFileName().toString());
        if (!m.matches()) {
            return false;
        }
        long minX = Long.parseLong(m.group(1)) * REGION_SIZE;
        long maxX = minX + REGION_SIZE - 1;
        long stripMin = (long) slot * Galaxy.SPACING - Galaxy.SPACING / 2;
        long stripMax = (long) slot * Galaxy.SPACING + Galaxy.SPACING / 2 - 1;
        return minX >= stripMin && maxX <= stripMax;
    }

    /** The galaxy dimension's folder in the world save. */
    private static Path folder(MinecraftServer server) {
        return DimensionType.getStorageFolder(PlanetGravityLink.GALAXY, server.getWorldPath(LevelResource.ROOT));
    }
}
