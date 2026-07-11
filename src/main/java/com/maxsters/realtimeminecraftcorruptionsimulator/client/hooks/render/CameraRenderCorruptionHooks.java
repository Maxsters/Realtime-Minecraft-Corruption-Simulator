package com.maxsters.realtimeminecraftcorruptionsimulator.client.hooks.render;

import com.maxsters.realtimeminecraftcorruptionsimulator.client.effects.ClientCorruptionEffects;
import com.maxsters.realtimeminecraftcorruptionsimulator.profile.CorruptionEffectStack;
import com.maxsters.realtimeminecraftcorruptionsimulator.profile.CorruptionSurface;
import net.minecraft.client.Camera;
import net.minecraft.client.Minecraft;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.api.distmarker.OnlyIn;
import net.minecraftforge.client.event.ViewportEvent;
import org.joml.Matrix4f;
import org.joml.Vector3f;

@OnlyIn(Dist.CLIENT)
public final class CameraRenderCorruptionHooks {
    private static final double MIN_SAFE_FOV = 8.0D;
    private static final double MAX_SAFE_FOV = 172.0D;
    private static final ThreadLocal<CameraProfile> VIEW_BOB_PROFILE = new ThreadLocal<>();

    private CameraRenderCorruptionHooks() {
    }

    public static void beginViewBob() {
        CameraProfile profile = currentWorldProfile();
        if (profile == null) {
            VIEW_BOB_PROFILE.remove();
            return;
        }
        VIEW_BOB_PROFILE.set(profile);
    }

    public static void endViewBob() {
        VIEW_BOB_PROFILE.remove();
    }

    public static float mutateBobAmplitude(float partialTick, float previousBob, float currentBob) {
        float source = Mth.lerp(partialTick, previousBob, currentBob);
        CameraProfile profile = VIEW_BOB_PROFILE.get();
        if (profile == null) {
            return source;
        }

        long seed = profile.seed() ^ 0x424F4241L;
        float intensity = profile.intensity();
        float blend = Mth.clamp(0.14F + intensity * 0.86F, 0.0F, 1.0F);
        float mutated;
        switch (Math.floorMod((int) (seed >>> 37), 8)) {
            case 0 -> {
                float scale = signedScale(seed ^ 0x5343414CL, intensity, 0.15F, 42.0F);
                mutated = source * scale;
            }
            case 1 -> {
                float scale = signedScale(seed ^ 0x424F4253L, intensity, 0.10F, 29.0F);
                float offset = signed(seed ^ 0x424F424FL, intensity * (0.28F + unit(seed ^ 0x424F4250L) * 3.2F));
                mutated = source * scale + offset;
            }
            case 2 -> {
                float scale = signedScale(seed ^ 0x5153434CL, intensity, 0.20F, 36.0F);
                float step = 0.03F + unit(seed ^ 0x51535450L) * (0.16F + intensity * 1.75F);
                mutated = quantize(source * scale, step);
            }
            case 3 -> {
                float width = 0.10F + unit(seed ^ 0x464F4C44L) * (0.42F + intensity * 3.8F);
                mutated = fold(source * signedScale(seed ^ 0x464F4C53L, intensity, 0.30F, 38.0F), width);
                if (unit(seed ^ 0x464C4950L) < 0.5F) {
                    mutated = -mutated;
                }
            }
            case 4 -> {
                float target = signed(seed ^ 0x53544154L, intensity * (0.25F + unit(seed ^ 0x53544142L) * 4.5F));
                mutated = Mth.lerp(blend, source, target);
            }
            case 5 -> {
                float scale = 1.0F + signed(seed ^ 0x534D4541L, intensity * (5.0F + unit(seed ^ 0x534D4542L) * 34.0F));
                mutated = source * scale + signed(seed ^ 0x534D4543L, intensity * 1.6F);
            }
            case 6 -> {
                float gate = unit(seed ^ 0x47415445L) < 0.5F ? -1.0F : 1.0F;
                float scale = gate * (0.55F + intensity * (3.0F + unit(seed ^ 0x47415453L) * 35.0F));
                mutated = quantize(source * scale, 0.04F + intensity * 0.65F);
            }
            default -> {
                float scale = signedScale(seed ^ 0x45585452L, intensity, 0.12F, 46.0F);
                mutated = source * scale + signed(seed ^ 0x4558544FL, intensity * 2.4F);
            }
        }
        return sanitizeBob(Mth.lerp(blend, source, mutated));
    }

    public static float mutateBobSine(float radians) {
        return Mth.sin(mutateBobPhase(radians));
    }

    public static float mutateBobCosine(float radians) {
        return Mth.cos(mutateBobPhase(radians));
    }

    public static void mutateCameraAngles(ViewportEvent.ComputeCameraAngles event) {
        if (event == null) {
            return;
        }

        CameraProfile profile = currentWorldProfile();
        if (profile == null) {
            return;
        }

        long seed = profile.seed();
        float intensity = profile.intensity();
        float blend = Mth.clamp(0.18F + intensity * 0.82F, 0.0F, 1.0F);
        float yaw = event.getYaw();
        float pitch = event.getPitch();
        float roll = event.getRoll();
        float sourceYaw = yaw;
        float sourcePitch = pitch;

        switch (Math.floorMod((int) (seed >>> 29), 8)) {
            case 0 -> {
                yaw = Mth.wrapDegrees(yaw + signed(seed ^ 0x5941574FL, 8.0F + intensity * 118.0F));
                pitch += signed(seed ^ 0x50495443L, 5.0F + intensity * 62.0F);
                roll += signed(seed ^ 0x524F4C4CL, 8.0F + intensity * 92.0F);
            }
            case 1 -> {
                float step = 2.0F + unit(seed ^ 0x53544550L) * (8.0F + intensity * 52.0F);
                yaw = quantizeDegrees(yaw + signed(seed ^ 0x59534E50L, intensity * 34.0F), step);
                pitch = quantizeDegrees(pitch + signed(seed ^ 0x50534E50L, intensity * 22.0F), Math.max(1.0F, step * 0.65F));
                roll = quantizeDegrees(roll + signed(seed ^ 0x52534E50L, intensity * 58.0F), Math.max(1.0F, step));
            }
            case 2 -> {
                float targetYaw = Mth.wrapDegrees(180.0F - sourceYaw + signed(seed ^ 0x59494E56L, intensity * 72.0F));
                float targetPitch = -sourcePitch + signed(seed ^ 0x50494E56L, intensity * 46.0F);
                yaw = blendDegrees(sourceYaw, targetYaw, blend);
                pitch = Mth.lerp(blend, sourcePitch, targetPitch);
                roll += signed(seed ^ 0x52494E56L, intensity * 78.0F);
            }
            case 3 -> {
                float yawCoupling = signed(seed ^ 0x59434F50L, 0.22F + intensity * 1.10F);
                float pitchCoupling = signed(seed ^ 0x50434F50L, 0.08F + intensity * 0.54F);
                yaw = Mth.wrapDegrees(sourceYaw + sourcePitch * yawCoupling + signed(seed ^ 0x59434F46L, intensity * 48.0F));
                pitch = sourcePitch + Mth.wrapDegrees(sourceYaw) * pitchCoupling + signed(seed ^ 0x50434F46L, intensity * 26.0F);
                roll += signed(seed ^ 0x52434F46L, intensity * 56.0F);
            }
            case 4 -> {
                float yawScale = signedScale(seed ^ 0x5953434CL, intensity, 0.35F, 2.4F);
                float pitchScale = signedScale(seed ^ 0x5053434CL, intensity, 0.20F, 1.65F);
                yaw = Mth.wrapDegrees(sourceYaw * yawScale + signed(seed ^ 0x594F4646L, intensity * 64.0F));
                pitch = sourcePitch * pitchScale + signed(seed ^ 0x504F4646L, intensity * 42.0F);
                roll += signed(seed ^ 0x524F4646L, intensity * 82.0F);
            }
            case 5 -> {
                float anchorYaw = signed(seed ^ 0x59414E43L, 180.0F);
                float anchorPitch = signed(seed ^ 0x50414E43L, 74.0F);
                yaw = blendDegrees(sourceYaw, anchorYaw, 0.22F + intensity * 0.70F);
                pitch = Mth.lerp(0.18F + intensity * 0.62F, sourcePitch, anchorPitch);
                roll += signed(seed ^ 0x52414E43L, intensity * 104.0F);
            }
            case 6 -> {
                float step = 7.0F + unit(seed ^ 0x53545554L) * (14.0F + intensity * 66.0F);
                yaw = quantizeDegrees(sourceYaw, step);
                pitch = quantizeDegrees(sourcePitch, Math.max(2.0F, step * 0.5F));
                roll = quantizeDegrees(roll + signed(seed ^ 0x52535455L, intensity * 66.0F), step);
            }
            default -> {
                yaw = Mth.wrapDegrees(sourceYaw + signed(seed ^ 0x5946524DL, intensity * 136.0F));
                pitch = sourcePitch + signed(seed ^ 0x5046524DL, intensity * 76.0F);
                roll += signed(seed ^ 0x5246524DL, intensity * 124.0F);
            }
        }

        event.setYaw(Mth.wrapDegrees(yaw));
        event.setPitch(Mth.clamp(pitch, -89.9F, 89.9F));
        event.setRoll(Mth.clamp(roll, -150.0F, 150.0F));
    }

    public static void mutateFov(ViewportEvent.ComputeFov event) {
        if (event == null) {
            return;
        }

        CameraProfile profile = currentWorldProfile();
        if (profile == null) {
            return;
        }
        event.setFOV(mutateFov(profile, event.getFOV(), event.usedConfiguredFov()));
    }

    public static float mutateCameraEyeHeight(Camera camera, Entity entity, float vanillaEyeHeight) {
        CameraProfile profile = currentWorldProfile();
        if (profile == null || !Float.isFinite(vanillaEyeHeight)) {
            return vanillaEyeHeight;
        }

        long seed = profile.seed() ^ 0x45594548L;
        float intensity = profile.intensity();
        float blend = Mth.clamp(0.16F + intensity * 0.84F, 0.0F, 1.0F);
        float source = vanillaEyeHeight;
        float mutated;
        switch (Math.floorMod((int) (seed >>> 35), 7)) {
            case 0 -> mutated = source * signedScale(seed ^ 0x45595343L, intensity, 0.18F, 3.8F);
            case 1 -> mutated = source + signed(seed ^ 0x45594F46L, intensity * (0.35F + unit(seed ^ 0x45594F53L) * 4.6F));
            case 2 -> {
                float step = 0.05F + unit(seed ^ 0x45595354L) * (0.20F + intensity * 1.25F);
                mutated = quantize(source * signedScale(seed ^ 0x45595153L, intensity, 0.22F, 3.4F), step);
            }
            case 3 -> {
                float anchor = signed(seed ^ 0x4559414EL, intensity * (0.35F + unit(seed ^ 0x45594153L) * 3.8F));
                mutated = Mth.lerp(blend, source, anchor);
            }
            case 4 -> mutated = -source * (0.22F + intensity * (0.55F + unit(seed ^ 0x4559494EL) * 2.55F));
            case 5 -> mutated = fold(source * signedScale(seed ^ 0x4559464FL, intensity, 0.25F, 3.6F), 0.10F + intensity * 1.85F);
            default -> mutated = source * signedScale(seed ^ 0x45594558L, intensity, 0.14F, 4.2F)
                    + signed(seed ^ 0x4559454FL, intensity * 2.4F);
        }
        return sanitizeEyeHeight(Mth.lerp(blend, source, mutated));
    }

    public static Vec3 mutateCameraPosition(Camera camera, Entity entity, Vec3 original) {
        CameraProfile profile = currentWorldProfile();
        if (profile == null || camera == null || original == null) {
            return original;
        }

        long seed = profile.seed() ^ 0x504F5345L;
        float intensity = profile.intensity();
        double scale = camera.isDetached() ? 1.35D : 1.0D;
        if (entity != null && entity.isShiftKeyDown()) {
            scale *= 1.18D;
        }

        double forward;
        double upward;
        double lateral;
        switch (Math.floorMod((int) (seed >>> 43), 6)) {
            case 0 -> {
                forward = signed(seed ^ 0x504F5346L, intensity * 3.4D);
                upward = signed(seed ^ 0x504F5355L, intensity * 2.1D);
                lateral = signed(seed ^ 0x504F534CL, intensity * 3.4D);
            }
            case 1 -> {
                forward = signed(seed ^ 0x50494E46L, intensity * 4.4D);
                upward = signed(seed ^ 0x50494E55L, intensity * 0.95D);
                lateral = 0.0D;
            }
            case 2 -> {
                forward = 0.0D;
                upward = signed(seed ^ 0x5046554CL, intensity * 3.8D);
                lateral = signed(seed ^ 0x50464C54L, intensity * 4.2D);
            }
            case 3 -> {
                forward = signed(seed ^ 0x504F4646L, intensity * 1.8D);
                upward = signed(seed ^ 0x504F4655L, intensity * 3.9D);
                lateral = signed(seed ^ 0x504F464CL, intensity * 1.8D);
            }
            case 4 -> {
                double step = 0.0625D + unit(seed ^ 0x504F5354L) * (0.45D + intensity * 2.1D);
                return finitePosition(new Vec3(
                        quantize(original.x, step),
                        quantize(original.y, step),
                        quantize(original.z, step)
                ), original);
            }
            default -> {
                forward = signed(seed ^ 0x50455846L, intensity * 4.6D);
                upward = signed(seed ^ 0x50455855L, intensity * 4.4D);
                lateral = signed(seed ^ 0x5045584CL, intensity * 4.6D);
            }
        }

        Vector3f look = camera.getLookVector();
        Vector3f up = camera.getUpVector();
        Vector3f left = camera.getLeftVector();
        Vec3 mutated = original.add(
                (look.x * forward + up.x * upward + left.x * lateral) * scale,
                (look.y * forward + up.y * upward + left.y * lateral) * scale,
                (look.z * forward + up.z * upward + left.z * lateral) * scale
        );
        return finitePosition(mutated, original);
    }

    public static float mutateMenuPitch(float original) {
        return mutateMenuAngle(original, 0x50495443, 0.92F);
    }

    public static float mutateMenuYaw(float original) {
        return mutateMenuAngle(original, 0x59415721, 1.0F);
    }

    public static Matrix4f setMenuPerspective(Matrix4f matrix, float fovRadians, float aspect, float nearPlane, float farPlane) {
        CameraProfile profile = menuProfile();
        double sourceDegrees = Double.isFinite(fovRadians) ? Math.toDegrees(fovRadians) : 85.0D;
        double fovDegrees = profile == null ? sanitizeFov(sourceDegrees) : mutateFov(profile, sourceDegrees, true);
        float safeAspect = Float.isFinite(aspect) ? Mth.clamp(aspect, 0.25F, 8.0F) : 1.0F;
        float safeNear = Float.isFinite(nearPlane) ? Mth.clamp(nearPlane, 0.001F, 4.0F) : 0.05F;
        float safeFar = Float.isFinite(farPlane) ? Mth.clamp(farPlane, safeNear + 0.01F, 2048.0F) : 10.0F;
        return matrix.setPerspective((float) Math.toRadians(fovDegrees), safeAspect, safeNear, safeFar);
    }

    public static boolean cameraReady(Minecraft minecraft) {
        return minecraft != null
                && minecraft.level != null
                && minecraft.player != null
                && minecraft.player.tickCount > 40;
    }

    private static double mutateFov(CameraProfile profile, double vanillaFov, boolean configuredFov) {
        double source = sanitizeFov(vanillaFov);
        float intensity = profile.intensity();
        float blend = Mth.clamp(0.18F + intensity * 0.82F, 0.0F, 1.0F);
        long seed = profile.seed() ^ (configuredFov ? 0x43464746L : 0x48414E44L);
        double mutated;

        switch (Math.floorMod((int) (seed >>> 26), 8)) {
            case 0 -> {
                double scale = 0.16D + unit(seed ^ 0x5343414CL) * (0.95D + intensity * 2.45D);
                mutated = source * scale;
            }
            case 1 -> {
                double fixed = MIN_SAFE_FOV + unit(seed ^ 0x46495845L) * (MAX_SAFE_FOV - MIN_SAFE_FOV);
                mutated = lerp(source, fixed, blend);
            }
            case 2 -> {
                double step = 1.0D + unit(seed ^ 0x53544550L) * (7.0D + intensity * 48.0D);
                mutated = quantize(source + signed(seed ^ 0x4F464653L, intensity * 68.0D), step);
            }
            case 3 -> {
                double reflected = 180.0D - source + signed(seed ^ 0x494E5646L, intensity * 38.0D);
                mutated = lerp(source, reflected, blend);
            }
            case 4 -> {
                double center = 90.0D + signed(seed ^ 0x43454E54L, intensity * 42.0D);
                double scale = signedScale(seed ^ 0x464F5653L, intensity, 0.18F, 2.75F);
                mutated = center + (source - center) * scale;
            }
            case 5 -> {
                double threshold = 35.0D + unit(seed ^ 0x54485253L) * 102.0D;
                double low = MIN_SAFE_FOV + unit(seed ^ 0x4C4F5746L) * 46.0D;
                double high = 112.0D + unit(seed ^ 0x48494746L) * (MAX_SAFE_FOV - 112.0D);
                mutated = source < threshold ? high : low;
            }
            case 6 -> {
                double factor = 0.06D + unit(seed ^ 0x434F4D50L) * (0.48D + intensity * 1.25D);
                mutated = source * factor + signed(seed ^ 0x434F4D4FL, intensity * 42.0D);
            }
            default -> {
                double wide = 118.0D + unit(seed ^ 0x57494445L) * (MAX_SAFE_FOV - 118.0D);
                double narrow = MIN_SAFE_FOV + unit(seed ^ 0x4E415252L) * 28.0D;
                mutated = unit(seed ^ 0x464C4950L) < 0.5F ? wide : narrow;
            }
        }
        return sanitizeFov(lerp(source, mutated, blend));
    }

    private static CameraProfile currentWorldProfile() {
        CorruptionEffectStack stack = ClientCorruptionEffects.currentForWorldRendering();
        Minecraft minecraft = Minecraft.getInstance();
        if (!cameraReady(minecraft)) {
            return null;
        }
        return profileFor(stack, cameraTargetId(minecraft));
    }

    private static CameraProfile menuProfile() {
        return profileFor(ClientCorruptionEffects.currentForGuiRendering(), "camera:profile:menu:panorama");
    }

    private static CameraProfile profileFor(CorruptionEffectStack stack, String targetId) {
        if (stack == null || !stack.activeOrExtreme(CorruptionSurface.CAMERA_TRANSFORM)) {
            return null;
        }
        float intensity = Mth.clamp(Math.max(
                stack.extreme(CorruptionSurface.CAMERA_TRANSFORM) ? 1.0F : stack.intensity(CorruptionSurface.CAMERA_TRANSFORM),
                stack.targetIntensity(CorruptionSurface.CAMERA_TRANSFORM, targetId)
        ), 0.0F, 1.0F);
        if (intensity <= 0.01F) {
            return null;
        }
        long seed = stack.stableLong(CorruptionSurface.CAMERA_TRANSFORM, targetId, 0x43414D50);
        return new CameraProfile(intensity, seed);
    }

    private static float mutateMenuAngle(float original, int salt, float axisScale) {
        CameraProfile profile = menuProfile();
        if (profile == null || !Float.isFinite(original)) {
            return original;
        }

        long seed = profile.seed() ^ salt;
        float intensity = profile.intensity();
        float blend = Mth.clamp(0.18F + intensity * 0.82F, 0.0F, 1.0F);
        float mutated;
        switch (Math.floorMod((int) (seed >>> 31), 7)) {
            case 0 -> mutated = original + signed(seed ^ 0x4F464653L, intensity * 260.0F * axisScale);
            case 1 -> mutated = quantizeDegrees(original + signed(seed ^ 0x53544550L, intensity * 96.0F), 2.0F + unit(seed ^ 0x53544551L) * (12.0F + intensity * 72.0F));
            case 2 -> mutated = -original * (0.15F + intensity * (1.4F + unit(seed ^ 0x494E5645L) * 4.8F));
            case 3 -> mutated = original * signedScale(seed ^ 0x5343414CL, intensity, 0.12F, 5.5F)
                    + signed(seed ^ 0x5343414FL, intensity * 180.0F * axisScale);
            case 4 -> mutated = signed(seed ^ 0x414E4348L, intensity * 360.0F);
            case 5 -> mutated = fold(original * signedScale(seed ^ 0x464F4C44L, intensity, 0.15F, 6.0F), 18.0F + intensity * 190.0F);
            default -> mutated = original + signed(seed ^ 0x45585452L, intensity * 420.0F * axisScale);
        }
        return Mth.lerp(blend, original, mutated);
    }

    private static String cameraTargetId(Minecraft minecraft) {
        String dimension = minecraft.level == null ? "no_level" : minecraft.level.dimension().location().toString();
        String camera = minecraft.options == null ? "unknown" : minecraft.options.getCameraType().name();
        return "camera:profile:" + dimension + ":" + camera;
    }

    private static double sanitizeFov(double value) {
        if (!Double.isFinite(value)) {
            return 70.0D;
        }
        return Mth.clamp(value, MIN_SAFE_FOV, MAX_SAFE_FOV);
    }

    private static float blendDegrees(float source, float target, float amount) {
        return source + Mth.wrapDegrees(target - source) * Mth.clamp(amount, 0.0F, 1.0F);
    }

    private static float quantizeDegrees(float value, float step) {
        return Mth.wrapDegrees(Math.round(value / step) * step);
    }

    private static float mutateBobPhase(float radians) {
        CameraProfile profile = VIEW_BOB_PROFILE.get();
        if (profile == null || !Float.isFinite(radians)) {
            return radians;
        }

        long seed = profile.seed() ^ 0x424F4250L;
        float intensity = profile.intensity();
        float mutated;
        switch (Math.floorMod((int) (seed >>> 41), 7)) {
            case 0 -> {
                float scale = signedScale(seed ^ 0x50484153L, intensity, 0.10F, 12.0F);
                mutated = radians * scale + signed(seed ^ 0x5048414FL, intensity * Mth.TWO_PI);
            }
            case 1 -> {
                float step = 0.04F + unit(seed ^ 0x53544550L) * (0.22F + intensity * 2.2F);
                mutated = quantize(radians * signedScale(seed ^ 0x50535445L, intensity, 0.15F, 10.0F), step);
            }
            case 2 -> mutated = -radians * (0.35F + intensity * (1.5F + unit(seed ^ 0x494E5653L) * 8.5F));
            case 3 -> {
                float width = 0.12F + unit(seed ^ 0x50464F4CL) * (0.28F + intensity * 2.4F);
                mutated = fold(radians * signedScale(seed ^ 0x50464F53L, intensity, 0.20F, 11.0F), width);
            }
            case 4 -> mutated = quantize(radians + signed(seed ^ 0x50535454L, intensity * Mth.TWO_PI), 0.08F + intensity * 1.25F);
            case 5 -> mutated = radians * (1.0F + signed(seed ^ 0x50534D52L, intensity * (2.0F + unit(seed ^ 0x50534D53L) * 10.0F)));
            default -> mutated = radians * signedScale(seed ^ 0x50455854L, intensity, 0.08F, 14.0F) + signed(seed ^ 0x5045584FL, intensity * Mth.TWO_PI);
        }
        return normalizeRadians(mutated, radians);
    }

    private static double lerp(double source, double target, double amount) {
        return source + (target - source) * Math.max(0.0D, Math.min(1.0D, amount));
    }

    private static double quantize(double value, double step) {
        return step <= 0.0D ? value : Math.rint(value / step) * step;
    }

    private static float quantize(float value, float step) {
        return step <= 0.0F ? value : Math.round(value / step) * step;
    }

    private static float fold(float value, float width) {
        if (width <= 0.0001F) {
            return value;
        }
        float folded = Math.abs(value % (width * 2.0F));
        return folded > width ? width * 2.0F - folded : folded;
    }

    private static float sanitizeBob(float value) {
        return Float.isFinite(value) ? Mth.clamp(value, -5.5F, 5.5F) : 0.0F;
    }

    private static float sanitizeEyeHeight(float value) {
        return Float.isFinite(value) ? Mth.clamp(value, -2.75F, 6.0F) : 0.0F;
    }

    private static Vec3 finitePosition(Vec3 value, Vec3 fallback) {
        if (Double.isFinite(value.x) && Double.isFinite(value.y) && Double.isFinite(value.z)) {
            return value;
        }
        return fallback;
    }

    private static float normalizeRadians(float value, float fallback) {
        if (!Float.isFinite(value)) {
            return fallback;
        }
        return (float) Math.IEEEremainder(value, Mth.TWO_PI);
    }

    private static float signedScale(long seed, float intensity, float minMagnitude, float maxMagnitude) {
        float magnitude = minMagnitude + unit(seed ^ 0x4D41474EL) * (maxMagnitude - minMagnitude) * Math.max(intensity, 0.001F);
        return unit(seed ^ 0x5349474EL) < 0.5F ? -magnitude : magnitude;
    }

    private static float signed(long value, float amplitude) {
        return (unit(value) * 2.0F - 1.0F) * amplitude;
    }

    private static double signed(long value, double amplitude) {
        return (unit(value) * 2.0D - 1.0D) * amplitude;
    }

    private static float unit(long value) {
        value ^= value >>> 33;
        value *= 0xff51afd7ed558ccdL;
        value ^= value >>> 33;
        value *= 0xc4ceb9fe1a85ec53L;
        value ^= value >>> 33;
        return ((value >>> 40) & 0xFF_FFFFL) / 16_777_215.0F;
    }

    private record CameraProfile(float intensity, long seed) {
    }
}
