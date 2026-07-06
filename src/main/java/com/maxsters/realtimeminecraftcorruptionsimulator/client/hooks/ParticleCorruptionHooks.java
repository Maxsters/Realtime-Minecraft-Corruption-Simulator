package com.maxsters.realtimeminecraftcorruptionsimulator.client.hooks;

import com.maxsters.realtimeminecraftcorruptionsimulator.client.effects.ClientCorruptionEffects;
import com.maxsters.realtimeminecraftcorruptionsimulator.client.access.ParticleAccessor;
import com.maxsters.realtimeminecraftcorruptionsimulator.client.access.SingleQuadParticleAccessor;
import com.maxsters.realtimeminecraftcorruptionsimulator.profile.CorruptionEffectStack;
import com.maxsters.realtimeminecraftcorruptionsimulator.profile.CorruptionSurface;
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
    private static final String SHARED_PARTICLE_TARGET = "particle:shared_state";
    private static final int MIN_PARTICLE_BUDGET = 1_000;
    private static final int MAX_PARTICLE_BUDGET = 8_500;
    private static final Map<Particle, Integer> NORMAL_LIFETIMES = new WeakHashMap<>();
    private static final Map<Particle, Long> APPLIED_PROFILE_KEYS = new WeakHashMap<>();
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
    private static CorruptionEffectStack sharedProfileStack;
    private static SharedParticleProfile sharedProfile = SharedParticleProfile.inactive();

    private ParticleCorruptionHooks() {
    }

    public static boolean shouldCullParticleForBudget(Particle particle) {
        CorruptionEffectStack stack = ClientCorruptionEffects.currentForWorldRendering();
        float intensity = intensity(stack, particleClassInfo(particle).budgetTarget());
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

        SharedParticleProfile profile = sharedParticleProfile(stack, intensity(stack, SHARED_PARTICLE_TARGET));
        if (!profile.active()) {
            return;
        }
        Long appliedKey = APPLIED_PROFILE_KEYS.get(particle);
        if (appliedKey != null && appliedKey == profile.key()) {
            return;
        }

        ParticleState state = particleState(particle);
        if (state == null) {
            return;
        }

        ParticleState mutated = mutateParticle(particle, state, profile);
        if (mutated == state) {
            APPLIED_PROFILE_KEYS.put(particle, profile.key());
            return;
        }
        applyParticleState(particle, state, mutated);
        APPLIED_PROFILE_KEYS.put(particle, profile.key());
    }

    public static boolean shouldProcessParticle(Particle particle) {
        if (particle == null) {
            return false;
        }

        CorruptionEffectStack stack = ClientCorruptionEffects.currentForWorldRendering();
        if (!stack.activeOrExtreme(CorruptionSurface.WORLD_RENDER)) {
            return false;
        }

        return sharedParticleProfile(stack, intensity(stack, SHARED_PARTICLE_TARGET)).active();
    }

    public static ParticleState mutateParticle(Particle particle, ParticleState original) {
        CorruptionEffectStack stack = ClientCorruptionEffects.currentForWorldRendering();
        if (!stack.activeOrExtreme(CorruptionSurface.WORLD_RENDER)) {
            return original;
        }

        SharedParticleProfile profile = sharedParticleProfile(stack, intensity(stack, SHARED_PARTICLE_TARGET));
        if (!profile.active()) {
            return original;
        }

        return mutateParticle(particle, original, profile);
    }

    private static ParticleState mutateParticle(Particle particle, ParticleState original, SharedParticleProfile profile) {
        Vec3 velocity = profile.mutateVelocity(original.velocity());
        Vec3 position = profile.mutatePosition(original.position());
        float gravity = profile.mutateGravity(original.gravity());
        float width = profile.mutateSize(original.width(), profile.widthScale());
        float height = profile.mutateSize(original.height(), profile.heightScale());
        float quadSize = Float.isNaN(original.quadSize()) ? original.quadSize() : profile.mutateSize(original.quadSize(), profile.quadScale());
        float red = profile.mutateColor(original.red(), profile.redScale(), profile.redAdd(), -6.0F, 8.0F);
        float green = profile.mutateColor(original.green(), profile.greenScale(), profile.greenAdd(), -6.0F, 8.0F);
        float blue = profile.mutateColor(original.blue(), profile.blueScale(), profile.blueAdd(), -6.0F, 8.0F);
        float alpha = profile.mutateColor(original.alpha(), profile.alphaScale(), profile.alphaAdd(), -3.0F, 5.0F);
        float roll = Mth.clamp(original.roll() + profile.rollAdd(), (float) -Math.PI * 24.0F, (float) Math.PI * 24.0F);
        float friction = Mth.clamp(original.friction() * profile.frictionScale() + profile.frictionAdd(), 0.005F, 3.25F);
        int lifetime = profile.mutateLifetime(particle, original);
        boolean hasPhysics = profile.mutatePhysics() ? profile.hasPhysics() : original.hasPhysics();
        boolean speedUpWhenBlocked = profile.mutateSpeedUpWhenBlocked() ? profile.speedUpWhenBlocked() : original.speedUpWhenBlocked();
        boolean changed = false;

        if (!sameVector(velocity, original.velocity())) {
            changed = true;
        }
        if (!sameVector(position, original.position())) {
            changed = true;
        }
        if (changed(gravity, original.gravity())
                || changed(width, original.width())
                || changed(height, original.height())
                || changed(quadSize, original.quadSize())
                || changed(red, original.red())
                || changed(green, original.green())
                || changed(blue, original.blue())
                || changed(alpha, original.alpha())
                || changed(roll, original.roll())
                || changed(friction, original.friction())
                || lifetime != original.lifetime()
                || hasPhysics != original.hasPhysics()
                || speedUpWhenBlocked != original.speedUpWhenBlocked()) {
            changed = true;
        }

        return changed ? new ParticleState(position, velocity, gravity, width, height, quadSize, red, green, blue, alpha, roll, friction, lifetime, hasPhysics, speedUpWhenBlocked) : original;
    }

    public static float mutateGravity(Particle particle, float originalGravity) {
        CorruptionEffectStack stack = ClientCorruptionEffects.currentForWorldRendering();
        if (!stack.activeOrExtreme(CorruptionSurface.WORLD_RENDER)) {
            return originalGravity;
        }

        SharedParticleProfile profile = sharedParticleProfile(stack, intensity(stack, SHARED_PARTICLE_TARGET));
        if (!profile.active()) {
            return originalGravity;
        }
        return profile.mutateGravity(originalGravity);
    }

    public static double mutateVerticalVelocity(Particle particle, double originalVelocity) {
        CorruptionEffectStack stack = ClientCorruptionEffects.currentForWorldRendering();
        if (!stack.activeOrExtreme(CorruptionSurface.WORLD_RENDER)) {
            return originalVelocity;
        }

        SharedParticleProfile profile = sharedParticleProfile(stack, intensity(stack, SHARED_PARTICLE_TARGET));
        if (!profile.active()) {
            return originalVelocity;
        }
        return Mth.clamp(originalVelocity * profile.velocityScaleY() + profile.velocityAdd().y, -20.0D, 24.0D);
    }

    private static SharedParticleProfile sharedParticleProfile(CorruptionEffectStack stack, float intensity) {
        if (stack == sharedProfileStack) {
            return sharedProfile;
        }

        sharedProfileStack = stack;
        APPLIED_PROFILE_KEYS.clear();
        if (!stack.activeOrExtreme(CorruptionSurface.WORLD_RENDER) || intensity <= 0.01F) {
            sharedProfile = SharedParticleProfile.inactive();
            return sharedProfile;
        }

        float boosted = particleBoost(intensity);
        boolean extreme = stack.extreme(CorruptionSurface.WORLD_RENDER);
        long seed = stack.stableLong(CorruptionSurface.WORLD_RENDER, SHARED_PARTICLE_TARGET, 0xB1A57);
        float activity = extreme ? 1.0F : Mth.clamp(0.34F + boosted * 0.62F + stack.instability() * 0.10F, 0.0F, 0.98F);
        if (unit(seed ^ 0x414354495645L) > activity) {
            sharedProfile = SharedParticleProfile.inactive();
            return sharedProfile;
        }

        double maxVelocity = extreme ? 24.0D : 8.0D + boosted * 14.0D;
        int velocityMode = Math.floorMod((int) (seed >>> 28), 8);
        boolean mutateVelocity = unit(seed ^ 0x56454C4FL) < Mth.clamp(0.28F + boosted * 0.70F + stack.instability() * 0.08F, 0.0F, 1.0F);
        double velocityScaleX = mutateVelocity ? signedScale(seed ^ 0x58415343L, boosted) : 1.0D;
        double velocityScaleY = mutateVelocity ? signedScale(seed ^ 0x59415343L, boosted) : 1.0D;
        double velocityScaleZ = mutateVelocity ? signedScale(seed ^ 0x5A415343L, boosted) : 1.0D;
        Vec3 velocityAdd = mutateVelocity ? axisImpulse(seed ^ 0x56414C55L, boosted, extreme ? 10.0D : 3.2D + boosted * 8.8D) : Vec3.ZERO;
        double velocityMagnitude = mutateVelocity ? 0.02D + unit(seed ^ 0x53504544L) * maxVelocity : 0.0D;

        boolean mutateGravity = unit(seed ^ 0x47524156L) < Mth.clamp(0.22F + boosted * 0.66F + stack.instability() * 0.08F, 0.0F, 1.0F);
        boolean replaceGravity = mutateGravity && unit(seed ^ 0x474D4F44L) < 0.16F + boosted * 0.50F;
        float gravityScale = mutateGravity ? Mth.clamp(1.0F + signedUnit(seed ^ 0x47534341L) * boosted * (extreme ? 12.0F : 5.0F), -12.0F, 12.0F) : 1.0F;
        float gravityAdd = mutateGravity ? signedUnit(seed ^ 0x47414444L) * (extreme ? 6.0F : 1.2F + intensity * 3.6F) * (1.0F + boosted * 3.0F) : 0.0F;
        float gravityReplacement = replaceGravity
                ? signedUnit(seed ^ 0x47444952L) * (0.35F + unit(seed ^ 0x4753504EL) * (extreme ? 24.0F : 7.0F + boosted * 17.0F))
                : 0.0F;

        Vec3 positionAdd = unit(seed ^ 0x504F534CL) < 0.04F + boosted * 0.22F
                ? axisImpulse(seed ^ 0x504F5349L, boosted, extreme ? 1.2D : 0.25D + boosted * 0.9D)
                : Vec3.ZERO;

        boolean mutateSize = unit(seed ^ 0x53495A45L) < 0.12F + boosted * 0.48F;
        float widthScale = mutateSize ? sizeScale(seed ^ 0x57494454L, boosted, extreme) : 1.0F;
        float heightScale = mutateSize ? sizeScale(seed ^ 0x48454947L, boosted, extreme) : 1.0F;
        float quadScale = mutateSize ? sizeScale(seed ^ 0x51554144L, boosted, extreme) : 1.0F;

        boolean mutateColor = unit(seed ^ 0x434F4C52L) < 0.10F + boosted * 0.46F;
        float redScale = mutateColor ? colorScale(seed ^ 0x52454453L, boosted) : 1.0F;
        float greenScale = mutateColor ? colorScale(seed ^ 0x47524545L, boosted) : 1.0F;
        float blueScale = mutateColor ? colorScale(seed ^ 0x424C5545L, boosted) : 1.0F;
        float alphaScale = mutateColor ? Mth.clamp(1.0F + signedUnit(seed ^ 0x414C5048L) * boosted * 3.0F, -2.0F, 4.0F) : 1.0F;
        float redAdd = mutateColor ? signedUnit(seed ^ 0x52414444L) * (2.0F + boosted * 8.0F) : 0.0F;
        float greenAdd = mutateColor ? signedUnit(seed ^ 0x47414444L) * (2.0F + boosted * 8.0F) : 0.0F;
        float blueAdd = mutateColor ? signedUnit(seed ^ 0x42414444L) * (2.0F + boosted * 8.0F) : 0.0F;
        float alphaAdd = mutateColor ? signedUnit(seed ^ 0x41414444L) * (1.6F + boosted * 6.4F) : 0.0F;

        float rollAdd = unit(seed ^ 0x524F4C4CL) < 0.08F + boosted * 0.38F
                ? signedUnit(seed ^ 0x52414444L) * (float) Math.PI * (1.0F + boosted * 12.0F)
                : 0.0F;
        boolean mutateFriction = unit(seed ^ 0x46524943L) < 0.06F + boosted * 0.34F;
        float frictionScale = mutateFriction ? Mth.clamp(1.0F + signedUnit(seed ^ 0x46534341L) * boosted * 2.0F, 0.01F, 3.25F) : 1.0F;
        float frictionAdd = mutateFriction ? signedUnit(seed ^ 0x46414444L) * (0.25F + boosted * 1.85F) : 0.0F;

        boolean mutateLifetime = unit(seed ^ 0x54494D45L) < 0.08F + boosted * 0.38F;
        float lifetimeScale = mutateLifetime ? Mth.clamp(1.0F + signedUnit(seed ^ 0x4C534341L) * boosted * 1.75F, 0.05F, 2.0F) : 1.0F;
        int lifetimeAdd = mutateLifetime ? Math.round(signedUnit(seed ^ 0x4C414444L) * (6.0F + boosted * 38.0F)) : 0;

        boolean mutatePhysics = unit(seed ^ 0x50485953L) < 0.05F + boosted * 0.28F;
        boolean mutateSpeedUpWhenBlocked = unit(seed ^ 0x424C4F43L) < 0.05F + boosted * 0.28F;
        boolean active = mutateVelocity || mutateGravity || !positionAdd.equals(Vec3.ZERO) || mutateSize || mutateColor
                || rollAdd != 0.0F || mutateFriction || mutateLifetime || mutatePhysics || mutateSpeedUpWhenBlocked;
        sharedProfile = active ? new SharedParticleProfile(
                true,
                seed,
                extreme,
                velocityMode,
                mutateVelocity,
                velocityScaleX,
                velocityScaleY,
                velocityScaleZ,
                velocityAdd,
                velocityMagnitude,
                maxVelocity,
                mutateGravity,
                replaceGravity,
                gravityReplacement,
                gravityScale,
                gravityAdd,
                positionAdd,
                widthScale,
                heightScale,
                quadScale,
                redScale,
                greenScale,
                blueScale,
                alphaScale,
                redAdd,
                greenAdd,
                blueAdd,
                alphaAdd,
                rollAdd,
                frictionScale,
                frictionAdd,
                lifetimeScale,
                lifetimeAdd,
                mutatePhysics,
                unit(seed ^ 0x48504859L) >= 0.46F,
                mutateSpeedUpWhenBlocked,
                unit(seed ^ 0x53505550L) < 0.64F
        ) : SharedParticleProfile.inactive();
        return sharedProfile;
    }

    private static float intensity(CorruptionEffectStack stack, String targetId) {
        return Mth.clamp(stack.extreme(CorruptionSurface.WORLD_RENDER) ? 1.0F : stack.intensity(CorruptionSurface.WORLD_RENDER), 0.0F, 1.0F);
    }

    private static float particleBoost(float intensity) {
        return Mth.clamp(intensity * 4.0F, 0.0F, 1.0F);
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

    private static float sizeScale(long seed, float intensity, boolean extreme) {
        float exponent = signedUnit(seed ^ 0x5343414CL) * intensity * (extreme ? 3.5F : 2.4F);
        float scale = (float) Math.pow(2.0D, exponent);
        if (unit(seed ^ 0x46554C4CL) < 0.06F + intensity * 0.16F) {
            scale = unit(seed ^ 0x54494E59L) < 0.46F
                    ? 0.02F + unit(seed ^ 0x4D494E49L) * 0.18F
                    : 2.0F + intensity * (extreme ? 20.0F : 8.0F) * (0.25F + unit(seed ^ 0x48554745L) * 0.75F);
        }
        return Mth.clamp(scale, 0.02F, extreme ? 40.0F : 20.0F);
    }

    private static float colorScale(long seed, float intensity) {
        return Mth.clamp(1.0F + signedUnit(seed ^ 0x5343414CL) * intensity * 4.0F, -3.0F, 5.0F);
    }

    private static int particleBudget(float intensity) {
        return Math.round(Mth.lerp(Mth.clamp(intensity, 0.0F, 1.0F), MAX_PARTICLE_BUDGET, MIN_PARTICLE_BUDGET));
    }

    private record SharedParticleProfile(
            boolean active,
            long key,
            boolean extreme,
            int velocityMode,
            boolean mutateVelocity,
            double velocityScaleX,
            double velocityScaleY,
            double velocityScaleZ,
            Vec3 velocityAdd,
            double velocityMagnitude,
            double maxVelocity,
            boolean mutateGravity,
            boolean replaceGravity,
            float gravityReplacement,
            float gravityScale,
            float gravityAdd,
            Vec3 positionAdd,
            float widthScale,
            float heightScale,
            float quadScale,
            float redScale,
            float greenScale,
            float blueScale,
            float alphaScale,
            float redAdd,
            float greenAdd,
            float blueAdd,
            float alphaAdd,
            float rollAdd,
            float frictionScale,
            float frictionAdd,
            float lifetimeScale,
            int lifetimeAdd,
            boolean mutatePhysics,
            boolean hasPhysics,
            boolean mutateSpeedUpWhenBlocked,
            boolean speedUpWhenBlocked
    ) {
        private static SharedParticleProfile inactive() {
            return new SharedParticleProfile(
                    false,
                    0L,
                    false,
                    0,
                    false,
                    1.0D,
                    1.0D,
                    1.0D,
                    Vec3.ZERO,
                    0.0D,
                    0.0D,
                    false,
                    false,
                    0.0F,
                    1.0F,
                    0.0F,
                    Vec3.ZERO,
                    1.0F,
                    1.0F,
                    1.0F,
                    1.0F,
                    1.0F,
                    1.0F,
                    1.0F,
                    0.0F,
                    0.0F,
                    0.0F,
                    0.0F,
                    0.0F,
                    1.0F,
                    0.0F,
                    1.0F,
                    0,
                    false,
                    true,
                    false,
                    false
            );
        }

        private Vec3 mutateVelocity(Vec3 original) {
            if (!mutateVelocity || !finite(original)) {
                return original;
            }

            Vec3 velocity = switch (velocityMode) {
                case 0 -> new Vec3(original.z, original.y, -original.x);
                case 1 -> new Vec3(-original.x, -original.y * Math.abs(velocityScaleY), -original.z);
                case 2 -> original.add(velocityAdd);
                case 3 -> new Vec3(original.x * velocityScaleX, original.y * velocityScaleY, original.z * velocityScaleZ);
                case 4 -> {
                    Vec3 direction = original.lengthSqr() > 1.0E-8D ? original.normalize() : new Vec3(0.0D, 1.0D, 0.0D);
                    yield direction.scale(velocityMagnitude);
                }
                case 5 -> new Vec3(original.y, original.z, original.x).scale(Math.max(0.05D, Math.abs(velocityScaleX)));
                case 6 -> original.scale(unit(Double.doubleToLongBits(velocityMagnitude) ^ 0x53544F50L) < 0.50F ? 0.0D : -1.0D - Math.abs(velocityScaleZ));
                default -> original;
            };
            if (velocityMode != 2) {
                velocity = velocity.add(velocityAdd.scale(0.35D));
            }
            return clampVector(velocity, maxVelocity);
        }

        private Vec3 mutatePosition(Vec3 original) {
            if (!finite(original) || positionAdd.equals(Vec3.ZERO)) {
                return original;
            }
            return original.add(positionAdd);
        }

        private float mutateGravity(float original) {
            if (!mutateGravity || !Float.isFinite(original)) {
                return original;
            }
            if (replaceGravity) {
                return Mth.clamp(gravityReplacement, -32.0F, 32.0F);
            }
            return Mth.clamp(original * gravityScale + gravityAdd, -32.0F, 32.0F);
        }

        private float mutateSize(float original, float scale) {
            if (!Float.isFinite(original) || scale == 1.0F) {
                return original;
            }
            float safeOriginal = original > 0.0F ? original : 0.02F;
            return Mth.clamp(safeOriginal * scale, 0.001F, extreme ? 6.0F : 3.0F);
        }

        private float mutateColor(float original, float scale, float add, float min, float max) {
            if (!Float.isFinite(original) || (scale == 1.0F && add == 0.0F)) {
                return original;
            }
            return Mth.clamp(original * scale + add, min, max);
        }

        private int mutateLifetime(Particle particle, ParticleState original) {
            if (lifetimeScale == 1.0F && lifetimeAdd == 0) {
                return original.lifetime();
            }
            int normalLifetime = normalLifetime(particle, original.lifetime());
            int maxLifetime = Math.max(1, normalLifetime * 2);
            int lifetime = Math.round(original.lifetime() * lifetimeScale + lifetimeAdd);
            return Mth.clamp(lifetime, 1, maxLifetime);
        }
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

    private record ParticleClassInfo(int classNameHash, String budgetTarget) {
        private static ParticleClassInfo create(String className) {
            return new ParticleClassInfo(
                    className.hashCode(),
                    "particle:budget:" + className
            );
        }
    }
}
