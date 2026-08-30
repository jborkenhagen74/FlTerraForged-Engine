package dev.foucaultleon.flterraforged.engine.continent;

import dev.foucaultleon.flterraforged.engine.noise.FractalNoise;
import dev.foucaultleon.flterraforged.engine.noise.GradientNoise;
import dev.foucaultleon.flterraforged.engine.noise.Interpolation;
import dev.foucaultleon.flterraforged.engine.noise.Noise;
import dev.foucaultleon.flterraforged.engine.noise.NoiseMath;
import dev.foucaultleon.flterraforged.engine.noise.Vector2;
import dev.foucaultleon.flterraforged.engine.noise.domain.Domain;
import dev.foucaultleon.flterraforged.engine.noise.domain.NoiseDomain;
import java.util.Objects;

/**
 * Warped jittered-Voronoi continent generator inspired by the modern TerraForged continent model.
 *
 * <p>Each tectonic cell owns a deterministic displaced point. The nearest point identifies the
 * continent, while the perpendicular bisectors to neighboring points form ocean-producing borders.
 * The resulting boundary distance is size-varied and coastline-modulated before being normalized.</p>
 */
public final class AdvancedContinent implements Continent {

    private static final long CELL_SEED = 0xA0761D6478BD642FL;
    private static final long CELL_Z_SEED = 0xE7037ED1A0B428DBL;
    private static final long ID_SEED = 0x8EBC6AF09C88C6E3L;
    private static final long SIZE_SEED = 0x589965CC75374CC3L;
    private static final long SKIP_SEED = 0x1D8E4E27C47D124FL;
    private static final long WARP_X_SEED = 0xEB44ACCAB455D165L;
    private static final long WARP_Z_SEED = 0x9E3779B97F4A7C15L;
    private static final long COAST_SEED = 0xC2B2AE3D27D4EB4FL;
    private static final double CENTER_CORRECTION = 0.35D;

    private final long seed;
    private final ContinentSettings settings;
    private final double frequency;
    private final Domain warp;
    private final Noise coastNoise;

    /**
     * Creates a continent generator.
     *
     * @param seed world seed
     * @param settings continent settings
     */
    public AdvancedContinent(long seed, ContinentSettings settings) {
        this.seed = seed;
        this.settings = Objects.requireNonNull(settings, "settings");
        this.frequency = 1.0D / settings.cellSize();

        double warpScale = Math.max(64.0D, settings.cellSize() * 0.225D);
        Noise xWarp = new FractalNoise(
                new GradientNoise(WARP_X_SEED, 1.0D / warpScale, Interpolation.QUINTIC),
                3,
                0.5D,
                2.0D);
        Noise zWarp = new FractalNoise(
                new GradientNoise(WARP_Z_SEED, 1.0D / warpScale, Interpolation.QUINTIC),
                3,
                0.5D,
                2.0D);
        this.warp = new NoiseDomain(xWarp, zWarp, settings.warpStrength());

        double coastScale = 1.0D / Math.max(48.0D, settings.cellSize() * 0.08D);
        this.coastNoise = new FractalNoise(
                new GradientNoise(COAST_SEED, coastScale, Interpolation.QUINTIC),
                3,
                0.5D,
                2.0D);
    }

    /** {@inheritDoc} */
    @Override
    public ContinentSample sample(double worldX, double worldZ) {
        ContinentCell cell = sampleCell(worldX, worldZ);
        ContinentPoint owner = cell.owner();
        double edge = cell.skipped()
                ? 0.0D
                : edgeValue(worldX, worldZ, owner.cellX(), owner.cellZ(), cell.borderDistanceSquared());
        double id = unitCellValue(ID_SEED, owner.cellX(), owner.cellZ());
        int centerX = correctedCenter(owner.x(), cell.neighborAverageX());
        int centerZ = correctedCenter(owner.z(), cell.neighborAverageZ());
        return new ContinentSample(id, edge, new ContinentCenter(centerX, centerZ));
    }

    /**
     * Resolves the full advanced-continent geometric workspace for a position.
     *
     * @param worldX world X coordinate
     * @param worldZ world Z coordinate
     * @return newly allocated populated continent cell
     */
    public ContinentCell sampleCell(double worldX, double worldZ) {
        return sampleCell(worldX, worldZ, new ContinentCell());
    }

    /**
     * Resolves the full advanced-continent geometric workspace into caller-owned storage.
     *
     * @param worldX world X coordinate
     * @param worldZ world Z coordinate
     * @param target reusable target workspace
     * @return {@code target}
     */
    public ContinentCell sampleCell(double worldX, double worldZ, ContinentCell target) {
        Objects.requireNonNull(target, "target").reset();
        Vector2 warped = warp.transform(worldX, worldZ, seed);
        double x = warped.x() * frequency;
        double z = warped.z() * frequency;
        target.setSample(x, z);

        int xi = NoiseMath.floor(x);
        int zi = NoiseMath.floor(z);
        for (int cz = zi - 1; cz <= zi + 1; cz++) {
            for (int cx = xi - 1; cx <= xi + 1; cx++) {
                ContinentPoint point = cellPoint(cx, cz);
                target.considerOwner(point, distanceSq(x, z, point.x(), point.z()));
            }
        }

        ContinentPoint owner = target.owner();
        if (owner == null) {
            throw new IllegalStateException("No continent owner resolved");
        }
        for (int cz = owner.cellZ() - 1; cz <= owner.cellZ() + 1; cz++) {
            for (int cx = owner.cellX() - 1; cx <= owner.cellX() + 1; cx++) {
                if (cx == owner.cellX() && cz == owner.cellZ()) {
                    continue;
                }
                ContinentPoint neighbor = cellPoint(cx, cz);
                double boundary = distanceToBisectorSq(x, z, owner, neighbor);
                target.considerNeighbor(neighbor, boundary);
            }
        }
        target.setSkipped(shouldSkip(owner.cellX(), owner.cellZ()));
        return target;
    }

    /**
     * Estimates the world-space distance from a continent center to its Voronoi boundary along a
     * direction.
     *
     * @param center stable continent center
     * @param directionX ray X direction
     * @param directionZ ray Z direction
     * @return distance in blocks to the owner change
     */
    public double distanceToEdge(ContinentCenter center, double directionX, double directionZ) {
        Objects.requireNonNull(center, "center");
        Vector2 direction = normalizedDirection(directionX, directionZ);
        ContinentPoint owner = sampleCell(center.x(), center.z()).owner();
        double low = 0.0D;
        double high = settings.cellSize() * 4.0D;

        for (int i = 0; i < 10; i++) {
            if (!sameOwner(owner, center.x() + direction.x() * high, center.z() + direction.z() * high)) {
                break;
            }
            low = high;
            high *= 2.0D;
        }

        for (int i = 0; i < 64 && high - low > 1.0D; i++) {
            double mid = (low + high) * 0.5D;
            double x = center.x() + direction.x() * mid;
            double z = center.z() + direction.z() * mid;
            if (sameOwner(owner, x, z)) {
                low = mid;
            } else {
                high = mid;
            }
        }
        return high;
    }

    /**
     * Estimates the distance from a continent center to the first point where the normalized edge
     * signal reaches a requested ocean-like threshold.
     *
     * <p>The threshold is engine-neutral. FlTerraForged may later map its own shallow-ocean control
     * point to this parameter without coupling the engine to Minecraft or biome settings.</p>
     *
     * @param center stable continent center
     * @param directionX ray X direction
     * @param directionZ ray Z direction
     * @param edgeThreshold normalized edge threshold in {@code [0, 1]}
     * @return distance in blocks to the threshold crossing
     */
    public double distanceToEdgeThreshold(
            ContinentCenter center,
            double directionX,
            double directionZ,
            double edgeThreshold) {
        if (!Double.isFinite(edgeThreshold) || edgeThreshold < 0.0D || edgeThreshold > 1.0D) {
            throw new IllegalArgumentException("edgeThreshold must be finite and in [0, 1]");
        }
        Vector2 direction = normalizedDirection(directionX, directionZ);
        double low = 0.0D;
        double high = distanceToEdge(center, direction.x(), direction.z());
        for (int i = 0; i < 64 && high - low > 1.0D; i++) {
            double mid = (low + high) * 0.5D;
            double x = center.x() + direction.x() * mid;
            double z = center.z() + direction.z() * mid;
            if (edgeValue(x, z) > edgeThreshold) {
                low = mid;
            } else {
                high = mid;
            }
        }
        return high;
    }

    private ContinentPoint cellPoint(int cellX, int cellZ) {
        double ox = unitCellValue(CELL_SEED, cellX, cellZ) - 0.5D;
        double oz = unitCellValue(CELL_Z_SEED, cellX, cellZ) - 0.5D;
        return new ContinentPoint(
                cellX,
                cellZ,
                cellX + 0.5D + ox * settings.jitter(),
                cellZ + 0.5D + oz * settings.jitter());
    }

    private double edgeValue(double worldX, double worldZ, int cellX, int cellZ, double borderSq) {
        double distance = Math.sqrt(Math.max(0.0D, borderSq));
        if (settings.sizeVariance() > 0.0D && !isOrigin(cellX, cellZ)) {
            double size = unitCellValue(SIZE_SEED, cellX, cellZ);
            double modifier = 1.0D + (size * 2.0D - 1.0D) * settings.sizeVariance();
            distance *= modifier;
        }

        // In cell space roughly 0.0 is a boundary and ~0.5 is the center. Keeping a broad ocean
        // band around the boundary produces distinct continents rather than seamless Voronoi land.
        double edge = NoiseMath.clamp((distance - 0.055D) / 0.205D, 0.0D, 1.0D);
        if (edge > 0.0D && edge < 1.0D && settings.coastRoughness() > 0.0D) {
            double roughness = coastNoise.sample(worldX, worldZ, seed);
            double alpha = 1.0D - Math.abs(edge * 2.0D - 1.0D);
            edge += roughness * settings.coastRoughness() * 0.16D * alpha;
            edge = NoiseMath.clamp(edge, 0.0D, 1.0D);
        }
        return edge;
    }

    private boolean shouldSkip(int cellX, int cellZ) {
        if (settings.skipping() <= 0.0D || isOrigin(cellX, cellZ)) {
            return false;
        }
        return unitCellValue(SKIP_SEED, cellX, cellZ) < settings.skipping();
    }

    private boolean isOrigin(int cellX, int cellZ) {
        return cellX == 0 && cellZ == 0;
    }

    private int correctedCenter(double point, double average) {
        double corrected = NoiseMath.lerp(point, average, CENTER_CORRECTION) / frequency;
        return (int) Math.round(corrected);
    }

    private double unitCellValue(long salt, int cellX, int cellZ) {
        return 0.5D + NoiseMath.value(seed ^ salt, cellX, cellZ) * 0.5D;
    }

    private boolean sameOwner(ContinentPoint expected, double worldX, double worldZ) {
        ContinentPoint actual = sampleCell(worldX, worldZ).owner();
        return actual.cellX() == expected.cellX() && actual.cellZ() == expected.cellZ();
    }

    private static Vector2 normalizedDirection(double x, double z) {
        if (!Double.isFinite(x) || !Double.isFinite(z)) {
            throw new IllegalArgumentException("direction must be finite");
        }
        double length = Math.hypot(x, z);
        if (length == 0.0D) {
            throw new IllegalArgumentException("direction must be non-zero");
        }
        return new Vector2(x / length, z / length);
    }

    private static double distanceSq(double x, double z, double px, double pz) {
        double dx = x - px;
        double dz = z - pz;
        return dx * dx + dz * dz;
    }

    private static double distanceToBisectorSq(double x, double z, ContinentPoint a, ContinentPoint b) {
        double mx = (a.x() + b.x()) * 0.5D;
        double mz = (a.z() + b.z()) * 0.5D;
        double dx = b.x() - a.x();
        double dz = b.z() - a.z();
        double lengthSq = dx * dx + dz * dz;
        if (lengthSq == 0.0D) {
            return 0.0D;
        }
        // The bisector normal is the vector between the points. Project the sample onto it.
        double signed = ((x - mx) * dx + (z - mz) * dz) / Math.sqrt(lengthSq);
        return signed * signed;
    }
}
