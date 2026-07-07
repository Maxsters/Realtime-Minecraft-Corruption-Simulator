package com.maxsters.realtimeminecraftcorruptionsimulator.world;

record WorldgenProfile(
        float continentalDensityScale,
        float shapeDensityScale,
        float climateDensityScale,
        float climateScale,
        float surfaceScale,
        float featureScale,
        float carverScale,
        float structureScale,
        float decorationScale,
        float verticalScale,
        float macroScale,
        int densityStyle,
        int gateStyle,
        float continentalActivation,
        float shapeActivation,
        float climateActivation,
        float extremeActivation,
        int featureStyle,
        float featureActivation,
        float featureVerticalActivation
) {
    static WorldgenProfile fromSeed(long fixedSeed) {
        long seed = mix(fixedSeed ^ 0x57474E50524F464CL);
        int primary = Math.floorMod((int) (seed >>> 57), 6);
        int secondary = Math.floorMod((int) (seed >>> 49), 6);
        if (secondary == primary) {
            secondary = (secondary + 1 + Math.floorMod((int) (seed >>> 43), 5)) % 6;
        }

        float continentalDensity = profileScale(seed ^ 0x434F4E54494E4CL, primary == 0, secondary == 0);
        float shapeDensity = profileScale(seed ^ 0x5348415045444CL, primary == 1, secondary == 1);
        float climateDensity = profileScale(seed ^ 0x434C494D44454EL, primary == 2, secondary == 2);
        float climate = profileScale(seed ^ 0x434C494D415445L, primary == 2, secondary == 2);
        float surface = profileScale(seed ^ 0x53555246414345L, primary == 3, secondary == 3);
        float feature = profileScale(seed ^ 0x46454154555245L, primary == 4, secondary == 4);
        float carver = profileScale(seed ^ 0x434152564552L, primary == 5, secondary == 5);
        float structure = profileScale(seed ^ 0x535452554354L, primary == 4, secondary == 4);
        float decoration = profileScale(seed ^ 0x4445434F524154L, primary == 4, secondary == 4);
        float vertical = profileScale(seed ^ 0x5645525449434CL, primary == 1 || primary == 4, secondary == 1 || secondary == 4);
        float macro = clampFloat(0.55F + unit(seed ^ 0x4D4143524F5343L) * 1.55F
                + (primary == 2 || primary == 3 ? 0.24F : 0.0F)
                - (primary == 0 ? 0.18F : 0.0F), 0.42F, 2.35F);
        int densityStyle = Math.floorMod((int) (seed >>> 35), 7);
        int gateStyle = gateStyleFromSeed(seed);
        float continentalActivation = densityGroupActivation(densityStyle, 0, seed ^ 0x434F4E54414354L);
        float shapeActivation = densityGroupActivation(densityStyle, 1, seed ^ 0x53484150414354L);
        float climateActivation = densityGroupActivation(densityStyle, 2, seed ^ 0x434C494D414354L);
        float extremeActivation = clampFloat(0.38F + unit(seed ^ 0x455854414354L) * 1.18F
                + (densityStyle == 1 || densityStyle == 4 ? 0.22F : 0.0F)
                - (densityStyle == 3 ? 0.18F : 0.0F), 0.22F, 1.55F);
        int featureStyle = Math.floorMod((int) (seed >>> 22), 6);
        float featureActivation = clampFloat(0.28F + unit(seed ^ 0x46454143544CL) * 0.92F
                + (primary == 4 ? 0.42F : 0.0F)
                + (featureStyle == 1 || featureStyle == 4 ? 0.28F : 0.0F), 0.12F, 1.55F);
        float featureVerticalActivation = clampFloat(switch (featureStyle) {
            case 0 -> 0.16F;
            case 1 -> 1.22F;
            case 2 -> 0.38F;
            case 3 -> 0.06F;
            case 4 -> 0.72F;
            default -> 0.26F;
        } + unit(seed ^ 0x465456455254L) * 0.18F, 0.04F, 1.34F);

        return new WorldgenProfile(
                continentalDensity,
                shapeDensity,
                climateDensity,
                climate,
                surface,
                feature,
                carver,
                structure,
                decoration,
                vertical,
                macro,
                densityStyle,
                gateStyle,
                continentalActivation,
                shapeActivation,
                climateActivation,
                extremeActivation,
                featureStyle,
                featureActivation,
                featureVerticalActivation
        );
    }

    private static int gateStyleFromSeed(long seed) {
        int roll = Math.floorMod((int) (seed >>> 28), 12);
        return switch (roll) {
            case 0, 1, 2 -> 0;
            case 3, 4 -> 2;
            case 5, 6 -> 3;
            case 7, 8 -> 4;
            case 9, 10 -> 5;
            default -> 1;
        };
    }

    private static float densityGroupActivation(int style, int group, long seed) {
        float jitter = unit(seed) * 0.34F;
        float base = switch (style) {
            case 0 -> group == 0 ? 1.45F : group == 1 ? 0.62F : 0.78F;
            case 1 -> group == 1 ? 1.58F : group == 0 ? 0.58F : 0.34F;
            case 2 -> group == 0 ? 0.82F : group == 1 ? 0.92F : 1.28F;
            case 3 -> group == 2 ? 1.55F : group == 0 ? 0.36F : 0.58F;
            case 4 -> group == 1 ? 1.12F : group == 0 ? 1.18F : 0.44F;
            case 5 -> group == 0 ? 0.55F : group == 1 ? 0.78F : 0.95F;
            default -> group == 0 ? 1.05F : group == 1 ? 1.05F : 0.72F;
        };
        return clampFloat(base + jitter, 0.18F, 1.72F);
    }

    private static float profileScale(long seed, boolean primary, boolean secondary) {
        float value = 0.38F + unit(seed) * 1.02F;
        if (primary) {
            value += 0.62F + unit(seed >>> 13) * 0.48F;
        } else if (secondary) {
            value += 0.28F + unit(seed >>> 17) * 0.28F;
        }
        return clampFloat(value, 0.30F, 2.15F);
    }

    private static long mix(long value) {
        value ^= value >>> 33;
        value *= 0xff51afd7ed558ccdL;
        value ^= value >>> 33;
        value *= 0xc4ceb9fe1a85ec53L;
        value ^= value >>> 33;
        return value;
    }

    private static float unit(long value) {
        return ((mix(value) >>> 40) & 0xFF_FFFFL) / 16_777_215.0F;
    }

    private static float clampFloat(float value, float min, float max) {
        return Math.max(min, Math.min(max, value));
    }
}
