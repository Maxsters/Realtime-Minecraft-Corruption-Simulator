package com.maxsters.realtimeminecraftcorruptionsimulator.mixin.client;

import com.maxsters.realtimeminecraftcorruptionsimulator.client.effects.ItemTextureCorruptionManager;
import net.minecraft.client.renderer.block.model.BakedQuad;
import net.minecraft.client.resources.model.BakedModel;
import net.minecraft.client.resources.model.SimpleBakedModel;
import net.minecraft.core.Direction;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.block.state.BlockState;
import org.spongepowered.asm.mixin.Dynamic;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import java.util.List;

@Mixin(SimpleBakedModel.class)
public abstract class SimpleBakedModelMixin {
    @Inject(
            method = {
                    "getQuads(Lnet/minecraft/world/level/block/state/BlockState;Lnet/minecraft/core/Direction;Lnet/minecraft/util/RandomSource;)Ljava/util/List;",
                    "m_213637_(Lnet/minecraft/world/level/block/state/BlockState;Lnet/minecraft/core/Direction;Lnet/minecraft/util/RandomSource;)Ljava/util/List;"
            },
            at = @At("RETURN"),
            cancellable = true,
            remap = false,
            require = 0
    )
    @Dynamic("Targets both mapped dev names and SRG runtime aliases for SimpleBakedModel#getQuads.")
    private void rmc$corruptReturnedQuads(BlockState state, Direction side, RandomSource random, CallbackInfoReturnable<List<BakedQuad>> callback) {
        callback.setReturnValue(ItemTextureCorruptionManager.corruptReturnedBlockQuads((BakedModel) (Object) this, state, side, callback.getReturnValue()));
    }
}
