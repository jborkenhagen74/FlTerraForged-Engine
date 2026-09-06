package dev.foucaultleon.flterraforged.engine.chunk;

import dev.foucaultleon.flterraforged.engine.api.chunk.ChunkSnapshot;
import dev.foucaultleon.flterraforged.engine.api.chunk.ColumnSnapshot;
import dev.foucaultleon.flterraforged.engine.api.chunk.NaturalMaterial;
import java.util.Objects;

/** Immutable compact implementation of the Engine-owned natural chunk snapshot. */
final class EngineChunkSnapshot implements ChunkSnapshot {

    private final int chunkX;
    private final int chunkZ;
    private final int minY;
    private final int maxYExclusive;
    private final ColumnSnapshot[] columns;
    private final byte[] materials;

    EngineChunkSnapshot(
            int chunkX,
            int chunkZ,
            int minY,
            int maxYExclusive,
            ColumnSnapshot[] columns,
            byte[] materials) {
        this.chunkX = chunkX;
        this.chunkZ = chunkZ;
        this.minY = minY;
        this.maxYExclusive = maxYExclusive;
        this.columns = Objects.requireNonNull(columns, "columns").clone();
        this.materials = Objects.requireNonNull(materials, "materials").clone();
        int expectedColumns = WIDTH * WIDTH;
        int expectedMaterials = expectedColumns * (maxYExclusive - minY);
        if (this.columns.length != expectedColumns) {
            throw new IllegalArgumentException("snapshot must contain exactly 256 columns");
        }
        if (this.materials.length != expectedMaterials) {
            throw new IllegalArgumentException("material volume does not match world height");
        }
    }

    @Override
    public int chunkX() {
        return chunkX;
    }

    @Override
    public int chunkZ() {
        return chunkZ;
    }

    @Override
    public int minY() {
        return minY;
    }

    @Override
    public int maxYExclusive() {
        return maxYExclusive;
    }

    @Override
    public ColumnSnapshot column(int localX, int localZ) {
        return columns[columnIndex(localX, localZ)];
    }

    @Override
    public NaturalMaterial materialAt(int localX, int y, int localZ) {
        if (y < minY || y >= maxYExclusive) {
            throw new IndexOutOfBoundsException("Y outside snapshot: " + y);
        }
        int index = (columnIndex(localX, localZ) * height()) + (y - minY);
        return NaturalMaterial.values()[Byte.toUnsignedInt(materials[index])];
    }

    private static int columnIndex(int localX, int localZ) {
        if (localX < 0 || localX >= WIDTH || localZ < 0 || localZ >= WIDTH) {
            throw new IndexOutOfBoundsException(
                    "local coordinates outside 0..15: " + localX + "," + localZ);
        }
        return localZ * WIDTH + localX;
    }
}
