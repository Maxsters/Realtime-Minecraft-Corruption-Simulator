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
        WorldgenCorruptionHooks.DensityCoordinates coordinates = WorldgenCorruptionHooks.corruptDensityCoordinates(
                channel,
                context.blockX(),
                context.blockY(),
                context.blockZ()
        );
        FunctionContext sampleContext = coordinates.matches(context.blockX(), context.blockY(), context.blockZ())
                ? context
                : new CoordinateFunctionContext(coordinates.x(), coordinates.y(), coordinates.z());
        return WorldgenCorruptionHooks.corruptDensitySample(
                channel,
                delegate.compute(sampleContext),
                context.blockX(),
                context.blockY(),
                context.blockZ(),
                delegate.minValue(),
                delegate.maxValue()
        );
    }

    @Override
    public void fillArray(double[] values, ContextProvider contextProvider) {
        if (WorldgenCorruptionHooks.shouldUseFastDensityFill()) {
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

    private record CoordinateFunctionContext(int blockX, int blockY, int blockZ) implements FunctionContext {
    }
}
