package com.maxsters.realtimeminecraftcorruptionsimulator.client.effects;

import com.maxsters.realtimeminecraftcorruptionsimulator.RealtimeMinecraftCorruptionSimulator;
import com.maxsters.realtimeminecraftcorruptionsimulator.client.hooks.LightingCorruptionHooks;
import com.maxsters.realtimeminecraftcorruptionsimulator.client.hooks.CameraRenderCorruptionHooks;
import com.maxsters.realtimeminecraftcorruptionsimulator.profile.CorruptionEffectStack;
import com.maxsters.realtimeminecraftcorruptionsimulator.profile.CorruptionSurface;
import com.maxsters.realtimeminecraftcorruptionsimulator.profile.CorruptionValueMutator;
import com.maxsters.realtimeminecraftcorruptionsimulator.state.CorruptionProfileSnapshot;
import com.mojang.blaze3d.shaders.FogShape;
import net.minecraft.client.Minecraft;
import net.minecraft.client.color.block.BlockColor;
import net.minecraft.client.color.block.BlockColors;
import net.minecraft.client.color.item.ItemColor;
import net.minecraft.client.color.item.ItemColors;
import net.minecraft.client.gui.screens.TitleScreen;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.core.Holder;
import net.minecraft.core.BlockPos;
import net.minecraft.core.SectionPos;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.Item;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.client.renderer.PanoramaRenderer;
import net.minecraftforge.client.event.ComputeFovModifierEvent;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.api.distmarker.OnlyIn;
import net.minecraftforge.client.event.RegisterColorHandlersEvent;
import net.minecraftforge.client.event.ScreenEvent;
import net.minecraftforge.client.event.ViewportEvent;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.util.ObfuscationReflectionHelper;
import net.minecraftforge.registries.ForgeRegistries;

import java.lang.reflect.Field;
import java.util.HashMap;
import java.util.Map;

@OnlyIn(Dist.CLIENT)
@Mod.EventBusSubscriber(modid = RealtimeMinecraftCorruptionSimulator.MOD_ID, value = Dist.CLIENT, bus = Mod.EventBusSubscriber.Bus.FORGE)
public final class VisualCorruptionManager {
    private static final int CORRUPT_SURFACE_COLOR = 0x1F3631;
    private static Field titlePanoramaField;
    private static Field blockColorsMapField;
    private static Field itemColorsMapField;
    private static boolean titlePanoramaFieldChecked;
    private static int pendingWorldRefreshDelay = -1;
    private static int pendingWorldRefreshPasses;
    private static boolean pendingWorldRefresh;
    private static boolean lightingCorruptionWasActive;

    private VisualCorruptionManager() {
    }

    public static void registerBlockColors(RegisterColorHandlersEvent.Block event) {
        Map<Holder.Reference<Block>, BlockColor> originalHandlers = snapshotBlockColorHandlers(event.getBlockColors());
        Block[] blocks = ForgeRegistries.BLOCKS.getValues().toArray(Block[]::new);
        event.register((state, level, pos, tintIndex) -> {
                    CorruptionEffectStack stack = ClientCorruptionEffects.currentForWorldRendering();
                    boolean active = stack.active(CorruptionSurface.BIOME_TINT);
                    int baseColor = originalBlockColor(originalHandlers, state, level, pos, tintIndex);
                    if (baseColor < 0) {
                        return active ? baseBlockMapColor(state, level, pos) : baseColor;
                    }
                    if (!active) {
                        return baseColor;
                    }
                    return corruptSurfaceColor(stack, blockTargetId(state), baseColor);
                },
                blocks
        );
    }

    public static void registerItemColors(RegisterColorHandlersEvent.Item event) {
        Map<Holder.Reference<Item>, ItemColor> originalHandlers = snapshotItemColorHandlers(event.getItemColors());
        Item[] items = ForgeRegistries.ITEMS.getValues().toArray(Item[]::new);
        event.register((itemStack, tintIndex) -> {
                    CorruptionEffectStack stack = ClientCorruptionEffects.current();
                    boolean active = stack.active(CorruptionSurface.BIOME_TINT);
                    int baseColor = originalItemColor(originalHandlers, itemStack, tintIndex);
                    if (baseColor < 0) {
                        return active ? baseItemMapColor(itemStack.getItem()) : baseColor;
                    }
                    return active ? corruptSurfaceColor(stack, itemTargetId(itemStack.getItem()), baseColor) : baseColor;
                },
                items
        );
    }

    public static void onSettingsChanged(CorruptionProfileSnapshot previous, CorruptionProfileSnapshot current) {
        CorruptionEffectStack previousStack = CorruptionEffectStack.from(previous);
        CorruptionEffectStack currentStack = CorruptionEffectStack.from(current);
        if (!chunkRenderRefreshSignature(previousStack).equals(chunkRenderRefreshSignature(currentStack))) {
            requestWorldRenderRefresh();
        }
        if (LightingCorruptionHooks.lightingCorruptionActive(previousStack)
                || LightingCorruptionHooks.lightingCorruptionActive(currentStack)) {
            requestLightTextureRefresh();
        }
        if (LightingCorruptionHooks.lightingCorruptionActive(previousStack)
                && !LightingCorruptionHooks.lightingCorruptionActive(currentStack)) {
            requestLightTextureReset();
        }
    }

    public static void requestWorldRenderRefresh() {
        pendingWorldRefreshDelay = pendingWorldRefreshDelay < 0 ? 2 : Math.min(pendingWorldRefreshDelay, 2);
        pendingWorldRefreshPasses = Math.max(pendingWorldRefreshPasses, 6);
        pendingWorldRefresh = true;
    }

    public static void requestLightTextureRefresh() {
        LightingCorruptionHooks.requestLightTextureRefresh();
    }

    public static void requestLightTextureReset() {
        LightingCorruptionHooks.requestLightTextureReset();
    }

    @SubscribeEvent
    public static void onComputeCameraAngles(ViewportEvent.ComputeCameraAngles event) {
        CorruptionEffectStack stack = ClientCorruptionEffects.current();
        if (!stack.active(CorruptionSurface.CAMERA_TRANSFORM)) {
            return;
        }

        Minecraft minecraft = Minecraft.getInstance();
        if (!CameraRenderCorruptionHooks.cameraReady(minecraft)) {
            return;
        }
        LocalPlayer player = minecraft.player;
        if (player == null) {
            return;
        }

        String targetId = cameraTargetId(player, minecraft, "angles");
        float intensity = stack.intensityOrExtreme(CorruptionSurface.CAMERA_TRANSFORM);
        long clock = staticClock(stack, CorruptionSurface.CAMERA_TRANSFORM, targetId, 0x43414D);
        event.setYaw(CorruptionValueMutator.mutateScalar(stack, CorruptionSurface.CAMERA_TRANSFORM, targetId + ":yaw", event.getYaw(), 10.0F + intensity * 38.0F, -180.0F, 180.0F, 1, clock));
        event.setPitch(CorruptionValueMutator.mutateScalar(stack, CorruptionSurface.CAMERA_TRANSFORM, targetId + ":pitch", event.getPitch(), 9.0F + intensity * 26.0F, -90.0F, 90.0F, 2, clock));
        event.setRoll(CorruptionValueMutator.mutateScalar(stack, CorruptionSurface.CAMERA_TRANSFORM, targetId + ":roll", event.getRoll(), 14.0F + intensity * 70.0F, -110.0F, 110.0F, 3, clock));

    }

    @SubscribeEvent
    public static void onComputeFov(ViewportEvent.ComputeFov event) {
        CorruptionEffectStack stack = ClientCorruptionEffects.current();
        if (!stack.activeOrExtreme(CorruptionSurface.CAMERA_TRANSFORM)) {
            return;
        }

        Minecraft minecraft = Minecraft.getInstance();
        if (!CameraRenderCorruptionHooks.cameraReady(minecraft)) {
            return;
        }
        LocalPlayer player = minecraft.player;
        String targetId = cameraTargetId(player, minecraft, "raw_fov") + ":" + event.usedConfiguredFov();
        float intensity = stack.intensityOrExtreme(CorruptionSurface.CAMERA_TRANSFORM);
        long clock = staticClock(stack, CorruptionSurface.CAMERA_TRANSFORM, targetId, 0x464F56);
        double baseFov = event.getFOV();
        double mutated = CorruptionValueMutator.mutateScalar(
                stack,
                CorruptionSurface.CAMERA_TRANSFORM,
                targetId,
                baseFov,
                18.0D + intensity * 96.0D,
                12.0D,
                165.0D,
                0x46,
                clock
        );
        double motionSignal = cameraMotionSignal(player);
        double featureScale = 1.0D + signed(stack, CorruptionSurface.CAMERA_TRANSFORM, targetId, 0x5343414C, 0.10D + intensity * 1.85D);
        double bobLeak = motionSignal * signed(stack, CorruptionSurface.CAMERA_TRANSFORM, targetId, 0x424F4246, 3.0D + intensity * 74.0D);
        mutated = mutated * featureScale + bobLeak;
        if (unit(stack, CorruptionSurface.CAMERA_TRANSFORM, targetId, 0x4A) < 0.18D + intensity * 0.28D) {
            double scale = 0.04D + unit(stack, CorruptionSurface.CAMERA_TRANSFORM, targetId, 0x4B) * (3.05D + intensity * 2.20D);
            mutated = baseFov * scale + signed(stack, CorruptionSurface.CAMERA_TRANSFORM, targetId, 0x4C, 28.0D + intensity * 86.0D) + bobLeak;
        }
        if (player != null && (player.isSwimming() || player.isUnderWater())) {
            mutated += signed(stack, CorruptionSurface.CAMERA_TRANSFORM, targetId, 0x5754, 8.0D + intensity * 32.0D);
        }
        if (stack.level() >= 72 && unit(stack, CorruptionSurface.CAMERA_TRANSFORM, targetId, 0x3244464F) < intensity * 0.34D) {
            mutated = unit(stack, CorruptionSurface.CAMERA_TRANSFORM, targetId, 0x464C4154) < 0.62D
                    ? 150.0D + unit(stack, CorruptionSurface.CAMERA_TRANSFORM, targetId, 0x57494445) * 15.0D
                    : 12.0D + unit(stack, CorruptionSurface.CAMERA_TRANSFORM, targetId, 0x54494E59) * 18.0D;
        }
        if (stack.extreme(CorruptionSurface.CAMERA_TRANSFORM)) {
            double multiplier = 0.025D + unit(stack, CorruptionSurface.CAMERA_TRANSFORM, targetId, 0x4D) * 5.75D;
            if (unit(stack, CorruptionSurface.CAMERA_TRANSFORM, targetId, 0x43) < 0.28D) {
                multiplier = 0.018D + unit(stack, CorruptionSurface.CAMERA_TRANSFORM, targetId, 0x44) * 0.20D;
            }
            mutated = mutated * multiplier + signed(stack, CorruptionSurface.CAMERA_TRANSFORM, targetId, 0x53, 96.0D);
        }
        event.setFOV(clampDouble(mutated, 12.0D, 165.0D));
    }

    @SubscribeEvent
    public static void onComputeFovModifier(ComputeFovModifierEvent event) {
        CorruptionEffectStack stack = ClientCorruptionEffects.current();
        if (!stack.activeOrExtreme(CorruptionSurface.CAMERA_TRANSFORM)) {
            return;
        }

        Minecraft minecraft = Minecraft.getInstance();
        if (!CameraRenderCorruptionHooks.cameraReady(minecraft)) {
            return;
        }
        String targetId = cameraTargetId(event.getPlayer(), minecraft, "fov_modifier");
        float intensity = stack.intensityOrExtreme(CorruptionSurface.CAMERA_TRANSFORM);
        long clock = staticClock(stack, CorruptionSurface.CAMERA_TRANSFORM, targetId, 0x464D);
        float mutated = CorruptionValueMutator.mutateScalar(
                stack,
                CorruptionSurface.CAMERA_TRANSFORM,
                targetId,
                event.getNewFovModifier(),
                0.35F + intensity * 4.50F,
                0.18F,
                4.0F,
                0x4F,
                clock
        );
        double motionSignal = cameraMotionSignal(event.getPlayer());
        mutated = (float) (mutated * (1.0D + signed(stack, CorruptionSurface.CAMERA_TRANSFORM, targetId, 0x464D5343, 0.08D + intensity * 1.75D))
                + motionSignal * signed(stack, CorruptionSurface.CAMERA_TRANSFORM, targetId, 0x464D424F, 0.12D + intensity * 2.10D));
        if (stack.extreme(CorruptionSurface.CAMERA_TRANSFORM)) {
            mutated = (float) (mutated * (0.16D + unit(stack, CorruptionSurface.CAMERA_TRANSFORM, targetId, 0x4E) * 3.60D)
                    + signed(stack, CorruptionSurface.CAMERA_TRANSFORM, targetId, 0x59, 1.20D));
        }
        event.setNewFovModifier((float) clampDouble(mutated, 0.18D, 4.0D));
    }

    @SubscribeEvent
    public static void onComputeFogColor(ViewportEvent.ComputeFogColor event) {
        CorruptionEffectStack stack = ClientCorruptionEffects.current();
        if (!stack.active(CorruptionSurface.LIGHT_FIELD)) {
            return;
        }

        Minecraft.getInstance();
        String targetId = "fog_color:" + event.getCamera().getFluidInCamera().name();
        long clock = staticClock(stack, CorruptionSurface.LIGHT_FIELD, targetId, 0x464F47);
        float span = 0.28F + stack.intensity(CorruptionSurface.LIGHT_FIELD) * 0.82F;
        event.setRed(CorruptionValueMutator.mutateScalar(stack, CorruptionSurface.LIGHT_FIELD, targetId + ":r", event.getRed(), span, 0.0F, 1.55F, 0x11, clock));
        event.setGreen(CorruptionValueMutator.mutateScalar(stack, CorruptionSurface.LIGHT_FIELD, targetId + ":g", event.getGreen(), span, 0.0F, 1.55F, 0x29, clock));
        event.setBlue(CorruptionValueMutator.mutateScalar(stack, CorruptionSurface.LIGHT_FIELD, targetId + ":b", event.getBlue(), span, 0.0F, 1.55F, 0x43, clock));
    }

    @SubscribeEvent
    public static void onRenderFog(ViewportEvent.RenderFog event) {
        CorruptionEffectStack stack = ClientCorruptionEffects.current();
        String targetId = "fog_plane:" + event.getType().name() + ":" + event.getMode().name();
        if (!stack.active(CorruptionSurface.LIGHT_FIELD, targetId)) {
            return;
        }

        Minecraft.getInstance();
        long clock = staticClock(stack, CorruptionSurface.LIGHT_FIELD, targetId, 0x504C414E);
        float intensity = stack.targetIntensity(CorruptionSurface.LIGHT_FIELD, targetId);
        float far = CorruptionValueMutator.mutateScalar(stack, CorruptionSurface.LIGHT_FIELD, targetId + ":far", event.getFarPlaneDistance(), 24.0F + intensity * 260.0F, 0.08F, 768.0F, 0x51, clock);
        float near = CorruptionValueMutator.mutateScalar(stack, CorruptionSurface.LIGHT_FIELD, targetId + ":near", event.getNearPlaneDistance(), 12.0F + intensity * 120.0F, -128.0F, Math.max(0.1F, far), 0x67, clock);
        event.setFarPlaneDistance(far);
        event.setNearPlaneDistance(near);
        if (CorruptionValueMutator.decision(stack, CorruptionSurface.LIGHT_FIELD, targetId + ":shape", 0x7B, 0.68F)) {
            event.setFogShape(event.getFogShape() == FogShape.CYLINDER ? FogShape.SPHERE : FogShape.CYLINDER);
        }
        event.setCanceled(true);
    }

    @SubscribeEvent
    public static void onClientTick(TickEvent.ClientTickEvent event) {
        if (event.phase != TickEvent.Phase.END) {
            return;
        }

        tickLightingCorruptionState();
        tickPendingWorldRefresh();
    }

    @SubscribeEvent
    public static void onTitleRenderPre(ScreenEvent.Render.Pre event) {
        CorruptionEffectStack stack = ClientCorruptionEffects.current();
        if (!stack.active(CorruptionSurface.TITLE_RENDER) || !(event.getScreen() instanceof TitleScreen titleScreen)) {
            return;
        }

        PanoramaRenderer panorama = titlePanorama(titleScreen);
        if (panorama != null) {
            panorama.render(event.getPartialTick(), 0.0F);
        }
    }

    private static int corruptSurfaceColor(CorruptionEffectStack stack, String targetId, int baseColor) {
        float intensity = stack.intensity(CorruptionSurface.BIOME_TINT);
        int shifted = CorruptionValueMutator.mutateColor(stack, CorruptionSurface.BIOME_TINT, targetId, CORRUPT_SURFACE_COLOR, 1, staticClock(stack, CorruptionSurface.BIOME_TINT, targetId, 0x54494E54));
        return blendColor(baseColor, shifted, intensity);
    }

    private static int originalBlockColor(Map<Holder.Reference<Block>, BlockColor> handlers, BlockState state, net.minecraft.world.level.BlockAndTintGetter level, BlockPos pos, int tintIndex) {
        if (state == null) {
            return -1;
        }
        BlockColor handler = handlers.get(ForgeRegistries.BLOCKS.getDelegateOrThrow(state.getBlock()));
        if (handler != null) {
            try {
                return handler.getColor(state, level, pos, tintIndex);
            } catch (RuntimeException ignored) {
                return -1;
            }
        }
        return -1;
    }

    private static int baseBlockMapColor(BlockState state, net.minecraft.world.level.BlockAndTintGetter level, BlockPos pos) {
        if (state == null) {
            return 0xFFFFFF;
        }
        try {
            return state.getMapColor(level, pos).col;
        } catch (RuntimeException ignored) {
            return 0xFFFFFF;
        }
    }

    private static int baseItemMapColor(Item item) {
        if (item instanceof BlockItem blockItem) {
            return baseBlockMapColor(blockItem.getBlock().defaultBlockState(), null, null);
        }
        return 0xFFFFFF;
    }

    private static int originalItemColor(Map<Holder.Reference<Item>, ItemColor> handlers, net.minecraft.world.item.ItemStack itemStack, int tintIndex) {
        if (itemStack == null || itemStack.isEmpty()) {
            return -1;
        }
        ItemColor handler = handlers.get(ForgeRegistries.ITEMS.getDelegateOrThrow(itemStack.getItem()));
        if (handler == null) {
            return -1;
        }
        try {
            return handler.getColor(itemStack, tintIndex);
        } catch (RuntimeException ignored) {
            return -1;
        }
    }

    @SuppressWarnings("unchecked")
    private static Map<Holder.Reference<Block>, BlockColor> snapshotBlockColorHandlers(BlockColors blockColors) {
        Map<Holder.Reference<Block>, BlockColor> snapshot = new HashMap<>();
        Field field = blockColorsMapField;
        if (field == null) {
            field = findField(BlockColors.class, "blockColors", "f_92571_");
            blockColorsMapField = field;
        }
        if (field == null) {
            return snapshot;
        }
        try {
            Object value = field.get(blockColors);
            if (value instanceof Map<?, ?> map) {
                map.forEach((key, handler) -> {
                    if (key instanceof Holder.Reference<?> reference && handler instanceof BlockColor blockColor) {
                        snapshot.put((Holder.Reference<Block>) reference, blockColor);
                    }
                });
            }
        } catch (IllegalAccessException ignored) {
        }
        return snapshot;
    }

    @SuppressWarnings("unchecked")
    private static Map<Holder.Reference<Item>, ItemColor> snapshotItemColorHandlers(ItemColors itemColors) {
        Map<Holder.Reference<Item>, ItemColor> snapshot = new HashMap<>();
        Field field = itemColorsMapField;
        if (field == null) {
            field = findField(ItemColors.class, "itemColors", "f_92674_");
            itemColorsMapField = field;
        }
        if (field == null) {
            return snapshot;
        }
        try {
            Object value = field.get(itemColors);
            if (value instanceof Map<?, ?> map) {
                map.forEach((key, handler) -> {
                    if (key instanceof Holder.Reference<?> reference && handler instanceof ItemColor itemColor) {
                        snapshot.put((Holder.Reference<Item>) reference, itemColor);
                    }
                });
            }
        } catch (IllegalAccessException ignored) {
        }
        return snapshot;
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

    private static boolean refreshWorldRendering() {
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.level != null && minecraft.levelRenderer != null && minecraft.player != null) {
            if (pendingWorldRefreshPasses >= 6) {
                minecraft.levelRenderer.allChanged();
            }
            minecraft.levelRenderer.needsUpdate();
            markVisibleSectionsDirty(minecraft);
            return true;
        }
        return false;
    }

    private static void markVisibleSectionsDirty(Minecraft minecraft) {
        Level level = minecraft.level;
        LocalPlayer player = minecraft.player;
        if (level == null || player == null || minecraft.levelRenderer == null) {
            return;
        }

        BlockPos center = player.blockPosition();
        int renderDistance = Math.max(2, Math.min(32, minecraft.options.getEffectiveRenderDistance() + 2));
        int centerSectionX = SectionPos.blockToSectionCoord(center.getX());
        int centerSectionZ = SectionPos.blockToSectionCoord(center.getZ());
        int minSectionY = SectionPos.blockToSectionCoord(level.getMinBuildHeight());
        int maxSectionY = SectionPos.blockToSectionCoord(level.getMaxBuildHeight() - 1);
        for (int sectionX = centerSectionX - renderDistance; sectionX <= centerSectionX + renderDistance; sectionX++) {
            for (int sectionZ = centerSectionZ - renderDistance; sectionZ <= centerSectionZ + renderDistance; sectionZ++) {
                for (int sectionY = minSectionY; sectionY <= maxSectionY; sectionY++) {
                    minecraft.levelRenderer.setSectionDirtyWithNeighbors(sectionX, sectionY, sectionZ);
                }
            }
        }
    }

    private static void tickPendingWorldRefresh() {
        if (!pendingWorldRefresh || pendingWorldRefreshDelay < 0) {
            return;
        }
        if (pendingWorldRefreshDelay-- > 0) {
            return;
        }
        boolean refreshed = refreshWorldRendering();
        if (!refreshed) {
            pendingWorldRefreshDelay = 10;
            return;
        }
        pendingWorldRefreshPasses--;
        if (pendingWorldRefreshPasses > 0) {
            pendingWorldRefreshDelay = 1;
            return;
        }
        pendingWorldRefresh = false;
        pendingWorldRefreshDelay = -1;
    }

    private static void tickLightingCorruptionState() {
        Minecraft minecraft = Minecraft.getInstance();
        boolean inWorld = minecraft.level != null && minecraft.player != null;
        boolean active = inWorld && LightingCorruptionHooks.lightingCorruptionActive(ClientCorruptionEffects.currentForWorldRendering());
        if (lightingCorruptionWasActive && !active) {
            requestLightTextureReset();
            requestWorldRenderRefresh();
        }
        lightingCorruptionWasActive = active;
    }

    private static String chunkRenderRefreshSignature(CorruptionEffectStack stack) {
        // Model geometry is baked into chunk meshes, so target changes need a rebuild even though it is not a world-visual surface.
        return stack.level()
                + ":" + stack.fixedSeed()
                + ":" + stack.bucket(CorruptionSurface.BIOME_TINT, 0x42494F4D, 64)
                + ":" + stack.bucket(CorruptionSurface.LIGHT_FIELD, 0x4C494748, 64)
                + ":" + stack.bucket(CorruptionSurface.TEXTURE_MEMORY, 0x544558, 64)
                + ":" + stack.bucket(CorruptionSurface.MODEL_GEOMETRY, 0x4D4F444C, 64)
                + ":" + stack.bucket(CorruptionSurface.WORLD_RENDER, 0x574F524C, 64);
    }

    private static String blockTargetId(BlockState state) {
        ResourceLocation location = state == null ? null : ForgeRegistries.BLOCKS.getKey(state.getBlock());
        return location == null ? "block:unknown" : "block:" + location;
    }

    private static String itemTargetId(Item item) {
        return "item:" + ForgeRegistries.ITEMS.getKey(item);
    }

    private static String cameraTargetId(Player player, Minecraft minecraft, String feature) {
        StringBuilder builder = new StringBuilder("camera:")
                .append(feature)
                .append(':')
                .append(minecraft.options.getCameraType().name());
        if (player == null) {
            return builder.append(":no_player").toString();
        }

        builder.append(player.isSwimming() ? ":swimming" : player.isInWater() ? ":water" : player.onGround() ? ":ground" : ":air");
        if (player.isInLava()) {
            builder.append(":lava");
        }
        if (player.isFallFlying()) {
            builder.append(":fall_flying");
        }
        if (player.isPassenger()) {
            builder.append(":vehicle");
        }
        if (player.fishing != null) {
            builder.append(":fishing");
        }
        if (player.isSprinting()) {
            builder.append(":sprint");
        }
        if (player.isCrouching()) {
            builder.append(":crouch");
        }
        if (player.isUsingItem() && !player.getUseItem().isEmpty()) {
            ResourceLocation itemId = ForgeRegistries.ITEMS.getKey(player.getUseItem().getItem());
            builder.append(":using=").append(itemId == null ? "unknown" : itemId);
        }
        return builder.toString();
    }

    private static long staticClock(CorruptionEffectStack stack, CorruptionSurface surface, String targetId, int salt) {
        return stack.stableLong(surface, targetId, salt) ^ ((long) stack.level() << 32) ^ stack.layerCount() * 0x9E3779B97F4A7C15L;
    }

    private static double unit(CorruptionEffectStack stack, CorruptionSurface surface, String targetId, int salt) {
        return stack.unit(surface, targetId, salt);
    }

    private static double signed(CorruptionEffectStack stack, CorruptionSurface surface, String targetId, int salt, double amplitude) {
        return (unit(stack, surface, targetId, salt) * 2.0D - 1.0D) * amplitude;
    }

    private static double cameraMotionSignal(Player player) {
        if (player == null) {
            return 0.0D;
        }
        double horizontal = player.getDeltaMovement().horizontalDistance();
        double vertical = Math.abs(player.getDeltaMovement().y) * 0.45D;
        double sprint = player.isSprinting() ? 0.22D : 0.0D;
        double fall = player.onGround() ? 0.0D : 0.16D;
        return Mth.clamp(horizontal * 8.0D + vertical + sprint + fall, 0.0D, 1.0D);
    }

    private static double clampDouble(double value, double min, double max) {
        if (!Double.isFinite(value)) {
            return min;
        }
        return Math.max(min, Math.min(max, value));
    }

    private static int blendColor(int from, int to, float amount) {
        float clamped = Math.max(0.0F, Math.min(1.0F, amount));
        int red = Math.round(Mth.lerp(clamped, (from >> 16) & 0xFF, (to >> 16) & 0xFF));
        int green = Math.round(Mth.lerp(clamped, (from >> 8) & 0xFF, (to >> 8) & 0xFF));
        int blue = Math.round(Mth.lerp(clamped, from & 0xFF, to & 0xFF));
        return red << 16 | green << 8 | blue;
    }

    private static PanoramaRenderer titlePanorama(TitleScreen titleScreen) {
        try {
            Field field = titlePanoramaField();
            Object value = field == null ? null : field.get(titleScreen);
            return value instanceof PanoramaRenderer panorama ? panorama : null;
        } catch (ReflectiveOperationException exception) {
            return null;
        }
    }

    private static Field titlePanoramaField() {
        if (titlePanoramaFieldChecked) {
            return titlePanoramaField;
        }
        titlePanoramaFieldChecked = true;
        try {
            titlePanoramaField = ObfuscationReflectionHelper.findField(TitleScreen.class, "f_96729_");
            titlePanoramaField.setAccessible(true);
            return titlePanoramaField;
        } catch (RuntimeException ignored) {
            try {
                titlePanoramaField = TitleScreen.class.getDeclaredField("panorama");
                titlePanoramaField.setAccessible(true);
                return titlePanoramaField;
            } catch (ReflectiveOperationException ignoredFallback) {
                return null;
            }
        }
    }
}
