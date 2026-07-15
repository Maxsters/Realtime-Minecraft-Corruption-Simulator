package com.maxsters.realtimeminecraftcorruptionsimulator.client.hooks.render;

import com.maxsters.realtimeminecraftcorruptionsimulator.client.effects.ClientCorruptionEffects;
import com.maxsters.realtimeminecraftcorruptionsimulator.profile.CorruptionEffectStack;
import com.maxsters.realtimeminecraftcorruptionsimulator.profile.CorruptionSurface;
import com.mojang.blaze3d.platform.GlStateManager;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.BufferBuilder;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.blaze3d.vertex.VertexBuffer;
import net.minecraft.client.renderer.ShaderInstance;
import net.minecraft.client.renderer.LevelRenderer;
import net.minecraft.util.Mth;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.api.distmarker.OnlyIn;
import org.joml.Matrix4f;
import org.joml.Quaternionf;

import java.lang.reflect.Method;

@OnlyIn(Dist.CLIENT)
public final class SkyRenderCorruptionHooks {
    private static final ThreadLocal<BuildContext> BUILD_CONTEXT = new ThreadLocal<>();
    private static final ThreadLocal<SkyRenderContext> SKY_RENDER_CONTEXT = new ThreadLocal<>();
    private static final ThreadLocal<Boolean> ALLOW_STAR_BUILD_CORRUPTION = ThreadLocal.withInitial(() -> false);
    private static Method createStarsMethod;
    private static boolean createStarsMethodChecked;
    private static String appliedSignature = "";

    private SkyRenderCorruptionHooks() {
    }

    public static void beginRenderSky(LevelRenderer renderer, float partialTick) {
        CorruptionEffectStack stack = ClientCorruptionEffects.currentForWorldRendering();
        if (skyColorMutationActive(stack)) {
            SKY_RENDER_CONTEXT.set(new SkyRenderContext(stack, skyColorSignature(stack, partialTick)));
        } else {
            SKY_RENDER_CONTEXT.remove();
        }
        onRenderSky(renderer);
    }

    public static void endRenderSky() {
        SkyRenderContext context = SKY_RENDER_CONTEXT.get();
        if (context != null) {
            restoreSkyState();
        }
        SKY_RENDER_CONTEXT.remove();
    }

    public static float mutateCelestialTime(float vanillaTime) {
        SkyRenderContext context = SKY_RENDER_CONTEXT.get();
        if (context == null || !Float.isFinite(vanillaTime)) {
            return vanillaTime;
        }

        CorruptionEffectStack stack = context.stack();
        float intensity = skyRenderStateIntensity(stack, "sky_celestial_trajectory", 0.94F);
        if (intensity <= 0.0F) {
            context.celestialTime = wrapUnit(vanillaTime);
            return vanillaTime;
        }

        long seed = stack.stableLong(CorruptionSurface.WORLD_RENDER, "sky_celestial_trajectory", 0x4F524249);
        double time = wrapUnit(vanillaTime);
        double phase = unit(seed ^ 0x5048415345L);
        int mode = Math.floorMod((int) (seed >>> 29), 8);
        double mutated = switch (mode) {
            case 0 -> time + signed(seed ^ 0x504F534CL, 0.5D + intensity * 1.5D);
            case 1 -> phase - time * (0.25D + unit(seed ^ 0x52455645L) * (2.0D + intensity * 5.0D));
            case 2 -> phase + time * (0.08D + unit(seed ^ 0x53504545L) * (3.0D + intensity * 12.0D));
            case 3 -> phase;
            case 4 -> {
                int steps = 2 + (int) Math.floor(unit(seed ^ 0x53544550L) * (5.0D + intensity * 19.0D));
                yield Math.floor((time + phase) * steps) / steps;
            }
            case 5 -> time + Math.sin((time * (1.0D + unit(seed ^ 0x46524551L) * 7.0D) + phase) * Mth.TWO_PI)
                    * intensity * (0.08D + unit(seed ^ 0x574F4242L) * 0.62D);
            case 6 -> Math.abs(wrapUnit(time * (0.5D + unit(seed ^ 0x50494E47L) * 6.5D) + phase) * 2.0D - 1.0D);
            default -> phase + Math.copySign(Math.pow(time, 0.25D + unit(seed ^ 0x43555256L) * 3.75D),
                    unit(seed ^ 0x5349474EL) < 0.5D ? -1.0D : 1.0D);
        };

        float blend = Mth.clamp(0.18F + intensity * 0.82F, 0.0F, 1.0F);
        float result = wrapUnit(Mth.lerp(blend, (float) time, (float) mutated));
        context.celestialTime = result;
        return result;
    }

    public static Quaternionf mutateCelestialOrbit(Quaternionf vanillaRotation) {
        SkyRenderContext context = SKY_RENDER_CONTEXT.get();
        if (context == null || vanillaRotation == null) {
            return vanillaRotation;
        }

        CorruptionEffectStack stack = context.stack();
        float intensity = skyRenderStateIntensity(stack, "sky_celestial_orbit", 0.92F);
        if (intensity <= 0.0F) {
            return vanillaRotation;
        }

        long seed = stack.stableLong(CorruptionSurface.WORLD_RENDER, "sky_celestial_orbit", 0x41584953);
        float cycle = Float.isFinite(context.celestialTime) ? context.celestialTime : 0.0F;
        float angle = cycle * Mth.TWO_PI;
        int mode = Math.floorMod((int) (seed >>> 33), 5);
        if (mode == 0 || mode == 1) {
            float axisX = 1.0F + signed(seed ^ 0x41584958L, intensity * 1.8F);
            float axisY = signed(seed ^ 0x41584959L, intensity * 2.4F);
            float axisZ = signed(seed ^ 0x4158495AL, intensity * 2.4F);
            float length = (float) Math.sqrt(axisX * axisX + axisY * axisY + axisZ * axisZ);
            if (length > 0.0001F && Float.isFinite(length)) {
                return new Quaternionf().rotationAxis(angle, axisX / length, axisY / length, axisZ / length);
            }
        }

        Quaternionf mutated = new Quaternionf();
        float fixedYaw = signed(seed ^ 0x594157L, intensity * (float) Math.PI);
        float fixedRoll = signed(seed ^ 0x524F4C4CL, intensity * (float) Math.PI);
        switch (mode) {
            case 2 -> mutated.rotateY(fixedYaw).rotateZ(fixedRoll).mul(vanillaRotation);
            case 3 -> {
                float frequency = 1.0F + unitFloat(seed ^ 0x50524651L) * 7.0F;
                float precession = (cycle * frequency * Mth.TWO_PI + fixedYaw) * intensity;
                mutated.rotateY(precession).rotateZ(fixedRoll * 0.65F).mul(vanillaRotation);
            }
            default -> {
                float phase = unitFloat(seed ^ 0x57425048L) * Mth.TWO_PI;
                float yaw = Mth.sin(cycle * Mth.TWO_PI * (1.0F + unitFloat(seed ^ 0x57424651L) * 5.0F) + phase)
                        * intensity * (0.35F + unitFloat(seed ^ 0x57425941L) * 2.4F);
                float roll = Mth.cos(cycle * Mth.TWO_PI * (1.0F + unitFloat(seed ^ 0x57425251L) * 4.0F) + phase)
                        * intensity * (0.25F + unitFloat(seed ^ 0x5742524CL) * 2.0F);
                mutated.rotateY(yaw).rotateZ(roll).mul(vanillaRotation);
            }
        }
        return mutated;
    }

    public static void onRenderSky(LevelRenderer renderer) {
        CorruptionEffectStack stack = ClientCorruptionEffects.currentForWorldRendering();
        String signature = starMutationActive(stack) ? starSignature(stack) : "";
        if (signature.equals(appliedSignature)) {
            return;
        }
        Method method = createStarsMethod();
        if (method == null || renderer == null) {
            return;
        }
        ALLOW_STAR_BUILD_CORRUPTION.set(true);
        try {
            method.invoke(renderer);
            appliedSignature = signature;
        } catch (ReflectiveOperationException | RuntimeException ignored) {
        } finally {
            ALLOW_STAR_BUILD_CORRUPTION.remove();
        }
    }

    public static void beginBuild() {
        if (!ALLOW_STAR_BUILD_CORRUPTION.get()) {
            BUILD_CONTEXT.remove();
            return;
        }

        CorruptionEffectStack stack = ClientCorruptionEffects.currentUnsuppressed();
        if (!starMutationActive(stack)) {
            BUILD_CONTEXT.remove();
            return;
        }
        BUILD_CONTEXT.set(new BuildContext(stack, starSignature(stack)));
    }

    public static void endBuild() {
        BUILD_CONTEXT.remove();
    }

    public static VertexConsumer vertex(BufferBuilder builder, double x, double y, double z) {
        BuildContext context = BUILD_CONTEXT.get();
        if (context == null) {
            return builder.vertex(x, y, z);
        }

        int ordinal = context.nextVertexOrdinal();
        int star = ordinal >> 2;
        int corner = ordinal & 3;
        CorruptionEffectStack stack = context.stack();
        float intensity = starIntensity(stack, star);
        if (intensity <= 0.0F) {
            return builder.vertex(x, y, z);
        }

        double length = Math.sqrt(x * x + y * y + z * z);
        if (length <= 0.000001D) {
            return builder.vertex(x, y, z);
        }

        String targetId = "sky_star:" + star;
        long hash = stack.stableLong(CorruptionSurface.LIGHT_FIELD, targetId, corner ^ 0x53544152);
        double centerScale = 100.0D / length;
        double centerX = x * centerScale;
        double centerY = y * centerScale;
        double centerZ = z * centerScale;
        double offsetX = x - centerX;
        double offsetY = y - centerY;
        double offsetZ = z - centerZ;
        double scale = 1.0D + intensity * (stack.extreme(CorruptionSurface.LIGHT_FIELD) || stack.extreme(CorruptionSurface.WORLD_RENDER) ? 34.0D : 14.0D);
        double skewX = 1.0D + signed(hash ^ 0x58415354L, intensity * 8.0D);
        double skewY = 1.0D + signed(hash ^ 0x59415354L, intensity * 8.0D);
        double skewZ = 1.0D + signed(hash ^ 0x5A415354L, intensity * 8.0D);
        double drift = stack.extreme(CorruptionSurface.WORLD_RENDER) ? signed(hash ^ 0x44524946L, intensity * 14.0D) : 0.0D;
        return builder.vertex(
                centerX + offsetX * scale * skewX + signed(hash ^ 0x4A585354L, intensity * 0.55D) + drift * 0.20D,
                centerY + offsetY * scale * skewY + signed(hash ^ 0x4A595354L, intensity * 0.55D),
                centerZ + offsetZ * scale * skewZ + signed(hash ^ 0x4A5A5354L, intensity * 0.55D) - drift * 0.20D
        );
    }

    public static VertexConsumer color(VertexConsumer consumer, float red, float green, float blue, float alpha) {
        SkyRenderContext context = SKY_RENDER_CONTEXT.get();
        if (context == null) {
            return consumer.color(red, green, blue, alpha);
        }

        int ordinal = context.nextColorOrdinal();
        CorruptionEffectStack stack = context.stack();
        float intensity = skyColorIntensity(stack, ordinal);
        if (intensity <= 0.0F) {
            return consumer.color(red, green, blue, alpha);
        }

        String targetId = "sky_color:" + ordinal;
        long seed = stack.stableLong(CorruptionSurface.WORLD_RENDER, context.signature() + ":" + targetId, ordinal ^ 0x534B5943);
        int mode = Math.floorMod((int) (seed >>> 30), 9);
        float r = red;
        float g = green;
        float b = blue;
        float a = alpha;
        float luma = r * 0.2126F + g * 0.7152F + b * 0.0722F;

        switch (mode) {
            case 0 -> {
                float contrast = 1.0F + signed(seed ^ 0x434F4E54L, 1.5F + intensity * 6.0F);
                r = luma + (r - luma) * contrast;
                g = luma + (g - luma) * contrast;
                b = luma + (b - luma) * contrast;
            }
            case 1 -> {
                float contrast = 1.0F + intensity * (2.5F + unitFloat(seed ^ 0x434F4E32L) * 8.0F);
                r = (r - 0.5F) * contrast + 0.5F;
                g = (g - 0.5F) * contrast + 0.5F;
                b = (b - 0.5F) * contrast + 0.5F;
            }
            case 2 -> {
                float threshold = 0.18F + unitFloat(seed ^ 0x54485245L) * 0.74F;
                float high = 0.72F + intensity * 0.92F;
                float low = unitFloat(seed ^ 0x4C4F5753L) * 0.18F;
                r = r > threshold ? high : low;
                g = g > threshold ? high : low;
                b = b > threshold ? high : low;
            }
            case 3 -> {
                float oldR = r;
                float oldG = g;
                r = b * (0.65F + intensity * 1.15F);
                g = oldR * (0.35F + unitFloat(seed ^ 0x47534846L) * 1.35F);
                b = oldG * (0.25F + intensity * 1.55F);
            }
            case 4 -> {
                r = 1.0F - r + signed(seed ^ 0x52494E56L, intensity * 0.45F);
                g = 1.0F - g + signed(seed ^ 0x47494E56L, intensity * 0.45F);
                b = 1.0F - b + signed(seed ^ 0x42494E56L, intensity * 0.45F);
            }
            case 5 -> {
                float step = 0.04F + unitFloat(seed ^ 0x5155414EL) * (0.18F + intensity * 0.52F);
                r = quantize(r + signed(seed ^ 0x52514E54L, intensity * 0.36F), step);
                g = quantize(g + signed(seed ^ 0x47514E54L, intensity * 0.36F), step);
                b = quantize(b + signed(seed ^ 0x42514E54L, intensity * 0.36F), step);
            }
            case 6 -> {
                float band = Math.max(0.001F, 0.12F + unitFloat(seed ^ 0x42414E44L) * (0.35F + intensity * 0.75F));
                r = (float) Math.abs(Math.sin((r + luma) / band + unit(seed) * Mth.TWO_PI));
                g = (float) Math.abs(Math.sin((g + luma) / band + unit(seed ^ 0x47) * Mth.TWO_PI));
                b = (float) Math.abs(Math.sin((b + luma) / band + unit(seed ^ 0x42) * Mth.TWO_PI));
            }
            case 7 -> {
                float channel = switch (Math.floorMod((int) (seed >>> 47), 3)) {
                    case 0 -> r;
                    case 1 -> g;
                    default -> b;
                };
                r = channel + signed(seed ^ 0x5243484EL, intensity * 0.65F);
                g = channel + signed(seed ^ 0x4743484EL, intensity * 0.65F);
                b = channel + signed(seed ^ 0x4243484EL, intensity * 0.65F);
            }
            default -> {
                float alphaScale = unit(seed ^ 0x414C5048L) < 0.45F + intensity * 0.34F
                        ? 0.02F + unitFloat(seed ^ 0x46414944L) * (0.28F + intensity * 0.34F)
                        : 1.0F + signed(seed ^ 0x414D504CL, intensity * 0.62F);
                a *= alphaScale;
                r += signed(seed ^ 0x52534947L, intensity * 0.52F);
                g += signed(seed ^ 0x47534947L, intensity * 0.52F);
                b += signed(seed ^ 0x42534947L, intensity * 0.52F);
            }
        }

        if (stack.extreme(CorruptionSurface.WORLD_RENDER) && unit(seed ^ 0x45585452L) < 0.22F) {
            float boost = 1.25F + unitFloat(seed ^ 0x424F4F53L) * 2.25F;
            r *= boost;
            g *= boost;
            b *= boost;
        }
        return consumer.color(clampColor(r), clampColor(g), clampColor(b), Mth.clamp(a, 0.0F, 1.0F));
    }

    public static void shaderColor(float red, float green, float blue, float alpha) {
        SkyRenderContext context = SKY_RENDER_CONTEXT.get();
        if (context == null) {
            RenderSystem.setShaderColor(red, green, blue, alpha);
            return;
        }

        int ordinal = context.nextShaderColorOrdinal();
        CorruptionEffectStack stack = context.stack();
        float intensity = skyRenderStateIntensity(stack, "sky_shader_color:" + ordinal, 0.96F);
        if (intensity <= 0.0F) {
            RenderSystem.setShaderColor(red, green, blue, alpha);
            return;
        }

        long seed = stack.stableLong(CorruptionSurface.WORLD_RENDER, context.signature() + ":shader:" + ordinal, ordinal ^ 0x534B5953);
        int mode = Math.floorMod((int) (seed >>> 31), 10);
        float r = red;
        float g = green;
        float b = blue;
        float a = alpha;
        float luma = r * 0.2126F + g * 0.7152F + b * 0.0722F;

        switch (mode) {
            case 0 -> {
                float contrast = 1.0F + intensity * (9.0F + unitFloat(seed ^ 0x434F4E54L) * 38.0F);
                float exposure = signed(seed ^ 0x4558504FL, intensity * 2.4F);
                r = (r - 0.5F) * contrast + 0.5F + exposure;
                g = (g - 0.5F) * contrast + 0.5F + exposure;
                b = (b - 0.5F) * contrast + 0.5F + exposure;
            }
            case 1 -> {
                float threshold = 0.05F + unitFloat(seed ^ 0x54485245L) * 0.72F;
                float high = 1.2F + intensity * (5.5F + unitFloat(seed ^ 0x48494748L) * 14.0F);
                float low = unitFloat(seed ^ 0x4C4F5753L) * intensity * 0.08F;
                float gate = luma > threshold ? high : low;
                r = gate * (0.35F + unitFloat(seed ^ 0x52474154L) * 1.8F);
                g = gate * (0.35F + unitFloat(seed ^ 0x47474154L) * 1.8F);
                b = gate * (0.35F + unitFloat(seed ^ 0x42474154L) * 1.8F);
            }
            case 2 -> {
                float gain = 1.0F + intensity * (7.0F + unitFloat(seed ^ 0x4741494EL) * 26.0F);
                int channel = Math.floorMod((int) (seed >>> 43), 3);
                r = channel == 0 ? (luma + 0.08F) * gain : r * (0.02F + intensity * 0.18F);
                g = channel == 1 ? (luma + 0.08F) * gain : g * (0.02F + intensity * 0.18F);
                b = channel == 2 ? (luma + 0.08F) * gain : b * (0.02F + intensity * 0.18F);
            }
            case 3 -> {
                float inversionGain = 0.8F + intensity * (2.4F + unitFloat(seed ^ 0x494E5647L) * 8.0F);
                r = (1.0F - r) * inversionGain;
                g = (1.0F - g) * inversionGain;
                b = (1.0F - b) * inversionGain;
            }
            case 4 -> {
                float step = 0.04F + unitFloat(seed ^ 0x5155414EL) * (0.10F + intensity * 0.30F);
                float boost = 1.0F + intensity * (4.0F + unitFloat(seed ^ 0x5155424CL) * 16.0F);
                r = quantize(r + signed(seed ^ 0x5251545AL, intensity * 0.8F), step) * boost;
                g = quantize(g + signed(seed ^ 0x4751545AL, intensity * 0.8F), step) * boost;
                b = quantize(b + signed(seed ^ 0x4251545AL, intensity * 0.8F), step) * boost;
            }
            case 5 -> {
                float crush = unit(seed ^ 0x43525553L) < 0.5F ? 0.0F : 1.0F + intensity * 11.0F;
                r = unit(seed ^ 0x52435253L) < intensity ? crush : r;
                g = unit(seed ^ 0x47435253L) < intensity ? crush : g;
                b = unit(seed ^ 0x42435253L) < intensity ? crush : b;
            }
            case 6 -> {
                float redGain = 0.15F + intensity * (unitFloat(seed ^ 0x52474149L) * 14.0F);
                float greenGain = 0.15F + intensity * (unitFloat(seed ^ 0x47474149L) * 14.0F);
                float blueGain = 0.15F + intensity * (unitFloat(seed ^ 0x42474149L) * 14.0F);
                r = (g + b * 0.5F) * redGain;
                g = (b + r * 0.5F) * greenGain;
                b = (red + green * 0.5F) * blueGain;
            }
            case 7 -> {
                float band = 0.035F + unitFloat(seed ^ 0x42414E44L) * (0.12F + intensity * 0.32F);
                float gain = 0.75F + intensity * (2.0F + unitFloat(seed ^ 0x42474E44L) * 9.0F);
                r = (float) Math.abs(Math.sin((r + luma) / band + unit(seed ^ 0x52424E44L) * Mth.TWO_PI)) * gain;
                g = (float) Math.abs(Math.sin((g + luma) / band + unit(seed ^ 0x47424E44L) * Mth.TWO_PI)) * gain;
                b = (float) Math.abs(Math.sin((b + luma) / band + unit(seed ^ 0x42424E44L) * Mth.TWO_PI)) * gain;
            }
            case 8 -> {
                float blackout = 1.0F - intensity * (0.78F + unitFloat(seed ^ 0x424C4143L) * 0.21F);
                float flash = unit(seed ^ 0x464C5348L) < 0.35F + intensity * 0.35F ? 1.0F + intensity * 9.0F : 0.0F;
                r = r * blackout + flash * unitFloat(seed ^ 0x52464C53L);
                g = g * blackout + flash * unitFloat(seed ^ 0x47464C53L);
                b = b * blackout + flash * unitFloat(seed ^ 0x42464C53L);
            }
            default -> {
                float haze = luma * (1.0F + intensity * 18.0F);
                r = haze + signed(seed ^ 0x5248415AL, intensity * 1.5F);
                g = haze + signed(seed ^ 0x4748415AL, intensity * 1.5F);
                b = haze + signed(seed ^ 0x4248415AL, intensity * 1.5F);
                a *= 0.2F + unitFloat(seed ^ 0x4148415AL) * (0.55F + intensity * 1.1F);
            }
        }

        RenderSystem.setShaderColor(clampShaderColor(r), clampShaderColor(g), clampShaderColor(b), Mth.clamp(a, 0.0F, 1.0F));
    }

    public static void depthMask(boolean mask) {
        SkyRenderContext context = SKY_RENDER_CONTEXT.get();
        if (context == null) {
            RenderSystem.depthMask(mask);
            return;
        }

        CorruptionEffectStack stack = context.stack();
        float intensity = skyRenderStateIntensity(stack, "sky_depth_mask", 0.84F);
        if (intensity <= 0.0F) {
            RenderSystem.depthMask(mask);
            return;
        }
        long seed = stack.stableLong(CorruptionSurface.WORLD_RENDER, context.signature() + ":depth:" + mask, 0x44455054);
        boolean mutated = mask;
        if (unit(seed) < 0.28F + intensity * 0.52F) {
            mutated = !mask;
        } else if (unit(seed ^ 0x5A45524FL) < intensity * 0.45F) {
            mutated = false;
        }
        RenderSystem.depthMask(mutated);
    }

    public static void enableBlend() {
        SkyRenderContext context = SKY_RENDER_CONTEXT.get();
        if (context == null) {
            RenderSystem.enableBlend();
            return;
        }
        RenderSystem.enableBlend();
        applyBlendProfile(context, context.nextBlendOrdinal(), false);
    }

    public static void disableBlend() {
        SkyRenderContext context = SKY_RENDER_CONTEXT.get();
        if (context == null) {
            RenderSystem.disableBlend();
            return;
        }
        int ordinal = context.nextBlendOrdinal();
        CorruptionEffectStack stack = context.stack();
        float intensity = skyRenderStateIntensity(stack, "sky_disable_blend:" + ordinal, 0.74F);
        long seed = stack.stableLong(CorruptionSurface.WORLD_RENDER, context.signature() + ":disable_blend:" + ordinal, ordinal ^ 0x424C4E44);
        if (intensity > 0.0F && unit(seed) < 0.18F + intensity * 0.66F) {
            RenderSystem.enableBlend();
            applyBlendProfile(context, ordinal, true);
            return;
        }
        RenderSystem.disableBlend();
    }

    public static void defaultBlendFunc() {
        SkyRenderContext context = SKY_RENDER_CONTEXT.get();
        if (context == null) {
            RenderSystem.defaultBlendFunc();
            return;
        }
        int ordinal = context.nextBlendOrdinal();
        CorruptionEffectStack stack = context.stack();
        float intensity = skyRenderStateIntensity(stack, "sky_default_blend:" + ordinal, 0.78F);
        long seed = stack.stableLong(CorruptionSurface.WORLD_RENDER, context.signature() + ":default_blend:" + ordinal, ordinal ^ 0x4442464E);
        if (intensity > 0.0F && unit(seed) < 0.22F + intensity * 0.66F) {
            RenderSystem.enableBlend();
            applyBlendProfile(context, ordinal, false);
            return;
        }
        RenderSystem.defaultBlendFunc();
    }

    public static void blendFuncSeparate(
            GlStateManager.SourceFactor sourceColor,
            GlStateManager.DestFactor destColor,
            GlStateManager.SourceFactor sourceAlpha,
            GlStateManager.DestFactor destAlpha
    ) {
        SkyRenderContext context = SKY_RENDER_CONTEXT.get();
        if (context == null) {
            RenderSystem.blendFuncSeparate(sourceColor, destColor, sourceAlpha, destAlpha);
            return;
        }
        int ordinal = context.nextBlendOrdinal();
        CorruptionEffectStack stack = context.stack();
        float intensity = skyRenderStateIntensity(stack, "sky_blend_func:" + ordinal, 0.84F);
        long seed = stack.stableLong(CorruptionSurface.WORLD_RENDER, context.signature() + ":blend_func:" + ordinal, ordinal ^ 0x46424C4E);
        if (intensity > 0.0F && unit(seed) < 0.25F + intensity * 0.70F) {
            applyBlendProfile(context, ordinal, false);
            return;
        }
        RenderSystem.blendFuncSeparate(sourceColor, destColor, sourceAlpha, destAlpha);
    }

    public static void drawSkyBuffer(VertexBuffer buffer, Matrix4f modelViewMatrix, Matrix4f projectionMatrix, ShaderInstance shader) {
        SkyRenderContext context = SKY_RENDER_CONTEXT.get();
        if (context == null) {
            buffer.drawWithShader(modelViewMatrix, projectionMatrix, shader);
            return;
        }

        int ordinal = context.nextBufferOrdinal();
        CorruptionEffectStack stack = context.stack();
        float intensity = skyRenderStateIntensity(stack, "sky_buffer:" + ordinal, 0.90F);
        if (intensity <= 0.0F) {
            buffer.drawWithShader(modelViewMatrix, projectionMatrix, shader);
            return;
        }

        long seed = stack.stableLong(CorruptionSurface.WORLD_RENDER, context.signature() + ":buffer:" + ordinal, ordinal ^ 0x534B5942);
        Matrix4f mutated = new Matrix4f(modelViewMatrix);
        int mode = Math.floorMod((int) (seed >>> 33), 7);
        switch (mode) {
            case 0 -> {
                float scaleX = signedScale(seed ^ 0x53584D54L, intensity, 0.05F, 7.5F);
                float scaleY = signedScale(seed ^ 0x53594D54L, intensity, 0.04F, 6.5F);
                float scaleZ = signedScale(seed ^ 0x535A4D54L, intensity, 0.05F, 7.5F);
                mutated.scale(scaleX, scaleY, scaleZ);
            }
            case 1 -> {
                mutated.rotateX((float) signed(seed ^ 0x52584D54L, intensity * Math.PI * 1.85D));
                mutated.rotateY((float) signed(seed ^ 0x52594D54L, intensity * Math.PI * 1.85D));
                mutated.rotateZ((float) signed(seed ^ 0x525A4D54L, intensity * Math.PI * 1.85D));
            }
            case 2 -> {
                float flatten = 0.015F + unitFloat(seed ^ 0x464C4154L) * (0.28F + intensity * 0.42F);
                float stretch = 1.0F + intensity * (4.0F + unitFloat(seed ^ 0x53545245L) * 18.0F);
                mutated.scale(stretch, flatten, signedScale(seed ^ 0x5A464C54L, intensity, 0.08F, 10.0F));
            }
            case 3 -> {
                float shiftX = signed(seed ^ 0x54584D54L, intensity * 180.0F);
                float shiftY = signed(seed ^ 0x54594D54L, intensity * 140.0F);
                float shiftZ = signed(seed ^ 0x545A4D54L, intensity * 180.0F);
                mutated.translate(shiftX, shiftY, shiftZ);
            }
            case 4 -> {
                float scale = signedScale(seed ^ 0x4D495252L, intensity, 0.08F, 9.5F);
                mutated.scale(scale, -scale * (0.35F + intensity * 1.85F), 1.0F + intensity * 5.0F);
                mutated.rotateZ((float) signed(seed ^ 0x4D524F54L, intensity * Math.PI));
            }
            case 5 -> {
                mutated.rotateZ((float) signed(seed ^ 0x53504E5AL, intensity * Math.PI * 2.0D));
                mutated.scale(
                        1.0F + signed(seed ^ 0x53585853L, intensity * 8.0F),
                        1.0F + signed(seed ^ 0x53595953L, intensity * 8.0F),
                        1.0F + signed(seed ^ 0x535A5A53L, intensity * 8.0F)
                );
            }
            default -> {
                mutated.rotateX((float) signed(seed ^ 0x4449584DL, intensity * Math.PI * 0.75D));
                mutated.translate(
                        signed(seed ^ 0x4454584DL, intensity * 70.0F),
                        signed(seed ^ 0x4454594DL, intensity * 90.0F),
                        signed(seed ^ 0x44545A4DL, intensity * 70.0F)
                );
                mutated.scale(
                        signedScale(seed ^ 0x4453584DL, intensity, 0.12F, 4.5F),
                        signedScale(seed ^ 0x4453594DL, intensity, 0.12F, 4.5F),
                        signedScale(seed ^ 0x44535A4DL, intensity, 0.12F, 4.5F)
                );
            }
        }
        buffer.drawWithShader(mutated, projectionMatrix, shader);
    }

    private static boolean starMutationActive(CorruptionEffectStack stack) {
        return stack.activeOrExtreme(CorruptionSurface.LIGHT_FIELD)
                || stack.activeOrExtreme(CorruptionSurface.WORLD_RENDER);
    }

    private static boolean skyColorMutationActive(CorruptionEffectStack stack) {
        return stack.activeOrExtreme(CorruptionSurface.LIGHT_FIELD)
                || stack.activeOrExtreme(CorruptionSurface.WORLD_RENDER);
    }

    private static float starIntensity(CorruptionEffectStack stack, int star) {
        if (stack.extreme(CorruptionSurface.LIGHT_FIELD) || stack.extreme(CorruptionSurface.WORLD_RENDER)) {
            return 1.0F;
        }
        String targetId = "sky_star:" + star;
        float light = Math.max(stack.targetIntensity(CorruptionSurface.LIGHT_FIELD, targetId), stack.intensity(CorruptionSurface.LIGHT_FIELD) * 0.86F);
        float world = Math.max(stack.targetIntensity(CorruptionSurface.WORLD_RENDER, targetId), stack.intensity(CorruptionSurface.WORLD_RENDER) * 0.72F);
        return Mth.clamp(Math.max(light, world), 0.0F, 1.0F);
    }

    private static float skyColorIntensity(CorruptionEffectStack stack, int ordinal) {
        if (stack.extreme(CorruptionSurface.LIGHT_FIELD) || stack.extreme(CorruptionSurface.WORLD_RENDER)) {
            return 1.0F;
        }
        String targetId = "sky_color:" + ordinal;
        float light = Math.max(stack.targetIntensity(CorruptionSurface.LIGHT_FIELD, targetId), stack.intensity(CorruptionSurface.LIGHT_FIELD) * 0.82F);
        float world = Math.max(stack.targetIntensity(CorruptionSurface.WORLD_RENDER, targetId), stack.intensity(CorruptionSurface.WORLD_RENDER) * 0.92F);
        return Mth.clamp(Math.max(light, world), 0.0F, 1.0F);
    }

    private static float skyRenderStateIntensity(CorruptionEffectStack stack, String targetId, float worldWeight) {
        if (stack.extreme(CorruptionSurface.LIGHT_FIELD) || stack.extreme(CorruptionSurface.WORLD_RENDER)) {
            return 1.0F;
        }
        float light = Math.max(stack.targetIntensity(CorruptionSurface.LIGHT_FIELD, targetId), stack.intensity(CorruptionSurface.LIGHT_FIELD) * 0.72F);
        float world = Math.max(stack.targetIntensity(CorruptionSurface.WORLD_RENDER, targetId), stack.intensity(CorruptionSurface.WORLD_RENDER) * worldWeight);
        return Mth.clamp(Math.max(light, world), 0.0F, 1.0F);
    }

    private static void applyBlendProfile(SkyRenderContext context, int ordinal, boolean fromDisable) {
        CorruptionEffectStack stack = context.stack();
        float intensity = skyRenderStateIntensity(stack, "sky_blend_profile:" + ordinal, fromDisable ? 0.82F : 0.88F);
        if (intensity <= 0.0F) {
            RenderSystem.defaultBlendFunc();
            return;
        }

        long seed = stack.stableLong(CorruptionSurface.WORLD_RENDER, context.signature() + ":blend_profile:" + ordinal, ordinal ^ 0x4250524F);
        int mode = Math.floorMod((int) (seed >>> 36), 8);
        switch (mode) {
            case 0 -> RenderSystem.blendFuncSeparate(
                    GlStateManager.SourceFactor.ONE,
                    GlStateManager.DestFactor.ONE,
                    GlStateManager.SourceFactor.ONE,
                    GlStateManager.DestFactor.ONE
            );
            case 1 -> RenderSystem.blendFuncSeparate(
                    GlStateManager.SourceFactor.SRC_COLOR,
                    GlStateManager.DestFactor.ONE_MINUS_SRC_COLOR,
                    GlStateManager.SourceFactor.ONE,
                    GlStateManager.DestFactor.ZERO
            );
            case 2 -> RenderSystem.blendFuncSeparate(
                    GlStateManager.SourceFactor.DST_COLOR,
                    GlStateManager.DestFactor.ONE_MINUS_DST_COLOR,
                    GlStateManager.SourceFactor.ONE,
                    GlStateManager.DestFactor.ZERO
            );
            case 3 -> RenderSystem.blendFuncSeparate(
                    GlStateManager.SourceFactor.ONE_MINUS_DST_COLOR,
                    GlStateManager.DestFactor.ONE,
                    GlStateManager.SourceFactor.ONE,
                    GlStateManager.DestFactor.ZERO
            );
            case 4 -> RenderSystem.blendFuncSeparate(
                    GlStateManager.SourceFactor.SRC_ALPHA,
                    GlStateManager.DestFactor.ONE,
                    GlStateManager.SourceFactor.ONE,
                    GlStateManager.DestFactor.ONE_MINUS_SRC_ALPHA
            );
            case 5 -> RenderSystem.blendFuncSeparate(
                    GlStateManager.SourceFactor.ONE_MINUS_SRC_ALPHA,
                    GlStateManager.DestFactor.SRC_COLOR,
                    GlStateManager.SourceFactor.ONE,
                    GlStateManager.DestFactor.ZERO
            );
            case 6 -> {
                if (unit(seed ^ 0x44495341L) < 0.35F + intensity * 0.35F) {
                    RenderSystem.disableBlend();
                } else {
                    RenderSystem.blendFuncSeparate(
                            GlStateManager.SourceFactor.SRC_ALPHA_SATURATE,
                            GlStateManager.DestFactor.ONE,
                            GlStateManager.SourceFactor.ONE,
                            GlStateManager.DestFactor.ZERO
                    );
                }
            }
            default -> RenderSystem.blendFuncSeparate(
                    GlStateManager.SourceFactor.ONE,
                    GlStateManager.DestFactor.ONE_MINUS_SRC_ALPHA,
                    GlStateManager.SourceFactor.ONE,
                    GlStateManager.DestFactor.ZERO
            );
        }
    }

    private static void restoreSkyState() {
        RenderSystem.setShaderColor(1.0F, 1.0F, 1.0F, 1.0F);
        RenderSystem.depthMask(true);
        RenderSystem.enableDepthTest();
        RenderSystem.disableBlend();
        RenderSystem.defaultBlendFunc();
    }

    private static String starSignature(CorruptionEffectStack stack) {
        return stack.level()
                + ":" + stack.fixedSeed()
                + ":" + stack.bucket(CorruptionSurface.LIGHT_FIELD, 0x53544152, 64)
                + ":" + stack.bucket(CorruptionSurface.WORLD_RENDER, 0x574F524C, 64);
    }

    private static String skyColorSignature(CorruptionEffectStack stack, float partialTick) {
        return stack.level()
                + ":" + stack.fixedSeed()
                + ":" + stack.bucket(CorruptionSurface.LIGHT_FIELD, 0x534B594C, 64)
                + ":" + stack.bucket(CorruptionSurface.WORLD_RENDER, 0x534B5957, 64);
    }

    private static Method createStarsMethod() {
        if (createStarsMethodChecked) {
            return createStarsMethod;
        }
        createStarsMethodChecked = true;
        for (String name : new String[]{"createStars", "m_109837_", "m_109835_"}) {
            try {
                Method method = LevelRenderer.class.getDeclaredMethod(name);
                method.setAccessible(true);
                createStarsMethod = method;
                return method;
            } catch (NoSuchMethodException ignored) {
            }
        }
        return null;
    }

    private static double signed(long value, double amplitude) {
        return (unit(value) * 2.0D - 1.0D) * amplitude;
    }

    private static float signed(long value, float amplitude) {
        return (unitFloat(value) * 2.0F - 1.0F) * amplitude;
    }

    private static double unit(long value) {
        value ^= value >>> 33;
        value *= 0xff51afd7ed558ccdL;
        value ^= value >>> 33;
        value *= 0xc4ceb9fe1a85ec53L;
        value ^= value >>> 33;
        return ((value >>> 40) & 0xFF_FFFFL) / 16_777_215.0D;
    }

    private static float unitFloat(long value) {
        return (float) unit(value);
    }

    private static float quantize(float value, float step) {
        return Math.round(value / step) * step;
    }

    private static float clampColor(float value) {
        return Mth.clamp(value, 0.0F, 1.75F);
    }

    private static float clampShaderColor(float value) {
        return Mth.clamp(value, 0.0F, 18.0F);
    }

    private static float signedScale(long seed, float intensity, float minMagnitude, float maxMagnitude) {
        float magnitude = minMagnitude + unitFloat(seed ^ 0x4D41474EL) * (maxMagnitude - minMagnitude) * Math.max(intensity, 0.001F);
        return unit(seed ^ 0x5349474EL) < 0.5D ? -magnitude : magnitude;
    }

    private static float wrapUnit(double value) {
        return (float) (value - Math.floor(value));
    }

    private static final class SkyRenderContext {
        private final CorruptionEffectStack stack;
        private final String signature;
        private int colorOrdinal = -1;
        private int shaderColorOrdinal = -1;
        private int blendOrdinal = -1;
        private int bufferOrdinal = -1;
        private float celestialTime = Float.NaN;

        private SkyRenderContext(CorruptionEffectStack stack, String signature) {
            this.stack = stack;
            this.signature = signature;
        }

        private CorruptionEffectStack stack() {
            return stack;
        }

        private String signature() {
            return signature;
        }

        private int nextColorOrdinal() {
            colorOrdinal++;
            return colorOrdinal;
        }

        private int nextShaderColorOrdinal() {
            shaderColorOrdinal++;
            return shaderColorOrdinal;
        }

        private int nextBlendOrdinal() {
            blendOrdinal++;
            return blendOrdinal;
        }

        private int nextBufferOrdinal() {
            bufferOrdinal++;
            return bufferOrdinal;
        }
    }

    private static final class BuildContext {
        private final CorruptionEffectStack stack;
        private int vertexOrdinal = -1;

        private BuildContext(CorruptionEffectStack stack, String signature) {
            this.stack = stack;
        }

        private CorruptionEffectStack stack() {
            return stack;
        }

        private int nextVertexOrdinal() {
            vertexOrdinal++;
            return vertexOrdinal;
        }
    }
}
