package com.maxsters.realtimeminecraftcorruptionsimulator.client.hooks.gui;

import com.maxsters.realtimeminecraftcorruptionsimulator.client.ClientCorruptionProtection;
import com.maxsters.realtimeminecraftcorruptionsimulator.client.effects.ClientCorruptionEffects;
import com.maxsters.realtimeminecraftcorruptionsimulator.client.effects.TextureMutationManager;
import com.maxsters.realtimeminecraftcorruptionsimulator.profile.CorruptionEffectStack;
import com.maxsters.realtimeminecraftcorruptionsimulator.profile.CorruptionSurface;
import com.maxsters.realtimeminecraftcorruptionsimulator.profile.CorruptionValueMutator;
import net.minecraft.client.Minecraft;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.packs.resources.ResourceManager;
import net.minecraft.util.Mth;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.api.distmarker.OnlyIn;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

@OnlyIn(Dist.CLIENT)
public final class GuiDirectTextureCorruptionHooks {
    private static final String GUI_TEXTURE_PREFIX = "textures/gui";
    private static final Set<String> VANILLA_GUI_TEXTURE_PATHS = Set.of(
            "textures/gui/accessibility.png",
            "textures/gui/advancements/backgrounds/adventure.png",
            "textures/gui/advancements/backgrounds/end.png",
            "textures/gui/advancements/backgrounds/husbandry.png",
            "textures/gui/advancements/backgrounds/nether.png",
            "textures/gui/advancements/backgrounds/stone.png",
            "textures/gui/advancements/tabs.png",
            "textures/gui/advancements/widgets.png",
            "textures/gui/advancements/window.png",
            "textures/gui/bars.png",
            "textures/gui/book.png",
            "textures/gui/chat_tags.png",
            "textures/gui/checkbox.png",
            "textures/gui/checkmark.png",
            "textures/gui/container/anvil.png",
            "textures/gui/container/beacon.png",
            "textures/gui/container/blast_furnace.png",
            "textures/gui/container/brewing_stand.png",
            "textures/gui/container/bundle.png",
            "textures/gui/container/cartography_table.png",
            "textures/gui/container/crafting_table.png",
            "textures/gui/container/creative_inventory/tab_inventory.png",
            "textures/gui/container/creative_inventory/tab_item_search.png",
            "textures/gui/container/creative_inventory/tab_items.png",
            "textures/gui/container/creative_inventory/tabs.png",
            "textures/gui/container/dispenser.png",
            "textures/gui/container/enchanting_table.png",
            "textures/gui/container/furnace.png",
            "textures/gui/container/gamemode_switcher.png",
            "textures/gui/container/generic_54.png",
            "textures/gui/container/grindstone.png",
            "textures/gui/container/hopper.png",
            "textures/gui/container/horse.png",
            "textures/gui/container/inventory.png",
            "textures/gui/container/legacy_smithing.png",
            "textures/gui/container/loom.png",
            "textures/gui/container/shulker_box.png",
            "textures/gui/container/smithing.png",
            "textures/gui/container/smoker.png",
            "textures/gui/container/stats_icons.png",
            "textures/gui/container/stonecutter.png",
            "textures/gui/container/villager2.png",
            "textures/gui/demo_background.png",
            "textures/gui/footer_separator.png",
            "textures/gui/hanging_signs/acacia.png",
            "textures/gui/hanging_signs/bamboo.png",
            "textures/gui/hanging_signs/birch.png",
            "textures/gui/hanging_signs/cherry.png",
            "textures/gui/hanging_signs/crimson.png",
            "textures/gui/hanging_signs/dark_oak.png",
            "textures/gui/hanging_signs/jungle.png",
            "textures/gui/hanging_signs/mangrove.png",
            "textures/gui/hanging_signs/oak.png",
            "textures/gui/hanging_signs/spruce.png",
            "textures/gui/hanging_signs/warped.png",
            "textures/gui/header_separator.png",
            "textures/gui/icons.png",
            "textures/gui/info_icon.png",
            "textures/gui/light_dirt_background.png",
            "textures/gui/options_background.png",
            "textures/gui/presets/isles.png",
            "textures/gui/recipe_book.png",
            "textures/gui/recipe_button.png",
            "textures/gui/report_button.png",
            "textures/gui/resource_packs.png",
            "textures/gui/server_selection.png",
            "textures/gui/slider.png",
            "textures/gui/social_interactions.png",
            "textures/gui/spectator_widgets.png",
            "textures/gui/stream_indicator.png",
            "textures/gui/tab_button.png",
            "textures/gui/title/background/panorama_0.png",
            "textures/gui/title/background/panorama_1.png",
            "textures/gui/title/background/panorama_2.png",
            "textures/gui/title/background/panorama_3.png",
            "textures/gui/title/background/panorama_4.png",
            "textures/gui/title/background/panorama_5.png",
            "textures/gui/title/background/panorama_overlay.png",
            "textures/gui/title/edition.png",
            "textures/gui/title/minceraft.png",
            "textures/gui/title/minecraft.png",
            "textures/gui/title/mojangstudios.png",
            "textures/gui/toasts.png",
            "textures/gui/unseen_notification.png",
            "textures/gui/widgets.png",
            "textures/gui/world_selection.png"
    );
    private static final List<ResourceLocation> TEXTURE_POOL = new ArrayList<>();
    private static final Set<ResourceLocation> TEXTURE_POOL_IDS = ConcurrentHashMap.newKeySet();
    private static final ThreadLocal<RawTextureState> RAW_TEXTURE = new ThreadLocal<>();
    private static ResourceManager cachedStableResourceManager;
    private static List<ResourceLocation> stableTexturePool = List.of();
    private static boolean stableTexturePoolLoaded;

    private GuiDirectTextureCorruptionHooks() {
    }

    public static DirectDraw corruptGuiBlit(ResourceLocation texture, int minX, int maxX, int minY, int maxY, int z, int sourceWidth, int sourceHeight, float sourceU, float sourceV, int textureWidth, int textureHeight) {
        if (texture == null || ClientCorruptionProtection.isProtectedResource(texture)) {
            return null;
        }
        TextureMutationManager.rememberGuiRenderedTexture(texture);
        remember(texture);

        CorruptionEffectStack stack = ClientCorruptionEffects.currentForGuiRendering();
        if (!stack.activeOrExtreme(CorruptionSurface.GUI_SURFACE)) {
            return null;
        }

        String targetId = targetId("gui_direct_texture", texture);
        float intensity = intensity(stack, targetId);
        if (intensity <= 0.025F) {
            return null;
        }

        int salt = texture.hashCode();
        salt = salt * 31 + sourceWidth;
        salt = salt * 31 + sourceHeight;
        salt = salt * 31 + Math.round(sourceU * 16.0F);
        salt = salt * 31 + Math.round(sourceV * 16.0F);
        salt = salt * 31 + Math.max(1, maxX - minX);
        salt = salt * 31 + Math.max(1, maxY - minY);
        float chance = stack.extreme(CorruptionSurface.GUI_SURFACE)
                ? 0.99F
                : Mth.clamp(0.12F + intensity * 0.78F + stack.instability() * 0.08F, 0.0F, 0.94F);
        if (!stack.extreme(CorruptionSurface.GUI_SURFACE)
                && stack.unit(CorruptionSurface.GUI_SURFACE, targetId, salt) > chance) {
            return null;
        }

        ResourceLocation replacement = replacementTexture(stack, texture, targetId, salt);
        RawTextureState state = state(stack, replacement, targetId, salt ^ 0x44495254, intensity, textureWidth, textureHeight);
        Uv uv0 = state.uv(sourceU, sourceV);
        Uv uv1 = state.uv(sourceU + Math.max(1, sourceWidth), sourceV + Math.max(1, sourceHeight));
        return new DirectDraw(replacement, uv0.u(), uv1.u(), uv0.v(), uv1.v());
    }

    public static ResourceLocation bindRawTexture(ResourceLocation texture, String context, int salt) {
        RAW_TEXTURE.remove();
        if (texture == null || ClientCorruptionProtection.isProtectedResource(texture)) {
            return texture;
        }
        remember(texture);

        CorruptionEffectStack stack = ClientCorruptionEffects.currentForGuiRendering();
        if (!stack.activeOrExtreme(CorruptionSurface.GUI_SURFACE)) {
            return texture;
        }

        String targetId = targetId(context, texture);
        float intensity = intensity(stack, targetId);
        if (intensity <= 0.025F) {
            return texture;
        }

        float chance = stack.extreme(CorruptionSurface.GUI_SURFACE)
                ? 0.99F
                : Mth.clamp(0.12F + intensity * 0.76F + stack.instability() * 0.08F, 0.0F, 0.94F);
        if (!stack.extreme(CorruptionSurface.GUI_SURFACE)
                && stack.unit(CorruptionSurface.GUI_SURFACE, targetId, salt) > chance) {
            return texture;
        }

        ResourceLocation replacement = replacementTexture(stack, texture, targetId, salt);
        RAW_TEXTURE.set(state(stack, replacement, targetId, salt ^ 0x524157, intensity, 1, 1));
        return replacement;
    }

    public static Uv rawUv(float u, float v) {
        RawTextureState state = RAW_TEXTURE.get();
        return state == null ? new Uv(u, v) : state.rawUv(u, v);
    }

    public static void clearRawTexture() {
        RAW_TEXTURE.remove();
    }

    private static void remember(ResourceLocation texture) {
        if (!isStableGuiTexture(texture)) {
            return;
        }
        if (TEXTURE_POOL_IDS.contains(texture)) {
            return;
        }
        synchronized (TEXTURE_POOL) {
            if (TEXTURE_POOL_IDS.add(texture)) {
                TEXTURE_POOL.add(insertionIndex(texture), texture);
            }
        }
    }

    private static ResourceLocation replacementTexture(CorruptionEffectStack stack, ResourceLocation original, String targetId, int salt) {
        List<ResourceLocation> pool = replacementPool();
        if (pool.size() < 2) {
            return original;
        }

        int index = CorruptionValueMutator.selectIndex(stack, CorruptionSurface.GUI_SURFACE, targetId, salt, pool.size());
        ResourceLocation replacement = pool.get(index);
        if (replacement.equals(original)) {
            int step = 1 + Math.floorMod((int) stack.stableLong(CorruptionSurface.GUI_SURFACE, targetId + ":donor_step", salt), pool.size() - 1);
            replacement = pool.get((index + step) % pool.size());
        }
        return replacement;
    }

    private static List<ResourceLocation> replacementPool() {
        List<ResourceLocation> stable = stableTexturePool();
        if (stable.size() >= 2) {
            return stable;
        }
        synchronized (TEXTURE_POOL) {
            return List.copyOf(TEXTURE_POOL);
        }
    }

    private static List<ResourceLocation> stableTexturePool() {
        Minecraft minecraft = Minecraft.getInstance();
        ResourceManager resourceManager = minecraft == null ? null : minecraft.getResourceManager();
        if (resourceManager == null) {
            return List.of();
        }

        synchronized (TEXTURE_POOL) {
            if (stableTexturePoolLoaded && resourceManager == cachedStableResourceManager) {
                return stableTexturePool;
            }
        }

        List<ResourceLocation> textureIds = new ArrayList<>(VANILLA_GUI_TEXTURE_PATHS.size());
        for (String path : VANILLA_GUI_TEXTURE_PATHS) {
            ResourceLocation textureId = ResourceLocation.fromNamespaceAndPath("minecraft", path);
            if (resourceManager.getResource(textureId).isPresent()) {
                textureIds.add(textureId);
            }
        }
        textureIds.sort(GuiDirectTextureCorruptionHooks::compareGuiTexturePriority);
        List<ResourceLocation> immutableTextureIds = List.copyOf(textureIds);

        synchronized (TEXTURE_POOL) {
            cachedStableResourceManager = resourceManager;
            stableTexturePool = immutableTextureIds;
            stableTexturePoolLoaded = true;
            return stableTexturePool;
        }
    }

    private static boolean isStableGuiTexture(ResourceLocation texture) {
        if (texture == null || ClientCorruptionProtection.isProtectedResource(texture)) {
            return false;
        }
        String path = texture.getPath();
        return "minecraft".equals(texture.getNamespace())
                && path != null
                && path.startsWith(GUI_TEXTURE_PREFIX + "/")
                && VANILLA_GUI_TEXTURE_PATHS.contains(path);
    }

    private static int compareGuiTexturePriority(ResourceLocation first, ResourceLocation second) {
        int priority = Integer.compare(guiTexturePriority(first), guiTexturePriority(second));
        return priority != 0 ? priority : first.toString().compareTo(second.toString());
    }

    private static int guiTexturePriority(ResourceLocation texture) {
        String path = texture == null ? "" : texture.getPath();
        if (path.startsWith("textures/gui/title/")) {
            return 0;
        }
        if (path.startsWith("textures/gui/container/")) {
            return 1;
        }
        if (path.contains("widgets") || path.contains("icons")) {
            return 2;
        }
        return 3;
    }

    private static RawTextureState state(CorruptionEffectStack stack, ResourceLocation texture, String targetId, int salt, float intensity, int textureWidth, int textureHeight) {
        long seed = stack.stableLong(CorruptionSurface.GUI_SURFACE, targetId, salt);
        boolean extreme = stack.extreme(CorruptionSurface.GUI_SURFACE);
        float uScale = extreme
                ? 0.10F + unit(seed ^ 0x5553434CL) * 8.5F
                : 0.45F + unit(seed ^ 0x5553434CL) * (1.15F + intensity * 4.8F);
        float vScale = extreme
                ? 0.10F + unit(seed ^ 0x5653434CL) * 8.5F
                : 0.45F + unit(seed ^ 0x5653434CL) * (1.15F + intensity * 4.8F);
        float leakU = extreme ? 5.5F : 0.08F + intensity * 2.8F;
        float leakV = extreme ? 5.5F : 0.08F + intensity * 2.8F;
        float uOffset = signed(seed ^ 0x554F4646L, leakU);
        float vOffset = signed(seed ^ 0x564F4646L, leakV);
        boolean flipU = unit(seed ^ 0x464C4955L) < 0.18F + intensity * 0.36F;
        boolean flipV = unit(seed ^ 0x464C4956L) < 0.18F + intensity * 0.36F;
        int safeWidth = Math.max(1, textureWidth);
        int safeHeight = Math.max(1, textureHeight);
        return new RawTextureState(texture, uOffset, vOffset, uScale, vScale, flipU, flipV, safeWidth, safeHeight);
    }

    private static float intensity(CorruptionEffectStack stack, String targetId) {
        return stack.extreme(CorruptionSurface.GUI_SURFACE)
                ? 1.0F
                : Mth.clamp(Math.max(
                        stack.targetIntensity(CorruptionSurface.GUI_SURFACE, targetId),
                        stack.intensity(CorruptionSurface.GUI_SURFACE) * 0.84F
                ) + stack.instability() * 0.08F, 0.0F, 1.0F);
    }

    private static String targetId(String context, ResourceLocation texture) {
        Minecraft minecraft = Minecraft.getInstance();
        String screen = minecraft == null || minecraft.screen == null ? "no_screen" : minecraft.screen.getClass().getName();
        return context + ":" + screen + ":" + texture;
    }

    private static int insertionIndex(ResourceLocation id) {
        String key = id.toString();
        int low = 0;
        int high = TEXTURE_POOL.size();
        while (low < high) {
            int mid = (low + high) >>> 1;
            String existingKey = TEXTURE_POOL.get(mid).toString();
            if (existingKey.compareTo(key) < 0) {
                low = mid + 1;
            } else {
                high = mid;
            }
        }
        return low;
    }

    private static float signed(long seed, float amplitude) {
        return (unit(seed) * 2.0F - 1.0F) * amplitude;
    }

    private static float unit(long value) {
        value ^= value >>> 33;
        value *= 0xff51afd7ed558ccdL;
        value ^= value >>> 33;
        value *= 0xc4ceb9fe1a85ec53L;
        value ^= value >>> 33;
        return ((value >>> 40) & 0xFF_FFFFL) / 16_777_215.0F;
    }

    public record DirectDraw(ResourceLocation texture, float u0, float u1, float v0, float v1) {
    }

    public record Uv(float u, float v) {
    }

    private record RawTextureState(ResourceLocation texture, float uOffset, float vOffset, float uScale, float vScale, boolean flipU, boolean flipV, int textureWidth, int textureHeight) {
        private Uv uv(float sourceU, float sourceV) {
            float u = sourceU / (float) textureWidth;
            float v = sourceV / (float) textureHeight;
            return rawUv(u, v);
        }

        private Uv rawUv(float u, float v) {
            float localU = flipU ? 1.0F - u : u;
            float localV = flipV ? 1.0F - v : v;
            return new Uv(localU * uScale + uOffset, localV * vScale + vOffset);
        }
    }
}
