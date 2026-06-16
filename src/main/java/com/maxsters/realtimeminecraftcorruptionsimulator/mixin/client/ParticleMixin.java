package com.maxsters.realtimeminecraftcorruptionsimulator.mixin.client;

import com.maxsters.realtimeminecraftcorruptionsimulator.client.hooks.AnimationSpeedCorruptionHooks;
import com.maxsters.realtimeminecraftcorruptionsimulator.client.hooks.ParticleCorruptionHooks;
import net.minecraft.client.particle.Particle;
import net.minecraft.client.particle.SingleQuadParticle;
import net.minecraft.world.phys.Vec3;
import org.spongepowered.asm.mixin.Dynamic;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.lang.reflect.Field;
import java.lang.reflect.Method;

@Mixin(Particle.class)
@SuppressWarnings("target")
public abstract class ParticleMixin {
    @Unique
    private static final int RMC$X = 0;
    @Unique
    private static final int RMC$Y = 1;
    @Unique
    private static final int RMC$Z = 2;
    @Unique
    private static final int RMC$XD = 3;
    @Unique
    private static final int RMC$YD = 4;
    @Unique
    private static final int RMC$ZD = 5;
    @Unique
    private static final int RMC$GRAVITY = 6;
    @Unique
    private static final int RMC$WIDTH = 7;
    @Unique
    private static final int RMC$HEIGHT = 8;
    @Unique
    private static final int RMC$RED = 9;
    @Unique
    private static final int RMC$GREEN = 10;
    @Unique
    private static final int RMC$BLUE = 11;
    @Unique
    private static final int RMC$ALPHA = 12;
    @Unique
    private static final int RMC$ROLL = 13;
    @Unique
    private static final int RMC$FRICTION = 14;
    @Unique
    private static final int RMC$LIFETIME = 15;
    @Unique
    private static final int RMC$HAS_PHYSICS = 16;
    @Unique
    private static final int RMC$SPEED_UP_WHEN_BLOCKED = 17;
    @Unique
    private static final int RMC$AGE = 18;

    @Unique
    private static final Field[] RMC$PARTICLE_FIELDS = new Field[19];
    @Unique
    private static final boolean[] RMC$PARTICLE_FIELDS_CHECKED = new boolean[19];
    @Unique
    private static Field rmc$quadSizeField;
    @Unique
    private static boolean rmc$quadSizeFieldChecked;
    @Unique
    private static Method rmc$setSizeMethod;
    @Unique
    private static boolean rmc$setSizeMethodChecked;
    @Unique
    private static Method rmc$setAlphaMethod;
    @Unique
    private static boolean rmc$setAlphaMethodChecked;

    @Inject(
            method = {
                    "tick()V",
                    "m_5989_()V"
            },
            at = @At("HEAD"),
            remap = false,
            require = 0
    )
    @Dynamic("Targets both mapped dev names and SRG runtime aliases for Particle#tick.")
    private void rmc$corruptParticleGravity(CallbackInfo callback) {
        Particle particle = (Particle) (Object) this;
        if (!ParticleCorruptionHooks.shouldProcessParticle(particle)) {
            return;
        }
        ParticleCorruptionHooks.ParticleState state = rmc$particleState(particle);
        if (state == null) {
            return;
        }
        ParticleCorruptionHooks.ParticleState mutated = ParticleCorruptionHooks.mutateParticle(particle, state);
        if (mutated == state || mutated.equals(state)) {
            return;
        }
        rmc$applyParticleState(particle, state, mutated);
    }

    @Inject(
            method = {
                    "tick()V",
                    "m_5989_()V"
            },
            at = @At("RETURN"),
            remap = false,
            require = 0
    )
    @Dynamic("Targets both mapped dev names and SRG runtime aliases for Particle#tick.")
    private void rmc$corruptParticleAnimationAge(CallbackInfo callback) {
        Particle particle = (Particle) (Object) this;
        Integer age = rmc$intField(particle, RMC$AGE, "age", "f_107224_");
        Integer lifetime = rmc$intField(particle, RMC$LIFETIME, "lifetime", "f_107225_");
        if (age == null || lifetime == null) {
            return;
        }
        int mutated = AnimationSpeedCorruptionHooks.mutateParticleAge(particle, age, lifetime);
        if (mutated != age) {
            rmc$setIntField(particle, RMC$AGE, "age", "f_107224_", mutated);
        }
    }

    @Unique
    private static ParticleCorruptionHooks.ParticleState rmc$particleState(Particle particle) {
        Double x = rmc$doubleField(particle, RMC$X, "x", "f_107212_");
        Double y = rmc$doubleField(particle, RMC$Y, "y", "f_107213_");
        Double z = rmc$doubleField(particle, RMC$Z, "z", "f_107214_");
        Double xd = rmc$doubleField(particle, RMC$XD, "xd", "f_107215_");
        Double yd = rmc$doubleField(particle, RMC$YD, "yd", "f_107216_");
        Double zd = rmc$doubleField(particle, RMC$ZD, "zd", "f_107217_");
        Float gravity = rmc$floatField(particle, RMC$GRAVITY, "gravity", "f_107226_");
        Float width = rmc$floatField(particle, RMC$WIDTH, "bbWidth", "f_107221_");
        Float height = rmc$floatField(particle, RMC$HEIGHT, "bbHeight", "f_107222_");
        Float red = rmc$floatField(particle, RMC$RED, "rCol", "f_107227_");
        Float green = rmc$floatField(particle, RMC$GREEN, "gCol", "f_107228_");
        Float blue = rmc$floatField(particle, RMC$BLUE, "bCol", "f_107229_");
        Float alpha = rmc$floatField(particle, RMC$ALPHA, "alpha", "f_107230_");
        Float roll = rmc$floatField(particle, RMC$ROLL, "roll", "f_107231_");
        Float friction = rmc$floatField(particle, RMC$FRICTION, "friction", "f_172258_");
        Integer lifetime = rmc$intField(particle, RMC$LIFETIME, "lifetime", "f_107225_");
        Boolean hasPhysics = rmc$booleanField(particle, RMC$HAS_PHYSICS, "hasPhysics", "f_107219_");
        Boolean speedUpWhenBlocked = rmc$booleanField(particle, RMC$SPEED_UP_WHEN_BLOCKED, "speedUpWhenYMotionIsBlocked", "f_172259_");
        if (x == null || y == null || z == null || xd == null || yd == null || zd == null || gravity == null
                || width == null || height == null || red == null || green == null || blue == null || alpha == null
                || roll == null || friction == null || lifetime == null || hasPhysics == null || speedUpWhenBlocked == null) {
            return null;
        }

        return new ParticleCorruptionHooks.ParticleState(
                new Vec3(x, y, z),
                new Vec3(xd, yd, zd),
                gravity,
                width,
                height,
                rmc$quadSize(particle),
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

    @Unique
    private static void rmc$applyParticleState(Particle particle, ParticleCorruptionHooks.ParticleState original, ParticleCorruptionHooks.ParticleState state) {
        if (state == null) {
            return;
        }

        Vec3 velocity = state.velocity();
        if (rmc$finite(velocity) && !rmc$sameVector(velocity, original.velocity())) {
            particle.setParticleSpeed(velocity.x, velocity.y, velocity.z);
        }
        if (rmc$changed(state.gravity(), original.gravity())) {
            rmc$setFloatField(particle, RMC$GRAVITY, "gravity", "f_107226_", state.gravity());
        }
        if (Float.isFinite(state.width()) && Float.isFinite(state.height())
                && (rmc$changed(state.width(), original.width()) || rmc$changed(state.height(), original.height()))) {
            rmc$setSize(particle, state.width(), state.height());
        }
        if (particle instanceof SingleQuadParticle && Float.isFinite(state.quadSize()) && rmc$changed(state.quadSize(), original.quadSize())) {
            rmc$setQuadSize(particle, state.quadSize());
        }
        if (Float.isFinite(state.red()) && Float.isFinite(state.green()) && Float.isFinite(state.blue())
                && (rmc$changed(state.red(), original.red()) || rmc$changed(state.green(), original.green()) || rmc$changed(state.blue(), original.blue()))) {
            particle.setColor(state.red(), state.green(), state.blue());
        }
        if (Float.isFinite(state.alpha()) && rmc$changed(state.alpha(), original.alpha())) {
            rmc$setAlpha(particle, state.alpha());
        }
        if (rmc$changed(state.roll(), original.roll())) {
            rmc$setFloatField(particle, RMC$ROLL, "roll", "f_107231_", state.roll());
        }
        if (rmc$changed(state.friction(), original.friction())) {
            rmc$setFloatField(particle, RMC$FRICTION, "friction", "f_172258_", state.friction());
        }
        if (state.lifetime() > 0 && state.lifetime() != original.lifetime()) {
            particle.setLifetime(state.lifetime());
        }
        if (state.hasPhysics() != original.hasPhysics()) {
            rmc$setBooleanField(particle, RMC$HAS_PHYSICS, "hasPhysics", "f_107219_", state.hasPhysics());
        }
        if (state.speedUpWhenBlocked() != original.speedUpWhenBlocked()) {
            rmc$setBooleanField(particle, RMC$SPEED_UP_WHEN_BLOCKED, "speedUpWhenYMotionIsBlocked", "f_172259_", state.speedUpWhenBlocked());
        }
        Vec3 position = state.position();
        if (rmc$finite(position) && !rmc$sameVector(position, original.position())) {
            particle.setPos(position.x, position.y, position.z);
        }
    }

    @Unique
    private static Double rmc$doubleField(Particle particle, int index, String mappedName, String srgName) {
        Field field = rmc$particleField(index, mappedName, srgName);
        if (field == null) {
            return null;
        }
        try {
            return field.getDouble(particle);
        } catch (IllegalAccessException | RuntimeException ignored) {
            return null;
        }
    }

    @Unique
    private static Float rmc$floatField(Particle particle, int index, String mappedName, String srgName) {
        Field field = rmc$particleField(index, mappedName, srgName);
        if (field == null) {
            return null;
        }
        try {
            return field.getFloat(particle);
        } catch (IllegalAccessException | RuntimeException ignored) {
            return null;
        }
    }

    @Unique
    private static Integer rmc$intField(Particle particle, int index, String mappedName, String srgName) {
        Field field = rmc$particleField(index, mappedName, srgName);
        if (field == null) {
            return null;
        }
        try {
            return field.getInt(particle);
        } catch (IllegalAccessException | RuntimeException ignored) {
            return null;
        }
    }

    @Unique
    private static Boolean rmc$booleanField(Particle particle, int index, String mappedName, String srgName) {
        Field field = rmc$particleField(index, mappedName, srgName);
        if (field == null) {
            return null;
        }
        try {
            return field.getBoolean(particle);
        } catch (IllegalAccessException | RuntimeException ignored) {
            return null;
        }
    }

    @Unique
    private static void rmc$setFloatField(Particle particle, int index, String mappedName, String srgName, float value) {
        if (!Float.isFinite(value)) {
            return;
        }
        Field field = rmc$particleField(index, mappedName, srgName);
        if (field == null) {
            return;
        }
        try {
            field.setFloat(particle, value);
        } catch (IllegalAccessException | RuntimeException ignored) {
        }
    }

    @Unique
    private static void rmc$setIntField(Particle particle, int index, String mappedName, String srgName, int value) {
        Field field = rmc$particleField(index, mappedName, srgName);
        if (field == null) {
            return;
        }
        try {
            field.setInt(particle, value);
        } catch (IllegalAccessException | RuntimeException ignored) {
        }
    }

    @Unique
    private static void rmc$setBooleanField(Particle particle, int index, String mappedName, String srgName, boolean value) {
        Field field = rmc$particleField(index, mappedName, srgName);
        if (field == null) {
            return;
        }
        try {
            field.setBoolean(particle, value);
        } catch (IllegalAccessException | RuntimeException ignored) {
        }
    }

    @Unique
    private static Field rmc$particleField(int index, String mappedName, String srgName) {
        if (!RMC$PARTICLE_FIELDS_CHECKED[index]) {
            RMC$PARTICLE_FIELDS_CHECKED[index] = true;
            RMC$PARTICLE_FIELDS[index] = rmc$findField(Particle.class, mappedName, srgName);
        }
        return RMC$PARTICLE_FIELDS[index];
    }

    @Unique
    private static float rmc$quadSize(Particle particle) {
        Field field = rmc$quadSizeField();
        if (field == null) {
            return Float.NaN;
        }
        try {
            return field.getFloat(particle);
        } catch (IllegalAccessException | RuntimeException ignored) {
            return Float.NaN;
        }
    }

    @Unique
    private static void rmc$setQuadSize(Particle particle, float value) {
        Field field = rmc$quadSizeField();
        if (field == null) {
            return;
        }
        try {
            field.setFloat(particle, value);
        } catch (IllegalAccessException | RuntimeException ignored) {
        }
    }

    @Unique
    private static Field rmc$quadSizeField() {
        if (!rmc$quadSizeFieldChecked) {
            rmc$quadSizeFieldChecked = true;
            rmc$quadSizeField = rmc$findField(SingleQuadParticle.class, "quadSize", "f_107663_");
        }
        return rmc$quadSizeField;
    }

    @Unique
    private static void rmc$setSize(Particle particle, float width, float height) {
        Method method = rmc$setSizeMethod();
        if (method == null) {
            return;
        }
        try {
            method.invoke(particle, width, height);
        } catch (ReflectiveOperationException | RuntimeException ignored) {
        }
    }

    @Unique
    private static Method rmc$setSizeMethod() {
        if (!rmc$setSizeMethodChecked) {
            rmc$setSizeMethodChecked = true;
            rmc$setSizeMethod = rmc$findMethod(Particle.class, new String[]{"setSize", "m_107250_"}, float.class, float.class);
        }
        return rmc$setSizeMethod;
    }

    @Unique
    private static void rmc$setAlpha(Particle particle, float alpha) {
        Method method = rmc$setAlphaMethod();
        if (method == null) {
            return;
        }
        try {
            method.invoke(particle, alpha);
        } catch (ReflectiveOperationException | RuntimeException ignored) {
        }
    }

    @Unique
    private static Method rmc$setAlphaMethod() {
        if (!rmc$setAlphaMethodChecked) {
            rmc$setAlphaMethodChecked = true;
            rmc$setAlphaMethod = rmc$findMethod(Particle.class, new String[]{"setAlpha", "m_107271_"}, float.class);
        }
        return rmc$setAlphaMethod;
    }

    @Unique
    private static Field rmc$findField(Class<?> owner, String mappedName, String srgName) {
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

    @Unique
    private static Method rmc$findMethod(Class<?> owner, String[] names, Class<?>... parameterTypes) {
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

    @Unique
    private static boolean rmc$finite(Vec3 vector) {
        return vector != null
                && Double.isFinite(vector.x)
                && Double.isFinite(vector.y)
                && Double.isFinite(vector.z);
    }

    @Unique
    private static boolean rmc$changed(float left, float right) {
        return Float.compare(left, right) != 0 && Float.isFinite(left);
    }

    @Unique
    private static boolean rmc$sameVector(Vec3 left, Vec3 right) {
        return left == right || (left != null
                && right != null
                && Double.compare(left.x, right.x) == 0
                && Double.compare(left.y, right.y) == 0
                && Double.compare(left.z, right.z) == 0);
    }
}
