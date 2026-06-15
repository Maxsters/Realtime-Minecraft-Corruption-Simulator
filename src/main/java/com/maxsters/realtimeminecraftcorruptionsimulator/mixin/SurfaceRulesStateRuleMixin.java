package com.maxsters.realtimeminecraftcorruptionsimulator.mixin;

import com.maxsters.realtimeminecraftcorruptionsimulator.world.WorldgenCorruptionHooks;
import net.minecraft.world.level.block.state.BlockState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(targets = "net.minecraft.world.level.levelgen.SurfaceRules$StateRule")
public abstract class SurfaceRulesStateRuleMixin {
    @Inject(
            method = {
                    "tryApply(III)Lnet/minecraft/world/level/block/state/BlockState;",
                    "m_183550_"
            },
            at = @At("RETURN"),
            cancellable = true,
            remap = false,
            require = 0
    )
    private void rmc$corruptSurfaceMaterial(int x, int y, int z, CallbackInfoReturnable<BlockState> callback) {
        callback.setReturnValue(WorldgenCorruptionHooks.corruptSurfaceMaterial(callback.getReturnValue(), x, y, z));
    }
}
