package com.maxsters.realtimeminecraftcorruptionsimulator.mixin;

import com.maxsters.realtimeminecraftcorruptionsimulator.world.WorldgenCorruptionHooks;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Holder;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.level.chunk.CarvingMask;
import net.minecraft.world.level.chunk.ChunkAccess;
import net.minecraft.world.level.levelgen.Aquifer;
import net.minecraft.world.level.levelgen.carver.CarvingContext;
import net.minecraft.world.level.levelgen.carver.ConfiguredWorldCarver;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import java.util.function.Function;

@Mixin(ConfiguredWorldCarver.class)
public abstract class ConfiguredWorldCarverMixin {
    @Inject(
            method = {
                    "isStartChunk(Lnet/minecraft/util/RandomSource;)Z",
                    "m_224896_"
            },
            at = @At("RETURN"),
            cancellable = true,
            remap = false,
            require = 0
    )
    private void rmc$corruptCarverStart(RandomSource random, CallbackInfoReturnable<Boolean> callback) {
        callback.setReturnValue(WorldgenCorruptionHooks.corruptCarverStart(this, callback.getReturnValue(), random));
    }

    @Inject(
            method = {
                    "carve(Lnet/minecraft/world/level/levelgen/carver/CarvingContext;Lnet/minecraft/world/level/chunk/ChunkAccess;Ljava/util/function/Function;Lnet/minecraft/util/RandomSource;Lnet/minecraft/world/level/levelgen/Aquifer;Lnet/minecraft/world/level/ChunkPos;Lnet/minecraft/world/level/chunk/CarvingMask;)Z",
                    "m_224898_"
            },
            at = @At("RETURN"),
            cancellable = true,
            remap = false,
            require = 0
    )
    private void rmc$corruptCarverResult(CarvingContext context, ChunkAccess chunk, Function<BlockPos, Holder<Biome>> biomeGetter, RandomSource random, Aquifer aquifer, ChunkPos carvingOrigin, CarvingMask carvingMask, CallbackInfoReturnable<Boolean> callback) {
        callback.setReturnValue(WorldgenCorruptionHooks.afterCarver(this, chunk, carvingOrigin, callback.getReturnValue()));
    }
}
