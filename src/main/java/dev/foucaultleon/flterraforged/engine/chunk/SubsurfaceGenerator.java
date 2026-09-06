package dev.foucaultleon.flterraforged.engine.chunk;

import dev.foucaultleon.flterraforged.engine.api.EngineContext;
import dev.foucaultleon.flterraforged.engine.api.chunk.ColumnSnapshot;
import dev.foucaultleon.flterraforged.engine.api.chunk.GeologyType;
import dev.foucaultleon.flterraforged.engine.api.chunk.NaturalMaterial;
import dev.foucaultleon.flterraforged.engine.api.river.RiverSample;
import dev.foucaultleon.flterraforged.engine.api.terrain.StandardTerrainTypes;
import dev.foucaultleon.flterraforged.engine.api.terrain.TerrainSample;

/** Resolves all natural vertical geometry without calling any host world-generation stage. */
final class SubsurfaceGenerator {

    private static final long GEOLOGY_SALT = 0x74D3A5B19E3779B9L;
    private static final long SOIL_SALT = 0x1A976CE5D4B2F301L;
    private static final long GROUNDWATER_SALT = 0x6C8E9CF570932BD5L;
    private static final long CAVE_A_SALT = 0x2FD42B81A76C91E3L;
    private static final long CAVE_B_SALT = 0x59C33D14E82A6B07L;
    private static final long CAVERN_SALT = 0x0D9187F4AC52E36BL;
    private static final long RAVINE_SALT = 0x63B57A09DE214CF1L;
    private static final long FLOOR_SALT = 0x45EA92D7138CB6F0L;
    private static final long LAVA_SALT = 0x7F21C54DA893B60EL;
    private static final double MIN_WET_DEPTH = 0.05D;

    private final EngineContext context;

    SubsurfaceGenerator(EngineContext context) {
        this.context = context;
    }

    EngineChunkSnapshot generate(int chunkX, int chunkZ, TerrainSample[] samples) {
        int height = context.height();
        ColumnSnapshot[] columns = new ColumnSnapshot[256];
        byte[] materials = new byte[256 * height];
        int originX = chunkX << 4;
        int originZ = chunkZ << 4;
        for (int localZ = 0; localZ < 16; localZ++) {
            for (int localX = 0; localX < 16; localX++) {
                int columnIndex = localZ * 16 + localX;
                int x = originX + localX;
                int z = originZ + localZ;
                TerrainSample sample = samples[columnIndex];
                ColumnSnapshot column = resolveColumn(sample, x, z);
                columns[columnIndex] = column;
                fillColumn(materials, columnIndex * height, column, x, z);
            }
        }
        return new EngineChunkSnapshot(
                chunkX,
                chunkZ,
                context.minY(),
                context.maxYExclusive(),
                columns,
                materials);
    }

    private ColumnSnapshot resolveColumn(TerrainSample sample, int x, int z) {
        int surfaceY = clamp(
                (int) Math.floor(sample.surfaceHeight()),
                context.minY() + 1,
                context.maxYExclusive() - 2);
        int solidTop = surfaceY + 1;
        int waterTop = solidTop;
        if (StandardTerrainTypes.OCEAN.equals(sample.terrainType())
                || StandardTerrainTypes.COAST.equals(sample.terrainType())) {
            waterTop = Math.max(waterTop, context.seaLevel() + 1);
        }
        RiverSample hydrology = sample.river();
        if (hydrology.hasWaterSurfaceHeight()
                && hydrology.depth() > MIN_WET_DEPTH
                && hydrology.waterSurfaceHeight() > sample.surfaceHeight()) {
            int hydrologyWaterTop = (int) Math.floor(hydrology.waterSurfaceHeight()) + 1;
            if (surfaceY <= context.seaLevel() + 1
                    && hydrology.waterSurfaceHeight() <= context.seaLevel() + 1.5D) {
                hydrologyWaterTop = Math.max(hydrologyWaterTop, context.seaLevel() + 1);
            }
            waterTop = Math.max(waterTop, hydrologyWaterTop);
        }
        waterTop = clamp(waterTop, solidTop, context.maxYExclusive());

        int soilDepth = 3 + (int) Math.floor(unitHash(x >> 4, 0, z >> 4, SOIL_SALT) * 3.0D);
        int groundwaterBase = context.seaLevel() - 7;
        int groundwaterOffset = (int) Math.round(
                smoothNoise2D(x / 96.0D, z / 96.0D, GROUNDWATER_SALT) * 8.0D);
        int groundwaterY = Math.min(surfaceY - 7, groundwaterBase + groundwaterOffset);
        groundwaterY = clamp(groundwaterY, context.minY() + 6, context.maxYExclusive() - 2);
        return new ColumnSnapshot(
                sample,
                geology(x, z),
                surfaceY,
                waterTop,
                soilDepth,
                groundwaterY);
    }

    private void fillColumn(
            byte[] materials,
            int offset,
            ColumnSnapshot column,
            int x,
            int z) {
        int surfaceY = column.solidSurfaceY();
        int naturalTopY = Math.max(surfaceY, column.waterTopExclusive() - 1);
        int bedrockThickness = 1 + (int) Math.floor(unitHash(x, context.minY(), z, FLOOR_SALT) * 4.0D);
        int lavaLevel = context.minY() + Math.max(10, context.height() / 24);
        VerticalNoiseSampler caveA = new VerticalNoiseSampler(x, z, 42.0D, 30.0D, 42.0D, CAVE_A_SALT);
        VerticalNoiseSampler caveB = new VerticalNoiseSampler(x, z, 58.0D, 37.0D, 58.0D, CAVE_B_SALT);
        VerticalNoiseSampler cavern = new VerticalNoiseSampler(x, z, 92.0D, 54.0D, 92.0D, CAVERN_SALT);
        VerticalNoiseSampler ravine = new VerticalNoiseSampler(x, z, 150.0D, 45.0D, 150.0D, RAVINE_SALT);

        // NaturalMaterial.AIR is ordinal zero, and a new byte[] is already zero-filled. Nothing can
        // exist above naturalTopY, so R49 leaves that upper volume untouched instead of evaluating
        // hundreds of guaranteed-air Y positions for every lowland column.
        for (int y = context.minY(); y <= naturalTopY; y++) {
            NaturalMaterial material;
            if (y < context.minY() + bedrockThickness) {
                material = NaturalMaterial.BEDROCK;
            } else if (y > surfaceY) {
                material = y < column.waterTopExclusive()
                        ? NaturalMaterial.WATER
                        : NaturalMaterial.AIR;
            } else if (y == surfaceY) {
                material = NaturalMaterial.SURFACE;
            } else {
                int depth = surfaceY - y;
                if (isNaturalVoid(y, depth, column.soilDepth(), caveA, caveB, cavern, ravine)) {
                    if (y <= lavaLevel && unitHash(x, y, z, LAVA_SALT) > 0.34D) {
                        material = NaturalMaterial.LAVA;
                    } else if (y <= column.groundwaterY()) {
                        material = NaturalMaterial.WATER;
                    } else {
                        material = NaturalMaterial.AIR;
                    }
                } else if (depth <= column.soilDepth()) {
                    material = NaturalMaterial.SOIL;
                } else if (depth >= 48 || y < context.minY() + context.height() / 3) {
                    material = NaturalMaterial.DEEP_ROCK;
                } else {
                    material = NaturalMaterial.ROCK;
                }
            }
            materials[offset + y - context.minY()] = (byte) material.ordinal();
        }
    }

    private boolean isNaturalVoid(
            int y,
            int depth,
            int soilDepth,
            VerticalNoiseSampler caveA,
            VerticalNoiseSampler caveB,
            VerticalNoiseSampler cavern,
            VerticalNoiseSampler ravine) {
        if (depth <= Math.max(7, soilDepth + 3) || y <= context.minY() + 5) {
            return false;
        }
        double caveAValue = caveA.sample(y);
        double caveBValue = caveB.sample(y);
        boolean tunnel = Math.abs(caveAValue) < 0.105D && Math.abs(caveBValue) < 0.32D;

        double cavernValue = cavern.sample(y);
        boolean largeCavern = depth > 18 && cavernValue > 0.68D && caveAValue > -0.28D;

        double ravineValue = ravine.sample(y);
        boolean narrowRavine = depth > 12 && Math.abs(ravineValue) < 0.028D && caveBValue > 0.05D;
        return tunnel || largeCavern || narrowRavine;
    }

    private GeologyType geology(int x, int z) {
        double value = smoothNoise2D(x / 192.0D, z / 192.0D, GEOLOGY_SALT);
        if (value < -0.58D) {
            return GeologyType.SEDIMENTARY;
        }
        if (value < -0.22D) {
            return GeologyType.CARBONATE;
        }
        if (value < 0.18D) {
            return GeologyType.METAMORPHIC;
        }
        if (value < 0.55D) {
            return GeologyType.GRANITIC;
        }
        return GeologyType.VOLCANIC;
    }

    private double smoothNoise2D(double x, double z, long salt) {
        int x0 = fastFloor(x);
        int z0 = fastFloor(z);
        int x1 = x0 + 1;
        int z1 = z0 + 1;
        double fx = fade(x - x0);
        double fz = fade(z - z0);
        double a = lerp(signedHash(x0, 0, z0, salt), signedHash(x1, 0, z0, salt), fx);
        double b = lerp(signedHash(x0, 0, z1, salt), signedHash(x1, 0, z1, salt), fx);
        return lerp(a, b, fz);
    }

    private double signedHash(int x, int y, int z, long salt) {
        return unitHash(x, y, z, salt) * 2.0D - 1.0D;
    }

    private double unitHash(int x, int y, int z, long salt) {
        long value = context.seed() ^ salt;
        value ^= (long) x * 0x9E3779B97F4A7C15L;
        value ^= (long) y * 0xC2B2AE3D27D4EB4FL;
        value ^= (long) z * 0x165667B19E3779F9L;
        value ^= value >>> 30;
        value *= 0xBF58476D1CE4E5B9L;
        value ^= value >>> 27;
        value *= 0x94D049BB133111EBL;
        value ^= value >>> 31;
        return (value >>> 11) * 0x1.0p-53;
    }

    private static int fastFloor(double value) {
        int integer = (int) value;
        return value < integer ? integer - 1 : integer;
    }

    private static double fade(double value) {
        return value * value * (3.0D - 2.0D * value);
    }

    private static double lerp(double a, double b, double alpha) {
        return a + (b - a) * alpha;
    }

    private static int clamp(int value, int min, int max) {
        return Math.max(min, Math.min(max, value));
    }

    /** Reuses the four X/Z-interpolated lattice values while scanning one vertical column. */
    private final class VerticalNoiseSampler {

        private final int x0;
        private final int x1;
        private final int z0;
        private final int z1;
        private final double fx;
        private final double fz;
        private final double inverseYScale;
        private final long salt;
        private int cachedY0 = Integer.MIN_VALUE;
        private double x00;
        private double x10;
        private double x01;
        private double x11;

        private VerticalNoiseSampler(
                int x,
                int z,
                double xScale,
                double yScale,
                double zScale,
                long salt) {
            double scaledX = x / xScale;
            double scaledZ = z / zScale;
            this.x0 = fastFloor(scaledX);
            this.x1 = x0 + 1;
            this.z0 = fastFloor(scaledZ);
            this.z1 = z0 + 1;
            this.fx = fade(scaledX - x0);
            this.fz = fade(scaledZ - z0);
            this.inverseYScale = 1.0D / yScale;
            this.salt = salt;
        }

        private double sample(int y) {
            double scaledY = y * inverseYScale;
            int y0 = fastFloor(scaledY);
            if (y0 != cachedY0) {
                int y1 = y0 + 1;
                x00 = lerp(signedHash(x0, y0, z0, salt), signedHash(x1, y0, z0, salt), fx);
                x10 = lerp(signedHash(x0, y1, z0, salt), signedHash(x1, y1, z0, salt), fx);
                x01 = lerp(signedHash(x0, y0, z1, salt), signedHash(x1, y0, z1, salt), fx);
                x11 = lerp(signedHash(x0, y1, z1, salt), signedHash(x1, y1, z1, salt), fx);
                cachedY0 = y0;
            }
            double fy = fade(scaledY - y0);
            return lerp(lerp(x00, x10, fy), lerp(x01, x11, fy), fz);
        }
    }
}
