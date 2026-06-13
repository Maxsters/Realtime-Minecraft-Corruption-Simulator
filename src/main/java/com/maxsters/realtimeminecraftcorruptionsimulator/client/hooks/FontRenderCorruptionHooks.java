package com.maxsters.realtimeminecraftcorruptionsimulator.client.hooks;

import com.maxsters.realtimeminecraftcorruptionsimulator.client.effects.ClientCorruptionEffects;
import com.maxsters.realtimeminecraftcorruptionsimulator.profile.CorruptionEffectStack;
import com.maxsters.realtimeminecraftcorruptionsimulator.profile.CorruptionSurface;
import com.mojang.blaze3d.vertex.VertexConsumer;
import net.minecraft.client.gui.font.glyphs.BakedGlyph;
import net.minecraft.util.Mth;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.api.distmarker.OnlyIn;
import org.joml.Matrix4f;

@OnlyIn(Dist.CLIENT)
public final class FontRenderCorruptionHooks {
    private static final FontMutation INACTIVE = new FontMutation(false, 0.0F, 0.0F, 1.0F, 1.0F, 1.0F, 0.0F, 0L);

    private FontRenderCorruptionHooks() {
    }

    public static float mutateAdvance(float advance) {
        FontMutation mutation = mutation();
        if (!mutation.active) {
            return advance;
        }
        float mutated = advance * mutation.advanceScale + mutation.advanceOffset;
        return Mth.clamp(mutated, -4.0F, 42.0F);
    }

    public static void renderGlyph(BakedGlyph glyph,
                                   boolean bold,
                                   boolean italic,
                                   float boldOffset,
                                   float x,
                                   float y,
                                   Matrix4f matrix,
                                   VertexConsumer consumer,
                                   float red,
                                   float green,
                                   float blue,
                                   float alpha,
                                   int light,
                                   boolean shadow) {
        FontMutation mutation = mutation();
        if (!mutation.active) {
            renderVanilla(glyph, bold, italic, boldOffset, x, y, matrix, consumer, red, green, blue, alpha, light);
            return;
        }

        Matrix4f glyphMatrix = matrix;
        if (Math.abs(mutation.xScale - 1.0F) > 0.015F || Math.abs(mutation.yScale - 1.0F) > 0.015F) {
            glyphMatrix = new Matrix4f(matrix);
            glyphMatrix.translate(x, y, 0.0F);
            glyphMatrix.scale(mutation.xScale, mutation.yScale, 1.0F);
            glyphMatrix.translate(-x, -y, 0.0F);
        }

        float[] color = mutateColor(red, green, blue, alpha, shadow, mutation);
        float mutatedBoldOffset = boldOffset * Mth.clamp(mutation.xScale, 0.20F, 2.40F);
        renderVanilla(glyph, bold, italic, mutatedBoldOffset, x, y, glyphMatrix, consumer, color[0], color[1], color[2], color[3], light);
    }

    private static void renderVanilla(BakedGlyph glyph,
                                      boolean bold,
                                      boolean italic,
                                      float boldOffset,
                                      float x,
                                      float y,
                                      Matrix4f matrix,
                                      VertexConsumer consumer,
                                      float red,
                                      float green,
                                      float blue,
                                      float alpha,
                                      int light) {
        glyph.render(italic, x, y, matrix, consumer, red, green, blue, alpha, light);
        if (bold) {
            glyph.render(italic, x + boldOffset, y, matrix, consumer, red, green, blue, alpha, light);
        }
    }

    private static FontMutation mutation() {
        CorruptionEffectStack stack = ClientCorruptionEffects.current();
        if (!stack.activeOrExtreme(CorruptionSurface.TEXTURE_MEMORY) && !stack.activeOrExtreme(CorruptionSurface.GUI_SURFACE)) {
            return INACTIVE;
        }

        float texture = stack.extreme(CorruptionSurface.TEXTURE_MEMORY) ? 1.0F : stack.intensity(CorruptionSurface.TEXTURE_MEMORY);
        float gui = stack.extreme(CorruptionSurface.GUI_SURFACE) ? 1.0F : stack.intensity(CorruptionSurface.GUI_SURFACE) * 0.92F;
        float levelFloor = Mth.clamp(stack.level() / 100.0F * 0.72F, 0.0F, 1.0F);
        float intensity = Mth.clamp(Math.max(levelFloor, Math.max(texture, gui)), 0.0F, 1.0F);
        if (intensity <= 0.025F) {
            return INACTIVE;
        }
        float progression = progressiveStrength(intensity);

        long bucket = stack.bucket(CorruptionSurface.TEXTURE_MEMORY, "font_render_attributes", 0x46524E44, 96);
        long seed = stack.stableLong(CorruptionSurface.TEXTURE_MEMORY, "font_render_attributes", (int) bucket)
                ^ stack.stableLong(CorruptionSurface.GUI_SURFACE, "font_render_attributes", (int) (bucket * 31L + 0x475549L))
                ^ 0x464F4E5452454E44L;
        int mode = Math.floorMod((int) (seed >>> 28), 8);
        float xScale = 1.0F;
        float yScale = 1.0F;
        float advanceScale = 1.0F;
        float advanceOffset = 0.0F;
        float power = intensity * intensity;

        switch (mode) {
            case 0 -> {
                xScale = scrunch(seed ^ 0x58534352L, intensity, 0.16F);
                yScale = 0.92F + unit(seed ^ 0x59535442L) * (0.18F + intensity * 0.28F);
                advanceScale = 0.45F + unit(seed ^ 0x41585631L) * (0.50F + intensity * 0.40F);
            }
            case 1 -> {
                yScale = scrunch(seed ^ 0x59534352L, intensity, 0.12F);
                xScale = 1.05F + unit(seed ^ 0x58535442L) * (0.34F + intensity * 0.92F);
                advanceScale = 0.95F + unit(seed ^ 0x41585632L) * (0.65F + intensity * 1.65F);
            }
            case 2 -> {
                xScale = 1.0F + unit(seed ^ 0x58535452L) * (0.58F + power * 2.35F);
                yScale = 0.70F + unit(seed ^ 0x59535453L) * 0.55F;
                advanceScale = 1.20F + unit(seed ^ 0x41445633L) * (0.80F + intensity * 1.75F);
            }
            case 3 -> {
                yScale = 1.0F + unit(seed ^ 0x59535452L) * (0.45F + power * 1.65F);
                xScale = 0.55F + unit(seed ^ 0x58535453L) * 0.82F;
                advanceScale = 0.55F + unit(seed ^ 0x41445634L) * 1.20F;
            }
            case 4 -> {
                xScale = scrunch(seed ^ 0x58515348L, intensity, 0.10F);
                yScale = scrunch(seed ^ 0x59515348L, intensity, 0.10F);
                advanceScale = 0.22F + unit(seed ^ 0x41445635L) * (0.80F + intensity * 0.55F);
            }
            case 5 -> {
                xScale = 0.75F + unit(seed ^ 0x584D4958L) * (0.75F + intensity * 1.80F);
                yScale = 0.28F + unit(seed ^ 0x594D4958L) * (0.92F + intensity * 0.74F);
                advanceScale = 0.18F + unit(seed ^ 0x41445636L) * (2.90F + intensity * 2.10F);
            }
            case 6 -> {
                xScale = 0.92F + signed(seed ^ 0x584A4954L, 0.22F + intensity * 0.42F);
                yScale = 0.92F + signed(seed ^ 0x594A4954L, 0.22F + intensity * 0.42F);
                advanceScale = 1.0F + signed(seed ^ 0x41445637L, 0.55F + intensity * 1.85F);
            }
            default -> {
                xScale = 0.32F + unit(seed ^ 0x58574944L) * (1.20F + intensity * 2.10F);
                yScale = 0.18F + unit(seed ^ 0x59484947L) * (1.08F + intensity * 1.35F);
                advanceScale = 0.35F + unit(seed ^ 0x41445638L) * (1.80F + intensity * 2.50F);
                advanceOffset = signed(seed ^ 0x41444F46L, 0.35F + intensity * 2.80F);
            }
        }

        if (unit(seed ^ 0x41444F46474CL) < 0.20F + intensity * 0.44F) {
            advanceOffset += signed(seed ^ 0x41444F46324CL, 0.25F + intensity * 3.60F);
        }

        xScale = Mth.lerp(progression, 1.0F, xScale);
        yScale = Mth.lerp(progression, 1.0F, yScale);
        advanceScale = Mth.lerp(progression, 1.0F, advanceScale);
        advanceOffset *= progression;
        xScale = Mth.clamp(xScale, 0.08F, 3.25F);
        yScale = Mth.clamp(yScale, 0.08F, 2.85F);
        advanceScale = Mth.clamp(advanceScale, -0.30F, 4.50F);
        return new FontMutation(true, intensity, progression, xScale, yScale, advanceScale, advanceOffset, seed);
    }

    private static float[] mutateColor(float red, float green, float blue, float alpha, boolean shadow, FontMutation mutation) {
        long seed = mutation.seed ^ (shadow ? 0x534841444F57434CL : 0x4D41494E434F4C4CL);
        float chance = shadow ? 0.04F + mutation.progression * 0.90F : 0.02F + mutation.progression * 0.72F;
        if (unit(seed ^ 0x4347415445L) > chance) {
            return new float[] {red, green, blue, alpha};
        }

        int mode = Math.floorMod((int) (seed >>> 25), shadow ? 6 : 7);
        float r = red;
        float g = green;
        float b = blue;
        if (shadow) {
            switch (mode) {
                case 0 -> {
                    r = 0.70F;
                    g = 0.62F;
                    b = 0.43F;
                }
                case 1 -> {
                    r = 0.52F;
                    g = 0.42F;
                    b = 0.72F;
                }
                case 2 -> {
                    r = 0.0F;
                    g = 0.0F;
                    b = 0.0F;
                }
                case 3 -> {
                    r = 0.95F;
                    g = 0.90F;
                    b = 0.78F;
                }
                case 4 -> {
                    r = b;
                    b = red;
                }
                default -> {
                    r = unit(seed ^ 0x52534844L);
                    g = unit(seed ^ 0x47534844L);
                    b = unit(seed ^ 0x42534844L);
                }
            }
        } else {
            switch (mode) {
                case 0 -> {
                    r = green;
                    g = blue;
                    b = red;
                }
                case 1 -> {
                    r = 1.0F - red;
                    g = 1.0F - green;
                    b = 1.0F - blue;
                }
                case 2 -> {
                    float gray = (red + green + blue) / 3.0F;
                    r = gray * 0.75F + 0.22F;
                    g = gray * 0.65F + 0.18F;
                    b = gray * 0.45F + 0.12F;
                }
                case 3 -> {
                    r = 1.0F;
                    g = 1.0F;
                    b = 1.0F;
                }
                case 4 -> {
                    r = 1.0F;
                    g = 0.0F;
                    b = 1.0F;
                }
                case 5 -> {
                    r = unit(seed ^ 0x52575041L);
                    g = unit(seed ^ 0x47575041L);
                    b = unit(seed ^ 0x42575041L);
                }
                default -> {
                    float wash = 0.35F + mutation.intensity * 0.55F;
                    r = Mth.lerp(wash, red, unit(seed ^ 0x5254494EL));
                    g = Mth.lerp(wash, green, unit(seed ^ 0x4754494EL));
                    b = Mth.lerp(wash, blue, unit(seed ^ 0x4254494EL));
                }
            }
        }

        float blend = Mth.clamp((shadow ? 0.12F : 0.08F) + mutation.progression * (shadow ? 0.84F : 0.70F), 0.0F, 1.0F);
        return new float[] {
                Mth.clamp(Mth.lerp(blend, red, r), 0.0F, 1.0F),
                Mth.clamp(Mth.lerp(blend, green, g), 0.0F, 1.0F),
                Mth.clamp(Mth.lerp(blend, blue, b), 0.0F, 1.0F),
                alpha
        };
    }

    private static float scrunch(long seed, float intensity, float floor) {
        return Mth.clamp(floor + unit(seed) * (0.56F - floor) - intensity * 0.20F, 0.08F, 0.72F);
    }

    private static float progressiveStrength(float intensity) {
        float clamped = Mth.clamp(intensity, 0.0F, 1.0F);
        return Mth.clamp((float) Math.pow(clamped, 1.65F), 0.0F, 1.0F);
    }

    private static float signed(long seed, float span) {
        return (unit(seed) * 2.0F - 1.0F) * span;
    }

    private static float unit(long value) {
        return ((mix(value) >>> 40) & 0xFF_FFFFL) / 16_777_215.0F;
    }

    private static long mix(long value) {
        value ^= value >>> 33;
        value *= 0xff51afd7ed558ccdL;
        value ^= value >>> 33;
        value *= 0xc4ceb9fe1a85ec53L;
        value ^= value >>> 33;
        return value;
    }

    private record FontMutation(boolean active, float intensity, float progression, float xScale, float yScale, float advanceScale, float advanceOffset, long seed) {
    }
}
