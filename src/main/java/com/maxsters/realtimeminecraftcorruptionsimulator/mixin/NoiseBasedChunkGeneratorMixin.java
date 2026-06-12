package com.maxsters.realtimeminecraftcorruptionsimulator.mixin;

import com.maxsters.realtimeminecraftcorruptionsimulator.world.WorldgenCorruptionHooks;
import net.minecraft.world.level.StructureManager;
import net.minecraft.world.level.chunk.ChunkAccess;
import net.minecraft.world.level.levelgen.NoiseBasedChunkGenerator;
import net.minecraft.world.level.levelgen.RandomState;
import net.minecraft.world.level.levelgen.blending.Blender;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executor;

@Mixin(NoiseBasedChunkGenerator.class)
public abstract class NoiseBasedChunkGeneratorMixin {
    @Inject(
            method = {
                    "fillFromNoise(Ljava/util/concurrent/Executor;Lnet/minecraft/world/level/levelgen/blending/Blender;Lnet/minecraft/world/level/levelgen/RandomState;Lnet/minecraft/world/level/StructureManager;Lnet/minecraft/world/level/chunk/ChunkAccess;)Ljava/util/concurrent/CompletableFuture;",
                    "m_213974_"
            },
            at = @At("HEAD"),
            remap = false,
            require = 0
    )
    private void rmc$prepareNoiseFill(Executor executor, Blender blender, RandomState randomState, StructureManager structureManager, ChunkAccess chunk, CallbackInfoReturnable<CompletableFuture<ChunkAccess>> callback) {
        WorldgenCorruptionHooks.noteFillFromNoise(chunk);
    }
}
