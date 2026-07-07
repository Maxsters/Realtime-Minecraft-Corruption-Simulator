package com.maxsters.realtimeminecraftcorruptionsimulator.client.effects;

import com.mojang.blaze3d.vertex.VertexConsumer;
import net.minecraft.util.Mth;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.api.distmarker.OnlyIn;

@OnlyIn(Dist.CLIENT)
final class CorruptedMaterialVertexConsumer implements VertexConsumer {
    private final VertexConsumer delegate;
    private final long seed;
    private final float intensity;
    private final boolean extreme;
    private int vertexOrdinal;

    CorruptedMaterialVertexConsumer(VertexConsumer delegate, long seed, float intensity, boolean extreme) {
        this.delegate = delegate;
        this.seed = seed;
        this.intensity = intensity;
        this.extreme = extreme;
    }

    @Override
    public VertexConsumer vertex(double x, double y, double z) {
        delegate.vertex(x, y, z);
        return this;
    }

    @Override
    public VertexConsumer color(int red, int green, int blue, int alpha) {
        float[] color = corruptMaterialColor(red / 255.0F, green / 255.0F, blue / 255.0F, alpha / 255.0F, seed, intensity, vertexOrdinal);
        delegate.color(
                Mth.clamp(Math.round(color[0] * 255.0F), 0, 255),
                Mth.clamp(Math.round(color[1] * 255.0F), 0, 255),
                Mth.clamp(Math.round(color[2] * 255.0F), 0, 255),
                Mth.clamp(Math.round(color[3] * 255.0F), 0, 255)
        );
        return this;
    }

    @Override
    public VertexConsumer uv(float u, float v) {
        float[] corrupted = corruptMaterialUv(u, v, seed, intensity, vertexOrdinal);
        delegate.uv(corrupted[0], corrupted[1]);
        return this;
    }

    @Override
    public VertexConsumer overlayCoords(int u, int v) {
        delegate.overlayCoords(u, v);
        return this;
    }

    @Override
    public VertexConsumer uv2(int u, int v) {
        delegate.uv2(u, v);
        return this;
    }

    @Override
    public VertexConsumer normal(float x, float y, float z) {
        delegate.normal(x, y, z);
        return this;
    }

    @Override
    public void endVertex() {
        delegate.endVertex();
        vertexOrdinal++;
    }

    @Override
    public void defaultColor(int red, int green, int blue, int alpha) {
        delegate.defaultColor(red, green, blue, alpha);
    }

    @Override
    public void unsetDefaultColor() {
        delegate.unsetDefaultColor();
    }

    @Override
    public void vertex(float x, float y, float z, float red, float green, float blue, float alpha, float u, float v, int overlay, int light, float normalX, float normalY, float normalZ) {
        int ordinal = vertexOrdinal++;
        float[] color = extreme || unitHash(seed ^ ordinal * 0x4D434F4CL) < 0.18F + intensity * 0.64F
                ? corruptMaterialColor(red, green, blue, alpha, seed, intensity, ordinal)
                : new float[]{red, green, blue, alpha};
        float[] uv = corruptMaterialUv(u, v, seed, intensity, ordinal);
        delegate.vertex(x, y, z, color[0], color[1], color[2], color[3], uv[0], uv[1], overlay, light, normalX, normalY, normalZ);
    }

    private static float[] corruptMaterialUv(float u, float v, long seed, float intensity, int ordinal) {
        long vertexSeed = mixLong(seed ^ (long) ordinal * 0x9E3779B97F4A7C15L);
        float centeredU = u - 0.5F;
        float centeredV = v - 0.5F;
        float power = intensity * intensity;
        int mode = Math.floorMod((int) (seed >>> 32), 6);
        float scaleU = 1.0F + unitHash(seed ^ 0x4D415455L) * (2.0F + power * 18.0F);
        float scaleV = 1.0F + unitHash(seed ^ 0x4D415456L) * (2.0F + power * 16.0F);
        if (mode == 1 || mode == 4) {
            scaleU = Math.max(0.02F, 0.35F - intensity * 0.30F);
        }
        if (mode == 2 || mode == 4) {
            scaleV = Math.max(0.02F, 0.35F - intensity * 0.30F);
        }
        float shearU = centeredV * signedHash(seed ^ 0x4D534855L, intensity * 3.2F);
        float shearV = centeredU * signedHash(seed ^ 0x4D534856L, intensity * 2.8F);
        float offsetU = signedHash(seed ^ 0x4D4F4646L, 0.35F + power * 4.4F);
        float offsetV = signedHash(seed ^ 0x4D564F46L, 0.35F + power * 4.0F);
        float corruptedU = 0.5F + centeredU * scaleU + shearU + offsetU;
        float corruptedV = 0.5F + centeredV * scaleV + shearV + offsetV;
        if (unitHash(vertexSeed ^ 0x4D515541L) < 0.18F + intensity * 0.54F) {
            float step = 0.04F + unitHash(vertexSeed ^ 0x4D535445L) * (0.18F + intensity * 0.72F);
            corruptedU = Math.round(corruptedU / step) * step;
        }
        if (unitHash(vertexSeed ^ 0x4D515556L) < 0.18F + intensity * 0.50F) {
            float step = 0.04F + unitHash(vertexSeed ^ 0x4D535456L) * (0.18F + intensity * 0.64F);
            corruptedV = Math.round(corruptedV / step) * step;
        }
        return new float[] {
                Mth.clamp(corruptedU, -4.0F, 5.0F),
                Mth.clamp(corruptedV, -4.0F, 5.0F)
        };
    }

    private static float[] corruptMaterialColor(float red, float green, float blue, float alpha, long seed, float intensity, int ordinal) {
        long vertexSeed = mixLong(seed ^ (long) ordinal * 0xC2B2AE3D27D4EB4FL);
        if (unitHash(vertexSeed ^ 0x4D434F4CL) > 0.16F + intensity * 0.72F) {
            return new float[]{red, green, blue, alpha};
        }
        int mode = Math.floorMod((int) (seed >>> 41), 5);
        if (mode == 0) {
            return new float[]{1.0F, 0.0F, 1.0F, Math.max(alpha, 0.72F)};
        }
        if (mode == 1) {
            float channel = unitHash(vertexSeed ^ 0x4D574849L) < 0.5F ? 0.0F : 1.0F;
            return new float[]{channel, channel, channel, Math.max(alpha, 0.62F)};
        }
        float targetRed = unitHash(seed ^ 0x4D524544L);
        float targetGreen = unitHash(seed ^ 0x4D475245L);
        float targetBlue = unitHash(seed ^ 0x4D424C55L);
        float wash = Mth.clamp(0.35F + intensity * 0.65F, 0.0F, 1.0F);
        return new float[]{
                Mth.clamp(red * (1.0F - wash) + targetRed * wash, 0.0F, 1.0F),
                Mth.clamp(green * (1.0F - wash) + targetGreen * wash, 0.0F, 1.0F),
                Mth.clamp(blue * (1.0F - wash) + targetBlue * wash, 0.0F, 1.0F),
                Mth.clamp(alpha, 0.20F, 1.0F)
        };
    }

    private static float signedHash(long hash, float amplitude) {
        return (unitHash(hash) * 2.0F - 1.0F) * amplitude;
    }

    private static long mixLong(long value) {
        value ^= value >>> 33;
        value *= 0xff51afd7ed558ccdL;
        value ^= value >>> 33;
        value *= 0xc4ceb9fe1a85ec53L;
        value ^= value >>> 33;
        return value;
    }

    private static float unitHash(long value) {
        return ((mixLong(value) >>> 40) & 0xFF_FFFFL) / 16_777_215.0F;
    }
}
