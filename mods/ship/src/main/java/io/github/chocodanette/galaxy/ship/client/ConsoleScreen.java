package io.github.chocodanette.galaxy.ship.client;

import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.neoforged.neoforge.client.network.ClientPacketDistributor;

import io.github.chocodanette.galaxy.ship.ShipActionPayload;
import io.github.chocodanette.galaxy.ship.ShipStatePayload;

/**
 * The console's flight screen: where the ship is, how much fuel is left, and two buttons.
 * It shows what the server sent and asks the server to act; it decides nothing itself.
 */
public final class ConsoleScreen extends Screen {
    private static final int BUTTON_WIDTH = 200;
    private static final int TEXT = 0xFFFFFFFF;
    private static final int DIM_TEXT = 0xFFA0A0B0;

    private final BlockPos console;
    private int galaxy;
    private int fuel;
    private int maxFuel;
    private int jumpCost;
    private Button jumpButton;

    public ConsoleScreen(ShipStatePayload state) {
        super(Component.translatable("galaxy_ship.console.title"));
        this.console = state.console();
        update(state);
    }

    /** New numbers from the server (a crewmate refueled or jumped). The console stays the same. */
    void update(ShipStatePayload state) {
        galaxy = state.galaxy();
        fuel = state.fuel();
        maxFuel = state.maxFuel();
        jumpCost = state.jumpCost();
        if (jumpButton != null) {
            refreshJumpButton();
        }
    }

    @Override
    protected void init() {
        int x = (width - BUTTON_WIDTH) / 2;
        int y = height / 2;
        addRenderableWidget(Button.builder(Component.translatable("galaxy_ship.console.land"),
                        button -> send(ShipActionPayload.LAND))
                .bounds(x, y, BUTTON_WIDTH, 20).build());
        jumpButton = addRenderableWidget(Button.builder(Component.empty(), button -> send(ShipActionPayload.JUMP))
                .bounds(x, y + 24, BUTTON_WIDTH, 20).build());
        addRenderableWidget(Button.builder(Component.translatable("gui.done"), button -> onClose())
                .bounds(x, y + 56, BUTTON_WIDTH, 20).build());
        refreshJumpButton();
    }

    private void refreshJumpButton() {
        jumpButton.setMessage(Component.translatable("galaxy_ship.console.jump", galaxy + 1, jumpCost));
        jumpButton.active = fuel >= jumpCost;
    }

    private void send(int action) {
        ClientPacketDistributor.sendToServer(new ShipActionPayload(action, console));
        if (action == ShipActionPayload.LAND) {
            onClose();
        }
    }

    @Override
    public void extractRenderState(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float partialTick) {
        super.extractRenderState(graphics, mouseX, mouseY, partialTick);
        int centerX = width / 2;
        int top = height / 2 - 64;
        graphics.centeredText(font, title.getString(), centerX, top, TEXT);
        graphics.centeredText(font, Component.translatable("galaxy_ship.console.parked", galaxy).getString(),
                centerX, top + 20, TEXT);
        graphics.centeredText(font, Component.translatable("galaxy_ship.console.fuel", fuel, maxFuel).getString(),
                centerX, top + 34, TEXT);
        graphics.centeredText(font, Component.translatable("galaxy_ship.console.refuel_hint").getString(),
                centerX, top + 48, DIM_TEXT);
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }
}
