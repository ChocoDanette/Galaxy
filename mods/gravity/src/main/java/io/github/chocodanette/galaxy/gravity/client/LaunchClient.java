package io.github.chocodanette.galaxy.gravity.client;

import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.Component;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.phys.Vec3;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.tick.EntityTickEvent;

import io.github.chocodanette.galaxy.gravity.GalaxyGravity;
import io.github.chocodanette.galaxy.gravity.GravityFrame;
import io.github.chocodanette.galaxy.gravity.GravityFrames;
import io.github.chocodanette.galaxy.gravity.Launch;

/** Shows you how charged your charged jump is: a sound and a message at each step, and sparks. */
@EventBusSubscriber(modid = GalaxyGravity.MODID, value = Dist.CLIENT)
public final class LaunchClient {
    private LaunchClient() {
    }

    @SubscribeEvent
    static void onPlayerTick(EntityTickEvent.Post event) {
        if (!(event.getEntity() instanceof LocalPlayer player) || player != Minecraft.getInstance().player) {
            return;
        }
        GravityFrame frame = GravityFrames.of(player);
        if (frame == null) {
            return;
        }
        int charge = frame.chargeTicks();
        if (charge == Launch.HOP_CHARGE_TICKS) {
            player.playSound(SoundEvents.NOTE_BLOCK_CHIME.value(), 0.6F, 1.0F);
            player.sendOverlayMessage(Component.translatable("message.galaxy_gravity.hop_ready"));
        } else if (charge == Launch.LAUNCH_CHARGE_TICKS) {
            player.playSound(SoundEvents.NOTE_BLOCK_CHIME.value(), 0.8F, 1.6F);
            player.sendOverlayMessage(Component.translatable("message.galaxy_gravity.launch_ready"));
        }
        Launch.Kind kind = Launch.kindFor(charge);
        if (kind != Launch.Kind.NONE && player.tickCount % 3 == 0) {
            Vec3 feet = player.position();
            Vec3 up = frame.up();
            var random = player.getRandom();
            Vec3 at = feet.add(random.nextGaussian() * 0.3, random.nextGaussian() * 0.3, random.nextGaussian() * 0.3);
            Vec3 speed = up.scale(kind == Launch.Kind.LAUNCH ? 0.12 : 0.05);
            player.level().addParticle(kind == Launch.Kind.LAUNCH ? ParticleTypes.END_ROD : ParticleTypes.ELECTRIC_SPARK,
                    at.x, at.y, at.z, speed.x, speed.y, speed.z);
        }
    }
}
