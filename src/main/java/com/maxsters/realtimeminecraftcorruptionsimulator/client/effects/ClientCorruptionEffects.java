package com.maxsters.realtimeminecraftcorruptionsimulator.client.effects;

import com.maxsters.realtimeminecraftcorruptionsimulator.client.ClientCorruptionProtection;
import com.maxsters.realtimeminecraftcorruptionsimulator.client.ClientCorruptionState;
import com.maxsters.realtimeminecraftcorruptionsimulator.profile.CorruptionEffectStack;
import com.maxsters.realtimeminecraftcorruptionsimulator.state.CorruptionStateSnapshot;
import net.minecraft.client.Minecraft;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.api.distmarker.OnlyIn;

@OnlyIn(Dist.CLIENT)
public final class ClientCorruptionEffects {
    private static final CorruptionEffectStack INACTIVE_STACK = CorruptionEffectStack.local(0);
    private static volatile CorruptionStateSnapshot cachedSnapshot;
    private static volatile CorruptionEffectStack cachedStack = INACTIVE_STACK;

    private ClientCorruptionEffects() {
    }

    public static CorruptionEffectStack current() {
        if (ClientCorruptionProtection.shouldSuppressClientCorruption()) {
            return INACTIVE_STACK;
        }

        return fromClientSnapshot();
    }

    public static CorruptionEffectStack currentUnsuppressed() {
        return fromClientSnapshot();
    }

    public static CorruptionEffectStack currentForGuiRendering() {
        if (ClientCorruptionProtection.isProtectedGuiRendering()) {
            return INACTIVE_STACK;
        }

        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft != null
                && (ClientCorruptionProtection.isModScreen(minecraft.screen)
                || ClientCorruptionProtection.isDeathScreen(minecraft.screen)
                || (minecraft.screen == null && minecraft.level == null && minecraft.player == null))) {
            return INACTIVE_STACK;
        }

        return fromClientSnapshot();
    }

    public static CorruptionEffectStack currentForWorldRendering() {
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft == null || minecraft.level == null) {
            return INACTIVE_STACK;
        }
        return fromClientSnapshot();
    }

    public static CorruptionEffectStack currentForGameplay() {
        CorruptionStateSnapshot snapshot = ClientCorruptionState.snapshot();
        return snapshot == null ? INACTIVE_STACK : CorruptionEffectStack.fromGameplay(snapshot);
    }

    private static CorruptionEffectStack fromClientSnapshot() {
        CorruptionStateSnapshot snapshot = ClientCorruptionState.snapshot();
        if (snapshot == null) {
            return INACTIVE_STACK;
        }

        CorruptionStateSnapshot cachedSnapshot = ClientCorruptionEffects.cachedSnapshot;
        if (snapshot == cachedSnapshot || snapshot.equals(cachedSnapshot)) {
            return cachedStack;
        }

        CorruptionEffectStack stack = CorruptionEffectStack.from(snapshot);
        ClientCorruptionEffects.cachedSnapshot = snapshot;
        cachedStack = stack;
        return stack;
    }
}
