package io.github.chocodanette.galaxy.ship.client;

import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.util.RandomSource;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ClientTickEvent;

import io.github.chocodanette.galaxy.ship.GalaxyShip;
import io.github.chocodanette.galaxy.ship.ShipLocation;

/**
 * Makes the parked ship feel like it's flying: specks of starlight stream past from the nose
 * to the tail. They're only drawn on each player's screen (nothing is sent over the network),
 * and the ship itself never moves.
 */
@EventBusSubscriber(modid = GalaxyShip.MODID, value = Dist.CLIENT)
public final class SpaceView {
    private static final int SPECKS_PER_TICK = 4;
    private static final double SPEED = 1.6;

    private SpaceView() {
    }

    @SubscribeEvent
    static void onClientTick(ClientTickEvent.Post event) {
        ClientLevel level = Minecraft.getInstance().level;
        if (level == null || !ShipLocation.isShip(level) || Minecraft.getInstance().isPaused()) {
            return;
        }
        RandomSource random = level.getRandom();
        for (int i = 0; i < SPECKS_PER_TICK; i++) {
            double x = ShipLocation.ORIGIN.getX() + (random.nextDouble() - 0.5) * 96;
            double y = ShipLocation.ORIGIN.getY() + (random.nextDouble() - 0.5) * 80;
            double z = ShipLocation.ORIGIN.getZ() - 64 + random.nextDouble() * 100;
            if (ShipLocation.nearHull(x, y, z)) {
                continue;
            }
            level.addAlwaysVisibleParticle(ParticleTypes.END_ROD, x, y, z, 0.0, 0.0, SPEED);
        }
    }
}
