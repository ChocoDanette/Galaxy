package io.github.chocodanette.galaxy.gravity.client;

import net.minecraft.client.Minecraft;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.fml.ModContainer;
import net.neoforged.fml.common.Mod;
import net.neoforged.neoforge.client.gui.ConfigurationScreen;
import net.neoforged.neoforge.client.gui.IConfigScreenFactory;

import io.github.chocodanette.galaxy.gravity.GalaxyGravity;
import io.github.chocodanette.galaxy.gravity.GravityConfig;
import io.github.chocodanette.galaxy.gravity.GravityFrames;

/** Client-only setup: the config screen, and the steady camera option for your own player. */
@Mod(value = GalaxyGravity.MODID, dist = Dist.CLIENT)
public final class GalaxyGravityClient {
    public GalaxyGravityClient(ModContainer container) {
        container.registerExtensionPoint(IConfigScreenFactory.class, ConfigurationScreen::new);
        GravityFrames.KEEPS_LOOK = entity -> entity == Minecraft.getInstance().player
                && GravityConfig.STEADY_CAMERA.getAsBoolean();
    }
}
