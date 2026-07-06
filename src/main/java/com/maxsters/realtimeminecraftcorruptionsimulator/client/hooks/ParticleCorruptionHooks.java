package com.maxsters.realtimeminecraftcorruptionsimulator.client.hooks;

import com.maxsters.realtimeminecraftcorruptionsimulator.client.effects.ClientCorruptionEffects;
import com.maxsters.realtimeminecraftcorruptionsimulator.client.access.ParticleAccessor;
import com.maxsters.realtimeminecraftcorruptionsimulator.client.access.SingleQuadParticleAccessor;
import com.maxsters.realtimeminecraftcorruptionsimulator.profile.CorruptionEffectStack;
import com.maxsters.realtimeminecraftcorruptionsimulator.profile.CorruptionSurface;
import com.maxsters.realtimeminecraftcorruptionsimulator.profile.CorruptionValueMutator;
import net.minecraft.client.Minecraft;
import net.minecraft.client.particle.Particle;
import net.minecraft.client.particle.SingleQuadParticle;
import net.minecraft.util.Mth;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.api.distmarker.OnlyIn;

import java.util.Map;
import java.util.WeakHashMap;
import java.lang.reflect.Field;
import java.lang.reflect.Method;

@OnlyIn(Dist.CLIENT)
public final class ParticleCorruptionHooks {
    private static final int MIN_PARTICLE_BUDGET = 1_000;
    private static final int MAX_PARTICLE_BUDGET = 8_500;
    private static final Map<Particle, Integer> NORMAL_LIFETIMES = new WeakHashMap<>();
    private static final int FIELD_X = 0;
    private static final int FIELD_Y = 1;
    private static final int FIELD_Z = 2;
    private static final int FIELD_XD = 3;
    private static final int FIELD_YD = 4;
    private static final int FIELD_ZD = 5;
    private static final int FIELD_GRAVITY = 6;
    private static final int FIELD_WIDTH = 7;
    private static final int FIELD_HEIGHT = 8;
    private static final int FIELD_RED = 9;
    private static final int FIELD_GREEN = 10;
    private static final int FIELD_BLUE = 11;
    private static final int FIELD_ALPHA = 12;
    private static final int FIELD_ROLL = 13;
    private static final int FIELD_FRICTION = 14;
    private static final int FIELD_LIFETIME = 15;
    private static final int FIELD_HAS_PHYSICS = 16;
    private static final int FIELD_SPEED_UP_WHEN_BLOCKED = 17;
    private static final Field[] PARTICLE_FIELDS = new Field[18];
    private static final boolean[] PARTICLE_FIELDS_CHECKED = new boolean[18];
    private static Field quadSizeField;
    private static boolean quadSizeFieldChecked;
    private static Method setSizeMethod;
    private static boolean setSizeMethodChecked;
    private static Method setAlphaMethod;
    private static boolean setAlphaMethodChecked;
    private static final ParticleClassInfo UNKNOWN_PARTICLE = ParticleClassInfo.create("unknown");
    private static final ClassValue<ParticleClassInfo> PARTICLE_CLASS_INFO = new ClassValue<>() {
        @Override
        protected ParticleClassInfo computeValue(Class<?> type) {
            return ParticleClassInfo.create(type.getName());
        }
    };
    private static long budgetTick = Long.MIN_VALUE;
    private static int particlesSeenThisTick;

    private ParticleCorruptionHooks() {
    }

    public static boolean shouldCullParticleForBudget(Particle particle) {
        CorruptionEffectStack stack = ClientCorruptionEffects.currentForWorldRendering();
        float intensity = intensity(stack, targetId(particle, "budget"));
        if (intensity <= 0.01F) {
            return false;
        }

        Minecraft minecraft = Minecraft.getInstance();
        long time = minecraft.level == null ? 0L : minecraft.level.getGameTime();
        if (time != budgetTick) {
            budgetTick = time;
            particlesSeenThisTick = 0;
        }

        int seen = ++particlesSeenThisTick;
        int budget = particleBudget(intensity);
        if (seen <= budget) {
            return false;
        }
        if (intensity >= 0.995F) {
            return true;
        }

        float overflow = (seen - budget) / Math.max(1.0F, budget * 0.30F);
        if (overflow >= 1.0F) {
            return true;
        }

        int stableParticle = particleSalt(particle);
        long sample = stack.stableLong(CorruptionSurface.WORLD_RENDER, "particle_budget", stableParticle ^ seen ^ (int) time);
        return unit(sample) < Mth.clamp(0.20F + overflow * 0.72F, 0.0F, 0.95F);
    }

    public static void mutateTickedParticle(Particle particle) {
        if (particle == null) {
            return;
        }

        CorruptionEffectStack stack = ClientCorruptionEffects.currentForWorldRendering();
        if (!stack.activeOrExtreme(CorruptionSurface.WORLD_RENDER)) {
            return;
        }

        String targetId = targetId(particle, "state");
        float intensity = intensity(stack, targetId);
        if (intensity <= 0.01F || !shouldProcessParticle(particle, stack, targetId, intensity)) {
            return;
        }

        ParticleState state = particleState(particle);
        if (state == null) {
            return;
        }

        ParticleState mutated = mutateParticle(particle, state, stack, targetId, intensity);
        if (mutated == state) {
            return;
        }
        applyParticleState(particle, state, mutated);
    }

    public static boolean shouldProcessParticle(Particle particle) {
        if (particle == null) {
            return false;
        }

        CorruptionEffectStack stack = ClientCorruptionEffects.currentForWorldRendering();
        String targetId = targetId(particle, "state");
        if (!stack.activeOrExtreme(CorruptionSurface.WORLD_RENDER)) {
            return false;
        }

        float intensity = intensity(stack, targetId);
        if (intensity <= 0.01F) {
            return false;
        }

        return shouldProcessParticle(particle, stack, targetId, intensity);
    }

    private static boolean shouldProcessParticle(Particle particle, CorruptionEffectStack stack, String targetId, float intensity) {

        Minecraft minecraft = Minecraft.getInstance();
        long time = minecraft.level == null ? 0L : minecraft.level.getGameTime();
        int stableParticle = particleSalt(particle);
        float boosted = particleBoost(intensity);
        int cadence = stack.extreme(CorruptionSurface.WORLD_RENDER) || boosted > 0.55F ? 1 : boosted > 0.28F ? 2 : 3;
        if (Math.floorMod((int) time + stableParticle, cadence) != 0) {
            return false;
        }

        float chance = stack.extreme(CorruptionSurface.WORLD_RENDER)
                ? 0.96F
                : Mth.clamp(0.18F + boosted * 0.68F + stack.instability() * 0.12F, 0.0F, 0.96F);
        long sample = stack.stableLong(CorruptionSurface.WORLD_RENDER, targetId, stableParticle ^ ((int) (time / cadence) * 0x1F123BB5));
        return unit(sample) < chance;
    }

    public static ParticleState mutateParticle(Particle particle, ParticleState original) {
        CorruptionEffectStack stack = ClientCorruptionEffects.currentForWorldRendering();
        String targetId = targetId(particle, "state");
        if (!stack.activeOrExtreme(CorruptionSurface.WORLD_RENDER)) {
            return original;
        }

        float intensity = intensity(stack, targetId);
        if (intensity <= 0.01F) {
            return original;
        }

        return mutateParticle(particle, original, stack, targetId, intensity);
    }

    private static ParticleState mutateParticle(Particle particle, ParticleState original, CorruptionEffectStack stack, String targetId, float intensity) {
        ParticleClassInfo info = particleClassInfo(particle);
        float boosted = particleBoost(intensity);
        float strength = 1.0F + boosted * 3.0F;
        long clock = clock(stack, targetId, particle);
        boolean extreme = stack.extreme(CorruptionSurface.WORLD_RENDER);
        Vec3 velocity = original.velocity();
        Vec3 position = original.position();
        float gravity = original.gravity();
        float width = original.width();
        float height = original.height();
        float quadSize = original.quadSize();
        float red = original.red();
        float green = original.green();
        float blue = original.blue();
        float alpha = original.alpha();
        float roll = original.roll();
        float friction = original.friction();
        int lifetime = original.lifetime();
        boolean hasPhysics = original.hasPhysics();
        boolean speedUpWhenBlocked = original.speedUpWhenBlocked();
        boolean changed = false;

        float velocityChance = Mth.clamp(0.24F + boosted * 0.68F + stack.instability() * 0.10F, 0.0F, extreme ? 1.0F : 0.96F);
        if (unit(clock ^ 0x56454C4FL) < velocityChance) {
            double maxVelocity = extreme ? 24.0D : 8.0D + boosted * 14.0D;
            velocity = CorruptionValueMutator.mutateVectorComponents(stack, CorruptionSurface.WORLD_RENDER, info.velocityXTarget(), info.velocityYTarget(), info.velocityZTarget(), velocity, (0.05D + intensity * 0.75D) * strength, maxVelocity, 0x50415254, clock);
            int mode = Math.floorMod((int) (clock >>> 28), 8);
            velocity = switch (mode) {
                case 0 -> new Vec3(velocity.z, velocity.y, -velocity.x);
                case 1 -> new Vec3(-velocity.x, -velocity.y * (0.20D + boosted * 1.75D), -velocity.z);
                case 2 -> velocity.add(axisImpulse(clock, boosted, extreme ? 10.0D : 3.2D + boosted * 8.8D));
                case 3 -> new Vec3(velocity.x * signedScale(clock ^ 0x58415343L, boosted), velocity.y * signedScale(clock ^ 0x59415343L, boosted), velocity.z * signedScale(clock ^ 0x5A415343L, boosted));
                case 4 -> velocity.normalize().scale(0.02D + unit(clock ^ 0x53504544L) * maxVelocity);
                case 5 -> new Vec3(velocity.y, velocity.z, velocity.x).scale(0.20D + boosted * 2.8D);
                case 6 -> velocity.scale(unit(clock ^ 0x53544F50L) < 0.50F ? 0.0D : -1.0D - boosted * 2.2D);
                default -> velocity;
            };
            velocity = clampVector(velocity, maxVelocity);
            changed = true;
        }

        float gravityChance = Mth.clamp(0.20F + boosted * 0.62F + stack.instability() * 0.10F, 0.0F, extreme ? 1.0F : 0.92F);
        if (unit(clock ^ 0x47524156L) < gravityChance) {
            float span = (extreme ? 6.0F : 1.2F + intensity * 3.6F) * strength;
            gravity = CorruptionValueMutator.mutateScalar(stack, CorruptionSurface.WORLD_RENDER, info.stateGravityTarget(), gravity, span, -32.0F, 32.0F, 0x4752, clock);
            if (unit(clock ^ 0x474D4F44L) < 0.16F + boosted * 0.50F) {
                gravity = signedUnit(clock ^ 0x47444952L) * (0.35F + unit(clock ^ 0x4753504EL) * (extreme ? 24.0F : 7.0F + boosted * 17.0F));
            }
            changed = true;
        }

        if (unit(clock ^ 0x504F534CL) < 0.06F + boosted * 0.32F) {
            Vec3 offset = axisImpulse(clock ^ 0x504F5349L, boosted, extreme ? 2.8D : 0.6D + boosted * 2.2D);
            position = position.add(offset);
            changed = true;
        }

        if (unit(clock ^ 0x53495A45L) < 0.10F + boosted * 0.42F) {
            width = mutateParticleSize(stack, info.widthTarget(), width, clock ^ 0x57494454L, boosted, extreme);
            height = mutateParticleSize(stack, info.heightTarget(), height, clock ^ 0x48454947L, boosted, extreme);
            if (!Float.isNaN(quadSize)) {
                quadSize = mutateParticleSize(stack, info.quadTarget(), quadSize, clock ^ 0x51554144L, boosted, extreme);
            }
            changed = true;
        }

        if (unit(clock ^ 0x434F4C52L) < 0.08F + boosted * 0.38F) {
            red = CorruptionValueMutator.mutateScalar(stack, CorruptionSurface.WORLD_RENDER, info.redTarget(), red, 2.0F + boosted * 8.0F, -6.0F, 8.0F, 0x52, clock);
            green = CorruptionValueMutator.mutateScalar(stack, CorruptionSurface.WORLD_RENDER, info.greenTarget(), green, 2.0F + boosted * 8.0F, -6.0F, 8.0F, 0x47, clock);
            blue = CorruptionValueMutator.mutateScalar(stack, CorruptionSurface.WORLD_RENDER, info.blueTarget(), blue, 2.0F + boosted * 8.0F, -6.0F, 8.0F, 0x42, clock);
            alpha = CorruptionValueMutator.mutateScalar(stack, CorruptionSurface.WORLD_RENDER, info.alphaTarget(), alpha, 1.6F + boosted * 6.4F, -3.0F, 5.0F, 0x41, clock);
            changed = true;
        }

        if (unit(clock ^ 0x54494D45L) < 0.08F + boosted * 0.36F) {
            int normalLifetime = normalLifetime(particle, original.lifetime());
            int maxLifetime = Math.max(1, normalLifetime * 2);
            int minLifetime = Math.max(1, Math.round(maxLifetime * (extreme ? 0.05F : 0.15F)));
            float span = Math.max(6.0F + boosted * 38.0F, normalLifetime * (0.18F + boosted * 1.35F));
            lifetime = Mth.clamp(Math.round(CorruptionValueMutator.mutateScalar(stack, CorruptionSurface.WORLD_RENDER, info.lifetimeTarget(), lifetime, span, minLifetime, maxLifetime, 0x4C54, clock)), minLifetime, maxLifetime);
            changed = true;
        }

        if (unit(clock ^ 0x524F4C4CL) < 0.08F + boosted * 0.36F) {
            roll = CorruptionValueMutator.mutateScalar(stack, CorruptionSurface.WORLD_RENDER, info.rollTarget(), roll, (float) Math.PI * (1.0F + boosted * 12.0F), (float) -Math.PI * 24.0F, (float) Math.PI * 24.0F, 0x524F, clock);
            changed = true;
        }

        if (unit(clock ^ 0x46524943L) < 0.06F + boosted * 0.32F) {
            friction = CorruptionValueMutator.mutateScalar(stack, CorruptionSurface.WORLD_RENDER, info.frictionTarget(), friction, 0.25F + boosted * 1.85F, 0.005F, 3.25F, 0x4652, clock);
            changed = true;
        }

        if (unit(clock ^ 0x50485953L) < 0.05F + boosted * 0.26F) {
            hasPhysics = unit(clock ^ 0x48504859L) >= 0.46F;
            changed = true;
        }
        if (unit(clock ^ 0x424C4F43L) < 0.05F + boosted * 0.26F) {
            speedUpWhenBlocked = unit(clock ^ 0x53505550L) < 0.64F;
            changed = true;
        }

        return changed ? new ParticleState(position, velocity, gravity, width, height, quadSize, red, green, blue, alpha, roll, friction, lifetime, hasPhysics, speedUpWhenBlocked) : original;
    }

    public static float mutateGravity(Particle particle, float originalGravity) {
        CorruptionEffectStack stack = ClientCorruptionEffects.currentForWorldRendering();
        String targetId = targetId(particle, "gravity");
        if (!stack.activeOrExtreme(CorruptionSurface.WORLD_RENDER)) {
            return originalGravity;
        }

        float intensity = intensity(stack, targetId);
        if (intensity <= 0.01F) {
            return originalGravity;
        }
        long clock = clock(stack, targetId, particle);
        float chance = Mth.clamp(0.08F + intensity * 0.80F + stack.instability() * 0.10F, 0.0F, 0.96F);
        if (!stack.extreme(CorruptionSurface.WORLD_RENDER) && stack.unit(CorruptionSurface.WORLD_RENDER, targetId, (int) clock) > chance) {
            return originalGravity;
        }

        float span = stack.extreme(CorruptionSurface.WORLD_RENDER) ? 10.0F : 4.0F + intensity * 8.0F;
        float mutated = CorruptionValueMutator.mutateScalar(stack, CorruptionSurface.WORLD_RENDER, targetId, originalGravity, span, -24.0F, 24.0F, 0x47524156, clock);
        if (unit(clock ^ 0x55505744L) < 0.18F + intensity * 0.42F) {
            mutated = -(2.0F + unit(clock ^ 0x55504641L) * (stack.extreme(CorruptionSurface.WORLD_RENDER) ? 42.0F : 18.0F));
        }
        return mutated;
    }

    public static double mutateVerticalVelocity(Particle particle, double originalVelocity) {
        CorruptionEffectStack stack = ClientCorruptionEffects.currentForWorldRendering();
        String targetId = targetId(particle, "vertical_velocity");
        if (!stack.activeOrExtreme(CorruptionSurface.WORLD_RENDER)) {
            return originalVelocity;
        }

        float intensity = intensity(stack, targetId);
        if (intensity <= 0.01F) {
            return originalVelocity;
        }
        long clock = clock(stack, targetId, particle);
        float chance = Mth.clamp(0.04F + intensity * 0.58F + stack.instability() * 0.08F, 0.0F, 0.88F);
        if (!stack.extreme(CorruptionSurface.WORLD_RENDER) && unit(clock ^ 0x5943484EL) > chance) {
            return originalVelocity;
        }

        double mutated = CorruptionValueMutator.mutateScalar(stack, CorruptionSurface.WORLD_RENDER, targetId, originalVelocity, 0.08D + intensity * 1.80D, -12.0D, 16.0D, 0x5944, clock);
        if (unit(clock ^ 0x4C41554EL) < 0.10F + intensity * 0.34F) {
            mutated += 0.55D + unit(clock ^ 0x55505041L) * (stack.extreme(CorruptionSurface.WORLD_RENDER) ? 20.0D : 8.0D);
        }
        return Mth.clamp(mutated, -20.0D, 24.0D);
    }

    private static float intensity(CorruptionEffectStack stack, String targetId) {
        return Mth.clamp(stack.extreme(CorruptionSurface.WORLD_RENDER) ? 1.0F : stack.intensity(CorruptionSurface.WORLD_RENDER), 0.0F, 1.0F);
    }

    private static float particleBoost(float intensity) {
        return Mth.clamp(intensity * 4.0F, 0.0F, 1.0F);
    }

    private static String targetId(Particle particle, String feature) {
        ParticleClassInfo info = particleClassInfo(particle);
        return switch (feature) {
            case "budget" -> info.budgetTarget();
            case "state" -> info.stateTarget();
            case "gravity" -> info.gravityTarget();
            case "vertical_velocity" -> info.verticalVelocityTarget();
            default -> "particle:" + feature + ":" + info.className();
        };
    }

    private static long clock(CorruptionEffectStack stack, String targetId, Particle particle) {
        Minecraft minecraft = Minecraft.getInstance();
        long time = minecraft.level == null ? 0L : minecraft.level.getGameTime();
        return stack.stableLong(CorruptionSurface.WORLD_RENDER, targetId, particleSalt(particle) ^ 0x50415254) ^ (time << 16);
    }

    private static int particleSalt(Particle particle) {
        if (particle == null) {
            return 0;
        }
        AABB box = particle.getBoundingBox();
        double x = (box.minX + box.maxX) * 0.5D;
        double y = box.minY;
        double z = (box.minZ + box.maxZ) * 0.5D;
        int qx = Mth.floor(x * 8.0D);
        int qy = Mth.floor(y * 8.0D);
        int qz = Mth.floor(z * 8.0D);
        int hash = particleClassInfo(particle).classNameHash();
        hash = 31 * hash + qx;
        hash = 31 * hash + qy;
        hash = 31 * hash + qz;
        hash = 31 * hash + particle.getLifetime();
        return hash;
    }

    private static ParticleClassInfo particleClassInfo(Particle particle) {
        return particle == null ? UNKNOWN_PARTICLE : PARTICLE_CLASS_INFO.get(particle.getClass());
    }

    private static int normalLifetime(Particle particle, int currentLifetime) {
        int safeLifetime = Math.max(1, currentLifetime);
        if (particle == null) {
            return safeLifetime;
        }
        Integer existing = NORMAL_LIFETIMES.get(particle);
        if (existing != null && existing > 0) {
            return existing;
        }
        NORMAL_LIFETIMES.put(particle, safeLifetime);
        return safeLifetime;
    }

    private static ParticleState particleState(Particle particle) {
        if (ParticleFieldAccess.particleAvailable() && particle instanceof ParticleAccessor accessor) {
            float quadSize = ParticleFieldAccess.singleQuadAvailable() && particle instanceof SingleQuadParticleAccessor quadAccessor ? quadAccessor.rmc$getQuadSize() : Float.NaN;
            return new ParticleState(
                    new Vec3(accessor.rmc$getX(), accessor.rmc$getY(), accessor.rmc$getZ()),
                    new Vec3(accessor.rmc$getXd(), accessor.rmc$getYd(), accessor.rmc$getZd()),
                    accessor.rmc$getGravity(),
                    accessor.rmc$getBbWidth(),
                    accessor.rmc$getBbHeight(),
                    quadSize,
                    accessor.rmc$getRed(),
                    accessor.rmc$getGreen(),
                    accessor.rmc$getBlue(),
                    accessor.rmc$getAlpha(),
                    accessor.rmc$getRoll(),
                    accessor.rmc$getFriction(),
                    accessor.rmc$getLifetime(),
                    accessor.rmc$getHasPhysics(),
                    accessor.rmc$getSpeedUpWhenBlocked()
            );
        }

        Double x = doubleField(particle, FIELD_X, "x", "f_107212_");
        Double y = doubleField(particle, FIELD_Y, "y", "f_107213_");
        Double z = doubleField(particle, FIELD_Z, "z", "f_107214_");
        Double xd = doubleField(particle, FIELD_XD, "xd", "f_107215_");
        Double yd = doubleField(particle, FIELD_YD, "yd", "f_107216_");
        Double zd = doubleField(particle, FIELD_ZD, "zd", "f_107217_");
        Float gravity = floatField(particle, FIELD_GRAVITY, "gravity", "f_107226_");
        Float width = floatField(particle, FIELD_WIDTH, "bbWidth", "f_107221_");
        Float height = floatField(particle, FIELD_HEIGHT, "bbHeight", "f_107222_");
        Float red = floatField(particle, FIELD_RED, "rCol", "f_107227_");
        Float green = floatField(particle, FIELD_GREEN, "gCol", "f_107228_");
        Float blue = floatField(particle, FIELD_BLUE, "bCol", "f_107229_");
        Float alpha = floatField(particle, FIELD_ALPHA, "alpha", "f_107230_");
        Float roll = floatField(particle, FIELD_ROLL, "roll", "f_107231_");
        Float friction = floatField(particle, FIELD_FRICTION, "friction", "f_172258_");
        Integer lifetime = intField(particle, FIELD_LIFETIME, "lifetime", "f_107225_");
        Boolean hasPhysics = booleanField(particle, FIELD_HAS_PHYSICS, "hasPhysics", "f_107219_");
        Boolean speedUpWhenBlocked = booleanField(particle, FIELD_SPEED_UP_WHEN_BLOCKED, "speedUpWhenYMotionIsBlocked", "f_172259_");
        if (x == null || y == null || z == null || xd == null || yd == null || zd == null || gravity == null
                || width == null || height == null || red == null || green == null || blue == null || alpha == null
                || roll == null || friction == null || lifetime == null || hasPhysics == null || speedUpWhenBlocked == null) {
            return null;
        }

        return new ParticleState(
                new Vec3(x, y, z),
                new Vec3(xd, yd, zd),
                gravity,
                width,
                height,
                quadSize(particle),
                red,
                green,
                blue,
                alpha,
                roll,
                friction,
                lifetime,
                hasPhysics,
                speedUpWhenBlocked
        );
    }

    private static void applyParticleState(Particle particle, ParticleState original, ParticleState state) {
        if (ParticleFieldAccess.particleAvailable() && particle instanceof ParticleAccessor accessor) {
            applyParticleStateFast(particle, accessor, original, state);
            return;
        }

        Vec3 velocity = state.velocity();
        if (finite(velocity) && !sameVector(velocity, original.velocity())) {
            particle.setParticleSpeed(velocity.x, velocity.y, velocity.z);
        }
        if (changed(state.gravity(), original.gravity())) {
            setFloatField(particle, FIELD_GRAVITY, "gravity", "f_107226_", state.gravity());
        }
        if (Float.isFinite(state.width()) && Float.isFinite(state.height())
                && (changed(state.width(), original.width()) || changed(state.height(), original.height()))) {
            setSize(particle, state.width(), state.height());
        }
        if (particle instanceof SingleQuadParticle && Float.isFinite(state.quadSize()) && changed(state.quadSize(), original.quadSize())) {
            setQuadSize(particle, state.quadSize());
        }
        if (Float.isFinite(state.red()) && Float.isFinite(state.green()) && Float.isFinite(state.blue())
                && (changed(state.red(), original.red()) || changed(state.green(), original.green()) || changed(state.blue(), original.blue()))) {
            particle.setColor(state.red(), state.green(), state.blue());
        }
        if (Float.isFinite(state.alpha()) && changed(state.alpha(), original.alpha())) {
            setAlpha(particle, state.alpha());
        }
        if (changed(state.roll(), original.roll())) {
            setFloatField(particle, FIELD_ROLL, "roll", "f_107231_", state.roll());
        }
        if (changed(state.friction(), original.friction())) {
            setFloatField(particle, FIELD_FRICTION, "friction", "f_172258_", state.friction());
        }
        if (state.lifetime() > 0 && state.lifetime() != original.lifetime()) {
            particle.setLifetime(state.lifetime());
        }
        if (state.hasPhysics() != original.hasPhysics()) {
            setBooleanField(particle, FIELD_HAS_PHYSICS, "hasPhysics", "f_107219_", state.hasPhysics());
        }
        if (state.speedUpWhenBlocked() != original.speedUpWhenBlocked()) {
            setBooleanField(particle, FIELD_SPEED_UP_WHEN_BLOCKED, "speedUpWhenYMotionIsBlocked", "f_172259_", state.speedUpWhenBlocked());
        }
        Vec3 position = state.position();
        if (finite(position) && !sameVector(position, original.position())) {
            particle.setPos(position.x, position.y, position.z);
        }
    }

    private static void applyParticleStateFast(Particle particle, ParticleAccessor accessor, ParticleState original, ParticleState state) {
        Vec3 velocity = state.velocity();
        if (finite(velocity) && !sameVector(velocity, original.velocity())) {
            particle.setParticleSpeed(velocity.x, velocity.y, velocity.z);
        }
        if (changed(state.gravity(), original.gravity())) {
            accessor.rmc$setGravity(state.gravity());
        }
        if (Float.isFinite(state.width()) && Float.isFinite(state.height())
                && (changed(state.width(), original.width()) || changed(state.height(), original.height()))) {
            accessor.rmc$invokeSetSize(state.width(), state.height());
        }
        if (ParticleFieldAccess.singleQuadAvailable()
                && particle instanceof SingleQuadParticleAccessor quadAccessor
                && Float.isFinite(state.quadSize()) && changed(state.quadSize(), original.quadSize())) {
            quadAccessor.rmc$setQuadSize(state.quadSize());
        }
        if (Float.isFinite(state.red()) && Float.isFinite(state.green()) && Float.isFinite(state.blue())
                && (changed(state.red(), original.red()) || changed(state.green(), original.green()) || changed(state.blue(), original.blue()))) {
            particle.setColor(state.red(), state.green(), state.blue());
        }
        if (Float.isFinite(state.alpha()) && changed(state.alpha(), original.alpha())) {
            accessor.rmc$invokeSetAlpha(state.alpha());
        }
        if (changed(state.roll(), original.roll())) {
            accessor.rmc$setRoll(state.roll());
        }
        if (changed(state.friction(), original.friction())) {
            accessor.rmc$setFriction(state.friction());
        }
        if (state.lifetime() > 0 && state.lifetime() != original.lifetime()) {
            particle.setLifetime(state.lifetime());
        }
        if (state.hasPhysics() != original.hasPhysics()) {
            accessor.rmc$setHasPhysics(state.hasPhysics());
        }
        if (state.speedUpWhenBlocked() != original.speedUpWhenBlocked()) {
            accessor.rmc$setSpeedUpWhenBlocked(state.speedUpWhenBlocked());
        }
        Vec3 position = state.position();
        if (finite(position) && !sameVector(position, original.position())) {
            particle.setPos(position.x, position.y, position.z);
        }
    }

    private static Double doubleField(Particle particle, int index, String mappedName, String srgName) {
        Field field = particleField(index, mappedName, srgName);
        if (field == null) {
            return null;
        }
        try {
            return field.getDouble(particle);
        } catch (IllegalAccessException | RuntimeException ignored) {
            return null;
        }
    }

    private static Float floatField(Particle particle, int index, String mappedName, String srgName) {
        Field field = particleField(index, mappedName, srgName);
        if (field == null) {
            return null;
        }
        try {
            return field.getFloat(particle);
        } catch (IllegalAccessException | RuntimeException ignored) {
            return null;
        }
    }

    private static Integer intField(Particle particle, int index, String mappedName, String srgName) {
        Field field = particleField(index, mappedName, srgName);
        if (field == null) {
            return null;
        }
        try {
            return field.getInt(particle);
        } catch (IllegalAccessException | RuntimeException ignored) {
            return null;
        }
    }

    private static Boolean booleanField(Particle particle, int index, String mappedName, String srgName) {
        Field field = particleField(index, mappedName, srgName);
        if (field == null) {
            return null;
        }
        try {
            return field.getBoolean(particle);
        } catch (IllegalAccessException | RuntimeException ignored) {
            return null;
        }
    }

    private static void setFloatField(Particle particle, int index, String mappedName, String srgName, float value) {
        if (!Float.isFinite(value)) {
            return;
        }
        Field field = particleField(index, mappedName, srgName);
        if (field == null) {
            return;
        }
        try {
            field.setFloat(particle, value);
        } catch (IllegalAccessException | RuntimeException ignored) {
        }
    }

    private static void setBooleanField(Particle particle, int index, String mappedName, String srgName, boolean value) {
        Field field = particleField(index, mappedName, srgName);
        if (field == null) {
            return;
        }
        try {
            field.setBoolean(particle, value);
        } catch (IllegalAccessException | RuntimeException ignored) {
        }
    }

    private static Field particleField(int index, String mappedName, String srgName) {
        if (!PARTICLE_FIELDS_CHECKED[index]) {
            PARTICLE_FIELDS_CHECKED[index] = true;
            PARTICLE_FIELDS[index] = findField(Particle.class, mappedName, srgName);
        }
        return PARTICLE_FIELDS[index];
    }

    private static float quadSize(Particle particle) {
        if (ParticleFieldAccess.singleQuadAvailable() && particle instanceof SingleQuadParticleAccessor accessor) {
            return accessor.rmc$getQuadSize();
        }

        Field field = quadSizeField();
        if (field == null) {
            return Float.NaN;
        }
        try {
            return field.getFloat(particle);
        } catch (IllegalAccessException | RuntimeException ignored) {
            return Float.NaN;
        }
    }

    private static void setQuadSize(Particle particle, float value) {
        if (ParticleFieldAccess.singleQuadAvailable() && particle instanceof SingleQuadParticleAccessor accessor) {
            accessor.rmc$setQuadSize(value);
            return;
        }

        Field field = quadSizeField();
        if (field == null) {
            return;
        }
        try {
            field.setFloat(particle, value);
        } catch (IllegalAccessException | RuntimeException ignored) {
        }
    }

    private static Field quadSizeField() {
        if (!quadSizeFieldChecked) {
            quadSizeFieldChecked = true;
            quadSizeField = findField(SingleQuadParticle.class, "quadSize", "f_107663_");
        }
        return quadSizeField;
    }

    private static void setSize(Particle particle, float width, float height) {
        Method method = setSizeMethod();
        if (method == null) {
            return;
        }
        try {
            method.invoke(particle, width, height);
        } catch (ReflectiveOperationException | RuntimeException ignored) {
        }
    }

    private static Method setSizeMethod() {
        if (!setSizeMethodChecked) {
            setSizeMethodChecked = true;
            setSizeMethod = findMethod(Particle.class, new String[]{"setSize", "m_107250_"}, float.class, float.class);
        }
        return setSizeMethod;
    }

    private static void setAlpha(Particle particle, float alpha) {
        Method method = setAlphaMethod();
        if (method == null) {
            return;
        }
        try {
            method.invoke(particle, alpha);
        } catch (ReflectiveOperationException | RuntimeException ignored) {
        }
    }

    private static Method setAlphaMethod() {
        if (!setAlphaMethodChecked) {
            setAlphaMethodChecked = true;
            setAlphaMethod = findMethod(Particle.class, new String[]{"setAlpha", "m_107271_"}, float.class);
        }
        return setAlphaMethod;
    }

    private static Field findField(Class<?> owner, String mappedName, String srgName) {
        for (String name : new String[]{mappedName, srgName}) {
            try {
                Field field = owner.getDeclaredField(name);
                field.setAccessible(true);
                return field;
            } catch (NoSuchFieldException | RuntimeException ignored) {
            }
        }
        return null;
    }

    private static Method findMethod(Class<?> owner, String[] names, Class<?>... parameterTypes) {
        for (String name : names) {
            try {
                Method method = owner.getDeclaredMethod(name, parameterTypes);
                method.setAccessible(true);
                return method;
            } catch (NoSuchMethodException | RuntimeException ignored) {
            }
        }
        return null;
    }

    private static boolean finite(Vec3 vector) {
        return vector != null
                && Double.isFinite(vector.x)
                && Double.isFinite(vector.y)
                && Double.isFinite(vector.z);
    }

    private static boolean changed(float left, float right) {
        return Float.compare(left, right) != 0 && Float.isFinite(left);
    }

    private static boolean sameVector(Vec3 left, Vec3 right) {
        return left == right || (left != null
                && right != null
                && Double.compare(left.x, right.x) == 0
                && Double.compare(left.y, right.y) == 0
                && Double.compare(left.z, right.z) == 0);
    }

    private static float unit(long value) {
        value ^= value >>> 33;
        value *= 0xff51afd7ed558ccdL;
        value ^= value >>> 33;
        value *= 0xc4ceb9fe1a85ec53L;
        value ^= value >>> 33;
        return ((value >>> 40) & 0xFF_FFFFL) / 16_777_215.0F;
    }

    private static float signedUnit(long value) {
        return unit(value) * 2.0F - 1.0F;
    }

    private static double signedScale(long value, float intensity) {
        return Mth.clamp(1.0D + signedUnit(value) * intensity * 1.6D, -1.6D, 2.8D);
    }

    private static Vec3 axisImpulse(long seed, float intensity, double maxMagnitude) {
        return new Vec3(
                signedUnit(seed ^ 0x58415849L) * intensity * maxMagnitude,
                signedUnit(seed ^ 0x59415849L) * intensity * maxMagnitude,
                signedUnit(seed ^ 0x5A415849L) * intensity * maxMagnitude
        );
    }

    private static Vec3 clampVector(Vec3 value, double maxMagnitude) {
        if (value.lengthSqr() <= maxMagnitude * maxMagnitude) {
            return value;
        }
        return value.normalize().scale(maxMagnitude);
    }

    private static float mutateParticleSize(CorruptionEffectStack stack, String targetId, float original, long clock, float intensity, boolean extreme) {
        float safeOriginal = Float.isFinite(original) && original > 0.0F ? original : 0.02F;
        float exponent = signedUnit(clock ^ 0x5343414CL) * intensity * (extreme ? 3.5F : 2.4F);
        float scaled = (float) (safeOriginal * Math.pow(2.0D, exponent));
        if (unit(clock ^ 0x46554C4CL) < 0.06F + intensity * 0.16F) {
            scaled = unit(clock ^ 0x54494E59L) < 0.46F
                    ? 0.001F + unit(clock ^ 0x4D494E49L) * 0.018F
                    : safeOriginal + intensity * (extreme ? 4.0F : 1.8F) * (0.25F + unit(clock ^ 0x48554745L) * 0.75F);
        }
        return CorruptionValueMutator.mutateScalar(stack, CorruptionSurface.WORLD_RENDER, targetId, scaled, safeOriginal * (extreme ? 5.0F : 2.5F), 0.001F, extreme ? 6.0F : 3.0F, targetId.hashCode(), clock);
    }

    private static int particleBudget(float intensity) {
        return Math.round(Mth.lerp(Mth.clamp(intensity, 0.0F, 1.0F), MAX_PARTICLE_BUDGET, MIN_PARTICLE_BUDGET));
    }

    public record ParticleState(
            Vec3 position,
            Vec3 velocity,
            float gravity,
            float width,
            float height,
            float quadSize,
            float red,
            float green,
            float blue,
            float alpha,
            float roll,
            float friction,
            int lifetime,
            boolean hasPhysics,
            boolean speedUpWhenBlocked
    ) {
    }

    private record ParticleClassInfo(
            String className,
            int classNameHash,
            String budgetTarget,
            String stateTarget,
            String gravityTarget,
            String verticalVelocityTarget,
            String velocityXTarget,
            String velocityYTarget,
            String velocityZTarget,
            String stateGravityTarget,
            String widthTarget,
            String heightTarget,
            String quadTarget,
            String redTarget,
            String greenTarget,
            String blueTarget,
            String alphaTarget,
            String lifetimeTarget,
            String rollTarget,
            String frictionTarget
    ) {
        private static ParticleClassInfo create(String className) {
            String stateTarget = "particle:state:" + className;
            String velocityTarget = stateTarget + ":velocity";
            return new ParticleClassInfo(
                    className,
                    className.hashCode(),
                    "particle:budget:" + className,
                    stateTarget,
                    "particle:gravity:" + className,
                    "particle:vertical_velocity:" + className,
                    velocityTarget + ":x",
                    velocityTarget + ":y",
                    velocityTarget + ":z",
                    stateTarget + ":gravity",
                    stateTarget + ":width",
                    stateTarget + ":height",
                    stateTarget + ":quad",
                    stateTarget + ":red",
                    stateTarget + ":green",
                    stateTarget + ":blue",
                    stateTarget + ":alpha",
                    stateTarget + ":lifetime",
                    stateTarget + ":roll",
                    stateTarget + ":friction"
            );
        }
    }
}
