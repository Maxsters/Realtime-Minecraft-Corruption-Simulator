package com.maxsters.realtimeminecraftcorruptionsimulator.client.hooks;

import com.maxsters.realtimeminecraftcorruptionsimulator.client.effects.ClientCorruptionEffects;
import com.maxsters.realtimeminecraftcorruptionsimulator.profile.CorruptionEffectStack;
import com.maxsters.realtimeminecraftcorruptionsimulator.profile.CorruptionSurface;
import com.mojang.blaze3d.platform.NativeImage;
import com.mojang.blaze3d.systems.RenderSystem;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.LightTexture;
import net.minecraft.client.renderer.texture.DynamicTexture;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.util.Mth;
import net.minecraft.world.level.BlockAndTintGetter;
import net.minecraft.world.level.LightLayer;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.api.distmarker.OnlyIn;

import java.lang.reflect.Field;

@OnlyIn(Dist.CLIENT)
public final class LightingCorruptionHooks {
    private static int pendingLightTextureRefreshPasses;
    private static int pendingLightTextureResetPasses;
    private static int guiLightProtectionDepth;
    private static Field lightTextureTextureField;
    private static Field lightTexturePixelsField;

    private LightingCorruptionHooks() {
    }

    public static void requestLightTextureRefresh() {
        pendingLightTextureRefreshPasses = Math.max(pendingLightTextureRefreshPasses, 8);
    }

    public static void requestLightTextureReset() {
        pendingLightTextureResetPasses = Math.max(pendingLightTextureResetPasses, 4);
        requestLightTextureRefresh();
        restoreLightTextureNow();
    }

    public static boolean consumeLightTextureRefreshRequest() {
        if (pendingLightTextureRefreshPasses <= 0) {
            return false;
        }
        pendingLightTextureRefreshPasses--;
        return true;
    }

    public static boolean consumeLightTextureResetRequest() {
        if (pendingLightTextureResetPasses <= 0) {
            return false;
        }
        pendingLightTextureResetPasses--;
        return true;
    }

    public static boolean lightingCorruptionActive(CorruptionEffectStack stack) {
        return lightingIntensity(stack) > 0.01F;
    }

    public static void beginGuiLightProtection() {
        if (!shouldProtectGuiLightTexture()) {
            return;
        }
        if (guiLightProtectionDepth++ == 0) {
            restoreLightTextureNow();
        }
        RenderSystem.setShaderColor(1.0F, 1.0F, 1.0F, 1.0F);
    }

    public static void endGuiLightProtection() {
        if (guiLightProtectionDepth <= 0) {
            return;
        }
        guiLightProtectionDepth--;
        RenderSystem.setShaderColor(1.0F, 1.0F, 1.0F, 1.0F);
        if (guiLightProtectionDepth == 0) {
            requestLightTextureRefresh();
        }
    }

    private static boolean shouldProtectGuiLightTexture() {
        CorruptionEffectStack stack = ClientCorruptionEffects.currentForWorldRendering();
        return lightingCorruptionActive(stack) && !stack.activeOrExtreme(CorruptionSurface.GUI_SURFACE);
    }

    public static void restoreLightTextureNow() {
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft == null || minecraft.gameRenderer == null) {
            return;
        }
        try {
            LightTexture lightTexture = minecraft.gameRenderer.lightTexture();
            NativeImage pixels = lightPixels(lightTexture);
            DynamicTexture texture = dynamicLightTexture(lightTexture);
            if (pixels == null || texture == null) {
                return;
            }
            for (int y = 0; y < 16; y++) {
                for (int x = 0; x < 16; x++) {
                    pixels.setPixelRGBA(x, y, -1);
                }
            }
            texture.upload();
            RenderSystem.setShaderColor(1.0F, 1.0F, 1.0F, 1.0F);
        } catch (RuntimeException ignored) {
        }
    }

    public static boolean mutateAmbientOcclusionSwitch(boolean vanilla) {
        CorruptionEffectStack stack = ClientCorruptionEffects.currentForWorldRendering();
        float intensity = lightingIntensity(stack);
        if (intensity <= 0.015F) {
            return vanilla;
        }

        long seed = stack.stableLong(CorruptionSurface.LIGHT_FIELD, 0x414F5357);
        int mode = Math.floorMod((int) (seed >>> 36), 7);
        if (!stack.extreme(CorruptionSurface.LIGHT_FIELD) && unit(seed ^ 0x47415445L) > 0.08D + intensity * 0.42D) {
            return vanilla;
        }

        return switch (mode) {
            case 0, 1 -> false;
            case 2 -> true;
            case 3 -> !vanilla;
            case 4 -> stack.unit(CorruptionSurface.LIGHT_FIELD, 0x414F4652) > 0.50F;
            case 5 -> stack.bucket(CorruptionSurface.LIGHT_FIELD, 0x414F4255, 8) % 3 != 0;
            default -> vanilla;
        };
    }

    public static int mutateBrightness(BlockAndTintGetter level, LightLayer layer, BlockPos pos, int value) {
        CorruptionEffectStack stack = ClientCorruptionEffects.currentForWorldRendering();
        float intensity = lightingIntensity(stack);
        if (intensity <= 0.01F || layer == null) {
            return clampLight(value);
        }

        int salt = layer == LightLayer.SKY ? 0x534B5942 : 0x424C4B42;
        return mutateLightComponent(stack, pos, clampLight(value), intensity, salt, layer == LightLayer.SKY);
    }

    public static int mutatePackedLight(BlockAndTintGetter level, BlockState state, BlockPos pos, int packedLight) {
        CorruptionEffectStack stack = ClientCorruptionEffects.currentForWorldRendering();
        float intensity = lightingIntensity(stack);
        if (intensity <= 0.01F) {
            return packedLight;
        }

        int block = clampLight(LightTexture.block(packedLight));
        int sky = clampLight(LightTexture.sky(packedLight));
        long seed = lightingSeed(stack, state, pos, 0x5041434B);
        int mode = Math.floorMod((int) (seed >>> 29), 10);
        boolean extreme = stack.extreme(CorruptionSurface.LIGHT_FIELD);
        double gate = extreme ? 0.96D : 0.18D + intensity * 0.72D;
        if (unit(seed ^ 0x50474C4FL) > gate) {
            return packedLight;
        }

        if (mode == 0 || (extreme && unit(seed ^ 0x53574150L) < 0.18D)) {
            int swapped = block;
            block = sky;
            sky = swapped;
        }

        if (mode == 1) {
            block = 15 - block;
        } else if (mode == 2) {
            sky = 15 - sky;
        } else if (mode == 3) {
            block = unit(seed ^ 0x42465354L) < 0.5D ? 0 : 15;
        } else if (mode == 4) {
            sky = unit(seed ^ 0x53465354L) < 0.5D ? 0 : 15;
        }

        block = mutateLightComponent(stack, pos, block, intensity, 0x424C4F43 ^ mode, false);
        sky = mutateLightComponent(stack, pos, sky, intensity, 0x534B5953 ^ mode, true);
        if (extreme && unit(seed ^ 0x46554C4CL) < 0.10D + intensity * 0.12D) {
            return unit(seed ^ 0x4441524BL) < 0.5D ? 0 : LightTexture.FULL_BRIGHT;
        }
        return LightTexture.pack(block, sky);
    }

    public static float mutateShade(BlockAndTintGetter level, Direction direction, boolean shade, float value) {
        CorruptionEffectStack stack = ClientCorruptionEffects.currentForWorldRendering();
        float intensity = lightingIntensity(stack);
        if (intensity <= 0.01F || direction == null) {
            return value;
        }

        String targetId = "shade:" + direction.getName() + ":" + shade;
        long seed = stack.stableLong(CorruptionSurface.LIGHT_FIELD, targetId, 0x53484144);
        if (!stack.extreme(CorruptionSurface.LIGHT_FIELD) && unit(seed ^ 0x53484754L) > 0.16D + intensity * 0.58D) {
            return value;
        }

        int mode = Math.floorMod((int) (seed >>> 31), 8);
        float result = switch (mode) {
            case 0 -> 1.0F - value;
            case 1 -> value * (0.10F + (float) unit(seed ^ 0x44494D4DL) * (0.72F + intensity));
            case 2 -> value + ((float) unit(seed ^ 0x4F464653L) * 2.0F - 1.0F) * (0.18F + intensity * 0.94F);
            case 3 -> (float) unit(seed ^ 0x5245504CL) * (0.38F + intensity * 1.20F);
            case 4 -> quantize(value, Math.max(0.05F, 0.44F - intensity * 0.28F));
            case 5 -> direction.getAxis() == Direction.Axis.Y ? value * (1.0F + intensity * 0.82F) : value * (1.0F - intensity * 0.64F);
            case 6 -> shade ? 0.04F + intensity * 1.42F : value * (0.18F + intensity * 0.54F);
            default -> value;
        };
        return Mth.clamp(result, 0.0F, 1.65F);
    }

    public static float mutateBrightnessCurve(int lightLevel, float value) {
        CorruptionEffectStack stack = ClientCorruptionEffects.currentForWorldRendering();
        float intensity = lightingIntensity(stack);
        if (intensity <= 0.01F) {
            return value;
        }

        long seed = stack.stableLong(CorruptionSurface.LIGHT_FIELD, 0x43555256) ^ (long) lightLevel * 0x9E3779B97F4A7C15L;
        if (!stack.extreme(CorruptionSurface.LIGHT_FIELD) && unit(seed ^ 0x47544352L) > 0.12D + intensity * 0.46D) {
            return value;
        }

        int mode = Math.floorMod((int) (seed >>> 33), 7);
        float result = switch (mode) {
            case 0 -> 1.0F - value;
            case 1 -> value * (0.18F + intensity * 1.90F);
            case 2 -> (float) Math.pow(Mth.clamp(value, 0.0F, 1.0F), 0.18D + unit(seed ^ 0x504F5745L) * 4.75D);
            case 3 -> quantize(value, Math.max(0.03125F, 0.42F - intensity * 0.30F));
            case 4 -> (float) unit(seed ^ 0x52414E44L);
            case 5 -> lightLevel <= (int) (unit(seed ^ 0x54485245L) * 15.0D) ? 0.0F : 1.0F;
            default -> value + ((float) unit(seed ^ 0x4F464653L) * 2.0F - 1.0F) * intensity;
        };
        return Mth.clamp(result, 0.0F, 1.75F);
    }

    public static void mutateAmbientOcclusion(BlockAndTintGetter level, BlockState state, BlockPos pos, Direction face, float[] brightness, int[] lightmap) {
        if (brightness == null || lightmap == null || brightness.length < 4 || lightmap.length < 4 || face == null) {
            return;
        }

        CorruptionEffectStack stack = ClientCorruptionEffects.currentForWorldRendering();
        float intensity = lightingIntensity(stack);
        if (intensity <= 0.01F) {
            return;
        }

        long seed = lightingSeed(stack, state, pos, 0x414F4643) ^ (long) face.get3DDataValue() * 0x632BE59BD9B4E019L;
        double gate = stack.extreme(CorruptionSurface.LIGHT_FIELD) ? 1.0D : 0.20D + intensity * 0.70D;
        if (unit(seed ^ 0x414F4754L) > gate) {
            return;
        }

        int mode = Math.floorMod((int) (seed >>> 27), 9);
        switch (mode) {
            case 0 -> collapseAo(seed, brightness, lightmap);
            case 1 -> rotateAo(seed, brightness, lightmap);
            case 2 -> invertAo(brightness, lightmap);
            case 3 -> spikeAo(seed, brightness, lightmap, intensity);
            case 4 -> quantizeAo(brightness, lightmap, intensity);
            case 5 -> smearAo(seed, brightness, lightmap, intensity);
            case 6 -> swapLightChannels(lightmap);
            case 7 -> scrambleAo(seed, brightness, lightmap, intensity);
            default -> {
                if (stack.extreme(CorruptionSurface.LIGHT_FIELD)) {
                    spikeAo(seed, brightness, lightmap, intensity);
                    rotateAo(seed >>> 7, brightness, lightmap);
                }
            }
        }

        for (int index = 0; index < 4; index++) {
            brightness[index] = Mth.clamp(brightness[index], 0.0F, 1.85F);
            lightmap[index] = mutatePackedLight(level, state, pos, lightmap[index]);
        }
    }

    public static int mutateLightmapPixel(int x, int y, int color) {
        CorruptionEffectStack stack = ClientCorruptionEffects.currentForWorldRendering();
        float intensity = lightingIntensity(stack);
        if (intensity <= 0.01F) {
            return color;
        }

        long seed = stack.stableLong(CorruptionSurface.LIGHT_FIELD, 0x4C4D4150)
                ^ (long) x * 0x9E3779B97F4A7C15L
                ^ (long) y * 0x632BE59BD9B4E019L;
        double gate = stack.extreme(CorruptionSurface.LIGHT_FIELD) ? 1.0D : 0.18D + intensity * 0.64D;
        if (unit(seed ^ 0x4C4D4754L) > gate) {
            return color;
        }

        int red = color & 0xFF;
        int green = color >> 8 & 0xFF;
        int blue = color >> 16 & 0xFF;
        int mode = Math.floorMod((int) (seed >>> 35), 10);
        switch (mode) {
            case 0 -> {
                red = 255 - red;
                green = 255 - green;
                blue = 255 - blue;
            }
            case 1 -> {
                int swap = red;
                red = blue;
                blue = green;
                green = swap;
            }
            case 2 -> {
                red = quantizeByte(red, intensity);
                green = quantizeByte(green, intensity);
                blue = quantizeByte(blue, intensity);
            }
            case 3 -> {
                float scale = 0.08F + (float) unit(seed ^ 0x5343414CL) * (0.80F + intensity * 2.60F);
                red = clampByte(Math.round(red * scale));
                green = clampByte(Math.round(green * (0.12F + intensity * 1.70F)));
                blue = clampByte(Math.round(blue * (0.10F + (float) unit(seed ^ 0x42534341L) * 2.40F)));
            }
            case 4 -> {
                red = unit(seed ^ 0x524544L) < intensity ? 255 : red / 4;
                green = unit(seed ^ 0x475245L) < intensity * 0.72D ? 255 : green / 5;
                blue = unit(seed ^ 0x424C55L) < intensity * 0.48D ? 255 : blue / 6;
            }
            case 5 -> {
                int missing = unit(seed ^ 0x4D495353L) < 0.5D ? 0xFF00FF : 0x00FF00;
                red = missing >> 16 & 0xFF;
                green = missing >> 8 & 0xFF;
                blue = missing & 0xFF;
            }
            case 6 -> {
                int threshold = (int) (unit(seed ^ 0x54485245L) * 255.0D);
                int average = (red + green + blue) / 3;
                red = average > threshold ? 255 : 0;
                green = average > threshold ? 255 : 0;
                blue = average > threshold ? 255 : 0;
            }
            case 7 -> {
                red = clampByte(red + Math.round(((float) unit(seed ^ 0x524F4646L) * 2.0F - 1.0F) * 255.0F * intensity));
                green = clampByte(green + Math.round(((float) unit(seed ^ 0x474F4646L) * 2.0F - 1.0F) * 255.0F * intensity));
                blue = clampByte(blue + Math.round(((float) unit(seed ^ 0x424F4646L) * 2.0F - 1.0F) * 255.0F * intensity));
            }
            default -> {
                if (stack.extreme(CorruptionSurface.LIGHT_FIELD)) {
                    red = unit(seed ^ 0x455852L) < 0.5D ? 0 : 255;
                    green = unit(seed ^ 0x455847L) < 0.5D ? 0 : 255;
                    blue = unit(seed ^ 0x455842L) < 0.5D ? 0 : 255;
                }
            }
        }
        return 0xFF000000 | blue << 16 | green << 8 | red;
    }

    private static DynamicTexture dynamicLightTexture(LightTexture lightTexture) {
        Field field = lightTextureTextureField;
        if (field == null) {
            field = findLightTextureField("lightTexture", "f_109870_");
            lightTextureTextureField = field;
        }
        if (field == null) {
            return null;
        }
        try {
            Object value = field.get(lightTexture);
            return value instanceof DynamicTexture texture ? texture : null;
        } catch (IllegalAccessException | RuntimeException ignored) {
            return null;
        }
    }

    private static NativeImage lightPixels(LightTexture lightTexture) {
        Field field = lightTexturePixelsField;
        if (field == null) {
            field = findLightTextureField("lightPixels", "f_109871_");
            lightTexturePixelsField = field;
        }
        if (field == null) {
            return null;
        }
        try {
            Object value = field.get(lightTexture);
            return value instanceof NativeImage pixels ? pixels : null;
        } catch (IllegalAccessException | RuntimeException ignored) {
            return null;
        }
    }

    private static Field findLightTextureField(String... names) {
        for (String name : names) {
            try {
                Field field = LightTexture.class.getDeclaredField(name);
                field.setAccessible(true);
                return field;
            } catch (NoSuchFieldException ignored) {
            }
        }
        return null;
    }

    private static float lightingIntensity(CorruptionEffectStack stack) {
        if (stack == null) {
            return 0.0F;
        }
        float light = stack.extreme(CorruptionSurface.LIGHT_FIELD) ? 1.0F : stack.intensity(CorruptionSurface.LIGHT_FIELD);
        return Mth.clamp(light, 0.0F, 1.0F);
    }

    private static int mutateLightComponent(CorruptionEffectStack stack, BlockPos pos, int value, float intensity, int salt, boolean sky) {
        long seed = stack.stableLong(CorruptionSurface.LIGHT_FIELD, salt)
                ^ (pos == null ? 0L : pos.asLong() * 0x9E3779B97F4A7C15L)
                ^ (sky ? 0x534B594C41594552L : 0x424C4F434B4C5952L);
        int mode = Math.floorMod((int) (seed >>> 32), 9);
        int result = value;
        switch (mode) {
            case 0 -> result = 15 - result;
            case 1 -> result = unit(seed ^ 0x42494E4CL) < 0.5D ? 0 : 15;
            case 2 -> {
                int step = Math.max(1, 1 + Math.round(intensity * 5.0F));
                result = Math.round((float) result / step) * step;
            }
            case 3 -> result = clampLight(Math.round(result * (0.12F + (float) unit(seed ^ 0x5343414CL) * (0.85F + intensity * 2.40F))));
            case 4 -> result = clampLight(Math.round((float) unit(seed ^ 0x5245504CL) * 15.0F));
            case 5 -> {
                int stripe = pos == null ? 0 : Math.floorMod(pos.getX() * 3 + pos.getY() * 5 + pos.getZ() * 7, 3 + Math.round(9.0F * (1.0F - intensity)));
                if (stripe == 0) {
                    result = sky ? 0 : 15;
                }
            }
            case 6 -> result = value <= Math.round(unit(seed ^ 0x54485245L) * 15.0D) ? 0 : 15;
            case 7 -> result = clampLight(Math.round(Mth.lerp(intensity, value, 15 - value)));
            default -> {
            }
        }
        if (stack.extreme(CorruptionSurface.LIGHT_FIELD) && unit(seed ^ 0x45585452L) < 0.10D) {
            result = unit(seed ^ 0x45585456L) < 0.5D ? 0 : 15;
        }
        return clampLight(result);
    }

    private static void collapseAo(long seed, float[] brightness, int[] lightmap) {
        int source = Math.floorMod((int) (seed >>> 17), 4);
        float bright = brightness[source];
        int light = lightmap[source];
        for (int index = 0; index < 4; index++) {
            brightness[index] = bright;
            lightmap[index] = light;
        }
    }

    private static void rotateAo(long seed, float[] brightness, int[] lightmap) {
        int shift = 1 + Math.floorMod((int) (seed >>> 21), 3);
        float[] brightCopy = brightness.clone();
        int[] lightCopy = lightmap.clone();
        for (int index = 0; index < 4; index++) {
            int source = (index + shift) & 3;
            brightness[index] = brightCopy[source];
            lightmap[index] = lightCopy[source];
        }
    }

    private static void invertAo(float[] brightness, int[] lightmap) {
        for (int index = 0; index < 4; index++) {
            brightness[index] = 1.0F - brightness[index];
            int block = 15 - clampLight(LightTexture.block(lightmap[index]));
            int sky = 15 - clampLight(LightTexture.sky(lightmap[index]));
            lightmap[index] = LightTexture.pack(block, sky);
        }
    }

    private static void spikeAo(long seed, float[] brightness, int[] lightmap, float intensity) {
        int corner = Math.floorMod((int) (seed >>> 19), 4);
        for (int index = 0; index < 4; index++) {
            long cornerSeed = seed ^ (long) index * 0xD1B54A32D192ED03L;
            float scale = index == corner ? 0.0F : 0.18F + (float) unit(cornerSeed ^ 0x53434C45L) * (0.65F + intensity * 1.65F);
            brightness[index] *= scale;
            if (index == corner || unit(cornerSeed ^ 0x464C4950L) < intensity * 0.36D) {
                lightmap[index] = unit(cornerSeed ^ 0x464C4956L) < 0.5D ? 0 : LightTexture.FULL_BRIGHT;
            }
        }
    }

    private static void quantizeAo(float[] brightness, int[] lightmap, float intensity) {
        float step = Math.max(0.03125F, 0.30F - intensity * 0.22F);
        for (int index = 0; index < 4; index++) {
            brightness[index] = quantize(brightness[index], step);
            int block = quantizeLight(LightTexture.block(lightmap[index]), intensity);
            int sky = quantizeLight(LightTexture.sky(lightmap[index]), intensity);
            lightmap[index] = LightTexture.pack(block, sky);
        }
    }

    private static void smearAo(long seed, float[] brightness, int[] lightmap, float intensity) {
        float aggregate = 0.0F;
        for (float value : brightness) {
            aggregate += value;
        }
        aggregate *= 0.25F;
        for (int index = 0; index < 4; index++) {
            long cornerSeed = seed ^ (long) index * 0x9E3779B97F4A7C15L;
            brightness[index] = aggregate + ((float) unit(cornerSeed ^ 0x534D4541L) * 2.0F - 1.0F) * (0.18F + intensity * 0.86F);
            int source = Math.floorMod((int) (cornerSeed >>> 39), 4);
            lightmap[index] = lightmap[source];
        }
    }

    private static void swapLightChannels(int[] lightmap) {
        for (int index = 0; index < 4; index++) {
            int block = clampLight(LightTexture.block(lightmap[index]));
            int sky = clampLight(LightTexture.sky(lightmap[index]));
            lightmap[index] = LightTexture.pack(sky, block);
        }
    }

    private static void scrambleAo(long seed, float[] brightness, int[] lightmap, float intensity) {
        for (int index = 0; index < 4; index++) {
            long cornerSeed = seed ^ (long) index * 0x94D049BB133111EBL;
            brightness[index] = (float) unit(cornerSeed ^ 0x42524947L) * (0.35F + intensity * 1.40F);
            int block = (int) Math.round(unit(cornerSeed ^ 0x424C4B4CL) * 15.0D);
            int sky = (int) Math.round(unit(cornerSeed ^ 0x534B594CL) * 15.0D);
            lightmap[index] = LightTexture.pack(block, sky);
        }
    }

    private static long lightingSeed(CorruptionEffectStack stack, BlockState state, BlockPos pos, int salt) {
        long seed = stack.stableLong(CorruptionSurface.LIGHT_FIELD, salt);
        if (pos != null) {
            seed ^= pos.asLong() * 0x9E3779B97F4A7C15L;
        }
        if (state != null) {
            seed ^= (long) Block.getId(state) * 0x632BE59BD9B4E019L;
        }
        return mixLong(seed);
    }

    private static int quantizeLight(int value, float intensity) {
        int step = Math.max(1, 1 + Math.round(intensity * 6.0F));
        return clampLight(Math.round((float) clampLight(value) / step) * step);
    }

    private static int quantizeByte(int value, float intensity) {
        int step = Math.max(1, 16 + Math.round(intensity * 80.0F));
        return clampByte(Math.round((float) value / step) * step);
    }

    private static float quantize(float value, float step) {
        return Math.round(value / step) * step;
    }

    private static int clampLight(int value) {
        return Mth.clamp(value, 0, 15);
    }

    private static int clampByte(int value) {
        return Mth.clamp(value, 0, 255);
    }

    private static double unit(long value) {
        return ((mixLong(value) >>> 40) & 0xFF_FFFFL) / 16_777_215.0D;
    }

    private static long mixLong(long value) {
        value ^= value >>> 33;
        value *= 0xff51afd7ed558ccdL;
        value ^= value >>> 33;
        value *= 0xc4ceb9fe1a85ec53L;
        value ^= value >>> 33;
        return value;
    }
}
