package io.github.chocodanette.galaxy.worldgen;

import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.Level;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.entity.EntityTravelToDimensionEvent;
import net.neoforged.neoforge.event.level.BlockEvent;

/**
 * The Nether and the End are gone from this pack: what they gave now comes from Ember and
 * Void planets (see {@link Shrines}). Minecraft still keeps both worlds around, so this shuts
 * every way in: nether portals don't light, and nothing (players, mobs, items) can travel there
 * by portal. Operators can still visit with commands.
 */
public final class VanillaDimensions {
    private VanillaDimensions() {
    }

    static void register() {
        NeoForge.EVENT_BUS.addListener(VanillaDimensions::onPortalSpawn);
        NeoForge.EVENT_BUS.addListener(VanillaDimensions::onTravel);
    }

    private static void onPortalSpawn(BlockEvent.PortalSpawnEvent event) {
        event.setCanceled(true);
    }

    private static void onTravel(EntityTravelToDimensionEvent event) {
        if (!event.getDimension().equals(Level.NETHER) && !event.getDimension().equals(Level.END)) {
            return;
        }
        event.setCanceled(true);
        if (event.getEntity() instanceof ServerPlayer player) {
            player.sendOverlayMessage(Component.translatable("galaxy_worldgen.portal.closed"));
        }
    }
}
