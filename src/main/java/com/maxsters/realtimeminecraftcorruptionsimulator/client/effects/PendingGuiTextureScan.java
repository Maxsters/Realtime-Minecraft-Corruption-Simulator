package com.maxsters.realtimeminecraftcorruptionsimulator.client.effects;

import com.maxsters.realtimeminecraftcorruptionsimulator.profile.CorruptionEffectStack;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.packs.resources.Resource;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.api.distmarker.OnlyIn;

import java.util.List;
import java.util.Map;
import java.util.Set;

@OnlyIn(Dist.CLIENT)
final class PendingGuiTextureScan {
    final String signature;
    final CorruptionEffectStack stack;
    final Map<ResourceLocation, Resource> resources;
    final List<ResourceLocation> textureIds;
    final List<ResourceLocation> donorTextureIds;
    final Set<ResourceLocation> staleTextureIds;
    int ordinal;
    int mutatedCount;

    PendingGuiTextureScan(String signature, CorruptionEffectStack stack, Map<ResourceLocation, Resource> resources, List<ResourceLocation> textureIds, List<ResourceLocation> donorTextureIds, Set<ResourceLocation> staleTextureIds) {
        this.signature = signature;
        this.stack = stack;
        this.resources = resources;
        this.textureIds = textureIds;
        this.donorTextureIds = donorTextureIds;
        this.staleTextureIds = staleTextureIds;
    }
}
