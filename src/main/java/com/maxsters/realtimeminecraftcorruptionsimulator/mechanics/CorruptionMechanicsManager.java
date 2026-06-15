package com.maxsters.realtimeminecraftcorruptionsimulator.mechanics;

import com.maxsters.realtimeminecraftcorruptionsimulator.RealtimeMinecraftCorruptionSimulator;
import com.maxsters.realtimeminecraftcorruptionsimulator.calibration.CorruptionCalibrationManager;
import com.maxsters.realtimeminecraftcorruptionsimulator.client.effects.ClientCorruptionEffects;
import com.maxsters.realtimeminecraftcorruptionsimulator.config.GlobalCorruptionSettings;
import com.maxsters.realtimeminecraftcorruptionsimulator.network.ModNetwork;
import com.maxsters.realtimeminecraftcorruptionsimulator.profile.CorruptionEffectStack;
import com.maxsters.realtimeminecraftcorruptionsimulator.profile.CorruptionSurface;
import com.maxsters.realtimeminecraftcorruptionsimulator.profile.CorruptionValueMutator;
import com.maxsters.realtimeminecraftcorruptionsimulator.runtime.CorruptionRuntimeManager;
import com.maxsters.realtimeminecraftcorruptionsimulator.state.CorruptionSavedData;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.tags.FluidTags;
import net.minecraft.tags.TagKey;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.projectile.Projectile;
import net.minecraft.world.entity.vehicle.Boat;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.FallingBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.LevelChunk;
import net.minecraft.world.level.material.Fluid;
import net.minecraft.world.level.material.FluidState;
import net.minecraft.world.level.material.Fluids;
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
import net.minecraftforge.event.level.LevelEvent;
import net.minecraftforge.event.level.BlockEvent;
import net.minecraftforge.event.server.ServerStartingEvent;
import net.minecraftforge.event.server.ServerStoppedEvent;
import net.minecraftforge.event.server.ServerStoppingEvent;
import net.minecraftforge.eventbus.api.Event;
import net.minecraftforge.eventbus.api.EventPriority;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.registries.ForgeRegistries;

import java.util.ArrayDeque;
import java.util.HashSet;
import java.util.Set;
import java.util.UUID;

@Mod.EventBusSubscriber(modid = RealtimeMinecraftCorruptionSimulator.MOD_ID, bus = Mod.EventBusSubscriber.Bus.FORGE)
public final class CorruptionMechanicsManager {
    private static final UUID NON_PLAYER_SPEED_ID = UUID.fromString("0e627e2a-9d3a-4d9b-a4b8-a1830ed20401");
    private static final String NON_PLAYER_SPEED_NAME = "realtime_minecraft_corruption_simulator_non_player_speed";
    private static final UUID PLAYER_MAX_HEALTH_ID = UUID.fromString("3ed6bc4d-1875-4b4a-9e4f-80f359996081");
    private static final String PLAYER_MAX_HEALTH_NAME = "realtime_minecraft_corruption_simulator_player_max_health";
    private static final int SERVER_JOIN_MUTATION_WARMUP_TICKS = 240;
    private static final int SERVER_DIMENSION_MUTATION_WARMUP_TICKS = 120;
    private static final int SERVER_LEVEL_LOAD_MUTATION_WARMUP_TICKS = 80;
    private static final int SERVER_SAVE_MUTATION_COOLDOWN_TICKS = 80;
    private static final int MAX_WORLD_PROCESS_MUTATIONS_PER_TICK = 12;
    private static final int MIN_PERSISTENT_TERRAIN_CORRUPTION_LEVEL = 1;
    private static final int MAX_PERSISTENT_TERRAIN_CORRUPTION_LEVEL = 100;
    private static final int MIN_PERSISTENT_TERRAIN_CONFIDENCE = 0;
    private static final int MIN_PERSISTENT_TERRAIN_COHERENCE = 0;
    private static final int MAX_PERSISTENT_TERRAIN_DEBT = 100;
    private static final int MAX_PERSISTENT_TERRAIN_DELTA = 100;
    private static final float MIN_PERSISTENT_TERRAIN_INTENSITY = 0.006F;
    private static final float MIN_WORLD_PROCESS_INTENSITY = 0.006F;
    private static final float MIN_PLAYER_MECHANICS_INTENSITY = 0.006F;
    private static final float MIN_ENTITY_MECHANICS_INTENSITY = 0.006F;
    private static final float MIN_SPAWN_MECHANICS_INTENSITY = 0.006F;
    private static final float MIN_LIQUID_MECHANICS_INTENSITY = 0.006F;
    private static final float MIN_DAY_TIME_INTENSITY = 0.006F;
    private static volatile boolean serverMutationSuspended;
    private static volatile long serverMutationResumeTick;
    private static long worldProcessBudgetTick = Long.MIN_VALUE;
    private static int worldProcessBudgetUsed;
    private static int cachedServerIdentity;
    private static long cachedServerStackTick = Long.MIN_VALUE;
    private static CorruptionEffectStack cachedServerStack = CorruptionEffectStack.local(0);
    private static final ArrayDeque<TerrainMutationRequest> pendingTerrainMutations = new ArrayDeque<>();
    private static final Set<String> pendingTerrainMutationKeys = new HashSet<>();

    private CorruptionMechanicsManager() {
    }

    @SubscribeEvent
    public static void onServerStarting(ServerStartingEvent event) {
        serverMutationSuspended = false;
        serverMutationResumeTick = 0L;
        clearServerStackCache();
        resetTerrainMutationBudget();
        resetWorldProcessBudget();
        clearPendingTerrainMutations();
    }

    @SubscribeEvent
    public static void onServerStopping(ServerStoppingEvent event) {
        serverMutationSuspended = true;
        clearServerStackCache();
        clearPendingTerrainMutations();
    }

    @SubscribeEvent
    public static void onServerStopped(ServerStoppedEvent event) {
        serverMutationSuspended = false;
        serverMutationResumeTick = 0L;
        clearServerStackCache();
        resetTerrainMutationBudget();
        resetWorldProcessBudget();
        clearPendingTerrainMutations();
    }

    public static void onGlobalSettingsApplied(MinecraftServer server) {
        serverMutationSuspended = false;
        scheduleServerMutationWarmup(server, SERVER_SAVE_MUTATION_COOLDOWN_TICKS);
        resetAutoIncreaseTimer(server);
        clearServerStackCache();
        resetTerrainMutationBudget();
        resetWorldProcessBudget();
        clearPendingTerrainMutations();
    }

    public static boolean corruptFluidDetection(Entity entity, String check, boolean original) {
        if (entity == null || entity.level() == null || entity.isSpectator()) {
            return original;
        }

        CorruptionEffectStack stack = activeStackFor(entity);
        String targetId = "fluid_detection:" + check + ":" + entityTargetId(entity);
        if (!liquidMechanicsActive(stack, targetId)) {
            return original;
        }

        CorruptionSurface surface = liquidSurface(stack, targetId);
        float intensity = liquidMechanicsIntensity(stack, targetId);
        FluidFeatureFault fault = fluidFeatureFault(stack, surface, targetId, intensity);
        int bucket = fluidPositionBucket(entity, fault);
        float chance = stack.extreme(surface)
                ? Mth.clamp(0.54F + fault.detectorScale * 0.36F, 0.0F, 0.96F)
                : Mth.clamp((0.08F + intensity * 0.74F + stack.instability() * 0.08F) * fault.detectorScale, 0.0F, 0.90F);
        if (stack.unit(surface, targetId + ":detector_path", bucket) >= chance) {
            return original;
        }

        TagKey<Fluid> fluidTag = fluidTagForCheck(check);
        boolean eyeOnly = check.startsWith("eye:") || check.contains("underwater");
        return sampleCorruptedFluid(entity, fluidTag, eyeOnly, fault);
    }

    public static boolean corruptFluidPush(Entity entity, boolean original) {
        if (entity == null || entity.level() == null || entity.isSpectator()) {
            return original;
        }

        CorruptionEffectStack stack = activeStackFor(entity);
        String targetId = "fluid_collision:" + entityTargetId(entity);
        if (!liquidMechanicsActive(stack, targetId)) {
            return original;
        }

        CorruptionSurface surface = liquidSurface(stack, targetId);
        float intensity = liquidMechanicsIntensity(stack, targetId);
        FluidFeatureFault fault = fluidFeatureFault(stack, surface, targetId, intensity);
        int bucket = fluidPositionBucket(entity, fault);
        float chance = stack.extreme(surface)
                ? Mth.clamp(0.46F + fault.flowScale * 0.34F, 0.0F, 0.92F)
                : Mth.clamp((0.08F + intensity * 0.58F + stack.instability() * 0.08F) * fault.flowScale, 0.0F, 0.82F);
        if (stack.unit(surface, targetId + ":push_feature", bucket) >= chance) {
            return original;
        }
        return stack.unit(surface, targetId + ":push_disabled", bucket ^ 0x50555348) >= 0.50F && original;
    }

    public static void corruptSwimmingState(Entity entity) {
        if (entity == null || entity.level() == null || entity.isSpectator()) {
            return;
        }

        // Swimming is now affected indirectly through corrupted fluid sampling and movement math.
    }

    public static Vec3 corruptCollisionResolution(Entity entity, Vec3 requested, Vec3 resolved) {
        if (entity == null || entity.level() == null || entity.isSpectator() || requested == null || resolved == null) {
            return resolved;
        }

        CorruptionEffectStack stack = activeStackFor(entity);
        String targetId = "collision_resolution:" + entityTargetId(entity);
        float minimum = entity instanceof Player ? MIN_PLAYER_MECHANICS_INTENSITY : MIN_ENTITY_MECHANICS_INTENSITY;
        if (!surfaceActive(stack, CorruptionSurface.BLOCK_COLLISION, minimum)
                && !targetActive(stack, CorruptionSurface.BLOCK_COLLISION, targetId, minimum)) {
            return resolved;
        }

        float intensity = Math.max(
                stack.targetIntensity(CorruptionSurface.BLOCK_COLLISION, targetId),
                stack.intensity(CorruptionSurface.BLOCK_COLLISION) * 0.82F
        );
        if (intensity <= 0.0F) {
            return resolved;
        }

        long seed = stack.stableLong(CorruptionSurface.BLOCK_COLLISION, targetId, entity.getId() ^ 0x43524C44);
        int bucket = collisionPositionBucket(entity, seed, intensity);
        long clock = stack.stableLong(CorruptionSurface.BLOCK_COLLISION, targetId + ":clock", bucket);
        Vec3 blocked = requested.subtract(resolved);
        Vec3 out = resolved;

        float leakChance = Math.min(0.94F, Math.max(0.0F, intensity - 0.16F) * 0.72F + stack.instability() * 0.10F);
        if (stack.unit(CorruptionSurface.BLOCK_COLLISION, targetId + ":leak", bucket) < leakChance) {
            double leak = Mth.clamp(unitHash(seed ^ 0x4C45414BL) * (0.22D + intensity * 1.15D), 0.0D, 1.45D);
            out = out.add(blocked.scale(leak));
        }

        float stickChance = Math.min(0.72F, 0.04F + intensity * 0.42F + stack.instability() * 0.08F);
        if (stack.unit(CorruptionSurface.BLOCK_COLLISION, targetId + ":stick", bucket ^ 0x53544943) < stickChance) {
            double stick = Mth.clamp(unitHash(seed ^ 0x535449434BL) * (0.25D + intensity * 0.82D), 0.0D, 0.96D);
            out = out.multiply(1.0D - stick, 1.0D - stick * 0.75D, 1.0D - stick);
        }

        float slingChance = Math.min(0.62F, Math.max(0.0F, intensity - 0.28F) * 0.48F + Math.abs(collisionPulse(seed, unitHash(seed ^ bucket))) * intensity * 0.14F);
        if (stack.unit(CorruptionSurface.BLOCK_COLLISION, targetId + ":sling", bucket ^ 0x534C494E) < slingChance) {
            Vec3 sling = mutateFeatureVelocity(stack, CorruptionSurface.BLOCK_COLLISION, targetId + ":sling_motion", requested, seed, bucket, intensity, 0.0D, 0.42D, 3.8D);
            out = out.add(sling.subtract(requested).scale(0.35D + intensity * 0.55D));
        }

        if (unitHash(seed ^ 0x43505245L) < 0.12F + intensity * 0.36F) {
            double precision = 0.015625D + unitHash(clock ^ 0x5155414EL) * intensity * 0.42D;
            out = new Vec3(quantize(out.x, precision), quantize(out.y, precision), quantize(out.z, precision));
        }

        float bypassChance = Math.min(0.98F, Math.max(0.0F, intensity - 0.38F) * 1.08F + stack.instability() * 0.12F);
        if (stack.unit(CorruptionSurface.BLOCK_COLLISION, targetId + ":phase_through", bucket ^ 0x50484153) < bypassChance) {
            double downwardSlip = 0.0D;
            if (requested.y <= resolved.y + 1.0E-6D
                    || stack.unit(CorruptionSurface.BLOCK_COLLISION, targetId + ":void_bias", bucket ^ 0x564F4944) < 0.24F + intensity * 0.54F) {
                downwardSlip = 0.035D + intensity * (0.18D + unitHash(seed ^ 0x534C4950L) * 1.65D);
            }
            out = requested.add(blocked.scale(0.35D + intensity * 1.25D)).add(0.0D, -downwardSlip, 0.0D);
        }

        double max = 6.0D + intensity * 18.0D;
        return new Vec3(Mth.clamp(out.x, -max, max), Mth.clamp(out.y, -max, max), Mth.clamp(out.z, -max, max));
    }

    public static boolean corruptBoatUnderWater(Boat boat, boolean original) {
        return corruptFluidDetection(boat, "boat_underwater", original);
    }

    @SubscribeEvent
    public static void onLevelLoad(LevelEvent.Load event) {
        if (event.getLevel() instanceof ServerLevel level) {
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
            scheduleServerMutationWarmup(player.getServer(), SERVER_JOIN_MUTATION_WARMUP_TICKS);
            clearServerStackCache();
        }
    }

    @SubscribeEvent
    public static void onPlayerLoggedOut(PlayerEvent.PlayerLoggedOutEvent event) {
        MinecraftServer server = event.getEntity().getServer();
        if (server != null && server.getPlayerList().getPlayerCount() <= 1) {
            serverMutationSuspended = true;
            clearServerStackCache();
        }
    }

    @SubscribeEvent
    public static void onPlayerChangedDimension(PlayerEvent.PlayerChangedDimensionEvent event) {
        if (event.getEntity() instanceof ServerPlayer player) {
            scheduleServerMutationWarmup(player.getServer(), SERVER_DIMENSION_MUTATION_WARMUP_TICKS);
            clearServerStackCache();
        }
        resetTerrainMutationBudget();
        resetWorldProcessBudget();
        clearPendingTerrainMutations();
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

        CorruptionEffectStack stack = serverStack(entity);
        String targetId = entityTargetId(entity);
        AttributeInstance movementSpeed = entity.getAttribute(Attributes.MOVEMENT_SPEED);
        if (stack.level() <= 0) {
            if (movementSpeed != null) {
                removeSpeedModifier(movementSpeed);
            }
            return;
        }
        if (!surfaceActive(stack, CorruptionSurface.ENTITY_STATE, MIN_ENTITY_MECHANICS_INTENSITY)
                && !surfaceActive(stack, CorruptionSurface.ENTITY_KINEMATICS, MIN_ENTITY_MECHANICS_INTENSITY)
                && !surfaceActive(stack, CorruptionSurface.BLOCK_COLLISION, MIN_ENTITY_MECHANICS_INTENSITY)) {
            if (movementSpeed != null) {
                removeSpeedModifier(movementSpeed);
            }
            return;
        }
        int cadence = corruptedCadence(stack, targetId + ":ai_tick", Math.max(2, 8 - Math.round(stack.intensity(CorruptionSurface.ENTITY_STATE) * 4.0F)), 40);
        if (entity.tickCount % cadence != 0) {
            return;
        }
        if (movementSpeed != null) {
            if (targetActive(stack, CorruptionSurface.ENTITY_KINEMATICS, targetId, MIN_ENTITY_MECHANICS_INTENSITY)) {
                double amount = entitySpeedMultiplier(stack, targetId);
                syncSpeedModifier(movementSpeed, amount);
            } else {
                removeSpeedModifier(movementSpeed);
            }
        }
        mutateLivingState(entity, stack, targetId);
        mutateEntityCollision(entity, stack, targetId);
    }

    @SubscribeEvent
    public static void onPlayerTick(TickEvent.PlayerTickEvent event) {
        if (event.phase != TickEvent.Phase.END) {
            return;
        }

        Player player = event.player;
        CorruptionEffectStack stack = activeStackFor(player);
        syncPlayerMaxHealth(player, stack);
        if (surfaceActive(stack, CorruptionSurface.PLAYER_PHYSICS, MIN_PLAYER_MECHANICS_INTENSITY)) {
            String targetId = playerTargetId(player);
            float intensity = Math.max(stack.targetIntensity(CorruptionSurface.PLAYER_PHYSICS, targetId), stack.intensity(CorruptionSurface.PLAYER_PHYSICS) * 0.70F);
            if (intensity > 0.0F) {
                mutatePlayerMotion(player, stack, targetId, intensity);
            }
        }
        mutatePlayerCollision(player, stack);

        if (player instanceof ServerPlayer serverPlayer) {
            int itemCadence = corruptedCadence(stack, "loose_entity_tick:" + serverPlayer.serverLevel().dimension().location(), 10, 40);
            int projectileCadence = corruptedCadence(stack, "projectile_tick:" + serverPlayer.serverLevel().dimension().location(), 8, 40);
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
        CorruptionEffectStack stack = activeStackFor(player);
        String targetId = "hunger_depletion:" + playerTargetId(player);
        boolean active = surfaceActive(stack, CorruptionSurface.TICK_SPEED, MIN_DAY_TIME_INTENSITY)
                || targetActive(stack, CorruptionSurface.PLAYER_PHYSICS, targetId, MIN_PLAYER_MECHANICS_INTENSITY)
                || targetActive(stack, CorruptionSurface.INTERACTION_ROUTING, targetId, MIN_PLAYER_MECHANICS_INTENSITY);
        if (!active) {
            return exhaustion;
        }

        float intensity = Mth.clamp(Math.max(
                stack.extreme(CorruptionSurface.TICK_SPEED) ? 1.0F : stack.intensity(CorruptionSurface.TICK_SPEED),
                Math.max(
                        stack.extreme(CorruptionSurface.PLAYER_PHYSICS) ? 1.0F : stack.intensity(CorruptionSurface.PLAYER_PHYSICS) * 0.72F,
                        stack.extreme(CorruptionSurface.INTERACTION_ROUTING) ? 1.0F : stack.intensity(CorruptionSurface.INTERACTION_ROUTING) * 0.55F
                )
        ), 0.0F, 1.0F);
        long hash = stack.stableLong(CorruptionSurface.TICK_SPEED, targetId, player.getId() ^ 0x48554E47);
        double speed = surfaceActive(stack, CorruptionSurface.TICK_SPEED, MIN_DAY_TIME_INTENSITY)
                ? gameSpeedMultiplier(stack, targetId)
                : Math.pow(2.0D, signedUnit(hash ^ 0x4558504FL) * intensity * 5.0D);
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
        CorruptionEffectStack stack = activeStackFor(player);
        String targetId = "attack_cooldown:" + playerTargetId(player);
        boolean active = surfaceActive(stack, CorruptionSurface.ANIMATION_TIMING, MIN_PLAYER_MECHANICS_INTENSITY)
                || targetActive(stack, CorruptionSurface.INTERACTION_ROUTING, targetId, MIN_PLAYER_MECHANICS_INTENSITY);
        if (!active) {
            return original;
        }

        float intensity = Mth.clamp(Math.max(
                stack.extreme(CorruptionSurface.ANIMATION_TIMING) ? 1.0F : stack.intensity(CorruptionSurface.ANIMATION_TIMING),
                (stack.extreme(CorruptionSurface.INTERACTION_ROUTING) ? 1.0F : stack.intensity(CorruptionSurface.INTERACTION_ROUTING)) * 0.84F
        ), 0.0F, 1.0F);
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
        CorruptionEffectStack stack = activeStackFor(player);
        String targetId = "attack_delay:" + playerTargetId(player);
        boolean active = surfaceActive(stack, CorruptionSurface.ANIMATION_TIMING, MIN_PLAYER_MECHANICS_INTENSITY)
                || targetActive(stack, CorruptionSurface.INTERACTION_ROUTING, targetId, MIN_PLAYER_MECHANICS_INTENSITY);
        if (!active) {
            return original;
        }

        float intensity = Mth.clamp(Math.max(
                stack.extreme(CorruptionSurface.ANIMATION_TIMING) ? 1.0F : stack.intensity(CorruptionSurface.ANIMATION_TIMING),
                (stack.extreme(CorruptionSurface.INTERACTION_ROUTING) ? 1.0F : stack.intensity(CorruptionSurface.INTERACTION_ROUTING)) * 0.84F
        ), 0.0F, 1.0F);
        long hash = stack.stableLong(CorruptionSurface.ANIMATION_TIMING, targetId, player.getId() ^ 0x44454C59);
        if (unitHash(hash ^ 0x53545543L) < 0.06F + intensity * 0.36F) {
            return 72000.0F;
        }
        double multiplier = Math.pow(2.0D, signedUnit(hash ^ 0x4D554C54L) * intensity * 7.0D);
        if (surfaceActive(stack, CorruptionSurface.TICK_SPEED, MIN_DAY_TIME_INTENSITY)) {
            multiplier *= Mth.clamp(1.0D / gameSpeedMultiplier(stack, targetId + ":speed"), 0.05D, 20.0D);
        }
        return (float) Mth.clamp(original * multiplier, 0.05D, 72000.0D);
    }

    public static boolean shouldCancelPlayerAttack(Player player, Entity target) {
        if (player == null || target == null || player.level() == null) {
            return false;
        }
        if (shouldDisableEntityTargeting(target, "player_attack")) {
            return true;
        }
        CorruptionEffectStack stack = activeStackFor(player);
        String targetId = "attack_routing:" + entityTargetId(target);
        if (!targetActive(stack, CorruptionSurface.INTERACTION_ROUTING, targetId, MIN_PLAYER_MECHANICS_INTENSITY)
                && !surfaceActive(stack, CorruptionSurface.ENTITY_STATE, MIN_ENTITY_MECHANICS_INTENSITY)) {
            return false;
        }
        float intensity = Mth.clamp(Math.max(
                stack.targetIntensity(CorruptionSurface.INTERACTION_ROUTING, targetId),
                Math.max(stack.intensity(CorruptionSurface.INTERACTION_ROUTING), stack.intensity(CorruptionSurface.ENTITY_STATE) * 0.72F)
        ), 0.0F, 1.0F);
        long hash = stack.stableLong(CorruptionSurface.INTERACTION_ROUTING, targetId, player.getId() ^ target.getId() ^ 0x4154544B);
        return unitHash(hash ^ 0x43414E43L) < Mth.clamp(0.04F + intensity * 0.78F + stack.instability() * 0.10F, 0.0F, 0.96F);
    }

    public static boolean shouldDisableEntityTargeting(Entity entity, String phase) {
        if (entity == null || entity.level() == null || entity instanceof Player || entity.isSpectator()) {
            return false;
        }
        CorruptionEffectStack stack = activeStackFor(entity);
        String targetId = "entity_hitbox_failure:" + phase + ":" + entityTargetId(entity);
        boolean active = surfaceActive(stack, CorruptionSurface.ENTITY_STATE, MIN_ENTITY_MECHANICS_INTENSITY)
                || targetActive(stack, CorruptionSurface.INTERACTION_ROUTING, targetId, MIN_ENTITY_MECHANICS_INTENSITY);
        if (!active) {
            return false;
        }

        float intensity = Mth.clamp(Math.max(
                stack.extreme(CorruptionSurface.ENTITY_STATE) ? 1.0F : stack.intensity(CorruptionSurface.ENTITY_STATE),
                (stack.extreme(CorruptionSurface.INTERACTION_ROUTING) ? 1.0F : stack.intensity(CorruptionSurface.INTERACTION_ROUTING)) * 0.82F
        ), 0.0F, 1.0F);
        String globalId = "entity_hitbox_failure:global:" + entity.level().dimension().location();
        long globalHash = stack.stableLong(CorruptionSurface.ENTITY_STATE, globalId, 0x474C4F42);
        if (stack.level() >= 85 && unitHash(globalHash ^ 0x414C4CL) < 0.18F + intensity * 0.62F) {
            return true;
        }

        long hash = stack.stableLong(CorruptionSurface.ENTITY_STATE, targetId, entity.getId() ^ 0x48495442);
        int bucket = collisionPositionBucket(entity, hash, intensity);
        float chance = stack.extreme(CorruptionSurface.ENTITY_STATE)
                ? 0.96F
                : Mth.clamp(0.05F + intensity * 0.72F + stack.instability() * 0.12F, 0.0F, 0.90F);
        return stack.unit(CorruptionSurface.ENTITY_STATE, targetId, bucket) < chance;
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
        CorruptionEffectStack stack = serverStack(level);
        if (entity instanceof Projectile projectile) {
            String targetId = projectileTargetId(projectile);
            if (!targetActive(stack, CorruptionSurface.PROJECTILE_PHYSICS, targetId, MIN_ENTITY_MECHANICS_INTENSITY)) {
                return;
            }
            mutateProjectileEntity(projectile, stack, targetId);
        } else if (entity instanceof LivingEntity living && !(living instanceof Player)) {
            String targetId = entityTargetId(living);
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
        CorruptionEffectStack stack = serverStack(event.getLevel());
        if (usesSaveStableVisualWorldgen(stack)) {
            return;
        }
        String targetId = "fluid_source:" + blockTargetId(event.getState());
        if (!targetActive(stack, CorruptionSurface.WORLDGEN_SURFACE, targetId, MIN_WORLD_PROCESS_INTENSITY)) {
            return;
        }

        float intensity = stack.targetIntensity(CorruptionSurface.WORLDGEN_SURFACE, targetId);
        boolean allow = CorruptionValueMutator.decision(stack, CorruptionSurface.WORLDGEN_SURFACE, targetId + ":allow", event.getPos().hashCode(), 0.58F + intensity * 0.18F);
        boolean deny = CorruptionValueMutator.decision(stack, CorruptionSurface.WORLDGEN_SURFACE, targetId + ":deny", event.getPos().hashCode() ^ 0x55, 0.42F + intensity * 0.16F);
        if (allow != deny) {
            event.setResult(allow ? Event.Result.ALLOW : Event.Result.DENY);
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

        if (CorruptionValueMutator.decision(stack, CorruptionSurface.BLOCK_COLLISION, targetId + ":cancel", event.getPos().hashCode(), 0.22F)) {
            event.setCanceled(true);
            return;
        }

        int exp = Math.round(CorruptionValueMutator.mutateScalar(stack, CorruptionSurface.BLOCK_COLLISION, targetId + ":xp", event.getExpToDrop(), 18.0F, 0.0F, 120.0F, 0x66, level.getGameTime()));
        event.setExpToDrop(exp);
    }

    @SubscribeEvent
    public static void onBlockPlace(BlockEvent.EntityPlaceEvent event) {
        if (!(event.getLevel() instanceof ServerLevel level)) {
            return;
        }

        CorruptionEffectStack stack = serverStack(level);
        String targetId = "block_place:" + blockTargetId(event.getPlacedBlock());
        if (targetActive(stack, CorruptionSurface.BLOCK_COLLISION, targetId, MIN_WORLD_PROCESS_INTENSITY)
                && CorruptionValueMutator.decision(stack, CorruptionSurface.BLOCK_COLLISION, targetId + ":cancel", event.getPos().hashCode(), 0.18F)) {
            event.setCanceled(true);
        }
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
        ResourceLocation itemId = ForgeRegistries.ITEMS.getKey(crafted.getItem());
        String targetId = "crafting_result:" + (itemId == null ? "unknown" : itemId);
        if (!targetActive(stack, CorruptionSurface.INTERACTION_ROUTING, targetId, MIN_WORLD_PROCESS_INTENSITY)) {
            return;
        }

        float intensity = stack.targetIntensity(CorruptionSurface.INTERACTION_ROUTING, targetId);
        long clock = player.serverLevel().getGameTime();
        int count = Math.round(CorruptionValueMutator.mutateScalar(stack, CorruptionSurface.INTERACTION_ROUTING, targetId + ":count", crafted.getCount(), 2.0F + intensity * 7.0F, 0.0F, crafted.getMaxStackSize(), 0x4352, clock));
        crafted.setCount(Math.max(0, count));
        if (crafted.isDamageableItem() && CorruptionValueMutator.decision(stack, CorruptionSurface.INTERACTION_ROUTING, targetId + ":damageable", crafted.hashCode(), 0.34F)) {
            int damage = Math.round(CorruptionValueMutator.mutateScalar(stack, CorruptionSurface.INTERACTION_ROUTING, targetId + ":damage", crafted.getDamageValue(), crafted.getMaxDamage() * 0.36F + 1.0F, 0.0F, crafted.getMaxDamage(), 0x444D, clock));
            crafted.setDamageValue(damage);
        }
    }

    @SubscribeEvent
    public static void onNeighborNotify(BlockEvent.NeighborNotifyEvent event) {
        if (!(event.getLevel() instanceof ServerLevel level)) {
            return;
        }

        CorruptionEffectStack stack = serverStack(level);
        if (shouldMutateWorldProcess(level, stack, CorruptionSurface.INTERACTION_ROUTING, "neighbor_notify", event.getState(), event.getPos(), 0x4E4F5449, 0.74F)) {
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

        long hash = worldProcessHash(stack, CorruptionSurface.INTERACTION_ROUTING, "tool_state_result", event.getState(), event.getPos(), 0x544F4F4C);
        if (unitHash(hash) < 0.56F) {
            event.setCanceled(true);
        } else {
            long stateHash = stableHash(level.getSeed(), event.getPos().getX(), event.getPos().getZ(), event.getPos().getY());
            event.setFinalState(mutatedNearbyBlockState(level, event.getPos(), stateHash, stack, event.getState(), false));
        }
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

    private static void mutateDamageEvent(LivingHurtEvent event, CorruptionEffectStack stack) {
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
        event.setAmount(amount);
    }

    private static void mutateLiquidMechanics(ServerPlayer player, CorruptionEffectStack stack) {
        boolean actualWater = isTouchingFluid(player, FluidTags.WATER);
        boolean actualLava = isTouchingFluid(player, FluidTags.LAVA);
        boolean detectedWater = player.isInWater() || player.isEyeInFluid(FluidTags.WATER);
        boolean detectedLava = player.isInLava() || player.isEyeInFluid(FluidTags.LAVA);
        boolean lava = actualLava || detectedLava;
        String fluid = lava ? "lava" : "water";
        String targetId = "liquid_mechanics:" + fluid + ":" + playerTargetId(player);

        if (!liquidMechanicsActive(stack, targetId)) {
            return;
        }

        CorruptionSurface surface = liquidSurface(stack, targetId);
        FluidFeatureFault fault = fluidFeatureFault(stack, surface, "liquid_mechanics:" + player.getUUID(), liquidMechanicsIntensity(stack, targetId));
        lava = actualLava || detectedLava;
        fluid = lava ? "lava" : "water";
        targetId = "liquid_mechanics:" + fluid + ":" + playerTargetId(player);
        float intensity = liquidMechanicsIntensity(stack, targetId);
        fault = fluidFeatureFault(stack, surface, targetId, intensity);
        int bucket = fluidPositionBucket(player, fault);
        boolean nearLiquid = actualWater || actualLava || detectedWater || detectedLava || isNearFluid(player, FluidTags.WATER) || isNearFluid(player, FluidTags.LAVA);
        if (!nearLiquid && !(player.getVehicle() instanceof Boat)) {
            return;
        }

        if (stack.unit(surface, targetId + ":current_feature", bucket) < Mth.clamp((0.10F + intensity * 0.62F) * fault.flowScale, 0.0F, 0.92F)) {
            mutateLiquidCollision(player, stack, targetId, surface, fault, intensity);
        }

        if (player.getVehicle() instanceof Boat boat) {
            mutateBoatLiquidMechanics(boat, player, stack);
        }
    }

    private static void mutateLiquidCollision(Entity entity, CorruptionEffectStack stack, String targetId, CorruptionSurface surface, FluidFeatureFault fault, float intensity) {
        int bucket = fluidPositionBucket(entity, fault);
        long clock = stack.stableLong(surface, targetId + ":flow_clock", bucket);
        Vec3 motion = entity.getDeltaMovement();
        Vec3 mutated = CorruptionValueMutator.mutateVector(stack, surface, targetId + ":flow", motion, (0.008D + intensity * 0.14D) * fault.flowScale, 2.2D, entity.getId(), clock);
        long seed = stack.stableLong(surface, targetId, entity.getId() ^ 0x4C495155);
        int period = Math.max(3, 18 - Math.round(intensity * 12.0F));
        float phase = ((entity.tickCount + (seed & 0x3FL)) % period) / (float) period;
        float pulse = collisionPulse(seed ^ 0x464C4F57L, phase);
        int mode = Math.floorMod((int) (seed >>> 28) + fault.flowMode, 7);
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
        FluidFeatureFault fault = fluidFeatureFault(stack, surface, "boat_liquid_mechanics:" + player.getUUID(), intensity);
        int bucket = fluidPositionBucket(boat, fault);
        long clock = stack.stableLong(surface, targetId + ":boat_clock", bucket);
        long seed = stack.stableLong(surface, targetId, boat.getId() ^ 0x424F4154);
        Vec3 motion = boat.getDeltaMovement();
        Vec3 mutated = CorruptionValueMutator.mutateVector(stack, surface, targetId + ":velocity", motion, (0.010D + intensity * 0.20D) * fault.boatScale, 3.0D, boat.getId(), clock);
        int mode = Math.floorMod((int) (seed >>> 31) + fault.flowMode, 6);
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

    private static void mutateDayTime(ServerLevel level, CorruptionEffectStack stack) {
        String targetId = "day_time:" + level.dimension().location();
        boolean worldgenActive = targetActive(stack, CorruptionSurface.WORLDGEN_SURFACE, targetId, MIN_DAY_TIME_INTENSITY);
        boolean tickSpeedActive = targetActive(stack, CorruptionSurface.TICK_SPEED, targetId, MIN_DAY_TIME_INTENSITY);
        if (!worldgenActive && !tickSpeedActive) {
            return;
        }
        CorruptionSurface surface = tickSpeedActive ? CorruptionSurface.TICK_SPEED : CorruptionSurface.WORLDGEN_SURFACE;
        if (tickSpeedActive) {
            double speed = gameSpeedMultiplier(stack, targetId);
            long offset = Math.round((speed - 1.0D) * 5.0D);
            if (offset != 0L) {
                level.setDayTime(level.getDayTime() + offset);
            }
            return;
        }
        float decisionChance = tickSpeedActive ? 0.94F : 0.28F;
        if (!CorruptionValueMutator.decision(stack, surface, targetId + ":tick", (int) level.getDayTime(), decisionChance)) {
            return;
        }

        float intensity = tickSpeedActive
                ? Math.max(stack.targetIntensity(CorruptionSurface.TICK_SPEED, targetId), stack.intensity(CorruptionSurface.TICK_SPEED))
                : stack.targetIntensity(CorruptionSurface.WORLDGEN_SURFACE, targetId);
        double span = tickSpeedActive ? 800.0D + intensity * 8400.0D : 200.0D + intensity * 3200.0D;
        double min = tickSpeedActive ? -12000.0D : -6000.0D;
        double max = tickSpeedActive ? 12000.0D : 6000.0D;
        long offset = Math.round(CorruptionValueMutator.mutateScalar(stack, surface, targetId + ":offset", 0.0D, span, min, max, 0x5449, level.getGameTime()));
        if (Math.abs(offset) >= 20L) {
            level.setDayTime(level.getDayTime() + offset);
        }
    }

    private static void mutateWeather(ServerLevel level, CorruptionEffectStack stack) {
        String targetId = "weather_system:" + level.dimension().location();
        boolean worldRenderActive = targetActive(stack, CorruptionSurface.WORLD_RENDER, targetId, MIN_WORLD_PROCESS_INTENSITY);
        boolean tickSpeedActive = targetActive(stack, CorruptionSurface.TICK_SPEED, targetId, MIN_DAY_TIME_INTENSITY);
        if (!worldRenderActive && !tickSpeedActive) {
            return;
        }

        float intensity = Mth.clamp(Math.max(
                stack.extreme(CorruptionSurface.WORLD_RENDER) ? 1.0F : stack.intensity(CorruptionSurface.WORLD_RENDER),
                (stack.extreme(CorruptionSurface.TICK_SPEED) ? 1.0F : stack.intensity(CorruptionSurface.TICK_SPEED)) * 0.66F
        ), 0.0F, 1.0F);
        long hash = stack.stableLong(CorruptionSurface.WORLD_RENDER, targetId, (int) level.getGameTime() ^ 0x57454154);
        float chance = Mth.clamp(0.04F + intensity * 0.72F + stack.instability() * 0.10F, 0.0F, 0.96F);
        if (!stack.extreme(CorruptionSurface.WORLD_RENDER) && unitHash(hash ^ 0x5449434BL) > chance) {
            return;
        }

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

    private static void applyAutoIncrease(MinecraftServer server) {
        if (server == null || server.getPlayerList().getPlayerCount() <= 0) {
            return;
        }

        CorruptionSavedData data = CorruptionSavedData.get(server);
        CorruptionRuntimeManager.syncGlobalSettings(data);
        int intervalTicks = data.getAutoIncreaseIntervalTicks();
        int amount = data.getAutoIncreaseAmount();
        if (intervalTicks <= 0 || amount == 0) {
            return;
        }

        long clock = Math.max(0L, server.overworld().getGameTime());
        long last = data.getLastAutoIncreaseGameTime();
        if (last > clock || last <= 0L) {
            data.setLastAutoIncreaseGameTime(clock);
            return;
        }
        if (clock - last < intervalTicks) {
            return;
        }

        int current = data.getCorruptionLevel();
        int next = clampInt(current + amount, 0, 100);
        data.setLastAutoIncreaseGameTime(clock);
        if (next == current) {
            return;
        }

        GlobalCorruptionSettings.applyAutoLevel(next);
        CorruptionCalibrationManager.applyCorruptionLevel(data, next);
        clearServerStackCache();
        ModNetwork.broadcastState(server);
    }

    private static void resetAutoIncreaseTimer(MinecraftServer server) {
        if (server == null) {
            return;
        }
        CorruptionSavedData data = CorruptionSavedData.get(server);
        CorruptionRuntimeManager.syncGlobalSettings(data);
        data.setLastAutoIncreaseGameTime(Math.max(0L, server.overworld().getGameTime()));
    }

    private static void syncSpeedModifier(AttributeInstance movementSpeed, double amount) {
        AttributeModifier existing = movementSpeed.getModifier(NON_PLAYER_SPEED_ID);
        if (existing != null && Math.abs(existing.getAmount() - amount) < 0.025D) {
            return;
        }
        removeSpeedModifier(movementSpeed);
        movementSpeed.addTransientModifier(new AttributeModifier(
                NON_PLAYER_SPEED_ID,
                NON_PLAYER_SPEED_NAME,
                amount,
                AttributeModifier.Operation.MULTIPLY_TOTAL
        ));
    }

    private static void syncPlayerMaxHealth(Player player, CorruptionEffectStack stack) {
        AttributeInstance maxHealth = player.getAttribute(Attributes.MAX_HEALTH);
        if (maxHealth == null) {
            return;
        }

        String targetId = "player_max_health:" + playerTargetId(player);
        boolean active = surfaceActive(stack, CorruptionSurface.PLAYER_PHYSICS, MIN_PLAYER_MECHANICS_INTENSITY)
                || targetActive(stack, CorruptionSurface.ENTITY_STATE, targetId, MIN_PLAYER_MECHANICS_INTENSITY)
                || targetActive(stack, CorruptionSurface.INTERACTION_ROUTING, targetId, MIN_PLAYER_MECHANICS_INTENSITY);
        if (!active) {
            removePlayerMaxHealthModifier(maxHealth);
            return;
        }

        float intensity = Mth.clamp(Math.max(
                stack.extreme(CorruptionSurface.PLAYER_PHYSICS) ? 1.0F : stack.intensity(CorruptionSurface.PLAYER_PHYSICS),
                Math.max(
                        (stack.extreme(CorruptionSurface.ENTITY_STATE) ? 1.0F : stack.intensity(CorruptionSurface.ENTITY_STATE)) * 0.78F,
                        (stack.extreme(CorruptionSurface.INTERACTION_ROUTING) ? 1.0F : stack.intensity(CorruptionSurface.INTERACTION_ROUTING)) * 0.54F
                )
        ), 0.0F, 1.0F);
        long hash = stack.stableLong(CorruptionSurface.PLAYER_PHYSICS, targetId, player.getId() ^ 0x4845414C);
        double exponent = signedUnit(hash ^ 0x5343414CL) * (stack.extreme(CorruptionSurface.PLAYER_PHYSICS) ? 4.15D : 2.45D) * intensity;
        if (unitHash(hash ^ 0x464C4950L) < 0.12F + intensity * 0.30F) {
            exponent = unitHash(hash ^ 0x4C4F5748L) < 0.50F
                    ? -4.20D * (0.35D + intensity * 0.65D)
                    : 3.40D * (0.35D + intensity * 0.65D);
        }
        double scale = Mth.clamp(Math.pow(2.0D, exponent), 0.05D, stack.extreme(CorruptionSurface.PLAYER_PHYSICS) ? 8.0D : 4.0D);
        double amount = scale - 1.0D;
        AttributeModifier existing = maxHealth.getModifier(PLAYER_MAX_HEALTH_ID);
        if (existing != null && Math.abs(existing.getAmount() - amount) < 0.015D) {
            clampPlayerHealthToMax(player);
            return;
        }

        removePlayerMaxHealthModifier(maxHealth);
        maxHealth.addTransientModifier(new AttributeModifier(
                PLAYER_MAX_HEALTH_ID,
                PLAYER_MAX_HEALTH_NAME,
                amount,
                AttributeModifier.Operation.MULTIPLY_TOTAL
        ));
        clampPlayerHealthToMax(player);
    }

    private static void removePlayerMaxHealthModifier(AttributeInstance maxHealth) {
        if (maxHealth.getModifier(PLAYER_MAX_HEALTH_ID) != null) {
            maxHealth.removeModifier(PLAYER_MAX_HEALTH_ID);
        }
    }

    private static void clampPlayerHealthToMax(Player player) {
        float max = player.getMaxHealth();
        if (player.getHealth() > max) {
            player.setHealth(max);
        } else if (player.getHealth() <= 0.0F && max > 0.0F && !player.isDeadOrDying()) {
            player.setHealth(Math.min(1.0F, max));
        }
    }

    private static double entitySpeedMultiplier(CorruptionEffectStack stack, String targetId) {
        double speedAmount = CorruptionValueMutator.mutateScalar(stack, CorruptionSurface.ENTITY_KINEMATICS, targetId, 0.0D, 1.40D + stack.instability() * 0.40D, -0.55D, 1.85D, 7, stack.level());
        if (!surfaceActive(stack, CorruptionSurface.TICK_SPEED, MIN_DAY_TIME_INTENSITY)) {
            return speedAmount;
        }
        double gameSpeed = Mth.clamp(gameSpeedMultiplier(stack, targetId + ":attribute"), 0.05D, 8.0D);
        return Mth.clamp((1.0D + speedAmount) * gameSpeed - 1.0D, -0.95D, 7.0D);
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

    private static Vec3 applyGameSpeedToVelocity(CorruptionEffectStack stack, String targetId, Vec3 motion, double maxMagnitude) {
        if (!surfaceActive(stack, CorruptionSurface.TICK_SPEED, MIN_DAY_TIME_INTENSITY)) {
            return motion;
        }
        double speed = Mth.clamp(gameSpeedMultiplier(stack, targetId + ":velocity"), 0.04D, 8.0D);
        Vec3 scaled = motion.scale(speed);
        double maxSqr = maxMagnitude * maxMagnitude;
        if (maxSqr > 0.0D && scaled.lengthSqr() > maxSqr) {
            scaled = scaled.normalize().scale(maxMagnitude);
        }
        return scaled;
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

    private static void mutatePlayerMotion(Player player, CorruptionEffectStack stack, String targetId, float intensity) {
        Vec3 motion = player.getDeltaMovement();
        long seed = stack.stableLong(CorruptionSurface.PLAYER_PHYSICS, targetId, player.getId() ^ 0x50485953);
        double horizontalScale = Mth.clamp(1.0D + signedUnit(seed ^ 0x48445247L) * intensity * 1.55D, -0.85D, 2.65D);
        double verticalScale = Mth.clamp(1.0D + signedUnit(seed ^ 0x56445247L) * intensity * 2.10D, -1.35D, 2.85D);
        double axisCoupling = signedUnit(seed ^ 0x43525558L) * intensity * 1.80D;
        double gravityBias = signedUnit(seed ^ 0x47524156L) * intensity * (player.onGround() ? 0.035D : 0.105D);

        Vec3 mutated = new Vec3(
                motion.x * horizontalScale + motion.z * axisCoupling,
                motion.y * verticalScale + gravityBias,
                motion.z * horizontalScale - motion.x * axisCoupling
        );

        if (unitHash(seed ^ 0x50524543L) < 0.18F + intensity * 0.46F) {
            double precision = 0.015625D + unitHash(seed ^ 0x5155414EL) * intensity * 0.34D;
            mutated = new Vec3(quantize(mutated.x, precision), quantize(mutated.y, precision), quantize(mutated.z, precision));
        }
        mutated = applyGameSpeedToVelocity(stack, targetId, mutated, 6.0D);

        if (mutated.distanceToSqr(motion) > 1.0E-7D) {
            player.setDeltaMovement(mutated);
            player.hasImpulse = true;
        }
    }

    private static void mutatePlayerCollision(Player player, CorruptionEffectStack stack) {
        mutateSharedBlockCollision(player, stack, "collision:" + playerTargetId(player), MIN_PLAYER_MECHANICS_INTENSITY);
    }

    private static void mutateSharedBlockCollision(Entity entity, CorruptionEffectStack stack, String targetId, float minimumIntensity) {
        if (!surfaceActive(stack, CorruptionSurface.BLOCK_COLLISION, minimumIntensity) || entity.isSpectator()) {
            return;
        }

        float intensity = Math.max(
                stack.targetIntensity(CorruptionSurface.BLOCK_COLLISION, targetId),
                stack.intensity(CorruptionSurface.BLOCK_COLLISION) * 0.78F
        );
        long seed = stack.stableLong(CorruptionSurface.BLOCK_COLLISION, targetId, entity.getId() ^ 0x434F4C4C);
        int bucket = collisionPositionBucket(entity, seed, intensity);
        float phase = unitHash(seed ^ bucket);
        float pulse = collisionPulse(seed, phase);

        Vec3 motion = entity.getDeltaMovement();
        boolean push = stack.unit(CorruptionSurface.BLOCK_COLLISION, targetId + ":push", bucket) < Math.min(0.86F, 0.14F + intensity * 0.46F + Math.abs(pulse) * 0.24F);
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

    private static void removeSpeedModifier(AttributeInstance movementSpeed) {
        if (movementSpeed.getModifier(NON_PLAYER_SPEED_ID) != null) {
            movementSpeed.removeModifier(NON_PLAYER_SPEED_ID);
        }
    }

    private static CorruptionEffectStack activeStackFor(Player player) {
        if (player instanceof ServerPlayer serverPlayer) {
            return serverStack(serverPlayer.serverLevel());
        }
        return player.level().isClientSide ? ClientCorruptionEffects.current() : CorruptionEffectStack.local(GlobalCorruptionSettings.activeLevel(), GlobalCorruptionSettings.seed(), GlobalCorruptionSettings.enabledTargetsMask());
    }

    private static CorruptionEffectStack activeStackFor(Entity entity) {
        if (entity instanceof Player player) {
            return activeStackFor(player);
        }
        Level level = entity.level();
        if (level instanceof ServerLevel serverLevel) {
            return serverStack(serverLevel);
        }
        return level != null && level.isClientSide
                ? ClientCorruptionEffects.current()
                : CorruptionEffectStack.local(GlobalCorruptionSettings.activeLevel(), GlobalCorruptionSettings.seed(), GlobalCorruptionSettings.enabledTargetsMask());
    }

    private static CorruptionEffectStack serverStack(LivingEntity entity) {
        if (!(entity.level() instanceof ServerLevel level) || level.getServer() == null) {
            return CorruptionEffectStack.local(GlobalCorruptionSettings.activeLevel(), GlobalCorruptionSettings.seed(), GlobalCorruptionSettings.enabledTargetsMask());
        }
        return serverStack(level);
    }

    private static CorruptionEffectStack serverStack(Level level) {
        if (level instanceof ServerLevel serverLevel) {
            return serverStack(serverLevel);
        }
        return CorruptionEffectStack.local(GlobalCorruptionSettings.activeLevel(), GlobalCorruptionSettings.seed(), GlobalCorruptionSettings.enabledTargetsMask());
    }

    private static CorruptionEffectStack serverStack(ServerLevel level) {
        MinecraftServer server = level.getServer();
        if (server == null) {
            return CorruptionEffectStack.local(GlobalCorruptionSettings.activeLevel(), GlobalCorruptionSettings.seed(), GlobalCorruptionSettings.enabledTargetsMask());
        }
        if (shouldSuspendServerMutations(server)) {
            return CorruptionEffectStack.local(0);
        }
        return cachedServerStack(server);
    }

    public static boolean shouldCancelNavigationMove(Mob mob, Entity target, Vec3 targetPosition, double speed, String route) {
        if (mob == null || mob.level().isClientSide || mob.isNoAi()) {
            return false;
        }
        CorruptionEffectStack stack = serverStack(mob);
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
        CorruptionEffectStack stack = serverStack(mob);
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
        CorruptionEffectStack stack = serverStack(mob);
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
        String targetType = target == null || ForgeRegistries.ENTITY_TYPES.getKey(target.getType()) == null
                ? "none"
                : ForgeRegistries.ENTITY_TYPES.getKey(target.getType()).toString();
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
                && stack.intensity(CorruptionSurface.WORLDGEN_SURFACE) >= MIN_PERSISTENT_TERRAIN_INTENSITY
                && stack.calibrationConfidence() >= MIN_PERSISTENT_TERRAIN_CONFIDENCE
                && stack.profileCoherence() >= MIN_PERSISTENT_TERRAIN_COHERENCE
                && stack.stabilityDebt() <= MAX_PERSISTENT_TERRAIN_DEBT
                && stack.delta() <= MAX_PERSISTENT_TERRAIN_DELTA;
    }

    private static boolean surfaceActive(CorruptionEffectStack stack, CorruptionSurface surface, float minimumIntensity) {
        return stack.extreme(surface) || (stack.intensity(surface) >= minimumIntensity && stack.active(surface));
    }

    private static boolean targetActive(CorruptionEffectStack stack, CorruptionSurface surface, String targetId, float minimumIntensity) {
        return stack.extreme(surface) || (stack.intensity(surface) >= minimumIntensity && stack.active(surface, targetId));
    }

    private static boolean liquidMechanicsActive(CorruptionEffectStack stack, String targetId) {
        return surfaceActive(stack, CorruptionSurface.WORLDGEN_SURFACE, MIN_LIQUID_MECHANICS_INTENSITY)
                || surfaceActive(stack, CorruptionSurface.PLAYER_PHYSICS, MIN_LIQUID_MECHANICS_INTENSITY)
                || surfaceActive(stack, CorruptionSurface.BLOCK_COLLISION, MIN_LIQUID_MECHANICS_INTENSITY)
                || surfaceActive(stack, CorruptionSurface.ENTITY_KINEMATICS, MIN_LIQUID_MECHANICS_INTENSITY)
                || targetActive(stack, CorruptionSurface.WORLDGEN_SURFACE, targetId, MIN_LIQUID_MECHANICS_INTENSITY)
                || targetActive(stack, CorruptionSurface.PLAYER_PHYSICS, targetId, MIN_LIQUID_MECHANICS_INTENSITY)
                || targetActive(stack, CorruptionSurface.BLOCK_COLLISION, targetId, MIN_LIQUID_MECHANICS_INTENSITY)
                || targetActive(stack, CorruptionSurface.ENTITY_KINEMATICS, targetId, MIN_LIQUID_MECHANICS_INTENSITY);
    }

    private static float liquidMechanicsIntensity(CorruptionEffectStack stack, String targetId) {
        return Mth.clamp(Math.max(
                Math.max(weightedSurfaceIntensity(stack, CorruptionSurface.WORLDGEN_SURFACE, targetId, 1.0F),
                        weightedSurfaceIntensity(stack, CorruptionSurface.PLAYER_PHYSICS, targetId, 0.88F)),
                Math.max(weightedSurfaceIntensity(stack, CorruptionSurface.BLOCK_COLLISION, targetId, 0.74F),
                        weightedSurfaceIntensity(stack, CorruptionSurface.ENTITY_KINEMATICS, targetId, 0.64F))
        ), 0.0F, 1.0F);
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
        CorruptionSurface best = CorruptionSurface.WORLDGEN_SURFACE;
        float bestIntensity = weightedSurfaceIntensity(stack, best, targetId, 1.0F);
        best = strongerLiquidSurface(stack, targetId, best, bestIntensity, CorruptionSurface.PLAYER_PHYSICS, 0.88F);
        bestIntensity = weightedSurfaceIntensity(stack, best, targetId, best == CorruptionSurface.PLAYER_PHYSICS ? 0.88F : 1.0F);
        best = strongerLiquidSurface(stack, targetId, best, bestIntensity, CorruptionSurface.BLOCK_COLLISION, 0.74F);
        bestIntensity = best == CorruptionSurface.BLOCK_COLLISION
                ? weightedSurfaceIntensity(stack, best, targetId, 0.74F)
                : weightedSurfaceIntensity(stack, best, targetId, best == CorruptionSurface.PLAYER_PHYSICS ? 0.88F : 1.0F);
        return strongerLiquidSurface(stack, targetId, best, bestIntensity, CorruptionSurface.ENTITY_KINEMATICS, 0.64F);
    }

    private static CorruptionSurface strongerLiquidSurface(CorruptionEffectStack stack, String targetId, CorruptionSurface current, float currentIntensity, CorruptionSurface candidate, float weight) {
        if (stack.extreme(candidate)) {
            return candidate;
        }
        float candidateIntensity = weightedSurfaceIntensity(stack, candidate, targetId, weight);
        return candidateIntensity > currentIntensity ? candidate : current;
    }

    private static FluidFeatureFault fluidFeatureFault(CorruptionEffectStack stack, CorruptionSurface surface, String targetId, float intensity) {
        long seed = stack.stableLong(surface, "fluid_feature:" + targetId, 0x4C514D45);
        double offsetSpan = Math.pow(Math.max(0.0F, intensity), 1.45D) * 20.0D;
        Vec3 sampleOffset = new Vec3(
                signedUnit(seed ^ 0x58574154L) * offsetSpan,
                signedUnit(seed ^ 0x59574154L) * offsetSpan * 0.72D,
                signedUnit(seed ^ 0x5A574154L) * offsetSpan
        );
        double precision = fluidPrecisionStep(seed ^ 0x50524543L, intensity);
        double sampleScale = Mth.clamp(0.18D + unitHash(seed ^ 0x5343414CL) * (0.90D + intensity * 3.20D), 0.08D, 4.0D);
        return new FluidFeatureFault(
                seed,
                sampleOffset,
                precision,
                sampleScale,
                featureScale(seed ^ 0x44455445L, intensity, 0.16F, 1.85F),
                featureScale(seed ^ 0x464C4F57L, intensity, 0.14F, 2.25F),
                featureScale(seed ^ 0x41495253L, intensity, 0.08F, 1.90F),
                featureScale(seed ^ 0x434F4C4CL, intensity, 0.06F, 2.10F),
                featureScale(seed ^ 0x47524156L, intensity, 0.06F, 1.95F),
                featureScale(seed ^ 0x48454154L, intensity, 0.03F, 1.35F),
                featureScale(seed ^ 0x424F4154L, intensity, 0.08F, 2.35F),
                Math.floorMod((int) (seed >>> 28), 7)
        );
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

    private static int fluidPositionBucket(Entity entity, FluidFeatureFault fault) {
        double step = fault.precisionStep <= 0.0D ? 0.5D : Math.max(0.5D, fault.precisionStep);
        int x = Mth.floor(quantize(entity.getX() + fault.sampleOffset.x, step) / step);
        int y = Mth.floor(quantize(entity.getY() + fault.sampleOffset.y, step) / step);
        int z = Mth.floor(quantize(entity.getZ() + fault.sampleOffset.z, step) / step);
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
        return applyGameSpeedToVelocity(stack, targetId, mutated, maxMagnitude);
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

    private static boolean sampleCorruptedFluid(Entity entity, TagKey<Fluid> fluidTag, boolean eyeOnly, FluidFeatureFault fault) {
        AABB base;
        if (eyeOnly) {
            double radius = 0.04D + fault.sampleScale * 0.10D;
            double eyeY = entity.getEyeY() - 0.11111111D;
            base = new AABB(entity.getX() - radius, eyeY - radius, entity.getZ() - radius, entity.getX() + radius, eyeY + radius, entity.getZ() + radius);
        } else {
            base = entity.getBoundingBox().deflate(0.001D);
        }

        AABB sampled = transformFluidSampleBox(base, fault);
        return isFluidInBox(entity.level(), sampled, fluidTag);
    }

    private static AABB transformFluidSampleBox(AABB base, FluidFeatureFault fault) {
        double centerX = (base.minX + base.maxX) * 0.5D + fault.sampleOffset.x;
        double centerY = (base.minY + base.maxY) * 0.5D + fault.sampleOffset.y;
        double centerZ = (base.minZ + base.maxZ) * 0.5D + fault.sampleOffset.z;
        if (fault.precisionStep > 0.0D) {
            centerX = quantize(centerX, fault.precisionStep);
            centerY = quantize(centerY, fault.precisionStep);
            centerZ = quantize(centerZ, fault.precisionStep);
        }

        double halfX = Mth.clamp((base.maxX - base.minX) * 0.5D * fault.sampleScale, 0.03125D, 2.5D);
        double halfY = Mth.clamp((base.maxY - base.minY) * 0.5D * fault.sampleScale, 0.03125D, 2.5D);
        double halfZ = Mth.clamp((base.maxZ - base.minZ) * 0.5D * fault.sampleScale, 0.03125D, 2.5D);
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

    private record FluidFeatureFault(
            long seed,
            Vec3 sampleOffset,
            double precisionStep,
            double sampleScale,
            float detectorScale,
            float flowScale,
            float airScale,
            float collisionScale,
            float gravityScale,
            float heatScale,
            float boatScale,
            int flowMode
    ) {
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

    private static synchronized CorruptionEffectStack cachedServerStack(MinecraftServer server) {
        int identity = System.identityHashCode(server);
        long tick = server.getTickCount();
        if (cachedServerIdentity == identity && cachedServerStackTick == tick) {
            return cachedServerStack;
        }

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

    private static synchronized void resetWorldProcessBudget() {
        worldProcessBudgetTick = Long.MIN_VALUE;
        worldProcessBudgetUsed = 0;
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

        BlockState sampled = level.getBlockState(sourcePos);
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
