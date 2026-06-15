package com.maxsters.realtimeminecraftcorruptionsimulator.client.effects;

import com.maxsters.realtimeminecraftcorruptionsimulator.RealtimeMinecraftCorruptionSimulator;
import com.maxsters.realtimeminecraftcorruptionsimulator.client.ClientCorruptionProtection;
import com.maxsters.realtimeminecraftcorruptionsimulator.profile.CorruptionEffectStack;
import com.maxsters.realtimeminecraftcorruptionsimulator.profile.CorruptionSurface;
import com.maxsters.realtimeminecraftcorruptionsimulator.profile.CorruptionValueMutator;
import com.maxsters.realtimeminecraftcorruptionsimulator.state.CorruptionProfileSnapshot;
import com.mojang.blaze3d.platform.NativeImage;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.texture.DynamicTexture;
import net.minecraft.client.renderer.texture.SpriteContents;
import net.minecraft.client.renderer.texture.TextureAtlas;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.packs.resources.Resource;
import net.minecraft.server.packs.resources.ResourceManager;
import net.minecraft.util.FastColor;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.api.distmarker.OnlyIn;
import net.minecraftforge.client.event.TextureStitchEvent;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

@OnlyIn(Dist.CLIENT)
@Mod.EventBusSubscriber(modid = RealtimeMinecraftCorruptionSimulator.MOD_ID, value = Dist.CLIENT, bus = Mod.EventBusSubscriber.Bus.FORGE)
public final class TextureMutationManager {
    private static final String GUI_TEXTURE_PREFIX = "textures/gui";
    private static final String ALL_TEXTURE_PREFIX = "textures";
    private static final int MAX_GUI_TEXTURES_PER_SCAN = 512;
    private static final int MAX_GUI_TEXTURE_MUTATIONS_PER_TICK = 24;
    private static final int MAX_GLOBAL_TEXTURES_PER_SCAN = 4096;
    private static final int MAX_GLOBAL_TEXTURE_MUTATIONS_PER_TICK = 18;
    private static final int MAX_ATLAS_TEXTURE_MUTATIONS_PER_ATLAS = 8192;
    private static final int MAX_ATLAS_TEXTURE_MUTATIONS_PER_TICK = 96;
    private static final int MAX_TEXTURE_PIXELS_TO_MUTATE = 4_194_304;
    private static final Set<ResourceLocation> MUTATED_GUI_TEXTURES = new HashSet<>();
    private static final Set<ResourceLocation> MUTATED_GLOBAL_TEXTURES = new HashSet<>();
    private static final Set<AtlasSpriteKey> MUTATED_ATLAS_TEXTURES = new HashSet<>();
    private static final Map<AtlasSpriteKey, AtlasSpriteSnapshot> ATLAS_ORIGINALS = new HashMap<>();
    private static final List<TextureAtlas> KNOWN_ATLASES = new ArrayList<>();
    private static PendingGuiTextureScan pendingGuiTextureScan;
    private static PendingGlobalTextureScan pendingGlobalTextureScan;
    private static PendingAtlasTextureScan pendingAtlasTextureScan;
    private static boolean startupTextureScanRequested;
    private static boolean startupGlobalTextureScanRequested;
    private static boolean atlasTextureScanRequested;
    private static String appliedGuiTextureSignature = "";
    private static String appliedGlobalTextureSignature = "";
    private static String appliedAtlasTextureSignature = "";
    private static long lastTextureScanAttemptMs;
    private static long lastGlobalTextureScanAttemptMs;

    private TextureMutationManager() {
    }

    public static void onTextureStitchPost(TextureStitchEvent.Post event) {
        TextureAtlas atlas = event.getAtlas();
        if (atlas == null) {
            return;
        }

        rememberAtlas(atlas);
        for (ResourceLocation spriteId : atlas.getTextureLocations()) {
            ItemTextureCorruptionManager.rememberAtlasSprite(atlas.getSprite(spriteId));
        }
        appliedAtlasTextureSignature = "";
    }

    public static void requestGuiTextureScan() {
        startupTextureScanRequested = true;
    }

    private static void rememberAtlas(TextureAtlas atlas) {
        for (int index = 0; index < KNOWN_ATLASES.size(); index++) {
            TextureAtlas known = KNOWN_ATLASES.get(index);
            if (known == atlas) {
                return;
            }
            if (known.location().equals(atlas.location())) {
                KNOWN_ATLASES.set(index, atlas);
                return;
            }
        }
        KNOWN_ATLASES.add(atlas);
    }

    public static void onSettingsChanged(CorruptionProfileSnapshot previous, CorruptionProfileSnapshot current) {
        CorruptionEffectStack previousStack = CorruptionEffectStack.from(previous);
        CorruptionEffectStack currentStack = CorruptionEffectStack.from(current);
        if (textureSettingsSignature(previousStack).equals(textureSettingsSignature(currentStack))) {
            return;
        }

        if (!guiTextureSignature(previousStack).equals(guiTextureSignature(currentStack))) {
            pendingGuiTextureScan = null;
            startupTextureScanRequested = true;
        }
        if (!globalTextureSignature(previousStack).equals(globalTextureSignature(currentStack))) {
            pendingGlobalTextureScan = null;
            startupGlobalTextureScanRequested = true;
        }
        if (!atlasTextureSignature(previousStack).equals(atlasTextureSignature(currentStack))) {
            pendingAtlasTextureScan = null;
            appliedAtlasTextureSignature = "";
            atlasTextureScanRequested = true;
        }
    }

    @SubscribeEvent
    public static void onClientTick(TickEvent.ClientTickEvent event) {
        if (event.phase != TickEvent.Phase.END) {
            return;
        }

        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft == null || minecraft.getTextureManager() == null || minecraft.getResourceManager() == null) {
            return;
        }

        CorruptionEffectStack stack = ClientCorruptionEffects.current();
        if (ClientCorruptionProtection.shouldSuppressClientCorruption() && stack.level() > 0) {
            return;
        }
        handleGuiTextureMutations(minecraft, stack);
        handleGlobalTextureMutations(minecraft, stack);
        handleAtlasTextureMutations(stack);
    }

    private static void handleGuiTextureMutations(Minecraft minecraft, CorruptionEffectStack stack) {
        if (!shouldMutateGuiTextures(stack)) {
            pendingGuiTextureScan = null;
            if (!MUTATED_GUI_TEXTURES.isEmpty()) {
                restoreGuiTextures(minecraft);
            }
            appliedGuiTextureSignature = "";
            startupTextureScanRequested = false;
            return;
        }

        String signature = guiTextureSignature(stack);
        if (pendingGuiTextureScan != null) {
            if (signature.equals(pendingGuiTextureScan.signature)) {
                processPendingGuiTextureMutations(minecraft);
                return;
            }
            pendingGuiTextureScan = null;
        }
        if (!startupTextureScanRequested) {
            return;
        }
        if (signature.equals(appliedGuiTextureSignature)) {
            startupTextureScanRequested = false;
            return;
        }

        long now = System.currentTimeMillis();
        if (now - lastTextureScanAttemptMs < 250L) {
            return;
        }
        lastTextureScanAttemptMs = now;
        applyGuiTextureMutations(minecraft, stack, signature);
    }

    private static void handleGlobalTextureMutations(Minecraft minecraft, CorruptionEffectStack stack) {
        if (!shouldMutateGlobalTextures(stack)) {
            pendingGlobalTextureScan = null;
            if (!MUTATED_GLOBAL_TEXTURES.isEmpty()) {
                restoreGlobalTextures(minecraft);
            }
            appliedGlobalTextureSignature = "";
            startupGlobalTextureScanRequested = false;
            return;
        }

        String signature = globalTextureSignature(stack);
        if (pendingGlobalTextureScan != null) {
            if (signature.equals(pendingGlobalTextureScan.signature)) {
                processPendingGlobalTextureMutations(minecraft);
                return;
            }
            pendingGlobalTextureScan = null;
        }
        if (!startupGlobalTextureScanRequested) {
            if (signature.equals(appliedGlobalTextureSignature)) {
                return;
            }
            return;
        }

        long now = System.currentTimeMillis();
        if (now - lastGlobalTextureScanAttemptMs < 250L) {
            return;
        }
        lastGlobalTextureScanAttemptMs = now;
        applyGlobalTextureMutations(minecraft, stack, signature);
    }

    private static void handleAtlasTextureMutations(CorruptionEffectStack stack) {
        pendingAtlasTextureScan = null;
        atlasTextureScanRequested = false;
        appliedAtlasTextureSignature = atlasTextureSignature(stack);
        if (!MUTATED_ATLAS_TEXTURES.isEmpty()) {
            MUTATED_ATLAS_TEXTURES.clear();
            ATLAS_ORIGINALS.clear();
        }
    }

    private static boolean shouldMutateGuiTextures(CorruptionEffectStack stack) {
        return stack.activeOrExtreme(CorruptionSurface.GUI_SURFACE);
    }

    private static boolean shouldMutateGlobalTextures(CorruptionEffectStack stack) {
        return stack.activeOrExtreme(CorruptionSurface.TEXTURE_MEMORY);
    }

    private static String textureSettingsSignature(CorruptionEffectStack stack) {
        return guiTextureSignature(stack) + "|" + globalTextureSignature(stack) + "|" + atlasTextureSignature(stack);
    }

    private static String guiTextureSignature(CorruptionEffectStack stack) {
        return stack.level()
                + ":"
                + stack.bucket(CorruptionSurface.GUI_SURFACE, 0x475549, 64)
                + ":"
                + stack.bucket(CorruptionSurface.TEXTURE_MEMORY, 0x4D454D, 64)
                + ":"
                + stack.stableLong(CorruptionSurface.GUI_SURFACE, 0x545854);
    }

    private static String globalTextureSignature(CorruptionEffectStack stack) {
        return stack.level()
                + ":"
                + stack.bucket(CorruptionSurface.TEXTURE_MEMORY, 0x474C4F42, 64)
                + ":"
                + stack.stableLong(CorruptionSurface.TEXTURE_MEMORY, 0x414C4C54);
    }

    private static String atlasTextureSignature(CorruptionEffectStack stack) {
        return stack.level()
                + ":"
                + stack.bucket(CorruptionSurface.TEXTURE_MEMORY, 0x41544C41, 64)
                + ":"
                + stack.stableLong(CorruptionSurface.TEXTURE_MEMORY, 0x53544954);
    }

    private static void applyGuiTextureMutations(Minecraft minecraft, CorruptionEffectStack stack, String signature) {
        ResourceManager resourceManager = minecraft.getResourceManager();
        Map<ResourceLocation, Resource> resources = resourceManager.listResources(
                GUI_TEXTURE_PREFIX,
                location -> "minecraft".equals(location.getNamespace()) && location.getPath().endsWith(".png")
        );

        List<ResourceLocation> textureIds = new ArrayList<>(resources.keySet());
        textureIds.sort(Comparator.comparing(ResourceLocation::toString));
        List<ResourceLocation> donorTextureIds = List.of();

        pendingGuiTextureScan = new PendingGuiTextureScan(signature, stack, resources, textureIds, donorTextureIds, new HashSet<>(MUTATED_GUI_TEXTURES));
        processPendingGuiTextureMutations(minecraft);
    }

    private static void applyGlobalTextureMutations(Minecraft minecraft, CorruptionEffectStack stack, String signature) {
        ResourceManager resourceManager = minecraft.getResourceManager();
        Map<ResourceLocation, Resource> resources = resourceManager.listResources(
                ALL_TEXTURE_PREFIX,
                TextureMutationManager::isGlobalTextureResource
        );

        List<ResourceLocation> textureIds = new ArrayList<>(resources.keySet());
        textureIds.sort(TextureMutationManager::compareGlobalTexturePriority);

        pendingGlobalTextureScan = new PendingGlobalTextureScan(signature, stack, resources, textureIds, List.of(), new HashSet<>(MUTATED_GLOBAL_TEXTURES));
        processPendingGlobalTextureMutations(minecraft);
    }

    private static void processPendingGuiTextureMutations(Minecraft minecraft) {
        PendingGuiTextureScan scan = pendingGuiTextureScan;
        if (scan == null) {
            return;
        }

        int processedThisTick = 0;
        while (scan.ordinal < scan.textureIds.size()
                && scan.ordinal < MAX_GUI_TEXTURES_PER_SCAN
                && processedThisTick < MAX_GUI_TEXTURE_MUTATIONS_PER_TICK) {
            ResourceLocation textureId = scan.textureIds.get(scan.ordinal);
            float intensity = guiTextureIntensity(scan.stack, textureId, scan.ordinal);
            Resource resource = scan.resources.get(textureId);
            scan.ordinal++;
            processedThisTick++;
            if (resource == null || intensity <= 0.035F) {
                continue;
            }
            if (replaceTexture(minecraft, textureId, resource, scan.stack, intensity, scan.ordinal, true, scan.donorTextureIds, "GUI")) {
                MUTATED_GUI_TEXTURES.add(textureId);
                scan.staleTextureIds.remove(textureId);
                scan.mutatedCount++;
            }
        }

        if (scan.ordinal >= scan.textureIds.size() || scan.ordinal >= MAX_GUI_TEXTURES_PER_SCAN) {
            restoreGuiTextures(minecraft, scan.staleTextureIds);
            appliedGuiTextureSignature = scan.signature;
            startupTextureScanRequested = false;
            pendingGuiTextureScan = null;
            if (scan.mutatedCount > 0) {
                RealtimeMinecraftCorruptionSimulator.LOGGER.debug("Mutated {} GUI texture resources for corruption signature {}", scan.mutatedCount, scan.signature);
            }
        }
    }

    private static void processPendingGlobalTextureMutations(Minecraft minecraft) {
        PendingGlobalTextureScan scan = pendingGlobalTextureScan;
        if (scan == null) {
            return;
        }

        int processedThisTick = 0;
        while (scan.ordinal < scan.textureIds.size()
                && scan.ordinal < MAX_GLOBAL_TEXTURES_PER_SCAN
                && processedThisTick < MAX_GLOBAL_TEXTURE_MUTATIONS_PER_TICK) {
            ResourceLocation textureId = scan.textureIds.get(scan.ordinal);
            float intensity = globalTextureIntensity(scan.stack, textureId, scan.ordinal);
            Resource resource = scan.resources.get(textureId);
            scan.ordinal++;
            processedThisTick++;
            if (resource == null || intensity <= 0.035F) {
                continue;
            }
            if (replaceTexture(minecraft, textureId, resource, scan.stack, intensity, scan.ordinal, true, scan.donorTextureIds, "global")) {
                MUTATED_GLOBAL_TEXTURES.add(textureId);
                scan.staleTextureIds.remove(textureId);
                scan.mutatedCount++;
            }
        }

        if (scan.ordinal >= scan.textureIds.size() || scan.ordinal >= MAX_GLOBAL_TEXTURES_PER_SCAN) {
            restoreGlobalTextures(minecraft, scan.staleTextureIds);
            appliedGlobalTextureSignature = scan.signature;
            startupGlobalTextureScanRequested = false;
            pendingGlobalTextureScan = null;
            if (scan.mutatedCount > 0) {
                RealtimeMinecraftCorruptionSimulator.LOGGER.debug("Mutated {} non-atlas texture resources for corruption signature {}", scan.mutatedCount, scan.signature);
            }
        }
    }

    private static void processPendingAtlasTextureMutations() {
        PendingAtlasTextureScan scan = pendingAtlasTextureScan;
        if (scan == null) {
            return;
        }

        int processedThisTick = 0;
        while (scan.atlasOrdinal < scan.atlases.size() && processedThisTick < MAX_ATLAS_TEXTURE_MUTATIONS_PER_TICK) {
            TextureAtlas atlas = scan.atlases.get(scan.atlasOrdinal);
            if (atlas == null) {
                scan.atlasOrdinal++;
                scan.spriteIds = List.of();
                scan.spriteOrdinal = 0;
                scan.atlasSpriteOrdinal = 0;
                continue;
            }
            if (scan.spriteIds.isEmpty()) {
                scan.spriteIds = new ArrayList<>(atlas.getTextureLocations());
                scan.spriteIds.sort(TextureMutationManager::compareAtlasSpritePriority);
                scan.spriteOrdinal = 0;
                scan.atlasSpriteOrdinal = 0;
            }

            if (scan.spriteOrdinal >= scan.spriteIds.size() || scan.atlasSpriteOrdinal >= MAX_ATLAS_TEXTURE_MUTATIONS_PER_ATLAS) {
                scan.atlasOrdinal++;
                scan.spriteIds = List.of();
                scan.spriteOrdinal = 0;
                scan.atlasSpriteOrdinal = 0;
                continue;
            }

            ResourceLocation atlasId = atlas.location();
            ResourceLocation spriteId = scan.spriteIds.get(scan.spriteOrdinal);
            scan.spriteOrdinal++;
            scan.atlasSpriteOrdinal++;
            processedThisTick++;
            if (ClientCorruptionProtection.isProtectedResource(spriteId)) {
                continue;
            }

            TextureAtlasSprite sprite = atlas.getSprite(spriteId);
            ItemTextureCorruptionManager.rememberAtlasSprite(sprite);
            float intensity = atlasTextureIntensity(scan.stack, atlasId, spriteId, scan.atlasSpriteOrdinal);
            if (intensity <= 0.035F) {
                restoreAtlasSprite(atlas, spriteId);
                continue;
            }
            if (mutateAtlasSprite(atlas, sprite, spriteId, scan.spriteIds, scan.stack, intensity, scan.atlasSpriteOrdinal)) {
                scan.mutatedCount++;
            }
        }

        if (scan.atlasOrdinal >= scan.atlases.size()) {
            appliedAtlasTextureSignature = scan.signature;
            atlasTextureScanRequested = false;
            pendingAtlasTextureScan = null;
            if (scan.mutatedCount > 0) {
                RealtimeMinecraftCorruptionSimulator.LOGGER.debug("Mutated {} atlas texture sprites for corruption signature {}", scan.mutatedCount, scan.signature);
            }
        }
    }

    private static float guiTextureIntensity(CorruptionEffectStack stack, ResourceLocation textureId, int ordinal) {
        if (stack.extreme(CorruptionSurface.GUI_SURFACE)) {
            return 1.0F;
        }

        String targetId = "gui_texture:" + textureId;
        float base = Math.max(stack.intensity(CorruptionSurface.GUI_SURFACE), stack.intensity(CorruptionSurface.TEXTURE_MEMORY) * 0.72F);
        float target = stack.targetIntensity(CorruptionSurface.GUI_SURFACE, targetId);
        float drift = 0.72F + stack.unit(CorruptionSurface.GUI_SURFACE, targetId, ordinal ^ 0x5445) * 0.28F;
        return clampFloat(Math.max(base * 0.58F, target) * drift + stack.instability() * 0.05F, 0.0F, 1.0F);
    }

    private static float globalTextureIntensity(CorruptionEffectStack stack, ResourceLocation textureId, int ordinal) {
        if (stack.extreme(CorruptionSurface.TEXTURE_MEMORY) || stack.extreme(CorruptionSurface.WORLD_RENDER)) {
            return 1.0F;
        }

        String targetId = "texture_resource:" + textureId;
        float base = Math.max(stack.intensity(CorruptionSurface.TEXTURE_MEMORY), stack.intensity(CorruptionSurface.WORLD_RENDER) * 0.76F);
        float target = Math.max(
                stack.targetIntensity(CorruptionSurface.TEXTURE_MEMORY, targetId),
                stack.targetIntensity(CorruptionSurface.WORLD_RENDER, targetId) * 0.84F
        );
        float drift = 0.68F + stack.unit(CorruptionSurface.TEXTURE_MEMORY, targetId, ordinal ^ 0x414C) * 0.32F;
        return clampFloat(Math.max(base * 0.52F, target) * drift + stack.instability() * 0.06F, 0.0F, 1.0F);
    }

    private static float atlasTextureIntensity(CorruptionEffectStack stack, ResourceLocation atlasId, ResourceLocation spriteId, int ordinal) {
        if (stack.extreme(CorruptionSurface.TEXTURE_MEMORY) || stack.extreme(CorruptionSurface.MODEL_UV)) {
            return 1.0F;
        }

        String targetId = "atlas_sprite:" + atlasId + ":" + spriteId;
        float base = Math.max(stack.intensity(CorruptionSurface.TEXTURE_MEMORY), stack.intensity(CorruptionSurface.MODEL_UV) * 0.82F);
        float target = Math.max(
                stack.targetIntensity(CorruptionSurface.TEXTURE_MEMORY, targetId),
                stack.targetIntensity(CorruptionSurface.MODEL_UV, targetId) * 0.86F
        );
        float drift = 0.72F + stack.unit(CorruptionSurface.TEXTURE_MEMORY, targetId, ordinal ^ 0x5350) * 0.28F;
        return clampFloat(Math.max(base * 0.50F, target) * drift + stack.instability() * 0.06F, 0.0F, 1.0F);
    }

    private static void restoreGuiTextures(Minecraft minecraft) {
        restoreGuiTextures(minecraft, new HashSet<>(MUTATED_GUI_TEXTURES));
    }

    private static void restoreGuiTextures(Minecraft minecraft, Set<ResourceLocation> textureIdsToRestore) {
        ResourceManager resourceManager = minecraft.getResourceManager();
        List<ResourceLocation> textureIds = new ArrayList<>(textureIdsToRestore);
        for (ResourceLocation textureId : textureIds) {
            Optional<Resource> resource = resourceManager.getResource(textureId);
            resource.ifPresent(value -> replaceTexture(minecraft, textureId, value, CorruptionEffectStack.local(0), 0.0F, 0, false, List.of(), "GUI"));
            MUTATED_GUI_TEXTURES.remove(textureId);
        }
    }

    private static void restoreGlobalTextures(Minecraft minecraft) {
        restoreGlobalTextures(minecraft, new HashSet<>(MUTATED_GLOBAL_TEXTURES));
    }

    private static void restoreGlobalTextures(Minecraft minecraft, Set<ResourceLocation> textureIdsToRestore) {
        ResourceManager resourceManager = minecraft.getResourceManager();
        List<ResourceLocation> textureIds = new ArrayList<>(textureIdsToRestore);
        for (ResourceLocation textureId : textureIds) {
            Optional<Resource> resource = resourceManager.getResource(textureId);
            resource.ifPresent(value -> replaceTexture(minecraft, textureId, value, CorruptionEffectStack.local(0), 0.0F, 0, false, List.of(), "global"));
            MUTATED_GLOBAL_TEXTURES.remove(textureId);
        }
    }

    private static void restoreAtlasTextures() {
        for (TextureAtlas atlas : new ArrayList<>(KNOWN_ATLASES)) {
            if (atlas == null) {
                continue;
            }
            for (ResourceLocation spriteId : new ArrayList<>(atlas.getTextureLocations())) {
                restoreAtlasSprite(atlas, spriteId);
            }
        }
    }

    private static boolean restoreAtlasSprite(TextureAtlas atlas, ResourceLocation spriteId) {
        if (atlas == null || spriteId == null) {
            return false;
        }
        AtlasSpriteKey key = new AtlasSpriteKey(atlas.location(), spriteId);
        if (!MUTATED_ATLAS_TEXTURES.contains(key)) {
            return false;
        }
        TextureAtlasSprite sprite = atlas.getSprite(spriteId);
        if (sprite == null || sprite.contents() == null) {
            MUTATED_ATLAS_TEXTURES.remove(key);
            return false;
        }
        AtlasSpriteSnapshot snapshot = ATLAS_ORIGINALS.get(key);
        if (snapshot == null || !snapshot.restore(sprite.contents())) {
            MUTATED_ATLAS_TEXTURES.remove(key);
            return false;
        }
        try {
            sprite.contents().uploadFirstFrame(sprite.getX(), sprite.getY());
            MUTATED_ATLAS_TEXTURES.remove(key);
            return true;
        } catch (RuntimeException exception) {
            return false;
        }
    }

    private static boolean replaceTexture(Minecraft minecraft, ResourceLocation textureId, Resource resource, CorruptionEffectStack stack, float intensity, int ordinal, boolean mutate, List<ResourceLocation> donorTextureIds, String label) {
        NativeImage image = null;
        try (InputStream input = resource.open()) {
            image = NativeImage.read(NativeImage.Format.RGBA, input);
            if (!isMutablePixelImage(image)) {
                return false;
            }
            if (mutate) {
                mutateTexture(textureId, image, minecraft.getResourceManager(), donorTextureIds, stack, intensity, ordinal);
            }
            minecraft.getTextureManager().register(textureId, new DynamicTexture(image));
            image = null;
            return true;
        } catch (IOException | RuntimeException exception) {
            RealtimeMinecraftCorruptionSimulator.LOGGER.debug("Unable to {} {} texture {}", mutate ? "mutate" : "restore", label, textureId, exception);
            return false;
        } finally {
            if (image != null) {
                image.close();
            }
        }
    }

    private static void mutateTexture(ResourceLocation textureId, NativeImage image, ResourceManager resourceManager, List<ResourceLocation> donorTextureIds, CorruptionEffectStack stack, float intensity, int ordinal) {
        mutateTexturePixels(textureId, image, resourceManager, donorTextureIds, null, stack, CorruptionSurface.GUI_SURFACE, "gui_texture:", intensity, ordinal, 0);
    }

    private static void mutateTexturePixels(ResourceLocation textureId, NativeImage image, ResourceManager resourceManager, List<ResourceLocation> donorTextureIds, PixelBank donorPixels, CorruptionEffectStack stack, CorruptionSurface surface, String targetPrefix, float intensity, int ordinal, int salt) {
        if (!isMutablePixelImage(image)) {
            return;
        }
        int width = image.getWidth();
        int height = image.getHeight();
        float effectiveIntensity = textureSpecificIntensity(textureId, intensity);

        int totalPixels = width * height;
        int[] source = new int[totalPixels];
        int[] pixels = new int[totalPixels];
        for (int y = 0; y < height; y++) {
            int rowOffset = y * width;
            for (int x = 0; x < width; x++) {
                int color = image.getPixelRGBA(x, y);
                source[rowOffset + x] = color;
                pixels[rowOffset + x] = color;
            }
        }

        long seed = mixLong(stack.stableLong(surface, targetPrefix + textureId, 0x54585452 ^ salt)
                ^ ((long) width << 32)
                ^ height
                ^ ((long) salt << 48)
                ^ ordinal * 0x9E3779B97F4A7C15L);
        leakTextureMaps(source, donorPixels, pixels, width, height, seed, effectiveIntensity);
        if (surface == CorruptionSurface.GUI_SURFACE) {
            stretchTextureBands(source, pixels, width, height, seed, effectiveIntensity);
            shearTextureChannels(pixels, width, height, seed, effectiveIntensity);
        }

        for (int y = 0; y < height; y++) {
            int rowOffset = y * width;
            for (int x = 0; x < width; x++) {
                image.setPixelRGBA(x, y, pixels[rowOffset + x]);
            }
        }
    }

    private static boolean mutateAtlasSprite(TextureAtlas atlas, TextureAtlasSprite sprite, ResourceLocation spriteId, List<ResourceLocation> donorSpriteIds, CorruptionEffectStack stack, float intensity, int ordinal) {
        if (atlas == null || sprite == null || sprite.contents() == null) {
            return false;
        }

        AtlasSpriteKey key = new AtlasSpriteKey(atlas.location(), spriteId);
        SpriteContents contents = sprite.contents();
        boolean mutated = false;
        try {
            AtlasSpriteSnapshot snapshot = ATLAS_ORIGINALS.get(key);
            if (snapshot == null) {
                snapshot = AtlasSpriteSnapshot.capture(contents);
                if (snapshot == null) {
                    return false;
                }
                ATLAS_ORIGINALS.put(key, snapshot);
            }
            snapshot.restore(contents);

            NativeImage[] mipLevels = contents.byMipLevel;
            if (mipLevels == null || mipLevels.length == 0) {
                NativeImage image = contents.getOriginalImage();
                if (isMutablePixelImage(image)) {
                    PixelBank donor = atlasDonorPixels(atlas, spriteId, donorSpriteIds, 0, stack, ordinal, 0x4154);
                    mutateTexturePixels(spriteId, image, null, List.of(), donor, stack, CorruptionSurface.TEXTURE_MEMORY, "atlas_sprite:", intensity, ordinal, 0x4154);
                    mutated = true;
                }
            } else {
                for (int mip = 0; mip < mipLevels.length; mip++) {
                    NativeImage image = mipLevels[mip];
                    if (!isMutablePixelImage(image)) {
                        continue;
                    }
                    PixelBank donor = atlasDonorPixels(atlas, spriteId, donorSpriteIds, mip, stack, ordinal, 0x4154 ^ mip);
                    mutateTexturePixels(spriteId, image, null, List.of(), donor, stack, CorruptionSurface.TEXTURE_MEMORY, "atlas_sprite:mip" + mip + ":", intensity, ordinal, 0x4154 ^ mip);
                    mutated = true;
                }
            }

            if (mutated) {
                contents.uploadFirstFrame(sprite.getX(), sprite.getY());
                MUTATED_ATLAS_TEXTURES.add(key);
            }
            return mutated;
        } catch (RuntimeException exception) {
            RealtimeMinecraftCorruptionSimulator.LOGGER.debug("Unable to mutate atlas sprite {}", spriteId, exception);
            return false;
        }
    }

    private static PixelBank atlasDonorPixels(TextureAtlas atlas, ResourceLocation spriteId, List<ResourceLocation> donorSpriteIds, int mip, CorruptionEffectStack stack, int ordinal, int salt) {
        if (atlas == null || donorSpriteIds == null || donorSpriteIds.size() < 2) {
            return null;
        }
        int attempts = Math.min(12, donorSpriteIds.size());
        int start = CorruptionValueMutator.selectIndex(stack, CorruptionSurface.TEXTURE_MEMORY, "atlas_donor:" + spriteId + ":" + mip, ordinal ^ salt, donorSpriteIds.size());
        for (int attempt = 0; attempt < attempts; attempt++) {
            ResourceLocation donorId = donorSpriteIds.get(Math.floorMod(start + attempt * 17, donorSpriteIds.size()));
            if (donorId == null || donorId.equals(spriteId) || ClientCorruptionProtection.isProtectedResource(donorId)) {
                continue;
            }
            TextureAtlasSprite donorSprite = atlas.getSprite(donorId);
            if (donorSprite == null || donorSprite.contents() == null) {
                continue;
            }
            AtlasSpriteKey donorKey = new AtlasSpriteKey(atlas.location(), donorId);
            AtlasSpriteSnapshot donorSnapshot = ATLAS_ORIGINALS.get(donorKey);
            if (donorSnapshot == null) {
                donorSnapshot = AtlasSpriteSnapshot.capture(donorSprite.contents());
                if (donorSnapshot != null) {
                    ATLAS_ORIGINALS.put(donorKey, donorSnapshot);
                }
            }
            PixelBank bank = donorSnapshot == null ? null : donorSnapshot.pixelBank(mip);
            if (bank != null) {
                return bank;
            }
        }
        return null;
    }

    private static boolean isGlobalTextureResource(ResourceLocation location) {
        if (location == null || ClientCorruptionProtection.isProtectedResource(location)) {
            return false;
        }
        String path = location.getPath();
        if (!path.startsWith(ALL_TEXTURE_PREFIX + "/") || !path.endsWith(".png")) {
            return false;
        }
        if (path.startsWith(GUI_TEXTURE_PREFIX + "/") || path.startsWith("textures/atlas/")) {
            return false;
        }
        return !isAtlasBackedTexturePath(path);
    }

    private static boolean isAtlasBackedTexturePath(String path) {
        return path.startsWith("textures/block/")
                || path.startsWith("textures/item/")
                || path.startsWith("textures/particle/");
    }

    private static int compareGlobalTexturePriority(ResourceLocation first, ResourceLocation second) {
        int priority = Integer.compare(globalTexturePriority(first), globalTexturePriority(second));
        return priority != 0 ? priority : first.toString().compareTo(second.toString());
    }

    private static int compareAtlasSpritePriority(ResourceLocation first, ResourceLocation second) {
        int priority = Integer.compare(atlasSpritePriority(first), atlasSpritePriority(second));
        return priority != 0 ? priority : first.toString().compareTo(second.toString());
    }

    private static int atlasSpritePriority(ResourceLocation id) {
        String path = id.getPath();
        if (path.contains("water") || path.contains("lava")) {
            return 0;
        }
        if (path.startsWith("block/")) {
            return 1;
        }
        if (path.startsWith("item/")) {
            return 2;
        }
        if (path.startsWith("entity/")) {
            return 3;
        }
        return 4;
    }

    private static int globalTexturePriority(ResourceLocation id) {
        String path = id.getPath();
        if (path.startsWith("textures/environment/")) {
            return 0;
        }
        if (path.startsWith("textures/misc/") || path.startsWith("textures/effect/")) {
            return 1;
        }
        if (path.startsWith("textures/font/")) {
            return 2;
        }
        if (path.startsWith("textures/entity/") || path.startsWith("textures/models/") || path.startsWith("textures/painting/")) {
            return 3;
        }
        return 4;
    }

    private static float textureSpecificIntensity(ResourceLocation textureId, float intensity) {
        if (textureId == null) {
            return intensity;
        }
        String path = textureId.getPath();
        if (path.contains("clouds") || path.contains("water") || path.contains("lava")) {
            return clampFloat(intensity * 1.65F + 0.08F, 0.0F, 1.0F);
        }
        if (path.startsWith("textures/block/") || path.startsWith("textures/item/") || path.startsWith("textures/entity/")
                || path.startsWith("block/") || path.startsWith("item/") || path.startsWith("entity/")) {
            return clampFloat(intensity * 1.28F + 0.03F, 0.0F, 1.0F);
        }
        return clampFloat(intensity, 0.0F, 1.0F);
    }

    private static boolean isMutablePixelImage(NativeImage image) {
        if (image == null || image.format() != NativeImage.Format.RGBA) {
            return false;
        }
        int width = image.getWidth();
        int height = image.getHeight();
        return width > 0
                && height > 0
                && (long) width * (long) height <= MAX_TEXTURE_PIXELS_TO_MUTATE;
    }

    private static void stretchTextureBands(int[] source, int[] pixels, int width, int height, long seed, float intensity) {
        int operationCount = Math.min(96, 2 + Math.round((width + height) * intensity * 0.12F) + Math.round(intensity * 28.0F));
        int maxBandWidth = Math.max(1, Math.round(width * (0.025F + intensity * 0.32F)));
        int maxBandHeight = Math.max(1, Math.round(height * (0.025F + intensity * 0.30F)));

        for (int operation = 0; operation < operationCount; operation++) {
            long hash = mixLong(seed ^ operation * 0xD6E8FEB86659FD93L);
            if (unitHash(hash) > Math.min(0.92F, 0.12F + intensity * 0.76F)) {
                continue;
            }
            boolean horizontal = (hash & 1L) == 0L;
            if (horizontal) {
                int bandHeight = 1 + Math.floorMod((int) (hash >>> 5), maxBandHeight);
                int srcY = Math.floorMod((int) (hash >>> 17), height);
                int dstY = Math.floorMod((int) (hash >>> 29), height);
                float scale = 0.35F + unitHash(hash >>> 41) * (1.40F + intensity * 3.20F);
                int shift = signedInt(hash >>> 53, Math.max(1, Math.round(width * intensity * 0.42F)));
                for (int y = 0; y < bandHeight; y++) {
                    int dy = Math.min(height - 1, dstY + y);
                    int sy = clampInt(srcY + Math.round(y / scale), 0, height - 1);
                    for (int x = 0; x < width; x++) {
                        int sx = Math.floorMod(x + shift + Math.round((y - bandHeight * 0.5F) * intensity), width);
                        pixels[dy * width + x] = source[sy * width + sx];
                    }
                }
            } else {
                int bandWidth = 1 + Math.floorMod((int) (hash >>> 5), maxBandWidth);
                int srcX = Math.floorMod((int) (hash >>> 17), width);
                int dstX = Math.floorMod((int) (hash >>> 29), width);
                float scale = 0.35F + unitHash(hash >>> 41) * (1.30F + intensity * 2.80F);
                int shift = signedInt(hash >>> 53, Math.max(1, Math.round(height * intensity * 0.36F)));
                for (int x = 0; x < bandWidth; x++) {
                    int dx = Math.min(width - 1, dstX + x);
                    int sx = clampInt(srcX + Math.round(x / scale), 0, width - 1);
                    for (int y = 0; y < height; y++) {
                        int sy = clampInt(y + shift + Math.round((x - bandWidth * 0.5F) * intensity), 0, height - 1);
                        pixels[y * width + dx] = source[sy * width + sx];
                    }
                }
            }
        }
    }

    private static void leakTextureMaps(int[] source, PixelBank donor, int[] pixels, int width, int height, long seed, float intensity) {
        int operationCount = Math.min(160, 1 + Math.round((width + height) * intensity * 0.16F) + Math.round(intensity * 42.0F));
        int maxPatchWidth = Math.max(1, Math.round(width * (0.08F + intensity * 0.70F)));
        int maxPatchHeight = Math.max(1, Math.round(height * (0.08F + intensity * 0.66F)));
        int[] donorSource = donor == null ? source : donor.pixels();
        int donorWidth = donor == null ? width : donor.width();
        int donorHeight = donor == null ? height : donor.height();

        for (int operation = 0; operation < operationCount; operation++) {
            long hash = mixLong(seed ^ operation * 0xA24BAED4963EE407L ^ 0x4C45414B544558L);
            if (unitHash(hash) > Math.min(0.98F, 0.10F + intensity * 0.86F)) {
                continue;
            }

            int dstX = Math.floorMod((int) (hash >>> 7), width);
            int dstY = Math.floorMod((int) (hash >>> 15), height);
            int srcX = Math.floorMod((int) (hash >>> 23), donorWidth);
            int srcY = Math.floorMod((int) (hash >>> 31), donorHeight);
            int patchWidth = 1 + Math.floorMod((int) (hash >>> 39), maxPatchWidth);
            int patchHeight = 1 + Math.floorMod((int) (hash >>> 47), maxPatchHeight);
            float stretchX = 0.18F + unitHash(hash ^ 0x53545258L) * (1.25F + intensity * 5.20F);
            float stretchY = 0.18F + unitHash(hash ^ 0x53545259L) * (1.10F + intensity * 4.80F);
            int shearX = signedInt(hash ^ 0x53484558L, Math.max(1, Math.round(width * intensity * 0.32F)));
            int shearY = signedInt(hash ^ 0x53484559L, Math.max(1, Math.round(height * intensity * 0.28F)));

            for (int py = 0; py < patchHeight; py++) {
                int dy = Math.floorMod(dstY + py, height);
                int rowShear = Math.round((py - patchHeight * 0.5F) * intensity * 0.85F);
                for (int px = 0; px < patchWidth; px++) {
                    int dx = Math.floorMod(dstX + px, width);
                    int sx = Math.floorMod(srcX + Math.round(px / stretchX) + rowShear + shearX, donorWidth);
                    int sy = Math.floorMod(srcY + Math.round(py / stretchY) + Math.round(px * intensity * 0.22F) + shearY, donorHeight);
                    pixels[dy * width + dx] = donorSource[sy * donorWidth + sx];
                }
            }
        }
    }

    private static void shearTextureChannels(int[] pixels, int width, int height, long seed, float intensity) {
        int[] warped = pixels.clone();
        int maxX = Math.max(1, Math.round(width * (0.010F + intensity * 0.10F)));
        int maxY = Math.max(1, Math.round(height * (0.010F + intensity * 0.08F)));
        int redX = signedInt(seed >>> 7, maxX);
        int greenY = signedInt(seed >>> 19, maxY);
        int blueX = signedInt(seed >>> 31, maxX);
        int blueY = signedInt(seed >>> 43, maxY);
        float blend = Math.min(0.58F, 0.10F + intensity * 0.36F);

        for (int y = 0; y < height; y++) {
            for (int x = 0; x < width; x++) {
                int index = y * width + x;
                int original = warped[index];
                int redSample = warped[y * width + Math.floorMod(x + redX, width)];
                int greenSample = warped[clampInt(y + greenY, 0, height - 1) * width + x];
                int blueSample = warped[clampInt(y + blueY, 0, height - 1) * width + Math.floorMod(x + blueX, width)];
                int alpha = FastColor.ABGR32.alpha(original);
                int red = blendByte(FastColor.ABGR32.red(original), FastColor.ABGR32.red(redSample), blend);
                int green = blendByte(FastColor.ABGR32.green(original), FastColor.ABGR32.green(greenSample), blend);
                int blue = blendByte(FastColor.ABGR32.blue(original), FastColor.ABGR32.blue(blueSample), blend);
                pixels[index] = FastColor.ABGR32.color(alpha, blue, green, red);
            }
        }
    }

    private static int blendByte(int from, int to, float amount) {
        return clampByte(Math.round(from + (to - from) * clampFloat(amount, 0.0F, 1.0F)));
    }

    private static int signedInt(long value, int amplitude) {
        int span = Math.max(1, amplitude);
        return Math.floorMod((int) mixLong(value), span * 2 + 1) - span;
    }

    private static int clampByte(int value) {
        return clampInt(value, 0, 255);
    }

    private static int clampInt(int value, int min, int max) {
        return Math.max(min, Math.min(max, value));
    }

    private static float clampFloat(float value, float min, float max) {
        return Math.max(min, Math.min(max, value));
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

    private record AtlasSpriteKey(ResourceLocation atlasId, ResourceLocation spriteId) {
    }

    private record PixelBank(int[] pixels, int width, int height) {
        private PixelBank {
            if (pixels == null || width <= 0 || height <= 0 || (long) width * (long) height > MAX_TEXTURE_PIXELS_TO_MUTATE || pixels.length < width * height) {
                throw new IllegalArgumentException("invalid pixel bank");
            }
        }
    }

    private static final class AtlasSpriteSnapshot {
        private final int[] widths;
        private final int[] heights;
        private final int[][] pixels;

        private AtlasSpriteSnapshot(int[] widths, int[] heights, int[][] pixels) {
            this.widths = widths;
            this.heights = heights;
            this.pixels = pixels;
        }

        private static AtlasSpriteSnapshot capture(SpriteContents contents) {
            if (contents == null) {
                return null;
            }
            NativeImage[] images = contents.byMipLevel;
            if (images == null || images.length == 0) {
                NativeImage original = contents.getOriginalImage();
                if (original == null) {
                    return null;
                }
                images = new NativeImage[]{original};
            }

            int[] widths = new int[images.length];
            int[] heights = new int[images.length];
            int[][] pixels = new int[images.length][];
            for (int index = 0; index < images.length; index++) {
                NativeImage image = images[index];
                if (!isMutablePixelImage(image)) {
                    widths[index] = 0;
                    heights[index] = 0;
                    pixels[index] = new int[0];
                    continue;
                }
                widths[index] = image.getWidth();
                heights[index] = image.getHeight();
                pixels[index] = new int[widths[index] * heights[index]];
                for (int y = 0; y < heights[index]; y++) {
                    int rowOffset = y * widths[index];
                    for (int x = 0; x < widths[index]; x++) {
                        pixels[index][rowOffset + x] = image.getPixelRGBA(x, y);
                    }
                }
            }
            return new AtlasSpriteSnapshot(widths, heights, pixels);
        }

        private boolean restore(SpriteContents contents) {
            if (contents == null) {
                return false;
            }
            NativeImage[] images = contents.byMipLevel;
            if (images == null || images.length == 0) {
                NativeImage original = contents.getOriginalImage();
                if (original == null) {
                    return false;
                }
                images = new NativeImage[]{original};
            }

            boolean restored = false;
            int count = Math.min(images.length, pixels.length);
            for (int index = 0; index < count; index++) {
                NativeImage image = images[index];
                if (!isMutablePixelImage(image) || image.getWidth() != widths[index] || image.getHeight() != heights[index]) {
                    continue;
                }
                for (int y = 0; y < heights[index]; y++) {
                    int rowOffset = y * widths[index];
                    for (int x = 0; x < widths[index]; x++) {
                        image.setPixelRGBA(x, y, pixels[index][rowOffset + x]);
                    }
                }
                restored = true;
            }
            return restored;
        }

        private PixelBank pixelBank(int mip) {
            if (mip < 0 || mip >= pixels.length || widths[mip] <= 0 || heights[mip] <= 0 || pixels[mip].length < widths[mip] * heights[mip]) {
                return null;
            }
            return new PixelBank(pixels[mip], widths[mip], heights[mip]);
        }
    }

    private static final class PendingGuiTextureScan {
        private final String signature;
        private final CorruptionEffectStack stack;
        private final Map<ResourceLocation, Resource> resources;
        private final List<ResourceLocation> textureIds;
        private final List<ResourceLocation> donorTextureIds;
        private final Set<ResourceLocation> staleTextureIds;
        private int ordinal;
        private int mutatedCount;

        private PendingGuiTextureScan(String signature, CorruptionEffectStack stack, Map<ResourceLocation, Resource> resources, List<ResourceLocation> textureIds, List<ResourceLocation> donorTextureIds, Set<ResourceLocation> staleTextureIds) {
            this.signature = signature;
            this.stack = stack;
            this.resources = resources;
            this.textureIds = textureIds;
            this.donorTextureIds = donorTextureIds;
            this.staleTextureIds = staleTextureIds;
        }
    }

    private static final class PendingGlobalTextureScan {
        private final String signature;
        private final CorruptionEffectStack stack;
        private final Map<ResourceLocation, Resource> resources;
        private final List<ResourceLocation> textureIds;
        private final List<ResourceLocation> donorTextureIds;
        private final Set<ResourceLocation> staleTextureIds;
        private int ordinal;
        private int mutatedCount;

        private PendingGlobalTextureScan(String signature, CorruptionEffectStack stack, Map<ResourceLocation, Resource> resources, List<ResourceLocation> textureIds, List<ResourceLocation> donorTextureIds, Set<ResourceLocation> staleTextureIds) {
            this.signature = signature;
            this.stack = stack;
            this.resources = resources;
            this.textureIds = textureIds;
            this.donorTextureIds = donorTextureIds;
            this.staleTextureIds = staleTextureIds;
        }
    }

    private static final class PendingAtlasTextureScan {
        private final String signature;
        private final CorruptionEffectStack stack;
        private final List<TextureAtlas> atlases;
        private List<ResourceLocation> spriteIds = List.of();
        private int atlasOrdinal;
        private int spriteOrdinal;
        private int atlasSpriteOrdinal;
        private int mutatedCount;

        private PendingAtlasTextureScan(String signature, CorruptionEffectStack stack, List<TextureAtlas> atlases) {
            this.signature = signature;
            this.stack = stack;
            this.atlases = atlases;
        }
    }
}
