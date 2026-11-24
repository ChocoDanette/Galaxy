package io.github.chocodanette.galaxy.gravity.mixin;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

import net.minecraft.world.entity.LivingEntity;

/** Reads whether a living entity is trying to jump (the jump key, for players). */
@Mixin(LivingEntity.class)
public interface LivingEntityAccess {
    @Accessor("jumping")
    boolean galaxy$isJumping();
}
