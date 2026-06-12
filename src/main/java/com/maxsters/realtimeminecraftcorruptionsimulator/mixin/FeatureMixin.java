package com.maxsters.realtimeminecraftcorruptionsimulator.mixin;

import com.maxsters.realtimeminecraftcorruptionsimulator.world.WorldgenCorruptionHooks;
import net.minecraft.world.level.levelgen.feature.Feature;
import net.minecraft.world.level.levelgen.feature.configurations.FeatureConfiguration;
import net.minecraft.world.level.WorldGenLevel;
import net.minecraft.world.level.chunk.ChunkGenerator;
import net.minecraft.util.RandomSource;
import net.minecraft.core.BlockPos;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(Feature.class)
public abstract class FeatureMixin<FC extends FeatureConfiguration> {
    @Inject(
            method = {
                    "place(Lnet/minecraft/world/level/levelgen/feature/configurations/FeatureConfiguration;Lnet/minecraft/world/level/WorldGenLevel;Lnet/minecraft/world/level/chunk/ChunkGenerator;Lnet/minecraft/util/RandomSource;Lnet/minecraft/core/BlockPos;)Z",
                    "m_225028_"
            },
            at = @At("HEAD"),
            cancellable = true,
            remap = false,
            require = 0
    )
    private void rmc$corruptFeaturePlacement(FC config, WorldGenLevel level, ChunkGenerator chunkGenerator, RandomSource random, BlockPos origin, CallbackInfoReturnable<Boolean> callback) {
        Feature<?> feature = (Feature<?>) (Object) this;
        if (WorldgenCorruptionHooks.shouldSkipFeature(feature, level, origin)) {
            callback.setReturnValue(WorldgenCorruptionHooks.fakeFeatureSuccess(feature, origin));
        }
    }
}
