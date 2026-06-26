package com.maxsters.realtimeminecraftcorruptionsimulator.client.hooks;

import com.maxsters.realtimeminecraftcorruptionsimulator.client.effects.ClientCorruptionEffects;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.maxsters.realtimeminecraftcorruptionsimulator.profile.CorruptionEffectStack;
import com.maxsters.realtimeminecraftcorruptionsimulator.profile.CorruptionSurface;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import net.minecraft.world.level.material.FluidState;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.api.distmarker.OnlyIn;
import net.minecraftforge.registries.ForgeRegistries;

@OnlyIn(Dist.CLIENT)
public final class LiquidRenderCorruptionHooks {
    private LiquidRenderCorruptionHooks() {
    }

    public static VertexConsumer wrapConsumer(VertexConsumer consumer, BlockPos pos, FluidState fluidState) {
        if (consumer == null || pos == null || fluidState == null || fluidState.isEmpty()) {
            return consumer;
        }

        CorruptionEffectStack stack = ClientCorruptionEffects.currentForWorldRendering();
        Vec3 renderOffset = BlockRenderCorruptionHooks.currentRenderSpaceOffset();
        boolean hasRenderOffset = BlockRenderCorruptionHooks.hasRenderSpaceOffset(renderOffset);
        boolean hasGeometryCorruption = stack.activeOrExtreme(CorruptionSurface.MODEL_GEOMETRY);
        if (!hasGeometryCorruption && !hasRenderOffset) {
            return consumer;
        }
        if (!hasGeometryCorruption) {
            return new CorruptedLiquidVertexConsumer(consumer, LiquidGeometryContext.renderOffsetOnly(renderOffset));
        }

        ResourceLocation fluidId = ForgeRegistries.FLUIDS.getKey(fluidState.getType());
        String targetId = "liquid_geometry:" + (fluidId == null ? "unknown" : fluidId) + ":" + pos.getX() + ":" + pos.getY() + ":" + pos.getZ();
        float intensity = stack.extreme(CorruptionSurface.MODEL_GEOMETRY)
                ? 1.0F
                : Math.max(stack.targetIntensity(CorruptionSurface.MODEL_GEOMETRY, targetId), stack.intensity(CorruptionSurface.MODEL_GEOMETRY) * 0.85F);
        if (intensity <= 0.01F && !hasRenderOffset) {
            return consumer;
        }

        return new CorruptedLiquidVertexConsumer(consumer, new LiquidGeometryContext(
                stack,
                stack.stableLong(CorruptionSurface.MODEL_GEOMETRY, targetId, 0x4C495147),
                intensity,
                stack.extreme(CorruptionSurface.MODEL_GEOMETRY),
                renderOffset
        ));
    }

    public static boolean shouldDropLiquidMesh(BlockPos pos, FluidState fluidState) {
        if (pos == null || fluidState == null || fluidState.isEmpty()) {
            return false;
        }

        CorruptionEffectStack stack = ClientCorruptionEffects.currentForWorldRendering();
        float intensity = Mth.clamp(stack.extreme(CorruptionSurface.WORLD_RENDER) ? 1.0F : stack.intensity(CorruptionSurface.WORLD_RENDER), 0.0F, 1.0F);
        boolean extreme = stack.extreme(CorruptionSurface.WORLD_RENDER);
        if (intensity <= 0.045F && !extreme) {
            return false;
        }

        ResourceLocation fluidId = ForgeRegistries.FLUIDS.getKey(fluidState.getType());
        String targetId = "liquid_face:" + (fluidId == null ? "unknown" : fluidId) + ":" + pos.getX() + ":" + pos.getY() + ":" + pos.getZ();
        float chance = extreme
                ? 0.72F
                : Mth.clamp(0.018F + intensity * 0.46F + stack.instability() * 0.08F, 0.0F, 0.74F);
        return stack.unit(CorruptionSurface.WORLD_RENDER, targetId, 0x4C495155) < chance;
    }

    private static final class LiquidGeometryContext {
        private final CorruptionEffectStack stack;
        private final long seed;
        private final float intensity;
        private final boolean extreme;
        private final Vec3 renderOffset;
        private final boolean geometryCorruption;
        private int vertexOrdinal;

        private LiquidGeometryContext(CorruptionEffectStack stack, long seed, float intensity, boolean extreme, Vec3 renderOffset) {
            this.stack = stack;
            this.seed = seed;
            this.intensity = intensity;
            this.extreme = extreme;
            this.renderOffset = renderOffset;
            this.geometryCorruption = intensity > 0.01F;
        }

        private static LiquidGeometryContext renderOffsetOnly(Vec3 renderOffset) {
            return new LiquidGeometryContext(null, 0L, 0.0F, false, renderOffset);
        }

        private double[] corrupt(double x, double y, double z) {
            if (!geometryCorruption) {
                return withRenderOffset(x, y, z);
            }

            int ordinal = vertexOrdinal++;
            int face = ordinal >> 2;
            int corner = ordinal & 3;
            long faceSeed = mixLong(seed ^ (long) face * 0x9E3779B97F4A7C15L);
            float chance = extreme ? 1.0F : Mth.clamp(0.12F + intensity * 0.82F + stack.instability() * 0.08F, 0.0F, 0.97F);
            if (unit(faceSeed ^ 0x4348414EL) > chance) {
                return withRenderOffset(x, y, z);
            }

            double baseX = Math.floor(x);
            double baseY = Math.floor(y);
            double baseZ = Math.floor(z);
            double localX = x - baseX;
            double localZ = z - baseZ;
            double centerX = baseX + 0.5D;
            double centerY = baseY + 0.5D;
            double centerZ = baseZ + 0.5D;
            double dx = x - centerX;
            double dy = y - centerY;
            double dz = z - centerZ;

            double power = intensity * intensity;
            double span = (0.035D + power * 0.92D) * (extreme ? 1.45D : 1.0D);
            int mode = Math.floorMod((int) (faceSeed >>> 28), 7);
            double sx = scale(faceSeed ^ 0x585343414C45L, 0.12D + power * 1.65D);
            double sy = scale(faceSeed ^ 0x595343414C45L, 0.10D + power * 1.20D);
            double sz = scale(faceSeed ^ 0x5A5343414C45L, 0.12D + power * 1.65D);
            double shearX = signed(faceSeed ^ 0x585348454152L, power * 0.85D);
            double shearZ = signed(faceSeed ^ 0x5A5348454152L, power * 0.85D);
            double offsetX = signed(faceSeed ^ 0x584F4646L, span);
            double offsetY = signed(faceSeed ^ 0x594F4646L, span * 0.70D);
            double offsetZ = signed(faceSeed ^ 0x5A4F4646L, span);
            long vertexSeed = mixLong(faceSeed ^ (long) corner * 0xD1B54A32D192ED03L);
            double tear = unit(vertexSeed ^ 0x54454152L) < 0.18D + intensity * 0.42D
                    ? signed(vertexSeed ^ 0x56455254L, 0.05D + power * 1.45D)
                    : 0.0D;

            double nx = centerX + dx * sx + dz * shearX + offsetX;
            double ny = centerY + dy * sy + offsetY;
            double nz = centerZ + dz * sz + dx * shearZ + offsetZ;
            if (mode == 0) {
                nx = centerX + signed(faceSeed ^ 0x434F4C58L, 0.04D + power * 0.18D);
            } else if (mode == 1) {
                nz = centerZ + signed(faceSeed ^ 0x434F4C5AL, 0.04D + power * 0.18D);
            } else if (mode == 2) {
                ny = centerY + signed(faceSeed ^ 0x464C4154L, 0.02D + power * 0.12D);
            } else if (mode == 3) {
                nx += tear;
                ny += signed(vertexSeed ^ 0x594A4954L, Math.abs(tear) * 0.75D);
                nz += signed(vertexSeed ^ 0x5A4A4954L, Math.abs(tear));
            } else if (mode == 4) {
                double snap = Math.max(0.03125D, 0.25D - intensity * 0.18D);
                nx = baseX + Math.rint(localX / snap) * snap;
                nz = baseZ + Math.rint(localZ / snap) * snap;
            } else if (mode == 5) {
                ny += signed(vertexSeed ^ 0x5350494BL, 0.08D + power * 0.90D);
            } else {
                nx += signed(vertexSeed ^ 0x5853504BL, Math.abs(tear));
                nz += signed(vertexSeed ^ 0x5A53504BL, Math.abs(tear));
            }
            if (extreme || intensity > 0.70F) {
                double highSpan = 0.35D + intensity * 2.20D;
                if (unit(vertexSeed ^ 0x48494748L) < 0.32D + intensity * 0.42D) {
                    ny += signed(vertexSeed ^ 0x59484947L, highSpan);
                }
                if (unit(vertexSeed ^ 0x57494445L) < 0.18D + intensity * 0.34D) {
                    nx += signed(vertexSeed ^ 0x58484947L, highSpan * 0.85D);
                    nz += signed(vertexSeed ^ 0x5A484947L, highSpan * 0.85D);
                }
            }

            return withRenderOffset(
                    clampAroundBlock(nx, baseX),
                    clampAroundBlock(ny, baseY),
                    clampAroundBlock(nz, baseZ)
            );
        }

        private double[] withRenderOffset(double x, double y, double z) {
            if (!BlockRenderCorruptionHooks.hasRenderSpaceOffset(renderOffset)) {
                return new double[] {x, y, z};
            }
            return new double[] {x + renderOffset.x, y + renderOffset.y, z + renderOffset.z};
        }

        private static double scale(long hash, double span) {
            double value = 1.0D + signed(hash, span);
            if (Math.abs(value) < 0.05D) {
                return Math.copySign(0.05D, value == 0.0D ? 1.0D : value);
            }
            return value;
        }

        private static double clampAroundBlock(double value, double base) {
            return Mth.clamp(value, base - 2.5D, base + 3.5D);
        }
    }

    private static final class CorruptedLiquidVertexConsumer implements VertexConsumer {
        private final VertexConsumer delegate;
        private final LiquidGeometryContext context;
        private VertexConsumer active;

        private CorruptedLiquidVertexConsumer(VertexConsumer delegate, LiquidGeometryContext context) {
            this.delegate = delegate;
            this.context = context;
            this.active = delegate;
        }

        @Override
        public VertexConsumer vertex(double x, double y, double z) {
            double[] corrupted = context.corrupt(x, y, z);
            active = delegate.vertex(corrupted[0], corrupted[1], corrupted[2]);
            return this;
        }

        @Override
        public VertexConsumer color(int red, int green, int blue, int alpha) {
            active = active.color(red, green, blue, alpha);
            return this;
        }

        @Override
        public VertexConsumer uv(float u, float v) {
            active = active.uv(u, v);
            return this;
        }

        @Override
        public VertexConsumer overlayCoords(int u, int v) {
            active = active.overlayCoords(u, v);
            return this;
        }

        @Override
        public VertexConsumer uv2(int u, int v) {
            active = active.uv2(u, v);
            return this;
        }

        @Override
        public VertexConsumer normal(float x, float y, float z) {
            active = active.normal(x, y, z);
            return this;
        }

        @Override
        public void endVertex() {
            active.endVertex();
            active = delegate;
        }

        @Override
        public void defaultColor(int red, int green, int blue, int alpha) {
            delegate.defaultColor(red, green, blue, alpha);
        }

        @Override
        public void unsetDefaultColor() {
            delegate.unsetDefaultColor();
        }
    }

    private static double signed(long value, double amplitude) {
        return (unit(value) * 2.0D - 1.0D) * amplitude;
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
