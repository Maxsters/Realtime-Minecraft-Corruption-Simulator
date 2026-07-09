package com.maxsters.realtimeminecraftcorruptionsimulator.mechanics;

import com.maxsters.realtimeminecraftcorruptionsimulator.RealtimeMinecraftCorruptionSimulator;
import com.maxsters.realtimeminecraftcorruptionsimulator.config.GlobalCorruptionSettings;
import com.maxsters.realtimeminecraftcorruptionsimulator.network.ModNetwork;
import com.maxsters.realtimeminecraftcorruptionsimulator.profile.CorruptionEffectStack;
import com.maxsters.realtimeminecraftcorruptionsimulator.profile.CorruptionSurface;
import com.maxsters.realtimeminecraftcorruptionsimulator.profile.CorruptionTarget;
import com.maxsters.realtimeminecraftcorruptionsimulator.profile.CorruptionValueMutator;
import com.maxsters.realtimeminecraftcorruptionsimulator.runtime.CorruptionRuntimeManager;
import com.maxsters.realtimeminecraftcorruptionsimulator.state.CorruptionSavedData;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.SectionPos;
import net.minecraft.network.protocol.game.ClientboundContainerSetSlotPacket;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.tags.DamageTypeTags;
import net.minecraft.tags.FluidTags;
import net.minecraft.tags.TagKey;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityDimensions;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.MoverType;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.projectile.Projectile;
import net.minecraft.world.entity.vehicle.Boat;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.CraftingContainer;
import net.minecraft.world.inventory.ResultContainer;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.trading.Merchant;
import net.minecraft.world.item.trading.MerchantOffer;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.FallingBlock;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.LevelChunk;
import net.minecraft.world.level.material.Fluid;
import net.minecraft.world.level.material.FluidState;
import net.minecraft.world.level.material.Fluids;
import net.minecraft.world.level.storage.ServerLevelData;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.entity.EntityJoinLevelEvent;
import net.minecraftforge.event.entity.ProjectileImpactEvent;
import net.minecraftforge.event.entity.living.LivingEvent;
import net.minecraftforge.event.entity.living.LivingHurtEvent;
import net.minecraftforge.event.entity.living.MobSpawnEvent;
import net.minecraftforge.event.entity.player.PlayerEvent;
import net.minecraftforge.event.entity.player.PlayerInteractEvent;
import net.minecraftforge.event.level.LevelEvent;
import net.minecraftforge.event.level.BlockEvent;
import net.minecraftforge.event.server.ServerStartingEvent;
import net.minecraftforge.event.server.ServerStoppedEvent;
import net.minecraftforge.event.server.ServerStoppingEvent;
import net.minecraftforge.eventbus.api.Event;
import net.minecraftforge.eventbus.api.EventPriority;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.loading.FMLEnvironment;
import net.minecraftforge.registries.ForgeRegistries;

import java.lang.reflect.Method;
import java.util.ArrayDeque;
import java.util.HashSet;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ThreadLocalRandom;
import java.util.function.Supplier;

@Mod.EventBusSubscriber(modid = RealtimeMinecraftCorruptionSimulator.MOD_ID, bus = Mod.EventBusSubscriber.Bus.FORGE)
public final class CorruptionMechanicsManager {
    private static final Supplier<CorruptionEffectStack> CLIENT_GAMEPLAY_STACK_SUPPLIER = createClientGameplayStackSupplier();
    private static final int SERVER_DIMENSION_MUTATION_WARMUP_TICKS = 120;
    private static final int SERVER_LEVEL_LOAD_MUTATION_WARMUP_TICKS = 80;
    private static final int SERVER_SAVE_MUTATION_COOLDOWN_TICKS = 80;
    private static final int MAX_WORLD_PROCESS_MUTATIONS_PER_TICK = 12;
    private static final int MAX_FLUID_SOURCE_ALLOWS_PER_TICK = 2;
    private static final int MIN_PERSISTENT_TERRAIN_CORRUPTION_LEVEL = 1;
    private static final int MAX_PERSISTENT_TERRAIN_CORRUPTION_LEVEL = 100;
    private static final float MIN_PERSISTENT_TERRAIN_INTENSITY = 0.006F;
    private static final float MIN_RUNTIME_MECHANICS_INTENSITY = 0.00025F;
    private static final float MIN_WORLD_PROCESS_INTENSITY = MIN_RUNTIME_MECHANICS_INTENSITY;
    private static final float MIN_PLAYER_MECHANICS_INTENSITY = MIN_RUNTIME_MECHANICS_INTENSITY;
    private static final float MIN_ENTITY_MECHANICS_INTENSITY = MIN_RUNTIME_MECHANICS_INTENSITY;
    private static final float MIN_SPAWN_MECHANICS_INTENSITY = MIN_RUNTIME_MECHANICS_INTENSITY;
    private static final float MIN_LIQUID_MECHANICS_INTENSITY = MIN_RUNTIME_MECHANICS_INTENSITY;
    private static final float MIN_AIR_MECHANICS_INTENSITY = MIN_RUNTIME_MECHANICS_INTENSITY;
    private static final float MIN_COLLISION_MECHANICS_INTENSITY = MIN_RUNTIME_MECHANICS_INTENSITY;
    private static final float MIN_DAY_TIME_INTENSITY = MIN_RUNTIME_MECHANICS_INTENSITY;
    private static final float MIN_FIRE_MECHANICS_INTENSITY = MIN_RUNTIME_MECHANICS_INTENSITY;
    private static final float MIN_POWDER_SNOW_MECHANICS_INTENSITY = MIN_RUNTIME_MECHANICS_INTENSITY;
    private static final String BLOCK_BREAK_SPEED_TARGET_ID = "block_break_speed:global";
    private static final int MAX_CORRUPTED_NO_PHYSICS_TICKS = 24;
    private static volatile boolean serverMutationSuspended;
    private static volatile long serverMutationResumeTick;
    private static long worldProcessBudgetTick = Long.MIN_VALUE;
    private static int worldProcessBudgetUsed;
    private static long fluidSourceAllowBudgetTick = Long.MIN_VALUE;
    private static int fluidSourceAllowsUsed;
    private static int cachedServerIdentity;
    private static long cachedServerStackTick = Long.MIN_VALUE;
    private static CorruptionEffectStack cachedServerStack = CorruptionEffectStack.local(0);
    private static final ArrayDeque<TerrainMutationRequest> pendingTerrainMutations = new ArrayDeque<>();
    private static final Set<String> pendingTerrainMutationKeys = new HashSet<>();

    private CorruptionMechanicsManager() {
    }

    public static int corruptFireTicks(Entity entity, int requestedTicks) {
        if (entity == null) {
            return requestedTicks;
        }
        CorruptionEffectStack stack = authoritativeGameplayStackFor(entity);
        String targetId = "fire_ticks:" + entityTargetId(entity);
        if (!fireMechanicsActive(stack, targetId)) {
            return requestedTicks;
        }

        float intensity = fireMechanicsIntensity(stack, targetId);
        long seed = stack.stableLong(CorruptionSurface.FIRE_MECHANICS, targetId, entityStableSalt(entity, requestedTicks ^ 0x46545254));
        int bucket = collisionPositionBucket(entity, seed, intensity);
        float chance = stack.extreme(CorruptionSurface.FIRE_MECHANICS)
                ? 0.94F
                : Mth.clamp(0.08F + intensity * 0.66F + stack.instability() * 0.10F, 0.0F, 0.88F);
        if (unitHash(seed ^ bucket ^ 0x5449434BL) > chance) {
            return requestedTicks;
        }

        int currentTicks = entity.getRemainingFireTicks();
        int maxTicks = stack.extreme(CorruptionSurface.FIRE_MECHANICS)
                ? 72_000
                : Math.max(400, 1_200 + Math.round(intensity * 18_000.0F));
        int mode = Math.floorMod((int) (seed >>> 28), 7);
        int mutated = switch (mode) {
            case 0 -> 0;
            case 1 -> Math.max(requestedTicks, currentTicks) + 1 + Math.round(unitHash(seed ^ 0x4255524EL) * (80.0F + intensity * 1_600.0F));
            case 2 -> Math.round(requestedTicks * (0.02F + unitHash(seed ^ 0x534C4F57L) * 0.24F));
            case 3 -> Math.round(requestedTicks * (2.0F + unitHash(seed ^ 0x46415354L) * (12.0F + intensity * 72.0F)));
            case 4 -> -Math.round(20.0F + unitHash(seed ^ 0x434F4F4CL) * (60.0F + intensity * 300.0F));
            case 5 -> currentTicks + Math.round(signedUnit(seed ^ 0x44524946L) * (40.0F + intensity * 900.0F));
            default -> requestedTicks + Math.round(signedUnit(seed ^ 0x53544154L) * (24.0F + intensity * 420.0F));
        };
        return clampInt(mutated, -400, maxTicks);
    }

    public static boolean corruptIsOnFire(Entity entity, boolean original) {
        if (entity == null) {
            return original;
        }
        CorruptionEffectStack stack = authoritativeGameplayStackFor(entity);
        String targetId = "fire_state:" + entityTargetId(entity);
        if (!fireMechanicsActive(stack, targetId)) {
            return original;
        }

        float intensity = fireMechanicsIntensity(stack, targetId);
        long seed = stack.stableLong(CorruptionSurface.FIRE_MECHANICS, targetId, entityStableSalt(entity, entity.getRemainingFireTicks() ^ 0x46535441));
        int bucket = collisionPositionBucket(entity, seed, intensity);
        if (original) {
            return unitHash(seed ^ bucket ^ 0x46414C53L) >= Mth.clamp(0.06F + intensity * 0.42F, 0.0F, 0.78F);
        }
        if (entity.getRemainingFireTicks() <= 0 && !isNearBlock(entity, Blocks.FIRE, 1)) {
            return false;
        }
        return unitHash(seed ^ bucket ^ 0x54525545L) < Mth.clamp(0.04F + intensity * 0.26F, 0.0F, 0.46F);
    }

    public static boolean shouldDisableSunBurn(Mob mob) {
        if (mob == null) {
            return false;
        }
        CorruptionEffectStack stack = authoritativeGameplayStackFor(mob);
        String targetId = "sunlight_burn:" + entityTargetId(mob);
        if (!fireMechanicsActive(stack, targetId)) {
            return false;
        }

        float intensity = fireMechanicsIntensity(stack, targetId);
        long seed = stack.stableLong(CorruptionSurface.FIRE_MECHANICS, targetId, entityStableSalt(mob, 0x53554E));
        int bucket = collisionPositionBucket(mob, seed, intensity);
        float chance = stack.extreme(CorruptionSurface.FIRE_MECHANICS)
                ? 0.98F
                : Mth.clamp(0.12F + intensity * 0.76F + stack.instability() * 0.08F, 0.0F, 0.92F);
        return unitHash(seed ^ bucket ^ 0x4255524EL) < chance;
    }

    public static boolean shouldCancelFireBlockTick(BlockState state, ServerLevel level, BlockPos pos, RandomSource random) {
        if (state == null || level == null || pos == null) {
            return false;
        }
        CorruptionEffectStack stack = serverStack(level);
        String targetId = "fire_spread:" + blockTargetId(state);
        if (!fireMechanicsActive(stack, targetId)) {
            return false;
        }

        float intensity = fireMechanicsIntensity(stack, targetId);
        long hash = stack.stableLong(CorruptionSurface.FIRE_MECHANICS, targetId, 0x46495245)
                ^ mixLong(pos.asLong() ^ (level.getGameTime() / Math.max(1, Math.round(8.0F + (1.0F - intensity) * 36.0F))));
        float chance = stack.extreme(CorruptionSurface.FIRE_MECHANICS)
                ? 0.94F
                : Mth.clamp(0.06F + intensity * 0.68F + stack.instability() * 0.10F, 0.0F, 0.86F);
        if (unitHash(hash ^ 0x5449434BL) > chance || !claimWorldProcessMutation(level)) {
            return false;
        }

        int mode = Math.floorMod((int) (hash >>> 30), 5);
        if (mode == 0) {
            level.removeBlock(pos, false);
        } else if (mode == 1) {
            placeCorruptedFire(level, pos, hash, intensity);
        } else if (mode == 2) {
            int delay = 1 + Math.round(unitHash(hash ^ 0x44454C41L) * (6.0F + intensity * 220.0F));
            level.scheduleTick(pos, state.getBlock(), delay);
        }
        return true;
    }

    public static boolean corruptCanFreeze(LivingEntity entity, boolean original) {
        if (entity == null) {
            return original;
        }
        CorruptionEffectStack stack = authoritativeGameplayStackFor(entity);
        String targetId = "powder_snow_can_freeze:" + entityTargetId(entity);
        if (!powderSnowMechanicsActive(stack, targetId)) {
            return original;
        }

        boolean nearPowderSnow = isNearBlock(entity, Blocks.POWDER_SNOW, 1) || entity.getTicksFrozen() > 0;
        if (!nearPowderSnow) {
            return original;
        }

        float intensity = powderSnowMechanicsIntensity(stack, targetId);
        long seed = stack.stableLong(CorruptionSurface.POWDER_SNOW_MECHANICS, targetId, entityStableSalt(entity, 0x46525A));
        int bucket = collisionPositionBucket(entity, seed, intensity);
        float chance = original
                ? Mth.clamp(0.08F + intensity * 0.54F, 0.0F, 0.82F)
                : Mth.clamp(0.04F + intensity * 0.38F, 0.0F, 0.62F);
        return unitHash(seed ^ bucket ^ 0x43414E46L) < chance ? !original : original;
    }

    public static int corruptTicksFrozen(Entity entity, int requestedTicks) {
        if (entity == null) {
            return requestedTicks;
        }
        CorruptionEffectStack stack = authoritativeGameplayStackFor(entity);
        String targetId = "powder_snow_ticks:" + entityTargetId(entity);
        if (!powderSnowMechanicsActive(stack, targetId)) {
            return requestedTicks;
        }

        boolean nearPowderSnow = requestedTicks > 0 || entity.getTicksFrozen() > 0 || isNearBlock(entity, Blocks.POWDER_SNOW, 1);
        if (!nearPowderSnow) {
            return requestedTicks;
        }

        float intensity = powderSnowMechanicsIntensity(stack, targetId);
        long seed = stack.stableLong(CorruptionSurface.POWDER_SNOW_MECHANICS, targetId, entityStableSalt(entity, requestedTicks ^ 0x46525A));
        int bucket = collisionPositionBucket(entity, seed, intensity);
        float chance = stack.extreme(CorruptionSurface.POWDER_SNOW_MECHANICS)
                ? 0.96F
                : Mth.clamp(0.08F + intensity * 0.70F + stack.instability() * 0.08F, 0.0F, 0.90F);
        if (unitHash(seed ^ bucket ^ 0x5449434BL) > chance) {
            return requestedTicks;
        }

        int currentTicks = entity.getTicksFrozen();
        int requiredTicks = Math.max(1, entity.getTicksRequiredToFreeze());
        int maxTicks = stack.extreme(CorruptionSurface.POWDER_SNOW_MECHANICS)
                ? Math.max(requiredTicks * 12, requiredTicks + 2_400)
                : Math.max(requiredTicks + 80, requiredTicks + Math.round(intensity * requiredTicks * 8.0F));
        int mode = Math.floorMod((int) (seed >>> 29), 7);
        int mutated = switch (mode) {
            case 0 -> 0;
            case 1 -> Math.max(currentTicks, requestedTicks) + 1 + Math.round(unitHash(seed ^ 0x46415354L) * (20.0F + intensity * requiredTicks * 4.0F));
            case 2 -> Math.round(requestedTicks * (0.02F + unitHash(seed ^ 0x534C4F57L) * 0.30F));
            case 3 -> requiredTicks + Math.round(unitHash(seed ^ 0x46554C4CL) * (requiredTicks + intensity * requiredTicks * 8.0F));
            case 4 -> currentTicks - Math.round(1.0F + unitHash(seed ^ 0x54484157L) * (12.0F + intensity * 120.0F));
            case 5 -> Math.round(currentTicks + signedUnit(seed ^ 0x44524946L) * (12.0F + intensity * requiredTicks * 3.0F));
            default -> requestedTicks + Math.round(signedUnit(seed ^ 0x53544154L) * (8.0F + intensity * requiredTicks * 2.0F));
        };
        return clampInt(mutated, 0, maxTicks);
    }

    public static boolean corruptPowderSnowDetection(Entity entity, boolean inPowderSnow) {
        if (entity == null) {
            return inPowderSnow;
        }
        CorruptionEffectStack stack = authoritativeGameplayStackFor(entity);
        String targetId = "powder_snow_detection:" + entityTargetId(entity);
        if (!powderSnowMechanicsActive(stack, targetId)) {
            return inPowderSnow;
        }

        boolean nearPowderSnow = inPowderSnow || entity.getTicksFrozen() > 0 || isNearBlock(entity, Blocks.POWDER_SNOW, 1);
        if (!nearPowderSnow) {
            return inPowderSnow;
        }

        float intensity = powderSnowMechanicsIntensity(stack, targetId);
        long seed = stack.stableLong(CorruptionSurface.POWDER_SNOW_MECHANICS, targetId, entityStableSalt(entity, inPowderSnow ? 0x494E : 0x4F5554));
        int bucket = collisionPositionBucket(entity, seed, intensity);
        float chance = stack.extreme(CorruptionSurface.POWDER_SNOW_MECHANICS)
                ? 0.92F
                : Mth.clamp(0.08F + intensity * 0.62F + stack.instability() * 0.08F, 0.0F, 0.84F);
        if (unitHash(seed ^ bucket ^ 0x44455445L) > chance) {
            return inPowderSnow;
        }

        int mode = Math.floorMod((int) (seed >>> 31), 4);
        return switch (mode) {
            case 0 -> false;
            case 1 -> true;
            case 2 -> !inPowderSnow;
            default -> inPowderSnow;
        };
    }

    @SubscribeEvent
    public static void onServerStarting(ServerStartingEvent event) {
        serverMutationSuspended = false;
        serverMutationResumeTick = 0L;
        clearServerStackCache();
        clearEntityRuntimeCaches();
        resetTerrainMutationBudget();
        resetWorldProcessBudget();
        clearPendingTerrainMutations();
    }

    @SubscribeEvent
    public static void onServerStopping(ServerStoppingEvent event) {
        serverMutationSuspended = true;
        clearServerStackCache();
        clearEntityRuntimeCaches();
        clearPendingTerrainMutations();
    }

    @SubscribeEvent
    public static void onServerStopped(ServerStoppedEvent event) {
        serverMutationSuspended = false;
        serverMutationResumeTick = 0L;
        clearServerStackCache();
        clearEntityRuntimeCaches();
        resetTerrainMutationBudget();
        resetWorldProcessBudget();
        clearPendingTerrainMutations();
    }

    public static void onGlobalSettingsApplied(MinecraftServer server) {
        serverMutationSuspended = false;
        if (server != null) {
            CorruptionSavedData data = CorruptionSavedData.get(server);
            if (!weatherCorruptionConfigured(GlobalCorruptionSettings.activeLevel(), GlobalCorruptionSettings.enabledTargetsMask())) {
                restoreCapturedWeather(server, data);
            }
            CorruptionRuntimeManager.copyGlobalSettingsToData(data);
            serverMutationResumeTick = Math.min(serverMutationResumeTick, server.getTickCount());
        }
        resetAutoIncreaseTimer(server);
        resetSeedRandomizerTimer(server);
        clearServerStackCache();
        clearEntityRuntimeCaches();
        resetTerrainMutationBudget();
        resetWorldProcessBudget();
        clearPendingTerrainMutations();
    }

    public static boolean corruptFluidDetection(Entity entity, String check, boolean original) {
        if (entity == null || entity.level() == null || entity.isSpectator()) {
            return original;
        }

        CorruptionEffectStack stack = mobilityGameplayStackFor(entity);
        String targetId = "fluid_detection:" + check + ":" + entityTargetId(entity);
        if (!liquidMechanicsActive(stack, targetId)) {
            return original;
        }

        CorruptionSurface surface = liquidSurface(stack, targetId);
        float intensity = liquidMechanicsIntensity(stack, targetId);
        long faultSeed = fluidFeatureSeed(stack, surface, targetId);
        int bucket = fluidPositionBucket(entity, faultSeed, intensity);
        float detectorScale = fluidDetectorScale(faultSeed, intensity);
        float chance = stack.extreme(surface)
                ? Mth.clamp(0.54F + detectorScale * 0.36F, 0.0F, 0.96F)
                : Mth.clamp((0.08F + intensity * 0.74F + stack.instability() * 0.08F) * detectorScale, 0.0F, 0.90F);
        if (stack.unit(surface, targetId + ":detector_path", bucket) >= chance) {
            return original;
        }

        TagKey<Fluid> fluidTag = fluidTagForCheck(check);
        boolean eyeOnly = check.startsWith("eye:") || check.contains("underwater");
        return sampleCorruptedFluid(entity, fluidTag, eyeOnly, faultSeed, intensity);
    }

    public static boolean corruptFluidPush(Entity entity, boolean original) {
        if (entity == null || entity.level() == null || entity.isSpectator()) {
            return original;
        }

        CorruptionEffectStack stack = mobilityGameplayStackFor(entity);
        String targetId = "fluid_collision:" + entityTargetId(entity);
        if (!liquidMechanicsActive(stack, targetId)) {
            return original;
        }

        CorruptionSurface surface = liquidSurface(stack, targetId);
        float intensity = liquidMechanicsIntensity(stack, targetId);
        long faultSeed = fluidFeatureSeed(stack, surface, targetId);
        int bucket = fluidPositionBucket(entity, faultSeed, intensity);
        float flowScale = fluidFlowScale(faultSeed, intensity);
        float chance = stack.extreme(surface)
                ? Mth.clamp(0.46F + flowScale * 0.34F, 0.0F, 0.92F)
                : Mth.clamp((0.08F + intensity * 0.58F + stack.instability() * 0.08F) * flowScale, 0.0F, 0.82F);
        if (stack.unit(surface, targetId + ":push_feature", bucket) >= chance) {
            return original;
        }
        return stack.unit(surface, targetId + ":push_disabled", bucket ^ 0x50555348) >= 0.50F && original;
    }

    public static void corruptSwimmingState(Entity entity) {
        if (entity == null || entity.level() == null || entity.isSpectator()) {
            return;
        }

        CorruptionEffectStack stack = mobilityGameplayStackFor(entity);
        String targetId = "swimming_state:" + entityTargetId(entity);
        if (!liquidMechanicsActive(stack, targetId)) {
            return;
        }

        CorruptionSurface surface = liquidSurface(stack, targetId);
        float intensity = liquidMechanicsIntensity(stack, targetId);
        long faultSeed = fluidFeatureSeed(stack, surface, targetId);
        int bucket = fluidPositionBucket(entity, faultSeed, intensity);
        float detectorScale = fluidDetectorScale(faultSeed, intensity);
        float chance = stack.extreme(surface)
                ? Mth.clamp(0.60F + detectorScale * 0.28F, 0.0F, 0.94F)
                : Mth.clamp((0.05F + intensity * 0.58F + stack.instability() * 0.08F) * detectorScale, 0.0F, 0.78F);
        if (stack.unit(surface, targetId + ":swim_gate", bucket) >= chance) {
            return;
        }

        boolean corruptedWater = sampleCorruptedFluid(entity, FluidTags.WATER, false, faultSeed, intensity);
        boolean brokenState = corruptedWater && stack.unit(surface, targetId + ":force_swim", bucket ^ 0x5357494D) < 0.62F;
        entity.setSwimming(brokenState);
    }

    public static Vec3 corruptCollisionResolution(Entity entity, Vec3 requested, Vec3 resolved) {
        if (entity == null || entity.level() == null || entity.isSpectator() || requested == null || resolved == null) {
            return resolved;
        }

        CorruptionEffectStack stack = mobilityGameplayStackFor(entity);
        String targetId = "collision_resolution:" + entityTargetId(entity);
        if (!collisionMechanicsActive(stack, targetId)) {
            return resolved;
        }

        float intensity = collisionMechanicsIntensity(stack, targetId, 0.82F);
        if (intensity <= 0.0F) {
            return resolved;
        }

        long seed = stack.stableLong(CorruptionSurface.BLOCK_COLLISION, targetId, entity.getId() ^ 0x43524C44);
        int bucket = collisionPositionBucket(entity, seed, intensity);
        long clock = stack.stableLong(CorruptionSurface.BLOCK_COLLISION, targetId + ":clock", bucket);
        Vec3 blocked = requested.subtract(resolved);
        Vec3 out = resolved;

        float collisionPressure = progressiveCollisionPressure(stack, intensity);
        float leakChance = Math.min(0.94F, collisionPressure * 0.76F + stack.instability() * 0.06F);
        if (stack.unit(CorruptionSurface.BLOCK_COLLISION, targetId + ":leak", bucket) < leakChance) {
            double leak = Mth.clamp(unitHash(seed ^ 0x4C45414BL) * (0.22D + intensity * 1.15D), 0.0D, 1.45D);
            out = out.add(blocked.scale(leak));
        }

        float stickChance = Math.min(0.72F, collisionPressure * 0.30F + intensity * 0.24F + stack.instability() * 0.05F);
        if (stack.unit(CorruptionSurface.BLOCK_COLLISION, targetId + ":stick", bucket ^ 0x53544943) < stickChance) {
            double stick = Mth.clamp(unitHash(seed ^ 0x535449434BL) * (0.25D + intensity * 0.82D), 0.0D, 0.96D);
            out = out.multiply(1.0D - stick, 1.0D - stick * 0.75D, 1.0D - stick);
        }

        float slingChance = Math.min(0.62F, collisionPressure * 0.42F + Math.abs(collisionPulse(seed, unitHash(seed ^ bucket))) * intensity * 0.14F);
        if (stack.unit(CorruptionSurface.BLOCK_COLLISION, targetId + ":sling", bucket ^ 0x534C494E) < slingChance) {
            Vec3 sling = mutateFeatureVelocity(stack, CorruptionSurface.BLOCK_COLLISION, targetId + ":sling_motion", requested, seed, bucket, intensity, 0.0D, 0.42D, 3.8D);
            out = out.add(sling.subtract(requested).scale(0.35D + intensity * 0.55D));
        }

        if (unitHash(seed ^ 0x43505245L) < 0.12F + intensity * 0.36F) {
            double precision = 0.015625D + unitHash(clock ^ 0x5155414EL) * intensity * 0.42D;
            out = new Vec3(quantize(out.x, precision), quantize(out.y, precision), quantize(out.z, precision));
        }

        float phasePressure = collisionPhasePressure(stack, intensity);
        float bypassChance = Math.min(0.64F, phasePressure * 0.38F + stack.instability() * phasePressure * 0.10F);
        if (stack.unit(CorruptionSurface.BLOCK_COLLISION, targetId + ":phase_through", bucket ^ 0x50484153) < bypassChance) {
            double downwardSlip = 0.0D;
            if (requested.y <= resolved.y + 1.0E-6D
                    || stack.unit(CorruptionSurface.BLOCK_COLLISION, targetId + ":void_bias", bucket ^ 0x564F4944) < phasePressure * (0.18F + intensity * 0.44F)) {
                downwardSlip = phasePressure * (0.025D + intensity * (0.12D + unitHash(seed ^ 0x534C4950L) * 1.10D));
            }
            out = requested.add(blocked.scale(0.35D + intensity * 1.25D)).add(0.0D, -downwardSlip, 0.0D);
        }

        double max = 6.0D + intensity * 18.0D;
        return new Vec3(Mth.clamp(out.x, -max, max), Mth.clamp(out.y, -max, max), Mth.clamp(out.z, -max, max));
    }

    public static boolean shouldTemporarilyBypassBlockCollision(Entity entity, MoverType moverType, Vec3 movement) {
        return corruptedBlockCollisionBypassTicks(entity, moverType, movement) > 0;
    }

    public static int corruptedBlockCollisionBypassTicks(Entity entity, MoverType moverType, Vec3 movement) {
        if (entity == null
                || entity.level() == null
                || movement == null
                || movement.lengthSqr() <= 1.0E-10D
                || entity.isSpectator()
                || moverType == MoverType.PISTON) {
            return 0;
        }

        CorruptionEffectStack stack = mobilityGameplayStackFor(entity);
        String targetId = "collision_move_bypass:" + entityTargetId(entity);
        if (!collisionMechanicsActive(stack, targetId)) {
            return 0;
        }

        float intensity = collisionMechanicsIntensity(stack, targetId, 0.86F);
        long seed = stack.stableLong(CorruptionSurface.BLOCK_COLLISION, targetId, entity.getId() ^ 0x4E4F434C);
        int bucket = collisionPositionBucket(entity, seed, intensity);
        float phasePressure = collisionPhasePressure(stack, intensity);
        float chance = Mth.clamp(phasePressure * 0.48F, 0.0F, 0.68F);
        if (movement.y < -1.0E-5D && entity.onGround()) {
            chance += phasePressure * (0.06F + intensity * 0.12F);
        }
        if (Math.abs(movement.x) > 1.0E-5D || Math.abs(movement.z) > 1.0E-5D) {
            chance += phasePressure * intensity * 0.08F;
        }
        if (stack.unit(CorruptionSurface.BLOCK_COLLISION, targetId + ":no_physics", bucket ^ moverType.ordinal()) >= Mth.clamp(chance, 0.0F, 0.94F)) {
            return 0;
        }
        int baseTicks = 1 + Math.round(phasePressure * 5.0F);
        int variableTicks = Math.round(phasePressure * (10.0F + stack.instability() * 4.0F));
        int seedTicks = Math.round(unitHash(seed ^ bucket ^ 0x5449434BL) * phasePressure * 8.0F);
        return Mth.clamp(baseTicks + variableTicks + seedTicks, 1, MAX_CORRUPTED_NO_PHYSICS_TICKS);
    }

    public static boolean shouldResolveBlockCollisionAsEmpty(Entity entity, Vec3 movement) {
        if (entity == null
                || entity.level() == null
                || movement == null
                || movement.lengthSqr() <= 1.0E-10D
                || entity.isSpectator()) {
            return false;
        }

        CorruptionEffectStack stack = mobilityGameplayStackFor(entity);
        String targetId = "collision_shape_bypass:" + entityTargetId(entity);
        if (!collisionMechanicsActive(stack, targetId)) {
            return false;
        }

        float intensity = collisionMechanicsIntensity(stack, targetId, 1.0F);
        if (intensity <= 0.0F) {
            return false;
        }
        long seed = stack.stableLong(CorruptionSurface.BLOCK_COLLISION, targetId, entity.getId() ^ 0x454D5054);
        int bucket = collisionPositionBucket(entity, seed, intensity);
        float phasePressure = collisionPhasePressure(stack, intensity);
        float chance = Mth.clamp(collisionPhaseChance(stack, intensity) * 0.82F, 0.0F, 0.72F);
        if (movement.y < -1.0E-5D) {
            chance += phasePressure * (0.04F + intensity * 0.10F);
        }
        return stack.unit(CorruptionSurface.BLOCK_COLLISION, targetId + ":empty_collision", bucket) < Mth.clamp(chance, 0.0F, 0.94F);
    }

    public static boolean shouldSuppressCorruptedWallCheck(Entity entity) {
        if (entity == null || entity.level() == null || entity.isSpectator()) {
            return false;
        }
        CorruptionEffectStack stack = mobilityGameplayStackFor(entity);
        String targetId = "collision_wall_check:" + entityTargetId(entity);
        if (!collisionMechanicsActive(stack, targetId)) {
            return false;
        }
        float intensity = collisionMechanicsIntensity(stack, targetId, 1.0F);
        long seed = stack.stableLong(CorruptionSurface.BLOCK_COLLISION, targetId, entity.getId() ^ 0x57414C4C);
        int bucket = collisionPositionBucket(entity, seed, intensity);
        float chance = Mth.clamp(collisionPhaseChance(stack, intensity) * 0.58F, 0.0F, 0.58F);
        return stack.unit(CorruptionSurface.BLOCK_COLLISION, targetId + ":suffocation_probe", bucket) < chance;
    }

    public static boolean corruptBoatUnderWater(Boat boat, boolean original) {
        return corruptFluidDetection(boat, "boat_underwater", original);
    }

    @SubscribeEvent
    public static void onLevelLoad(LevelEvent.Load event) {
        if (event.getLevel() instanceof ServerLevel level) {
            MinecraftServer server = level.getServer();
            syncServerRuntime(server);
            if (server != null) {
                CorruptionSavedData data = CorruptionSavedData.get(server);
                if (!weatherCorruptionConfigured(data.getCorruptionLevel(), data.getEnabledTargetsMask())) {
                    restoreCapturedWeather(level, data);
                }
            }
            scheduleServerMutationWarmup(level.getServer(), SERVER_LEVEL_LOAD_MUTATION_WARMUP_TICKS);
            clearServerStackCache();
            resetTerrainMutationBudget();
            resetWorldProcessBudget();
            clearPendingTerrainMutations();
        }
    }

    @SubscribeEvent(priority = EventPriority.HIGHEST)
    public static void onLevelSave(LevelEvent.Save event) {
        if (event.getLevel() instanceof ServerLevel level) {
            scheduleServerMutationWarmup(level.getServer(), SERVER_SAVE_MUTATION_COOLDOWN_TICKS);
            clearServerStackCache();
            resetTerrainMutationBudget();
            resetWorldProcessBudget();
            clearPendingTerrainMutations();
        }
    }

    @SubscribeEvent
    public static void onLevelUnload(LevelEvent.Unload event) {
        if (event.getLevel() instanceof ServerLevel level) {
            MinecraftServer server = level.getServer();
            if (server == null || server.getPlayerList().getPlayerCount() <= 0) {
                serverMutationSuspended = true;
            }
            scheduleServerMutationWarmup(server, SERVER_LEVEL_LOAD_MUTATION_WARMUP_TICKS);
        clearServerStackCache();
        resetTerrainMutationBudget();
        resetWorldProcessBudget();
        clearPendingTerrainMutations();
        }
    }

    @SubscribeEvent
    public static void onPlayerLoggedIn(PlayerEvent.PlayerLoggedInEvent event) {
        if (event.getEntity() instanceof ServerPlayer player) {
            serverMutationSuspended = false;
            syncServerRuntime(player.getServer());
            clearServerStackCache();
            forceSyncPlayerMechanics(player);
        }
    }

    @SubscribeEvent
    public static void onPlayerLoggedOut(PlayerEvent.PlayerLoggedOutEvent event) {
        MinecraftServer server = event.getEntity().getServer();
        if (server != null && server.getPlayerList().getPlayerCount() <= 1) {
            serverMutationSuspended = true;
            clearServerStackCache();
            clearEntityRuntimeCaches();
        }
    }

    @SubscribeEvent
    public static void onPlayerChangedDimension(PlayerEvent.PlayerChangedDimensionEvent event) {
        if (event.getEntity() instanceof ServerPlayer player) {
            scheduleServerMutationWarmup(player.getServer(), SERVER_DIMENSION_MUTATION_WARMUP_TICKS);
            clearServerStackCache();
            forceSyncPlayerMechanics(player);
        }
        resetTerrainMutationBudget();
        resetWorldProcessBudget();
        clearPendingTerrainMutations();
    }

    @SubscribeEvent
    public static void onPlayerClone(PlayerEvent.Clone event) {
        if (event.getEntity() instanceof ServerPlayer player) {
            forceSyncPlayerMechanics(player);
        }
    }

    @SubscribeEvent
    public static void onServerTick(TickEvent.ServerTickEvent event) {
        if (event.phase != TickEvent.Phase.END) {
            return;
        }
        applyAutoIncrease(event.getServer());
    }

    @SubscribeEvent
    public static void onLivingTick(LivingEvent.LivingTickEvent event) {
        LivingEntity entity = event.getEntity();
        if (entity.level().isClientSide || entity instanceof Player) {
            return;
        }

        CorruptionEffectStack stack = serverGameplayStack(entity);
        String targetId = entityTargetId(entity);
        if (shouldSyncEntityMechanics(entity, stack, false)) {
            syncEntityMechanics(entity, stack, targetId);
        }
        if (stack.level() <= 0) {
            return;
        }
        mutateAirSupplyTick(entity, stack);
        boolean entityBehaviorActive = surfaceActive(stack, CorruptionSurface.ENTITY_STATE, MIN_ENTITY_MECHANICS_INTENSITY)
                || surfaceActive(stack, CorruptionSurface.ENTITY_KINEMATICS, MIN_ENTITY_MECHANICS_INTENSITY);
        if (entityBehaviorActive) {
            int cadence = Math.max(2, corruptedCadence(stack, targetId + ":ai_tick", Math.max(2, 8 - Math.round(stack.intensity(CorruptionSurface.ENTITY_STATE) * 4.0F)), 40));
            if (entity.tickCount % cadence == 0) {
                mutateLivingState(entity, stack, targetId);
            }
        }
        if (surfaceActive(stack, CorruptionSurface.BLOCK_COLLISION, MIN_ENTITY_MECHANICS_INTENSITY) && entity.tickCount % 2 == 0) {
            mutateEntityCollision(entity, stack, targetId);
        }
    }

    @SubscribeEvent
    public static void onPlayerTick(TickEvent.PlayerTickEvent event) {
        if (event.phase != TickEvent.Phase.END) {
            return;
        }

        Player player = event.player;
        CorruptionEffectStack stack = activeStackFor(player);
        boolean suppressClientAuthoritative = shouldSuppressClientAuthoritativeGameplay(player);
        boolean runClientPredictedMobility = suppressClientAuthoritative && usesClientPredictedMobility(player);
        if (!suppressClientAuthoritative && shouldSyncEntityMechanics(player, stack, true)) {
            syncEntityMechanics(player, stack, entityTargetId(player));
        }
        if (!suppressClientAuthoritative || runClientPredictedMobility) {
            mutatePlayerCollision(player, stack);
        }
        if (suppressClientAuthoritative) {
            return;
        }

        if (player instanceof ServerPlayer serverPlayer) {
            mutateAirSupplyTick(serverPlayer, stack);
            int itemCadence = 10;
            int projectileCadence = 8;
            if (serverPlayer.tickCount % itemCadence == 0 && surfaceActive(stack, CorruptionSurface.LOOSE_ENTITY_PHYSICS, MIN_ENTITY_MECHANICS_INTENSITY)) {
                shakeNearbyDroppedItems(serverPlayer, stack);
            }
            if (serverPlayer.tickCount % projectileCadence == 0 && surfaceActive(stack, CorruptionSurface.PROJECTILE_PHYSICS, MIN_ENTITY_MECHANICS_INTENSITY)) {
                corruptNearbyProjectiles(serverPlayer, stack);
            }
            if (serverPlayer.tickCount % 5 == 0) {
                mutateLiquidMechanics(serverPlayer, stack);
            }
            int timeCadence = surfaceActive(stack, CorruptionSurface.TICK_SPEED, MIN_DAY_TIME_INTENSITY) ? 5 : 160;
            if (serverPlayer.tickCount % timeCadence == 0) {
                mutateDayTime(serverPlayer.serverLevel(), stack);
            }
            int weatherCadence = surfaceActive(stack, CorruptionSurface.WORLD_RENDER, MIN_WORLD_PROCESS_INTENSITY) ? 10 : 160;
            if (serverPlayer.tickCount % weatherCadence == 0) {
                mutateWeather(serverPlayer.serverLevel(), stack);
            }
        }
    }

    public static float corruptFoodExhaustion(Player player, float exhaustion) {
        if (player == null || player.level() == null || exhaustion == 0.0F) {
            return exhaustion;
        }
        CorruptionEffectStack stack = authoritativeGameplayStackFor(player);
        String targetId = "hunger_depletion:" + playerTargetId(player);
        if (!surfaceActive(stack, CorruptionSurface.TICK_SPEED, MIN_DAY_TIME_INTENSITY)) {
            return exhaustion;
        }

        float intensity = Mth.clamp(stack.extreme(CorruptionSurface.TICK_SPEED) ? 1.0F : stack.intensity(CorruptionSurface.TICK_SPEED), 0.0F, 1.0F);
        long hash = stack.stableLong(CorruptionSurface.TICK_SPEED, targetId, player.getId() ^ 0x48554E47);
        double speed = gameSpeedMultiplier(stack, targetId);
        if (unitHash(hash ^ 0x5354414CL) < 0.10F + intensity * 0.28F) {
            speed *= unitHash(hash ^ 0x534C4F57L) < 0.50F
                    ? 0.0D
                    : 6.0D + unitHash(hash ^ 0x46415354L) * 54.0D;
        }
        return (float) Mth.clamp(exhaustion * speed, 0.0D, 80.0D);
    }

    public static float corruptAttackStrengthScale(Player player, float original, float partialTick) {
        if (player == null || player.level() == null) {
            return original;
        }
        CorruptionEffectStack stack = authoritativeGameplayStackFor(player);
        String targetId = "attack_cooldown:" + playerTargetId(player);
        if (!surfaceActive(stack, CorruptionSurface.ANIMATION_TIMING, MIN_PLAYER_MECHANICS_INTENSITY)) {
            return original;
        }

        float intensity = Mth.clamp(stack.extreme(CorruptionSurface.ANIMATION_TIMING) ? 1.0F : stack.intensity(CorruptionSurface.ANIMATION_TIMING), 0.0F, 1.0F);
        long clock = stack.stableLong(CorruptionSurface.ANIMATION_TIMING, targetId, player.getId() ^ 0x41544B43) ^ Float.floatToIntBits(partialTick);
        if (unitHash(clock ^ 0x554E4C4FL) < 0.08F + intensity * (stack.extreme(CorruptionSurface.ANIMATION_TIMING) ? 0.84F : 0.44F)) {
            return 0.0F;
        }
        float mutated = CorruptionValueMutator.mutateScalar(stack, CorruptionSurface.ANIMATION_TIMING, targetId + ":scale", original, 0.35F + intensity * 2.60F, -1.0F, 2.8F, 0x41, clock);
        return Mth.clamp(mutated, 0.0F, 1.0F);
    }

    public static float corruptAttackStrengthDelay(Player player, float original) {
        if (player == null || player.level() == null || original <= 0.0F) {
            return original;
        }
        CorruptionEffectStack stack = authoritativeGameplayStackFor(player);
        String targetId = "attack_delay:" + playerTargetId(player);
        if (!surfaceActive(stack, CorruptionSurface.ANIMATION_TIMING, MIN_PLAYER_MECHANICS_INTENSITY)) {
            return original;
        }

        float intensity = Mth.clamp(stack.extreme(CorruptionSurface.ANIMATION_TIMING) ? 1.0F : stack.intensity(CorruptionSurface.ANIMATION_TIMING), 0.0F, 1.0F);
        long hash = stack.stableLong(CorruptionSurface.ANIMATION_TIMING, targetId, player.getId() ^ 0x44454C59);
        if (unitHash(hash ^ 0x53545543L) < 0.06F + intensity * 0.36F) {
            return 72000.0F;
        }
        double multiplier = Math.pow(2.0D, signedUnit(hash ^ 0x4D554C54L) * intensity * 7.0D);
        return (float) Mth.clamp(original * multiplier, 0.05D, 72000.0D);
    }

    public static int corruptAirSupplyChange(LivingEntity entity, int before, int after, boolean decreasing) {
        if (entity == null || entity.level() == null || entity.isSpectator() || before == after) {
            return after;
        }
        CorruptionEffectStack stack = authoritativeGameplayStackFor(entity);
        String targetId = "air_supply_speed:" + (decreasing ? "drain:" : "refill:") + entityTargetId(entity);
        boolean player = entity instanceof Player;
        if (!airSupplyMechanicsActive(stack, targetId, player)) {
            return after;
        }

        CorruptionSurface surface = airSupplySurface(stack, targetId, player);
        float intensity = airSupplyIntensity(stack, targetId, player);
        double speed = airSupplySpeedMultiplier(stack, surface, targetId, player, intensity);
        long hash = stack.stableLong(surface, targetId, entity.getId() ^ before ^ 0x414952);
        if (unitHash(hash ^ 0x5354414CL) < 0.08F + intensity * (stack.extreme(surface) ? 0.74F : 0.44F)) {
            int mode = Math.floorMod((int) (hash >>> 25), 6);
            speed = switch (mode) {
                case 0 -> 0.0D;
                case 1 -> 8.0D + unitHash(hash ^ 0x46415354L) * (48.0D + intensity * 180.0D);
                case 2 -> 0.01D + unitHash(hash ^ 0x534C4F57L) * 0.18D;
                case 3 -> -1.0D * (0.20D + intensity * (stack.extreme(surface) ? 32.0D : 8.0D));
                case 4 -> speed * (unitHash(hash ^ 0x42555253L) < 0.50F ? -12.0D : 12.0D);
                default -> speed;
            };
        }

        int delta = Math.abs(after - before);
        int mutatedDelta = Math.max(0, (int) Math.round(delta * Math.abs(speed)));
        if (speed < 0.0D) {
            return Mth.clamp(before + (decreasing ? mutatedDelta : -mutatedDelta), -160, entity.getMaxAirSupply() + 240);
        }
        return Mth.clamp(before + (decreasing ? -mutatedDelta : mutatedDelta), -160, entity.getMaxAirSupply() + 240);
    }

    private static void mutateAirSupplyTick(LivingEntity entity, CorruptionEffectStack stack) {
        if (entity == null || entity.level() == null || entity.level().isClientSide || entity.isSpectator()) {
            return;
        }

        String targetId = "air_supply_tick:" + entityTargetId(entity);
        boolean player = entity instanceof Player;
        if (!airSupplyMechanicsActive(stack, targetId, player)) {
            return;
        }

        CorruptionSurface surface = airSupplySurface(stack, targetId, player);
        float intensity = airSupplyIntensity(stack, targetId, player);
        if (intensity <= MIN_AIR_MECHANICS_INTENSITY) {
            return;
        }

        boolean inWater = entity.isInWater() || isEyeTouchingFluid(entity, FluidTags.WATER);
        boolean inLava = entity.isInLava() || isEyeTouchingFluid(entity, FluidTags.LAVA);
        int maxAir = Math.max(1, entity.getMaxAirSupply());
        int currentAir = entity.getAirSupply();
        boolean recoveringAir = currentAir < maxAir;
        long seed = stack.stableLong(surface, targetId, entity.getId() ^ currentAir ^ 0x41495254);
        float falseContextChance = stack.extreme(surface)
                ? 0.82F
                : Mth.clamp(0.01F + intensity * 0.24F + visibleCorruptionPressure(stack) * 0.20F + stack.instability() * 0.10F, 0.0F, 0.56F);
        boolean falseAirContext = stack.unit(surface, targetId + ":detector_fault", entity.tickCount >> 3) < falseContextChance;
        if (!inWater && !inLava && !recoveringAir && !falseAirContext) {
            return;
        }

        int cadence = stack.extreme(surface) ? 1 : Mth.clamp(Math.round(10.0F - intensity * 8.0F), 1, 10);
        if (entity.tickCount % cadence != 0) {
            return;
        }

        double speed = Math.max(0.02D, Math.abs(airSupplySpeedMultiplier(stack, surface, targetId, player, intensity)));
        int amount = Mth.clamp((int) Math.round((1.0D + intensity * (stack.extreme(surface) ? 18.0D : 8.0D)) * Math.sqrt(speed)), 1, maxAir + 120);
        int mode = Math.floorMod((int) (seed >>> 27), 7);
        int nextAir;
        if (inWater || inLava || recoveringAir) {
            nextAir = switch (mode) {
                case 0 -> currentAir == maxAir ? currentAir - amount : currentAir;
                case 1 -> currentAir - amount;
                case 2 -> currentAir - amount * (2 + Math.round(intensity * 5.0F));
                case 3 -> currentAir + (currentAir >= maxAir ? -amount : amount);
                case 4 -> Math.round(-20.0F - intensity * (20.0F + unitHash(seed ^ 0x44524F50L) * 120.0F));
                case 5 -> Math.round(maxAir * (0.05F + unitHash(seed ^ 0x434150L) * (0.55F - intensity * 0.35F)));
                default -> currentAir + (unitHash(seed ^ 0x524556L) < 0.45F ? amount : -amount);
            };
        } else {
            nextAir = switch (mode) {
                case 0, 1, 4 -> currentAir - Math.max(1, amount / 2);
                case 2 -> Math.round(maxAir * (0.10F + unitHash(seed ^ 0x46414C53L) * 0.42F));
                case 3 -> currentAir + amount;
                default -> currentAir;
            };
        }

        nextAir = Mth.clamp(nextAir, -180, maxAir + 240);
        if (nextAir != currentAir) {
            entity.setAirSupply(nextAir);
        }
    }

    public static float corruptJumpPower(LivingEntity entity, float original) {
        if (entity == null || entity.level() == null || entity.isSpectator()) {
            return original;
        }
        CorruptionEffectStack stack = mobilityGameplayStackFor(entity);
        String targetId = "mobility_motion:" + entityTargetId(entity) + (entity instanceof Player ? ":player" : ":living") + ":jump_power";
        float intensity = mobilityMotionIntensity(stack, targetId);
        if (intensity <= MIN_ENTITY_MECHANICS_INTENSITY) {
            return original;
        }

        CorruptionSurface surface = CorruptionSurface.PLAYER_PHYSICS;
        long hash = stack.stableLong(surface, targetId, entityStableSalt(entity, 0x4A554D50));
        float span = (float) (0.28D + intensity * (stack.extreme(surface) ? 5.8D : 2.6D));
        long clock = stack.stableLong(surface, targetId + ":jump_clock", entityStableSalt(entity, 0x4A50));
        float mutated = CorruptionValueMutator.mutateScalar(stack, surface, targetId, original, span, -3.5F, stack.extreme(surface) ? 8.0F : 4.5F, 0x4A50, clock);
        if (unitHash(hash ^ 0x5354414CL) < 0.08F + intensity * 0.32F) {
            int mode = Math.floorMod((int) (hash >>> 27), 5);
            mutated = switch (mode) {
                case 0 -> 0.0F;
                case 1 -> -0.10F - unitHash(hash ^ 0x444F574EL) * intensity * 2.2F;
                case 2 -> original * (0.04F + unitHash(hash ^ 0x54494E59L) * 0.18F);
                case 3 -> original + 0.85F + unitHash(hash ^ 0x48494748L) * intensity * 5.0F;
                default -> -original * (0.45F + intensity * 1.8F);
            };
        }
        return Mth.clamp(mutated, -3.5F, stack.extreme(surface) ? 8.0F : 4.5F);
    }

    public static Vec3 corruptTravelVector(LivingEntity entity, Vec3 travel) {
        if (entity == null || travel == null || entity.level() == null || entity.isSpectator()) {
            return travel;
        }
        CorruptionEffectStack stack = mobilityGameplayStackFor(entity);
        String targetId = "mobility_motion:" + entityTargetId(entity) + (entity instanceof Player ? ":player" : ":living") + ":travel";
        float intensity = mobilityMotionIntensity(stack, targetId);
        if (intensity <= MIN_ENTITY_MECHANICS_INTENSITY) {
            return travel;
        }

        CorruptionSurface surface = CorruptionSurface.PLAYER_PHYSICS;
        long seed = stack.stableLong(surface, targetId, entityStableSalt(entity, 0x54524156));
        int bucket = collisionPositionBucket(entity, seed, intensity);
        Vec3 mutated = mutateFeatureVelocity(stack, surface, targetId, travel, seed, bucket, intensity, 0.018D, 1.15D, 8.0D);
        if (unitHash(seed ^ 0x53574150L) < 0.10F + intensity * 0.34F) {
            int mode = Math.floorMod((int) (seed >>> 29), 6);
            mutated = switch (mode) {
                case 0 -> new Vec3(-mutated.x, mutated.y, -mutated.z);
                case 1 -> new Vec3(mutated.z, mutated.y, -mutated.x);
                case 2 -> mutated.scale(0.0D);
                case 3 -> new Vec3(mutated.x, signedUnit(seed ^ 0x594D4F44L) * intensity * 1.6D, mutated.z);
                case 4 -> mutated.normalize().scale(0.02D + unitHash(seed ^ 0x53504544L) * intensity * 3.5D);
                default -> mutated.add(signedUnit(seed ^ 0x584F4646L) * intensity * 1.4D, signedUnit(seed ^ 0x594F4646L) * intensity * 1.1D, signedUnit(seed ^ 0x5A4F4646L) * intensity * 1.4D);
            };
        }
        return clampVector(mutated, stack.extreme(surface) ? 12.0D : 8.0D);
    }

    public static boolean shouldCancelPlayerAttack(Player player, Entity target) {
        if (player == null || target == null || player.level() == null) {
            return false;
        }
        CorruptionEffectStack stack = authoritativeGameplayStackFor(player);
        String targetId = "attack_routing:" + entityTargetId(target);
        if (!targetActive(stack, CorruptionSurface.INTERACTION_ROUTING, targetId, MIN_PLAYER_MECHANICS_INTENSITY)) {
            return false;
        }
        float intensity = Mth.clamp(Math.max(stack.targetIntensity(CorruptionSurface.INTERACTION_ROUTING, targetId), stack.intensity(CorruptionSurface.INTERACTION_ROUTING)), 0.0F, 1.0F);
        int salt = player.level().dimension().location().hashCode() ^ InteractionRayCorruptionMechanics.entitySalt(target) ^ 0x4154544B;
        long hash = stack.stableLong(CorruptionSurface.INTERACTION_ROUTING, targetId, salt);
        return unitHash(hash ^ 0x43414E43L) < Mth.clamp(0.04F + intensity * 0.78F + stack.instability() * 0.10F, 0.0F, 0.96F);
    }

    public static boolean shouldCancelPlayerInteraction(Player player, String phase, BlockPos pos, Entity target, ItemStack itemStack) {
        if (player == null || player.level() == null || player.isSpectator()) {
            return false;
        }
        CorruptionEffectStack stack = authoritativeGameplayStackFor(player);
        String itemId = "empty";
        if (itemStack != null && !itemStack.isEmpty()) {
            ResourceLocation location = ForgeRegistries.ITEMS.getKey(itemStack.getItem());
            itemId = location == null ? itemStack.getItem().toString() : location.toString();
        }
        String targetPart = target == null ? "world" : entityTargetId(target);
        String targetId = "interaction_failure:" + phase + ":" + targetPart + ":" + itemId;
        if (!surfaceActive(stack, CorruptionSurface.INTERACTION_ROUTING, MIN_PLAYER_MECHANICS_INTENSITY)
                && !targetActive(stack, CorruptionSurface.INTERACTION_ROUTING, targetId, MIN_PLAYER_MECHANICS_INTENSITY)) {
            return false;
        }

        float intensity = Mth.clamp(Math.max(
                stack.extreme(CorruptionSurface.INTERACTION_ROUTING) ? 1.0F : stack.intensity(CorruptionSurface.INTERACTION_ROUTING),
                stack.targetIntensity(CorruptionSurface.INTERACTION_ROUTING, targetId)
        ), 0.0F, 1.0F);
        int salt = player.level().dimension().location().hashCode() ^ phase.hashCode() ^ itemId.hashCode();
        if (pos != null) {
            salt ^= InteractionRayCorruptionMechanics.positionSalt(pos);
        }
        if (target != null) {
            salt ^= InteractionRayCorruptionMechanics.entitySalt(target) * 31;
        }
        long hash = stack.stableLong(CorruptionSurface.INTERACTION_ROUTING, targetId, salt);
        float chance = stack.extreme(CorruptionSurface.INTERACTION_ROUTING)
                ? 0.94F
                : Mth.clamp(0.035F + intensity * 0.78F + stack.instability() * 0.10F, 0.0F, 0.90F);
        return stack.unit(CorruptionSurface.INTERACTION_ROUTING, targetId + ":cancel", (int) (hash ^ 0x43414E43L)) < chance;
    }

    public static float corruptBlockBreakSpeed(Player player, BlockState state, BlockPos pos, float original) {
        if (player == null || state == null || player.level() == null || player.isSpectator() || original <= 0.0F) {
            return original;
        }

        CorruptionEffectStack stack = blockBreakGameplayStackFor(player);
        String targetId = blockBreakTargetId();
        if (!blockBreakInteractionActive(stack, targetId)) {
            return original;
        }

        float intensity = blockBreakInteractionIntensity(stack, targetId);
        long hash = blockBreakInteractionHash(stack, player, "speed_multiplier");
        float chance = stack.extreme(CorruptionSurface.INTERACTION_ROUTING)
                ? 0.98F
                : Mth.clamp(0.05F + intensity * 0.78F + stack.instability() * 0.08F, 0.0F, 0.92F);
        if (unitHash(hash ^ 0x53504544L) > chance) {
            return original;
        }

        boolean faster = unitHash(hash ^ 0x46415354L) < 0.50F;
        float magnitude = 0.18F + unitHash(hash ^ 0x4D41474EL) * 0.82F;
        float multiplier;
        if (faster) {
            float maximumBoost = stack.extreme(CorruptionSurface.INTERACTION_ROUTING) ? 160.0F : 54.0F;
            multiplier = 1.0F + magnitude * magnitude * (4.0F + intensity * maximumBoost);
        } else {
            float minimum = stack.extreme(CorruptionSurface.INTERACTION_ROUTING) && unitHash(hash ^ 0x5A45524FL) < 0.18F ? 0.0F : 0.003F;
            float slowPressure = magnitude * (0.64F + intensity * 0.36F);
            multiplier = Mth.clamp(1.0F - slowPressure, minimum, 0.86F);
        }

        float mutated = original * multiplier;
        return (float) Mth.clamp(mutated, 0.0D, Math.max(64.0D, original * 192.0D));
    }

    @SubscribeEvent(priority = EventPriority.HIGHEST)
    public static void onRightClickBlock(PlayerInteractEvent.RightClickBlock event) {
        cancelPlayerInteraction(event, "right_click_block", event.getPos(), null);
    }

    @SubscribeEvent(priority = EventPriority.HIGHEST)
    public static void onLeftClickBlock(PlayerInteractEvent.LeftClickBlock event) {
        cancelPlayerInteraction(event, "left_click_block", event.getPos(), null);
    }

    @SubscribeEvent(priority = EventPriority.HIGHEST)
    public static void onRightClickItem(PlayerInteractEvent.RightClickItem event) {
        cancelPlayerInteraction(event, "right_click_item", event.getPos(), null);
    }

    @SubscribeEvent(priority = EventPriority.HIGHEST)
    public static void onEntityInteract(PlayerInteractEvent.EntityInteract event) {
        cancelPlayerInteraction(event, "entity_interact", event.getPos(), event.getTarget());
    }

    @SubscribeEvent(priority = EventPriority.HIGHEST)
    public static void onEntityInteractSpecific(PlayerInteractEvent.EntityInteractSpecific event) {
        cancelPlayerInteraction(event, "entity_interact_specific", event.getPos(), event.getTarget());
    }

    private static void cancelPlayerInteraction(PlayerInteractEvent event, String phase, BlockPos pos, Entity target) {
        if (event == null) {
            return;
        }
        Player player = event.getEntity();
        if (!shouldCancelPlayerInteraction(player, phase, pos, target, event.getItemStack())) {
            return;
        }
        event.setCancellationResult(InteractionResult.FAIL);
        event.setCanceled(true);
    }

    @SubscribeEvent(priority = EventPriority.LOWEST)
    public static void onBreakSpeed(PlayerEvent.BreakSpeed event) {
        Player player = event.getEntity();
        BlockPos pos = event.getPosition().orElse(null);
        float mutated = corruptBlockBreakSpeed(player, event.getState(), pos, event.getNewSpeed());
        event.setNewSpeed(mutated);
    }

    public static boolean shouldDisableEntityTargeting(Entity entity, String phase) {
        if (entity == null || entity.level() == null || entity instanceof Player || entity.isSpectator()) {
            return false;
        }
        CorruptionEffectStack stack = authoritativeGameplayStackFor(entity);
        String targetId = "entity_hitbox_failure:" + phase + ":" + entityTargetId(entity);
        if (!surfaceActive(stack, CorruptionSurface.ENTITY_STATE, MIN_ENTITY_MECHANICS_INTENSITY)) {
            return false;
        }

        float intensity = Mth.clamp(stack.intensity(CorruptionSurface.ENTITY_STATE), 0.0F, 1.0F);
        String globalId = "entity_hitbox_failure:global:" + entity.level().dimension().location();
        long globalHash = stack.stableLong(CorruptionSurface.ENTITY_STATE, globalId, 0x474C4F42);
        float globalFailureChance = Mth.clamp((float) Math.pow(intensity, 2.55F) * (0.20F + stack.instability() * 0.46F), 0.0F, 0.78F);
        if (unitHash(globalHash ^ 0x414C4CL) < globalFailureChance) {
            return true;
        }

        long hash = stack.stableLong(CorruptionSurface.ENTITY_STATE, targetId, entity.getId() ^ 0x48495442);
        int bucket = collisionPositionBucket(entity, hash, intensity);
        float chance = Mth.clamp(0.03F + intensity * 0.68F + stack.instability() * intensity * 0.12F, 0.0F, 0.90F);
        return stack.unit(CorruptionSurface.ENTITY_STATE, targetId, bucket) < chance;
    }

    public static EntityDimensions corruptEntityHitboxDimensions(Entity entity, EntityDimensions original) {
        return EntityHitboxCorruptionMechanics.corruptDimensions(entity, original, activeStackFor(entity));
    }

    public static AABB corruptEntityHitboxBounds(Entity entity, AABB original) {
        return EntityHitboxCorruptionMechanics.corruptBounds(entity, original, activeStackFor(entity));
    }

    public static int entityHitboxMutationSignature(Entity entity) {
        return EntityHitboxCorruptionMechanics.mutationSignature(entity, activeStackFor(entity));
    }

    public static void beginEntityHitboxDimensionBypass() {
        EntityHitboxCorruptionMechanics.beginDimensionBypass();
    }

    public static void endEntityHitboxDimensionBypass() {
        EntityHitboxCorruptionMechanics.endDimensionBypass();
    }

    public static void corruptCraftingPreviewResult(AbstractContainerMenu menu, Level level, Player player, CraftingContainer craftSlots, ResultContainer resultSlots) {
        if (!(player instanceof ServerPlayer serverPlayer) || level == null || level.isClientSide || craftSlots == null || resultSlots == null || menu == null) {
            return;
        }

        CorruptionEffectStack stack = serverStack(serverPlayer.serverLevel());
        ItemStack original = resultSlots.getItem(0).copy();
        if (original.isEmpty() || !craftingSystemBroken(stack, level)) {
            return;
        }

        resultSlots.setItem(0, ItemStack.EMPTY);
        menu.setRemoteSlot(0, ItemStack.EMPTY);
        serverPlayer.connection.send(new ClientboundContainerSetSlotPacket(menu.containerId, menu.incrementStateId(), 0, ItemStack.EMPTY));
    }

    public static int corruptFurnaceBurnDuration(BlockEntity blockEntity, ItemStack fuel, int original) {
        if (blockEntity == null || fuel == null || fuel.isEmpty() || original <= 0) {
            return original;
        }

        CorruptionEffectStack stack = blockEntityStack(blockEntity);
        String targetId = "gui_container:furnace_fuel:" + blockEntityTargetId(blockEntity) + ":" + itemTargetId(fuel);
        if (!guiFunctionalityActive(stack, targetId)) {
            return original;
        }

        float intensity = guiFunctionalityIntensity(stack, targetId);
        long seed = stack.stableLong(CorruptionSurface.GUI_FUNCTIONALITY, targetId, 0x4655454C) ^ blockEntitySalt(blockEntity);
        float chance = stack.extreme(CorruptionSurface.GUI_FUNCTIONALITY)
                ? 0.98F
                : Mth.clamp(0.08F + intensity * 0.72F + stack.instability() * 0.08F, 0.0F, 0.92F);
        if (unitHash(seed ^ 0x4255524EL) > chance) {
            return original;
        }

        int mode = Math.floorMod((int) (seed >>> 28), 6);
        int mutated = switch (mode) {
            case 0, 1, 2 -> 0;
            case 3 -> Math.max(1, Math.round(original * (0.01F + unitHash(seed ^ 0x534C4F57L) * 0.16F)));
            case 4 -> Math.round(original * (2.0F + unitHash(seed ^ 0x46415354L) * (8.0F + intensity * 48.0F)));
            default -> Math.round(CorruptionValueMutator.mutateScalar(
                    stack,
                    CorruptionSurface.GUI_FUNCTIONALITY,
                    targetId,
                    original,
                    80.0F + intensity * 3600.0F,
                    0.0F,
                    Math.max(1_200.0F, original * (stack.extreme(CorruptionSurface.GUI_FUNCTIONALITY) ? 96.0F : 24.0F)),
                    0x4655,
                    seed
            ));
        };
        return clampInt(mutated, 0, Math.max(32_000, original * 128));
    }

    public static boolean corruptFurnaceCanBurn(BlockEntity blockEntity, boolean original) {
        if (blockEntity == null || !original) {
            return original;
        }

        CorruptionEffectStack stack = blockEntityStack(blockEntity);
        String targetId = "gui_container:furnace_burn:" + blockEntityTargetId(blockEntity);
        if (!guiFunctionalityActive(stack, targetId)) {
            return original;
        }

        float intensity = guiFunctionalityIntensity(stack, targetId);
        long seed = stack.stableLong(CorruptionSurface.GUI_FUNCTIONALITY, targetId, 0x434F4F4B) ^ blockEntitySalt(blockEntity);
        float chance = stack.extreme(CorruptionSurface.GUI_FUNCTIONALITY)
                ? 0.96F
                : Mth.clamp(0.05F + intensity * 0.66F + stack.instability() * 0.08F, 0.0F, 0.88F);
        return unitHash(seed ^ 0x53544F50L) < chance ? false : original;
    }

    public static boolean corruptBrewingStandBrewable(BlockEntity blockEntity, boolean original) {
        if (blockEntity == null || !original) {
            return original;
        }

        CorruptionEffectStack stack = blockEntityStack(blockEntity);
        String targetId = "gui_container:brewing_stand:" + blockEntityTargetId(blockEntity);
        if (!guiFunctionalityActive(stack, targetId)) {
            return original;
        }

        float intensity = guiFunctionalityIntensity(stack, targetId);
        long seed = stack.stableLong(CorruptionSurface.GUI_FUNCTIONALITY, targetId, 0x42524557) ^ blockEntitySalt(blockEntity);
        float chance = stack.extreme(CorruptionSurface.GUI_FUNCTIONALITY)
                ? 0.96F
                : Mth.clamp(0.05F + intensity * 0.68F + stack.instability() * 0.08F, 0.0F, 0.88F);
        return unitHash(seed ^ 0x4E4F4252L) < chance ? false : original;
    }

    public static boolean shouldBlockMerchantTrade(Merchant merchant, Player player, MerchantOffer offer) {
        if (merchant == null || player == null || offer == null || player.level().isClientSide) {
            return false;
        }

        Entity merchantEntity = merchant instanceof Entity entity ? entity : null;
        CorruptionEffectStack stack = merchantEntity == null ? activeStackFor(player) : authoritativeGameplayStackFor(merchantEntity);
        String targetId = "gui_container:villager_trade:" + merchantTradeTargetId(merchantEntity, offer);
        if (!guiFunctionalityActive(stack, targetId)) {
            return false;
        }

        float intensity = guiFunctionalityIntensity(stack, targetId);
        int salt = merchantEntity == null ? player.level().dimension().location().hashCode() : entityStableSalt(merchantEntity, 0x54524144);
        long seed = stack.stableLong(CorruptionSurface.GUI_FUNCTIONALITY, targetId, salt);
        float chance = stack.extreme(CorruptionSurface.GUI_FUNCTIONALITY)
                ? 0.98F
                : Mth.clamp(0.05F + intensity * 0.74F + stack.instability() * 0.08F, 0.0F, 0.92F);
        return unitHash(seed ^ 0x4F464652L) < chance;
    }

    public static int corruptRedstoneSignal(BlockState state, BlockGetter level, BlockPos pos, Direction direction, int original, String channel) {
        return RedstoneCorruptionMechanics.corruptSignal(state, pos, direction, original, channel, false, redstoneStack(level), redstoneGameTime(level), MIN_WORLD_PROCESS_INTENSITY);
    }

    public static int corruptRedstoneAnalogSignal(BlockState state, Level level, BlockPos pos, int original) {
        return RedstoneCorruptionMechanics.corruptSignal(state, pos, null, original, "analog", true, redstoneStack(level), redstoneGameTime(level), MIN_WORLD_PROCESS_INTENSITY);
    }

    @SubscribeEvent
    public static void onProjectileImpact(ProjectileImpactEvent event) {
        Projectile projectile = event.getProjectile();
        if (!(projectile.level() instanceof ServerLevel level) || event.getRayTraceResult().getType() == HitResult.Type.MISS) {
            return;
        }

        CorruptionEffectStack stack = serverStack(level);
        String targetId = projectileTargetId(projectile);
        if (!targetActive(stack, CorruptionSurface.IMPACT_RESOLUTION, targetId, MIN_ENTITY_MECHANICS_INTENSITY)) {
            return;
        }

        Vec3 velocity = projectile.getDeltaMovement();
        float intensity = stack.targetIntensity(CorruptionSurface.IMPACT_RESOLUTION, targetId);
        if (CorruptionValueMutator.decision(stack, CorruptionSurface.IMPACT_RESOLUTION, targetId, projectile.getId() ^ projectile.tickCount, 0.74F)) {
            event.setImpactResult(ProjectileImpactEvent.ImpactResult.SKIP_ENTITY);
            Vec3 mutatedVelocity = CorruptionValueMutator.mutateVector(stack, CorruptionSurface.IMPACT_RESOLUTION, targetId + ":velocity", velocity, 0.06D + intensity * 0.30D, 2.2D, projectile.tickCount, level.getGameTime());
            projectile.setDeltaMovement(mutatedVelocity);
            projectile.hasImpulse = true;
        }
    }

    @SubscribeEvent
    public static void onEntityJoinLevel(EntityJoinLevelEvent event) {
        if (!(event.getLevel() instanceof ServerLevel level) || event.loadedFromDisk()) {
            return;
        }

        Entity entity = event.getEntity();
        if (entity instanceof ServerPlayer player) {
            forceSyncPlayerMechanics(player);
            return;
        }

        CorruptionEffectStack stack = serverStack(level);
        if (entity instanceof Projectile projectile) {
            String targetId = projectileTargetId(projectile);
            if (!targetActive(stack, CorruptionSurface.PROJECTILE_PHYSICS, targetId, MIN_ENTITY_MECHANICS_INTENSITY)) {
                return;
            }
            mutateProjectileEntity(projectile, stack, targetId);
        } else if (entity instanceof LivingEntity living && !(living instanceof Player)) {
            String targetId = entityTargetId(living);
            forceSyncEntityMechanics(living, serverGameplayStack(living), targetId);
            mutateLivingState(living, stack, targetId);
        }
    }

    @SubscribeEvent
    public static void onLivingHurt(LivingHurtEvent event) {
        if (!(event.getEntity().level() instanceof ServerLevel level)) {
            return;
        }

        CorruptionEffectStack stack = serverStack(level);
        mutateDamageEvent(event, stack);
        if (event.isCanceled() || !(event.getSource().getDirectEntity() instanceof Projectile projectile)) {
            return;
        }

        String targetId = projectileTargetId(projectile) + ":damage";
        if (!targetActive(stack, CorruptionSurface.PROJECTILE_PHYSICS, targetId, MIN_ENTITY_MECHANICS_INTENSITY)) {
            return;
        }

        if (CorruptionValueMutator.decision(stack, CorruptionSurface.PROJECTILE_PHYSICS, targetId + ":no_damage", projectile.getId() ^ event.getEntity().getId(), 0.26F)) {
            event.setCanceled(true);
        } else {
            float intensity = stack.targetIntensity(CorruptionSurface.PROJECTILE_PHYSICS, targetId);
            float amount = CorruptionValueMutator.mutateScalar(stack, CorruptionSurface.PROJECTILE_PHYSICS, targetId + ":amount", event.getAmount(), event.getAmount() * 2.0F + 8.0F * intensity, 0.0F, 256.0F, 0x57, level.getGameTime());
            event.setAmount(amount);
        }

    }

    @SubscribeEvent
    public static void onSpawnPlacement(MobSpawnEvent.SpawnPlacementCheck event) {
        ServerLevel level = event.getLevel().getLevel();
        CorruptionEffectStack stack = serverStack(level);
        String targetId = "spawn_rule:" + ForgeRegistries.ENTITY_TYPES.getKey(event.getEntityType()) + ":" + event.getSpawnType().name();
        mutateSpawnResult(event, stack, targetId, event.getPos().hashCode() ^ event.getSpawnType().ordinal(), event.getDefaultResult());
    }

    @SubscribeEvent
    public static void onSpawnPosition(MobSpawnEvent.PositionCheck event) {
        ServerLevel level = event.getLevel().getLevel();
        CorruptionEffectStack stack = serverStack(level);
        String targetId = "spawn_position:" + entityTargetId(event.getEntity()) + ":" + event.getSpawnType().name();
        mutateSpawnResult(event, stack, targetId, event.getEntity().blockPosition().hashCode() ^ event.getSpawnType().ordinal(), true);
    }

    @SubscribeEvent
    public static void onFinalizeSpawn(MobSpawnEvent.FinalizeSpawn event) {
        ServerLevel level = event.getLevel().getLevel();
        CorruptionEffectStack stack = serverStack(level);
        Mob mob = event.getEntity();
        String targetId = entityTargetId(mob);
        if (targetActive(stack, CorruptionSurface.SPAWN_RULES, "spawn_finalize:" + targetId, MIN_SPAWN_MECHANICS_INTENSITY)
                && CorruptionValueMutator.decision(stack, CorruptionSurface.SPAWN_RULES, "spawn_finalize:" + targetId, mob.getId(), 0.16F)) {
            event.setSpawnCancelled(true);
            return;
        }
        mutateLivingState(mob, stack, targetId);
    }

    @SubscribeEvent
    public static void onFluidPlaceBlock(BlockEvent.FluidPlaceBlockEvent event) {
        if (!(event.getLevel() instanceof ServerLevel level)) {
            return;
        }

        CorruptionEffectStack stack = serverStack(level);
        if (usesSaveStableVisualWorldgen(stack)) {
            return;
        }
        String targetId = "fluid_place:" + blockTargetId(event.getOriginalState()) + "->" + blockTargetId(event.getNewState());
        float intensity = stack.targetIntensity(CorruptionSurface.WORLDGEN_SURFACE, targetId);
        if (targetActive(stack, CorruptionSurface.WORLDGEN_SURFACE, targetId, MIN_WORLD_PROCESS_INTENSITY)
                && CorruptionValueMutator.decision(stack, CorruptionSurface.WORLDGEN_SURFACE, targetId, event.getPos().hashCode(), 0.52F + intensity * 0.22F)) {
            long hash = stableHash(level.getSeed(), event.getPos().getX(), event.getPos().getZ(), event.getPos().getY());
            event.setNewState(mutatedNearbyBlockState(level, event.getPos(), hash, stack, event.getOriginalState(), true));
        }
    }

    @SubscribeEvent
    public static void onCreateFluidSource(BlockEvent.CreateFluidSourceEvent event) {
        if (!(event.getLevel() instanceof ServerLevel level)) {
            return;
        }

        CorruptionEffectStack stack = serverStack(level);
        if (usesSaveStableVisualWorldgen(stack)) {
            return;
        }
        String targetId = "fluid_source:" + blockTargetId(event.getState());
        float intensity = stack.targetIntensity(CorruptionSurface.WORLDGEN_SURFACE, targetId);
        if (intensity <= MIN_WORLD_PROCESS_INTENSITY) {
            return;
        }

        int salt = event.getPos().hashCode();
        boolean deny = CorruptionValueMutator.decision(stack, CorruptionSurface.WORLDGEN_SURFACE, targetId + ":deny", salt ^ 0x55, 0.46F + intensity * 0.26F);
        if (deny) {
            event.setResult(Event.Result.DENY);
            return;
        }

        float allowChance = stack.extreme(CorruptionSurface.WORLDGEN_SURFACE)
                ? 0.22F
                : Mth.clamp(0.015F + intensity * 0.12F + stack.instability() * 0.035F, 0.0F, 0.18F);
        boolean allow = CorruptionValueMutator.decision(stack, CorruptionSurface.WORLDGEN_SURFACE, targetId + ":allow", salt, allowChance);
        if (allow && claimWorldProcessMutation(level) && claimFluidSourceAllow(level)) {
            event.setResult(Event.Result.ALLOW);
        }
    }

    @SubscribeEvent
    public static void onBlockBreak(BlockEvent.BreakEvent event) {
        if (!(event.getLevel() instanceof ServerLevel level)) {
            return;
        }

        CorruptionEffectStack stack = serverStack(level);
        String targetId = "block_break:" + blockTargetId(event.getState());
        if (!targetActive(stack, CorruptionSurface.BLOCK_COLLISION, targetId, MIN_WORLD_PROCESS_INTENSITY)) {
            return;
        }

        int exp = Math.round(CorruptionValueMutator.mutateScalar(stack, CorruptionSurface.BLOCK_COLLISION, targetId + ":xp", event.getExpToDrop(), 18.0F, 0.0F, 120.0F, 0x66, level.getGameTime()));
        event.setExpToDrop(exp);
    }

    @SubscribeEvent
    public static void onItemCrafted(PlayerEvent.ItemCraftedEvent event) {
        if (!(event.getEntity() instanceof ServerPlayer player)) {
            return;
        }
        ItemStack crafted = event.getCrafting();
        if (crafted == null || crafted.isEmpty()) {
            return;
        }

        CorruptionEffectStack stack = serverStack(player.serverLevel());
        if (craftingSystemBroken(stack, player.serverLevel())) {
            crafted.setCount(0);
        }
    }

    @SubscribeEvent
    public static void onNeighborNotify(BlockEvent.NeighborNotifyEvent event) {
        if (!(event.getLevel() instanceof ServerLevel level)) {
            return;
        }

        CorruptionEffectStack stack = serverStack(level);
        String targetId = "redstone_neighbor_notify:" + blockTargetId(event.getState());
        if (!RedstoneCorruptionMechanics.active(stack, targetId, MIN_WORLD_PROCESS_INTENSITY)) {
            return;
        }

        float intensity = RedstoneCorruptionMechanics.intensity(stack, targetId);
        long hash = stack.stableLong(CorruptionSurface.REDSTONE_MECHANICS, targetId, event.getPos().hashCode() ^ 0x4E4F5449)
                ^ mixLong(event.getPos().asLong() ^ level.getGameTime() * 0x9E3779B97F4A7C15L);
        float chance = stack.extreme(CorruptionSurface.REDSTONE_MECHANICS)
                ? 0.74F
                : Mth.clamp(0.03F + intensity * 0.48F + stack.instability() * 0.08F, 0.0F, 0.62F);
        if (unitHash(hash ^ 0x4E4F5449L) < chance && claimWorldProcessMutation(level)) {
            event.setCanceled(true);
        }
    }

    @SubscribeEvent
    public static void onCropGrowPre(BlockEvent.CropGrowEvent.Pre event) {
        if (!(event.getLevel() instanceof ServerLevel level)) {
            return;
        }

        CorruptionEffectStack stack = serverStack(level);
        if (!shouldMutateWorldProcess(level, stack, CorruptionSurface.WORLDGEN_SURFACE, "growth_tick", event.getState(), event.getPos(), 0x47524F57, 0.82F)) {
            return;
        }

        long hash = worldProcessHash(stack, CorruptionSurface.WORLDGEN_SURFACE, "growth_tick_result", event.getState(), event.getPos(), 0x52455355);
        event.setResult(unitHash(hash) < 0.78F ? Event.Result.DENY : Event.Result.ALLOW);
    }

    @SubscribeEvent
    public static void onBlockToolModification(BlockEvent.BlockToolModificationEvent event) {
        if (event.isSimulated() || !(event.getLevel() instanceof ServerLevel level)) {
            return;
        }

        CorruptionEffectStack stack = serverStack(level);
        if (!shouldMutateWorldProcess(level, stack, CorruptionSurface.INTERACTION_ROUTING, "tool_state_transform", event.getState(), event.getPos(), 0x544F4F4C, 0.66F)) {
            return;
        }

        long stateHash = stableHash(level.getSeed(), event.getPos().getX(), event.getPos().getZ(), event.getPos().getY());
        event.setFinalState(mutatedNearbyBlockState(level, event.getPos(), stateHash, stack, event.getState(), false));
    }

    @SubscribeEvent
    public static void onFarmlandTrample(BlockEvent.FarmlandTrampleEvent event) {
        if (!(event.getLevel() instanceof ServerLevel level)) {
            return;
        }

        CorruptionEffectStack stack = serverStack(level);
        if (shouldMutateWorldProcess(level, stack, CorruptionSurface.BLOCK_COLLISION, "impact_state_transform", event.getState(), event.getPos(), 0x54414D50, 0.58F)) {
            event.setCanceled(true);
        }
    }

    private static boolean craftingSystemBroken(CorruptionEffectStack stack, Level level) {
        if (!craftingMechanicsActive(stack)) {
            return false;
        }

        String targetId = "crafting_system";
        float intensity = craftingIntensity(stack);
        long dimensionSalt = level == null ? 0L : level.dimension().location().hashCode();
        long hash = stack.stableLong(CorruptionSurface.GUI_FUNCTIONALITY, targetId, (int) (dimensionSalt ^ 0x43524654));
        float chance = stack.extreme(CorruptionSurface.GUI_FUNCTIONALITY)
                ? 0.96F
                : Mth.clamp(0.04F + intensity * 0.62F + stack.instability() * 0.10F, 0.0F, 0.82F);
        return unitHash(hash ^ 0x42524F4BL) < chance;
    }

    private static boolean craftingMechanicsActive(CorruptionEffectStack stack) {
        return surfaceActive(stack, CorruptionSurface.GUI_FUNCTIONALITY, MIN_WORLD_PROCESS_INTENSITY);
    }

    private static float craftingIntensity(CorruptionEffectStack stack) {
        return Mth.clamp(stack.extreme(CorruptionSurface.GUI_FUNCTIONALITY) ? 1.0F : stack.intensity(CorruptionSurface.GUI_FUNCTIONALITY), 0.0F, 1.0F);
    }

    private static CorruptionEffectStack redstoneStack(BlockGetter level) {
        if (level instanceof ServerLevel serverLevel) {
            return serverStack(serverLevel);
        }
        if (level instanceof Level gameLevel && gameLevel.isClientSide) {
            return clientStack();
        }
        return CorruptionEffectStack.local(0);
    }

    private static long redstoneGameTime(BlockGetter level) {
        return level instanceof Level gameLevel ? gameLevel.getGameTime() : 0L;
    }

    private static CorruptionEffectStack blockEntityStack(BlockEntity blockEntity) {
        Level level = blockEntity == null ? null : blockEntity.getLevel();
        if (level instanceof ServerLevel serverLevel) {
            return serverStack(serverLevel);
        }
        if (level != null && level.isClientSide) {
            return clientStack();
        }
        return CorruptionEffectStack.local(0);
    }

    private static boolean guiFunctionalityActive(CorruptionEffectStack stack, String targetId) {
        return surfaceActive(stack, CorruptionSurface.GUI_FUNCTIONALITY, MIN_WORLD_PROCESS_INTENSITY)
                || targetActive(stack, CorruptionSurface.GUI_FUNCTIONALITY, targetId, MIN_WORLD_PROCESS_INTENSITY);
    }

    private static float guiFunctionalityIntensity(CorruptionEffectStack stack, String targetId) {
        return Mth.clamp(weightedSurfaceIntensity(stack, CorruptionSurface.GUI_FUNCTIONALITY, targetId, 1.0F), 0.0F, 1.0F);
    }

    private static String merchantTradeTargetId(Entity merchant, MerchantOffer offer) {
        String owner = merchant == null ? "merchant" : entityTargetId(merchant);
        String result = itemTargetId(offer.getResult());
        String costA = itemTargetId(offer.getCostA());
        String costB = itemTargetId(offer.getCostB());
        return owner + ":" + costA + "+" + costB + "->" + result;
    }

    private static long blockEntitySalt(BlockEntity blockEntity) {
        long pos = blockEntity == null || blockEntity.getBlockPos() == null ? 0L : blockEntity.getBlockPos().asLong();
        ResourceLocation typeId = blockEntity == null ? null : ForgeRegistries.BLOCK_ENTITY_TYPES.getKey(blockEntity.getType());
        long typeHash = typeId == null ? 0L : typeId.toString().hashCode();
        return mixLong(pos ^ typeHash * 0x9E3779B97F4A7C15L);
    }

    private static String blockEntityTargetId(BlockEntity blockEntity) {
        if (blockEntity == null) {
            return "unknown";
        }
        ResourceLocation typeId = ForgeRegistries.BLOCK_ENTITY_TYPES.getKey(blockEntity.getType());
        BlockPos pos = blockEntity.getBlockPos();
        String position = pos == null ? "no_pos" : pos.getX() + "," + pos.getY() + "," + pos.getZ();
        return (typeId == null ? blockEntity.getType().toString() : typeId.toString()) + ":" + position;
    }

    private static String itemTargetId(ItemStack stack) {
        ResourceLocation itemId = stack == null || stack.isEmpty() ? null : ForgeRegistries.ITEMS.getKey(stack.getItem());
        return itemId == null ? "unknown" : itemId.toString();
    }

    private static void mutateDamageEvent(LivingHurtEvent event, CorruptionEffectStack stack) {
        if (isFireDamage(event)) {
            mutateFireDamageEvent(event, stack);
            return;
        }
        if (isFreezingDamage(event)) {
            mutateFreezingDamageEvent(event, stack);
            return;
        }

        boolean drowningMutated = mutateDrowningDamageEvent(event, stack);
        if (event.isCanceled()) {
            return;
        }

        LivingEntity entity = event.getEntity();
        String targetId = "damage_resolution:" + event.getSource().getMsgId() + ":" + entityTargetId(entity);
        if (!targetActive(stack, CorruptionSurface.INTERACTION_ROUTING, targetId, MIN_WORLD_PROCESS_INTENSITY)) {
            return;
        }

        float intensity = stack.targetIntensity(CorruptionSurface.INTERACTION_ROUTING, targetId);
        long clock = entity.level().getGameTime();
        if (CorruptionValueMutator.decision(stack, CorruptionSurface.INTERACTION_ROUTING, targetId + ":cancel", entity.getId(), 0.12F + intensity * 0.18F)) {
            event.setCanceled(true);
            return;
        }

        float span = event.getAmount() * (0.45F + intensity * 1.60F) + intensity * 5.0F;
        float amount = CorruptionValueMutator.mutateScalar(stack, CorruptionSurface.INTERACTION_ROUTING, targetId + ":amount", event.getAmount(), span, 0.0F, 512.0F, 0x4447, clock);
        if (drowningMutated && "drown".equals(event.getSource().getMsgId())) {
            amount = Mth.clamp(amount, 0.0F, 768.0F);
        }
        event.setAmount(amount);
    }

    private static boolean mutateFireDamageEvent(LivingHurtEvent event, CorruptionEffectStack stack) {
        LivingEntity entity = event.getEntity();
        String targetId = "fire_damage:" + event.getSource().getMsgId() + ":" + entityTargetId(entity);
        if (!fireMechanicsActive(stack, targetId)) {
            return false;
        }

        float intensity = fireMechanicsIntensity(stack, targetId);
        long hash = stack.stableLong(CorruptionSurface.FIRE_MECHANICS, targetId, entityStableSalt(entity, Float.floatToIntBits(event.getAmount()) ^ 0x464449));
        if (unitHash(hash ^ 0x43414E43L) < Mth.clamp(0.08F + intensity * 0.36F, 0.0F, 0.72F)) {
            event.setCanceled(true);
            return true;
        }

        double multiplier = Math.pow(2.0D, signedUnit(hash ^ 0x4D554C54L) * (0.18D + intensity * (stack.extreme(CorruptionSurface.FIRE_MECHANICS) ? 7.2D : 4.8D)));
        if (unitHash(hash ^ 0x5354414CL) < 0.10F + intensity * (stack.extreme(CorruptionSurface.FIRE_MECHANICS) ? 0.78F : 0.48F)) {
            int mode = Math.floorMod((int) (hash >>> 27), 6);
            multiplier = switch (mode) {
                case 0 -> 0.0D;
                case 1 -> 0.02D + unitHash(hash ^ 0x534C4F57L) * 0.16D;
                case 2 -> 5.0D + unitHash(hash ^ 0x46415354L) * (40.0D + intensity * 180.0D);
                case 3 -> Mth.clamp(multiplier * (0.08D + unitHash(hash ^ 0x53544152L) * 0.42D), 0.0D, 5.0D);
                case 4 -> Mth.clamp(multiplier * (5.0D + unitHash(hash ^ 0x42555253L) * 56.0D), 0.0D, 320.0D);
                default -> multiplier;
            };
        }

        float amount = (float) Mth.clamp(event.getAmount() * multiplier, 0.0D, stack.extreme(CorruptionSurface.FIRE_MECHANICS) ? 768.0D : 320.0D);
        if (amount <= 0.0F) {
            event.setCanceled(true);
        } else {
            event.setAmount(amount);
        }
        return true;
    }

    private static boolean mutateFreezingDamageEvent(LivingHurtEvent event, CorruptionEffectStack stack) {
        LivingEntity entity = event.getEntity();
        String targetId = "powder_snow_damage:" + entityTargetId(entity);
        if (!powderSnowMechanicsActive(stack, targetId)) {
            return false;
        }

        float intensity = powderSnowMechanicsIntensity(stack, targetId);
        long hash = stack.stableLong(CorruptionSurface.POWDER_SNOW_MECHANICS, targetId, entityStableSalt(entity, Float.floatToIntBits(event.getAmount()) ^ 0x465244));
        if (unitHash(hash ^ 0x43414E43L) < Mth.clamp(0.08F + intensity * 0.34F, 0.0F, 0.70F)) {
            event.setCanceled(true);
            return true;
        }

        double multiplier = Math.pow(2.0D, signedUnit(hash ^ 0x4D554C54L) * (0.16D + intensity * (stack.extreme(CorruptionSurface.POWDER_SNOW_MECHANICS) ? 7.0D : 4.6D)));
        if (unitHash(hash ^ 0x5354414CL) < 0.10F + intensity * (stack.extreme(CorruptionSurface.POWDER_SNOW_MECHANICS) ? 0.78F : 0.48F)) {
            int mode = Math.floorMod((int) (hash >>> 27), 6);
            multiplier = switch (mode) {
                case 0 -> 0.0D;
                case 1 -> 0.02D + unitHash(hash ^ 0x534C4F57L) * 0.16D;
                case 2 -> 4.0D + unitHash(hash ^ 0x46415354L) * (34.0D + intensity * 160.0D);
                case 3 -> Mth.clamp(multiplier * (0.08D + unitHash(hash ^ 0x53544152L) * 0.42D), 0.0D, 5.0D);
                case 4 -> Mth.clamp(multiplier * (4.0D + unitHash(hash ^ 0x42555253L) * 48.0D), 0.0D, 300.0D);
                default -> multiplier;
            };
        }

        float amount = (float) Mth.clamp(event.getAmount() * multiplier, 0.0D, stack.extreme(CorruptionSurface.POWDER_SNOW_MECHANICS) ? 768.0D : 300.0D);
        if (amount <= 0.0F) {
            event.setCanceled(true);
        } else {
            event.setAmount(amount);
        }
        return true;
    }

    private static boolean mutateDrowningDamageEvent(LivingHurtEvent event, CorruptionEffectStack stack) {
        if (!"drown".equals(event.getSource().getMsgId())) {
            return false;
        }

        LivingEntity entity = event.getEntity();
        boolean player = entity instanceof Player;
        String targetId = "drowning_rate:" + entityTargetId(entity);
        if (!airSupplyMechanicsActive(stack, targetId, player)) {
            return false;
        }

        CorruptionSurface surface = airSupplySurface(stack, targetId, player);
        float intensity = airSupplyIntensity(stack, targetId, player);
        if (intensity <= MIN_AIR_MECHANICS_INTENSITY) {
            return false;
        }

        long hash = stack.stableLong(surface, targetId, entity.getId() ^ entity.getAirSupply() ^ 0x44524F57);
        double multiplier = Math.pow(2.0D, signedUnit(hash ^ 0x4D554C54L) * (0.20D + intensity * (stack.extreme(surface) ? 8.0D : 5.4D)));

        if (unitHash(hash ^ 0x5354414CL) < 0.10F + intensity * (stack.extreme(surface) ? 0.78F : 0.48F)) {
            int mode = Math.floorMod((int) (hash >>> 26), 6);
            multiplier = switch (mode) {
                case 0 -> 0.0D;
                case 1 -> 0.02D + unitHash(hash ^ 0x534C4F57L) * 0.18D;
                case 2 -> 6.0D + unitHash(hash ^ 0x46415354L) * (34.0D + intensity * 140.0D);
                case 3 -> Mth.clamp(multiplier * (0.05D + unitHash(hash ^ 0x53544152L) * 0.35D), 0.0D, 4.0D);
                case 4 -> Mth.clamp(multiplier * (4.0D + unitHash(hash ^ 0x42555253L) * 48.0D), 0.0D, 256.0D);
                default -> multiplier;
            };
        }

        float amount = (float) Mth.clamp(event.getAmount() * multiplier, 0.0D, stack.extreme(surface) ? 768.0D : 256.0D);
        if (amount <= 0.0F) {
            event.setCanceled(true);
        } else {
            event.setAmount(amount);
        }
        return true;
    }

    private static void mutateLiquidMechanics(ServerPlayer player, CorruptionEffectStack stack) {
        boolean actualWater = isTouchingFluid(player, FluidTags.WATER);
        boolean actualLava = isTouchingFluid(player, FluidTags.LAVA);
        boolean detectedWater = player.isInWater() || isEyeTouchingFluid(player, FluidTags.WATER);
        boolean detectedLava = player.isInLava() || isEyeTouchingFluid(player, FluidTags.LAVA);
        boolean lava = actualLava || detectedLava;
        String fluid = lava ? "lava" : "water";
        String targetId = "liquid_mechanics:" + fluid + ":" + playerTargetId(player);

        if (!liquidMechanicsActive(stack, targetId)) {
            return;
        }

        CorruptionSurface surface = liquidSurface(stack, targetId);
        lava = actualLava || detectedLava;
        fluid = lava ? "lava" : "water";
        targetId = "liquid_mechanics:" + fluid + ":" + playerTargetId(player);
        float intensity = liquidMechanicsIntensity(stack, targetId);
        long faultSeed = fluidFeatureSeed(stack, surface, targetId);
        int bucket = fluidPositionBucket(player, faultSeed, intensity);
        float flowScale = fluidFlowScale(faultSeed, intensity);
        boolean nearLiquid = actualWater || actualLava || detectedWater || detectedLava || isNearFluid(player, FluidTags.WATER) || isNearFluid(player, FluidTags.LAVA);
        if (!nearLiquid && !(player.getVehicle() instanceof Boat)) {
            return;
        }

        if (stack.unit(surface, targetId + ":current_feature", bucket) < Mth.clamp((0.10F + intensity * 0.62F) * flowScale, 0.0F, 0.92F)) {
            mutateLiquidCollision(player, stack, targetId, surface, faultSeed, intensity);
        }

        if (player.getVehicle() instanceof Boat boat) {
            mutateBoatLiquidMechanics(boat, player, stack);
        }
    }

    private static void mutateLiquidCollision(Entity entity, CorruptionEffectStack stack, String targetId, CorruptionSurface surface, long faultSeed, float intensity) {
        int bucket = fluidPositionBucket(entity, faultSeed, intensity);
        long clock = stack.stableLong(surface, targetId + ":flow_clock", bucket);
        Vec3 motion = entity.getDeltaMovement();
        float flowScale = fluidFlowScale(faultSeed, intensity);
        Vec3 mutated = CorruptionValueMutator.mutateVector(stack, surface, targetId + ":flow", motion, (0.008D + intensity * 0.14D) * flowScale, 2.2D, entity.getId(), clock);
        long seed = stack.stableLong(surface, targetId, entity.getId() ^ 0x4C495155);
        int period = Math.max(3, 18 - Math.round(intensity * 12.0F));
        float phase = ((entity.tickCount + (seed & 0x3FL)) % period) / (float) period;
        float pulse = collisionPulse(seed ^ 0x464C4F57L, phase);
        int mode = Math.floorMod((int) (seed >>> 28) + fluidFlowMode(faultSeed), 7);
        switch (mode) {
            case 0 -> mutated = mutated.multiply(0.05D + intensity * 0.55D, 0.70D + intensity * 0.60D, 0.05D + intensity * 0.55D);
            case 1 -> mutated = mutated.add(signedUnit(seed ^ bucket) * intensity * 0.24D, -0.08D - intensity * (0.34D + Math.abs(pulse) * 0.40D), signedUnit(seed ^ 0x5A4C4951L ^ bucket) * intensity * 0.24D);
            case 2 -> mutated = mutated.add(motion.z * intensity * (0.80D + Math.abs(pulse)), 0.04D + intensity * (0.38D + Math.abs(pulse) * 0.48D), -motion.x * intensity * (0.80D + Math.abs(pulse)));
            case 3 -> {
                double quant = 0.015D + intensity * 0.16D;
                mutated = new Vec3(Math.rint(mutated.x / quant) * quant, Math.rint(mutated.y / quant) * quant, Math.rint(mutated.z / quant) * quant);
            }
            case 4 -> mutated = new Vec3(
                    Math.copySign(Math.max(Math.abs(mutated.x), 0.05D + intensity * 0.34D), signedUnit(seed ^ 0x58534C49L ^ bucket)),
                    mutated.y * (0.08D + intensity * 0.52D),
                    Math.copySign(Math.max(Math.abs(mutated.z), 0.05D + intensity * 0.34D), signedUnit(seed ^ 0x5A534C49L ^ bucket))
            );
            case 5 -> mutated = mutated.scale(0.22D + intensity * 2.30D + Math.abs(pulse) * 0.85D);
            default -> mutated = new Vec3(mutated.x, -Math.abs(mutated.y) - intensity * 0.48D, mutated.z).scale(0.50D + intensity);
        }

        if (mutated.distanceToSqr(motion) > 1.0E-7D) {
            entity.setDeltaMovement(mutated);
            entity.hasImpulse = true;
        }
    }

    private static void mutateBoatLiquidMechanics(Boat boat, ServerPlayer player, CorruptionEffectStack stack) {
        String targetId = "boat_liquid_mechanics:" + entityTargetId(boat) + ":" + player.getUUID();
        if (!liquidMechanicsActive(stack, targetId)) {
            return;
        }

        CorruptionSurface surface = liquidSurface(stack, targetId);
        float intensity = liquidMechanicsIntensity(stack, targetId);
        long faultSeed = fluidFeatureSeed(stack, surface, "boat_liquid_mechanics:" + player.getUUID());
        int bucket = fluidPositionBucket(boat, faultSeed, intensity);
        long clock = stack.stableLong(surface, targetId + ":boat_clock", bucket);
        long seed = stack.stableLong(surface, targetId, boat.getId() ^ 0x424F4154);
        Vec3 motion = boat.getDeltaMovement();
        Vec3 mutated = CorruptionValueMutator.mutateVector(stack, surface, targetId + ":velocity", motion, (0.010D + intensity * 0.20D) * fluidBoatScale(faultSeed, intensity), 3.0D, boat.getId(), clock);
        int mode = Math.floorMod((int) (seed >>> 31) + fluidFlowMode(faultSeed), 6);
        switch (mode) {
            case 0 -> mutated = mutated.add(signedUnit(seed ^ bucket) * intensity * 0.32D, signedUnit(seed ^ 0x5957494EL ^ bucket) * intensity * 0.18D, signedUnit(seed ^ 0x5A57494EL ^ bucket) * intensity * 0.32D);
            case 1 -> mutated = mutated.multiply(0.05D + intensity * 0.60D, 0.12D + intensity * 0.80D, 0.05D + intensity * 0.60D);
            case 2 -> mutated = new Vec3(-motion.z, motion.y + signedUnit(seed ^ bucket) * intensity * 0.20D, motion.x).scale(0.45D + intensity * 1.85D);
            case 3 -> mutated = mutated.add(0.0D, -0.14D - intensity * 0.52D, 0.0D);
            case 4 -> mutated = mutated.add(0.0D, 0.08D + intensity * 0.64D, 0.0D);
            default -> mutated = mutated.scale(0.20D + intensity * 2.60D);
        }
        boat.setDeltaMovement(mutated);
        boat.hasImpulse = true;
    }

    private static boolean isTouchingFluid(Entity entity, TagKey<Fluid> fluidTag) {
        return isFluidAround(entity, fluidTag, 0.04D);
    }

    private static boolean isNearFluid(Entity entity, TagKey<Fluid> fluidTag) {
        return isFluidAround(entity, fluidTag, 0.72D);
    }

    private static boolean isFluidAround(Entity entity, TagKey<Fluid> fluidTag, double inflate) {
        Level level = entity.level();
        AABB box = entity.getBoundingBox().inflate(inflate);
        int minX = Mth.floor(box.minX);
        int maxX = Mth.ceil(box.maxX);
        int minY = Math.max(level.getMinBuildHeight(), Mth.floor(box.minY));
        int maxY = Math.min(level.getMaxBuildHeight(), Mth.ceil(box.maxY));
        int minZ = Mth.floor(box.minZ);
        int maxZ = Mth.ceil(box.maxZ);
        if (minY >= maxY) {
            return false;
        }

        BlockPos.MutableBlockPos mutable = new BlockPos.MutableBlockPos();
        for (int x = minX; x < maxX; x++) {
            for (int y = minY; y < maxY; y++) {
                for (int z = minZ; z < maxZ; z++) {
                    mutable.set(x, y, z);
                    FluidState state = loadedFluidState(level, mutable);
                    if (state.is(fluidTag)) {
                        return true;
                    }
                }
            }
        }
        return false;
    }

    private static boolean isEyeTouchingFluid(Entity entity, TagKey<Fluid> fluidTag) {
        if (entity == null || entity.level() == null || fluidTag == null) {
            return false;
        }

        Level level = entity.level();
        double eyeY = entity.getEyeY() - 0.11111111D;
        BlockPos pos = BlockPos.containing(entity.getX(), eyeY, entity.getZ());
        FluidState state = loadedFluidState(level, pos);
        return state.is(fluidTag) && eyeY < pos.getY() + state.getHeight(level, pos);
    }

    private static void mutateDayTime(ServerLevel level, CorruptionEffectStack stack) {
        String targetId = "day_time:" + level.dimension().location();
        boolean tickSpeedActive = targetActive(stack, CorruptionSurface.TICK_SPEED, targetId, MIN_DAY_TIME_INTENSITY);
        if (!tickSpeedActive) {
            return;
        }

        double speed = gameSpeedMultiplier(stack, targetId);
        long offset = Math.round((speed - 1.0D) * 5.0D);
        if (offset != 0L) {
            level.setDayTime(level.getDayTime() + offset);
            return;
        }

        if (!CorruptionValueMutator.decision(stack, CorruptionSurface.TICK_SPEED, targetId + ":tick", (int) level.getDayTime(), 0.94F)) {
            return;
        }

        float intensity = Math.max(stack.targetIntensity(CorruptionSurface.TICK_SPEED, targetId), stack.intensity(CorruptionSurface.TICK_SPEED));
        long mutatedOffset = Math.round(CorruptionValueMutator.mutateScalar(stack, CorruptionSurface.TICK_SPEED, targetId + ":offset", 0.0D, 800.0D + intensity * 8400.0D, -12000.0D, 12000.0D, 0x5449, level.getGameTime()));
        if (Math.abs(mutatedOffset) >= 20L) {
            level.setDayTime(level.getDayTime() + mutatedOffset);
        }
    }

    private static void mutateWeather(ServerLevel level, CorruptionEffectStack stack) {
        String targetId = "weather_system:" + level.dimension().location();
        boolean worldRenderActive = targetActive(stack, CorruptionSurface.WORLD_RENDER, targetId, MIN_WORLD_PROCESS_INTENSITY);
        if (!worldRenderActive) {
            return;
        }

        float intensity = Mth.clamp(stack.extreme(CorruptionSurface.WORLD_RENDER) ? 1.0F : stack.intensity(CorruptionSurface.WORLD_RENDER), 0.0F, 1.0F);
        long hash = stack.stableLong(CorruptionSurface.WORLD_RENDER, targetId, (int) level.getGameTime() ^ 0x57454154);
        float chance = Mth.clamp(0.04F + intensity * 0.72F + stack.instability() * 0.10F, 0.0F, 0.96F);
        if (!stack.extreme(CorruptionSurface.WORLD_RENDER) && unitHash(hash ^ 0x5449434BL) > chance) {
            return;
        }

        captureWeatherBeforeWorldVisualsMutation(level);
        int mode = Math.floorMod((int) (hash >>> 32), 5);
        if (mode == 0) {
            boolean storm = (level.getGameTime() >> 3 & 1L) == 0L;
            level.setWeatherParameters(storm ? 0 : 1, storm ? 2 : 1, storm, storm && unitHash(hash ^ 0x54484E44L) < 0.55F + intensity * 0.35F);
            return;
        }
        if (mode == 1 || stack.extreme(CorruptionSurface.WORLD_RENDER)) {
            int duration = 12000 + Math.round(unitHash(hash ^ 0x44555241L) * 240000.0F);
            level.setWeatherParameters(0, duration, true, unitHash(hash ^ 0x5448554EL) < 0.32F + intensity * 0.58F);
            return;
        }
        if (mode == 2) {
            int clearTime = 1 + Math.round(unitHash(hash ^ 0x434C4541L) * 20.0F);
            level.setWeatherParameters(clearTime, 0, false, false);
            return;
        }

        int rainTime = 20 + Math.round(unitHash(hash ^ 0x5241494EL) * (2400.0F + intensity * 36000.0F));
        boolean raining = unitHash(hash ^ 0x5241494FL) < 0.28F + intensity * 0.56F;
        boolean thundering = raining && unitHash(hash ^ 0x54484E44L) < intensity * 0.46F;
        level.setWeatherParameters(raining ? 0 : rainTime, rainTime, raining, thundering);
    }

    private static void captureWeatherBeforeWorldVisualsMutation(ServerLevel level) {
        MinecraftServer server = level.getServer();
        if (server == null) {
            return;
        }
        if (!(level.getLevelData() instanceof ServerLevelData weather)) {
            return;
        }
        CorruptionSavedData.get(server).captureWeatherSnapshot(
                weatherDimensionKey(level),
                weather.getClearWeatherTime(),
                weather.getRainTime(),
                weather.getThunderTime(),
                weather.isRaining(),
                weather.isThundering()
        );
    }

    private static void restoreCapturedWeather(MinecraftServer server, CorruptionSavedData data) {
        if (server == null || data == null) {
            return;
        }
        for (ServerLevel level : server.getAllLevels()) {
            restoreCapturedWeather(level, data);
        }
    }

    private static void restoreCapturedWeather(ServerLevel level, CorruptionSavedData data) {
        if (level == null || data == null) {
            return;
        }
        CorruptionSavedData.WeatherSnapshot snapshot = data.consumeWeatherSnapshot(weatherDimensionKey(level));
        if (snapshot == null) {
            return;
        }
        if (!(level.getLevelData() instanceof ServerLevelData weather)) {
            return;
        }
        weather.setClearWeatherTime(snapshot.clearWeatherTime());
        weather.setRainTime(snapshot.rainTime());
        weather.setThunderTime(snapshot.thunderTime());
        weather.setRaining(snapshot.raining());
        weather.setThundering(snapshot.thundering());
    }

    private static boolean weatherCorruptionConfigured(int corruptionLevel, int enabledTargetsMask) {
        return corruptionLevel > 0 && CorruptionTarget.enabled(enabledTargetsMask, CorruptionTarget.WORLD_VISUALS);
    }

    private static String weatherDimensionKey(ServerLevel level) {
        return level.dimension().location().toString();
    }

    private static void applyAutoIncrease(MinecraftServer server) {
        if (server == null || server.getPlayerList().getPlayerCount() <= 0) {
            return;
        }

        CorruptionSavedData data = CorruptionSavedData.get(server);
        int intervalTicks = data.getAutoIncreaseIntervalTicks();
        int amount = data.getAutoIncreaseAmount();
        long clock = Math.max(0L, server.overworld().getGameTime());

        boolean changed = false;
        if (intervalTicks > 0 && amount != 0) {
            long last = data.getLastAutoIncreaseGameTime();
            if (last > clock || last <= 0L) {
                data.setLastAutoIncreaseGameTime(clock);
            } else if (clock - last >= intervalTicks) {
                int current = data.getCorruptionLevel();
                int next = clampInt(current + amount, 0, 100);
                data.setLastAutoIncreaseGameTime(clock);
                if (next != current) {
                    data.setCorruptionLevel(next);
                    changed = true;
                }
            }
        }

        if (applySeedRandomizer(data, clock)) {
            changed = true;
        }

        if (changed) {
            CorruptionRuntimeManager.applySavedDataToGlobalSettings(data);
            clearServerStackCache();
            ModNetwork.broadcastState(server);
        }
    }

    private static void resetAutoIncreaseTimer(MinecraftServer server) {
        if (server == null) {
            return;
        }
        CorruptionSavedData data = CorruptionSavedData.get(server);
        data.setLastAutoIncreaseGameTime(Math.max(0L, server.overworld().getGameTime()));
    }

    private static boolean applySeedRandomizer(CorruptionSavedData data, long clock) {
        int intervalTicks = data.getSeedRandomizerIntervalTicks();
        if (intervalTicks <= 0) {
            return false;
        }

        long last = data.getLastSeedRandomizerGameTime();
        if (last > clock || last <= 0L) {
            data.setLastSeedRandomizerGameTime(clock);
            return false;
        }
        if (clock - last < intervalTicks) {
            return false;
        }

        long nextSeed = ThreadLocalRandom.current().nextLong();
        data.setCorruptionSeed(nextSeed, CorruptionSavedData.seedLabel(nextSeed));
        data.setLastSeedRandomizerGameTime(clock);
        return true;
    }

    private static void resetSeedRandomizerTimer(MinecraftServer server) {
        if (server == null) {
            return;
        }
        CorruptionSavedData data = CorruptionSavedData.get(server);
        data.setLastSeedRandomizerGameTime(Math.max(0L, server.overworld().getGameTime()));
    }

    private static boolean shouldSyncEntityMechanics(LivingEntity entity, CorruptionEffectStack stack, boolean player) {
        return EntityAttributeCorruptionMechanics.shouldSync(entity, stack, player);
    }

    private static void forceSyncPlayerMechanics(ServerPlayer player) {
        if (player == null) {
            return;
        }
        forceSyncEntityMechanics(player, serverGameplayStack(player.serverLevel()), entityTargetId(player));
    }

    private static void forceSyncEntityMechanics(LivingEntity entity, CorruptionEffectStack stack, String entityTargetId) {
        EntityAttributeCorruptionMechanics.forceSync(entity, stack, entityTargetId);
    }

    private static void syncEntityMechanics(LivingEntity entity, CorruptionEffectStack stack, String entityTargetId) {
        EntityAttributeCorruptionMechanics.sync(entity, stack, entityTargetId);
    }

    private static float mobilityMotionIntensity(CorruptionEffectStack stack, String targetId) {
        return EntityAttributeCorruptionMechanics.mobilityMotionIntensity(stack, targetId);
    }

    private static int corruptedCadence(CorruptionEffectStack stack, String targetId, int baseCadence, int maxCadence) {
        if (!surfaceActive(stack, CorruptionSurface.TICK_SPEED, MIN_DAY_TIME_INTENSITY)) {
            return Math.max(1, baseCadence);
        }
        double speed = gameSpeedMultiplier(stack, targetId);
        if (speed >= 1.0D) {
            return Math.max(1, (int) Math.round(baseCadence / Mth.clamp(speed, 1.0D, 12.0D)));
        }
        return Mth.clamp((int) Math.round(baseCadence / Math.max(0.05D, speed)), 1, maxCadence);
    }

    private static double gameSpeedMultiplier(CorruptionEffectStack stack, String targetId) {
        if (!surfaceActive(stack, CorruptionSurface.TICK_SPEED, MIN_DAY_TIME_INTENSITY)) {
            return 1.0D;
        }
        float intensity = stack.extreme(CorruptionSurface.TICK_SPEED)
                ? 1.0F
                : Math.max(stack.targetIntensity(CorruptionSurface.TICK_SPEED, targetId), stack.intensity(CorruptionSurface.TICK_SPEED));
        long hash = stack.stableLong(CorruptionSurface.TICK_SPEED, "game_speed:" + targetId, 0x47414D45);
        int mode = Math.floorMod((int) (hash >>> 28), 6);
        double curve = unitHash(hash ^ 0x43555256L);
        double exponent = switch (mode) {
            case 0 -> -5.0D * intensity * (0.35D + curve * 0.65D);
            case 1 -> 7.0D * intensity * (0.25D + curve * 0.75D);
            case 2 -> (curve * 2.0D - 1.0D) * 6.0D * intensity;
            case 3 -> unitHash(hash ^ 0x53544F50L) < intensity * 0.34F ? -8.0D * intensity : 4.0D * intensity * curve;
            case 4 -> Math.rint((curve * 2.0D - 1.0D) * 5.0D * intensity);
            default -> 5.0D * intensity * (0.20D + curve * 0.80D);
        };
        double multiplier = Math.pow(2.0D, exponent);
        if (unitHash(hash ^ 0x5354414CL) < intensity * 0.12F) {
            multiplier *= 0.05D + unitHash(hash ^ 0x534C4F57L) * 0.20D;
        }
        return Mth.clamp(multiplier, 0.02D, 160.0D);
    }

    private static void mutateLivingState(LivingEntity entity, CorruptionEffectStack stack, String targetId) {
        if (!targetActive(stack, CorruptionSurface.ENTITY_STATE, targetId, MIN_ENTITY_MECHANICS_INTENSITY)) {
            return;
        }

        float intensity = stack.targetIntensity(CorruptionSurface.ENTITY_STATE, targetId);
        long seed = stack.stableLong(CorruptionSurface.ENTITY_STATE, targetId, entity.getId() ^ 0x454E5449);
        int bucket = collisionPositionBucket(entity, seed, intensity);
        Vec3 motion = entity.getDeltaMovement();
        Vec3 mutatedMotion = mutateFeatureVelocity(stack, CorruptionSurface.ENTITY_STATE, targetId + ":motion", motion, seed, bucket, intensity, 0.012D, 0.18D, 2.4D);
        if (mutatedMotion.distanceToSqr(motion) > 1.0E-7D) {
            entity.setDeltaMovement(mutatedMotion);
            entity.hasImpulse = true;
        }
    }

    private static void mutateEntityCollision(Entity entity, CorruptionEffectStack stack, String targetId) {
        mutateSharedBlockCollision(entity, stack, "collision:" + targetId, MIN_ENTITY_MECHANICS_INTENSITY);
    }

    private static void mutatePlayerCollision(Player player, CorruptionEffectStack stack) {
        mutateSharedBlockCollision(player, stack, "collision:" + playerTargetId(player), MIN_COLLISION_MECHANICS_INTENSITY);
    }

    private static void mutateSharedBlockCollision(Entity entity, CorruptionEffectStack stack, String targetId, float minimumIntensity) {
        if (!collisionMechanicsActive(stack, targetId) || entity.isSpectator()) {
            return;
        }

        float intensity = collisionMechanicsIntensity(stack, targetId, 0.78F);
        long seed = stack.stableLong(CorruptionSurface.BLOCK_COLLISION, targetId, entity.getId() ^ 0x434F4C4C);
        int bucket = collisionPositionBucket(entity, seed, intensity);
        float phase = unitHash(seed ^ bucket);
        float pulse = collisionPulse(seed, phase);
        float collisionPressure = progressiveCollisionPressure(stack, intensity);

        Vec3 motion = entity.getDeltaMovement();
        boolean push = stack.unit(CorruptionSurface.BLOCK_COLLISION, targetId + ":push", bucket) < Math.min(0.88F, collisionPressure * 0.62F + Math.abs(pulse) * intensity * 0.28F);
        if (push) {
            double span = 0.018D + intensity * (0.22D + Math.abs(pulse) * 0.32D);
            Vec3 mutated = CorruptionValueMutator.mutateVector(stack, CorruptionSurface.BLOCK_COLLISION, targetId + ":motion", motion, span, 4.2D, entity.getId(), stack.stableLong(CorruptionSurface.BLOCK_COLLISION, targetId + ":motion_clock", bucket));
            double verticalBias = signedUnit(seed ^ bucket ^ 0x56455254L) * intensity * 0.18D;
            if (stack.unit(CorruptionSurface.BLOCK_COLLISION, targetId + ":drop", bucket) < intensity * 0.34F) {
                verticalBias -= 0.12D + intensity * 0.58D;
            }
            if (stack.unit(CorruptionSurface.BLOCK_COLLISION, targetId + ":launch", bucket) < intensity * 0.16F) {
                verticalBias += 0.10D + intensity * 0.42D;
            }
            entity.setDeltaMovement(mutated.x, mutated.y + verticalBias, mutated.z);
            entity.hasImpulse = true;
        }

        float phasePressure = collisionPhasePressure(stack, intensity);
        float phaseSlipChance = Mth.clamp(collisionPhaseChance(stack, intensity) * 0.52F + Math.abs(pulse) * intensity * phasePressure * 0.12F, 0.0F, 0.54F);
        if (stack.unit(CorruptionSurface.BLOCK_COLLISION, targetId + ":phase_slip", bucket ^ 0x534C4950) < phaseSlipChance) {
            double down = 0.04D + intensity * (0.32D + unitHash(seed ^ 0x444F574EL) * 0.90D);
            double side = intensity * (0.02D + unitHash(seed ^ 0x53494445L) * 0.18D);
            double dx = signedUnit(seed ^ 0x58504853L) * side;
            double dz = signedUnit(seed ^ 0x5A504853L) * side;
            if (entity.onGround() || unitHash(seed ^ 0x564F4944L) < 0.35F + intensity * 0.48F) {
                entity.setPos(entity.getX() + dx, entity.getY() - down, entity.getZ() + dz);
                Vec3 current = entity.getDeltaMovement();
                entity.setDeltaMovement(current.x + dx * 0.45D, Math.min(current.y, -0.04D - intensity * 0.42D), current.z + dz * 0.45D);
                entity.hasImpulse = true;
            }
        }
    }

    private static float collisionPulse(long seed, float phase) {
        int mode = Math.floorMod((int) (seed >>> 29), 6);
        return switch (mode) {
            case 0 -> (float) Math.sin(phase * Math.PI * 2.0D);
            case 1 -> phase < 0.5F ? phase * 2.0F : (1.0F - phase) * 2.0F;
            case 2 -> phase < 0.70F ? 0.0F : (phase - 0.70F) / 0.30F;
            case 3 -> (float) Math.sin(phase * Math.PI * 6.0D);
            case 4 -> phase < 0.25F ? -1.0F : phase < 0.50F ? 1.0F : phase < 0.75F ? -0.35F : 0.35F;
            default -> signedUnit(seed ^ (long) (phase * 255.0F));
        };
    }

    private static void mutateProjectileEntity(Projectile projectile, CorruptionEffectStack stack, String targetId) {
        if (!targetActive(stack, CorruptionSurface.PROJECTILE_PHYSICS, targetId, MIN_ENTITY_MECHANICS_INTENSITY)) {
            return;
        }

        float intensity = stack.targetIntensity(CorruptionSurface.PROJECTILE_PHYSICS, targetId);
        long seed = stack.stableLong(CorruptionSurface.PROJECTILE_PHYSICS, targetId, projectile.getId() ^ 0x50524F4A);
        int bucket = collisionPositionBucket(projectile, seed, intensity);
        Vec3 velocity = projectile.getDeltaMovement();
        Vec3 mutated = mutateFeatureVelocity(stack, CorruptionSurface.PROJECTILE_PHYSICS, targetId + ":velocity", velocity, seed, bucket, intensity, 0.028D, 0.82D, 9.5D);
        if (unitHash(seed ^ bucket ^ 0x52414E4745L) < 0.20F + intensity * 0.58F) {
            int mode = Math.floorMod((int) (seed >>> 31), 5);
            mutated = switch (mode) {
                case 0 -> mutated.scale(-(0.25D + intensity * 1.75D));
                case 1 -> new Vec3(mutated.z, mutated.y * (0.10D + intensity), -mutated.x).scale(0.60D + intensity * 2.90D);
                case 2 -> mutated.normalize().scale(0.04D + unitHash(seed ^ 0x53504545L) * intensity * 8.0D);
                case 3 -> mutated.multiply(0.02D + intensity * 0.16D, 0.02D + intensity * 0.16D, 0.02D + intensity * 0.16D);
                default -> mutated.add(signedUnit(seed ^ 0x5850524AL) * intensity * 1.8D, signedUnit(seed ^ 0x5950524AL) * intensity * 1.2D, signedUnit(seed ^ 0x5A50524AL) * intensity * 1.8D);
            };
            mutated = clampVector(mutated, 9.5D);
        }
        if (mutated.distanceToSqr(velocity) > 1.0E-7D) {
            projectile.setDeltaMovement(mutated);
            projectile.hasImpulse = true;
        }

    }

    private static void mutateSpawnResult(Event event, CorruptionEffectStack stack, String targetId, int salt, boolean defaultResult) {
        if (!targetActive(stack, CorruptionSurface.SPAWN_RULES, targetId, MIN_SPAWN_MECHANICS_INTENSITY)
                || !CorruptionValueMutator.decision(stack, CorruptionSurface.SPAWN_RULES, targetId + ":flip", salt, 0.72F)) {
            return;
        }

        boolean allow = CorruptionValueMutator.decision(stack, CorruptionSurface.SPAWN_RULES, targetId + ":allow", salt ^ 0x44, 0.62F);
        event.setResult(allow == defaultResult ? Event.Result.DENY : Event.Result.ALLOW);
    }

    private static CorruptionEffectStack activeStackFor(Player player) {
        if (player instanceof ServerPlayer serverPlayer) {
            return serverGameplayStack(serverPlayer.serverLevel());
        }
        return player.level().isClientSide ? clientStack() : CorruptionEffectStack.local(0);
    }

    private static CorruptionEffectStack activeStackFor(Entity entity) {
        if (entity instanceof Player player) {
            return activeStackFor(player);
        }
        Level level = entity.level();
        if (level instanceof ServerLevel serverLevel) {
            return serverGameplayStack(serverLevel);
        }
        return level != null && level.isClientSide
                ? clientStack()
                : CorruptionEffectStack.local(0);
    }

    private static CorruptionEffectStack authoritativeGameplayStackFor(Entity entity) {
        CorruptionEffectStack stack = activeStackFor(entity);
        if (!shouldSuppressClientAuthoritativeGameplay(entity)) {
            return stack;
        }
        return stack.clientDriftEnabled() ? stack : CorruptionEffectStack.local(0);
    }

    private static CorruptionEffectStack mobilityGameplayStackFor(Entity entity) {
        CorruptionEffectStack stack = activeStackFor(entity);
        return shouldSuppressClientAuthoritativeGameplay(entity) && !usesClientPredictedMobility(entity)
                ? CorruptionEffectStack.local(0)
                : stack;
    }

    private static boolean shouldSuppressClientAuthoritativeGameplay(Entity entity) {
        return entity != null
                && entity.level() != null
                && entity.level().isClientSide;
    }

    private static boolean usesClientPredictedMobility(Entity entity) {
        if (entity instanceof Player player) {
            return player.isLocalPlayer();
        }
        return entity.isControlledByLocalInstance();
    }

    private static CorruptionEffectStack serverStack(ServerLevel level) {
        MinecraftServer server = level.getServer();
        if (server == null) {
            return CorruptionEffectStack.local(0);
        }
        if (shouldSuspendServerMutations(server)) {
            return CorruptionEffectStack.local(0);
        }
        return cachedServerStack(server);
    }

    private static CorruptionEffectStack serverGameplayStack(LivingEntity entity) {
        if (!(entity.level() instanceof ServerLevel level) || level.getServer() == null) {
            return CorruptionEffectStack.local(0);
        }
        return serverGameplayStack(level);
    }

    private static CorruptionEffectStack serverGameplayStack(ServerLevel level) {
        MinecraftServer server = level.getServer();
        return server == null ? CorruptionEffectStack.local(0) : cachedServerStack(server);
    }

    public static boolean shouldCancelNavigationMove(Mob mob, Entity target, Vec3 targetPosition, double speed, String route) {
        if (mob == null || mob.level().isClientSide || mob.isNoAi()) {
            return false;
        }
        CorruptionEffectStack stack = serverGameplayStack(mob);
        String targetId = navigationTargetId(mob, target, route);
        if (!navigationActive(stack, targetId)) {
            return false;
        }
        float intensity = navigationIntensity(stack, targetId);
        long seed = stack.stableLong(CorruptionSurface.ENTITY_STATE, targetId, mob.getId() ^ 0x4E415649);
        int bucket = collisionPositionBucket(mob, seed, intensity);
        double targetDistance = targetPosition == null ? 0.0D : mob.position().distanceTo(targetPosition);
        float distancePressure = Mth.clamp((float) (targetDistance / 18.0D), 0.0F, 1.0F);
        float chance = Mth.clamp(0.08F + intensity * 0.74F + stack.instability() * 0.10F + distancePressure * intensity * 0.16F, 0.0F, 0.94F);
        return unitHash(seed ^ bucket ^ 0x4641494CL) < chance;
    }

    public static double corruptNavigationSpeed(Mob mob, double originalSpeed, String route) {
        if (mob == null || mob.level().isClientSide || mob.isNoAi()) {
            return originalSpeed;
        }
        CorruptionEffectStack stack = serverGameplayStack(mob);
        String targetId = navigationTargetId(mob, null, route);
        if (!navigationActive(stack, targetId)) {
            return originalSpeed;
        }
        float intensity = navigationIntensity(stack, targetId);
        long seed = stack.stableLong(CorruptionSurface.ENTITY_KINEMATICS, targetId + ":speed", mob.getId() ^ 0x53504544);
        int mode = Math.floorMod((int) (seed >>> 30), 6);
        double scale = switch (mode) {
            case 0 -> 0.02D + unitHash(seed ^ 0x53544F50L) * intensity * 0.24D;
            case 1 -> -(0.05D + intensity * (0.35D + unitHash(seed ^ 0x52455653L) * 1.85D));
            case 2 -> 1.0D + signedUnit(seed ^ 0x4A495454L) * intensity * 4.5D;
            case 3 -> Math.rint((1.0D + signedUnit(seed ^ 0x5155414EL) * intensity * 4.0D) * 4.0D) / 4.0D;
            case 4 -> 0.0D;
            default -> 1.0D + unitHash(seed ^ 0x46415354L) * intensity * 5.5D;
        };
        if (surfaceActive(stack, CorruptionSurface.TICK_SPEED, MIN_DAY_TIME_INTENSITY)) {
            scale *= Mth.clamp(gameSpeedMultiplier(stack, targetId + ":nav"), 0.05D, 8.0D);
        }
        double mutated = originalSpeed * scale + signedUnit(seed ^ 0x4F464653L) * intensity * 0.22D;
        return Mth.clamp(mutated, -3.0D, 8.0D);
    }

    public static boolean shouldStopNavigationTick(Mob mob) {
        if (mob == null || mob.level().isClientSide || mob.isNoAi()) {
            return false;
        }
        CorruptionEffectStack stack = serverGameplayStack(mob);
        String targetId = navigationTargetId(mob, null, "tick");
        if (!navigationActive(stack, targetId)) {
            return false;
        }
        float intensity = navigationIntensity(stack, targetId);
        long seed = stack.stableLong(CorruptionSurface.ENTITY_STATE, targetId + ":stop", mob.getId() ^ 0x5449434B);
        int bucket = collisionPositionBucket(mob, seed, intensity);
        return unitHash(seed ^ bucket ^ 0x53544F50L) < 0.04F + intensity * 0.32F;
    }

    private static boolean navigationActive(CorruptionEffectStack stack, String targetId) {
        return targetActive(stack, CorruptionSurface.ENTITY_STATE, targetId, MIN_ENTITY_MECHANICS_INTENSITY)
                || targetActive(stack, CorruptionSurface.ENTITY_KINEMATICS, targetId, MIN_ENTITY_MECHANICS_INTENSITY);
    }

    private static float navigationIntensity(CorruptionEffectStack stack, String targetId) {
        if (stack.extreme(CorruptionSurface.ENTITY_STATE) || stack.extreme(CorruptionSurface.ENTITY_KINEMATICS)) {
            return 1.0F;
        }
        return Mth.clamp(Math.max(
                stack.targetIntensity(CorruptionSurface.ENTITY_STATE, targetId),
                Math.max(stack.targetIntensity(CorruptionSurface.ENTITY_KINEMATICS, targetId), stack.intensity(CorruptionSurface.ENTITY_STATE) * 0.82F)
        ), 0.0F, 1.0F);
    }

    private static String navigationTargetId(Mob mob, Entity target, String route) {
        ResourceLocation targetTypeId = target == null ? null : ForgeRegistries.ENTITY_TYPES.getKey(target.getType());
        String targetType = targetTypeId == null ? "none" : targetTypeId.toString();
        return "navigation:" + entityTargetId(mob) + ":" + targetType + ":" + route;
    }

    private static void shakeNearbyDroppedItems(ServerPlayer player, CorruptionEffectStack stack) {
        ServerLevel level = player.serverLevel();
        float intensity = stack.intensity(CorruptionSurface.LOOSE_ENTITY_PHYSICS);
        AABB area = player.getBoundingBox().inflate(24.0D + intensity * 48.0D, 12.0D + intensity * 20.0D, 24.0D + intensity * 48.0D);
        for (ItemEntity item : level.getEntitiesOfClass(ItemEntity.class, area, Entity::isAlive)) {
            String targetId = "item_entity:" + ForgeRegistries.ITEMS.getKey(item.getItem().getItem());
            float targetIntensity = Math.max(intensity * 0.82F, stack.targetIntensity(CorruptionSurface.LOOSE_ENTITY_PHYSICS, targetId));
            if (targetIntensity <= 0.0F) {
                continue;
            }
            Vec3 motion = item.getDeltaMovement();
            long seed = stack.stableLong(CorruptionSurface.LOOSE_ENTITY_PHYSICS, targetId, item.getId() ^ 0x4954454D);
            int bucket = collisionPositionBucket(item, seed, targetIntensity);
            Vec3 mutated = mutateFeatureVelocity(stack, CorruptionSurface.LOOSE_ENTITY_PHYSICS, targetId + ":velocity", motion, seed, bucket, targetIntensity, 0.020D, 0.52D, 5.5D);
            if (unitHash(seed ^ bucket ^ 0x4954454DL) < 0.18F + targetIntensity * 0.48F) {
                int mode = Math.floorMod((int) (seed >>> 35), 4);
                mutated = switch (mode) {
                    case 0 -> mutated.scale(0.02D + targetIntensity * 0.12D);
                    case 1 -> mutated.add(0.0D, -0.08D - targetIntensity * 0.65D, 0.0D);
                    case 2 -> mutated.add(signedUnit(seed ^ 0x5849544DL) * targetIntensity * 1.15D, signedUnit(seed ^ 0x5949544DL) * targetIntensity * 0.75D, signedUnit(seed ^ 0x5A49544DL) * targetIntensity * 1.15D);
                    default -> new Vec3(-mutated.z, mutated.y, mutated.x).scale(0.45D + targetIntensity * 1.85D);
                };
                mutated = clampVector(mutated, 5.5D);
            }
            if (mutated.distanceToSqr(motion) > 1.0E-7D) {
                item.setDeltaMovement(mutated);
                item.hasImpulse = true;
            }
        }

    }

    private static void corruptNearbyProjectiles(ServerPlayer player, CorruptionEffectStack stack) {
        ServerLevel level = player.serverLevel();
        float intensity = stack.intensity(CorruptionSurface.PROJECTILE_PHYSICS);
        AABB area = player.getBoundingBox().inflate(48.0D + intensity * 80.0D, 32.0D + intensity * 64.0D, 48.0D + intensity * 80.0D);
        for (Projectile projectile : level.getEntitiesOfClass(Projectile.class, area, Entity::isAlive)) {
            String targetId = projectileTargetId(projectile);
            float targetIntensity = Math.max(intensity * 0.86F, stack.targetIntensity(CorruptionSurface.PROJECTILE_PHYSICS, targetId));
            if (targetIntensity <= 0.0F) {
                continue;
            }
            mutateProjectileEntity(projectile, stack, targetId);
        }

    }

    private static boolean usesSaveStableVisualWorldgen(CorruptionEffectStack stack) {
        return stack.level() > MAX_PERSISTENT_TERRAIN_CORRUPTION_LEVEL
                || !allowsPersistentTerrainMutation(stack);
    }

    private static boolean allowsPersistentTerrainMutation(CorruptionEffectStack stack) {
        return stack.level() >= MIN_PERSISTENT_TERRAIN_CORRUPTION_LEVEL
                && stack.level() <= MAX_PERSISTENT_TERRAIN_CORRUPTION_LEVEL
                && stack.intensity(CorruptionSurface.WORLDGEN_SURFACE) >= MIN_PERSISTENT_TERRAIN_INTENSITY;
    }

    private static boolean surfaceActive(CorruptionEffectStack stack, CorruptionSurface surface, float minimumIntensity) {
        return stack.extreme(surface) || (stack.intensity(surface) >= minimumIntensity && stack.active(surface));
    }

    private static boolean targetActive(CorruptionEffectStack stack, CorruptionSurface surface, String targetId, float minimumIntensity) {
        return stack.extreme(surface) || (stack.intensity(surface) >= minimumIntensity && stack.active(surface, targetId));
    }

    private static CorruptionEffectStack blockBreakGameplayStackFor(Player player) {
        if (player == null || player.level() == null) {
            return CorruptionEffectStack.local(0);
        }
        return player.level().isClientSide ? activeStackFor(player) : authoritativeGameplayStackFor(player);
    }

    private static boolean blockBreakInteractionActive(CorruptionEffectStack stack, String targetId) {
        return stack != null
                && (surfaceActive(stack, CorruptionSurface.INTERACTION_ROUTING, MIN_PLAYER_MECHANICS_INTENSITY)
                || targetActive(stack, CorruptionSurface.INTERACTION_ROUTING, targetId, MIN_PLAYER_MECHANICS_INTENSITY));
    }

    private static float blockBreakInteractionIntensity(CorruptionEffectStack stack, String targetId) {
        return Mth.clamp(Math.max(
                stack.extreme(CorruptionSurface.INTERACTION_ROUTING) ? 1.0F : stack.intensity(CorruptionSurface.INTERACTION_ROUTING),
                stack.targetIntensity(CorruptionSurface.INTERACTION_ROUTING, targetId)
        ), 0.0F, 1.0F);
    }

    private static long blockBreakInteractionHash(CorruptionEffectStack stack, Player player, String channel) {
        int salt = channel.hashCode() ^ player.level().dimension().location().hashCode();
        return stack.stableLong(CorruptionSurface.INTERACTION_ROUTING, blockBreakTargetId() + ":" + channel, salt);
    }

    private static String blockBreakTargetId() {
        return BLOCK_BREAK_SPEED_TARGET_ID;
    }

    private static boolean collisionMechanicsActive(CorruptionEffectStack stack, String targetId) {
        return surfaceActive(stack, CorruptionSurface.BLOCK_COLLISION, MIN_COLLISION_MECHANICS_INTENSITY)
                || targetActive(stack, CorruptionSurface.BLOCK_COLLISION, targetId, MIN_COLLISION_MECHANICS_INTENSITY);
    }

    private static float collisionMechanicsIntensity(CorruptionEffectStack stack, String targetId, float surfaceWeight) {
        return Mth.clamp(Math.max(
                stack.targetIntensity(CorruptionSurface.BLOCK_COLLISION, targetId),
                stack.intensity(CorruptionSurface.BLOCK_COLLISION) * surfaceWeight
        ), 0.0F, 1.0F);
    }

    private static float progressiveCollisionPressure(CorruptionEffectStack stack, float intensity) {
        float visiblePressure = visibleCorruptionPressure(stack);
        // Collision needs to grow from the actual corrupted surface, not a hard
        // percent floor, or low percentages turn into near-global no-clip.
        float curved = (float) Math.pow(Math.max(0.0F, intensity), 1.12F);
        return Mth.clamp(curved * 0.70F + intensity * 0.18F + visiblePressure * intensity * 0.12F, 0.0F, 1.0F);
    }

    private static float collisionPhaseChance(CorruptionEffectStack stack, float intensity) {
        return Mth.clamp(collisionPhasePressure(stack, intensity) * 0.58F, 0.0F, 0.78F);
    }

    private static float collisionPhasePressure(CorruptionEffectStack stack, float intensity) {
        float pressure = progressiveCollisionPressure(stack, intensity);
        float phaseCurve = (float) Math.pow(Math.max(0.0F, intensity), 2.65F);
        float visiblePressure = visibleCorruptionPressure(stack);
        return Mth.clamp(pressure * 0.18F + phaseCurve * 0.72F + visiblePressure * phaseCurve * 0.10F, 0.0F, 1.0F);
    }

    private static float visibleCorruptionPressure(CorruptionEffectStack stack) {
        if (stack == null || stack.level() <= 0) {
            return 0.0F;
        }
        float visible = Mth.clamp(stack.level() / 100.0F, 0.0F, 1.0F);
        return visible * visible * (3.0F - 2.0F * visible);
    }

    private static boolean fireMechanicsActive(CorruptionEffectStack stack, String targetId) {
        return surfaceActive(stack, CorruptionSurface.FIRE_MECHANICS, MIN_FIRE_MECHANICS_INTENSITY)
                || targetActive(stack, CorruptionSurface.FIRE_MECHANICS, targetId, MIN_FIRE_MECHANICS_INTENSITY);
    }

    private static float fireMechanicsIntensity(CorruptionEffectStack stack, String targetId) {
        return Mth.clamp(weightedSurfaceIntensity(stack, CorruptionSurface.FIRE_MECHANICS, targetId, 1.0F), 0.0F, 1.0F);
    }

    private static boolean powderSnowMechanicsActive(CorruptionEffectStack stack, String targetId) {
        return surfaceActive(stack, CorruptionSurface.POWDER_SNOW_MECHANICS, MIN_POWDER_SNOW_MECHANICS_INTENSITY)
                || targetActive(stack, CorruptionSurface.POWDER_SNOW_MECHANICS, targetId, MIN_POWDER_SNOW_MECHANICS_INTENSITY);
    }

    private static float powderSnowMechanicsIntensity(CorruptionEffectStack stack, String targetId) {
        return Mth.clamp(weightedSurfaceIntensity(stack, CorruptionSurface.POWDER_SNOW_MECHANICS, targetId, 1.0F), 0.0F, 1.0F);
    }

    private static boolean isFireDamage(LivingHurtEvent event) {
        return event != null && event.getSource().is(DamageTypeTags.IS_FIRE);
    }

    private static boolean isFreezingDamage(LivingHurtEvent event) {
        return event != null && event.getSource().is(DamageTypeTags.IS_FREEZING);
    }

    private static void placeCorruptedFire(ServerLevel level, BlockPos source, long hash, float intensity) {
        BlockState fire = Blocks.FIRE.defaultBlockState();
        Direction[] directions = Direction.values();
        int attempts = 1 + Math.round(intensity * 4.0F);
        int horizontalRange = Math.max(1, Math.round(1.0F + intensity * 3.0F));
        int verticalRange = Math.max(1, Math.round(1.0F + intensity * 2.0F));
        for (int attempt = 0; attempt < attempts; attempt++) {
            long attemptHash = hash ^ mixLong(attempt * 0x9E3779B97F4A7C15L);
            Direction direction = directions[Math.floorMod((int) (attemptHash >>> 9), directions.length)];
            BlockPos target = source.relative(direction)
                    .offset(
                            signedRange(attemptHash >>> 17, horizontalRange),
                            signedRange(attemptHash >>> 29, verticalRange),
                            signedRange(attemptHash >>> 41, horizontalRange)
                    );
            if (target.getY() <= level.getMinBuildHeight() || target.getY() >= level.getMaxBuildHeight()) {
                continue;
            }
            BlockState targetState = loadedBlockState(level, target);
            if (targetState != null && targetState.isAir()) {
                level.setBlock(target, fire, 11);
                return;
            }
        }
    }

    private static boolean isNearBlock(Entity entity, net.minecraft.world.level.block.Block block, int radius) {
        if (entity == null || block == null || entity.level() == null) {
            return false;
        }
        Level level = entity.level();
        int range = Math.max(0, radius);
        BlockPos center = entity.blockPosition();
        BlockPos.MutableBlockPos cursor = new BlockPos.MutableBlockPos();
        for (int x = -range; x <= range; x++) {
            for (int y = -range; y <= range; y++) {
                for (int z = -range; z <= range; z++) {
                    cursor.set(center.getX() + x, center.getY() + y, center.getZ() + z);
                    if (cursor.getY() <= level.getMinBuildHeight() || cursor.getY() >= level.getMaxBuildHeight()) {
                        continue;
                    }
                    BlockState state = loadedBlockState(level, cursor);
                    if (state != null && state.is(block)) {
                        return true;
                    }
                }
            }
        }
        return false;
    }

    private static boolean airSupplyMechanicsActive(CorruptionEffectStack stack, String targetId, boolean player) {
        return airSurfaceActive(stack, CorruptionSurface.ENTITY_STATE, targetId);
    }

    private static boolean airSurfaceActive(CorruptionEffectStack stack, CorruptionSurface surface, String targetId) {
        return surfaceActive(stack, surface, MIN_AIR_MECHANICS_INTENSITY)
                || targetActive(stack, surface, targetId, MIN_AIR_MECHANICS_INTENSITY);
    }

    private static float airSupplyIntensity(CorruptionEffectStack stack, String targetId, boolean player) {
        return Mth.clamp(weightedSurfaceIntensity(stack, CorruptionSurface.ENTITY_STATE, targetId, 1.0F), 0.0F, 1.0F);
    }

    private static CorruptionSurface airSupplySurface(CorruptionEffectStack stack, String targetId, boolean player) {
        return CorruptionSurface.ENTITY_STATE;
    }

    private static double airSupplySpeedMultiplier(CorruptionEffectStack stack, CorruptionSurface surface, String targetId, boolean player, float intensity) {
        long hash = stack.stableLong(surface, "air_supply_multiplier:" + targetId, 0x41495253);
        double multiplier = Math.pow(2.0D, signedUnit(hash ^ 0x43555256L) * (0.12D + intensity * (stack.extreme(surface) ? 8.0D : 5.2D)));
        if (airSurfaceActive(stack, CorruptionSurface.ENTITY_STATE, targetId)) {
            long entityStateHash = stack.stableLong(CorruptionSurface.ENTITY_STATE, targetId + ":state_clock", 0x4C554E47);
            multiplier *= Math.pow(2.0D, signedUnit(entityStateHash) * intensity * 3.4D);
        }
        return Mth.clamp(multiplier, 0.0D, stack.extreme(surface) ? 4096.0D : 512.0D);
    }

    private static boolean liquidMechanicsActive(CorruptionEffectStack stack, String targetId) {
        return surfaceActive(stack, CorruptionSurface.PLAYER_PHYSICS, MIN_LIQUID_MECHANICS_INTENSITY)
                || targetActive(stack, CorruptionSurface.PLAYER_PHYSICS, targetId, MIN_LIQUID_MECHANICS_INTENSITY);
    }

    private static float liquidMechanicsIntensity(CorruptionEffectStack stack, String targetId) {
        return Mth.clamp(weightedSurfaceIntensity(stack, CorruptionSurface.PLAYER_PHYSICS, targetId, 1.0F), 0.0F, 1.0F);
    }

    private static float weightedSurfaceIntensity(CorruptionEffectStack stack, CorruptionSurface surface, String targetId, float weight) {
        if (stack.extreme(surface)) {
            return 1.0F;
        }
        if (!stack.active(surface) && !stack.active(surface, targetId)) {
            return 0.0F;
        }
        return Math.max(stack.intensity(surface), stack.targetIntensity(surface, targetId)) * weight;
    }

    private static CorruptionSurface liquidSurface(CorruptionEffectStack stack, String targetId) {
        return CorruptionSurface.PLAYER_PHYSICS;
    }

    private static long fluidFeatureSeed(CorruptionEffectStack stack, CorruptionSurface surface, String targetId) {
        return stack.stableLong(surface, "fluid_feature:" + targetId, 0x4C514D45);
    }

    private static double fluidOffsetSpan(float intensity) {
        return Math.pow(Math.max(0.0F, intensity), 1.45D) * 20.0D;
    }

    private static double fluidSampleOffsetX(long seed, float intensity) {
        return signedUnit(seed ^ 0x58574154L) * fluidOffsetSpan(intensity);
    }

    private static double fluidSampleOffsetY(long seed, float intensity) {
        return signedUnit(seed ^ 0x59574154L) * fluidOffsetSpan(intensity) * 0.72D;
    }

    private static double fluidSampleOffsetZ(long seed, float intensity) {
        return signedUnit(seed ^ 0x5A574154L) * fluidOffsetSpan(intensity);
    }

    private static double fluidSampleScale(long seed, float intensity) {
        return Mth.clamp(0.18D + unitHash(seed ^ 0x5343414CL) * (0.90D + intensity * 3.20D), 0.08D, 4.0D);
    }

    private static float fluidDetectorScale(long seed, float intensity) {
        return featureScale(seed ^ 0x44455445L, intensity, 0.16F, 1.85F);
    }

    private static float fluidFlowScale(long seed, float intensity) {
        return featureScale(seed ^ 0x464C4F57L, intensity, 0.14F, 2.25F);
    }

    private static float fluidBoatScale(long seed, float intensity) {
        return featureScale(seed ^ 0x424F4154L, intensity, 0.08F, 2.35F);
    }

    private static int fluidFlowMode(long seed) {
        return Math.floorMod((int) (seed >>> 28), 7);
    }

    private static float featureScale(long seed, float intensity, float min, float max) {
        float curve = unitHash(seed);
        curve = curve * curve * (3.0F - 2.0F * curve);
        return Mth.clamp(min + (max - min) * curve * (0.35F + intensity * 0.90F), 0.0F, max);
    }

    private static double fluidPrecisionStep(long seed, float intensity) {
        float selection = unitHash(seed);
        if (selection < 0.32F) {
            return 0.0D;
        }
        int exponent = -4 + Math.floorMod((int) (seed >>> 12), 8);
        return Mth.clamp(Math.scalb(1.0D, exponent) * (0.35D + intensity * 1.65D), 0.03125D, 8.0D);
    }

    private static int fluidPositionBucket(Entity entity, long faultSeed, float intensity) {
        double precisionStep = fluidPrecisionStep(faultSeed ^ 0x50524543L, intensity);
        double step = precisionStep <= 0.0D ? 0.5D : Math.max(0.5D, precisionStep);
        int x = Mth.floor(quantize(entity.getX() + fluidSampleOffsetX(faultSeed, intensity), step) / step);
        int y = Mth.floor(quantize(entity.getY() + fluidSampleOffsetY(faultSeed, intensity), step) / step);
        int z = Mth.floor(quantize(entity.getZ() + fluidSampleOffsetZ(faultSeed, intensity), step) / step);
        return x * 73428767 ^ y * 912931 ^ z * 42317861;
    }

    private static int collisionPositionBucket(Entity entity, long seed, float intensity) {
        double step = 0.25D + unitHash(seed ^ 0x50524543L) * (0.75D + intensity * 6.0D);
        int x = Mth.floor(quantize(entity.getX() + signedUnit(seed ^ 0x584F4646L) * intensity * 3.0D, step) / step);
        int y = Mth.floor(quantize(entity.getY() + signedUnit(seed ^ 0x594F4646L) * intensity * 2.0D, step) / step);
        int z = Mth.floor(quantize(entity.getZ() + signedUnit(seed ^ 0x5A4F4646L) * intensity * 3.0D, step) / step);
        return x * 3129871 ^ y * 116129781 ^ z * 423931;
    }

    private static Vec3 mutateFeatureVelocity(CorruptionEffectStack stack, CorruptionSurface surface, String targetId, Vec3 motion, long seed, int bucket, float intensity, double baseSpan, double scaledSpan, double maxMagnitude) {
        long clock = stack.stableLong(surface, targetId + ":clock", bucket);
        Vec3 mutated = CorruptionValueMutator.mutateVector(stack, surface, targetId, motion, baseSpan + intensity * scaledSpan, maxMagnitude, (int) (seed ^ bucket), clock);
        double horizontalScale = Mth.clamp(1.0D + signedUnit(seed ^ 0x48464C57L) * intensity * 1.35D, -0.65D, 2.35D);
        double verticalScale = Mth.clamp(1.0D + signedUnit(seed ^ 0x56464C57L) * intensity * 1.75D, -1.10D, 2.55D);
        double coupling = signedUnit(seed ^ 0x43504C47L) * intensity * 1.20D;
        mutated = new Vec3(
                mutated.x * horizontalScale + mutated.z * coupling,
                mutated.y * verticalScale + signedUnit(seed ^ 0x47524654L) * intensity * 0.08D,
                mutated.z * horizontalScale - mutated.x * coupling
        );
        if (unitHash(seed ^ 0x50564C53L) < 0.16F + intensity * 0.44F) {
            double precision = 0.015625D + unitHash(seed ^ 0x51564C53L) * intensity * 0.28D;
            mutated = new Vec3(quantize(mutated.x, precision), quantize(mutated.y, precision), quantize(mutated.z, precision));
        }
        return clampVector(mutated, maxMagnitude);
    }

    private static Vec3 clampVector(Vec3 value, double maxMagnitude) {
        if (value == null || maxMagnitude <= 0.0D) {
            return value == null ? Vec3.ZERO : value;
        }
        double maxSqr = maxMagnitude * maxMagnitude;
        if (value.lengthSqr() <= maxSqr) {
            return value;
        }
        return value.normalize().scale(maxMagnitude);
    }

    private static boolean sampleCorruptedFluid(Entity entity, TagKey<Fluid> fluidTag, boolean eyeOnly, long faultSeed, float intensity) {
        AABB base;
        if (eyeOnly) {
            double radius = 0.04D + fluidSampleScale(faultSeed, intensity) * 0.10D;
            double eyeY = entity.getEyeY() - 0.11111111D;
            base = new AABB(entity.getX() - radius, eyeY - radius, entity.getZ() - radius, entity.getX() + radius, eyeY + radius, entity.getZ() + radius);
        } else {
            base = entity.getBoundingBox().deflate(0.001D);
        }

        AABB sampled = transformFluidSampleBox(base, faultSeed, intensity);
        return isFluidInBox(entity.level(), sampled, fluidTag);
    }

    private static AABB transformFluidSampleBox(AABB base, long faultSeed, float intensity) {
        double centerX = (base.minX + base.maxX) * 0.5D + fluidSampleOffsetX(faultSeed, intensity);
        double centerY = (base.minY + base.maxY) * 0.5D + fluidSampleOffsetY(faultSeed, intensity);
        double centerZ = (base.minZ + base.maxZ) * 0.5D + fluidSampleOffsetZ(faultSeed, intensity);
        double precisionStep = fluidPrecisionStep(faultSeed ^ 0x50524543L, intensity);
        if (precisionStep > 0.0D) {
            centerX = quantize(centerX, precisionStep);
            centerY = quantize(centerY, precisionStep);
            centerZ = quantize(centerZ, precisionStep);
        }

        double sampleScale = fluidSampleScale(faultSeed, intensity);
        double halfX = Mth.clamp((base.maxX - base.minX) * 0.5D * sampleScale, 0.03125D, 2.5D);
        double halfY = Mth.clamp((base.maxY - base.minY) * 0.5D * sampleScale, 0.03125D, 2.5D);
        double halfZ = Mth.clamp((base.maxZ - base.minZ) * 0.5D * sampleScale, 0.03125D, 2.5D);
        return new AABB(centerX - halfX, centerY - halfY, centerZ - halfZ, centerX + halfX, centerY + halfY, centerZ + halfZ);
    }

    private static double quantize(double value, double step) {
        return step <= 0.0D ? value : Math.rint(value / step) * step;
    }

    private static TagKey<Fluid> fluidTagForCheck(String check) {
        return check.contains("lava") || check.contains("minecraft:lava") ? FluidTags.LAVA : FluidTags.WATER;
    }

    private static boolean isFluidInBox(Level level, AABB box, TagKey<Fluid> fluidTag) {
        int minX = Mth.floor(box.minX);
        int maxX = Mth.ceil(box.maxX);
        int minY = Math.max(level.getMinBuildHeight(), Mth.floor(box.minY));
        int maxY = Math.min(level.getMaxBuildHeight(), Mth.ceil(box.maxY));
        int minZ = Mth.floor(box.minZ);
        int maxZ = Mth.ceil(box.maxZ);
        if (minY >= maxY) {
            return false;
        }

        BlockPos.MutableBlockPos mutable = new BlockPos.MutableBlockPos();
        for (int x = minX; x < maxX; x++) {
            for (int y = minY; y < maxY; y++) {
                for (int z = minZ; z < maxZ; z++) {
                    mutable.set(x, y, z);
                    FluidState state = loadedFluidState(level, mutable);
                    if (state.is(fluidTag)) {
                        return true;
                    }
                }
            }
        }
        return false;
    }

    private static FluidState loadedFluidState(Level level, BlockPos pos) {
        int chunkX = pos.getX() >> 4;
        int chunkZ = pos.getZ() >> 4;
        if (level instanceof ServerLevel serverLevel) {
            LevelChunk chunk = serverLevel.getChunkSource().getChunkNow(chunkX, chunkZ);
            return chunk == null ? Fluids.EMPTY.defaultFluidState() : chunk.getFluidState(pos);
        }
        if (!level.hasChunk(chunkX, chunkZ)) {
            return Fluids.EMPTY.defaultFluidState();
        }
        return level.getChunk(chunkX, chunkZ).getFluidState(pos);
    }

    private static BlockState loadedBlockState(Level level, BlockPos pos) {
        int chunkX = SectionPos.blockToSectionCoord(pos.getX());
        int chunkZ = SectionPos.blockToSectionCoord(pos.getZ());
        if (level instanceof ServerLevel serverLevel) {
            LevelChunk chunk = serverLevel.getChunkSource().getChunkNow(chunkX, chunkZ);
            return chunk == null ? null : chunk.getBlockState(pos);
        }
        if (!level.hasChunk(chunkX, chunkZ)) {
            return null;
        }
        return level.getChunk(chunkX, chunkZ).getBlockState(pos);
    }

    private static boolean shouldSuspendServerMutations(MinecraftServer server) {
        return serverMutationSuspended
                || server.getPlayerList().getPlayerCount() <= 0
                || server.getTickCount() < serverMutationResumeTick;
    }

    private static void scheduleServerMutationWarmup(MinecraftServer server, int ticks) {
        if (server == null) {
            return;
        }
        long resumeTick = server.getTickCount() + Math.max(1, ticks);
        serverMutationResumeTick = Math.max(serverMutationResumeTick, resumeTick);
    }

    private static void syncServerRuntime(MinecraftServer server) {
        if (server == null || server.overworld() == null) {
            return;
        }
        CorruptionRuntimeManager.applySavedDataToGlobalSettings(CorruptionSavedData.get(server));
    }

    private static CorruptionEffectStack clientStack() {
        return CLIENT_GAMEPLAY_STACK_SUPPLIER.get();
    }

    private static Supplier<CorruptionEffectStack> createClientGameplayStackSupplier() {
        if (FMLEnvironment.dist != Dist.CLIENT) {
            return () -> CorruptionEffectStack.local(0);
        }
        try {
            Class<?> type = Class.forName("com.maxsters.realtimeminecraftcorruptionsimulator.client.effects.ClientCorruptionEffects");
            Method method = type.getMethod("currentForGameplay");
            return () -> {
                try {
                    Object value = method.invoke(null);
                    return value instanceof CorruptionEffectStack stack ? stack : CorruptionEffectStack.local(0);
                } catch (ReflectiveOperationException | LinkageError ignored) {
                    return CorruptionEffectStack.local(0);
                }
            };
        } catch (ReflectiveOperationException | LinkageError ignored) {
            return () -> CorruptionEffectStack.local(0);
        }
    }

    private static synchronized CorruptionEffectStack cachedServerStack(MinecraftServer server) {
        int identity = System.identityHashCode(server);
        long tick = server.getTickCount();
        if (cachedServerIdentity == identity && cachedServerStackTick == tick) {
            return cachedServerStack;
        }

        // Server hooks can ask for the stack many times per tick; build it once from saved world settings.
        CorruptionSavedData data = CorruptionSavedData.get(server);
        CorruptionRuntimeManager.syncGlobalLevel(data);
        cachedServerIdentity = identity;
        cachedServerStackTick = tick;
        cachedServerStack = CorruptionEffectStack.from(data);
        return cachedServerStack;
    }

    private static synchronized void clearServerStackCache() {
        cachedServerIdentity = 0;
        cachedServerStackTick = Long.MIN_VALUE;
        cachedServerStack = CorruptionEffectStack.local(0);
    }

    private static synchronized void clearEntityRuntimeCaches() {
        EntityAttributeCorruptionMechanics.clearCaches();
        EntityHitboxCorruptionMechanics.clearCaches();
    }

    private static synchronized void resetTerrainMutationBudget() {
    }

    private static synchronized boolean claimWorldProcessMutation(ServerLevel level) {
        long tick = level.getServer() == null ? level.getGameTime() : level.getServer().getTickCount();
        if (worldProcessBudgetTick != tick) {
            worldProcessBudgetTick = tick;
            worldProcessBudgetUsed = 0;
        }
        if (worldProcessBudgetUsed >= MAX_WORLD_PROCESS_MUTATIONS_PER_TICK) {
            return false;
        }
        worldProcessBudgetUsed++;
        return true;
    }

    private static synchronized boolean claimFluidSourceAllow(ServerLevel level) {
        long tick = level.getServer() == null ? level.getGameTime() : level.getServer().getTickCount();
        if (fluidSourceAllowBudgetTick != tick) {
            fluidSourceAllowBudgetTick = tick;
            fluidSourceAllowsUsed = 0;
        }
        if (fluidSourceAllowsUsed >= MAX_FLUID_SOURCE_ALLOWS_PER_TICK) {
            return false;
        }
        fluidSourceAllowsUsed++;
        return true;
    }

    private static synchronized void resetWorldProcessBudget() {
        worldProcessBudgetTick = Long.MIN_VALUE;
        worldProcessBudgetUsed = 0;
        fluidSourceAllowBudgetTick = Long.MIN_VALUE;
        fluidSourceAllowsUsed = 0;
    }

    private static void clearPendingTerrainMutations() {
        synchronized (pendingTerrainMutations) {
            pendingTerrainMutations.clear();
            pendingTerrainMutationKeys.clear();
        }
    }

    private static BlockState mutatedNearbyBlockState(ServerLevel level, BlockPos pos, long hash, CorruptionEffectStack stack, BlockState fallback, boolean allowAirTears) {
        float intensity = stack.intensityOrExtreme(CorruptionSurface.WORLDGEN_SURFACE);
        if (allowAirTears && unitHash(hash ^ 0x4E454152414952L) < 0.02F + intensity * 0.16F) {
            return Blocks.AIR.defaultBlockState();
        }

        int range = Math.max(1, Math.round(1.0F + intensity * 8.0F));
        BlockPos sourcePos = pos.offset(
                signedRange(hash >>> 5, range),
                signedRange(hash >>> 17, Math.max(1, Math.round(1.0F + intensity * 12.0F))),
                signedRange(hash >>> 29, range)
        );
        if (sourcePos.getY() <= level.getMinBuildHeight() || sourcePos.getY() >= level.getMaxBuildHeight()) {
            return fallback;
        }

        BlockState sampled = loadedBlockState(level, sourcePos);
        if (sampled == null) {
            return fallback;
        }
        if (isTerrainLogicMutationReplaceable(sampled)) {
            return sampled;
        }
        return fallback;
    }

    private static int signedRange(long value, int range) {
        int span = Math.max(1, range);
        return Math.floorMod((int) mixLong(value), span * 2 + 1) - span;
    }

    private static boolean isTerrainLogicMutationReplaceable(BlockState state) {
        return !state.hasBlockEntity()
                && state.getFluidState().isEmpty()
                && !(state.getBlock() instanceof FallingBlock)
                && !state.is(Blocks.BEDROCK)
                && !state.is(Blocks.END_PORTAL)
                && !state.is(Blocks.END_PORTAL_FRAME)
                && !state.is(Blocks.NETHER_PORTAL)
                && !state.is(Blocks.BARRIER)
                && !state.is(Blocks.STRUCTURE_BLOCK)
                && !state.is(Blocks.STRUCTURE_VOID)
                && !state.is(Blocks.COMMAND_BLOCK)
                && !state.is(Blocks.CHAIN_COMMAND_BLOCK)
                && !state.is(Blocks.REPEATING_COMMAND_BLOCK);
    }

    private static String entityTargetId(Entity entity) {
        ResourceLocation location = ForgeRegistries.ENTITY_TYPES.getKey(entity.getType());
        return location == null ? entity.getType().toString() : location.toString();
    }

    private static int entityStableSalt(Entity entity, int salt) {
        return (int) mixLong(entityStableIdentity(entity) ^ Integer.toUnsignedLong(salt) * 0x9E3779B97F4A7C15L);
    }

    private static long entityStableIdentity(Entity entity) {
        UUID uuid = entity == null ? null : entity.getUUID();
        if (uuid == null) {
            return 0L;
        }
        return mixLong(uuid.getMostSignificantBits() ^ Long.rotateLeft(uuid.getLeastSignificantBits(), 32));
    }

    private static String blockTargetId(BlockState state) {
        ResourceLocation location = state == null ? null : ForgeRegistries.BLOCKS.getKey(state.getBlock());
        return location == null ? "unknown" : location.toString();
    }

    private static String projectileTargetId(Projectile projectile) {
        return "projectile:" + entityTargetId(projectile);
    }

    private static String playerTargetId(Player player) {
        StringBuilder builder = new StringBuilder("player_motion:");
        if (player.isSwimming()) {
            builder.append("swimming");
        } else if (player.isInWater()) {
            builder.append("water");
        } else if (player.isInLava()) {
            builder.append("lava");
        } else if (player.onGround()) {
            builder.append("ground");
        } else {
            builder.append("air");
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

    private static boolean shouldMutateWorldProcess(ServerLevel level, CorruptionEffectStack stack, CorruptionSurface surface, String process, BlockState state, BlockPos pos, int salt, float highChance) {
        if (stack.level() <= 0) {
            return false;
        }
        if (usesSaveStableVisualWorldgen(stack)) {
            return false;
        }
        if (!surfaceActive(stack, surface, MIN_WORLD_PROCESS_INTENSITY)) {
            return false;
        }

        String targetId = "world_process:" + process + ":" + blockTargetId(state);
        if (stack.extreme(surface)) {
            return unitHash(worldProcessHash(stack, surface, process, state, pos, salt)) < highChance && claimWorldProcessMutation(level);
        }
        if (!targetActive(stack, surface, targetId, MIN_WORLD_PROCESS_INTENSITY)) {
            return false;
        }

        float intensity = Math.max(stack.targetIntensity(surface, targetId), stack.intensity(surface) * 0.58F);
        float chance = Math.min(highChance * 0.82F, 0.035F + intensity * 0.46F + stack.instability() * 0.10F);
        return stack.unit(surface, targetId, pos.hashCode() ^ salt) < chance && claimWorldProcessMutation(level);
    }

    private static long worldProcessHash(CorruptionEffectStack stack, CorruptionSurface surface, String process, BlockState state, BlockPos pos, int salt) {
        String targetId = "world_process:" + process + ":" + blockTargetId(state);
        return stack.stableLong(surface, targetId, salt) ^ mixLong(pos.asLong() ^ salt * 0x9E3779B97F4A7C15L);
    }

    private static int clampInt(int value, int min, int max) {
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

    private static float signedUnit(long value) {
        return unitHash(value) * 2.0F - 1.0F;
    }

    private static long stableHash(long seed, int a, int b, long c) {
        long value = seed;
        value ^= a * 0x9E3779B97F4A7C15L;
        value = Long.rotateLeft(value, 27) * 0x94D049BB133111EBL;
        value ^= b * 0xBF58476D1CE4E5B9L;
        value = Long.rotateLeft(value, 31) * 0xD6E8FEB86659FD93L;
        value ^= c;
        value ^= value >>> 33;
        value *= 0xff51afd7ed558ccdL;
        value ^= value >>> 33;
        value *= 0xc4ceb9fe1a85ec53L;
        value ^= value >>> 33;
        return value;
    }

    private record TerrainMutationRequest(ServerLevel level, ChunkPos chunkPos, String key, CorruptionEffectStack stack) {
    }
}
