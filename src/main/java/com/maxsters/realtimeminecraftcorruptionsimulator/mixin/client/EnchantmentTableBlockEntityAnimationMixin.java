package com.maxsters.realtimeminecraftcorruptionsimulator.mixin.client;

import com.maxsters.realtimeminecraftcorruptionsimulator.client.hooks.animation.BlockEntityAnimationCorruptionHooks;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.EnchantmentTableBlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import org.spongepowered.asm.mixin.Dynamic;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(EnchantmentTableBlockEntity.class)
@SuppressWarnings("target")
public abstract class EnchantmentTableBlockEntityAnimationMixin {
    @Inject(
            method = {
                    "bookAnimationTick(Lnet/minecraft/world/level/Level;Lnet/minecraft/core/BlockPos;Lnet/minecraft/world/level/block/state/BlockState;Lnet/minecraft/world/level/block/entity/EnchantmentTableBlockEntity;)V",
                    "m_155503_(Lnet/minecraft/world/level/Level;Lnet/minecraft/core/BlockPos;Lnet/minecraft/world/level/block/state/BlockState;Lnet/minecraft/world/level/block/entity/EnchantmentTableBlockEntity;)V"
            },
            at = @At("RETURN"),
            remap = false,
            require = 0
    )
    @Dynamic("Targets both mapped dev names and SRG runtime aliases for EnchantmentTableBlockEntity#bookAnimationTick.")
    private static void rmc$corruptBookAnimationTick(Level level, BlockPos blockPos, BlockState blockState, EnchantmentTableBlockEntity blockEntity, CallbackInfo callback) {
        if (level != null && level.isClientSide) {
            BlockEntityAnimationCorruptionHooks.corruptEnchantmentTableFields(blockEntity);
        }
    }
}
