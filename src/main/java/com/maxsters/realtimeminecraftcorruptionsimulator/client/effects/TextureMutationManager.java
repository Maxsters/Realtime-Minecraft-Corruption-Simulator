package com.maxsters.realtimeminecraftcorruptionsimulator.client.effects;

import com.maxsters.realtimeminecraftcorruptionsimulator.RealtimeMinecraftCorruptionSimulator;
import com.maxsters.realtimeminecraftcorruptionsimulator.client.ClientCorruptionProtection;
import com.maxsters.realtimeminecraftcorruptionsimulator.client.hooks.gui.GuiAtlasSpriteCorruptionHooks;
import com.maxsters.realtimeminecraftcorruptionsimulator.client.hooks.render.DirectTextureCorruptionHooks;
import com.maxsters.realtimeminecraftcorruptionsimulator.profile.CorruptionEffectStack;
import com.maxsters.realtimeminecraftcorruptionsimulator.profile.CorruptionSurface;
import com.maxsters.realtimeminecraftcorruptionsimulator.state.CorruptionStateSnapshot;
import com.mojang.blaze3d.platform.NativeImage;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.texture.DynamicTexture;
import net.minecraft.client.renderer.texture.TextureAtlas;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.packs.resources.Resource;
import net.minecraft.server.packs.resources.ResourceManager;
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
    private static final int MAX_TEXTURE_PIXELS_TO_MUTATE = 4_194_304;
    private static final Set<ResourceLocation> MUTATED_GUI_TEXTURES = new HashSet<>();
    private static final Set<ResourceLocation> MUTATED_GLOBAL_TEXTURES = new HashSet<>();
    private static final Map<ResourceLocation, StoredTexturePixels> ORIGINAL_TEXTURE_PIXELS = new HashMap<>();
    private static PendingGuiTextureScan pendingGuiTextureScan;
    private static PendingGlobalTextureScan pendingGlobalTextureScan;
    private static boolean startupTextureScanRequested;
    private static boolean startupGlobalTextureScanRequested;
    private static ResourceManager cachedGuiResourceManager;
    private static GuiTextureInventory cachedGuiTextureInventory = GuiTextureInventory.empty();
    private static String appliedGuiTextureSignature = "";
    private static String appliedGlobalTextureSignature = "";

    private TextureMutationManager() {
    }

    public static void onTextureStitchPost(TextureStitchEvent.Post event) {
        TextureAtlas atlas = event.getAtlas();
        if (atlas == null) {
            return;
        }

        List<ResourceLocation> spriteIds = new ArrayList<>(atlas.getTextureLocations());
        spriteIds.sort(Comparator.comparing(ResourceLocation::toString));
        for (ResourceLocation spriteId : spriteIds) {
            var sprite = atlas.getSprite(spriteId);
            ItemTextureCorruptionManager.rememberAtlasSprite(sprite);
            GuiAtlasSpriteCorruptionHooks.rememberAtlasSprite(sprite);
        }
    }

    public static void requestGuiTextureScan() {
        startupTextureScanRequested = true;
    }

    public static void rememberGuiRenderedTexture(ResourceLocation texture) {
        if (texture == null
                || ClientCorruptionProtection.isProtectedResource(texture)
                || !TextureRenderOwnership.rememberGuiRendered(texture)) {
            return;
        }

        DirectTextureCorruptionHooks.forgetTexture(texture);
        if (!MUTATED_GLOBAL_TEXTURES.remove(texture)) {
            return;
        }

        PendingGlobalTextureScan scan = pendingGlobalTextureScan;
        if (scan != null) {
            scan.staleTextureIds.remove(texture);
        }
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft != null && minecraft.getTextureManager() != null && minecraft.getResourceManager() != null) {
            restoreTexture(minecraft, minecraft.getResourceManager(), texture, CorruptionSurface.TEXTURE_MEMORY, "texture_resource:", "global");
        }
    }

    public static void onSettingsChanged(CorruptionStateSnapshot previous, CorruptionStateSnapshot current) {
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

        refreshResourceInventoryCache(minecraft.getResourceManager());
        CorruptionEffectStack stack = ClientCorruptionEffects.currentUnsuppressed();
        if (ClientCorruptionProtection.shouldSuppressClientCorruption() && stack.level() > 0) {
            return;
        }
        handleGuiTextureMutations(minecraft, stack);
        handleGlobalTextureMutations(minecraft, stack);
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
        if (signature.equals(appliedGuiTextureSignature)) {
            startupTextureScanRequested = false;
            return;
        }
        if (!startupTextureScanRequested) {
            startupTextureScanRequested = true;
        }

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
        if (signature.equals(appliedGlobalTextureSignature)) {
            startupGlobalTextureScanRequested = false;
            return;
        }
        if (!startupGlobalTextureScanRequested) {
            startupGlobalTextureScanRequested = true;
        }

        applyGlobalTextureMutations(minecraft, stack, signature);
    }

    private static boolean shouldMutateGuiTextures(CorruptionEffectStack stack) {
        return stack.activeOrExtreme(CorruptionSurface.GUI_SURFACE);
    }

    private static boolean shouldMutateGlobalTextures(CorruptionEffectStack stack) {
        return stack.activeOrExtreme(CorruptionSurface.TEXTURE_MEMORY);
    }

    private static String textureSettingsSignature(CorruptionEffectStack stack) {
        return guiTextureSignature(stack) + "|" + globalTextureSignature(stack);
    }

    private static String guiTextureSignature(CorruptionEffectStack stack) {
        return stack.level()
                + ":"
                + stack.bucket(CorruptionSurface.GUI_SURFACE, 0x475549, 64)
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

    private static void applyGuiTextureMutations(Minecraft minecraft, CorruptionEffectStack stack, String signature) {
        ResourceManager resourceManager = minecraft.getResourceManager();
        GuiTextureInventory inventory = guiTextureInventory(resourceManager);
        List<ResourceLocation> donorTextureIds = List.of();

        pendingGuiTextureScan = new PendingGuiTextureScan(signature, stack, inventory.resources(), inventory.textureIds(), donorTextureIds, new HashSet<>(MUTATED_GUI_TEXTURES));
        processPendingGuiTextureMutations(minecraft);
    }

    private static void refreshResourceInventoryCache(ResourceManager resourceManager) {
        if (resourceManager == cachedGuiResourceManager) {
            return;
        }
        cachedGuiResourceManager = resourceManager;
        cachedGuiTextureInventory = GuiTextureInventory.empty();
        pendingGuiTextureScan = null;
        appliedGuiTextureSignature = "";
        pendingGlobalTextureScan = null;
        appliedGlobalTextureSignature = "";
        ORIGINAL_TEXTURE_PIXELS.clear();
        if (resourceManager != null) {
            startupTextureScanRequested = true;
            startupGlobalTextureScanRequested = true;
        }
    }

    private static GuiTextureInventory guiTextureInventory(ResourceManager resourceManager) {
        if (resourceManager == cachedGuiResourceManager && !cachedGuiTextureInventory.textureIds().isEmpty()) {
            return cachedGuiTextureInventory;
        }
        Map<ResourceLocation, Resource> resources = resourceManager.listResources(
                GUI_TEXTURE_PREFIX,
                location -> "minecraft".equals(location.getNamespace()) && location.getPath().endsWith(".png")
        );
        List<ResourceLocation> textureIds = new ArrayList<>(resources.keySet());
        textureIds.sort(Comparator.comparing(ResourceLocation::toString));
        cachedGuiResourceManager = resourceManager;
        cachedGuiTextureInventory = new GuiTextureInventory(resources, List.copyOf(textureIds));
        return cachedGuiTextureInventory;
    }

    private static void applyGlobalTextureMutations(Minecraft minecraft, CorruptionEffectStack stack, String signature) {
        ResourceManager resourceManager = minecraft.getResourceManager();
        Map<ResourceLocation, Resource> resources = resourceManager.listResources(
                ALL_TEXTURE_PREFIX,
                TextureMutationManager::isGlobalTextureResource
        );

        List<ResourceLocation> textureIds = new ArrayList<>(resources.keySet());
        textureIds.sort(TextureMutationManager::compareGlobalTexturePriority);
        DirectTextureCorruptionHooks.updateTexturePool(textureIds);

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
            if (replaceTexture(minecraft, textureId, resource, scan.stack, CorruptionSurface.GUI_SURFACE, "gui_texture:", intensity, scan.ordinal, true, scan.donorTextureIds, "GUI")) {
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
            if (TextureRenderOwnership.isGuiOwned(textureId)) {
                scan.ordinal++;
                processedThisTick++;
                continue;
            }
            float intensity = globalTextureIntensity(scan.stack, textureId, scan.ordinal);
            Resource resource = scan.resources.get(textureId);
            scan.ordinal++;
            processedThisTick++;
            if (resource == null || intensity <= 0.035F) {
                continue;
            }
            if (replaceTexture(minecraft, textureId, resource, scan.stack, CorruptionSurface.TEXTURE_MEMORY, "texture_resource:", intensity, scan.ordinal, true, scan.donorTextureIds, "global")) {
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

    private static float guiTextureIntensity(CorruptionEffectStack stack, ResourceLocation textureId, int ordinal) {
        if (stack.extreme(CorruptionSurface.GUI_SURFACE)) {
            return 1.0F;
        }

        String targetId = "gui_texture:" + textureId;
        float base = stack.intensity(CorruptionSurface.GUI_SURFACE);
        float target = stack.targetIntensity(CorruptionSurface.GUI_SURFACE, targetId);
        float drift = 0.72F + stack.unit(CorruptionSurface.GUI_SURFACE, targetId, ordinal ^ 0x5445) * 0.28F;
        return clampFloat(Math.max(base * 0.58F, target) * drift + stack.instability() * 0.05F, 0.0F, 1.0F);
    }

    private static float globalTextureIntensity(CorruptionEffectStack stack, ResourceLocation textureId, int ordinal) {
        if (stack.extreme(CorruptionSurface.TEXTURE_MEMORY)) {
            return 1.0F;
        }

        String targetId = "texture_resource:" + textureId;
        float base = stack.intensity(CorruptionSurface.TEXTURE_MEMORY);
        float target = stack.targetIntensity(CorruptionSurface.TEXTURE_MEMORY, targetId);
        float drift = 0.68F + stack.unit(CorruptionSurface.TEXTURE_MEMORY, targetId, ordinal ^ 0x414C) * 0.32F;
        return clampFloat(Math.max(base * 0.52F, target) * drift + stack.instability() * 0.06F, 0.0F, 1.0F);
    }

    private static void restoreGuiTextures(Minecraft minecraft) {
        restoreGuiTextures(minecraft, new HashSet<>(MUTATED_GUI_TEXTURES));
    }

    private static void restoreGuiTextures(Minecraft minecraft, Set<ResourceLocation> textureIdsToRestore) {
        ResourceManager resourceManager = minecraft.getResourceManager();
        List<ResourceLocation> textureIds = new ArrayList<>(textureIdsToRestore);
        textureIds.sort(Comparator.comparing(ResourceLocation::toString));
        for (ResourceLocation textureId : textureIds) {
            restoreTexture(minecraft, resourceManager, textureId, CorruptionSurface.GUI_SURFACE, "gui_texture:", "GUI");
            MUTATED_GUI_TEXTURES.remove(textureId);
        }
    }

    private static void restoreGlobalTextures(Minecraft minecraft) {
        restoreGlobalTextures(minecraft, new HashSet<>(MUTATED_GLOBAL_TEXTURES));
    }

    private static void restoreGlobalTextures(Minecraft minecraft, Set<ResourceLocation> textureIdsToRestore) {
        ResourceManager resourceManager = minecraft.getResourceManager();
        List<ResourceLocation> textureIds = new ArrayList<>(textureIdsToRestore);
        textureIds.sort(Comparator.comparing(ResourceLocation::toString));
        for (ResourceLocation textureId : textureIds) {
            restoreTexture(minecraft, resourceManager, textureId, CorruptionSurface.TEXTURE_MEMORY, "texture_resource:", "global");
            MUTATED_GLOBAL_TEXTURES.remove(textureId);
        }
    }

    private static boolean replaceTexture(Minecraft minecraft, ResourceLocation textureId, Resource resource, CorruptionEffectStack stack, CorruptionSurface surface, String targetPrefix, float intensity, int ordinal, boolean mutate, List<ResourceLocation> donorTextureIds, String label) {
        NativeImage image = null;
        try (InputStream input = resource.open()) {
            image = NativeImage.read(NativeImage.Format.RGBA, input);
            if (!isMutablePixelImage(image)) {
                return false;
            }
            if (mutate) {
                rememberOriginalTexture(textureId, image);
                mutateTexturePixels(textureId, image, minecraft.getResourceManager(), donorTextureIds, null, stack, surface, targetPrefix, intensity, ordinal, 0);
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

    private static void restoreTexture(Minecraft minecraft, ResourceManager resourceManager, ResourceLocation textureId, CorruptionSurface surface, String targetPrefix, String label) {
        if (restoreTextureFromCache(minecraft, textureId, label)) {
            return;
        }
        Optional<Resource> resource = resourceManager.getResource(textureId);
        resource.ifPresent(value -> replaceTexture(minecraft, textureId, value, CorruptionEffectStack.local(0), surface, targetPrefix, 0.0F, 0, false, List.of(), label));
    }

    private static boolean restoreTextureFromCache(Minecraft minecraft, ResourceLocation textureId, String label) {
        StoredTexturePixels cached = ORIGINAL_TEXTURE_PIXELS.get(textureId);
        if (cached == null) {
            return false;
        }

        NativeImage image = null;
        try {
            image = new NativeImage(NativeImage.Format.RGBA, cached.width(), cached.height(), false);
            int[] pixels = cached.pixels();
            for (int y = 0; y < cached.height(); y++) {
                int rowOffset = y * cached.width();
                for (int x = 0; x < cached.width(); x++) {
                    image.setPixelRGBA(x, y, pixels[rowOffset + x]);
                }
            }
            minecraft.getTextureManager().register(textureId, new DynamicTexture(image));
            image = null;
            return true;
        } catch (RuntimeException exception) {
            RealtimeMinecraftCorruptionSimulator.LOGGER.debug("Unable to restore cached {} texture {}", label, textureId, exception);
            return false;
        } finally {
            if (image != null) {
                image.close();
            }
        }
    }

    private static void rememberOriginalTexture(ResourceLocation textureId, NativeImage image) {
        if (ORIGINAL_TEXTURE_PIXELS.containsKey(textureId) || !isMutablePixelImage(image)) {
            return;
        }
        try {
            ORIGINAL_TEXTURE_PIXELS.put(textureId, new StoredTexturePixels(image.getWidth(), image.getHeight(), image.getPixelsRGBA()));
        } catch (RuntimeException ignored) {
        }
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
        }

        for (int y = 0; y < height; y++) {
            int rowOffset = y * width;
            for (int x = 0; x < width; x++) {
                image.setPixelRGBA(x, y, pixels[rowOffset + x]);
            }
        }
    }

    private static boolean isGlobalTextureResource(ResourceLocation location) {
        if (location == null || ClientCorruptionProtection.isProtectedResource(location)) {
            return false;
        }
        String path = location.getPath();
        if (!path.startsWith(ALL_TEXTURE_PREFIX + "/") || !path.endsWith(".png")) {
            return false;
        }
        if (TextureRenderOwnership.isGuiOwned(location) || path.startsWith("textures/font/") || path.startsWith("textures/atlas/")) {
            return false;
        }
        if (isDestroyStageTexturePath(path)) {
            return true;
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

    private static int globalTexturePriority(ResourceLocation id) {
        String path = id.getPath();
        if (isBlockEntityTexturePath(path)) {
            return 0;
        }
        if (isDestroyStageTexturePath(path)) {
            return 1;
        }
        if (path.startsWith("textures/environment/")) {
            return 2;
        }
        if (path.startsWith("textures/misc/") || path.startsWith("textures/effect/")) {
            return 3;
        }
        if (path.startsWith("textures/entity/") || path.startsWith("textures/models/") || path.startsWith("textures/painting/")) {
            return 4;
        }
        return 5;
    }

    private static boolean isDestroyStageTexturePath(String path) {
        return path != null
                && path.startsWith("textures/block/destroy_stage_")
                && path.endsWith(".png");
    }

    private static boolean isBlockEntityTexturePath(String path) {
        return path.startsWith("textures/entity/banner/")
                || path.startsWith("textures/entity/beacon_beam")
                || path.startsWith("textures/entity/bed/")
                || path.startsWith("textures/entity/bell/")
                || path.startsWith("textures/entity/chest/")
                || path.startsWith("textures/entity/conduit/")
                || path.startsWith("textures/entity/creeper/creeper")
                || path.startsWith("textures/entity/decorated_pot/")
                || path.startsWith("textures/entity/end_gateway_beam")
                || path.startsWith("textures/entity/end_portal")
                || path.startsWith("textures/entity/enderdragon/dragon")
                || path.startsWith("textures/entity/hanging_signs/")
                || path.startsWith("textures/entity/lectern_book")
                || path.startsWith("textures/entity/piglin/piglin")
                || path.startsWith("textures/entity/skeleton/skeleton")
                || path.startsWith("textures/entity/skeleton/wither_skeleton")
                || path.startsWith("textures/entity/shulker/")
                || path.startsWith("textures/entity/signs/")
                || path.startsWith("textures/entity/trapped_chest")
                || path.startsWith("textures/entity/zombie/zombie");
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

    private static int signedInt(long value, int amplitude) {
        int span = Math.max(1, amplitude);
        return Math.floorMod((int) mixLong(value), span * 2 + 1) - span;
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

    private record PixelBank(int[] pixels, int width, int height) {
        private PixelBank {
            if (pixels == null || width <= 0 || height <= 0 || (long) width * (long) height > MAX_TEXTURE_PIXELS_TO_MUTATE || pixels.length < width * height) {
                throw new IllegalArgumentException("invalid pixel bank");
            }
        }
    }

    private record StoredTexturePixels(int width, int height, int[] pixels) {
        private StoredTexturePixels {
            if (pixels == null || width <= 0 || height <= 0 || (long) width * (long) height > MAX_TEXTURE_PIXELS_TO_MUTATE || pixels.length < width * height) {
                throw new IllegalArgumentException("invalid texture cache");
            }
        }
    }

    private record GuiTextureInventory(Map<ResourceLocation, Resource> resources, List<ResourceLocation> textureIds) {
        private static GuiTextureInventory empty() {
            return new GuiTextureInventory(Map.of(), List.of());
        }
    }

}
