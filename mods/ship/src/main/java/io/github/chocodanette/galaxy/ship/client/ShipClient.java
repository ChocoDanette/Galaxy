package io.github.chocodanette.galaxy.ship.client;

import net.minecraft.client.Minecraft;

import io.github.chocodanette.galaxy.ship.ShipStatePayload;

/** Player side of the console: opens or refreshes the console screen when the server says so. */
public final class ShipClient {
    private ShipClient() {
    }

    public static void onState(ShipStatePayload state) {
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.gui.screen() instanceof ConsoleScreen screen) {
            screen.update(state);
        } else if (state.openScreen()) {
            minecraft.gui.setScreen(new ConsoleScreen(state));
        }
    }
}
