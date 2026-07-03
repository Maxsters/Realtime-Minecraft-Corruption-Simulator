package com.maxsters.realtimeminecraftcorruptionsimulator.mixin;

import com.maxsters.realtimeminecraftcorruptionsimulator.mechanics.CorruptionMechanicsManager;
import net.minecraft.core.BlockPos;
import net.minecraft.core.NonNullList;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BrewingStandBlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import org.spongepowered.asm.mixin.Dynamic;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(BrewingStandBlockEntity.class)
@SuppressWarnings("target")
public abstract class BrewingStandBlockEntityMixin {
    private static final ThreadLocal<BrewingStandBlockEntity> RMC$CURRENT_BREWING_STAND = new ThreadLocal<>();

    @Inject(
            method = {
                    "serverTick(Lnet/minecraft/world/level/Level;Lnet/minecraft/core/BlockPos;Lnet/minecraft/world/level/block/state/BlockState;Lnet/minecraft/world/level/block/entity/BrewingStandBlockEntity;)V",
                    "m_155285_(Lnet/minecraft/world/level/Level;Lnet/minecraft/core/BlockPos;Lnet/minecraft/world/level/block/state/BlockState;Lnet/minecraft/world/level/block/entity/BrewingStandBlockEntity;)V"
            },
            at = @At("HEAD"),
            remap = false,
            require = 0
    )
    @Dynamic("Tracks the brewing stand behind static brewing checks.")
    private static void rmc$beginBrewingStandTick(Level level, BlockPos pos, BlockState state, BrewingStandBlockEntity blockEntity, CallbackInfo callback) {
        RMC$CURRENT_BREWING_STAND.set(blockEntity);
    }

    @Inject(
            method = {
                    "serverTick(Lnet/minecraft/world/level/Level;Lnet/minecraft/core/BlockPos;Lnet/minecraft/world/level/block/state/BlockState;Lnet/minecraft/world/level/block/entity/BrewingStandBlockEntity;)V",
                    "m_155285_(Lnet/minecraft/world/level/Level;Lnet/minecraft/core/BlockPos;Lnet/minecraft/world/level/block/state/BlockState;Lnet/minecraft/world/level/block/entity/BrewingStandBlockEntity;)V"
            },
            at = @At("RETURN"),
            remap = false,
            require = 0
    )
    private static void rmc$endBrewingStandTick(Level level, BlockPos pos, BlockState state, BrewingStandBlockEntity blockEntity, CallbackInfo callback) {
        RMC$CURRENT_BREWING_STAND.remove();
    }

    @Inject(
            method = {
                    "isBrewable(Lnet/minecraft/core/NonNullList;)Z",
                    "m_155294_(Lnet/minecraft/core/NonNullList;)Z"
            },
            at = @At("RETURN"),
            cancellable = true,
            remap = false,
            require = 0
    )
    @Dynamic("Targets both mapped dev names and SRG runtime aliases for BrewingStandBlockEntity#isBrewable.")
    private static void rmc$corruptBrewable(NonNullList<ItemStack> items, CallbackInfoReturnable<Boolean> callback) {
        callback.setReturnValue(CorruptionMechanicsManager.corruptBrewingStandBrewable((BlockEntity) RMC$CURRENT_BREWING_STAND.get(), callback.getReturnValue()));
    }
}
