package com.maxsters.realtimeminecraftcorruptionsimulator.client.hooks.render;

import com.maxsters.realtimeminecraftcorruptionsimulator.client.effects.ClientCorruptionEffects;
import com.maxsters.realtimeminecraftcorruptionsimulator.profile.CorruptionEffectStack;
import com.maxsters.realtimeminecraftcorruptionsimulator.profile.CorruptionSurface;
import com.maxsters.realtimeminecraftcorruptionsimulator.profile.CorruptionValueMutator;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.VoxelShape;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.api.distmarker.OnlyIn;
import net.minecraftforge.registries.ForgeRegistries;
import org.joml.Matrix4f;

@OnlyIn(Dist.CLIENT)
public final class BlockOutlineCorruptionHooks {
    private static final ThreadLocal<OutlineContext> OUTLINE_CONTEXT = new ThreadLocal<>();

    private BlockOutlineCorruptionHooks() {
    }

    public static void renderHitOutline(
            PoseStack poseStack,
            VertexConsumer consumer,
            Entity entity,
            double cameraX,
            double cameraY,
            double cameraZ,
            BlockPos pos,
            BlockState state
    ) {
        Minecraft minecraft = Minecraft.getInstance();
        ClientLevel level = minecraft == null ? null : minecraft.level;
        if (level == null || pos == null || state == null) {
            return;
        }

        begin(cameraX, cameraY, cameraZ, pos, state);
        try {
            VoxelShape shape = state.getShape(level, pos, CollisionContext.of(entity));
            renderShape(
                    poseStack,
                    consumer,
                    shape,
                    pos.getX() - cameraX,
                    pos.getY() - cameraY,
                    pos.getZ() - cameraZ,
                    0.0F,
                    0.0F,
                    0.0F,
                    0.4F
            );
        } finally {
            end();
        }
    }

    public static void begin(double cameraX, double cameraY, double cameraZ, BlockPos pos, BlockState state) {
        CorruptionEffectStack stack = ClientCorruptionEffects.currentForWorldRendering();
        if (pos == null || state == null || !stack.activeOrExtreme(CorruptionSurface.MODEL_GEOMETRY)) {
            OUTLINE_CONTEXT.remove();
            return;
        }

        ResourceLocation blockId = ForgeRegistries.BLOCKS.getKey(state.getBlock());
        String targetId = "block_outline:" + (blockId == null ? state.getBlock().toString() : blockId)
                + ":" + pos.getX()
                + ":" + pos.getY()
                + ":" + pos.getZ();
        float intensity = stack.extreme(CorruptionSurface.MODEL_GEOMETRY)
                ? 1.0F
                : Math.max(
                        stack.targetIntensity(CorruptionSurface.MODEL_GEOMETRY, targetId),
                        stack.intensity(CorruptionSurface.MODEL_GEOMETRY) * 0.74F
                );
        if (intensity <= 0.01F) {
            OUTLINE_CONTEXT.remove();
            return;
        }

        OUTLINE_CONTEXT.set(new OutlineContext(
                stack,
                targetId,
                stack.stableLong(CorruptionSurface.MODEL_GEOMETRY, targetId, 0x4F55544C),
                Mth.clamp(intensity, 0.0F, 1.0F),
                stack.extreme(CorruptionSurface.MODEL_GEOMETRY),
                pos.getX() - cameraX,
                pos.getY() - cameraY,
                pos.getZ() - cameraZ
        ));
    }

    public static void end() {
        OUTLINE_CONTEXT.remove();
    }

    public static VertexConsumer vertex(VertexConsumer consumer, Matrix4f matrix, float x, float y, float z) {
        OutlineContext context = OUTLINE_CONTEXT.get();
        if (context == null) {
            return consumer.vertex(matrix, x, y, z);
        }
        return context.vertex(consumer, matrix, x, y, z);
    }

    public static void renderShape(
            PoseStack poseStack,
            VertexConsumer consumer,
            VoxelShape shape,
            double x,
            double y,
            double z,
            float red,
            float green,
            float blue,
            float alpha
    ) {
        PoseStack.Pose pose = poseStack.last();
        shape.forAllEdges((x1, y1, z1, x2, y2, z2) -> {
            float normalX = (float) (x2 - x1);
            float normalY = (float) (y2 - y1);
            float normalZ = (float) (z2 - z1);
            float length = Mth.sqrt(normalX * normalX + normalY * normalY + normalZ * normalZ);
            if (length <= 0.000001F) {
                return;
            }
            normalX /= length;
            normalY /= length;
            normalZ /= length;
            vertex(consumer, pose.pose(), (float) (x1 + x), (float) (y1 + y), (float) (z1 + z))
                    .color(red, green, blue, alpha)
                    .normal(pose.normal(), normalX, normalY, normalZ)
                    .endVertex();
            vertex(consumer, pose.pose(), (float) (x2 + x), (float) (y2 + y), (float) (z2 + z))
                    .color(red, green, blue, alpha)
                    .normal(pose.normal(), normalX, normalY, normalZ)
                    .endVertex();
        });
    }

    private static final class OutlineContext {
        private final CorruptionEffectStack stack;
        private final String targetId;
        private final long seed;
        private final float intensity;
        private final boolean extreme;
        private final double baseX;
        private final double baseY;
        private final double baseZ;
        private int vertexOrdinal;

        private OutlineContext(
                CorruptionEffectStack stack,
                String targetId,
                long seed,
                float intensity,
                boolean extreme,
                double baseX,
                double baseY,
                double baseZ
        ) {
            this.stack = stack;
            this.targetId = targetId;
            this.seed = seed;
            this.intensity = intensity;
            this.extreme = extreme;
            this.baseX = baseX;
            this.baseY = baseY;
            this.baseZ = baseZ;
        }

        private VertexConsumer vertex(VertexConsumer consumer, Matrix4f matrix, float x, float y, float z) {
            int ordinal = vertexOrdinal++;
            int edge = ordinal >> 1;
            int endpoint = ordinal & 1;
            long edgeSeed = mix(seed ^ (long) edge * 0x9E3779B97F4A7C15L);
            long vertexSeed = mix(edgeSeed ^ (long) endpoint * 0xD1B54A32D192ED03L);
            double power = intensity * intensity;
            double centerX = baseX + 0.5D;
            double centerY = baseY + 0.5D;
            double centerZ = baseZ + 0.5D;
            double dx = x - centerX;
            double dy = y - centerY;
            double dz = z - centerZ;

            double offsetSpan = (0.025D + power * 0.88D) * (extreme ? 2.25D : 1.0D);
            double scaleSpan = 0.08D + power * (extreme ? 4.80D : 2.20D);
            double xScale = scale(edgeSeed ^ 0x585343414C45L, scaleSpan);
            double yScale = scale(edgeSeed ^ 0x595343414C45L, scaleSpan * 0.86D);
            double zScale = scale(edgeSeed ^ 0x5A5343414C45L, scaleSpan);
            double shearX = signed(edgeSeed ^ 0x585348454152L, power * (extreme ? 1.95D : 0.82D));
            double shearY = signed(edgeSeed ^ 0x595348454152L, power * (extreme ? 1.60D : 0.64D));
            double shearZ = signed(edgeSeed ^ 0x5A5348454152L, power * (extreme ? 1.95D : 0.82D));

            double nx = centerX + dx * xScale + dz * shearX + signed(edgeSeed ^ 0x584F4646L, offsetSpan);
            double ny = centerY + dy * yScale + dx * shearY + signed(edgeSeed ^ 0x594F4646L, offsetSpan * 0.74D);
            double nz = centerZ + dz * zScale + dx * shearZ + signed(edgeSeed ^ 0x5A4F4646L, offsetSpan);
            int mode = Math.floorMod((int) (seed >>> 29), 7);
            switch (mode) {
                case 0 -> {
                    nx += signed(vertexSeed ^ 0x4A495458L, 0.04D + power * 0.42D);
                    ny += signed(vertexSeed ^ 0x4A495459L, 0.025D + power * 0.28D);
                    nz += signed(vertexSeed ^ 0x4A49545AL, 0.04D + power * 0.42D);
                }
                case 1 -> {
                    double snap = 0.03125D + power * (0.18D + unit(edgeSeed ^ 0x53544550L) * 0.52D);
                    nx = centerX + quantize(nx - centerX, snap);
                    ny = centerY + quantize(ny - centerY, snap);
                    nz = centerZ + quantize(nz - centerZ, snap);
                }
                case 2 -> {
                    nx = centerX + dz * xScale + signed(vertexSeed ^ 0x53574158L, offsetSpan);
                    nz = centerZ + dx * zScale + signed(vertexSeed ^ 0x5357415AL, offsetSpan);
                }
                case 3 -> {
                    ny = centerY + signed(edgeSeed ^ 0x464C4154L, 0.02D + power * 0.22D);
                    nx += signed(vertexSeed ^ 0x464C4158L, offsetSpan * 1.6D);
                    nz += signed(vertexSeed ^ 0x464C415AL, offsetSpan * 1.6D);
                }
                case 4 -> {
                    double fold = Math.sin((dx - dz) * (2.0D + intensity * 9.0D) + unit(edgeSeed) * Mth.TWO_PI) * power;
                    nx += fold * (extreme ? 1.25D : 0.56D);
                    nz -= fold * (extreme ? 1.25D : 0.56D);
                }
                case 5 -> {
                    nx = centerX + dx * xScale * (unit(edgeSeed ^ 0x44524F58L) < 0.5D ? -1.0D : 1.0D);
                    ny = centerY + dy * yScale;
                    nz += signed(vertexSeed ^ 0x44524F5AL, offsetSpan * 2.1D);
                }
                default -> {
                    double pulse = CorruptionValueMutator.mutateScalar(
                            stack,
                            CorruptionSurface.MODEL_GEOMETRY,
                            targetId + ":outline_pulse:" + edge,
                            0.0D,
                            0.04D + power * 0.68D,
                            -1.5D,
                            1.5D,
                            endpoint,
                            edgeSeed
                    );
                    nx += pulse;
                    ny -= pulse * 0.55D;
                    nz += pulse * 0.35D;
                }
            }

            double clamp = extreme ? 8.0D : 3.5D;
            return consumer.vertex(
                    matrix,
                    (float) Mth.clamp(nx, centerX - clamp, centerX + clamp),
                    (float) Mth.clamp(ny, centerY - clamp, centerY + clamp),
                    (float) Mth.clamp(nz, centerZ - clamp, centerZ + clamp)
            );
        }

        private static double scale(long hash, double span) {
            double value = 1.0D + signed(hash, span);
            if (Math.abs(value) < 0.035D) {
                return Math.copySign(0.035D, value == 0.0D ? 1.0D : value);
            }
            return value;
        }

        private static double quantize(double value, double step) {
            return step <= 0.0D ? value : Math.rint(value / step) * step;
        }
    }

    private static double signed(long value, double amplitude) {
        return (unit(value) * 2.0D - 1.0D) * amplitude;
    }

    private static double unit(long value) {
        return ((mix(value) >>> 40) & 0xFF_FFFFL) / 16_777_215.0D;
    }

    private static long mix(long value) {
        value ^= value >>> 33;
        value *= 0xff51afd7ed558ccdL;
        value ^= value >>> 33;
        value *= 0xc4ceb9fe1a85ec53L;
        value ^= value >>> 33;
        return value;
    }
}
