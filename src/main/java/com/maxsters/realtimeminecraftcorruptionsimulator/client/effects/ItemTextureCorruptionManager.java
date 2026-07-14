package com.maxsters.realtimeminecraftcorruptionsimulator.client.effects;

import com.maxsters.realtimeminecraftcorruptionsimulator.client.ClientCorruptionProtection;
import com.maxsters.realtimeminecraftcorruptionsimulator.profile.CorruptionEffectStack;
import com.maxsters.realtimeminecraftcorruptionsimulator.profile.CorruptionSurface;
import com.maxsters.realtimeminecraftcorruptionsimulator.profile.DeterministicCorruption;
import com.maxsters.realtimeminecraftcorruptionsimulator.profile.CorruptionValueMutator;
import com.maxsters.realtimeminecraftcorruptionsimulator.state.CorruptionStateSnapshot;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.math.Axis;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.block.model.BakedQuad;
import net.minecraft.client.renderer.block.model.ItemOverrides;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import net.minecraft.client.resources.model.BakedModel;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.BlockAndTintGetter;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.api.distmarker.OnlyIn;
import net.minecraftforge.client.ChunkRenderTypeSet;
import net.minecraftforge.client.event.ModelEvent;
import net.minecraftforge.client.model.data.ModelData;
import net.minecraftforge.registries.ForgeRegistries;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.lang.reflect.Array;
import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.function.Supplier;

@OnlyIn(Dist.CLIENT)
public final class ItemTextureCorruptionManager {
    private static final long ITEM_TEXTURE_SEED = DeterministicCorruption.DEFAULT_SEED ^ 0x493354454d55564cL;
    private static final List<TextureAtlasSprite> SPRITE_POOL = new ArrayList<>();
    private static final Set<ResourceLocation> SPRITE_POOL_IDS = ConcurrentHashMap.newKeySet();
    private static final Map<ResourceLocation, List<TextureAtlasSprite>> SPRITES_BY_ATLAS = new HashMap<>();
    private static final Set<CorruptedItemBakedModel> MODEL_WRAPPERS = Collections.newSetFromMap(new ConcurrentHashMap<>());
    private static final ConcurrentMap<BakedModel, CorruptedItemBakedModel> RUNTIME_ITEM_MODEL_WRAPPERS = new ConcurrentHashMap<>();
    private static final ConcurrentMap<BakedModel, CorruptedItemBakedModel> RUNTIME_BLOCK_MODEL_WRAPPERS = new ConcurrentHashMap<>();
    private static final ConcurrentMap<ResourceLocation, CorruptedItemBakedModel> RENDERED_BLOCK_QUAD_WRAPPERS = new ConcurrentHashMap<>();
    private static final ThreadLocal<Boolean> SUPPRESS_RETURNED_QUAD_HOOK = ThreadLocal.withInitial(() -> false);
    private static final Field ITEM_OVERRIDES_FIELD = field(ItemOverrides.class, "overrides", "f_111735_");
    private static final ConcurrentMap<Class<?>, Field> BAKED_OVERRIDE_MODEL_FIELDS = new ConcurrentHashMap<>();

    private ItemTextureCorruptionManager() {
    }

    public static void rememberAtlasSprite(TextureAtlasSprite sprite) {
        if (sprite == null || sprite.contents() == null || sprite.contents().name() == null) {
            return;
        }
        ResourceLocation id = sprite.contents().name();
        if (SPRITE_POOL_IDS.contains(id)) {
            return;
        }
        boolean becameUsable = false;
        synchronized (SPRITE_POOL) {
            if (SPRITE_POOL_IDS.add(id)) {
                SPRITE_POOL.add(spriteInsertionIndex(id), sprite);
                ResourceLocation atlasId = sprite.atlasLocation();
                if (atlasId != null) {
                    List<TextureAtlasSprite> atlasSprites = SPRITES_BY_ATLAS.computeIfAbsent(atlasId, ignored -> new ArrayList<>());
                    atlasSprites.add(spriteInsertionIndex(atlasSprites, id), sprite);
                }
                becameUsable = SPRITE_POOL.size() == 2;
            }
        }
        if (becameUsable && ClientCorruptionEffects.current().activeOrExtreme(CorruptionSurface.TEXTURE_MEMORY)) {
            clearModelCaches();
            VisualCorruptionManager.requestWorldRenderRefresh();
        }
    }

    public static TextureAtlasSprite corruptParticleSprite(TextureAtlasSprite original, String targetId, int salt) {
        CorruptionEffectStack stack = ClientCorruptionEffects.current();
        if (original == null || !stack.activeOrExtreme(CorruptionSurface.TEXTURE_MEMORY)) {
            return original;
        }
        rememberAtlasSprite(original);

        float intensity = stack.extreme(CorruptionSurface.TEXTURE_MEMORY) ? 1.0F : stack.intensity(CorruptionSurface.TEXTURE_MEMORY);
        float chance = Math.min(0.98F, 0.16F + intensity * 0.76F + stack.instability() * 0.12F);
        if (!stack.extreme(CorruptionSurface.TEXTURE_MEMORY)
                && stack.unit(CorruptionSurface.TEXTURE_MEMORY, targetId, salt) > chance) {
            return original;
        }

        synchronized (SPRITE_POOL) {
            if (SPRITE_POOL.size() < 2) {
                return original;
            }
            int index = CorruptionValueMutator.selectIndex(stack, CorruptionSurface.TEXTURE_MEMORY, targetId, salt, SPRITE_POOL.size());
            TextureAtlasSprite replacement = SPRITE_POOL.get(index);
            if (sameSprite(original, replacement)) {
                replacement = SPRITE_POOL.get((index + 1) % SPRITE_POOL.size());
            }
            return replacement;
        }
    }

    public static TextureAtlasSprite corruptMaterialSprite(TextureAtlasSprite original, ResourceLocation atlasLocation, ResourceLocation materialTexture) {
        CorruptionEffectStack stack = ClientCorruptionEffects.currentForWorldRendering();
        if (original == null
                || !stack.activeOrExtreme(CorruptionSurface.TEXTURE_MEMORY)
                || ClientCorruptionProtection.isProtectedResource(atlasLocation)
                || ClientCorruptionProtection.isProtectedResource(materialTexture)) {
            return original;
        }
        rememberAtlasSprite(original);

        String targetId = "material_sprite:" + (atlasLocation == null ? "unknown_atlas" : atlasLocation) + ":" + (materialTexture == null ? "unknown_texture" : materialTexture);
        float intensity = stack.extreme(CorruptionSurface.TEXTURE_MEMORY)
                ? 1.0F
                : Mth.clamp(Math.max(
                        stack.targetIntensity(CorruptionSurface.TEXTURE_MEMORY, targetId),
                        stack.intensity(CorruptionSurface.TEXTURE_MEMORY) * 0.84F
                ) + stack.instability() * 0.08F, 0.0F, 1.0F);
        if (intensity <= 0.025F) {
            return original;
        }

        float chance = stack.extreme(CorruptionSurface.TEXTURE_MEMORY)
                ? 0.99F
                : Mth.clamp(0.16F + intensity * 0.80F + stack.instability() * 0.10F, 0.0F, 0.96F);
        int salt = materialTexture == null ? 0x4D41544C : materialTexture.hashCode();
        if (!stack.extreme(CorruptionSurface.TEXTURE_MEMORY)
                && stack.unit(CorruptionSurface.TEXTURE_MEMORY, targetId, salt) > chance) {
            return original;
        }

        synchronized (SPRITE_POOL) {
            TextureAtlasSprite replacement = replacementSpriteFromPool(original, stack, targetId, salt, SPRITES_BY_ATLAS.get(atlasLocation));
            if (replacement != original) {
                return replacement;
            }
            return replacementSpriteFromPool(original, stack, targetId, salt ^ 0x474C4F42, SPRITE_POOL);
        }
    }

    public static VertexConsumer wrapMaterialVertexConsumer(VertexConsumer consumer, ResourceLocation atlasLocation, ResourceLocation materialTexture) {
        CorruptionEffectStack stack = ClientCorruptionEffects.currentForWorldRendering();
        if (consumer == null
                || !stack.activeOrExtreme(CorruptionSurface.TEXTURE_MEMORY)
                || ClientCorruptionProtection.isProtectedResource(atlasLocation)
                || ClientCorruptionProtection.isProtectedResource(materialTexture)) {
            return consumer;
        }

        String targetId = "material_vertices:" + (atlasLocation == null ? "unknown_atlas" : atlasLocation) + ":" + (materialTexture == null ? "unknown_texture" : materialTexture);
        float intensity = stack.extreme(CorruptionSurface.TEXTURE_MEMORY)
                ? 1.0F
                : Mth.clamp(Math.max(
                        stack.targetIntensity(CorruptionSurface.TEXTURE_MEMORY, targetId),
                        stack.intensity(CorruptionSurface.TEXTURE_MEMORY) * 0.82F
                ) + stack.instability() * 0.08F, 0.0F, 1.0F);
        if (intensity <= 0.03F) {
            return consumer;
        }

        int salt = materialTexture == null ? 0x564D4154 : materialTexture.hashCode();
        float chance = stack.extreme(CorruptionSurface.TEXTURE_MEMORY)
                ? 1.0F
                : Mth.clamp(0.14F + intensity * 0.82F + stack.instability() * 0.10F, 0.0F, 0.98F);
        if (!stack.extreme(CorruptionSurface.TEXTURE_MEMORY)
                && stack.unit(CorruptionSurface.TEXTURE_MEMORY, targetId, salt) > chance) {
            return consumer;
        }

        long seed = stack.stableLong(CorruptionSurface.TEXTURE_MEMORY, targetId, salt);
        return new CorruptedMaterialVertexConsumer(consumer, seed, intensity, stack.extreme(CorruptionSurface.TEXTURE_MEMORY));
    }

    public static List<BakedQuad> corruptBlockRenderQuads(BakedModel model, @Nullable BlockState state, @Nullable Direction side, RandomSource random) {
        List<BakedQuad> quads = model.getQuads(state, side, random, ModelData.EMPTY, null);
        return corruptReturnedBlockQuads(model, state, side, quads);
    }

    public static List<BakedQuad> corruptReturnedBlockQuads(BakedModel model, @Nullable BlockState state, @Nullable Direction side, List<BakedQuad> quads) {
        if (quads.isEmpty() || model instanceof CorruptedItemBakedModel) {
            return quads;
        }
        if (SUPPRESS_RETURNED_QUAD_HOOK.get()) {
            return quads;
        }

        CorruptionEffectStack stack = state == null ? ClientCorruptionEffects.current() : ClientCorruptionEffects.currentForWorldRendering();
        if (!stack.activeOrExtreme(CorruptionSurface.TEXTURE_MEMORY)
                && !stack.activeOrExtreme(CorruptionSurface.MODEL_GEOMETRY)) {
            return quads;
        }

        CorruptedItemBakedModel wrapper = runtimeBlockWrapper(model, state);
        return wrapper == null ? quads : wrapper.transform(quads, stack);
    }

    public static BakedQuad corruptRenderedBlockQuad(@Nullable BlockState state, BakedQuad quad) {
        if (state == null || quad == null) {
            return quad;
        }

        CorruptionEffectStack stack = ClientCorruptionEffects.currentForWorldRendering();
        if (!stack.activeOrExtreme(CorruptionSurface.TEXTURE_MEMORY)
                && !stack.activeOrExtreme(CorruptionSurface.MODEL_GEOMETRY)) {
            return quad;
        }

        ResourceLocation modelId = blockModelLocation(state);
        if (ClientCorruptionProtection.isProtectedResource(modelId)) {
            return quad;
        }

        CorruptedItemBakedModel wrapper = RENDERED_BLOCK_QUAD_WRAPPERS.computeIfAbsent(modelId, key -> new CorruptedItemBakedModel(null, key, false));
        return wrapper.transformSingle(quad, stack);
    }

    private static <T> T withReturnedQuadHookSuppressed(Supplier<T> supplier) {
        boolean previous = SUPPRESS_RETURNED_QUAD_HOOK.get();
        SUPPRESS_RETURNED_QUAD_HOOK.set(true);
        try {
            return supplier.get();
        } finally {
            SUPPRESS_RETURNED_QUAD_HOOK.set(previous);
        }
    }

    public static void modifyBakedModels(ModelEvent.ModifyBakingResult event) {
        Map<ResourceLocation, BakedModel> models = event.getModels();
        for (Map.Entry<ResourceLocation, BakedModel> entry : new ArrayList<>(models.entrySet())) {
            ResourceLocation location = entry.getKey();
            if (!isCandidateModelSurface(location)) {
                continue;
            }
            BakedModel model = entry.getValue();
            if (model != null && !(model instanceof CorruptedItemBakedModel)) {
                models.put(location, new CorruptedItemBakedModel(model, location, true));
            }
        }
    }

    public static void onSettingsChanged(CorruptionStateSnapshot previous, CorruptionStateSnapshot current) {
        CorruptionEffectStack previousStack = CorruptionEffectStack.from(previous);
        CorruptionEffectStack currentStack = CorruptionEffectStack.from(current);
        if (!modelTextureSignature(previousStack).equals(modelTextureSignature(currentStack))) {
            clearModelCaches();
            VisualCorruptionManager.requestWorldRenderRefresh();
        }
    }

    private static void clearModelCaches() {
        for (CorruptedItemBakedModel wrapper : MODEL_WRAPPERS) {
            wrapper.clearCache();
        }
        for (CorruptedItemBakedModel wrapper : RUNTIME_ITEM_MODEL_WRAPPERS.values()) {
            wrapper.clearCache();
        }
        RUNTIME_BLOCK_MODEL_WRAPPERS.clear();
        RUNTIME_ITEM_MODEL_WRAPPERS.clear();
        RENDERED_BLOCK_QUAD_WRAPPERS.clear();
    }

    private static String modelTextureSignature(CorruptionEffectStack stack) {
        return stack.level()
                + ":" + stack.fixedSeed()
                + ":" + stack.enabledTargetsMask()
                + ":" + stack.bucket(CorruptionSurface.TEXTURE_MEMORY, 0x4954454D, 64)
                + ":" + stack.bucket(CorruptionSurface.MODEL_GEOMETRY, 0x47454F4D, 64);
    }

    private static boolean isCandidateModelSurface(ResourceLocation location) {
        if (ClientCorruptionProtection.isProtectedResource(location)) {
            return false;
        }
        String path = location.getPath();
        return path.startsWith("item/")
                || path.startsWith("block/")
                || ForgeRegistries.BLOCKS.containsKey(registryLocation(location))
                || ForgeRegistries.ITEMS.containsKey(registryLocation(location));
    }

    private static boolean isBlockModelSurface(ResourceLocation location) {
        String path = location.getPath();
        return path.startsWith("block/") || ForgeRegistries.BLOCKS.containsKey(registryLocation(location));
    }

    @Nullable
    private static CorruptedItemBakedModel runtimeBlockWrapper(BakedModel model, @Nullable BlockState state) {
        if (model == null || state == null || ClientCorruptionProtection.isProtectedResource(blockModelLocation(state))) {
            return null;
        }
        return RUNTIME_BLOCK_MODEL_WRAPPERS.computeIfAbsent(model, key -> new CorruptedItemBakedModel(key, blockModelLocation(state), false));
    }

    private static ResourceLocation blockModelLocation(BlockState state) {
        ResourceLocation blockId = ForgeRegistries.BLOCKS.getKey(state.getBlock());
        if (blockId == null) {
            return ResourceLocation.fromNamespaceAndPath("minecraft", "block/unknown");
        }
        return ResourceLocation.fromNamespaceAndPath(blockId.getNamespace(), "block/" + blockId.getPath());
    }

    private static ResourceLocation registryLocation(ResourceLocation location) {
        String path = location.getPath();
        int slash = path.indexOf('/');
        if (slash >= 0 && slash + 1 < path.length()) {
            path = path.substring(slash + 1);
        }
        return ResourceLocation.fromNamespaceAndPath(location.getNamespace(), path);
    }

    private static int stableHash(String value, long seed) {
        long hash = seed;
        for (int i = 0; i < value.length(); i++) {
            hash ^= value.charAt(i);
            hash *= 0x100000001b3L;
            hash ^= hash >>> 32;
        }
        return (int) (hash ^ (hash >>> 32));
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

    private static final class CorruptedItemBakedModel implements BakedModel {
        @Nullable
        private final BakedModel delegate;
        private final String modelId;
        private final boolean blockModel;
        private final int effectHash;
        private final ItemOverrides overrides;
        private final ConcurrentMap<Integer, ConcurrentMap<BakedQuad, BakedQuad>> textureLeakedQuadsByBucket = new ConcurrentHashMap<>();
        private final ConcurrentMap<Integer, ConcurrentMap<BakedQuad, BakedQuad>> modelGeometryQuadsByBucket = new ConcurrentHashMap<>();

        private CorruptedItemBakedModel(@Nullable BakedModel delegate, ResourceLocation modelId, boolean tracked) {
            this(delegate, modelId.toString(), isBlockModelSurface(modelId), tracked);
        }

        private CorruptedItemBakedModel(@Nullable BakedModel delegate, String modelId, boolean blockModel, boolean tracked) {
            this.delegate = delegate;
            this.modelId = modelId;
            this.blockModel = blockModel;
            this.effectHash = stableHash(modelId, ITEM_TEXTURE_SEED ^ 0x445241575f495445L);
            this.overrides = delegate == null ? ItemOverrides.EMPTY : new CorruptedItemOverrides(this, delegate.getOverrides());
            if (tracked) {
                MODEL_WRAPPERS.add(this);
            }
        }

        @Deprecated
        @Override
        public @NotNull List<BakedQuad> getQuads(@Nullable BlockState state, @Nullable Direction side, @NotNull RandomSource random) {
            if (delegate == null) {
                return Collections.emptyList();
            }
            return getQuads(state, side, random, ModelData.EMPTY, null);
        }

        @Override
        public @NotNull List<BakedQuad> getQuads(@Nullable BlockState state, @Nullable Direction side, @NotNull RandomSource random, @NotNull ModelData data, @Nullable RenderType renderType) {
            if (delegate == null) {
                return Collections.emptyList();
            }
            CorruptionEffectStack stack = state == null ? ClientCorruptionEffects.current() : ClientCorruptionEffects.currentForWorldRendering();
            return transform(withReturnedQuadHookSuppressed(() -> delegate.getQuads(state, side, random, data, renderType)), stack);
        }

        @Override
        public boolean useAmbientOcclusion() {
            return corruptedRenderFlag("ambient_occlusion", delegate.useAmbientOcclusion());
        }

        @Override
        public boolean useAmbientOcclusion(BlockState state) {
            return corruptedRenderFlag("ambient_occlusion_state", delegate.useAmbientOcclusion(state));
        }

        @Override
        public boolean useAmbientOcclusion(BlockState state, RenderType renderType) {
            return corruptedRenderFlag("ambient_occlusion_render_type", delegate.useAmbientOcclusion(state, renderType));
        }

        @Override
        public boolean isGui3d() {
            return delegate.isGui3d();
        }

        @Override
        public boolean usesBlockLight() {
            return corruptedRenderFlag("block_light", delegate.usesBlockLight());
        }

        @Override
        public boolean isCustomRenderer() {
            return delegate.isCustomRenderer();
        }

        @Deprecated
        @Override
        public TextureAtlasSprite getParticleIcon() {
            return getParticleIcon(ModelData.EMPTY);
        }

        @Override
        public TextureAtlasSprite getParticleIcon(@NotNull ModelData data) {
            return delegate.getParticleIcon(data);
        }

        @Override
        public BakedModel applyTransform(ItemDisplayContext transformType, PoseStack poseStack, boolean applyLeftHandTransform) {
            BakedModel transformed = delegate.applyTransform(transformType, poseStack, applyLeftHandTransform);
            corruptItemTransform(transformType, poseStack);
            return transformed == delegate ? this : transformed;
        }

        @Override
        public ItemOverrides getOverrides() {
            return overrides;
        }

        @Override
        public @NotNull ModelData getModelData(@NotNull BlockAndTintGetter level, @NotNull BlockPos pos, @NotNull BlockState state, @NotNull ModelData modelData) {
            return delegate.getModelData(level, pos, state, modelData);
        }

        @Override
        public ChunkRenderTypeSet getRenderTypes(@NotNull BlockState state, @NotNull RandomSource random, @NotNull ModelData data) {
            return delegate.getRenderTypes(state, random, data);
        }

        @Override
        public List<RenderType> getRenderTypes(ItemStack stack, boolean fabulous) {
            return delegate.getRenderTypes(stack, fabulous);
        }

        private List<BakedQuad> transform(List<BakedQuad> quads, CorruptionEffectStack stack) {
            for (BakedQuad quad : quads) {
                rememberAtlasSprite(quad.getSprite());
            }
            if (quads.isEmpty()) {
                return quads;
            }

            List<BakedQuad> transformed = quads;
            if (stack.activeOrExtreme(CorruptionSurface.TEXTURE_MEMORY)) {
                transformed = transformTextureMaps(transformed, stack);
            }
            if (stack.activeOrExtreme(CorruptionSurface.MODEL_GEOMETRY)) {
                transformed = transformModelGeometry(transformed, stack);
            }
            return transformed;
        }

        private BakedQuad transformSingle(BakedQuad quad, CorruptionEffectStack stack) {
            rememberAtlasSprite(quad.getSprite());

            BakedQuad transformed = quad;
            if (stack.activeOrExtreme(CorruptionSurface.TEXTURE_MEMORY)) {
                int bucket = textureBucket(stack);
                ConcurrentMap<BakedQuad, BakedQuad> transformedQuads = textureLeakedQuadsByBucket.computeIfAbsent(bucket, ignored -> new ConcurrentHashMap<>());
                BakedQuad cached = transformedQuads.get(transformed);
                if (cached == null) {
                    cached = transformTextureMapLeak(transformed, stack);
                    if (cached != transformed) {
                        BakedQuad existing = transformedQuads.putIfAbsent(transformed, cached);
                        if (existing != null) {
                            cached = existing;
                        }
                    }
                }
                transformed = cached;
            }
            if (stack.activeOrExtreme(CorruptionSurface.MODEL_GEOMETRY)) {
                int bucket = modelBucket(stack);
                ConcurrentMap<BakedQuad, BakedQuad> transformedQuads = modelGeometryQuadsByBucket.computeIfAbsent(bucket, ignored -> new ConcurrentHashMap<>());
                transformed = transformedQuads.computeIfAbsent(transformed, value -> transformModelGeometry(value, stack, bucket));
            }
            return transformed;
        }

        private List<BakedQuad> transformTextureMaps(List<BakedQuad> quads, CorruptionEffectStack stack) {
            int bucket = textureBucket(stack);
            ConcurrentMap<BakedQuad, BakedQuad> transformedQuads = textureLeakedQuadsByBucket.computeIfAbsent(bucket, ignored -> new ConcurrentHashMap<>());
            List<BakedQuad> result = new ArrayList<>(quads.size());
            boolean changed = false;
            for (BakedQuad quad : quads) {
                BakedQuad transformed = transformedQuads.get(quad);
                if (transformed == null) {
                    transformed = transformTextureMapLeak(quad, stack);
                    if (transformed != quad) {
                        BakedQuad cached = transformedQuads.putIfAbsent(quad, transformed);
                        if (cached != null) {
                            transformed = cached;
                        }
                    }
                }
                result.add(transformed);
                changed |= transformed != quad;
            }
            return changed ? result : quads;
        }

        private List<BakedQuad> transformModelGeometry(List<BakedQuad> quads, CorruptionEffectStack stack) {
            int bucket = modelBucket(stack);
            ConcurrentMap<BakedQuad, BakedQuad> transformedQuads = modelGeometryQuadsByBucket.computeIfAbsent(bucket, ignored -> new ConcurrentHashMap<>());
            List<BakedQuad> result = new ArrayList<>(quads.size());
            boolean changed = false;
            for (BakedQuad quad : quads) {
                BakedQuad transformed = transformedQuads.computeIfAbsent(quad, value -> transformModelGeometry(value, stack, bucket));
                result.add(transformed);
                changed |= transformed != quad;
            }
            return changed ? result : quads;
        }

        private int textureBucket(CorruptionEffectStack stack) {
            return blockModel
                    ? stack.bucket(CorruptionSurface.TEXTURE_MEMORY, effectHash ^ 0x544558, 48)
                    : stack.bucket(CorruptionSurface.TEXTURE_MEMORY, modelId, effectHash ^ 0x544558, 48);
        }

        private int modelBucket(CorruptionEffectStack stack) {
            return blockModel
                    ? stack.bucket(CorruptionSurface.MODEL_GEOMETRY, effectHash ^ 0x4D4F444C, 64)
                    : stack.bucket(CorruptionSurface.MODEL_GEOMETRY, modelId, effectHash ^ 0x4D4F444C, 64);
        }

        private BakedQuad transformTextureMapLeak(BakedQuad quad, CorruptionEffectStack stack) {
            TextureAtlasSprite sourceSprite = quad.getSprite();
            rememberAtlasSprite(sourceSprite);
            if (sourceSprite == null || sourceSprite.contents() == null || sourceSprite.contents().name() == null) {
                return quad;
            }

            float intensity = stack.extreme(CorruptionSurface.TEXTURE_MEMORY) ? 1.0F : stack.intensity(CorruptionSurface.TEXTURE_MEMORY);
            ResourceLocation sourceId = sourceSprite.contents().name();
            ResourceLocation atlasId = sourceSprite.atlasLocation();
            String targetId = "texture_map_leak:" + (atlasId == null ? "unknown_atlas" : atlasId) + ":" + sourceId;
            int sourceSalt = sourceId.hashCode() ^ 0x5445584C;
            int sourceBucket = stack.bucket(CorruptionSurface.TEXTURE_MEMORY, targetId, sourceSalt, 48);
            float chance = Math.min(1.0F, 0.10F + intensity * (blockModel ? 0.88F : 0.72F) + stack.instability() * 0.10F);
            if (!stack.extreme(CorruptionSurface.TEXTURE_MEMORY)
                    && stack.unit(CorruptionSurface.TEXTURE_MEMORY, targetId, sourceBucket ^ sourceSalt) > chance) {
                return quad;
            }

            long seed = stack.stableLong(CorruptionSurface.TEXTURE_MEMORY, targetId, sourceBucket ^ sourceSalt);
            int textureMode = Math.floorMod((int) (seed >>> 29), 10);
            TextureAtlasSprite replacement = textureMode >= 6 && unitHash(seed ^ 0x5352434E43484D44L) < 0.72F
                    ? sourceSprite
                    : replacementSprite(sourceSprite, stack, targetId, sourceBucket ^ sourceSalt);
            if (replacement == null) {
                replacement = sourceSprite;
            }

            int[] vertices = quad.getVertices().clone();
            int vertexIndex = 0;
            for (int vertex = 0; vertex + 5 < vertices.length; vertex += 8) {
                float u = Float.intBitsToFloat(vertices[vertex + 4]);
                float v = Float.intBitsToFloat(vertices[vertex + 5]);
                float localU = sourceSprite.getUOffset(u);
                float localV = sourceSprite.getVOffset(v);
                long vertexSeed = mixLong(seed ^ vertexIndex * 0x9E3779B97F4A7C15L);
                float[] leaked = corruptedLeakUv(localU, localV, seed, vertexSeed, intensity, textureMode);
                vertices[vertex + 4] = Float.floatToRawIntBits(replacement.getU(leaked[0]));
                vertices[vertex + 5] = Float.floatToRawIntBits(replacement.getV(leaked[1]));
                vertices[vertex + 3] = corruptedLeakColor(vertices[vertex + 3], seed, vertexSeed, intensity);
                vertexIndex++;
            }
            return new BakedQuad(vertices, quad.getTintIndex(), quad.getDirection(), replacement, quad.isShade());
        }

        private float[] corruptedLeakUv(float localU, float localV, long seed, long vertexSeed, float intensity, int mode) {
            float centeredU = localU - 8.0F;
            float centeredV = localV - 8.0F;
            float stretchPower = intensity * intensity;
            float stretchU = 1.0F + unitHash(seed ^ 0x53545255L) * (4.0F + stretchPower * 88.0F);
            float stretchV = 1.0F + unitHash(seed ^ 0x53545256L) * (3.0F + stretchPower * 72.0F);
            if (mode == 0 || mode == 3) {
                stretchV = Math.max(0.015F, 0.18F - intensity * 0.14F);
            }
            if (mode == 1 || mode == 4) {
                stretchU = Math.max(0.015F, 0.18F - intensity * 0.14F);
            }
            boolean scrunchU = mode == 6 || mode == 8 || mode == 9;
            boolean scrunchV = mode == 7 || mode == 8 || mode == 9;
            if (scrunchU) {
                stretchU = Math.max(0.006F, 0.30F - intensity * (mode == 9 ? 0.27F : 0.22F));
                stretchV = Math.min(stretchV, 0.92F + unitHash(seed ^ 0x564352554E43L) * (0.36F + intensity * 0.50F));
            }
            if (scrunchV) {
                stretchV = Math.max(0.006F, 0.30F - intensity * (mode == 9 ? 0.27F : 0.22F));
                stretchU = Math.min(stretchU, 0.92F + unitHash(seed ^ 0x554352554E43L) * (0.36F + intensity * 0.50F));
            }
            float shearU = centeredV * signedHash(seed ^ 0x53484555L, 1.0F) * intensity * 3.8F;
            float shearV = centeredU * signedHash(seed ^ 0x53484556L, 1.0F) * intensity * 3.2F;
            float offsetU = signedHash(seed ^ 0x4F464655L, 1.0F) * (8.0F + stretchPower * 128.0F);
            float offsetV = signedHash(seed ^ 0x4F464656L, 1.0F) * (8.0F + stretchPower * 112.0F);
            if (mode >= 6) {
                shearU *= 0.18F + intensity * 0.22F;
                shearV *= 0.18F + intensity * 0.22F;
                offsetU = signedHash(seed ^ 0x5543524F4646L, 0.35F + intensity * 5.0F);
                offsetV = signedHash(seed ^ 0x5643524F4646L, 0.35F + intensity * 5.0F);
            }
            float leakedU = 8.0F + centeredU * stretchU + shearU + offsetU;
            float leakedV = 8.0F + centeredV * stretchV + shearV + offsetV;

            if (unitHash(vertexSeed ^ 0x43555455L) < 0.20F + intensity * 0.58F) {
                float step = Math.max(0.5F, 1.0F + unitHash(vertexSeed ^ 0x51554155L) * (2.0F + intensity * 14.0F));
                leakedU = Math.round(leakedU / step) * step;
            }
            if (unitHash(vertexSeed ^ 0x43555456L) < 0.20F + intensity * 0.58F) {
                float step = Math.max(0.5F, 1.0F + unitHash(vertexSeed ^ 0x51554156L) * (2.0F + intensity * 14.0F));
                leakedV = Math.round(leakedV / step) * step;
            }
            if (unitHash(vertexSeed ^ 0x45444745L) < intensity * 0.42F) {
                leakedU = unitHash(vertexSeed ^ 0x45444755L) < 0.5F ? -64.0F : 96.0F;
            }
            if (unitHash(vertexSeed ^ 0x45444746L) < intensity * 0.38F) {
                leakedV = unitHash(vertexSeed ^ 0x45444756L) < 0.5F ? -64.0F : 96.0F;
            }
            return new float[] {
                    Mth.clamp(leakedU, -256.0F, 272.0F),
                    Mth.clamp(leakedV, -256.0F, 272.0F)
            };
        }

        private int corruptedLeakColor(int originalColor, long seed, long vertexSeed, float intensity) {
            float chance = 0.10F + intensity * 0.76F;
            if (unitHash(vertexSeed ^ 0x434F4C52L) > chance) {
                return originalColor;
            }
            int mode = Math.floorMod((int) (seed >>> 41), 7);
            if (mode == 0 || (mode == 4 && unitHash(vertexSeed ^ 0x4D495353L) < intensity)) {
                return unitHash(vertexSeed ^ 0x4D495354L) < 0.5F ? 0xFFFF00FF : 0xFF000000;
            }
            if (mode == 1) {
                return 0xFFFFFFFF;
            }
            int alpha = originalColor >>> 24;
            if (alpha == 0) {
                alpha = 0xFF;
            }
            int red = originalColor & 0xFF;
            int green = (originalColor >>> 8) & 0xFF;
            int blue = (originalColor >>> 16) & 0xFF;
            float wash = unitHash(vertexSeed ^ 0x57415348L) * intensity;
            if (mode == 2 || mode == 5) {
                red = Mth.clamp(Math.round(red + (255 - red) * (0.35F + wash * 0.65F)), 0, 255);
                green = Mth.clamp(Math.round(green + (255 - green) * (0.35F + wash * 0.65F)), 0, 255);
                blue = Mth.clamp(Math.round(blue + (255 - blue) * (0.35F + wash * 0.65F)), 0, 255);
            } else {
                int wrongRed = Math.round(unitHash(seed ^ 0x5257524FL) * 255.0F);
                int wrongGreen = Math.round(unitHash(seed ^ 0x4757524FL) * 255.0F);
                int wrongBlue = Math.round(unitHash(seed ^ 0x4257524FL) * 255.0F);
                red = Mth.clamp(Math.round(red * (1.0F - intensity) + wrongRed * intensity), 0, 255);
                green = Mth.clamp(Math.round(green * (1.0F - intensity) + wrongGreen * intensity), 0, 255);
                blue = Mth.clamp(Math.round(blue * (1.0F - intensity) + wrongBlue * intensity), 0, 255);
            }
            return alpha << 24 | blue << 16 | green << 8 | red;
        }

        private TextureAtlasSprite replacementSprite(TextureAtlasSprite original, CorruptionEffectStack stack, String targetId, int sourceSalt) {
            synchronized (SPRITE_POOL) {
                if (SPRITE_POOL.size() < 2) {
                    return original;
                }
                int index = stack.extreme(CorruptionSurface.TEXTURE_MEMORY)
                        ? Math.floorMod(stableHash(targetId, ITEM_TEXTURE_SEED ^ stack.stableLong(CorruptionSurface.TEXTURE_MEMORY, targetId, sourceSalt)), SPRITE_POOL.size())
                        : CorruptionValueMutator.selectIndex(stack, CorruptionSurface.TEXTURE_MEMORY, targetId, sourceSalt, SPRITE_POOL.size());
                TextureAtlasSprite replacement = SPRITE_POOL.get(index);
                if (sameSprite(original, replacement)) {
                    replacement = SPRITE_POOL.get((index + 1) % SPRITE_POOL.size());
                }
                return replacement;
            }
        }

        private BakedQuad transformModelGeometry(BakedQuad quad, CorruptionEffectStack stack, int bucket) {
            int[] original = quad.getVertices();
            if (original.length < 32) {
                return quad;
            }

            float intensity = stack.extreme(CorruptionSurface.MODEL_GEOMETRY)
                    ? 1.0F
                    : Math.max(stack.targetIntensity(CorruptionSurface.MODEL_GEOMETRY, modelId), stack.intensity(CorruptionSurface.MODEL_GEOMETRY) * 0.80F);
            String targetId = modelId + ":model_geometry:" + quad.getDirection().getName() + ":" + quad.getTintIndex();
            float chance = Math.min(0.98F, 0.12F + intensity * (blockModel ? 0.82F : 0.66F) + stack.instability() * 0.08F);
            if (!stack.extreme(CorruptionSurface.MODEL_GEOMETRY)
                    && stack.unit(CorruptionSurface.MODEL_GEOMETRY, targetId, bucket ^ effectHash) > chance) {
                return quad;
            }

            int[] vertices = original.clone();
            float centerX = 0.0F;
            float centerY = 0.0F;
            float centerZ = 0.0F;
            int vertexCount = Math.max(1, vertices.length / 8);
            for (int vertex = 0; vertex + 2 < vertices.length; vertex += 8) {
                centerX += Float.intBitsToFloat(vertices[vertex]);
                centerY += Float.intBitsToFloat(vertices[vertex + 1]);
                centerZ += Float.intBitsToFloat(vertices[vertex + 2]);
            }
            centerX /= vertexCount;
            centerY /= vertexCount;
            centerZ /= vertexCount;

            long seed = stack.stableLong(CorruptionSurface.MODEL_GEOMETRY, targetId, bucket ^ effectHash);
            float modelScale = blockModel ? 1.0F : 0.64F;
            float extremeBoost = stack.extreme(CorruptionSurface.MODEL_GEOMETRY) ? 3.85F : 1.0F;
            float offsetSpan = (0.035F + intensity * 0.72F) * modelScale * extremeBoost;
            float scaleSpan = (0.12F + intensity * (blockModel ? 3.75F : 2.15F)) * extremeBoost;
            float shearSpan = (0.02F + intensity * 0.68F) * modelScale * extremeBoost;
            float xOffset = signedHash(seed ^ 0x584F46465345544CL, offsetSpan);
            float yOffset = signedHash(seed ^ 0x594F46465345544CL, offsetSpan);
            float zOffset = signedHash(seed ^ 0x5A4F46465345544CL, offsetSpan);
            float xScale = geometryScale(seed ^ 0x585343414C45L, scaleSpan);
            float yScale = geometryScale(seed ^ 0x595343414C45L, scaleSpan);
            float zScale = geometryScale(seed ^ 0x5A5343414C45L, scaleSpan);
            if (blockModel && unitHash(seed ^ 0x57494445424C4BL) < 0.18F + intensity * 0.52F) {
                int wideAxis = Math.floorMod((int) (seed >>> 37), 3);
                float wideScale = 2.0F + unitHash(seed ^ 0x574944455343L) * (stack.extreme(CorruptionSurface.MODEL_GEOMETRY) ? 10.0F : 5.0F);
                if (wideAxis == 0) {
                    xScale = Math.copySign(wideScale, xScale);
                } else if (wideAxis == 1) {
                    yScale = Math.copySign(wideScale, yScale);
                } else {
                    zScale = Math.copySign(wideScale, zScale);
                }
            }
            float xyShear = signedHash(seed ^ 0x58595348454152L, shearSpan);
            float yzShear = signedHash(seed ^ 0x595A5348454152L, shearSpan);
            float zxShear = signedHash(seed ^ 0x5A585348454152L, shearSpan);
            float vertexWarpSpan = (0.04F + intensity * (blockModel ? 0.54F : 0.32F)) * modelScale * (stack.extreme(CorruptionSurface.MODEL_GEOMETRY) ? 2.8F : 1.0F);

            for (int vertex = 0; vertex + 2 < vertices.length; vertex += 8) {
                int ordinal = vertex / 8;
                long vertexSeed = mixLong(seed ^ (long) ordinal * 0x9E3779B97F4A7C15L);
                float x = Float.intBitsToFloat(vertices[vertex]) - centerX;
                float y = Float.intBitsToFloat(vertices[vertex + 1]) - centerY;
                float z = Float.intBitsToFloat(vertices[vertex + 2]) - centerZ;
                float vertexWarp = (stack.extreme(CorruptionSurface.MODEL_GEOMETRY) || unitHash(vertexSeed) < intensity * 0.42F)
                        ? signedHash(vertexSeed ^ 0x56455254L, vertexWarpSpan)
                        : 0.0F;
                float warpedX = centerX + x * xScale + y * xyShear + vertexWarp + xOffset;
                float warpedY = centerY + y * yScale + z * yzShear + signedHash(vertexSeed ^ 0x594A4954544552L, Math.abs(vertexWarp)) + yOffset;
                float warpedZ = centerZ + z * zScale + x * zxShear + signedHash(vertexSeed ^ 0x5A4A4954544552L, Math.abs(vertexWarp)) + zOffset;
                vertices[vertex] = Float.floatToRawIntBits(clampFloat(warpedX, -12.0F, 13.0F));
                vertices[vertex + 1] = Float.floatToRawIntBits(clampFloat(warpedY, -12.0F, 13.0F));
                vertices[vertex + 2] = Float.floatToRawIntBits(clampFloat(warpedZ, -12.0F, 13.0F));
            }

            return new BakedQuad(vertices, quad.getTintIndex(), quad.getDirection(), quad.getSprite(), quad.isShade());
        }

        private boolean corruptedRenderFlag(String flag, boolean original) {
            CorruptionEffectStack stack = ClientCorruptionEffects.current();
            if (flag.startsWith("ambient_occlusion")) {
                if (!stack.activeOrExtreme(CorruptionSurface.LIGHT_FIELD) && !stack.activeOrExtreme(CorruptionSurface.WORLD_RENDER)) {
                    return original;
                }
                String targetId = modelId + ":smooth_lighting:" + flag;
                float intensity = stack.extreme(CorruptionSurface.LIGHT_FIELD)
                        ? 1.0F
                        : Math.max(stack.targetIntensity(CorruptionSurface.LIGHT_FIELD, targetId), stack.intensity(CorruptionSurface.LIGHT_FIELD) * 0.90F);
                intensity = Math.max(intensity, stack.intensity(CorruptionSurface.WORLD_RENDER) * 0.45F);
                long hash = stack.stableLong(CorruptionSurface.LIGHT_FIELD, targetId, effectHash);
                float chance = Mth.clamp(0.10F + intensity * 0.84F + stack.instability() * 0.10F, 0.0F, 0.96F);
                if (unitHash(hash ^ 0x464C4950L) > chance) {
                    return original;
                }
                return (hash & 1L) == 0L ? !original : unitHash(hash ^ 0x464F5243L) < 0.5F;
            }
            if (!stack.extreme(CorruptionSurface.MODEL_GEOMETRY)) {
                return original;
            }
            long hash = stack.stableLong(CorruptionSurface.MODEL_GEOMETRY, modelId + ":render_flag:" + flag, effectHash);
            return (hash & 1L) == 0L;
        }

        private void corruptItemTransform(ItemDisplayContext transformType, PoseStack poseStack) {
            CorruptionEffectStack stack = ClientCorruptionEffects.current();
            if (!stack.extreme(CorruptionSurface.MODEL_GEOMETRY) || transformType == ItemDisplayContext.GUI) {
                return;
            }

            String targetId = modelId + ":display:" + transformType.name();
            long hash = stack.stableLong(CorruptionSurface.MODEL_GEOMETRY, targetId, effectHash);
            float shift = blockModel ? 1.30F : 0.75F;
            poseStack.translate(
                    signedHash(hash ^ 0x584F46465345544CL, shift),
                    signedHash(hash ^ 0x594F46465345544CL, shift * 1.10F),
                    signedHash(hash ^ 0x5A4F46465345544CL, shift * 0.85F)
            );
            poseStack.mulPose(Axis.XP.rotationDegrees(signedHash(hash ^ 0x58524F54415445L, 150.0F)));
            poseStack.mulPose(Axis.YP.rotationDegrees(signedHash(hash ^ 0x59524F54415445L, 170.0F)));
            poseStack.mulPose(Axis.ZP.rotationDegrees(signedHash(hash ^ 0x5A524F54415445L, 160.0F)));
            float xScale = extremeScale(hash ^ 0x585343414C45L);
            float yScale = extremeScale(hash ^ 0x595343414C45L);
            float zScale = extremeScale(hash ^ 0x5A5343414C45L);
            poseStack.scale(xScale, yScale, zScale);
        }

        private void clearCache() {
            textureLeakedQuadsByBucket.clear();
            modelGeometryQuadsByBucket.clear();
        }

        private BakedModel wrapResolvedOverride(@Nullable BakedModel model, ItemStack stack, int overrideIndex) {
            if (model == null || model == delegate) {
                return this;
            }
            if (model instanceof CorruptedItemBakedModel) {
                return model;
            }
            String itemId = itemTargetId(stack);
            String id = modelId + ":override:" + itemId + ":" + Math.max(0, overrideIndex);
            return RUNTIME_ITEM_MODEL_WRAPPERS.computeIfAbsent(model, key -> new CorruptedItemBakedModel(key, id, false, false));
        }

    }

    private static final class CorruptedItemOverrides extends ItemOverrides {
        private final CorruptedItemBakedModel owner;
        private final ItemOverrides delegate;
        private final List<BakedModel> overrideModels;

        private CorruptedItemOverrides(CorruptedItemBakedModel owner, ItemOverrides delegate) {
            this.owner = owner;
            this.delegate = delegate == null ? ItemOverrides.EMPTY : delegate;
            this.overrideModels = readOverrideModels(this.delegate);
        }

        @Override
        public @Nullable BakedModel resolve(@NotNull BakedModel model, @NotNull ItemStack stack, @Nullable ClientLevel level, @Nullable LivingEntity entity, int seed) {
            BakedModel base = owner.delegate == null ? model : owner.delegate;
            BakedModel resolved = delegate.resolve(base, stack, level, entity, seed);
            int resolvedIndex = indexOfModel(resolved);
            BakedModel selected = corruptOverrideChoice(resolved, resolvedIndex, stack, level, entity, seed);
            int selectedIndex = indexOfModel(selected);
            return owner.wrapResolvedOverride(selected, stack, selectedIndex);
        }

        private BakedModel corruptOverrideChoice(@Nullable BakedModel resolved, int resolvedIndex, ItemStack itemStack, @Nullable ClientLevel level, @Nullable LivingEntity entity, int renderSeed) {
            if (overrideModels.size() < 2 || itemStack == null || itemStack.isEmpty()) {
                return resolved;
            }

            CorruptionEffectStack stack = ClientCorruptionEffects.current();
            String targetId = "item_override_state:" + itemTargetId(itemStack);
            if (!stack.activeOrExtreme(CorruptionSurface.INTERACTION_ROUTING, targetId)
                    && !stack.activeOrExtreme(CorruptionSurface.INTERACTION_ROUTING)) {
                return resolved;
            }

            float intensity = stack.extreme(CorruptionSurface.INTERACTION_ROUTING)
                    ? 1.0F
                    : Mth.clamp(Math.max(
                            stack.targetIntensity(CorruptionSurface.INTERACTION_ROUTING, targetId),
                            stack.intensity(CorruptionSurface.INTERACTION_ROUTING) * 0.78F
                    ) + stack.instability() * 0.08F, 0.0F, 1.0F);
            if (intensity <= 0.025F) {
                return resolved;
            }

            long clock = overrideClock(level, entity, intensity);
            int salt = itemOverrideSalt(itemStack, renderSeed) ^ (int) clock;
            long hash = stack.stableLong(CorruptionSurface.INTERACTION_ROUTING, targetId, salt);
            float chance = stack.extreme(CorruptionSurface.INTERACTION_ROUTING)
                    ? 0.98F
                    : Mth.clamp(0.06F + intensity * 0.74F + stack.instability() * 0.08F, 0.0F, 0.92F);
            if (!stack.extreme(CorruptionSurface.INTERACTION_ROUTING)
                    && stack.unit(CorruptionSurface.INTERACTION_ROUTING, targetId + ":override_gate", salt) > chance) {
                return resolved;
            }

            int count = overrideModels.size();
            int mode = Math.floorMod((int) (hash >>> 29), 7);
            int selectedIndex = switch (mode) {
                case 0 -> resolvedIndex >= 0 ? count - 1 - resolvedIndex : count - 1;
                case 1 -> 0;
                case 2 -> count - 1;
                case 3 -> Math.floorMod(resolvedIndex + 1, count);
                default -> CorruptionValueMutator.selectIndex(stack, CorruptionSurface.INTERACTION_ROUTING, targetId + ":override_pick", salt ^ 0x4F5652, count);
            };
            if (selectedIndex == resolvedIndex && count > 1) {
                selectedIndex = Math.floorMod(selectedIndex + 1 + (int) (hash & 3L), count);
            }
            BakedModel selected = overrideModels.get(selectedIndex);
            return selected == null ? resolved : selected;
        }

        private int indexOfModel(@Nullable BakedModel model) {
            if (model == null) {
                return -1;
            }
            for (int i = 0; i < overrideModels.size(); i++) {
                if (overrideModels.get(i) == model) {
                    return i;
                }
            }
            return -1;
        }
    }

    private static List<BakedModel> readOverrideModels(ItemOverrides overrides) {
        if (overrides == null || ITEM_OVERRIDES_FIELD == null) {
            return Collections.emptyList();
        }
        try {
            Object value = ITEM_OVERRIDES_FIELD.get(overrides);
            if (value == null) {
                return Collections.emptyList();
            }
            List<BakedModel> models = new ArrayList<>();
            if (value instanceof Iterable<?> iterable) {
                for (Object override : iterable) {
                    addOverrideModel(models, override);
                }
            } else if (value.getClass().isArray()) {
                int length = Array.getLength(value);
                for (int i = 0; i < length; i++) {
                    addOverrideModel(models, Array.get(value, i));
                }
            }
            return models.size() < 2 ? Collections.emptyList() : List.copyOf(models);
        } catch (IllegalAccessException ignored) {
            return Collections.emptyList();
        }
    }

    private static void addOverrideModel(List<BakedModel> models, Object override) {
        BakedModel model = overrideModel(override);
        if (model != null && !(model instanceof CorruptedItemBakedModel)) {
            models.add(model);
        }
    }

    @Nullable
    private static BakedModel overrideModel(Object override) {
        if (override == null) {
            return null;
        }
        Field field = BAKED_OVERRIDE_MODEL_FIELDS.computeIfAbsent(override.getClass(), type -> field(type, "model", "f_173481_"));
        if (field == null) {
            return null;
        }
        try {
            Object value = field.get(override);
            return value instanceof BakedModel model ? model : null;
        } catch (IllegalAccessException ignored) {
            return null;
        }
    }

    @Nullable
    private static Field field(Class<?> type, String... names) {
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

    private static long overrideClock(@Nullable ClientLevel level, @Nullable LivingEntity entity, float intensity) {
        long gameTime = 0L;
        if (level != null) {
            gameTime = level.getGameTime();
        } else if (entity != null && entity.level() != null) {
            gameTime = entity.level().getGameTime();
        }
        long cadence = Math.max(1L, Math.round(Mth.lerp(intensity, 18.0F, 2.0F)));
        return Math.floorDiv(gameTime, cadence);
    }

    private static int itemOverrideSalt(ItemStack stack, int renderSeed) {
        int salt = renderSeed ^ 0x49544F56;
        if (stack == null || stack.isEmpty()) {
            return salt;
        }
        salt = salt * 31 + stack.getItem().hashCode();
        salt = salt * 31 + stack.getDamageValue();
        if (stack.hasTag()) {
            salt = salt * 31 + stack.getTag().hashCode();
        }
        return salt;
    }

    private static String itemTargetId(ItemStack stack) {
        ResourceLocation itemId = stack == null || stack.isEmpty() ? null : ForgeRegistries.ITEMS.getKey(stack.getItem());
        return itemId == null ? "unknown" : itemId.toString();
    }

    private static boolean sameSprite(TextureAtlasSprite first, TextureAtlasSprite second) {
        if (first == second) {
            return true;
        }
        if (first == null || second == null || first.contents() == null || second.contents() == null) {
            return false;
        }
        ResourceLocation firstName = first.contents().name();
        ResourceLocation secondName = second.contents().name();
        return firstName != null && firstName.equals(secondName);
    }

    private static int spriteInsertionIndex(ResourceLocation id) {
        return spriteInsertionIndex(SPRITE_POOL, id);
    }

    private static int spriteInsertionIndex(List<TextureAtlasSprite> sprites, ResourceLocation id) {
        String key = id.toString();
        int low = 0;
        int high = sprites.size();
        while (low < high) {
            int mid = (low + high) >>> 1;
            TextureAtlasSprite sprite = sprites.get(mid);
            ResourceLocation existing = sprite == null || sprite.contents() == null ? null : sprite.contents().name();
            String existingKey = existing == null ? "" : existing.toString();
            if (existingKey.compareTo(key) < 0) {
                low = mid + 1;
            } else {
                high = mid;
            }
        }
        return low;
    }

    private static TextureAtlasSprite replacementSpriteFromPool(TextureAtlasSprite original, CorruptionEffectStack stack, String targetId, int salt, List<TextureAtlasSprite> sprites) {
        if (sprites == null || sprites.size() < 2) {
            return original;
        }
        int index = stack.extreme(CorruptionSurface.TEXTURE_MEMORY)
                ? Math.floorMod(stableHash(targetId, ITEM_TEXTURE_SEED ^ stack.stableLong(CorruptionSurface.TEXTURE_MEMORY, targetId, salt)), sprites.size())
                : CorruptionValueMutator.selectIndex(stack, CorruptionSurface.TEXTURE_MEMORY, targetId, salt, sprites.size());
        TextureAtlasSprite replacement = sprites.get(index);
        if (sameSprite(original, replacement)) {
            replacement = sprites.get((index + 1) % sprites.size());
        }
        return replacement == null ? original : replacement;
    }

    private static float signedHash(long hash, float amplitude) {
        return (unitHash(hash) * 2.0F - 1.0F) * amplitude;
    }

    private static float extremeScale(long hash) {
        float scale = 0.10F + unitHash(hash) * 3.65F;
        return unitHash(hash ^ 0x494E56455254L) < 0.36F ? -scale : scale;
    }

    private static float geometryScale(long hash, float span) {
        float scale = 1.0F + signedHash(hash, span);
        if (Math.abs(scale) < 0.06F) {
            return Math.copySign(0.06F, scale == 0.0F ? 1.0F : scale);
        }
        return scale;
    }

    private static float clampFloat(float value, float min, float max) {
        return Math.max(min, Math.min(max, value));
    }

}
