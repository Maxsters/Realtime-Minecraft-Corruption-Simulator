package com.maxsters.realtimeminecraftcorruptionsimulator.client.hooks;

import com.maxsters.realtimeminecraftcorruptionsimulator.client.effects.ClientCorruptionEffects;
import com.maxsters.realtimeminecraftcorruptionsimulator.profile.CorruptionEffectStack;
import com.maxsters.realtimeminecraftcorruptionsimulator.profile.CorruptionSurface;
import com.maxsters.realtimeminecraftcorruptionsimulator.profile.CorruptionValueMutator;
import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.vehicle.Boat;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.api.distmarker.OnlyIn;
import net.minecraftforge.registries.ForgeRegistries;
import org.joml.AxisAngle4f;
import org.joml.Quaternionf;

import java.util.ArrayDeque;
import java.util.Deque;

@OnlyIn(Dist.CLIENT)
public final class ModelRenderCorruptionHooks {
    private static final ThreadLocal<Deque<RenderContext>> CONTEXT_STACK = ThreadLocal.withInitial(ArrayDeque::new);

    private ModelRenderCorruptionHooks() {
    }

    public static void beginEntityRender(LivingEntity entity, float partialTick) {
        CorruptionEffectStack stack = ClientCorruptionEffects.current();
        RenderContext context = RenderContext.inactive();
        if (entity != null && modelMutationActive(stack)) {
            String targetId = entityTargetId(entity);
            context = new RenderContext(entity.getId(), entity.tickCount + partialTick, targetId, stack);
        }
        CONTEXT_STACK.get().push(context);
    }

    public static void endEntityRender() {
        Deque<RenderContext> stack = CONTEXT_STACK.get();
        if (!stack.isEmpty()) {
            stack.pop();
        }
        if (stack.isEmpty()) {
            CONTEXT_STACK.remove();
        }
    }

    public static float[] mutatePrepareArgs(Entity entity, float limbSwing, float limbSwingAmount, float partialTick) {
        CorruptionEffectStack stack = ClientCorruptionEffects.current();
        String targetId = animationTargetId(entity, "prepare");
        if (!stack.activeOrExtreme(CorruptionSurface.ANIMATION_TIMING) && !stack.activeOrExtreme(CorruptionSurface.ANIMATION_TIMING, targetId)) {
            return new float[]{limbSwing, limbSwingAmount, partialTick};
        }

        long clock = animationClock(stack, entity, targetId);
        float intensity = Math.max(stack.targetIntensity(CorruptionSurface.ANIMATION_TIMING, targetId), stack.intensity(CorruptionSurface.ANIMATION_TIMING) * 0.92F);

        return new float[]{
                CorruptionValueMutator.mutateScalar(stack, CorruptionSurface.ANIMATION_TIMING, targetId + ":limb_swing", limbSwing, 5.0F + intensity * 84.0F, -16384.0F, 16384.0F, 0x31, clock),
                CorruptionValueMutator.mutateScalar(stack, CorruptionSurface.ANIMATION_TIMING, targetId + ":limb_amount", limbSwingAmount, 0.75F + intensity * 7.50F, -8.0F, 12.0F, 0x47, clock),
                CorruptionValueMutator.mutateScalar(stack, CorruptionSurface.ANIMATION_TIMING, targetId + ":partial", partialTick, 0.55F + intensity * 12.0F, -24.0F, 24.0F, 0x5D, clock)
        };
    }

    public static float[] mutateSetupAnimArgs(Entity entity, float limbSwing, float limbSwingAmount, float ageInTicks, float netHeadYaw, float headPitch) {
        CorruptionEffectStack stack = ClientCorruptionEffects.current();
        String targetId = animationTargetId(entity, "setup");
        if (!stack.activeOrExtreme(CorruptionSurface.ANIMATION_TIMING) && !stack.activeOrExtreme(CorruptionSurface.ANIMATION_TIMING, targetId)) {
            return new float[]{limbSwing, limbSwingAmount, ageInTicks, netHeadYaw, headPitch};
        }

        long clock = animationClock(stack, entity, targetId);
        float intensity = Math.max(stack.targetIntensity(CorruptionSurface.ANIMATION_TIMING, targetId), stack.intensity(CorruptionSurface.ANIMATION_TIMING) * 0.96F);

        return new float[]{
                CorruptionValueMutator.mutateScalar(stack, CorruptionSurface.ANIMATION_TIMING, targetId + ":limb_swing", limbSwing, 7.0F + intensity * 112.0F, -16384.0F, 16384.0F, 0x61, clock),
                CorruptionValueMutator.mutateScalar(stack, CorruptionSurface.ANIMATION_TIMING, targetId + ":limb_amount", limbSwingAmount, 0.85F + intensity * 8.80F, -10.0F, 14.0F, 0x72, clock),
                CorruptionValueMutator.mutateScalar(stack, CorruptionSurface.ANIMATION_TIMING, targetId + ":age", ageInTicks, 24.0F + intensity * 360.0F, -8192.0F, 16384.0F, 0x83, clock),
                CorruptionValueMutator.mutateScalar(stack, CorruptionSurface.ANIMATION_TIMING, targetId + ":head_yaw", netHeadYaw, 45.0F + intensity * 520.0F, -1440.0F, 1440.0F, 0x94, clock),
                CorruptionValueMutator.mutateScalar(stack, CorruptionSurface.ANIMATION_TIMING, targetId + ":head_pitch", headPitch, 45.0F + intensity * 440.0F, -1440.0F, 1440.0F, 0xA5, clock)
        };
    }

    public static float[] mutateBoatSetupAnimArgs(Boat boat, float limbSwing, float limbSwingAmount, float ageInTicks, float netHeadYaw, float headPitch) {
        CorruptionEffectStack stack = ClientCorruptionEffects.current();
        String targetId = animationTargetId(boat, "boat_rowing");
        if (boat == null || (!stack.activeOrExtreme(CorruptionSurface.ANIMATION_TIMING) && !stack.activeOrExtreme(CorruptionSurface.ANIMATION_TIMING, targetId))) {
            return new float[]{limbSwing, limbSwingAmount, ageInTicks, netHeadYaw, headPitch};
        }

        long clock = animationClock(stack, boat, targetId);
        float intensity = Math.max(stack.targetIntensity(CorruptionSurface.ANIMATION_TIMING, targetId), stack.intensity(CorruptionSurface.ANIMATION_TIMING));
        float rowScale = Mth.clamp(1.0F + signedUnit(clock ^ 0x524F575343414C45L) * intensity * 9.0F, -8.0F, 10.0F);
        float rowBias = signedUnit(clock ^ 0x524F5742494153L) * intensity * 42.0F;
        float ageLeak = ageInTicks * signedUnit(clock ^ 0x4147454C45414BL) * intensity * 1.45F;
        float time = limbSwing * rowScale + rowBias + ageLeak;
        if (unit(clock ^ 0x524F5750524543L) < 0.18F + intensity * 0.42F) {
            float step = Math.max(0.0625F, 0.25F + unit(clock ^ 0x53544550L) * intensity * 8.0F);
            time = Math.round(time / step) * step;
        }
        time = CorruptionValueMutator.mutateScalar(stack, CorruptionSurface.ANIMATION_TIMING, targetId + ":row", time, 8.0F + intensity * 128.0F, -2048.0F, 2048.0F, 0x52, clock);
        return new float[]{
                time,
                CorruptionValueMutator.mutateScalar(stack, CorruptionSurface.ANIMATION_TIMING, targetId + ":limb_amount", limbSwingAmount, 1.0F + intensity * 6.0F, -8.0F, 10.0F, 0x41, clock),
                CorruptionValueMutator.mutateScalar(stack, CorruptionSurface.ANIMATION_TIMING, targetId + ":age", ageInTicks, 1.0F + intensity * 16.0F, -48.0F, 48.0F, 0x47, clock),
                CorruptionValueMutator.mutateScalar(stack, CorruptionSurface.ANIMATION_TIMING, targetId + ":yaw", netHeadYaw, 12.0F + intensity * 180.0F, -720.0F, 720.0F, 0x59, clock),
                CorruptionValueMutator.mutateScalar(stack, CorruptionSurface.ANIMATION_TIMING, targetId + ":pitch", headPitch, 12.0F + intensity * 180.0F, -720.0F, 720.0F, 0x50, clock)
        };
    }

    public static void mutateNonLivingEntityTransform(Entity entity, float partialTick, PoseStack poseStack) {
        CorruptionEffectStack stack = ClientCorruptionEffects.current();
        if (entity == null || entity instanceof LivingEntity || poseStack == null || !stack.activeOrExtreme(CorruptionSurface.ANIMATION_TIMING)) {
            return;
        }

        String targetId = animationTargetId(entity, "entity_render");
        float intensity = Math.max(stack.targetIntensity(CorruptionSurface.ANIMATION_TIMING, targetId), stack.intensity(CorruptionSurface.ANIMATION_TIMING) * 0.86F);
        if (intensity <= 0.0F) {
            return;
        }

        long clock = animationClock(stack, entity, targetId) ^ Float.floatToIntBits(partialTick);
        float phase = entity.tickCount + partialTick + unit(clock) * 40.0F;
        double wave = Math.sin(phase * (0.20D + intensity * 1.30D));
        poseStack.translate(
                signedUnit(clock ^ 0x58454E54L) * intensity * 0.18D + wave * intensity * 0.10D,
                signedUnit(clock ^ 0x59454E54L) * intensity * 0.14D,
                signedUnit(clock ^ 0x5A454E54L) * intensity * 0.18D - wave * intensity * 0.10D
        );
        float scaleX = Mth.clamp(1.0F + signedUnit(clock ^ 0x53584E54L) * intensity * 0.72F, 0.18F, 2.4F);
        float scaleY = Mth.clamp(1.0F + signedUnit(clock ^ 0x53594E54L) * intensity * 0.64F, 0.18F, 2.4F);
        float scaleZ = Mth.clamp(1.0F + signedUnit(clock ^ 0x535A4E54L) * intensity * 0.72F, 0.18F, 2.4F);
        if (stack.extreme(CorruptionSurface.ANIMATION_TIMING) && unit(clock ^ 0x494E56454EL) < 0.26F) {
            scaleX = -scaleX;
        }
        poseStack.scale(scaleX, scaleY, scaleZ);
        rotate(poseStack, clock ^ 0x52454E54495459L, 0.45F + intensity * 2.6F, intensity);
    }

    public static boolean beginShadowRender(PoseStack poseStack, Entity entity, float partialTick, float radius) {
        CorruptionEffectStack stack = ClientCorruptionEffects.current();
        if (poseStack == null || entity == null || !stack.activeOrExtreme(CorruptionSurface.WORLD_RENDER)) {
            return false;
        }

        String targetId = animationTargetId(entity, "shadow");
        float intensity = stack.extreme(CorruptionSurface.WORLD_RENDER)
                ? 1.0F
                : Math.max(stack.targetIntensity(CorruptionSurface.WORLD_RENDER, targetId), stack.intensity(CorruptionSurface.WORLD_RENDER) * 0.78F);
        if (intensity <= 0.015F) {
            return false;
        }
        long clock = animationClock(stack, entity, targetId) ^ Float.floatToIntBits(partialTick) ^ Float.floatToIntBits(radius);
        float chance = Mth.clamp(0.08F + intensity * 0.78F + stack.instability() * 0.10F, 0.0F, 0.94F);
        if (stack.unit(CorruptionSurface.WORLD_RENDER, targetId, entity.getId() ^ 0x53484457) > chance) {
            return false;
        }

        poseStack.pushPose();
        double height = Math.max(0.05D, entity.getBbHeight());
        double width = Math.max(0.05D, entity.getBbWidth());
        poseStack.translate(
                signedUnit(clock ^ 0x5853484457L) * width * (0.10D + intensity * 1.35D),
                height * (0.18D + unit(clock ^ 0x5953484457L) * intensity * 0.72D),
                signedUnit(clock ^ 0x5A53484457L) * width * (0.10D + intensity * 1.35D)
        );
        if (unit(clock ^ 0x5343484CL) < 0.28F + intensity * 0.42F) {
            float scale = Mth.clamp(0.25F + unit(clock ^ 0x534853434CL) * (1.0F + intensity * 4.0F), 0.05F, 5.5F);
            poseStack.scale(scale, scale, scale);
        }
        return true;
    }

    public static void endShadowRender(PoseStack poseStack, boolean applied) {
        if (applied && poseStack != null) {
            poseStack.popPose();
        }
    }

    public static void mutateModelPartTransform(ModelPart part, PoseStack poseStack) {
        RenderContext context = currentContext();
        if (context == null || !context.active() || part == null || poseStack == null) {
            return;
        }

        CorruptionEffectStack stack = context.stack();
        int ordinal = context.nextPartOrdinal();
        String geometryTargetId = context.geometryTargetId() + ":part:" + ordinal;
        String animationTargetId = context.animationTargetId() + ":part:" + ordinal;
        float geometryIntensity = partGeometryIntensity(stack, geometryTargetId);
        float animationIntensity = partAnimationIntensity(stack, animationTargetId);
        if (geometryIntensity <= 0.0F && animationIntensity <= 0.0F) {
            return;
        }

        long geometryClock = stack.stableLong(CorruptionSurface.MODEL_GEOMETRY, geometryTargetId, 0x50415254) ^ (long) ordinal * 0x9E3779B97F4A7C15L;
        long animationClock = ((long) Math.floor(context.renderTime() * (1.0F + animationIntensity * 5.0F)) << 32)
                ^ stack.stableLong(CorruptionSurface.ANIMATION_TIMING, animationTargetId, 0x414E494D);

        if (geometryIntensity > 0.0F) {
            double offsetSpan = (0.025D + geometryIntensity * 0.58D) * (stack.extreme(CorruptionSurface.MODEL_GEOMETRY) ? 4.40D : 1.0D);
            double offsetClamp = stack.extreme(CorruptionSurface.MODEL_GEOMETRY) ? 3.25D : 1.35D;
            double x = CorruptionValueMutator.mutateScalar(stack, CorruptionSurface.MODEL_GEOMETRY, geometryTargetId + ":offset_x", 0.0D, offsetSpan, -offsetClamp, offsetClamp, 0x11, geometryClock);
            double y = CorruptionValueMutator.mutateScalar(stack, CorruptionSurface.MODEL_GEOMETRY, geometryTargetId + ":offset_y", 0.0D, offsetSpan, -offsetClamp, offsetClamp, 0x23, geometryClock);
            double z = CorruptionValueMutator.mutateScalar(stack, CorruptionSurface.MODEL_GEOMETRY, geometryTargetId + ":offset_z", 0.0D, offsetSpan, -offsetClamp, offsetClamp, 0x35, geometryClock);
            poseStack.translate(x, y, z);

            float scaleSpan = 0.12F + geometryIntensity * (stack.extreme(CorruptionSurface.MODEL_GEOMETRY) ? 7.20F : 2.35F);
            float xScale = scaleMutation(stack, CorruptionSurface.MODEL_GEOMETRY, geometryTargetId + ":scale_x", scaleSpan, 0x58, geometryClock);
            float yScale = scaleMutation(stack, CorruptionSurface.MODEL_GEOMETRY, geometryTargetId + ":scale_y", scaleSpan, 0x59, geometryClock);
            float zScale = scaleMutation(stack, CorruptionSurface.MODEL_GEOMETRY, geometryTargetId + ":scale_z", scaleSpan, 0x5A, geometryClock);
            poseStack.scale(xScale, yScale, zScale);
        }

        if (geometryIntensity > 0.0F || animationIntensity > 0.0F) {
            float rotationSpan = 0.10F + geometryIntensity * 1.12F + animationIntensity * 3.20F;
            boolean useAnimation = animationIntensity > geometryIntensity;
            long clock = useAnimation ? animationClock : geometryClock;
            CorruptionSurface rotationSurface = useAnimation ? CorruptionSurface.ANIMATION_TIMING : CorruptionSurface.MODEL_GEOMETRY;
            String rotationTargetId = useAnimation ? animationTargetId : geometryTargetId;
            float angle = CorruptionValueMutator.mutateScalar(stack, rotationSurface, rotationTargetId + ":angle", 0.0F, rotationSpan, -3.35F, 3.35F, 0x41, clock);
            float x = signedUnit(clock ^ 0x58524F54415445L);
            float y = signedUnit(clock ^ 0x59524F54415445L);
            float z = signedUnit(clock ^ 0x5A524F54415445L);
            if (Math.abs(x) + Math.abs(y) + Math.abs(z) < 0.001F) {
                y = 1.0F;
            }
            poseStack.mulPose(new Quaternionf(new AxisAngle4f(angle, x, y, z)));
        }
    }

    private static RenderContext currentContext() {
        Deque<RenderContext> stack = CONTEXT_STACK.get();
        return stack.isEmpty() ? null : stack.peek();
    }

    private static boolean modelMutationActive(CorruptionEffectStack stack) {
        return stack.activeOrExtreme(CorruptionSurface.MODEL_GEOMETRY)
                || stack.activeOrExtreme(CorruptionSurface.ANIMATION_TIMING);
    }

    private static float partGeometryIntensity(CorruptionEffectStack stack, String targetId) {
        if (stack.extreme(CorruptionSurface.MODEL_GEOMETRY)) {
            return 1.0F;
        }
        return Mth.clamp(Math.max(
                stack.targetIntensity(CorruptionSurface.MODEL_GEOMETRY, targetId),
                stack.intensity(CorruptionSurface.MODEL_GEOMETRY) * 0.76F
        ), 0.0F, 1.0F);
    }

    private static float partAnimationIntensity(CorruptionEffectStack stack, String targetId) {
        if (stack.extreme(CorruptionSurface.ANIMATION_TIMING)) {
            return 1.0F;
        }
        return Mth.clamp(Math.max(
                stack.targetIntensity(CorruptionSurface.ANIMATION_TIMING, targetId),
                stack.intensity(CorruptionSurface.ANIMATION_TIMING) * 1.0F
        ), 0.0F, 1.0F);
    }

    private static float scaleMutation(CorruptionEffectStack stack, CorruptionSurface surface, String targetId, float span, int salt, long clock) {
        float scale = CorruptionValueMutator.mutateScalar(stack, surface, targetId, 1.0F, span, -6.50F, 9.50F, salt, clock);
        if (Math.abs(scale) < 0.05F) {
            return Math.copySign(0.05F, scale == 0.0F ? 1.0F : scale);
        }
        return scale;
    }

    private static void rotate(PoseStack poseStack, long seed, float strength, float intensity) {
        float angle = signedUnit(seed >>> 31) * (2.0F + intensity * 18.0F) * strength;
        float x = signedUnit(seed >>> 7);
        float y = signedUnit(seed >>> 19);
        float z = signedUnit(seed >>> 43);
        if (Math.abs(x) + Math.abs(y) + Math.abs(z) < 0.001F) {
            y = 1.0F;
        }
        poseStack.mulPose(new Quaternionf(new AxisAngle4f(angle * ((float) Math.PI / 180.0F), x, y, z)));
    }

    private static long animationClock(CorruptionEffectStack stack, Entity entity, String targetId) {
        long gameClock = entity == null ? 0L : ((long) entity.tickCount << 24) ^ entity.getId() * 0x632BE59BD9B4E019L;
        return gameClock ^ stack.stableLong(CorruptionSurface.ANIMATION_TIMING, targetId, 0x434C4F43);
    }

    private static String animationTargetId(Entity entity, String phase) {
        return "animation:" + entityTargetId(entity) + ":" + phase;
    }

    private static String entityTargetId(Entity entity) {
        if (entity == null) {
            return "entity:unknown";
        }
        ResourceLocation location = ForgeRegistries.ENTITY_TYPES.getKey(entity.getType());
        return location == null ? entity.getType().toString() : location.toString();
    }

    private static float signedUnit(long value) {
        return unit(value) * 2.0F - 1.0F;
    }

    private static float unit(long value) {
        long mixed = mix(value);
        return ((mixed >>> 40) & 0xFF_FFFFL) / 16_777_215.0F;
    }

    private static long mix(long value) {
        value ^= value >>> 33;
        value *= 0xff51afd7ed558ccdL;
        value ^= value >>> 33;
        value *= 0xc4ceb9fe1a85ec53L;
        value ^= value >>> 33;
        return value;
    }

    private static final class RenderContext {
        private static final RenderContext INACTIVE = new RenderContext(0, 0.0F, "", CorruptionEffectStack.local(0));

        private final int entityId;
        private final float renderTime;
        private final String targetId;
        private final CorruptionEffectStack stack;
        private int partOrdinal;

        private RenderContext(int entityId, float renderTime, String targetId, CorruptionEffectStack stack) {
            this.entityId = entityId;
            this.renderTime = renderTime;
            this.targetId = targetId;
            this.stack = stack;
        }

        private static RenderContext inactive() {
            return INACTIVE;
        }

        private boolean active() {
            return stack.level() > 0;
        }

        private float renderTime() {
            return renderTime;
        }

        private String geometryTargetId() {
            return targetId;
        }

        private String animationTargetId() {
            return targetId + ":entity:" + entityId;
        }

        private CorruptionEffectStack stack() {
            return stack;
        }

        private int nextPartOrdinal() {
            return partOrdinal++;
        }
    }
}
