package com.maxsters.realtimeminecraftcorruptionsimulator.client.hooks;

import com.maxsters.realtimeminecraftcorruptionsimulator.client.effects.ClientCorruptionEffects;
import com.maxsters.realtimeminecraftcorruptionsimulator.profile.CorruptionEffectStack;
import com.maxsters.realtimeminecraftcorruptionsimulator.profile.CorruptionSurface;
import com.maxsters.realtimeminecraftcorruptionsimulator.profile.CorruptionValueMutator;
import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.EnchantmentTableBlockEntity;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.api.distmarker.OnlyIn;
import net.minecraftforge.registries.ForgeRegistries;

@OnlyIn(Dist.CLIENT)
public final class BlockEntityAnimationCorruptionHooks {
    private BlockEntityAnimationCorruptionHooks() {
    }

    public static float mutateLidOpenNess(BlockEntity blockEntity, float partialTick, float original, String channel) {
        return mutateOpenProgress(blockEntity, partialTick, original, "lid_" + channel, -0.85F, 1.95F, 0x4C4944);
    }

    public static float mutateShulkerProgress(BlockEntity blockEntity, float partialTick, float original) {
        return mutateOpenProgress(blockEntity, partialTick, original, "shulker_progress", -0.45F, 1.70F, 0x53484C4B);
    }

    public static float mutatePistonProgress(BlockEntity blockEntity, float partialTick, float original) {
        return mutateOpenProgress(blockEntity, partialTick, original, "piston_progress", -0.35F, 1.55F, 0x50495354);
    }

    public static float mutateConduitRotation(BlockEntity blockEntity, float partialTick, float original) {
        return mutateScalarAnimation(blockEntity, partialTick, original, "conduit_rotation", 0.28F, 7.20F, -48.0F, 48.0F, 0x434F4E44);
    }

    public static float mutateConduitBob(BlockEntity blockEntity, float phase, float original) {
        return mutateScalarAnimation(blockEntity, phase, original, "conduit_bob", 0.28F, 7.80F, -9.0F, 9.0F, 0x434F4242);
    }

    public static boolean beginConduitRenderPose(BlockEntity blockEntity, float partialTick, PoseStack poseStack) {
        if (poseStack == null) {
            return false;
        }
        CorruptionEffectStack stack = ClientCorruptionEffects.currentForWorldRendering();
        if (!stack.activeOrExtreme(CorruptionSurface.ANIMATION_TIMING)) {
            return false;
        }

        String targetId = targetId(blockEntity, "conduit_vertical_motion");
        float intensity = stack.extreme(CorruptionSurface.ANIMATION_TIMING)
                ? 1.0F
                : Mth.clamp(Math.max(
                stack.targetIntensity(CorruptionSurface.ANIMATION_TIMING, targetId),
                stack.intensity(CorruptionSurface.ANIMATION_TIMING) * 0.94F
        ), 0.0F, 1.0F);
        if (intensity <= 0.0F) {
            return false;
        }

        long seed = stack.stableLong(CorruptionSurface.ANIMATION_TIMING, targetId, 0x434F4E59);
        float time = gameTime(blockEntity) + partialTick;
        float amplitude = 0.10F + intensity * (stack.extreme(CorruptionSurface.ANIMATION_TIMING) ? 4.80F : 2.40F);
        float primary = oscillatorSigned(time, seed, intensity, amplitude);
        float drift = signed(seed ^ 0x42494153L, intensity * (stack.extreme(CorruptionSurface.ANIMATION_TIMING) ? 1.85F : 0.85F));
        float offset = primary + drift;
        if (stack.extreme(CorruptionSurface.ANIMATION_TIMING) || unit(seed ^ 0x53544550L) < 0.18F + intensity * 0.48F) {
            float step = 0.0625F + unit(seed ^ 0x5155414EL) * (0.18F + intensity * 0.78F);
            offset = quantize(offset, step);
        }

        poseStack.pushPose();
        poseStack.translate(0.0D, Mth.clamp(offset, -5.25F, 5.25F), 0.0D);
        return true;
    }

    public static void endConduitRenderPose(PoseStack poseStack, boolean applied) {
        if (applied && poseStack != null) {
            poseStack.popPose();
        }
    }

    public static float mutateSkullAnimation(BlockEntity blockEntity, float partialTick, float original) {
        return mutateScalarAnimation(blockEntity, partialTick, original, "skull_animation", 4.0F, 180.0F, -8192.0F, 8192.0F, 0x534B554C);
    }

    public static float mutateBannerWave(BlockEntity blockEntity, float phase, float original) {
        return mutateScalarAnimation(blockEntity, phase, original, "banner_wave", 0.35F, 8.50F, -10.0F, 10.0F, 0x42414E4E);
    }

    public static float mutateBellWave(BlockEntity blockEntity, float phase, float original) {
        return mutateScalarAnimation(blockEntity, phase, original, "bell_swing", 0.30F, 9.00F, -10.0F, 10.0F, 0x42454C4C);
    }

    public static float mutateEndGatewayPercent(BlockEntity blockEntity, float partialTick, float original, String channel) {
        return mutateOpenProgress(blockEntity, partialTick, original, "end_gateway_" + channel, -0.55F, 1.85F, 0x454E4447);
    }

    public static long mutateAnimationGameTime(BlockEntity blockEntity, long original, String channel) {
        CorruptionEffectStack stack = ClientCorruptionEffects.currentForWorldRendering();
        if (!stack.activeOrExtreme(CorruptionSurface.ANIMATION_TIMING)) {
            return original;
        }

        String targetId = targetId(blockEntity, channel);
        float intensity = stack.extreme(CorruptionSurface.ANIMATION_TIMING)
                ? 1.0F
                : Mth.clamp(Math.max(
                stack.targetIntensity(CorruptionSurface.ANIMATION_TIMING, targetId),
                stack.intensity(CorruptionSurface.ANIMATION_TIMING) * 0.84F
        ), 0.0F, 1.0F);
        if (intensity <= 0.0F) {
            return original;
        }

        long gameTime = gameTime(blockEntity);
        long seed = stack.stableLong(CorruptionSurface.ANIMATION_TIMING, targetId, 0x54494D45);
        long cadence = Math.max(1L, Math.round(Mth.lerp(intensity, 20.0F, 1.0F)));
        long clock = seed ^ (Math.floorDiv(gameTime, cadence) * 0x9E3779B97F4A7C15L) ^ original;
        long span = Math.max(1L, Math.round(8.0D + intensity * 960.0D));
        int mode = Math.floorMod((int) (seed >>> 30), 6);

        long result = switch (mode) {
            case 0 -> original + signedOffset(seed ^ 0x42494153L, span);
            case 1 -> -original + signedOffset(seed ^ 0x52455653L, span);
            case 2 -> {
                long step = Math.max(1L, Math.round(2.0D + unit(seed ^ 0x53544550L) * (8.0D + intensity * 180.0D)));
                yield Math.round((double) original / (double) step) * step;
            }
            case 3 -> original + Math.round(oscillatorSigned(gameTime, seed, intensity, (float) span));
            case 4 -> signedOffset(clock ^ 0x4142534CL, span * 2L);
            default -> original + signedOffset(clock ^ 0x4F464653L, span);
        };

        if (stack.extreme(CorruptionSurface.ANIMATION_TIMING) || unit(seed ^ 0x5155414EL) < 0.12F + intensity * 0.42F) {
            long step = Math.max(1L, Math.round(2.0D + unit(seed ^ 0x51535445L) * (4.0D + intensity * 96.0D)));
            result = Math.round((double) result / (double) step) * step;
        }
        return result;
    }

    public static void corruptEnchantmentTableFields(EnchantmentTableBlockEntity blockEntity) {
        if (blockEntity == null) {
            return;
        }
        blockEntity.time = mutateAnimationTick(blockEntity, blockEntity.time, "enchant_time", -20000, 20000, 0x4554494D);
        blockEntity.open = mutateOpenProgress(blockEntity, 0.0F, blockEntity.open, "enchant_open", -0.65F, 1.90F, 0x454F504E);
        blockEntity.oOpen = mutateOpenProgress(blockEntity, 0.0F, blockEntity.oOpen, "enchant_o_open", -0.65F, 1.90F, 0x454F4F50);
        blockEntity.rot = mutateScalarAnimation(blockEntity, 0.0F, blockEntity.rot, "enchant_rot", 0.20F, Mth.TWO_PI * 3.50F, -Mth.TWO_PI * 8.0F, Mth.TWO_PI * 8.0F, 0x45524F54);
        blockEntity.oRot = mutateScalarAnimation(blockEntity, 0.0F, blockEntity.oRot, "enchant_o_rot", 0.20F, Mth.TWO_PI * 3.50F, -Mth.TWO_PI * 8.0F, Mth.TWO_PI * 8.0F, 0x454F524F);
        blockEntity.tRot = mutateScalarAnimation(blockEntity, 0.0F, blockEntity.tRot, "enchant_t_rot", 0.20F, Mth.TWO_PI * 4.00F, -Mth.TWO_PI * 8.0F, Mth.TWO_PI * 8.0F, 0x4554524F);
        blockEntity.flip = mutateScalarAnimation(blockEntity, 0.0F, blockEntity.flip, "enchant_flip", 0.35F, 18.0F, -32.0F, 32.0F, 0x45464C50);
        blockEntity.oFlip = mutateScalarAnimation(blockEntity, 0.0F, blockEntity.oFlip, "enchant_o_flip", 0.35F, 18.0F, -32.0F, 32.0F, 0x454F464C);
        blockEntity.flipT = mutateScalarAnimation(blockEntity, 0.0F, blockEntity.flipT, "enchant_flip_target", 0.45F, 24.0F, -48.0F, 48.0F, 0x45465447);
        blockEntity.flipA = mutateScalarAnimation(blockEntity, 0.0F, blockEntity.flipA, "enchant_flip_accel", 0.20F, 5.50F, -12.0F, 12.0F, 0x45464143);
    }

    private static int mutateAnimationTick(BlockEntity blockEntity, int original, String channel, int min, int max, int salt) {
        float mutated = mutateScalarAnimation(blockEntity, original, original, channel, 2.0F, 120.0F, min, max, salt);
        return Mth.clamp(Math.round(mutated), min, max);
    }

    private static float mutateOpenProgress(BlockEntity blockEntity, float partialTick, float original, String channel, float min, float max, int salt) {
        CorruptionEffectStack stack = ClientCorruptionEffects.currentForWorldRendering();
        if (!stack.activeOrExtreme(CorruptionSurface.ANIMATION_TIMING)) {
            return original;
        }

        String targetId = targetId(blockEntity, channel);
        float intensity = stack.extreme(CorruptionSurface.ANIMATION_TIMING)
                ? 1.0F
                : Mth.clamp(Math.max(
                stack.targetIntensity(CorruptionSurface.ANIMATION_TIMING, targetId),
                stack.intensity(CorruptionSurface.ANIMATION_TIMING) * 0.82F
        ), 0.0F, 1.0F);
        if (intensity <= 0.0F) {
            return original;
        }

        long gameTime = gameTime(blockEntity);
        long seed = stack.stableLong(CorruptionSurface.ANIMATION_TIMING, targetId, salt);
        long cadence = Math.max(1L, Math.round(Mth.lerp(intensity, 18.0F, 2.0F)));
        long clock = seed ^ (Math.floorDiv(gameTime, cadence) * 0x9E3779B97F4A7C15L) ^ Float.floatToIntBits(partialTick);
        int mode = Math.floorMod((int) (seed >>> 29), 6);

        float result = switch (mode) {
            case 0 -> original + signed(seed ^ 0x42494153L, 0.18F + intensity * 0.95F);
            case 1 -> 1.0F - original + signed(seed ^ 0x494E5645L, 0.10F + intensity * 0.38F);
            case 2 -> quantize(original + signed(clock ^ 0x53544550L, intensity * 0.45F), 0.125F + unit(seed ^ 0x5155414EL) * (0.18F + intensity * 0.42F));
            case 3 -> oscillator(gameTime + partialTick, seed, intensity);
            case 4 -> original * (0.20F + unit(seed ^ 0x5343414CL) * (0.75F + intensity * 2.80F))
                    + signed(seed ^ 0x4F464653L, intensity * 0.75F);
            default -> CorruptionValueMutator.mutateScalar(
                    stack,
                    CorruptionSurface.ANIMATION_TIMING,
                    targetId,
                    original,
                    0.22F + intensity * 1.65F,
                    min,
                    max,
                    salt ^ 0x4F50454E,
                    clock
            );
        };

        if (stack.extreme(CorruptionSurface.ANIMATION_TIMING) || unit(seed ^ 0x53545554L) < 0.18F + intensity * 0.48F) {
            float step = 0.0625F + unit(seed ^ 0x53544550L) * (0.18F + intensity * 0.56F);
            result = quantize(result, step);
        }

        return Mth.clamp(result, min, max);
    }

    private static float mutateScalarAnimation(BlockEntity blockEntity, float sample, float original, String channel, float baseSpan, float intensitySpan, float min, float max, int salt) {
        CorruptionEffectStack stack = ClientCorruptionEffects.currentForWorldRendering();
        if (!stack.activeOrExtreme(CorruptionSurface.ANIMATION_TIMING)) {
            return original;
        }

        String targetId = targetId(blockEntity, channel);
        float intensity = stack.extreme(CorruptionSurface.ANIMATION_TIMING)
                ? 1.0F
                : Mth.clamp(Math.max(
                stack.targetIntensity(CorruptionSurface.ANIMATION_TIMING, targetId),
                stack.intensity(CorruptionSurface.ANIMATION_TIMING) * 0.84F
        ), 0.0F, 1.0F);
        if (intensity <= 0.0F) {
            return original;
        }

        long gameTime = gameTime(blockEntity);
        long seed = stack.stableLong(CorruptionSurface.ANIMATION_TIMING, targetId, salt);
        long cadence = Math.max(1L, Math.round(Mth.lerp(intensity, 16.0F, 1.0F)));
        long clock = seed ^ (Math.floorDiv(gameTime, cadence) * 0x9E3779B97F4A7C15L) ^ Float.floatToIntBits(sample);
        float span = baseSpan + intensity * intensitySpan;
        int mode = Math.floorMod((int) (seed >>> 27), 7);

        float result = switch (mode) {
            case 0 -> original + signed(seed ^ 0x42494153L, span);
            case 1 -> original * (0.05F + unit(seed ^ 0x5343414CL) * (0.70F + intensity * 3.60F))
                    + signed(seed ^ 0x4F464653L, span * 0.65F);
            case 2 -> quantize(original + signed(clock ^ 0x53544550L, span * 0.80F), 0.05F + unit(seed ^ 0x5155414EL) * Math.max(0.05F, span * 0.55F));
            case 3 -> original + oscillatorSigned(gameTime + sample, seed, intensity, span);
            case 4 -> oscillatorSigned(gameTime + sample, seed, intensity, span * 1.35F) + signed(seed ^ 0x4142534CL, span * 0.35F);
            case 5 -> CorruptionValueMutator.mutateScalar(
                    stack,
                    CorruptionSurface.ANIMATION_TIMING,
                    targetId,
                    original,
                    span,
                    min,
                    max,
                    salt ^ 0x5343414C,
                    clock
            );
            default -> original - signed(clock ^ 0x52455653L, span) + signed(seed ^ 0x42494153L, span * 0.30F);
        };

        if (stack.extreme(CorruptionSurface.ANIMATION_TIMING) || unit(seed ^ 0x5155414EL) < 0.16F + intensity * 0.46F) {
            float step = 0.03125F + unit(seed ^ 0x53544550L) * Math.max(0.05F, span * 0.24F);
            result = quantize(result, step);
        }

        return Mth.clamp(result, min, max);
    }

    private static float oscillator(float time, long seed, float intensity) {
        float speed = 0.10F + unit(seed ^ 0x53504545L) * (0.28F + intensity * 1.55F);
        float phase = unit(seed ^ 0x50484153L) * Mth.TWO_PI;
        float wave = (float) Math.sin(time * speed + phase) * 0.5F + 0.5F;
        float gain = 0.60F + unit(seed ^ 0x4741494EL) * (0.65F + intensity * 0.95F);
        return wave * gain + signed(seed ^ 0x42494153L, intensity * 0.40F);
    }

    private static float oscillatorSigned(float time, long seed, float intensity, float amplitude) {
        float speed = 0.08F + unit(seed ^ 0x53504544L) * (0.22F + intensity * 1.70F);
        float phase = unit(seed ^ 0x50484153L) * Mth.TWO_PI;
        return (float) Math.sin(time * speed + phase) * amplitude;
    }

    private static String targetId(BlockEntity blockEntity, String channel) {
        String typeId = "unknown";
        String dimension = "no_level";
        String pos = "no_pos";
        if (blockEntity != null) {
            ResourceLocation id = ForgeRegistries.BLOCK_ENTITY_TYPES.getKey(blockEntity.getType());
            typeId = id == null ? blockEntity.getType().toString() : id.toString();
            if (blockEntity.hasLevel() && blockEntity.getLevel() != null) {
                dimension = blockEntity.getLevel().dimension().location().toString();
            }
            BlockPos blockPos = blockEntity.getBlockPos();
            if (blockPos != null) {
                pos = blockPos.getX() + "," + blockPos.getY() + "," + blockPos.getZ();
            }
        }
        return "block_entity_animation:" + channel + ":" + dimension + ":" + typeId + ":" + pos;
    }

    private static long gameTime(BlockEntity blockEntity) {
        if (blockEntity != null && blockEntity.hasLevel() && blockEntity.getLevel() != null) {
            return blockEntity.getLevel().getGameTime();
        }
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft != null && minecraft.level != null) {
            return minecraft.level.getGameTime();
        }
        return 0L;
    }

    private static float quantize(float value, float step) {
        return Math.round(value / Math.max(0.001F, step)) * step;
    }

    private static float signed(long value, float amplitude) {
        return (unit(value) * 2.0F - 1.0F) * amplitude;
    }

    private static long signedOffset(long value, long amplitude) {
        return Math.round(signed(value, (float) Math.min(Integer.MAX_VALUE, Math.max(1L, amplitude))));
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
