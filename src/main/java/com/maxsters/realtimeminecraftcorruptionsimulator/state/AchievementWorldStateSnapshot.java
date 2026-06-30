package com.maxsters.realtimeminecraftcorruptionsimulator.state;

import net.minecraft.network.FriendlyByteBuf;

import java.util.LinkedHashSet;
import java.util.Set;

public record AchievementWorldStateSnapshot(
        boolean disqualified,
        boolean warrantyStarted,
        boolean warrantyDisqualified,
        Set<String> armedDragonIds,
        Set<String> spoiledDragonIds
) {
    private static final int MAX_DRAGON_IDS = 64;
    private static final int MAX_ID_LENGTH = 64;

    public AchievementWorldStateSnapshot {
        armedDragonIds = copyLimited(armedDragonIds);
        spoiledDragonIds = copyLimited(spoiledDragonIds);
    }

    public static AchievementWorldStateSnapshot empty() {
        return new AchievementWorldStateSnapshot(false, false, false, Set.of(), Set.of());
    }

    public static AchievementWorldStateSnapshot from(CorruptionSavedData data) {
        if (data == null) {
            return empty();
        }
        return new AchievementWorldStateSnapshot(
                data.isAchievementWorldDisqualified(),
                data.isWarrantyStarted(),
                data.isWarrantyDisqualified(),
                data.armedDragonIds(),
                data.spoiledDragonIds()
        );
    }

    public void encode(FriendlyByteBuf buffer) {
        buffer.writeBoolean(disqualified);
        buffer.writeBoolean(warrantyStarted);
        buffer.writeBoolean(warrantyDisqualified);
        writeSet(buffer, armedDragonIds);
        writeSet(buffer, spoiledDragonIds);
    }

    public static AchievementWorldStateSnapshot decode(FriendlyByteBuf buffer) {
        if (!buffer.isReadable()) {
            return empty();
        }
        boolean disqualified = buffer.readBoolean();
        boolean warrantyStarted = buffer.readBoolean();
        boolean warrantyDisqualified = buffer.readBoolean();
        Set<String> armedDragonIds = readSet(buffer);
        Set<String> spoiledDragonIds = readSet(buffer);
        return new AchievementWorldStateSnapshot(disqualified, warrantyStarted, warrantyDisqualified, armedDragonIds, spoiledDragonIds);
    }

    private static void writeSet(FriendlyByteBuf buffer, Set<String> values) {
        Set<String> limited = copyLimited(values);
        buffer.writeVarInt(limited.size());
        for (String value : limited) {
            buffer.writeUtf(value, MAX_ID_LENGTH);
        }
    }

    private static Set<String> readSet(FriendlyByteBuf buffer) {
        int count = Math.max(0, buffer.readVarInt());
        Set<String> values = new LinkedHashSet<>();
        for (int index = 0; index < count; index++) {
            String value = sanitizeId(buffer.readUtf(MAX_ID_LENGTH));
            if (values.size() < MAX_DRAGON_IDS && !value.isBlank()) {
                values.add(value);
            }
        }
        return values;
    }

    private static Set<String> copyLimited(Set<String> values) {
        Set<String> copy = new LinkedHashSet<>();
        if (values == null || values.isEmpty()) {
            return Set.copyOf(copy);
        }
        for (String value : values) {
            String sanitized = sanitizeId(value);
            if (!sanitized.isBlank()) {
                copy.add(sanitized);
            }
            if (copy.size() >= MAX_DRAGON_IDS) {
                break;
            }
        }
        return Set.copyOf(copy);
    }

    private static String sanitizeId(String value) {
        if (value == null || value.isBlank()) {
            return "";
        }
        String trimmed = value.trim();
        return trimmed.length() > MAX_ID_LENGTH ? trimmed.substring(0, MAX_ID_LENGTH) : trimmed;
    }
}
