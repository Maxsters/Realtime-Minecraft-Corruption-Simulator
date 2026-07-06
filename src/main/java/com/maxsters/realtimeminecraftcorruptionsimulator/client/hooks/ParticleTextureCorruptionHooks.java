package com.maxsters.realtimeminecraftcorruptionsimulator.client.hooks;

import com.maxsters.realtimeminecraftcorruptionsimulator.client.effects.ClientCorruptionEffects;
import com.maxsters.realtimeminecraftcorruptionsimulator.client.access.TextureSheetParticleAccessor;
import com.maxsters.realtimeminecraftcorruptionsimulator.profile.CorruptionEffectStack;
import com.maxsters.realtimeminecraftcorruptionsimulator.profile.CorruptionSurface;
import com.mojang.blaze3d.vertex.VertexConsumer;
import net.minecraft.client.particle.Particle;
import net.minecraft.client.particle.TextureSheetParticle;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.api.distmarker.OnlyIn;

import java.lang.reflect.Field;
import java.util.Map;
import java.util.WeakHashMap;

@OnlyIn(Dist.CLIENT)
public final class ParticleTextureCorruptionHooks {
    private static final ThreadLocal<ParticleTextureContext> PARTICLE_CONTEXT = new ThreadLocal<>();
    private static final ParticleTextureContext DISABLED_CONTEXT = new ParticleTextureContext(0L, 0.0F, false);
    private static final Map<TextureAtlasSprite, String> SPRITE_TARGET_IDS = new WeakHashMap<>();
    private static final ClassValue<String> CLASS_TARGET_IDS = new ClassValue<>() {
        @Override
        protected String computeValue(Class<?> type) {
            return "particle_texture_class:" + type.getName();
        }
    };
    private static Field spriteField;
    private static boolean reflectionChecked;

    private ParticleTextureCorruptionHooks() {
    }

    public static void beginParticleRender(Particle particle) {
        ParticleTextureContext context = createContext(particle);
        if (context == null) {
            PARTICLE_CONTEXT.set(DISABLED_CONTEXT);
        } else {
            PARTICLE_CONTEXT.set(context);
        }
    }

    public static void endParticleRender() {
        PARTICLE_CONTEXT.remove();
    }

    public static VertexConsumer uv(Particle particle, VertexConsumer consumer, float u, float v) {
        ParticleTextureContext context = currentContext(particle);
        if (context == null) {
            return consumer.uv(u, v);
        }
        return context.uv(consumer, u, v);
    }

    public static VertexConsumer color(Particle particle, VertexConsumer consumer, float red, float green, float blue, float alpha) {
        ParticleTextureContext context = currentContext(particle);
        if (context == null) {
            return consumer.color(red, green, blue, alpha);
        }
        return context.color(consumer, red, green, blue, alpha);
    }

    public static VertexConsumer wrapParticleConsumer(Particle particle, VertexConsumer consumer) {
        if (particle == null || consumer == null) {
            return consumer;
        }

        ParticleTextureContext context = createContext(particle);
        return context == null ? consumer : new CorruptedParticleVertexConsumer(consumer, context);
    }

    private static ParticleTextureContext currentContext(Particle particle) {
        ParticleTextureContext context = PARTICLE_CONTEXT.get();
        if (context == DISABLED_CONTEXT) {
            return null;
        }
        if (context != null) {
            return context;
        }
        return createContext(particle);
    }

    private static ParticleTextureContext createContext(Particle particle) {
        if (particle == null) {
            return null;
        }

        CorruptionEffectStack stack = ClientCorruptionEffects.currentForWorldRendering();
        if (!stack.activeOrExtreme(CorruptionSurface.TEXTURE_MEMORY)) {
            return null;
        }

        String targetId = targetId(particle);
        float intensity = stack.extreme(CorruptionSurface.TEXTURE_MEMORY)
                ? 1.0F
                : Mth.clamp(Math.max(
                        stack.targetIntensity(CorruptionSurface.TEXTURE_MEMORY, targetId),
                        stack.intensity(CorruptionSurface.TEXTURE_MEMORY) * 0.88F
                ) + stack.instability() * 0.08F, 0.0F, 1.0F);
        if (intensity <= 0.015F) {
            return null;
        }

        int salt = particleSalt(targetId);
        float chance = stack.extreme(CorruptionSurface.TEXTURE_MEMORY)
                ? 1.0F
                : Mth.clamp(0.12F + intensity * 0.80F + stack.instability() * 0.08F, 0.0F, 0.97F);
        if (!stack.extreme(CorruptionSurface.TEXTURE_MEMORY)
                && stack.unit(CorruptionSurface.TEXTURE_MEMORY, targetId, salt) > chance) {
            return null;
        }

        long seed = stack.stableLong(CorruptionSurface.TEXTURE_MEMORY, targetId, salt);
        return new ParticleTextureContext(seed, intensity, stack.extreme(CorruptionSurface.TEXTURE_MEMORY));
    }

    private static String targetId(Particle particle) {
        TextureAtlasSprite sprite = particleSprite(particle);
        if (sprite != null) {
            return SPRITE_TARGET_IDS.computeIfAbsent(sprite, ParticleTextureCorruptionHooks::spriteTargetId);
        }
        return CLASS_TARGET_IDS.get(particle.getClass());
    }

    private static String spriteTargetId(TextureAtlasSprite sprite) {
        if (sprite.contents() != null && sprite.contents().name() != null) {
            ResourceLocation atlas = sprite.atlasLocation();
            return "particle_texture_sprite:" + (atlas == null ? "unknown_atlas" : atlas) + ":" + sprite.contents().name();
        }
        return "particle_texture_sprite:unknown";
    }

    private static TextureAtlasSprite particleSprite(Particle particle) {
        if (!(particle instanceof TextureSheetParticle)) {
            return null;
        }
        if (ParticleFieldAccess.textureSheetAvailable() && particle instanceof TextureSheetParticleAccessor accessor) {
            return accessor.rmc$getSprite();
        }
        ensureSpriteReflection();
        if (spriteField == null) {
            return null;
        }
        try {
            return (TextureAtlasSprite) spriteField.get(particle);
        } catch (IllegalAccessException | ClassCastException ignored) {
            return null;
        }
    }

    private static void ensureSpriteReflection() {
        if (reflectionChecked) {
            return;
        }
        reflectionChecked = true;
        for (String name : new String[]{"sprite", "f_108321_"}) {
            try {
                Field field = TextureSheetParticle.class.getDeclaredField(name);
                field.setAccessible(true);
                spriteField = field;
                return;
            } catch (NoSuchFieldException ignored) {
            }
        }
    }

    private static int particleSalt(String targetId) {
        return targetId.hashCode() ^ 0x50415254;
    }

    private static final class ParticleTextureContext {
        private final long seed;
        private final float intensity;
        private final boolean extreme;
        private int vertexOrdinal;
        private int lastUvOrdinal;
        private float lastU;
        private float lastV;

        private ParticleTextureContext(long seed, float intensity, boolean extreme) {
            this.seed = seed;
            this.intensity = intensity;
            this.extreme = extreme;
        }

        private VertexConsumer uv(VertexConsumer consumer, float u, float v) {
            int ordinal = vertexOrdinal++;
            lastUvOrdinal = ordinal;
            corruptUv(u, v, ordinal);
            return consumer.uv(lastU, lastV);
        }

        private VertexConsumer color(VertexConsumer consumer, float red, float green, float blue, float alpha) {
            int color = corruptColor(
                    Mth.clamp(Math.round(red * 255.0F), 0, 255),
                    Mth.clamp(Math.round(green * 255.0F), 0, 255),
                    Mth.clamp(Math.round(blue * 255.0F), 0, 255),
                    Mth.clamp(Math.round(alpha * 255.0F), 0, 255),
                    Math.max(0, lastUvOrdinal)
            );
            return consumer.color(
                    ((color >>> 16) & 0xFF) / 255.0F,
                    ((color >>> 8) & 0xFF) / 255.0F,
                    (color & 0xFF) / 255.0F,
                    ((color >>> 24) & 0xFF) / 255.0F
            );
        }

        private void corruptUv(float u, float v, int ordinal) {
            int quad = ordinal >> 2;
            int corner = ordinal & 3;
            long quadSeed = mixLong(seed ^ (long) quad * 0x9E3779B97F4A7C15L);
            long vertexSeed = mixLong(quadSeed ^ (long) corner * 0xD1B54A32D192ED03L);
            float chance = extreme ? 1.0F : Mth.clamp(0.14F + intensity * 0.78F, 0.0F, 0.96F);
            if (unit(vertexSeed ^ 0x55564741L) > chance) {
                lastU = u;
                lastV = v;
                return;
            }

            float du = u - 0.5F;
            float dv = v - 0.5F;
            float power = intensity * intensity;
            int mode = Math.floorMod((int) (quadSeed >>> 29), 8);
            float scaleU = 1.0F + unit(quadSeed ^ 0x55534341L) * (1.4F + power * 12.0F);
            float scaleV = 1.0F + unit(quadSeed ^ 0x56534341L) * (1.4F + power * 10.0F);
            if (mode == 1 || mode == 5) {
                scaleU = Math.max(0.02F, 0.34F - intensity * 0.30F);
            }
            if (mode == 2 || mode == 5) {
                scaleV = Math.max(0.02F, 0.34F - intensity * 0.30F);
            }

            float shearU = signed(quadSeed ^ 0x55534852L, power * 2.8F);
            float shearV = signed(quadSeed ^ 0x56534852L, power * 2.4F);
            float offsetU = signed(quadSeed ^ 0x554F4646L, 0.025F + power * 0.36F);
            float offsetV = signed(quadSeed ^ 0x564F4646L, 0.025F + power * 0.34F);
            float corruptedU = 0.5F + du * scaleU + dv * shearU + offsetU;
            float corruptedV = 0.5F + dv * scaleV + du * shearV + offsetV;

            if (mode == 3 || mode == 6) {
                float step = 0.001953125F + unit(vertexSeed ^ 0x51554155L) * (0.012F + intensity * 0.070F);
                corruptedU = Math.round(corruptedU / step) * step;
            }
            if (mode == 4 || mode == 6) {
                float step = 0.001953125F + unit(vertexSeed ^ 0x51554156L) * (0.012F + intensity * 0.066F);
                corruptedV = Math.round(corruptedV / step) * step;
            }
            if (mode == 7 || (extreme && unit(vertexSeed ^ 0x45444745L) < 0.28F)) {
                corruptedU = unit(vertexSeed ^ 0x55454447L);
                corruptedV = unit(vertexSeed ^ 0x56454447L);
            }

            lastU = Mth.clamp(corruptedU, -2.0F, 3.0F);
            lastV = Mth.clamp(corruptedV, -2.0F, 3.0F);
        }

        private int corruptColor(int red, int green, int blue, int alpha, int ordinal) {
            long colorSeed = mixLong(seed ^ (long) ordinal * 0xC2B2AE3D27D4EB4FL);
            if (!extreme && unit(colorSeed ^ 0x434F4C52L) > 0.08F + intensity * 0.48F) {
                return alpha << 24 | red << 16 | green << 8 | blue;
            }

            int mode = Math.floorMod((int) (seed >>> 41), 6);
            if (mode == 0) {
                return Math.max(alpha, 0xB8) << 24 | 0xFF00FF;
            }
            if (mode == 1) {
                int channel = unit(colorSeed ^ 0x57484954L) < 0.5F ? 0 : 255;
                return alpha << 24 | channel << 16 | channel << 8 | channel;
            }

            float wash = Mth.clamp(0.28F + intensity * 0.68F, 0.0F, 1.0F);
            int targetRed = Math.round(unit(seed ^ 0x524544L) * 255.0F);
            int targetGreen = Math.round(unit(seed ^ 0x475245L) * 255.0F);
            int targetBlue = Math.round(unit(seed ^ 0x424C55L) * 255.0F);
            int corruptedRed = Mth.clamp(Math.round(red * (1.0F - wash) + targetRed * wash), 0, 255);
            int corruptedGreen = Mth.clamp(Math.round(green * (1.0F - wash) + targetGreen * wash), 0, 255);
            int corruptedBlue = Mth.clamp(Math.round(blue * (1.0F - wash) + targetBlue * wash), 0, 255);
            return alpha << 24 | corruptedRed << 16 | corruptedGreen << 8 | corruptedBlue;
        }
    }

    private static final class CorruptedParticleVertexConsumer implements VertexConsumer {
        private final VertexConsumer delegate;
        private final ParticleTextureContext context;

        private CorruptedParticleVertexConsumer(VertexConsumer delegate, ParticleTextureContext context) {
            this.delegate = delegate;
            this.context = context;
        }

        @Override
        public VertexConsumer vertex(double x, double y, double z) {
            delegate.vertex(x, y, z);
            return this;
        }

        @Override
        public VertexConsumer color(int red, int green, int blue, int alpha) {
            context.color(
                    delegate,
                    Mth.clamp(red, 0, 255) / 255.0F,
                    Mth.clamp(green, 0, 255) / 255.0F,
                    Mth.clamp(blue, 0, 255) / 255.0F,
                    Mth.clamp(alpha, 0, 255) / 255.0F
            );
            return this;
        }

        @Override
        public VertexConsumer uv(float u, float v) {
            context.uv(delegate, u, v);
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
            int ordinal = context.vertexOrdinal++;
            context.lastUvOrdinal = ordinal;
            context.corruptUv(u, v, ordinal);
            int color = context.corruptColor(
                    Mth.clamp(Math.round(red * 255.0F), 0, 255),
                    Mth.clamp(Math.round(green * 255.0F), 0, 255),
                    Mth.clamp(Math.round(blue * 255.0F), 0, 255),
                    Mth.clamp(Math.round(alpha * 255.0F), 0, 255),
                    ordinal
            );
            delegate.vertex(
                    x,
                    y,
                    z,
                    ((color >>> 16) & 0xFF) / 255.0F,
                    ((color >>> 8) & 0xFF) / 255.0F,
                    (color & 0xFF) / 255.0F,
                    ((color >>> 24) & 0xFF) / 255.0F,
                    context.lastU,
                    context.lastV,
                    overlay,
                    light,
                    normalX,
                    normalY,
                    normalZ
            );
        }
    }

    private static float signed(long value, float amplitude) {
        return (unit(value) * 2.0F - 1.0F) * amplitude;
    }

    private static float unit(long value) {
        return ((mixLong(value) >>> 40) & 0xFF_FFFFL) / 16_777_215.0F;
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
