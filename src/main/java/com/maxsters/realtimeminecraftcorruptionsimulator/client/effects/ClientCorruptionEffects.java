package com.maxsters.realtimeminecraftcorruptionsimulator.client.effects;

import com.maxsters.realtimeminecraftcorruptionsimulator.client.ClientCorruptionProtection;
import com.maxsters.realtimeminecraftcorruptionsimulator.client.ClientCorruptionState;
import com.maxsters.realtimeminecraftcorruptionsimulator.profile.CorruptionEffectStack;
import com.maxsters.realtimeminecraftcorruptionsimulator.state.CorruptionProfileSnapshot;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.api.distmarker.OnlyIn;

@OnlyIn(Dist.CLIENT)
public final class ClientCorruptionEffects {
    private ClientCorruptionEffects() {
    }

    public static CorruptionEffectStack current() {
        if (ClientCorruptionProtection.shouldSuppressClientCorruption()) {
            return CorruptionEffectStack.local(0);
        }

        CorruptionProfileSnapshot snapshot = ClientCorruptionState.snapshot();
        return snapshot == null ? CorruptionEffectStack.local(0) : CorruptionEffectStack.from(snapshot);
    }
}
