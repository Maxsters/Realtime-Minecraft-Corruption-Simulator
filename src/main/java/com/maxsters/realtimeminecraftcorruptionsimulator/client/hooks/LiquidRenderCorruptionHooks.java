package com.maxsters.realtimeminecraftcorruptionsimulator.client.hooks;

import com.maxsters.realtimeminecraftcorruptionsimulator.client.effects.ClientCorruptionEffects;
import com.maxsters.realtimeminecraftcorruptionsimulator.profile.CorruptionEffectStack;
import com.maxsters.realtimeminecraftcorruptionsimulator.profile.CorruptionSurface;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import net.minecraft.world.level.material.FluidState;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.api.distmarker.OnlyIn;
import net.minecraftforge.registries.ForgeRegistries;

@OnlyIn(Dist.CLIENT)
public final class LiquidRenderCorruptionHooks {
    private LiquidRenderCorruptionHooks() {
    }

    public static boolean shouldDropLiquidMesh(BlockPos pos, FluidState fluidState) {
        if (pos == null || fluidState == null || fluidState.isEmpty()) {
            return false;
        }

        CorruptionEffectStack stack = ClientCorruptionEffects.current();
        float world = stack.intensity(CorruptionSurface.WORLD_RENDER) * 0.52F;
        float texture = stack.intensity(CorruptionSurface.TEXTURE_MEMORY) * 0.64F;
        float intensity = Mth.clamp(Math.max(world, texture), 0.0F, 1.0F);
        if (intensity <= 0.045F && !stack.extreme(CorruptionSurface.WORLD_RENDER) && !stack.extreme(CorruptionSurface.TEXTURE_MEMORY)) {
            return false;
        }

        ResourceLocation fluidId = ForgeRegistries.FLUIDS.getKey(fluidState.getType());
        String targetId = "liquid_face:" + (fluidId == null ? "unknown" : fluidId) + ":" + pos.getX() + ":" + pos.getY() + ":" + pos.getZ();
        float chance = stack.extreme(CorruptionSurface.WORLD_RENDER) || stack.extreme(CorruptionSurface.TEXTURE_MEMORY)
                ? 0.62F
                : Mth.clamp(0.018F + intensity * 0.46F + stack.instability() * 0.08F, 0.0F, 0.74F);
        return stack.unit(CorruptionSurface.WORLD_RENDER, targetId, 0x4C495155) < chance;
    }
}
