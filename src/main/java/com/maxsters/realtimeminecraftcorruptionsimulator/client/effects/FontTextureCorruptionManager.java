package com.maxsters.realtimeminecraftcorruptionsimulator.client.effects;

import com.maxsters.realtimeminecraftcorruptionsimulator.RealtimeMinecraftCorruptionSimulator;
import com.maxsters.realtimeminecraftcorruptionsimulator.client.ClientCorruptionProtection;
import com.maxsters.realtimeminecraftcorruptionsimulator.profile.CorruptionEffectStack;
import com.maxsters.realtimeminecraftcorruptionsimulator.profile.CorruptionSurface;
import com.maxsters.realtimeminecraftcorruptionsimulator.state.CorruptionProfileSnapshot;
import com.mojang.blaze3d.font.GlyphInfo;
import com.mojang.blaze3d.font.GlyphProvider;
import com.mojang.blaze3d.font.SheetGlyphInfo;
import com.mojang.blaze3d.vertex.VertexConsumer;
import it.unimi.dsi.fastutil.ints.IntSet;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.font.FontManager;
import net.minecraft.client.gui.font.FontSet;
import net.minecraft.client.gui.font.GlyphRenderTypes;
import net.minecraft.client.gui.font.glyphs.BakedGlyph;
import net.minecraft.resources.ResourceLocation;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.api.distmarker.OnlyIn;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import org.joml.Matrix4f;

import javax.annotation.Nullable;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.function.Function;

@OnlyIn(Dist.CLIENT)
@Mod.EventBusSubscriber(modid = RealtimeMinecraftCorruptionSimulator.MOD_ID, value = Dist.CLIENT, bus = Mod.EventBusSubscriber.Bus.FORGE)
public final class FontTextureCorruptionManager {
    private static final long FONT_MUTATION_SEED = 0x53503034464F4E54L;

    private static Field minecraftFontManagerField;
    private static Field fontManagerFontSetsField;
    private static Field fontSetNameField;
    private static Field fontSetProvidersField;
    private static Field fontSetGlyphsField;
    private static Field fontSetGlyphInfosField;
    private static Field fontSetGlyphsByWidthField;
    private static Field bakedGlyphRenderTypesField;
    private static Field bakedGlyphU0Field;
    private static Field bakedGlyphU1Field;
    private static Field bakedGlyphV0Field;
    private static Field bakedGlyphV1Field;
    private static Field bakedGlyphLeftField;
    private static Field bakedGlyphRightField;
    private static Field bakedGlyphUpField;
    private static Field bakedGlyphDownField;
    private static Method fontSetCloseTexturesMethod;
    private static final String STALE_SIGNATURE = "<stale>";
    private static String activeSignature = "";
    private static int installCooldown;
    private static boolean pendingFontRefresh;
    private static long lastReportMs;

    private FontTextureCorruptionManager() {
    }

    @SubscribeEvent
    public static void onClientTick(TickEvent.ClientTickEvent event) {
        if (event.phase != TickEvent.Phase.END) {
            return;
        }
        if (ClientCorruptionProtection.shouldSuppressClientCorruption()) {
            installCooldown = 0;
            return;
        }
        if (installCooldown-- > 0) {
            return;
        }
        installCooldown = pendingFontRefresh ? 0 : 6;

        CorruptionEffectStack stack = ClientCorruptionEffects.current();
        String desiredSignature = fontMutationActive(stack) ? fontSignature(stack) : "";
        boolean forceRefresh = pendingFontRefresh || STALE_SIGNATURE.equals(activeSignature) || !desiredSignature.equals(activeSignature);
        if (!fontMutationActive(stack)) {
            if (forceRefresh || !activeSignature.isEmpty()) {
                restoreProviders();
                activeSignature = "";
                pendingFontRefresh = false;
            }
            return;
        }

        installCorruptingProviders(stack, forceRefresh);
        pendingFontRefresh = false;
    }

    public static void onSettingsChanged(CorruptionProfileSnapshot previous, CorruptionProfileSnapshot current) {
        if (previous == null || current == null
                || previous.getCorruptionLevel() != current.getCorruptionLevel()
                || previous.getEffectiveCorruptionSeed() != current.getEffectiveCorruptionSeed()
                || previous.getEnabledTargetsMask() != current.getEnabledTargetsMask()) {
            installCooldown = 0;
            activeSignature = STALE_SIGNATURE;
            pendingFontRefresh = true;
        }
    }

    private static void installCorruptingProviders(CorruptionEffectStack stack, boolean forceRefresh) {
        FontManager fontManager = fontManager();
        if (fontManager == null) {
            return;
        }

        Map<ResourceLocation, FontSet> fontSets = fontSets(fontManager);
        if (fontSets == null || fontSets.isEmpty()) {
            return;
        }

        String signature = fontSignature(stack);
        boolean signatureChanged = !signature.equals(activeSignature);

        boolean changed = false;
        for (Map.Entry<ResourceLocation, FontSet> entry : fontSets.entrySet()) {
            if (!isMutableFontSet(entry.getKey(), entry.getValue()) || ClientCorruptionProtection.isProtectedResource(entry.getKey())) {
                continue;
            }
            List<GlyphProvider> providers = providers(entry.getValue());
            if (providers == null || providers.isEmpty()) {
                continue;
            }
            if (!signatureChanged && allProvidersUseSignature(providers, signature)) {
                if (forceRefresh) {
                    List<GlyphProvider> unwrapped = unwrapProviders(providers);
                    List<GlyphProvider> wrapped = new ArrayList<>(unwrapped.size());
                    for (GlyphProvider provider : unwrapped) {
                        wrapped.add(new CorruptingGlyphProvider(provider, entry.getKey(), signature));
                    }
                    reloadFontSet(entry.getValue(), wrapped);
                    changed = true;
                }
                continue;
            }

            List<GlyphProvider> unwrapped = unwrapProviders(providers);
            List<GlyphProvider> wrapped = new ArrayList<>(unwrapped.size());
            for (GlyphProvider provider : unwrapped) {
                wrapped.add(new CorruptingGlyphProvider(provider, entry.getKey(), signature));
            }
            if (reloadFontSet(entry.getValue(), wrapped)) {
                changed = true;
            }
        }

        activeSignature = signature;
        if (changed) {
            reportFontMutation();
        }
    }

    private static void restoreProviders() {
        FontManager fontManager = fontManager();
        if (fontManager == null) {
            return;
        }

        Map<ResourceLocation, FontSet> fontSets = fontSets(fontManager);
        if (fontSets == null || fontSets.isEmpty()) {
            return;
        }

        for (Map.Entry<ResourceLocation, FontSet> entry : fontSets.entrySet()) {
            if (!isMutableFontSet(entry.getKey(), entry.getValue())) {
                continue;
            }
            List<GlyphProvider> providers = providers(entry.getValue());
            if (providers == null || providers.isEmpty()) {
                continue;
            }
            List<GlyphProvider> unwrapped = unwrapProviders(providers);
            if (unwrapped.size() != providers.size() || containsCorruptingProvider(providers)) {
                reloadFontSet(entry.getValue(), unwrapped);
            }
        }
    }

    private static boolean reloadFontSet(FontSet fontSet, List<GlyphProvider> replacement) {
        List<GlyphProvider> current = providers(fontSet);
        if (current != null && !containsCorruptingProvider(current)) {
            replaceProviders(fontSet, new ArrayList<>());
        }
        try {
            fontSet.reload(new ArrayList<>(replacement));
            return true;
        } catch (RuntimeException exception) {
            if (replaceProviders(fontSet, replacement)) {
                resetFontSetAtlases(fontSet);
                return true;
            }
            return false;
        }
    }

    private static List<GlyphProvider> unwrapProviders(List<GlyphProvider> providers) {
        List<GlyphProvider> unwrapped = new ArrayList<>(providers.size());
        for (GlyphProvider provider : providers) {
            unwrapped.add(provider instanceof CorruptingGlyphProvider corrupting ? corrupting.delegate() : provider);
        }
        return unwrapped;
    }

    private static boolean containsCorruptingProvider(List<GlyphProvider> providers) {
        for (GlyphProvider provider : providers) {
            if (provider instanceof CorruptingGlyphProvider) {
                return true;
            }
        }
        return false;
    }

    private static boolean allProvidersUseSignature(List<GlyphProvider> providers, String signature) {
        for (GlyphProvider provider : providers) {
            if (!(provider instanceof CorruptingGlyphProvider corrupting) || !corrupting.signature().equals(signature)) {
                return false;
            }
        }
        return !providers.isEmpty();
    }

    private static boolean replaceProviders(FontSet fontSet, List<GlyphProvider> replacement) {
        Field field = fontSetProvidersField;
        if (field == null) {
            field = findField(FontSet.class, "providers", "f_95055_");
            fontSetProvidersField = field;
        }
        if (field == null) {
            return false;
        }
        try {
            field.set(fontSet, replacement);
            return true;
        } catch (IllegalAccessException | RuntimeException ex) {
            return false;
        }
    }

    private static FontManager fontManager() {
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft == null) {
            return null;
        }
        Field field = minecraftFontManagerField;
        if (field == null) {
            field = findField(Minecraft.class, "fontManager", "f_91045_");
            minecraftFontManagerField = field;
        }
        if (field == null) {
            return null;
        }
        try {
            Object value = field.get(minecraft);
            return value instanceof FontManager manager ? manager : null;
        } catch (IllegalAccessException ex) {
            return null;
        }
    }

    @SuppressWarnings("unchecked")
    private static Map<ResourceLocation, FontSet> fontSets(FontManager fontManager) {
        Field field = fontManagerFontSetsField;
        if (field == null) {
            field = findField(FontManager.class, "fontSets", "f_94999_");
            fontManagerFontSetsField = field;
        }
        if (field == null) {
            return null;
        }
        try {
            Object value = field.get(fontManager);
            return value instanceof Map<?, ?> map ? (Map<ResourceLocation, FontSet>) map : null;
        } catch (IllegalAccessException ex) {
            return null;
        }
    }

    @SuppressWarnings("unchecked")
    private static List<GlyphProvider> providers(FontSet fontSet) {
        Field field = fontSetProvidersField;
        if (field == null) {
            field = findField(FontSet.class, "providers", "f_95055_");
            fontSetProvidersField = field;
        }
        if (field == null) {
            return null;
        }
        try {
            Object value = field.get(fontSet);
            return value instanceof List<?> list ? (List<GlyphProvider>) list : null;
        } catch (IllegalAccessException ex) {
            return null;
        }
    }

    private static void resetFontSetAtlases(FontSet fontSet) {
        closeFontSetTextures(fontSet);
        clearFontSetCaches(fontSet);
    }

    private static void clearFontSetCaches(FontSet fontSet) {
        clearFieldValue(fontSet, glyphsField());
        clearFieldValue(fontSet, glyphInfosField());
        clearFieldValue(fontSet, glyphsByWidthField());
    }

    private static Field glyphsField() {
        Field field = fontSetGlyphsField;
        if (field == null) {
            field = findField(FontSet.class, "glyphs", "f_95056_");
            fontSetGlyphsField = field;
        }
        return field;
    }

    private static Field glyphInfosField() {
        Field field = fontSetGlyphInfosField;
        if (field == null) {
            field = findField(FontSet.class, "glyphInfos", "f_95057_");
            fontSetGlyphInfosField = field;
        }
        return field;
    }

    private static Field glyphsByWidthField() {
        Field field = fontSetGlyphsByWidthField;
        if (field == null) {
            field = findField(FontSet.class, "glyphsByWidth", "f_95058_");
            fontSetGlyphsByWidthField = field;
        }
        return field;
    }

    private static void closeFontSetTextures(FontSet fontSet) {
        if (fontSet == null) {
            return;
        }
        Method method = fontSetCloseTexturesMethod;
        if (method == null) {
            method = findMethod(FontSet.class, "closeTextures", "m_95080_");
            fontSetCloseTexturesMethod = method;
        }
        if (method == null) {
            clearFieldValue(fontSet, findField(FontSet.class, "textures", "f_95059_"));
            return;
        }
        try {
            method.invoke(fontSet);
        } catch (ReflectiveOperationException | RuntimeException ignored) {
            clearFieldValue(fontSet, findField(FontSet.class, "textures", "f_95059_"));
        }
    }

    private static void clearFieldValue(Object owner, Field field) {
        if (owner == null || field == null) {
            return;
        }
        try {
            Object value = field.get(owner);
            if (value != null) {
                value.getClass().getMethod("clear").invoke(value);
            }
        } catch (ReflectiveOperationException | RuntimeException ignored) {
        }
    }

    private static Field findField(Class<?> type, String... names) {
        for (String name : names) {
            try {
                Field field = type.getDeclaredField(name);
                field.setAccessible(true);
                return field;
            } catch (NoSuchFieldException ignored) {
            }
        }
        return null;
    }

    private static Method findMethod(Class<?> type, String... names) {
        for (String name : names) {
            try {
                Method method = type.getDeclaredMethod(name);
                method.setAccessible(true);
                return method;
            } catch (NoSuchMethodException ignored) {
            }
        }
        return null;
    }

    private static boolean fontMutationActive(CorruptionEffectStack stack) {
        return stack.activeOrExtreme(CorruptionSurface.GUI_SURFACE);
    }

    private static boolean isMutableFontSet(ResourceLocation fontId, FontSet fontSet) {
        return isNamedResource(fontId) && isNamedResource(fontSetName(fontSet));
    }

    private static boolean isNamedResource(ResourceLocation id) {
        return id != null && id.getPath() != null && !id.getPath().isBlank();
    }

    private static ResourceLocation fontSetName(FontSet fontSet) {
        if (fontSet == null) {
            return null;
        }
        Field field = fontSetNameField;
        if (field == null) {
            field = findField(FontSet.class, "name", "f_95052_");
            fontSetNameField = field;
        }
        if (field == null) {
            return null;
        }
        try {
            Object value = field.get(fontSet);
            return value instanceof ResourceLocation id ? id : null;
        } catch (IllegalAccessException | RuntimeException ignored) {
            return null;
        }
    }

    private static String fontSignature(CorruptionEffectStack stack) {
        return stack.level()
                + ":" + stack.previousLevel()
                + ":" + stack.delta()
                + ":" + stack.fixedSeed()
                + ":" + stack.bucket(CorruptionSurface.GUI_SURFACE, 0x475549, 64);
    }

    private static float fontIntensity(CorruptionEffectStack stack, String targetId) {
        if (stack.extreme(CorruptionSurface.GUI_SURFACE)) {
            return 1.0F;
        }
        float gui = Math.max(stack.targetIntensity(CorruptionSurface.GUI_SURFACE, targetId), stack.intensity(CorruptionSurface.GUI_SURFACE) * 0.92F);
        return clampFloat(gui, 0.0F, 1.0F);
    }

    private static void reportFontMutation() {
        long now = System.currentTimeMillis();
        if (now - lastReportMs > 1500L) {
            lastReportMs = now;
        }
    }

    private static float clampFloat(float value, float min, float max) {
        return Math.max(min, Math.min(max, value));
    }

    private static float unit(long value) {
        return ((mix(value) >>> 40) & 0xFF_FFFFL) / 16_777_215.0F;
    }

    private static int signedOffset(long value, int amplitude) {
        if (amplitude <= 0) {
            return 0;
        }
        return Math.round((unit(value) * 2.0F - 1.0F) * amplitude);
    }

    private static long mix(long value) {
        value ^= value >>> 33;
        value *= 0xff51afd7ed558ccdL;
        value ^= value >>> 33;
        value *= 0xc4ceb9fe1a85ec53L;
        value ^= value >>> 33;
        return value;
    }

    private record CorruptingGlyphProvider(GlyphProvider delegate, ResourceLocation fontId, String signature) implements GlyphProvider {
        @Override
        @Nullable
        public GlyphInfo getGlyph(int codepoint) {
            GlyphInfo glyph = delegate.getGlyph(codepoint);
            return glyph == null ? null : new CorruptingGlyphInfo(glyph, fontId, codepoint, signature);
        }

        @Override
        public IntSet getSupportedGlyphs() {
            return delegate.getSupportedGlyphs();
        }

        @Override
        public void close() {
        }
    }

    private record CorruptingGlyphInfo(GlyphInfo delegate, ResourceLocation fontId, int codepoint, String signature) implements GlyphInfo {
        @Override
        public float getAdvance() {
            return delegate.getAdvance();
        }

        @Override
        public float getAdvance(boolean bold) {
            return getAdvance() + (bold ? getBoldOffset() : 0.0F);
        }

        @Override
        public float getBoldOffset() {
            return delegate.getBoldOffset();
        }

        @Override
        public float getShadowOffset() {
            return delegate.getShadowOffset();
        }

        @Override
        public BakedGlyph bake(Function<SheetGlyphInfo, BakedGlyph> stitcher) {
            BakedGlyph glyph = delegate.bake(sheet -> stitcher.apply(new CorruptingSheetGlyphInfo(sheet, targetId(), signature)));
            return wrapBakedGlyph(glyph, targetId(), signature);
        }

        private String targetId() {
            return "font:" + fontId + ":" + Integer.toHexString(codepoint);
        }
    }

    private record CorruptingSheetGlyphInfo(SheetGlyphInfo delegate, String targetId, String signature) implements SheetGlyphInfo {
        @Override
        public int getPixelWidth() {
            return delegate.getPixelWidth();
        }

        @Override
        public int getPixelHeight() {
            return delegate.getPixelHeight();
        }

        @Override
        public void upload(int x, int y) {
            uploadGlyph(delegate, x, y);
        }

        @Override
        public boolean isColored() {
            return delegate.isColored();
        }

        @Override
        public float getOversample() {
            return delegate.getOversample();
        }

        @Override
        public float getBearingX() {
            return delegate.getBearingX();
        }

        @Override
        public float getBearingY() {
            return delegate.getBearingY();
        }
    }

    private static BakedGlyph wrapBakedGlyph(BakedGlyph glyph, String targetId, String signature) {
        if (glyph == null || glyph instanceof CorruptingBakedGlyph) {
            return glyph;
        }
        try {
            return new CorruptingBakedGlyph(glyph, targetId, signature);
        } catch (RuntimeException exception) {
            return glyph;
        }
    }

    private static GlyphRenderTypes bakedGlyphRenderTypes(BakedGlyph glyph) {
        Field field = bakedGlyphRenderTypesField;
        if (field == null) {
            field = findField(BakedGlyph.class, "renderTypes", "f_283799_");
            bakedGlyphRenderTypesField = field;
        }
        if (field == null) {
            throw new IllegalStateException("Missing BakedGlyph render type field");
        }
        try {
            Object value = field.get(glyph);
            if (value instanceof GlyphRenderTypes renderTypes) {
                return renderTypes;
            }
        } catch (IllegalAccessException ignored) {
        }
        throw new IllegalStateException("Invalid BakedGlyph render type field");
    }

    private static float bakedGlyphFloat(BakedGlyph glyph, String fieldName, String obfuscatedName) {
        Field field = switch (fieldName) {
            case "u0" -> bakedGlyphU0Field == null ? bakedGlyphU0Field = findField(BakedGlyph.class, fieldName, obfuscatedName) : bakedGlyphU0Field;
            case "u1" -> bakedGlyphU1Field == null ? bakedGlyphU1Field = findField(BakedGlyph.class, fieldName, obfuscatedName) : bakedGlyphU1Field;
            case "v0" -> bakedGlyphV0Field == null ? bakedGlyphV0Field = findField(BakedGlyph.class, fieldName, obfuscatedName) : bakedGlyphV0Field;
            case "v1" -> bakedGlyphV1Field == null ? bakedGlyphV1Field = findField(BakedGlyph.class, fieldName, obfuscatedName) : bakedGlyphV1Field;
            case "left" -> bakedGlyphLeftField == null ? bakedGlyphLeftField = findField(BakedGlyph.class, fieldName, obfuscatedName) : bakedGlyphLeftField;
            case "right" -> bakedGlyphRightField == null ? bakedGlyphRightField = findField(BakedGlyph.class, fieldName, obfuscatedName) : bakedGlyphRightField;
            case "up" -> bakedGlyphUpField == null ? bakedGlyphUpField = findField(BakedGlyph.class, fieldName, obfuscatedName) : bakedGlyphUpField;
            case "down" -> bakedGlyphDownField == null ? bakedGlyphDownField = findField(BakedGlyph.class, fieldName, obfuscatedName) : bakedGlyphDownField;
            default -> null;
        };
        if (field == null) {
            throw new IllegalStateException("Missing BakedGlyph field " + fieldName);
        }
        try {
            return field.getFloat(glyph);
        } catch (IllegalAccessException exception) {
            throw new IllegalStateException("Invalid BakedGlyph field " + fieldName, exception);
        }
    }

    private static void glyphVertex(VertexConsumer consumer, Matrix4f matrix, float x, float y, float u, float v, float red, float green, float blue, float alpha, int light) {
        consumer.vertex(matrix, x, y, 0.0F)
                .color(red, green, blue, alpha)
                .uv(u, v)
                .uv2(light)
                .endVertex();
    }

    private static final class CorruptingBakedGlyph extends BakedGlyph {
        private final BakedGlyph delegate;
        private final String targetId;
        private final String signature;
        private final float u0;
        private final float u1;
        private final float v0;
        private final float v1;
        private final float left;
        private final float right;
        private final float up;
        private final float down;

        private CorruptingBakedGlyph(BakedGlyph delegate, String targetId, String signature) {
            this(
                    delegate,
                    targetId,
                    signature,
                    bakedGlyphRenderTypes(delegate),
                    bakedGlyphFloat(delegate, "u0", "f_95201_"),
                    bakedGlyphFloat(delegate, "u1", "f_95202_"),
                    bakedGlyphFloat(delegate, "v0", "f_95203_"),
                    bakedGlyphFloat(delegate, "v1", "f_95204_"),
                    bakedGlyphFloat(delegate, "left", "f_95205_"),
                    bakedGlyphFloat(delegate, "right", "f_95206_"),
                    bakedGlyphFloat(delegate, "up", "f_95207_"),
                    bakedGlyphFloat(delegate, "down", "f_95208_")
            );
        }

        private CorruptingBakedGlyph(BakedGlyph delegate, String targetId, String signature, GlyphRenderTypes renderTypes, float u0, float u1, float v0, float v1, float left, float right, float up, float down) {
            super(renderTypes, u0, u1, v0, v1, left, right, up, down);
            this.delegate = delegate;
            this.targetId = targetId;
            this.signature = signature;
            this.u0 = u0;
            this.u1 = u1;
            this.v0 = v0;
            this.v1 = v1;
            this.left = left;
            this.right = right;
            this.up = up;
            this.down = down;
        }

        @Override
        public void render(boolean italic, float x, float y, Matrix4f matrix, VertexConsumer consumer, float red, float green, float blue, float alpha, int light) {
            CorruptionEffectStack stack = ClientCorruptionEffects.current();
            float intensity = fontIntensity(stack, targetId);
            if (intensity <= 0.10F) {
                delegate.render(italic, x, y, matrix, consumer, red, green, blue, alpha, light);
                return;
            }

            long seed = stack.stableLong(CorruptionSurface.GUI_SURFACE, targetId + ":stretch_render:" + signature, 0x53545246) ^ FONT_MUTATION_SEED;
            float warpChance = stack.extreme(CorruptionSurface.GUI_SURFACE)
                    ? 0.72F
                    : clampFloat(0.075F + intensity * 0.42F, 0.0F, 0.60F);
            if (unit(seed ^ 0x474C595048474154L) > warpChance) {
                delegate.render(italic, x, y, matrix, consumer, red, green, blue, alpha, light);
                return;
            }

            float warpIntensity = clampFloat(intensity * (0.48F + unit(seed ^ 0x57415250494E54L) * 0.78F), 0.0F, 1.20F);
            float x0 = x + left;
            float x1 = x + right;
            float y0 = y + up - 3.0F;
            float y1 = y + down - 3.0F;
            float width = Math.max(1.0F, x1 - x0);
            float height = Math.max(1.0F, y1 - y0);
            int strips = 2 + Math.round(warpIntensity * 6.0F);
            float stretch = warpIntensity * warpIntensity;
            float maxExtraX = clampFloat(width * (0.24F + warpIntensity * 2.15F) + unit(seed >>> 9) * 24.0F * stretch, 0.60F, 54.0F);
            float maxExtraY = clampFloat(height * (0.06F + warpIntensity * 0.38F), 0.15F, 8.25F);
            float minX = x0 - maxExtraX;
            float maxX = x1 + maxExtraX;
            float minY = y0 - maxExtraY;
            float maxY = y1 + maxExtraY;
            float italicSkew = italic ? 0.18F + warpIntensity * 0.46F : 0.0F;

            for (int strip = 0; strip < strips; strip++) {
                float t0 = strip / (float) strips;
                float t1 = (strip + 1) / (float) strips;
                float top = y0 + height * t0;
                float bottom = y0 + height * t1;
                long h0 = mix(seed ^ strip * 0x9E3779B97F4A7C15L);
                long h1 = mix(seed ^ (strip + 1L) * 0xBF58476D1CE4E5B9L);
                float leftPull0 = maxExtraX * unit(h0 >>> 19) * (0.10F + stretch);
                float rightPull0 = maxExtraX * unit(h0 >>> 31) * (0.10F + stretch);
                float leftPull1 = maxExtraX * unit(h1 >>> 19) * (0.10F + stretch);
                float rightPull1 = maxExtraX * unit(h1 >>> 31) * (0.10F + stretch);
                if (unit(h0 >>> 51) < 0.06F + stretch * 0.42F) {
                    rightPull0 += maxExtraX * (0.18F + unit(h0 >>> 3) * 0.82F);
                }
                if (unit(h1 >>> 51) < 0.06F + stretch * 0.42F) {
                    leftPull1 += maxExtraX * (0.18F + unit(h1 >>> 3) * 0.82F);
                }
                float jitterSpan = Math.max(0.25F, maxExtraX * (0.04F + warpIntensity * 0.16F));
                float left0 = clampFloat(x0 + signedOffset(h0 >>> 7, Math.round(jitterSpan)) - leftPull0 + (1.0F - t0) * italicSkew, minX, maxX);
                float right0 = clampFloat(x1 + signedOffset(h0 >>> 23, Math.round(jitterSpan)) + rightPull0 + (1.0F - t0) * italicSkew, minX, maxX);
                float left1 = clampFloat(x0 + signedOffset(h1 >>> 7, Math.round(jitterSpan)) - leftPull1 + (1.0F - t1) * italicSkew, minX, maxX);
                float right1 = clampFloat(x1 + signedOffset(h1 >>> 23, Math.round(jitterSpan)) + rightPull1 + (1.0F - t1) * italicSkew, minX, maxX);
                float topOffset = signedOffset(h0 >>> 39, Math.round(maxExtraY));
                float bottomOffset = signedOffset(h1 >>> 39, Math.round(maxExtraY));
                if (unit(h0 >>> 5) < warpIntensity * 0.34F) {
                    float center = (top + bottom) * 0.5F + topOffset * 0.35F;
                    float thickness = clampFloat(0.18F + unit(h0 >>> 13) * 0.72F, 0.18F, Math.max(0.24F, height / strips));
                    top = center - thickness;
                    bottom = center + thickness;
                    topOffset = 0.0F;
                    bottomOffset = 0.0F;
                }
                float uvTop = v0 + (v1 - v0) * t0;
                float uvBottom = v0 + (v1 - v0) * t1;

                glyphVertex(consumer, matrix, left0, clampFloat(top + topOffset, minY, maxY), u0, uvTop, red, green, blue, alpha, light);
                glyphVertex(consumer, matrix, left1, clampFloat(bottom + bottomOffset, minY, maxY), u0, uvBottom, red, green, blue, alpha, light);
                glyphVertex(consumer, matrix, right1, clampFloat(bottom - bottomOffset * 0.18F, minY, maxY), u1, uvBottom, red, green, blue, alpha, light);
                glyphVertex(consumer, matrix, right0, clampFloat(top - topOffset * 0.18F, minY, maxY), u1, uvTop, red, green, blue, alpha, light);
            }

            int lineCount = unit(seed ^ 0x4C494E454348L) < 0.04F + warpIntensity * 0.32F ? 1 + Math.round(warpIntensity * 2.0F) : 0;
            for (int line = 0; line < lineCount; line++) {
                long hash = mix(seed ^ 0x4C494E45L ^ line * 0x94D049BB133111EBL);
                float center = clampFloat(y0 + height * unit(hash >>> 7) + signedOffset(hash >>> 19, Math.max(1, Math.round(maxExtraY))), minY, maxY);
                float thickness = clampFloat(0.30F + unit(hash >>> 27) * (0.54F + warpIntensity * 0.55F), 0.30F, 2.55F);
                float reachLeft = maxExtraX * (0.25F + unit(hash >>> 35) * 0.75F);
                float reachRight = maxExtraX * (0.25F + unit(hash >>> 43) * 0.75F);
                float start = clampFloat(x0 - reachLeft, minX, maxX);
                float end = clampFloat(x1 + reachRight, minX, maxX);
                float uvTop = v0 + (v1 - v0) * clampFloat(unit(hash >>> 11), 0.0F, 0.96F);
                float uvBottom = Math.min(v1, uvTop + (v1 - v0) * (0.025F + unit(hash >>> 22) * 0.12F));
                float lineAlpha = alpha * clampFloat(0.54F + warpIntensity * 0.42F, 0.0F, 0.92F);

                glyphVertex(consumer, matrix, start, clampFloat(center - thickness, minY, maxY), u0, uvTop, red, green, blue, lineAlpha, light);
                glyphVertex(consumer, matrix, start, clampFloat(center + thickness, minY, maxY), u0, uvBottom, red, green, blue, lineAlpha, light);
                glyphVertex(consumer, matrix, end, clampFloat(center + thickness, minY, maxY), u1, uvBottom, red, green, blue, lineAlpha, light);
                glyphVertex(consumer, matrix, end, clampFloat(center - thickness, minY, maxY), u1, uvTop, red, green, blue, lineAlpha, light);
            }
        }
    }

    private static boolean uploadGlyph(SheetGlyphInfo glyph, int x, int y) {
        try {
            glyph.upload(x, y);
            return true;
        } catch (RuntimeException ex) {
            return false;
        }
    }
}
