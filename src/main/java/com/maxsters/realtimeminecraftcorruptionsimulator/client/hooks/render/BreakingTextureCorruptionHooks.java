package com.maxsters.realtimeminecraftcorruptionsimulator.client.hooks.render;

import com.maxsters.realtimeminecraftcorruptionsimulator.client.effects.ClientCorruptionEffects;
import com.maxsters.realtimeminecraftcorruptionsimulator.profile.CorruptionEffectStack;
import com.maxsters.realtimeminecraftcorruptionsimulator.profile.CorruptionSurface;
import com.mojang.blaze3d.vertex.VertexConsumer;
import net.minecraft.client.Minecraft;
import net.minecraft.client.resources.model.ModelBakery;
import net.minecraft.util.Mth;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.api.distmarker.OnlyIn;

import java.util.List;

@OnlyIn(Dist.CLIENT)
public final class BreakingTextureCorruptionHooks {
    private BreakingTextureCorruptionHooks() {
    }

    public static Object getDestroyTexture(List<?> list, int stage) {
        if (list != ModelBakery.DESTROY_TYPES) {
            return list.get(stage);
        }

        CorruptionEffectStack stack = ClientCorruptionEffects.currentForWorldRendering();
        if (!stack.activeOrExtreme(CorruptionSurface.TEXTURE_MEMORY)) {
            return list.get(stage);
        }

        int corruptedStage = corruptStage(stack, stage, list.size());
        return list.get(corruptedStage);
    }

    public static VertexConsumer uv(VertexConsumer consumer, float u, float v) {
        CorruptionEffectStack stack = ClientCorruptionEffects.currentForWorldRendering();
        if (!stack.activeOrExtreme(CorruptionSurface.TEXTURE_MEMORY)) {
            return consumer.uv(u, v);
        }

        float intensity = destroyIntensity(stack);
        long gameTime = Minecraft.getInstance().level == null ? 0L : Minecraft.getInstance().level.getGameTime();
        long seed = stack.stableLong(CorruptionSurface.TEXTURE_MEMORY, "destroy_uv", (int) gameTime ^ 0x445556);
        float scaleU = 0.20F + unit(seed ^ 0x555343414C45L) * (1.0F + intensity * 7.0F);
        float scaleV = 0.20F + unit(seed ^ 0x565343414C45L) * (1.0F + intensity * 7.0F);
        float offsetU = signed(seed ^ 0x554F4646534554L, 0.35F + intensity * 3.6F);
        float offsetV = signed(seed ^ 0x564F4646534554L, 0.35F + intensity * 3.6F);
        float shear = signed(seed ^ 0x5348454152L, intensity * 1.85F);
        float pulse = (float) Math.sin((gameTime + unit(seed) * 40.0D) * (0.14D + intensity * 0.42D));
        float leakedU = u * scaleU + v * shear + offsetU + pulse * intensity * 0.55F;
        float leakedV = v * scaleV + u * -shear * 0.65F + offsetV;
        if (unit(seed ^ Float.floatToIntBits(u + v)) < intensity * 0.45F) {
            leakedU = Math.round(leakedU * (2.0F + intensity * 14.0F)) / (2.0F + intensity * 14.0F);
            leakedV = Math.round(leakedV * (2.0F + intensity * 14.0F)) / (2.0F + intensity * 14.0F);
        }
        return consumer.uv(leakedU, leakedV);
    }

    public static VertexConsumer color(VertexConsumer consumer, float red, float green, float blue, float alpha) {
        CorruptionEffectStack stack = ClientCorruptionEffects.currentForWorldRendering();
        if (!stack.activeOrExtreme(CorruptionSurface.TEXTURE_MEMORY)) {
            return consumer.color(red, green, blue, alpha);
        }
        float intensity = destroyIntensity(stack);
        long seed = stack.stableLong(CorruptionSurface.TEXTURE_MEMORY, "destroy_color", 0x44535443);
        float channelLeak = intensity * 0.85F;
        return consumer.color(
                Mth.clamp(red + signed(seed ^ 0x52L, channelLeak), 0.0F, 1.0F),
                Mth.clamp(green + signed(seed ^ 0x47L, channelLeak), 0.0F, 1.0F),
                Mth.clamp(blue + signed(seed ^ 0x42L, channelLeak), 0.0F, 1.0F),
                Mth.clamp(alpha * (0.35F + unit(seed ^ 0x41L) * (0.90F + intensity * 1.25F)), 0.05F, 1.0F)
        );
    }

    private static int corruptStage(CorruptionEffectStack stack, int stage, int size) {
        int bound = Math.max(1, size);
        float intensity = destroyIntensity(stack);
        if (intensity <= 0.01F) {
            return Math.floorMod(stage, bound);
        }

        long gameTime = Minecraft.getInstance().level == null ? 0L : Minecraft.getInstance().level.getGameTime();
        long seed = stack.stableLong(CorruptionSurface.TEXTURE_MEMORY, "destroy_stage", stage ^ 0x44535452);
        if (!stack.extreme(CorruptionSurface.TEXTURE_MEMORY)
                && stack.unit(CorruptionSurface.TEXTURE_MEMORY, "destroy_stage", stage ^ (int) gameTime) > 0.18F + intensity * 0.74F) {
            return Math.floorMod(stage, bound);
        }

        int timeStride = 1 + Math.round(intensity * 18.0F);
        int temporal = (int) Math.floorMod(gameTime * timeStride + (seed >>> 24), bound);
        int jump = Math.round((unit(seed ^ gameTime) * 2.0F - 1.0F) * intensity * (bound - 1));
        return Math.floorMod(stage + temporal + jump, bound);
    }

    private static float destroyIntensity(CorruptionEffectStack stack) {
        float texture = stack.extreme(CorruptionSurface.TEXTURE_MEMORY)
                ? 1.0F
                : stack.intensity(CorruptionSurface.TEXTURE_MEMORY);
        return Mth.clamp(texture, 0.0F, 1.0F);
    }

    private static float signed(long value, float amplitude) {
        return (unit(value) * 2.0F - 1.0F) * amplitude;
    }

    private static float unit(long value) {
        value ^= value >>> 33;
        value *= 0xff51afd7ed558ccdL;
        value ^= value >>> 33;
        value *= 0xc4ceb9fe1a85ec53L;
        value ^= value >>> 33;
        return ((value >>> 40) & 0xFF_FFFFL) / 16_777_215.0F;
    }
}
