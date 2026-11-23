package io.github.chocodanette.galaxy.gravity;

import java.util.List;
import java.util.function.Supplier;

import org.jspecify.annotations.Nullable;

import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.phys.Vec3;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.attachment.AttachmentType;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.tick.EntityTickEvent;
import net.neoforged.neoforge.registries.DeferredRegister;
import net.neoforged.neoforge.registries.NeoForgeRegistries;

/**
 * Gives every living entity a {@link GravityFrame} and turns it toward its planet each tick.
 *
 * The frame is not saved or sent over the network: both the server and each player work it
 * out from the same synced planet list, so they agree without extra traffic.
 */
public final class GravityFrames {
    private static final DeferredRegister<AttachmentType<?>> ATTACHMENTS =
            DeferredRegister.create(NeoForgeRegistries.Keys.ATTACHMENT_TYPES, GalaxyGravity.MODID);

    public static final Supplier<AttachmentType<GravityFrame>> FRAME =
            ATTACHMENTS.register("frame", () -> AttachmentType.builder(GravityFrame::new).build());

    private GravityFrames() {
    }

    static void register(IEventBus modEventBus) {
        ATTACHMENTS.register(modEventBus);
        NeoForge.EVENT_BUS.addListener(GravityFrames::onEntityTick);
    }

    /** The entity's frame when it is tilted, otherwise null (vanilla behavior applies). */
    public static @Nullable GravityFrame tilted(Entity entity) {
        if (!(entity instanceof LivingEntity)) {
            return null;
        }
        GravityFrame frame = entity.getExistingDataOrNull(FRAME);
        return frame != null && frame.isTilted() ? frame : null;
    }

    /** The entity's frame if it has one (tilted or not), otherwise null. */
    public static @Nullable GravityFrame of(Entity entity) {
        return entity instanceof LivingEntity ? entity.getExistingDataOrNull(FRAME) : null;
    }

    private static void onEntityTick(EntityTickEvent.Pre event) {
        if (!(event.getEntity() instanceof LivingEntity entity)) {
            return;
        }
        List<GravityField> fields = fieldsFor(entity);
        if (fields.isEmpty() && !entity.hasData(FRAME)) {
            return;
        }
        Vec3 down = GravityMath.downDirection(fields, entity.position());
        entity.getData(FRAME).tick(down.scale(-1));
        if (tilted(entity) != null) {
            // Vanilla measures falls along world "down", which is wrong on a planet's side.
            entity.resetFallDistance();
        }
    }

    static List<GravityField> fieldsFor(Entity entity) {
        if (entity.level() instanceof ServerLevel level) {
            return PlanetGravityData.get(level).fields();
        }
        return ClientPlanets.fields();
    }
}
