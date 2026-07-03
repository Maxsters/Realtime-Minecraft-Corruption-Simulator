package com.maxsters.realtimeminecraftcorruptionsimulator.mixin;

import com.maxsters.realtimeminecraftcorruptionsimulator.mechanics.CorruptionMechanicsManager;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.block.state.BlockState;
import org.spongepowered.asm.mixin.Dynamic;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(BlockBehaviour.BlockStateBase.class)
@SuppressWarnings("target")
public abstract class BlockStateRedstoneSignalMixin {
    @Inject(
            method = {
                    "getSignal(Lnet/minecraft/world/level/BlockGetter;Lnet/minecraft/core/BlockPos;Lnet/minecraft/core/Direction;)I",
                    "m_60746_(Lnet/minecraft/world/level/BlockGetter;Lnet/minecraft/core/BlockPos;Lnet/minecraft/core/Direction;)I"
            },
            at = @At("RETURN"),
            cancellable = true,
            remap = false,
            require = 0
    )
    @Dynamic("Targets both mapped dev names and SRG runtime aliases for BlockStateBase#getSignal.")
    private void rmc$corruptSignal(BlockGetter level, BlockPos pos, Direction direction, CallbackInfoReturnable<Integer> callback) {
        callback.setReturnValue(CorruptionMechanicsManager.corruptRedstoneSignal(rmc$state(), level, pos, direction, callback.getReturnValue(), "weak"));
    }

    @Inject(
            method = {
                    "getDirectSignal(Lnet/minecraft/world/level/BlockGetter;Lnet/minecraft/core/BlockPos;Lnet/minecraft/core/Direction;)I",
                    "m_60775_(Lnet/minecraft/world/level/BlockGetter;Lnet/minecraft/core/BlockPos;Lnet/minecraft/core/Direction;)I"
            },
            at = @At("RETURN"),
            cancellable = true,
            remap = false,
            require = 0
    )
    @Dynamic("Targets both mapped dev names and SRG runtime aliases for BlockStateBase#getDirectSignal.")
    private void rmc$corruptDirectSignal(BlockGetter level, BlockPos pos, Direction direction, CallbackInfoReturnable<Integer> callback) {
        callback.setReturnValue(CorruptionMechanicsManager.corruptRedstoneSignal(rmc$state(), level, pos, direction, callback.getReturnValue(), "direct"));
    }

    @Inject(
            method = {
                    "getAnalogOutputSignal(Lnet/minecraft/world/level/Level;Lnet/minecraft/core/BlockPos;)I",
                    "m_60674_(Lnet/minecraft/world/level/Level;Lnet/minecraft/core/BlockPos;)I"
            },
            at = @At("RETURN"),
            cancellable = true,
            remap = false,
            require = 0
    )
    @Dynamic("Targets both mapped dev names and SRG runtime aliases for BlockStateBase#getAnalogOutputSignal.")
    private void rmc$corruptAnalogSignal(Level level, BlockPos pos, CallbackInfoReturnable<Integer> callback) {
        callback.setReturnValue(CorruptionMechanicsManager.corruptRedstoneAnalogSignal(rmc$state(), level, pos, callback.getReturnValue()));
    }

    private BlockState rmc$state() {
        return (BlockState) (Object) this;
    }
}
