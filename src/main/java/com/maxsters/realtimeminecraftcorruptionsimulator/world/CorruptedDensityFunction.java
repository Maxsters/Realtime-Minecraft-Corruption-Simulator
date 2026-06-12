package com.maxsters.realtimeminecraftcorruptionsimulator.world;

import net.minecraft.util.KeyDispatchDataCodec;
import net.minecraft.world.level.levelgen.DensityFunction;

public final class CorruptedDensityFunction implements DensityFunction {
    private final String channel;
    private final DensityFunction delegate;

    CorruptedDensityFunction(String channel, DensityFunction delegate) {
        this.channel = channel;
        this.delegate = delegate;
    }

    @Override
    public double compute(FunctionContext context) {
        return WorldgenCorruptionHooks.corruptDensitySample(
                channel,
                delegate.compute(context),
                context.blockX(),
                context.blockY(),
                context.blockZ(),
                delegate.minValue(),
                delegate.maxValue()
        );
    }

    @Override
    public void fillArray(double[] values, ContextProvider contextProvider) {
        delegate.fillArray(values, contextProvider);
        for (int i = 0; i < values.length; i++) {
            FunctionContext context = contextProvider.forIndex(i);
            values[i] = WorldgenCorruptionHooks.corruptDensitySample(
                    channel,
                    values[i],
                    context.blockX(),
                    context.blockY(),
                    context.blockZ(),
                    delegate.minValue(),
                    delegate.maxValue()
            );
        }
    }

    @Override
    public DensityFunction mapAll(Visitor visitor) {
        return visitor.apply(new CorruptedDensityFunction(channel, delegate.mapAll(visitor)));
    }

    @Override
    public double minValue() {
        return WorldgenCorruptionHooks.densityMinValue(channel, delegate.minValue());
    }

    @Override
    public double maxValue() {
        return WorldgenCorruptionHooks.densityMaxValue(channel, delegate.maxValue());
    }

    @Override
    public KeyDispatchDataCodec<? extends DensityFunction> codec() {
        return delegate.codec();
    }
}
