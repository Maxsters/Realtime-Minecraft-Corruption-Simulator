package com.maxsters.realtimeminecraftcorruptionsimulator.mechanics;

import com.maxsters.realtimeminecraftcorruptionsimulator.profile.CorruptionEffectStack;
import com.maxsters.realtimeminecraftcorruptionsimulator.profile.CorruptionSurface;
import net.minecraft.core.Direction;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.Property;

import java.util.ArrayList;
import java.util.List;

final class BlockBehaviorCorruptionMechanics {
    private BlockBehaviorCorruptionMechanics() {
    }

    static boolean suppress(
            CorruptionEffectStack stack,
            String callback,
            String subject,
            int salt,
            float baseChance,
            float intensityScale,
            float extremeChance,
            float minimumIntensity
    ) {
        return StableSubsystemFaults.broken(
                stack,
                CorruptionSurface.BLOCK_BEHAVIOR,
                callback,
                subject,
                salt,
                baseChance,
                intensityScale,
                extremeChance,
                minimumIntensity
        );
    }

    static BlockState mutateDirectionalState(
            CorruptionEffectStack stack,
            BlockState before,
            BlockState vanilla,
            float minimumIntensity
    ) {
        if (vanilla == null) {
            return null;
        }

        List<Property<?>> directional = directionalProperties(vanilla);
        if (directional.isEmpty()) {
            return vanilla;
        }

        String subject = vanilla.getBlock().getClass().getName();
        if (!suppress(stack, "connection_rule", subject, 0x434F4E4E,
                0.24F, 0.86F, 0.98F, minimumIntensity)) {
            return vanilla;
        }

        long seed = StableSubsystemFaults.seed(
                stack,
                CorruptionSurface.BLOCK_BEHAVIOR,
                "connection_profile",
                subject,
                0x434F4E4E
        );
        int mode = Math.floorMod((int) (seed >>> 29), 5);
        return switch (mode) {
            case 0 -> setDirectionalExtremum(vanilla, directional, false);
            case 1 -> setDirectionalExtremum(vanilla, directional, true);
            case 2 -> rotateDirectionalValues(vanilla, directional, 1 + Math.floorMod((int) (seed >>> 41), 3));
            case 3 -> cycleDirectionalValues(vanilla, directional);
            default -> before == null ? rotateDirectionalValues(vanilla, directional, 2) : copyDirectionalValues(vanilla, before, directional);
        };
    }

    private static List<Property<?>> directionalProperties(BlockState state) {
        List<Property<?>> result = new ArrayList<>(6);
        for (Property<?> property : state.getProperties()) {
            if (direction(property.getName()) != null) {
                result.add(property);
            }
        }
        return result;
    }

    private static BlockState setDirectionalExtremum(BlockState state, List<Property<?>> properties, boolean maximum) {
        BlockState result = state;
        for (Property<?> property : properties) {
            List<? extends Comparable<?>> values = List.copyOf(property.getPossibleValues());
            if (!values.isEmpty()) {
                result = setValue(result, property, values.get(maximum ? values.size() - 1 : 0));
            }
        }
        return result;
    }

    private static BlockState cycleDirectionalValues(BlockState state, List<Property<?>> properties) {
        BlockState result = state;
        for (Property<?> property : properties) {
            List<? extends Comparable<?>> values = List.copyOf(property.getPossibleValues());
            if (values.size() < 2) {
                continue;
            }
            Comparable<?> current = value(state, property);
            int index = values.indexOf(current);
            result = setValue(result, property, values.get(Math.floorMod(index + 1, values.size())));
        }
        return result;
    }

    private static BlockState rotateDirectionalValues(BlockState state, List<Property<?>> properties, int steps) {
        BlockState result = state;
        for (Property<?> target : properties) {
            Direction targetDirection = direction(target.getName());
            if (targetDirection == null || targetDirection.getAxis().isVertical()) {
                continue;
            }
            Direction sourceDirection = targetDirection;
            for (int i = 0; i < steps; i++) {
                sourceDirection = sourceDirection.getCounterClockWise();
            }
            Property<?> source = property(properties, sourceDirection);
            if (source != null) {
                result = setValue(result, target, value(state, source));
            }
        }
        return result;
    }

    private static BlockState copyDirectionalValues(BlockState targetState, BlockState sourceState, List<Property<?>> properties) {
        BlockState result = targetState;
        for (Property<?> target : properties) {
            Property<?> source = sourceState.getProperties().stream()
                    .filter(property -> property.getName().equals(target.getName()))
                    .findFirst()
                    .orElse(null);
            if (source != null) {
                result = setValue(result, target, value(sourceState, source));
            }
        }
        return result;
    }

    private static Property<?> property(List<Property<?>> properties, Direction direction) {
        for (Property<?> property : properties) {
            if (direction == direction(property.getName())) {
                return property;
            }
        }
        return null;
    }

    private static Direction direction(String name) {
        return switch (name) {
            case "north" -> Direction.NORTH;
            case "east" -> Direction.EAST;
            case "south" -> Direction.SOUTH;
            case "west" -> Direction.WEST;
            case "up" -> Direction.UP;
            case "down" -> Direction.DOWN;
            default -> null;
        };
    }

    @SuppressWarnings({"rawtypes", "unchecked"})
    private static Comparable<?> value(BlockState state, Property<?> property) {
        return state.getValue((Property) property);
    }

    @SuppressWarnings({"rawtypes", "unchecked"})
    private static BlockState setValue(BlockState state, Property<?> property, Comparable<?> value) {
        Property raw = property;
        return raw.getPossibleValues().contains(value) ? state.setValue(raw, (Comparable) value) : state;
    }
}
