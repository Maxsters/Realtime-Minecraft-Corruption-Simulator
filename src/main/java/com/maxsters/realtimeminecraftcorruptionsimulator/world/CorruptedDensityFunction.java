package com.maxsters.realtimeminecraftcorruptionsimulator.world;

import net.minecraft.util.KeyDispatchDataCodec;
import net.minecraft.world.level.levelgen.DensityFunction;

public final class CorruptedDensityFunction implements DensityFunction {
    private final String channel;
    private final DensityFunction delegate;
    private final double delegateMinValue;
    private final double delegateMaxValue;
    private volatile WorldgenCorruptionHooks.DensityRuntime cachedRuntime;

    CorruptedDensityFunction(String channel, DensityFunction delegate) {
        this.channel = channel;
        this.delegate = delegate;
        this.delegateMinValue = delegate.minValue();
        this.delegateMaxValue = delegate.maxValue();
    }

    @Override
    public double compute(FunctionContext context) {
        WorldgenCorruptionHooks.DensityRuntime runtime = runtime();
        if (!runtime.sampleActive()) {
            return delegate.compute(context);
        }
        return WorldgenCorruptionHooks.corruptDensitySample(
                runtime,
                delegate.compute(context),
                context.blockX(),
                context.blockY(),
                context.blockZ(),
                delegateMinValue,
                delegateMaxValue
        );
    }

    @Override
    public void fillArray(double[] values, ContextProvider contextProvider) {
        WorldgenCorruptionHooks.DensityRuntime runtime = runtime();
        if (!runtime.sampleActive()) {
            delegate.fillArray(values, contextProvider);
            return;
        }
        delegate.fillArray(values, contextProvider);
        for (int i = 0; i < values.length; i++) {
            FunctionContext context = contextProvider.forIndex(i);
            values[i] = WorldgenCorruptionHooks.corruptDensitySample(
                    runtime,
                    values[i],
                    context.blockX(),
                    context.blockY(),
                    context.blockZ(),
                    delegateMinValue,
                    delegateMaxValue
            );
        }
    }

    @Override
    public DensityFunction mapAll(Visitor visitor) {
        return visitor.apply(new CorruptedDensityFunction(channel, delegate.mapAll(visitor)));
    }

    @Override
    public double minValue() {
        WorldgenCorruptionHooks.DensityRuntime runtime = runtime();
        return !runtime.sampleActive() ? delegateMinValue : delegateMinValue - runtime.boundsAmplitude() * 1.6D;
    }

    @Override
    public double maxValue() {
        WorldgenCorruptionHooks.DensityRuntime runtime = runtime();
        return !runtime.sampleActive() ? delegateMaxValue : delegateMaxValue + runtime.boundsAmplitude() * 1.6D;
    }

    @Override
    public KeyDispatchDataCodec<? extends DensityFunction> codec() {
        return delegate.codec();
    }

    private WorldgenCorruptionHooks.DensityRuntime runtime() {
        WorldgenCorruptionHooks.DensityRuntime runtime = cachedRuntime;
        long version = WorldgenCorruptionHooks.worldgenVersion();
        if (runtime != null && runtime.version() == version) {
            return runtime;
        }
        runtime = WorldgenCorruptionHooks.densityRuntime(channel);
        cachedRuntime = runtime;
        return runtime;
    }
}
