package io.github.chocodanette.galaxy.gravity.mixin;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.ai.navigation.PathNavigation;

/** Reads which mob a navigation belongs to. */
@Mixin(PathNavigation.class)
public interface PathNavigationAccess {
    @Accessor("mob")
    Mob galaxy$mob();
}
