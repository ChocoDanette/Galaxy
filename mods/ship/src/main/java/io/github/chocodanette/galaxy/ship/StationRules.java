package io.github.chocodanette.galaxy.ship;

import java.util.Optional;

import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.TriState;
import net.minecraft.world.Container;
import net.minecraft.world.MenuProvider;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityTypes;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.BucketItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.entity.living.LivingIncomingDamageEvent;
import net.neoforged.neoforge.event.entity.player.AttackEntityEvent;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.event.entity.player.PlayerInteractEvent;
import net.neoforged.neoforge.event.level.BlockEvent;
import net.neoforged.neoforge.event.level.ExplosionEvent;
import net.neoforged.neoforge.event.level.block.BreakBlockEvent;

import io.github.chocodanette.galaxy.ship.crew.Crew;
import io.github.chocodanette.galaxy.ship.crew.CrewRole;

/**
 * Who may change what on the stations.
 * <ul>
 * <li>The common station is everyone's shared home and stays as it was built: nobody builds or
 * breaks there. Its chests, crafting table and furnace still work for everyone.</li>
 * <li>On a crew's own station, Captains and Crew build, break and open chests inside the
 * station's building area. Guests and outsiders can only look around (crafting tables and
 * doors still work for them).</li>
 * <li>Nothing can be built anywhere else in the station dimension: it's empty space.</li>
 * <li>Everywhere in the station dimension: no explosions, and players can't hurt each other.</li>
 * </ul>
 * Players in creative mode can still edit everything, for building and fixing stations.
 */
public final class StationRules {
    private StationRules() {
    }

    static void register() {
        NeoForge.EVENT_BUS.addListener(StationRules::onBreak);
        NeoForge.EVENT_BUS.addListener(StationRules::onBreakSpeed);
        NeoForge.EVENT_BUS.addListener(StationRules::onPlace);
        NeoForge.EVENT_BUS.addListener(StationRules::onUseOnBlock);
        NeoForge.EVENT_BUS.addListener(StationRules::onUseItem);
        NeoForge.EVENT_BUS.addListener(StationRules::onExplosion);
        NeoForge.EVENT_BUS.addListener(StationRules::onAttackEntity);
        NeoForge.EVENT_BUS.addListener(StationRules::onDamage);
    }

    /** The player's role on the station at this spot, if they're in the crew that owns it. */
    private static Optional<CrewRole> roleAt(Player player, BlockPos pos) {
        if (player.level().getServer() == null) {
            return Optional.empty();
        }
        return StationPlots.ownerAt(player.level().getServer(), pos).flatMap(crew -> crew.role(player.getUUID()));
    }

    /** True when this player may build and break at this spot. */
    static boolean canEdit(Player player, Level level, BlockPos pos) {
        if (!StationLocation.isStation(level) || player.isCreative()) {
            return true;
        }
        if (level.isClientSide()) {
            // Only the server knows the crews. The client already knows the common station
            // and empty space are off limits, so mining there doesn't even start.
            return StationPlots.plotAt(pos) != Crew.NO_PLOT;
        }
        Optional<Crew> owner = StationPlots.ownerAt(level.getServer(), pos);
        return owner.isPresent() && StationPlots.inBuildArea(owner.get(), pos)
                && owner.get().role(player.getUUID()).map(CrewRole::canBuild).orElse(false);
    }

    /** True when this player may open chests and other storage at this spot. */
    static boolean canOpenStorage(Player player, Level level, BlockPos pos) {
        if (!StationLocation.isStation(level) || player.isCreative() || level.isClientSide()) {
            return true;
        }
        int plot = StationPlots.plotAt(pos);
        // The common station's storage is shared by everyone.
        return plot == Crew.NO_PLOT || roleAt(player, pos).map(CrewRole::canBuild).orElse(false);
    }

    /**
     * True when this spot is fine for the player to be: the common station, or a station of
     * their own crew. False on another crew's station.
     */
    static boolean belongsAt(ServerPlayer player, BlockPos pos) {
        return StationPlots.plotAt(pos) == Crew.NO_PLOT || roleAt(player, pos).isPresent();
    }

    private static void refuse(Player player, BlockPos pos) {
        boolean common = StationPlots.plotAt(pos) == Crew.NO_PLOT;
        boolean member = !common && roleAt(player, pos).isPresent();
        String key = common ? "galaxy_ship.no_building"
                : member && roleAt(player, pos).get().canBuild() ? "galaxy_ship.outside_area"
                : member ? "galaxy_ship.guest_no_building" : "galaxy_ship.not_your_station";
        player.sendOverlayMessage(Component.translatable(key));
    }

    private static void onBreak(BreakBlockEvent event) {
        if (event.getLevel() instanceof Level level && !canEdit(event.getPlayer(), level, event.getPos())) {
            event.setCanceled(true);
            refuse(event.getPlayer(), event.getPos());
        }
    }

    /** No cracks appear either: mining just doesn't start. */
    private static void onBreakSpeed(PlayerEvent.BreakSpeed event) {
        Player player = event.getEntity();
        if (event.getPosition().isPresent() && !canEdit(player, player.level(), event.getPosition().get())) {
            event.setNewSpeed(0.0F);
        }
    }

    private static void onPlace(BlockEvent.EntityPlaceEvent event) {
        Entity entity = event.getEntity();
        if (event.getLevel() instanceof Level level && StationLocation.isStation(level)
                && (entity instanceof Player player ? !canEdit(player, level, event.getPos()) : !level.isClientSide())) {
            event.setCanceled(true);
            if (entity instanceof Player player) {
                refuse(player, event.getPos());
            }
        }
    }

    /** Blocks and buckets only work where you may build; storage only opens for the crew. */
    private static void onUseOnBlock(PlayerInteractEvent.RightClickBlock event) {
        Player player = event.getEntity();
        Level level = event.getLevel();
        if (!StationLocation.isStation(level)) {
            return;
        }
        BlockPos target = event.getFace() == null ? event.getPos() : event.getPos().relative(event.getFace());
        if (changesBlocks(event.getItemStack()) && !canEdit(player, level, target)) {
            event.setUseItem(TriState.FALSE);
            refuse(player, target);
        }
        BlockEntity blockEntity = level.getBlockEntity(event.getPos());
        if ((blockEntity instanceof Container || blockEntity instanceof MenuProvider)
                && !canOpenStorage(player, level, event.getPos())) {
            event.setUseBlock(TriState.FALSE);
            player.sendOverlayMessage(Component.translatable("galaxy_ship.no_storage"));
        }
    }

    private static void onUseItem(PlayerInteractEvent.RightClickItem event) {
        if (changesBlocks(event.getItemStack()) && !canEdit(event.getEntity(), event.getLevel(), event.getEntity().blockPosition())) {
            event.setCanceled(true);
        }
    }

    /** Item frames, paintings and armor stands count as part of the station. */
    private static void onAttackEntity(AttackEntityEvent event) {
        Entity target = event.getTarget();
        Player player = event.getEntity();
        boolean decoration = !(target instanceof LivingEntity) || target.getType() == EntityTypes.ARMOR_STAND;
        if (decoration && !canEdit(player, player.level(), target.blockPosition())) {
            event.setCanceled(true);
        }
    }

    /** No fighting between players on the stations. */
    private static void onDamage(LivingIncomingDamageEvent event) {
        if (event.getEntity() instanceof Player && event.getSource().getEntity() instanceof Player
                && StationLocation.isStation(event.getEntity().level())) {
            event.setCanceled(true);
        }
    }

    private static boolean changesBlocks(ItemStack stack) {
        return stack.getItem() instanceof BlockItem || stack.getItem() instanceof BucketItem;
    }

    private static void onExplosion(ExplosionEvent.Detonate event) {
        if (StationLocation.isStation(event.getLevel())) {
            event.getAffectedBlocks().clear();
        }
    }
}
