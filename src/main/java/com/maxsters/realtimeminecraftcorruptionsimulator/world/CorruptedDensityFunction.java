package com.maxsters.realtimeminecraftcorruptionsimulator.world;

import net.minecraft.util.KeyDispatchDataCodec;
import net.minecraft.world.level.levelgen.DensityFunction;

public final class CorruptedDensityFunction implements DensityFunction {
    private final ThreadLocal<WorldgenCorruptionHooks.DensityCoordinates> sampleContext = ThreadLocal.withInitial(WorldgenCorruptionHooks.DensityCoordinates::new);
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
        return compute(runtime, context, null);
    }

    private double compute(WorldgenCorruptionHooks.DensityRuntime runtime, FunctionContext context, DensitySampleCache sampleCache) {
        int x = context.blockX();
        int y = context.blockY();
        int z = context.blockZ();
        WorldgenCorruptionHooks.DensityCoordinates coordinates = sampleContext.get();
        boolean rerouted = WorldgenCorruptionHooks.corruptDensityCoordinates(runtime, x, y, z, coordinates);
        double delegateValue;
        if (rerouted) {
            delegateValue = sampleCache == null ? delegate.compute(coordinates) : sampleCache.compute(delegate, coordinates);
        } else {
            delegateValue = delegate.compute(context);
        }
        return WorldgenCorruptionHooks.corruptDensitySample(
                runtime,
                delegateValue,
                x,
                y,
                z,
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
        if (!runtime.coordinateActive()) {
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
            return;
        }

        DensitySampleCache sampleCache = new DensitySampleCache();
        for (int i = 0; i < values.length; i++) {
            FunctionContext context = contextProvider.forIndex(i);
            values[i] = compute(runtime, context, sampleCache);
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

    private static final class DensitySampleCache {
        private static final int SIZE = 64;
        private final int[] xs = new int[SIZE];
        private final int[] ys = new int[SIZE];
        private final int[] zs = new int[SIZE];
        private final double[] values = new double[SIZE];
        private final boolean[] populated = new boolean[SIZE];

        private double compute(DensityFunction delegate, WorldgenCorruptionHooks.DensityCoordinates context) {
            int x = context.blockX();
            int y = context.blockY();
            int z = context.blockZ();
            int slot = slot(x, y, z);
            if (populated[slot] && xs[slot] == x && ys[slot] == y && zs[slot] == z) {
                return values[slot];
            }
            double value = delegate.compute(context);
            populated[slot] = true;
            xs[slot] = x;
            ys[slot] = y;
            zs[slot] = z;
            values[slot] = value;
            return value;
        }

        private static int slot(int x, int y, int z) {
            int hash = x * 73428767 ^ y * 91227153 ^ z * 4382893;
            hash ^= hash >>> 16;
            return hash & (SIZE - 1);
        }
    }
}
