package dev.foucaultleon.flterraforged.engine.chunk;

import dev.foucaultleon.flterraforged.engine.api.EngineContext;
import dev.foucaultleon.flterraforged.engine.api.chunk.ColumnSnapshot;
import dev.foucaultleon.flterraforged.engine.api.chunk.GeologyType;
import dev.foucaultleon.flterraforged.engine.api.chunk.NaturalMaterial;
import dev.foucaultleon.flterraforged.engine.api.river.RiverSample;
import dev.foucaultleon.flterraforged.engine.api.terrain.StandardTerrainTypes;
import dev.foucaultleon.flterraforged.engine.api.terrain.TerrainSample;
import dev.foucaultleon.flterraforged.engine.noise.ValueNoise3D;
import java.util.Objects;

/** Generates immutable vertical natural-material fields for Engine-owned chunks. */
final class SubsurfaceGenerator {

    private static final double MIN_WET_DEPTH = 0.05D;
    private static final double MOUTH_CLAMP_HEIGHT = 6.0D;

    private final EngineContext context;
    private final ValueNoise3D caveNoise;
    private final ValueNoise3D cavernNoise;
    private final ValueNoise3D ravineNoise;
    private final ValueNoise3D geologyNoise;

    SubsurfaceGenerator(EngineContext context) {
        this.context = Objects.requireNonNull(context, "context");
        long seed = context.seed();
        this.caveNoise = new ValueNoise3D(seed ^ 0xF1357AEA2E62A9C5L, 0.038D);
        this.cavernNoise = new ValueNoise3D(seed ^ 0x9E3779B97F4A7C15L, 0.017D);
        this.ravineNoise = new ValueNoise3D(seed ^ 0xC2B2AE3D27D4EB4FL, 0.024D);
        this.geologyNoise = new ValueNoise3D(seed ^ 0x94D049BB133111EBL, 0.0065D);
    }

    EngineChunkSnapshot generate(int chunkX, int chunkZ, TerrainSample[] terrain) {
        if (terrain.length != EngineChunkSnapshot.AREA) {
            throw new IllegalArgumentException("terrain must contain exactly 256 samples");
        }
        int height = context.maxYExclusive() - context.minY();
        byte[] materials = new byte[EngineChunkSnapshot.AREA * height];
        ColumnSnapshot[] columns = new ColumnSnapshot[EngineChunkSnapshot.AREA];
        int originX = chunkX << 4;
        int originZ = chunkZ << 4;
        for (int localZ = 0; localZ < EngineChunkSnapshot.SIZE; localZ++) {
            for (int localX = 0; localX < EngineChunkSnapshot.SIZE; localX++) {
                int columnIndex = localZ * EngineChunkSnapshot.SIZE + localX;
                int x = originX + localX;
                int z = originZ + localZ;
                TerrainSample sample = terrain[columnIndex];
                ColumnSnapshot column = resolveColumn(sample, x, z);
                columns[columnIndex] = column;
                fillColumn(materials, columnIndex, x, z, column, sample);
            }
        }
        return new EngineChunkSnapshot(
                chunkX,
                chunkZ,
                context.minY(),
                context.maxYExclusive(),
                terrain,
                columns,
                materials);
    }

    private ColumnSnapshot resolveColumn(TerrainSample sample, int x, int z) {
        int surfaceY = clamp(
                (int) Math.floor(sample.surfaceHeight()),
                context.minY(),
                context.maxYExclusive() - 2);
        int solidTop = surfaceY + 1;
        int waterTop = solidTop;
        boolean marine = StandardTerrainTypes.OCEAN.equals(sample.terrainType())
                || StandardTerrainTypes.COAST.equals(sample.terrainType());
        if (marine) {
            waterTop = Math.max(waterTop, context.seaLevel() + 1);
        }

        RiverSample hydrology = sample.river();
        if (hydrology.hasWaterSurfaceHeight()
                && hydrology.depth() > MIN_WET_DEPTH
                && hydrology.waterSurfaceHeight() > sample.surfaceHeight()) {
            double waterSurface = hydrology.waterSurfaceHeight();
            // R51 belt-and-suspenders invariant: low river mouths may never materialize above the
            // global ocean surface. The pipeline already canonicalizes them; this final snapshot
            // guard prevents a future hydrology provider from reintroducing a water shelf.
            if (marine
                    || (surfaceY <= context.seaLevel() + 2
                            && waterSurface <= context.seaLevel() + MOUTH_CLAMP_HEIGHT)) {
                waterSurface = Math.min(waterSurface, context.seaLevel());
            }
            int hydrologyWaterTop = (int) Math.floor(waterSurface) + 1;
            waterTop = Math.max(waterTop, hydrologyWaterTop);
        }
        waterTop = clamp(waterTop, solidTop, context.maxYExclusive());

        int soilDepth = soilDepth(sample);
        int deepRockY = Math.min(surfaceY - soilDepth - 10, context.seaLevel() - 18);
        deepRockY = clamp(deepRockY, context.minY() + 5, surfaceY - soilDepth);
        int bedrockTop = Math.min(context.minY() + 5, context.maxYExclusive() - 1);
        int groundwaterY = Math.min(context.seaLevel() - 5, surfaceY - 10);
        groundwaterY = clamp(groundwaterY, context.minY() + 6, context.maxYExclusive() - 1);
        int lavaTop = Math.min(context.minY() + 12, context.maxYExclusive() - 1);
        GeologyType geology = geology(x, z, surfaceY);
        return new ColumnSnapshot(
                surfaceY,
                waterTop,
                soilDepth,
                deepRockY,
                bedrockTop,
                groundwaterY,
                lavaTop,
                geology);
    }

    private void fillColumn(
            byte[] materials,
            int columnIndex,
            int x,
            int z,
            ColumnSnapshot column,
            TerrainSample sample) {
        int height = context.maxYExclusive() - context.minY();
        int naturalTopY = Math.min(
                context.maxYExclusive() - 1,
                Math.max(column.surfaceY(), column.waterTopExclusive() - 1));
        ValueNoise3D.VerticalSampler caveSampler = caveNoise.verticalSampler(x, z);
        ValueNoise3D.VerticalSampler cavernSampler = cavernNoise.verticalSampler(x, z);
        ValueNoise3D.VerticalSampler ravineSampler = ravineNoise.verticalSampler(x, z);
        for (int y = context.minY(); y <= naturalTopY; y++) {
            NaturalMaterial material = materialAt(x, y, z, column, sample, caveSampler, cavernSampler, ravineSampler);
            int yIndex = y - context.minY();
            materials[yIndex * EngineChunkSnapshot.AREA + columnIndex] = (byte) material.ordinal();
        }
        if (height < 1) {
            throw new IllegalStateException("World height must be positive");
        }
    }

    private NaturalMaterial materialAt(
            int x,
            int y,
            int z,
            ColumnSnapshot column,
            TerrainSample sample,
            ValueNoise3D.VerticalSampler caveSampler,
            ValueNoise3D.VerticalSampler cavernSampler,
            ValueNoise3D.VerticalSampler ravineSampler) {
        if (y >= column.waterTopExclusive() && y > column.surfaceY()) {
            return NaturalMaterial.AIR;
        }
        if (y > column.surfaceY()) {
            return NaturalMaterial.WATER;
        }
        if (y <= context.minY()) {
            return NaturalMaterial.BEDROCK;
        }
        if (y <= column.bedrockTop()) {
            double chance = geologyNoise.sample(x, y, z);
            if (chance > -0.20D + (y - context.minY()) * 0.13D) {
                return NaturalMaterial.BEDROCK;
            }
        }

        if (isOpenSubsurface(y, column, caveSampler, cavernSampler, ravineSampler)) {
            if (y <= column.lavaTop()) {
                return NaturalMaterial.LAVA;
            }
            if (y <= column.groundwaterY() && sample.surfaceHeight() > context.seaLevel() - 6.0D) {
                return NaturalMaterial.WATER;
            }
            return NaturalMaterial.AIR;
        }

        int depth = column.surfaceY() - y;
        if (depth == 0) {
            return NaturalMaterial.SURFACE;
        }
        if (depth <= column.soilDepth()) {
            return NaturalMaterial.SOIL;
        }
        if (y <= column.deepRockY()) {
            return NaturalMaterial.DEEP_ROCK;
        }
        return NaturalMaterial.ROCK;
    }

    private boolean isOpenSubsurface(
            int y,
            ColumnSnapshot column,
            ValueNoise3D.VerticalSampler caveSampler,
            ValueNoise3D.VerticalSampler cavernSampler,
            ValueNoise3D.VerticalSampler ravineSampler) {
        int depth = column.surfaceY() - y;
        if (depth < 8 || y <= column.bedrockTop() + 1) {
            return false;
        }
        double cave = caveSampler.sample(y);
        double cavern = cavernSampler.sample(y);
        double ravine = Math.abs(ravineSampler.sample(y));
        double caveThreshold = depth > 28 ? 0.56D : 0.66D;
        boolean tunnel = cave > caveThreshold;
        boolean largeCavern = depth > 22 && cavern > 0.71D;
        boolean ravineCut = depth > 12 && ravine < 0.055D && cave > 0.05D;
        return tunnel || largeCavern || ravineCut;
    }

    private GeologyType geology(int x, int z, int surfaceY) {
        double value = geologyNoise.sample(x, surfaceY - 24, z);
        if (value < -0.52D) {
            return GeologyType.CARBONATE;
        }
        if (value < -0.12D) {
            return GeologyType.SEDIMENTARY;
        }
        if (value > 0.55D) {
            return GeologyType.VOLCANIC;
        }
        if (value > 0.16D) {
            return GeologyType.CRYSTALLINE;
        }
        return GeologyType.GENERIC;
    }

    private static int soilDepth(TerrainSample sample) {
        double slope = sample.hasSlope() ? sample.slope() : 0.0D;
        double moisture = sample.climate().isAvailable() ? sample.climate().moisture() : 0.5D;
        int depth = 3 + (int) Math.round(moisture * 2.0D - slope * 0.65D);
        return clamp(depth, 1, 6);
    }

    private static int clamp(int value, int minimum, int maximum) {
        return Math.max(minimum, Math.min(maximum, value));
    }
}
