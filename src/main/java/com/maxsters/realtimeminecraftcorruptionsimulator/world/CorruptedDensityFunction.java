package com.maxsters.realtimeminecraftcorruptionsimulator.world;

import net.minecraft.util.KeyDispatchDataCodec;
import net.minecraft.world.level.levelgen.DensityFunction;

public final class CorruptedDensityFunction implements DensityFunction {
    private final String channel;
    private final DensityFunction delegate;
    private final double delegateMinValue;
    private final double delegateMaxValue;

    CorruptedDensityFunction(String channel, DensityFunction delegate) {
        this.channel = channel;
        this.delegate = delegate;
        this.delegateMinValue = delegate.minValue();
        this.delegateMaxValue = delegate.maxValue();
    }

    @Override
    public double compute(FunctionContext context) {
        if (!WorldgenCorruptionHooks.shouldCorruptDensity(channel)) {
            return delegate.compute(context);
        }
        int x = context.blockX();
        int y = context.blockY();
        int z = context.blockZ();
        WorldgenCorruptionHooks.DensityCoordinates coordinates = WorldgenCorruptionHooks.corruptDensityCoordinates(channel, x, y, z);
        FunctionContext sampleContext = coordinates == null ? context : new CoordinateFunctionContext(coordinates.x(), coordinates.y(), coordinates.z());
        return WorldgenCorruptionHooks.corruptDensitySample(
                channel,
                delegate.compute(sampleContext),
                x,
                y,
                z,
                delegateMinValue,
                delegateMaxValue
        );
    }

    @Override
    public void fillArray(double[] values, ContextProvider contextProvider) {
        if (!WorldgenCorruptionHooks.shouldCorruptDensity(channel)) {
            delegate.fillArray(values, contextProvider);
            return;
        }
        if (WorldgenCorruptionHooks.shouldUseFastDensityFill(channel)) {
            delegate.fillArray(values, contextProvider);
            for (int i = 0; i < values.length; i++) {
                FunctionContext context = contextProvider.forIndex(i);
                values[i] = WorldgenCorruptionHooks.corruptDensitySample(
                        channel,
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

        for (int i = 0; i < values.length; i++) {
            FunctionContext context = contextProvider.forIndex(i);
            values[i] = compute(context);
        }
    }

    @Override
    public DensityFunction mapAll(Visitor visitor) {
        return visitor.apply(new CorruptedDensityFunction(channel, delegate.mapAll(visitor)));
    }

    @Override
    public double minValue() {
        return WorldgenCorruptionHooks.densityMinValue(channel, delegateMinValue);
    }

    @Override
    public double maxValue() {
        return WorldgenCorruptionHooks.densityMaxValue(channel, delegateMaxValue);
    }

    @Override
    public KeyDispatchDataCodec<? extends DensityFunction> codec() {
        return delegate.codec();
    }

    private record CoordinateFunctionContext(int blockX, int blockY, int blockZ) implements FunctionContext {
    }
}
