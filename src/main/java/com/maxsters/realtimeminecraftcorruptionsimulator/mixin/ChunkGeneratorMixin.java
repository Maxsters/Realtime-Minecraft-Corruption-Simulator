package com.maxsters.realtimeminecraftcorruptionsimulator.mixin;

import com.maxsters.realtimeminecraftcorruptionsimulator.world.WorldgenCorruptionHooks;
import net.minecraft.core.RegistryAccess;
import net.minecraft.world.level.StructureManager;
import net.minecraft.world.level.WorldGenLevel;
import net.minecraft.world.level.chunk.ChunkAccess;
import net.minecraft.world.level.chunk.ChunkGenerator;
import net.minecraft.world.level.chunk.ChunkGeneratorStructureState;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructureTemplateManager;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(ChunkGenerator.class)
public abstract class ChunkGeneratorMixin {
    @Inject(
            method = {
                    "applyBiomeDecoration(Lnet/minecraft/world/level/WorldGenLevel;Lnet/minecraft/world/level/chunk/ChunkAccess;Lnet/minecraft/world/level/StructureManager;)V",
                    "m_213609_"
            },
            at = @At("HEAD"),
            cancellable = true,
            remap = false,
            require = 0
    )
    private void rmc$corruptBiomeDecoration(WorldGenLevel level, ChunkAccess chunk, StructureManager structureManager, CallbackInfo callback) {
        if (WorldgenCorruptionHooks.shouldSkipBiomeDecoration(level, chunk)) {
            callback.cancel();
        }
    }

    @Inject(
            method = {
                    "createStructures(Lnet/minecraft/core/RegistryAccess;Lnet/minecraft/world/level/chunk/ChunkGeneratorStructureState;Lnet/minecraft/world/level/StructureManager;Lnet/minecraft/world/level/chunk/ChunkAccess;Lnet/minecraft/world/level/levelgen/structure/templatesystem/StructureTemplateManager;)V",
                    "m_255037_"
            },
            at = @At("HEAD"),
            cancellable = true,
            remap = false,
            require = 0
    )
    private void rmc$corruptStructureStarts(RegistryAccess registryAccess, ChunkGeneratorStructureState state, StructureManager structureManager, ChunkAccess chunk, StructureTemplateManager templateManager, CallbackInfo callback) {
        if (WorldgenCorruptionHooks.shouldSkipStructures(state, structureManager, chunk)) {
            callback.cancel();
        }
    }
}
