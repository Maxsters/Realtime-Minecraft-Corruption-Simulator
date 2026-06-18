package com.maxsters.realtimeminecraftcorruptionsimulator.client.hooks;

import com.maxsters.realtimeminecraftcorruptionsimulator.client.effects.ClientCorruptionEffects;
import com.maxsters.realtimeminecraftcorruptionsimulator.profile.CorruptionEffectStack;
import com.maxsters.realtimeminecraftcorruptionsimulator.profile.CorruptionSurface;
import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.EntityHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.api.distmarker.OnlyIn;

import java.util.Locale;

@OnlyIn(Dist.CLIENT)
public final class InteractionCorruptionHooks {
    private InteractionCorruptionHooks() {
    }

    public static void corruptPick(Minecraft minecraft, float partialTick) {
        if (minecraft == null || minecraft.player == null || minecraft.level == null || minecraft.hitResult == null
                || minecraft.hitResult.getType() == HitResult.Type.MISS) {
            return;
        }

        CorruptionEffectStack stack = ClientCorruptionEffects.current();
        if (!stack.activeOrExtreme(CorruptionSurface.INTERACTION_ROUTING)) {
            return;
        }

        Player player = minecraft.player;
        HitResult hitResult = minecraft.hitResult;
        String targetId = targetId(hitResult);
        float intensity = Mth.clamp(Math.max(
                stack.extreme(CorruptionSurface.INTERACTION_ROUTING) ? 1.0F : stack.intensity(CorruptionSurface.INTERACTION_ROUTING),
                stack.targetIntensity(CorruptionSurface.INTERACTION_ROUTING, targetId)
        ), 0.0F, 1.0F);
        long clock = gameTime(minecraft) ^ Float.floatToIntBits(partialTick);
        long seed = stack.stableLong(CorruptionSurface.INTERACTION_ROUTING, targetId, player.getId() ^ (int) clock);
        float chance = stack.extreme(CorruptionSurface.INTERACTION_ROUTING)
                ? 0.94F
                : Mth.clamp(0.04F + intensity * 0.76F + stack.instability() * 0.10F, 0.0F, 0.88F);
        if (stack.unit(CorruptionSurface.INTERACTION_ROUTING, targetId + ":client_pick", (int) (seed ^ 0x5049434BL)) > chance) {
            return;
        }

        Vec3 location = hitResult.getLocation();
        Vec3 view = player.getViewVector(partialTick);
        Direction direction = Direction.getNearest(view.x, view.y, view.z).getOpposite();
        if (unit(seed ^ 0x4F464653L) < intensity * 0.46F) {
            location = location.add(
                    signed(seed ^ 0x584F4646L, intensity * 0.28D),
                    signed(seed ^ 0x594F4646L, intensity * 0.18D),
                    signed(seed ^ 0x5A4F4646L, intensity * 0.28D)
            );
        }
        minecraft.hitResult = BlockHitResult.miss(location, direction, BlockPos.containing(location));
        minecraft.crosshairPickEntity = null;
    }

    private static String targetId(HitResult hitResult) {
        if (hitResult instanceof BlockHitResult blockHit) {
            return "client_hover:block:" + blockHit.getBlockPos().getX() + ":" + blockHit.getBlockPos().getY() + ":" + blockHit.getBlockPos().getZ();
        }
        if (hitResult instanceof EntityHitResult entityHit) {
            return "client_hover:entity:" + entityHit.getEntity().getType();
        }
        return "client_hover:" + hitResult.getType().name().toLowerCase(Locale.ROOT);
    }

    private static long gameTime(Minecraft minecraft) {
        return minecraft.level == null ? System.currentTimeMillis() / 50L : minecraft.level.getGameTime();
    }

    private static double signed(long seed, double amplitude) {
        return (unit(seed) * 2.0D - 1.0D) * amplitude;
    }

    private static float unit(long value) {
        value ^= value >>> 33;
        value *= 0xff51afd7ed558ccdL;
        value ^= value >>> 33;
        value *= 0xc4ceb9fe1a85ec53L;
        value ^= value >>> 33;
        return ((value >>> 40) & 0xFF_FFFFL) / 16_777_215.0F;
    }
}
